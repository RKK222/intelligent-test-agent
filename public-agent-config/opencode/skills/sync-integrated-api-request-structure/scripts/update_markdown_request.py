#!/usr/bin/env python3
"""读取或原子替换 042 Markdown 中唯一的请求 JSON 代码块。"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import sys
import tempfile
from pathlib import Path
from typing import Any, Iterable

HEADING_RE = re.compile(r"(?m)^[ \t]*##[ \t]+3\.[ \t]*请求报文[ \t]*\r?$")
NEXT_H2_RE = re.compile(r"(?m)^[ \t]*##[ \t]+(?!3\.[ \t]*请求报文[ \t]*\r?$).+\r?$")
JSON_FENCE_RE = re.compile(
    r"(?ms)^(?P<indent>[ \t]*)```[ \t]*json[ \t]*\r?\n(?P<body>.*?)(?P<before_close>\r?\n)(?P=indent)```[ \t]*\r?$",
    re.IGNORECASE,
)


class MarkdownUpdateError(RuntimeError):
    """目标 Markdown 不满足确定性读取或写回约束。"""


def validate_target_file(target_file: str | Path) -> Path:
    try:
        target = Path(target_file).expanduser().resolve(strict=True)
    except OSError as exc:
        raise MarkdownUpdateError("目标 Markdown 不存在") from exc
    if not target.is_file() or target.suffix.lower() != ".md":
        raise MarkdownUpdateError("目标必须是 Markdown 文件")
    if target.parent.name != "042-测试执行":
        raise MarkdownUpdateError("目标 Markdown 必须直接位于 042-测试执行 目录")
    return target


def _decode_markdown(raw: bytes) -> tuple[str, bool]:
    has_bom = raw.startswith(b"\xef\xbb\xbf")
    try:
        return raw.decode("utf-8-sig"), has_bom
    except UnicodeDecodeError as exc:
        raise MarkdownUpdateError("目标 Markdown 不是有效 UTF-8") from exc


def _locate_json_block(text: str) -> tuple[re.Match[str], int, int]:
    headings = list(HEADING_RE.finditer(text))
    if len(headings) != 1:
        raise MarkdownUpdateError("目标 Markdown 必须且只能包含一个 ## 3. 请求报文")
    heading = headings[0]
    next_heading = NEXT_H2_RE.search(text, heading.end())
    section_end = next_heading.start() if next_heading else len(text)
    section = text[heading.end() : section_end]
    blocks = list(JSON_FENCE_RE.finditer(section))
    if len(blocks) != 1:
        raise MarkdownUpdateError("## 3. 请求报文 下必须且只能包含一个 JSON 代码块")
    block = blocks[0]
    absolute_body_start = heading.end() + block.start("body")
    absolute_body_end = heading.end() + block.end("body")
    return block, absolute_body_start, absolute_body_end


def read_request_payload(target_file: str | Path) -> tuple[Path, Any, bytes]:
    target = validate_target_file(target_file)
    raw = target.read_bytes()
    text, _ = _decode_markdown(raw)
    _, body_start, body_end = _locate_json_block(text)
    body = text[body_start:body_end]
    try:
        payload = json.loads(body)
    except json.JSONDecodeError as exc:
        raise MarkdownUpdateError("请求报文 JSON 代码块无法解析") from exc
    if not isinstance(payload, dict):
        raise MarkdownUpdateError("请求报文 JSON 根节点必须是对象")
    return target, payload, raw


def _atomic_write_bytes(target: Path, data: bytes, original_mode: int | None = None) -> None:
    temp_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(prefix=f".{target.name}.", suffix=".tmp", dir=target.parent, delete=False) as handle:
            temp_path = Path(handle.name)
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        if original_mode is not None:
            os.chmod(temp_path, stat.S_IMODE(original_mode))
        os.replace(temp_path, target)
        temp_path = None
    finally:
        if temp_path is not None:
            try:
                temp_path.unlink()
            except FileNotFoundError:
                pass


def restore_original_bytes(target_file: str | Path, original: bytes) -> None:
    target = validate_target_file(target_file)
    mode = target.stat().st_mode
    _atomic_write_bytes(target, original, mode)


def update_request_payload(target_file: str | Path, payload: Any) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise MarkdownUpdateError("待写入请求报文的 JSON 根节点必须是对象")
    target = validate_target_file(target_file)
    raw = target.read_bytes()
    text, has_bom = _decode_markdown(raw)
    _, body_start, body_end = _locate_json_block(text)
    newline = "\r\n" if "\r\n" in text else "\n"
    rendered = json.dumps(payload, ensure_ascii=False, indent=2)
    if newline != "\n":
        rendered = rendered.replace("\n", newline)
    updated_text = text[:body_start] + rendered + text[body_end:]
    updated_raw = ((b"\xef\xbb\xbf" if has_bom else b"") + updated_text.encode("utf-8"))
    changed = updated_raw != raw
    if changed:
        _atomic_write_bytes(target, updated_raw, target.stat().st_mode)
    return {
        "targetFile": str(target),
        "changed": changed,
        "sha256": hashlib.sha256(updated_raw).hexdigest(),
    }


def _load_payload(source: str) -> Any:
    try:
        if source == "-":
            value = json.load(sys.stdin)
        else:
            with Path(source).open("r", encoding="utf-8-sig") as handle:
                value = json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        raise MarkdownUpdateError(f"无法读取待写入 JSON：{exc.__class__.__name__}") from exc
    if isinstance(value, dict) and "normalizedPayload" in value:
        return value["normalizedPayload"]
    return value


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="读取或更新 042 请求报文 JSON")
    parser.add_argument("--target-file", required=True)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--read", action="store_true", help="只读取请求 JSON")
    action.add_argument("--payload", help="写入 JSON 文件，- 表示 stdin")
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.read:
            target, payload, raw = read_request_payload(args.target_file)
            result = {
                "targetFile": str(target),
                "payload": payload,
                "sha256": hashlib.sha256(raw).hexdigest(),
            }
        else:
            result = update_request_payload(args.target_file, _load_payload(args.payload))
    except MarkdownUpdateError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())


