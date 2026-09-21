#!/usr/bin/env python3
"""确定性过滤、分类并排序 TCDS 返回的 NIT 脚本。"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
import unicodedata
from collections import OrderedDict
from pathlib import Path
from typing import Any, Iterable


DATE_PATTERN = re.compile(r"(?<!\d)(20\d{6})(?!\d)")
VERSION_PATTERN = re.compile(r"^(20\d{2})年(1[0-2]|[1-9])月$")
TOKEN_PATTERN = re.compile(r"[a-z0-9]+|[\u3400-\u9fff]+", re.IGNORECASE)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        default="-",
        help="TCDS 原始 JSON；使用 - 从 stdin 读取，或传入明确的非临时文件路径",
    )
    parser.add_argument("--identifier", action="append", default=[], help="接口 ID 或英文名，可重复")
    parser.add_argument("--design-file", action="append", default=[], help="已评审案例 Markdown，可重复")
    parser.add_argument("--design-text", action="append", default=[], help="额外设计文本，可重复")
    parser.add_argument("--workspace-value", action="append", default=[], help="工作空间版本或路径，可重复")
    return parser.parse_args()


def compact_text(value: Any) -> str:
    normalized = unicodedata.normalize("NFKC", str(value or "")).casefold()
    return "".join(ch for ch in normalized if ch.isalnum())


def tokens(value: Any) -> set[str]:
    normalized = unicodedata.normalize("NFKC", str(value or "")).casefold()
    return {token for token in TOKEN_PATTERN.findall(normalized) if token}


def bigrams(value: Any) -> set[str]:
    compact = compact_text(value)
    if len(compact) < 2:
        return {compact} if compact else set()
    return {compact[index : index + 2] for index in range(len(compact) - 1)}


def overlap(left: set[str], right: set[str]) -> float:
    union = left | right
    return len(left & right) / len(union) if union else 0.0


def pair_score(candidate: str, target: str) -> int:
    left = compact_text(candidate)
    right = compact_text(target)
    if not left or not right:
        return 0
    if left == right:
        return 1000
    if min(len(left), len(right)) >= 3 and (left in right or right in left):
        return 650 + int(100 * min(len(left), len(right)) / max(len(left), len(right)))

    token_overlap = overlap(tokens(candidate), tokens(target))
    bigram_overlap = overlap(bigrams(candidate), bigrams(target))
    if token_overlap < 0.25 and bigram_overlap < 0.25:
        return 0
    return int(token_overlap * 300 + bigram_overlap * 250)


def relevance_score(candidate_parts: Iterable[str], design_texts: list[str], identifiers: list[str]) -> int:
    parts = [part for part in candidate_parts if part]
    best = 0
    for part in parts:
        for design in design_texts:
            best = max(best, pair_score(part, design))

    combined = " ".join(parts)
    identifier_bonus = 0
    compact_combined = compact_text(combined)
    for identifier in identifiers:
        normalized = compact_text(identifier)
        if normalized and normalized in compact_combined:
            identifier_bonus = max(identifier_bonus, 250)
    return best + identifier_bonus


def normalize_script_id(value: Any) -> str:
    script_id = str(value or "").strip()
    if "," in script_id:
        prefix, remainder = script_id.split(",", 1)
        if prefix.strip().casefold() == "nit":
            script_id = remainder.strip()
    return script_id


def split_script_id(script_id: str) -> tuple[str, str, str] | None:
    parts = script_id.split("##", 2)
    if len(parts) != 3 or any(not part.strip() for part in parts[:2]):
        return None
    return tuple(part.strip() for part in parts)  # type: ignore[return-value]


def tc_file_candidates(path_id: str) -> dict[str, Any]:
    segments = [segment for segment in path_id.split(".") if segment]
    tc_indexes = [index for index, segment in enumerate(segments) if segment.casefold().endswith("tc")]
    if not tc_indexes:
        return {
            "java": "",
            "xlsGlob": "",
            "recursiveJavaGlob": "",
            "recursiveXlsGlob": "",
            "baseName": "",
        }
    tc_index = tc_indexes[-1]
    base_name = segments[tc_index]
    relative = "/".join(segments[: tc_index + 1])
    parent = "/".join(segments[:tc_index])
    return {
        "java": f"{relative}.java",
        "xlsGlob": f"{parent + '/' if parent else ''}{base_name}_*.xls",
        "recursiveJavaGlob": f"**/{base_name}.java",
        "recursiveXlsGlob": f"**/{base_name}_*.xls",
        "baseName": base_name,
    }


def derive_version(values: Iterable[str]) -> str | None:
    """按调用方传入的上下文优先级选取第一个可用版本。"""
    for value in values:
        text = str(value or "")
        explicit = [match.group(0) for match in re.finditer(r"20\d{2}年(?:1[0-2]|[1-9])月", text)]
        if explicit:
            candidate = explicit[0]
            return candidate if VERSION_PATTERN.fullmatch(candidate) else None
        dated: list[str] = []
        for raw in DATE_PATTERN.findall(text):
            try:
                parsed = dt.datetime.strptime(raw, "%Y%m%d").date()
            except ValueError:
                continue
            dated.append(f"{parsed.year}年{parsed.month}月")
        if dated:
            return dated[-1]
    return None


def read_design_texts(paths: list[str], inline: list[str]) -> list[str]:
    result = [value for value in inline if value and value.strip()]
    for raw_path in paths:
        path = Path(raw_path)
        result.append(path.read_text(encoding="utf-8"))
    return result


def classify(payload: Any, design_texts: list[str], identifiers: list[str], workspace_values: list[str]) -> dict[str, Any]:
    records = payload.get("data") if isinstance(payload, dict) else payload
    if not isinstance(records, list):
        raise ValueError("TCDS 响应 data 必须是数组")

    nit_scripts: list[dict[str, Any]] = []
    invalid: list[dict[str, Any]] = []
    for original_order, item in enumerate(records):
        if not isinstance(item, dict) or str(item.get("scriptType", "")).strip().casefold() != "nit":
            continue
        script_id = normalize_script_id(item.get("scriptId"))
        parts = split_script_id(script_id)
        if parts is None:
            invalid.append({"scriptId": script_id, "originalOrder": original_order})
            continue
        first, middle, third = parts
        nit_scripts.append(
            {
                "scriptId": script_id,
                "first": first,
                "middle": middle,
                "third": third,
                "originalOrder": original_order,
                "kind": "TC" if middle.casefold().endswith("tc") else "INTEGRATED",
            }
        )

    grouped: OrderedDict[str, dict[str, Any]] = OrderedDict()
    integrated: list[dict[str, Any]] = []
    for script in nit_scripts:
        if script["kind"] == "TC":
            group = grouped.setdefault(
                script["middle"],
                {
                    "tcName": script["middle"],
                    "originalOrder": script["originalOrder"],
                    "scriptIds": [],
                    "pathCandidates": [],
                },
            )
            group["scriptIds"].append(script["scriptId"])
            if script["first"] not in [candidate["pathId"] for candidate in group["pathCandidates"]]:
                group["pathCandidates"].append(
                    {"pathId": script["first"], **tc_file_candidates(script["first"])}
                )
        else:
            score = relevance_score(
                [script["third"], script["middle"], script["first"].split(".")[-1]],
                design_texts,
                identifiers,
            )
            integrated.append(
                {
                    "scriptId": script["scriptId"],
                    "caseName": script["third"],
                    "score": score,
                    "originalOrder": script["originalOrder"],
                }
            )

    tc_groups = list(grouped.values())
    for group in tc_groups:
        group["score"] = relevance_score(
            [group["tcName"]] + [candidate["baseName"] for candidate in group["pathCandidates"]],
            design_texts,
            identifiers,
        )
    tc_groups.sort(key=lambda item: (-item["score"], item["originalOrder"]))
    integrated.sort(key=lambda item: (-item["score"], item["originalOrder"]))

    deduplicated: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in integrated:
        if item["scriptId"] in seen:
            continue
        seen.add(item["scriptId"])
        deduplicated.append(item)

    required_reference_kinds: list[str] = []
    if tc_groups:
        required_reference_kinds.append("TC")
    if deduplicated:
        required_reference_kinds.append("INTEGRATED")

    return {
        "nitScriptCount": len(nit_scripts),
        "nitScriptIds": [item["scriptId"] for item in nit_scripts],
        "invalidScriptIds": invalid,
        "tcGroups": tc_groups,
        "integratedScripts": deduplicated,
        "integratedTop10": [item["scriptId"] for item in deduplicated[:10]],
        "requiredReferenceKinds": required_reference_kinds,
        "derivedVersion": derive_version(workspace_values),
    }


def main() -> int:
    args = parse_args()
    raw_input = sys.stdin.read() if args.input == "-" else Path(args.input).read_text(encoding="utf-8")
    payload = json.loads(raw_input)
    if isinstance(payload, dict) and payload.get("code") not in (None, 0):
        raise ValueError(f"TCDS 返回失败 code={payload.get('code')}")
    result = classify(
        payload,
        read_design_texts(args.design_file, args.design_text),
        [value for value in args.identifier if value and value.strip()],
        args.workspace_value,
    )
    output = json.dumps(result, ensure_ascii=False, indent=2)
    print(output)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"reference ranking failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
