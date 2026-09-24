#!/usr/bin/env python3
"""查询、规范化、原子写回并复核一个 042 请求报文。"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Iterable

from locate_request_markdown import LocateError, locate_request_markdown
from normalize_request_payload import NormalizeError, normalize_payload, validate_payload_structure
from query_interface_contract import ContractError, query_contract
from update_markdown_request import (
    MarkdownUpdateError,
    read_request_payload,
    restore_original_bytes,
    update_request_payload,
    validate_target_file,
)


class SyncError(RuntimeError):
    """同步未安全完成。"""


def sync_request_structure(
    workspace_root: str,
    interface_en_name: str,
    *,
    target_file: str | Path | None = None,
    execution_root: str | Path | None = None,
) -> dict[str, Any]:
    if (target_file is None) == (execution_root is None):
        raise SyncError("必须且只能指定 target_file 或 execution_root")
    target = (
        validate_target_file(target_file)
        if target_file is not None
        else locate_request_markdown(execution_root, interface_en_name)
    )

    target, source_payload, original_bytes = read_request_payload(target)
    contract = query_contract(workspace_root, interface_en_name)
    normalized = normalize_payload(source_payload, contract["reqParamStruct"])
    if not normalized["passed"]:
        raise SyncError("规范化结果未通过结构校验")

    write_result = update_request_payload(target, normalized["normalizedPayload"])
    try:
        _, persisted_payload, _ = read_request_payload(target)
        verification = validate_payload_structure(persisted_payload, contract["reqParamStruct"])
        if not verification["passed"] or persisted_payload != normalized["normalizedPayload"]:
            raise SyncError("写回后的请求报文未通过二次结构复核")
    except Exception as exc:
        if write_result["changed"]:
            restore_original_bytes(target, original_bytes)
        if isinstance(exc, SyncError):
            raise
        raise SyncError("写回后复核失败，已恢复原文件") from exc

    return {
        "stageStatus": "COMPLETED",
        "interfaceEnName": contract["interfaceEnName"],
        "appName": contract["appName"],
        "version": contract["version"],
        "resolvedOutputTarget": str(target),
        "generatedFiles": [str(target)] if write_result["changed"] else [],
        "changed": write_result["changed"],
        "sha256": write_result["sha256"],
        "wrapperRoot": normalized["wrapperRoot"],
        "sourcePassed": normalized["sourcePassed"],
        "missingPaths": normalized["missingPaths"],
        "extraPaths": normalized["extraPaths"],
        "invalidContainers": normalized["invalidContainers"],
        "nullPaths": normalized["nullPaths"],
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="同步 042 请求报文结构")
    parser.add_argument("--workspace-root", required=True)
    parser.add_argument("--interface-en-name", required=True)
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--target-file")
    target.add_argument("--execution-root")
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        result = sync_request_structure(
            args.workspace_root,
            args.interface_en_name,
            target_file=args.target_file,
            execution_root=args.execution_root,
        )
    except (SyncError, LocateError, NormalizeError, ContractError, MarkdownUpdateError) as exc:
        json.dump({"stageStatus": "INCOMPLETE", "error": str(exc)}, sys.stdout, ensure_ascii=False, indent=2)
        sys.stdout.write("\n")
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
