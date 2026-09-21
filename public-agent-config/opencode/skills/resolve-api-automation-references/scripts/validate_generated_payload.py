#!/usr/bin/env python3
"""忽略 reqParamStruct 第一层根节点后校验报文结构、案例指定增删字段和字段长度。"""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


@dataclass
class ValidationResult:
    missing_paths: list[str] = field(default_factory=list)
    allowed_missing_paths: list[str] = field(default_factory=list)
    unexpected_missing_paths: list[str] = field(default_factory=list)
    expected_missing_but_present_paths: list[str] = field(default_factory=list)
    unknown_expected_missing_paths: list[str] = field(default_factory=list)
    extra_paths: list[str] = field(default_factory=list)
    allowed_additional_paths: list[str] = field(default_factory=list)
    unexpected_additional_paths: list[str] = field(default_factory=list)
    expected_additional_but_missing_paths: list[str] = field(default_factory=list)
    invalid_expected_additional_paths: list[str] = field(default_factory=list)
    null_paths: list[str] = field(default_factory=list)
    invalid_containers: list[str] = field(default_factory=list)
    present_paths: list[str] = field(default_factory=list)
    visited_container_paths: list[str] = field(default_factory=list)
    leaf_values: dict[str, Any] = field(default_factory=dict)
    length_checks: list[dict[str, Any]] = field(default_factory=list)

    @property
    def passed(self) -> bool:
        return not (
            self.unexpected_missing_paths
            or self.expected_missing_but_present_paths
            or self.unknown_expected_missing_paths
            or self.unexpected_additional_paths
            or self.expected_additional_but_missing_paths
            or self.invalid_expected_additional_paths
            or self.null_paths
            or self.invalid_containers
            or any(not item["passed"] for item in self.length_checks)
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        default="-",
        help="包含 payload 和 reqParamStruct 的 JSON；使用 - 从 stdin 读取",
    )
    parser.add_argument(
        "--length-check",
        action="append",
        default=[],
        help="字段路径=期望长度:chars|utf8-bytes，可重复，例如 ABSP.a.b=34:chars",
    )
    parser.add_argument(
        "--expected-missing-field",
        action="append",
        default=[],
        help="已评审案例明确要求缺少的字段路径，可重复；其它 reqParamStruct 字段仍必须完整存在",
    )
    parser.add_argument(
        "--expected-additional-field",
        action="append",
        default=[],
        help="已评审案例明确要求新增的字段路径，可重复；未声明的额外字段仍禁止出现",
    )
    return parser.parse_args()


def parse_serialized(value: Any) -> Any:
    if not isinstance(value, str):
        return value
    stripped = value.strip()
    if not stripped:
        return value
    if stripped.startswith(("{", "[")):
        try:
            return json.loads(stripped)
        except json.JSONDecodeError:
            return value
    if stripped.startswith("<"):
        try:
            return ET.fromstring(stripped)
        except ET.ParseError:
            return value
    return value


def lookup(container: Any, name: str) -> tuple[bool, Any]:
    parsed = parse_serialized(container)
    if isinstance(parsed, dict):
        return (name in parsed, parsed.get(name))
    if isinstance(parsed, ET.Element):
        if parsed.tag == name:
            return True, parsed
        child = parsed.find(name)
        return (child is not None, child)
    return False, None


def actual_child_names(container: Any) -> set[str]:
    parsed = parse_serialized(container)
    if isinstance(parsed, dict):
        return {str(key) for key in parsed}
    if isinstance(parsed, ET.Element):
        return {child.tag for child in list(parsed)}
    return set()


def join_path(prefix: str, name: str) -> str:
    return f"{prefix}.{name}" if prefix else name


def canonical_schema_path(path: str) -> str:
    return re.sub(r"\[\d+\]", "", path)


def collect_schema_paths(nodes: list[dict[str, Any]], prefix: str = "") -> set[str]:
    paths: set[str] = set()
    for node in nodes:
        name = str(node.get("name", "")).strip()
        if not name:
            continue
        path = join_path(prefix, name)
        paths.add(path)
        children = node.get("children")
        if isinstance(children, list):
            paths.update(collect_schema_paths(children, path))
    return paths


def collect_schema_container_paths(nodes: list[dict[str, Any]], prefix: str = "") -> set[str]:
    paths: set[str] = set()
    for node in nodes:
        name = str(node.get("name", "")).strip()
        if not name:
            continue
        path = join_path(prefix, name)
        children = node.get("children")
        if isinstance(children, list) and children:
            paths.add(path)
            paths.update(collect_schema_container_paths(children, path))
    return paths


def validate_nodes(nodes: list[dict[str, Any]], container: Any, prefix: str, result: ValidationResult) -> None:
    result.visited_container_paths.append(prefix)
    expected_names = {str(node.get("name", "")) for node in nodes if str(node.get("name", ""))}
    parsed_container = parse_serialized(container)
    if isinstance(parsed_container, ET.Element) and parsed_container.tag in expected_names:
        actual_names = {parsed_container.tag}
    else:
        actual_names = actual_child_names(container)
    for extra in sorted(actual_names - expected_names):
        extra_path = join_path(prefix, extra)
        result.extra_paths.append(extra_path)
        found, extra_value = lookup(container, extra)
        if found and extra_value is None:
            result.null_paths.append(extra_path)

    for node in nodes:
        name = str(node.get("name", "")).strip()
        if not name:
            continue
        path = join_path(prefix, name)
        found, value = lookup(container, name)
        if not found:
            result.missing_paths.append(path)
            continue
        result.present_paths.append(path)
        if value is None:
            result.null_paths.append(path)
            continue

        children = node.get("children")
        children = children if isinstance(children, list) else []
        if not children:
            if isinstance(value, (dict, list)):
                result.invalid_containers.append(path)
                continue
            result.leaf_values[path] = value.text or "" if isinstance(value, ET.Element) else value
            continue

        parsed = parse_serialized(value)
        if isinstance(parsed, list):
            if not parsed:
                result.invalid_containers.append(path)
                continue
            for index, item in enumerate(parsed):
                validate_nodes(children, item, f"{path}[{index}]", result)
        elif isinstance(parsed, (dict, ET.Element)):
            validate_nodes(children, parsed, path, result)
        else:
            result.invalid_containers.append(path)


def parse_length_check(raw: str) -> tuple[str, int, str]:
    if "=" not in raw or ":" not in raw:
        raise ValueError(f"长度检查格式无效: {raw}")
    path, expectation = raw.split("=", 1)
    length_text, unit = expectation.rsplit(":", 1)
    unit = unit.strip()
    if unit not in {"chars", "utf8-bytes"}:
        raise ValueError(f"长度单位无效: {unit}")
    return path.strip(), int(length_text), unit


def strip_schema_root(path: str, root_name: str) -> str:
    prefix = f"{root_name}." if root_name else ""
    return path[len(prefix) :] if prefix and path.casefold().startswith(prefix.casefold()) else path


def measured_length(value: Any, unit: str) -> int:
    text = "" if value is None else str(value)
    return len(text) if unit == "chars" else len(text.encode("utf-8"))


def validate(
    document: dict[str, Any],
    raw_checks: list[str],
    raw_expected_missing: list[str] | None = None,
    raw_expected_additional: list[str] | None = None,
) -> ValidationResult:
    result = ValidationResult()
    payload = parse_serialized(document.get("payload"))
    struct = document.get("reqParamStruct")
    if not isinstance(struct, list) or not struct:
        raise ValueError("reqParamStruct 必须是非空数组")

    root = struct
    schema_root_name = ""
    if len(struct) == 1 and isinstance(struct[0], dict):
        schema_root_name = str(struct[0].get("name", "")).strip()
        children = struct[0].get("children")
        root = children if isinstance(children, list) else []
    if not isinstance(payload, (dict, ET.Element)):
        raise ValueError("payload 必须是 JSON 对象或 XML 文本")
    validate_nodes(root, payload, "", result)

    document_expected_missing = document.get("expectedMissingPaths", [])
    if not isinstance(document_expected_missing, list) or any(
        not isinstance(path, str) for path in document_expected_missing
    ):
        raise ValueError("expectedMissingPaths 必须是字符串数组")
    expected_missing = {
        canonical_schema_path(strip_schema_root(path.strip(), schema_root_name))
        for path in [*document_expected_missing, *(raw_expected_missing or [])]
        if path.strip()
    }
    schema_paths = collect_schema_paths(root)
    result.unknown_expected_missing_paths = sorted(expected_missing - schema_paths)

    missing_by_schema_path: dict[str, list[str]] = {}
    for path in result.missing_paths:
        missing_by_schema_path.setdefault(canonical_schema_path(path), []).append(path)
    present_schema_paths = {canonical_schema_path(path) for path in result.present_paths}
    for path in sorted(result.missing_paths):
        if canonical_schema_path(path) in expected_missing:
            result.allowed_missing_paths.append(path)
        else:
            result.unexpected_missing_paths.append(path)
    result.expected_missing_but_present_paths = sorted(
        path
        for path in expected_missing & schema_paths
        if path in present_schema_paths or path not in missing_by_schema_path
    )

    # 新增字段白名单只能来自案例输入；先验证声明路径，再和实际额外字段逐项对照。
    document_expected_additional = document.get("expectedAdditionalPaths", [])
    if not isinstance(document_expected_additional, list) or any(
        not isinstance(path, str) for path in document_expected_additional
    ):
        raise ValueError("expectedAdditionalPaths 必须是字符串数组")
    expected_additional = {
        canonical_schema_path(strip_schema_root(path.strip(), schema_root_name))
        for path in [*document_expected_additional, *(raw_expected_additional or [])]
        if path.strip()
    }
    schema_container_paths = collect_schema_container_paths(root)
    root_name_conflicts = {
        path
        for path in expected_additional
        if schema_root_name and path.casefold() == schema_root_name.casefold()
    }
    result.invalid_expected_additional_paths = sorted(
        path
        for path in expected_additional
        if path in schema_paths
        or path in root_name_conflicts
        or ("." in path and path.rsplit(".", 1)[0] not in schema_container_paths)
        or not path
        or any(not segment for segment in path.split("."))
    )
    valid_expected_additional = expected_additional - set(result.invalid_expected_additional_paths)
    for path in sorted(result.extra_paths):
        if canonical_schema_path(path) in valid_expected_additional:
            result.allowed_additional_paths.append(path)
        else:
            result.unexpected_additional_paths.append(path)

    # 对数组父节点展开实际下标，确保每个已生成的数组元素都包含案例要求的新增字段。
    extra_path_set = set(result.extra_paths)
    for declared_path in sorted(valid_expected_additional):
        parent_path, _, field_name = declared_path.rpartition(".")
        matching_parents = sorted(
            path
            for path in set(result.visited_container_paths)
            if canonical_schema_path(path) == parent_path
        )
        if not matching_parents:
            result.expected_additional_but_missing_paths.append(declared_path)
            continue
        for actual_parent in matching_parents:
            actual_path = join_path(actual_parent, field_name)
            if actual_path not in extra_path_set:
                result.expected_additional_but_missing_paths.append(actual_path)

    for raw in raw_checks:
        path, expected, unit = parse_length_check(raw)
        path = strip_schema_root(path, schema_root_name)
        value = result.leaf_values.get(path)
        actual = measured_length(value, unit) if path in result.leaf_values else None
        result.length_checks.append(
            {
                "path": path,
                "expected": expected,
                "actual": actual,
                "unit": unit,
                "passed": actual == expected,
            }
        )
    return result


def main() -> int:
    args = parse_args()
    raw_input = sys.stdin.read() if args.input == "-" else Path(args.input).read_text(encoding="utf-8")
    document = json.loads(raw_input)
    if not isinstance(document, dict):
        raise ValueError("输入文件根节点必须是对象")
    result = validate(
        document,
        args.length_check,
        args.expected_missing_field,
        args.expected_additional_field,
    )
    output = json.dumps(
        {
            "passed": result.passed,
            "missingPaths": result.missing_paths,
            "allowedMissingPaths": result.allowed_missing_paths,
            "unexpectedMissingPaths": result.unexpected_missing_paths,
            "expectedMissingButPresentPaths": result.expected_missing_but_present_paths,
            "unknownExpectedMissingPaths": result.unknown_expected_missing_paths,
            "extraPaths": result.extra_paths,
            "allowedAdditionalPaths": result.allowed_additional_paths,
            "unexpectedAdditionalPaths": result.unexpected_additional_paths,
            "expectedAdditionalButMissingPaths": result.expected_additional_but_missing_paths,
            "invalidExpectedAdditionalPaths": result.invalid_expected_additional_paths,
            "nullPaths": result.null_paths,
            "invalidContainers": result.invalid_containers,
            "lengthChecks": result.length_checks,
        },
        ensure_ascii=False,
        indent=2,
    )
    print(output)
    return 0 if result.passed else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"payload validation failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
