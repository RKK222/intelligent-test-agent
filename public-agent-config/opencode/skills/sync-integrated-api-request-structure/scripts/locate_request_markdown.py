#!/usr/bin/env python3
"""在一个 042-测试执行目录内按接口英文名精确定位唯一 Markdown。"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Iterable

from query_interface_contract import ContractError, validate_interface_name

LABELS = {"接口英文名", "英文名", "接口名称"}
TABLE_SEPARATOR_RE = re.compile(r"^:?-{3,}:?$")
TAG_LINE_RE = re.compile(
    r"^\s*(?:[-*+]\s*)?(?:\*\*|__)?(接口英文名|英文名|接口名称)(?:\*\*|__)?\s*[：:]\s*(.+?)\s*$"
)
XML_TAG_RE = re.compile(r"<(?P<label>接口英文名|英文名|接口名称)>\s*(?P<value>[^<>\r\n]+?)\s*</(?P=label)>")


class LocateError(RuntimeError):
    """目标目录或目标文件无法唯一确定。"""


def _clean_cell(value: str) -> str:
    value = value.strip()
    wrappers = (("`", "`"), ("**", "**"), ("__", "__"))
    changed = True
    while changed:
        changed = False
        for left, right in wrappers:
            if value.startswith(left) and value.endswith(right) and len(value) >= len(left) + len(right):
                value = value[len(left) : len(value) - len(right)].strip()
                changed = True
    return value


def _parse_table_row(line: str) -> list[str] | None:
    stripped = line.strip()
    if "|" not in stripped:
        return None
    if stripped.startswith("|"):
        stripped = stripped[1:]
    if stripped.endswith("|"):
        stripped = stripped[:-1]
    cells = [_clean_cell(cell) for cell in stripped.split("|")]
    if len(cells) < 2:
        return None
    return cells


def _is_separator_row(cells: list[str]) -> bool:
    return bool(cells) and all(TABLE_SEPARATOR_RE.fullmatch(cell.replace(" ", "")) for cell in cells)


def extract_interface_values(text: str) -> set[str]:
    """只从 Markdown 表格、键值标签或显式 XML 风格标签中提取值。"""
    values: set[str] = set()
    lines = text.splitlines()

    for line in lines:
        tag_match = TAG_LINE_RE.fullmatch(line)
        if tag_match:
            value = _clean_cell(tag_match.group(2))
            if value:
                values.add(value)
        for match in XML_TAG_RE.finditer(line):
            value = _clean_cell(match.group("value"))
            if value:
                values.add(value)

    index = 0
    while index < len(lines):
        row = _parse_table_row(lines[index])
        if row is None:
            index += 1
            continue
        table: list[list[str]] = []
        while index < len(lines):
            candidate = _parse_table_row(lines[index])
            if candidate is None:
                break
            table.append(candidate)
            index += 1
        data_rows = [cells for cells in table if not _is_separator_row(cells)]
        for cells in data_rows:
            for cell_index, cell in enumerate(cells[:-1]):
                if cell in LABELS:
                    value = _clean_cell(cells[cell_index + 1])
                    if value and value not in LABELS:
                        values.add(value)
        if len(data_rows) >= 2:
            header = data_rows[0]
            for column, cell in enumerate(header):
                if cell not in LABELS:
                    continue
                for data_row in data_rows[1:]:
                    if column < len(data_row):
                        value = _clean_cell(data_row[column])
                        if value and value not in LABELS:
                            values.add(value)
        if not table:
            index += 1
    return values


def validate_execution_root(execution_root: str | Path) -> Path:
    try:
        root = Path(execution_root).expanduser().resolve(strict=True)
    except OSError as exc:
        raise LocateError("042-测试执行目录不存在") from exc
    if not root.is_dir() or root.name != "042-测试执行":
        raise LocateError("execution-root 必须直接指向 042-测试执行 目录")
    return root


def locate_request_markdown(execution_root: str | Path, interface_en_name: str) -> Path:
    root = validate_execution_root(execution_root)
    try:
        expected = validate_interface_name(interface_en_name)
    except ContractError as exc:
        raise LocateError(str(exc)) from exc
    matches: list[Path] = []
    for path in sorted(root.glob("*.md"), key=lambda item: item.name.casefold()):
        try:
            text = path.read_text(encoding="utf-8-sig")
        except (OSError, UnicodeDecodeError) as exc:
            raise LocateError(f"无法读取候选 Markdown：{path.name}") from exc
        if expected in extract_interface_values(text):
            matches.append(path.resolve())
    if not matches:
        raise LocateError(f"未找到接口英文名精确等于 {expected} 的 Markdown")
    if len(matches) != 1:
        names = "、".join(path.name for path in matches)
        raise LocateError(f"接口英文名匹配到多个 Markdown：{names}")
    return matches[0]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="在 042-测试执行 中精确定位接口脚本")
    parser.add_argument("--execution-root", required=True)
    parser.add_argument("--interface-en-name", required=True)
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        target = locate_request_markdown(args.execution_root, args.interface_en_name)
    except LocateError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    json.dump({"targetFile": str(target)}, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
