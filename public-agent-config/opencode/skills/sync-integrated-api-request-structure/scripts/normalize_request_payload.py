#!/usr/bin/env python3
"""按照 reqParamStruct 规范化请求 JSON，并执行独立的结构复核。"""

from __future__ import annotations

import argparse
import copy
import json
import sys
from pathlib import Path
from typing import Any, Iterable

from query_interface_contract import ContractError, validate_req_param_struct


class NormalizeError(RuntimeError):
    """规范化输入或结构不合法。"""


def _node_kind(node: dict[str, Any]) -> str:
    param_class = str(node.get("paramClass") or "").lower().replace(" ", "")
    if (
        "list" in param_class
        or "collection" in param_class
        or "array" in param_class
        or param_class.endswith("[]")
        or "set<" in param_class
    ):
        return "list"
    if node.get("children"):
        return "object"
    if "map" in param_class or "object" in param_class or "dto" in param_class:
        return "object"
    return "leaf"


def _effective_nodes(req_param_struct: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], str | None]:
    if len(req_param_struct) == 1 and req_param_struct[0].get("children"):
        root = req_param_struct[0]
        return root["children"], root["name"]
    return req_param_struct, None


def _path(parent: str, name: str) -> str:
    return f"$.{name}" if parent == "$" else f"{parent}.{name}"


def _append_unique(items: list[str], value: str) -> None:
    if value not in items:
        items.append(value)


def validate_payload_structure(payload: Any, req_param_struct: list[dict[str, Any]]) -> dict[str, Any]:
    req_param_struct = validate_req_param_struct(req_param_struct)
    nodes, wrapper_root = _effective_nodes(req_param_struct)
    diagnostics = {
        "missingPaths": [],
        "extraPaths": [],
        "invalidContainers": [],
        "nullPaths": [],
    }

    def check_object(current: Any, children: list[dict[str, Any]], parent: str) -> None:
        if not isinstance(current, dict):
            _append_unique(diagnostics["invalidContainers"], parent)
            return
        expected = [node["name"] for node in children]
        expected_set = set(expected)
        for key in current:
            if key not in expected_set:
                _append_unique(diagnostics["extraPaths"], _path(parent, str(key)))
        for node in children:
            name = node["name"]
            child_path = _path(parent, name)
            if name not in current:
                _append_unique(diagnostics["missingPaths"], child_path)
                continue
            value = current[name]
            if value is None:
                _append_unique(diagnostics["nullPaths"], child_path)
                continue
            kind = _node_kind(node)
            child_nodes = node.get("children", [])
            if kind == "object":
                if not isinstance(value, dict):
                    _append_unique(diagnostics["invalidContainers"], child_path)
                elif child_nodes:
                    check_object(value, child_nodes, child_path)
            elif kind == "list":
                if not isinstance(value, list):
                    _append_unique(diagnostics["invalidContainers"], child_path)
                elif child_nodes:
                    if not value:
                        _append_unique(diagnostics["invalidContainers"], child_path)
                    for index, item in enumerate(value):
                        item_path = f"{child_path}[{index}]"
                        if not isinstance(item, dict):
                            _append_unique(diagnostics["invalidContainers"], item_path)
                        else:
                            check_object(item, child_nodes, item_path)

    check_object(payload, nodes, "$")
    passed = not any(diagnostics.values())
    return {"passed": passed, "wrapperRoot": wrapper_root, **diagnostics}


def normalize_payload(payload: Any, req_param_struct: list[dict[str, Any]]) -> dict[str, Any]:
    req_param_struct = validate_req_param_struct(req_param_struct)
    nodes, wrapper_root = _effective_nodes(req_param_struct)
    source_validation = validate_payload_structure(payload, req_param_struct)
    missing: list[str] = []
    extra: list[str] = []
    invalid: list[str] = []
    nulls: list[str] = []

    def build_object(current: Any, children: list[dict[str, Any]], parent: str) -> dict[str, Any]:
        if not isinstance(current, dict):
            _append_unique(invalid, parent)
            current = {}
        expected_names = [node["name"] for node in children]
        expected_set = set(expected_names)
        for key in current:
            if key not in expected_set:
                _append_unique(extra, _path(parent, str(key)))
        output: dict[str, Any] = {}
        for node in children:
            name = node["name"]
            child_path = _path(parent, name)
            present = name in current
            value = current.get(name)
            if not present:
                _append_unique(missing, child_path)
            elif value is None:
                _append_unique(nulls, child_path)
            kind = _node_kind(node)
            child_nodes = node.get("children", [])
            if kind == "object":
                if present and value is not None and not isinstance(value, dict):
                    _append_unique(invalid, child_path)
                seed = value if isinstance(value, dict) else {}
                output[name] = build_object(seed, child_nodes, child_path) if child_nodes else copy.deepcopy(seed)
            elif kind == "list":
                if present and value is not None and not isinstance(value, list):
                    _append_unique(invalid, child_path)
                seed_list = value if isinstance(value, list) else []
                if child_nodes:
                    if not seed_list:
                        output[name] = [build_object({}, child_nodes, f"{child_path}[0]")]
                    else:
                        normalized_items: list[Any] = []
                        for index, item in enumerate(seed_list):
                            item_path = f"{child_path}[{index}]"
                            if not isinstance(item, dict):
                                _append_unique(invalid, item_path)
                                item = {}
                            normalized_items.append(build_object(item, child_nodes, item_path))
                        output[name] = normalized_items
                else:
                    output[name] = copy.deepcopy(seed_list)
            else:
                output[name] = "" if not present or value is None else copy.deepcopy(value)
        return output

    normalized = build_object(payload, nodes, "$")
    final_validation = validate_payload_structure(normalized, req_param_struct)
    result = {
        "normalizedPayload": normalized,
        "passed": final_validation["passed"],
        "sourcePassed": source_validation["passed"],
        "changed": normalized != payload,
        "wrapperRoot": wrapper_root,
        "missingPaths": missing,
        "extraPaths": extra,
        "invalidContainers": invalid,
        "nullPaths": nulls,
    }
    if not result["passed"]:
        raise NormalizeError("规范化后的请求报文仍未通过结构复核")
    return result


def _load_json(source: str) -> Any:
    try:
        if source == "-":
            return json.load(sys.stdin)
        with Path(source).open("r", encoding="utf-8-sig") as handle:
            return json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        raise NormalizeError(f"无法读取输入 JSON：{exc.__class__.__name__}") from exc


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="按 reqParamStruct 规范化请求报文")
    parser.add_argument("--input", default="-", help="输入 JSON 文件，- 表示 stdin")
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        request = _load_json(args.input)
        if not isinstance(request, dict) or "payload" not in request or "reqParamStruct" not in request:
            raise NormalizeError("输入必须是包含 payload 和 reqParamStruct 的 JSON 对象")
        result = normalize_payload(request["payload"], request["reqParamStruct"])
    except (NormalizeError, ContractError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
