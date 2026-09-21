#!/usr/bin/env python3
"""逐项校验生成脚本中的数据准备/恢复是否完整保留参考内容。"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


@dataclass
class ValidationResult:
    missing_indexes: list[int] = field(default_factory=list)
    extra_indexes: list[int] = field(default_factory=list)
    changed_paths: list[str] = field(default_factory=list)

    @property
    def passed(self) -> bool:
        return not (self.missing_indexes or self.extra_indexes or self.changed_paths)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        default="-",
        help="包含 referenceDataPreparations 和 generatedDataPreparations 的 JSON；使用 - 从 stdin 读取",
    )
    return parser.parse_args()


def normalize(value: Any) -> Any:
    """只统一跨平台换行，不裁剪 SQL 或折叠任何空白。"""
    if isinstance(value, str):
        return value.replace("\r\n", "\n").replace("\r", "\n")
    if isinstance(value, list):
        return [normalize(item) for item in value]
    if isinstance(value, dict):
        return {str(key): normalize(item) for key, item in value.items()}
    return value


def compare(expected: Any, actual: Any, path: str, result: ValidationResult) -> None:
    if type(expected) is not type(actual):
        result.changed_paths.append(path)
        return
    if isinstance(expected, dict):
        expected_keys = set(expected)
        actual_keys = set(actual)
        for key in sorted(expected_keys - actual_keys):
            result.changed_paths.append(f"{path}.{key}:missing")
        for key in sorted(actual_keys - expected_keys):
            result.changed_paths.append(f"{path}.{key}:extra")
        for key in sorted(expected_keys & actual_keys):
            compare(expected[key], actual[key], f"{path}.{key}", result)
        return
    if isinstance(expected, list):
        if len(expected) != len(actual):
            result.changed_paths.append(f"{path}.length")
        for index, (expected_item, actual_item) in enumerate(zip(expected, actual)):
            compare(expected_item, actual_item, f"{path}[{index}]", result)
        return
    if expected != actual:
        result.changed_paths.append(path)


def validate(document: dict[str, Any]) -> ValidationResult:
    reference = document.get("referenceDataPreparations")
    generated = document.get("generatedDataPreparations")
    if not isinstance(reference, list) or not isinstance(generated, list):
        raise ValueError("referenceDataPreparations 和 generatedDataPreparations 必须是数组")

    expected = normalize(reference)
    actual = normalize(generated)
    result = ValidationResult()
    if len(expected) > len(actual):
        result.missing_indexes.extend(range(len(actual), len(expected)))
    elif len(actual) > len(expected):
        result.extra_indexes.extend(range(len(expected), len(actual)))

    for index, (expected_item, actual_item) in enumerate(zip(expected, actual)):
        compare(expected_item, actual_item, f"dataPreparations[{index}]", result)
    return result


def main() -> int:
    args = parse_args()
    raw_input = sys.stdin.read() if args.input == "-" else Path(args.input).read_text(encoding="utf-8")
    document = json.loads(raw_input)
    if not isinstance(document, dict):
        raise ValueError("输入文件根节点必须是对象")
    result = validate(document)
    output = json.dumps(
        {
            "passed": result.passed,
            "missingIndexes": result.missing_indexes,
            "extraIndexes": result.extra_indexes,
            "changedPaths": result.changed_paths,
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
        print(f"data preparation validation failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
