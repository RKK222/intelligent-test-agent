#!/usr/bin/env python3
"""校验请求、数据准备和断言是否同时参考全部必需来源。"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


DIMENSIONS = ("request", "dataPreparations", "assertions")
ACTIVE_STATUSES = {"ADOPTED", "COMPLEMENTED", "CROSS_VALIDATED", "CONFLICT_RESOLVED"}


@dataclass
class ValidationResult:
    issues: list[str] = field(default_factory=list)

    @property
    def passed(self) -> bool:
        return not self.issues


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        default="-",
        help="包含来源内容和来源贡献台账的 JSON；使用 - 从 stdin 读取",
    )
    return parser.parse_args()


def validate(document: dict[str, Any]) -> ValidationResult:
    result = ValidationResult()
    required = document.get("requiredReferenceKinds")
    source_content = document.get("sourceContent")
    contributions = document.get("sourceContributions")
    if not isinstance(required, list) or not all(isinstance(item, str) for item in required):
        raise ValueError("requiredReferenceKinds 必须是字符串数组")
    if not isinstance(source_content, dict) or not isinstance(contributions, dict):
        raise ValueError("sourceContent 和 sourceContributions 必须是对象")

    active_counts = {source: 0 for source in required}
    for dimension in DIMENSIONS:
        dimension_contributions = contributions.get(dimension)
        if not isinstance(dimension_contributions, dict):
            result.issues.append(f"sourceContributions.{dimension}:missing")
            continue
        for source in required:
            content_by_dimension = source_content.get(source)
            if not isinstance(content_by_dimension, dict):
                result.issues.append(f"sourceContent.{source}:missing")
                continue
            has_content = content_by_dimension.get(dimension)
            if not isinstance(has_content, bool):
                result.issues.append(f"sourceContent.{source}.{dimension}:invalid")
                continue

            contribution = dimension_contributions.get(source)
            if not isinstance(contribution, dict):
                result.issues.append(f"sourceContributions.{dimension}.{source}:missing")
                continue
            status = contribution.get("status")
            evidence = contribution.get("evidence")
            if not isinstance(evidence, list) or not all(
                isinstance(item, str) and item.strip() for item in evidence
            ):
                result.issues.append(f"sourceContributions.{dimension}.{source}.evidence:invalid")
                continue

            if has_content:
                if status not in ACTIVE_STATUSES:
                    result.issues.append(
                        f"sourceContributions.{dimension}.{source}.status:not_referenced"
                    )
                    continue
                if not evidence:
                    result.issues.append(
                        f"sourceContributions.{dimension}.{source}.evidence:empty"
                    )
                    continue
                active_counts[source] += 1
            elif status != "NO_CONTENT":
                result.issues.append(
                    f"sourceContributions.{dimension}.{source}.status:expected_no_content"
                )

    if {"TC", "INTEGRATED"}.issubset(set(required)):
        for source in ("TC", "INTEGRATED"):
            if active_counts.get(source, 0) == 0:
                result.issues.append(f"{source}:no_active_contribution")
    return result


def main() -> int:
    args = parse_args()
    raw_input = sys.stdin.read() if args.input == "-" else Path(args.input).read_text(encoding="utf-8")
    document = json.loads(raw_input)
    if not isinstance(document, dict):
        raise ValueError("输入文件根节点必须是对象")
    result = validate(document)
    output = json.dumps(
        {"passed": result.passed, "issues": result.issues},
        ensure_ascii=False,
        indent=2,
    )
    print(output)
    return 0 if result.passed else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"reference contribution validation failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
