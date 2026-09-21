#!/usr/bin/env python3
"""校验 042-测试执行只保留执行前基线与正式生成文件。"""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable


FORBIDDEN_DIRECTORY_NAMES = {".reference-work", ".tmp"}
FORBIDDEN_CLEANUP_FILES = {"clean-up.json", "cleanup.json"}


@dataclass
class CleanupValidationResult:
    passed: bool
    residual_paths: list[str] = field(default_factory=list)
    unexpected_output_entries: list[str] = field(default_factory=list)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-target", required=True, help="042-测试执行目录")
    parser.add_argument(
        "--temporary-run-directory",
        help="可选；与 042-测试执行同级 .tmp 下的本次 api-automation-<runId> 目录",
    )
    parser.add_argument(
        "--temporary-root-created",
        action="store_true",
        help="本次运行创建了同级 .tmp 根；若清理后为空则该根也必须不存在",
    )
    parser.add_argument("--baseline-entry", action="append", default=[], help="执行前 042 已有相对路径")
    parser.add_argument("--generated-file", action="append", default=[], help="本次正式生成文件的相对路径")
    return parser.parse_args()


def normalize_relative_entry(value: str, label: str) -> str:
    normalized = value.replace("\\", "/").strip().strip("/")
    path = Path(normalized)
    if not normalized or path.is_absolute() or ".." in path.parts:
        raise ValueError(f"{label} 必须是 042-测试执行内的相对路径: {value}")
    return path.as_posix()


def collect_entries(output_target: Path) -> set[str]:
    return {
        path.relative_to(output_target).as_posix()
        for path in output_target.rglob("*")
    }


def allowed_entries(baseline_entries: Iterable[str], generated_files: Iterable[str]) -> set[str]:
    allowed = {
        normalize_relative_entry(entry, "baselineEntry")
        for entry in baseline_entries
    }
    for value in generated_files:
        entry = normalize_relative_entry(value, "generatedFile")
        allowed.add(entry)
        parent = Path(entry).parent
        while parent != Path("."):
            allowed.add(parent.as_posix())
            parent = parent.parent
    return allowed


def validate_temporary_run_directory(output_target: Path, temporary_run_directory: Path) -> tuple[Path, Path]:
    resolved_run = temporary_run_directory.resolve(strict=False)
    temporary_root = resolved_run.parent
    if temporary_root.name != ".tmp" or temporary_root.parent != output_target.parent:
        raise ValueError("temporaryRunDirectory 必须是 042-测试执行同级 .tmp 下的独立一级目录")
    if not resolved_run.name.startswith("api-automation-"):
        raise ValueError("temporaryRunDirectory 名称必须以 api-automation- 开头")
    return resolved_run, temporary_root


def validate(
    output_target: Path,
    temporary_run_directory: Path | None = None,
    baseline_entries: Iterable[str] = (),
    generated_files: Iterable[str] = (),
    temporary_root_created: bool = False,
) -> CleanupValidationResult:
    resolved_target = output_target.resolve(strict=True)
    if not resolved_target.is_dir():
        raise ValueError("outputTarget 必须是目录")

    current_entries = collect_entries(resolved_target)
    residual_paths = sorted(
        entry
        for entry in current_entries
        if any(part in FORBIDDEN_DIRECTORY_NAMES for part in Path(entry).parts)
        or Path(entry).name.casefold() in FORBIDDEN_CLEANUP_FILES
    )

    temporary_root: Path | None = None
    if temporary_run_directory is not None:
        resolved_run, temporary_root = validate_temporary_run_directory(
            resolved_target, temporary_run_directory
        )
        if os.path.lexists(resolved_run):
            residual_paths.append(resolved_run.relative_to(resolved_target.parent).as_posix())

    if temporary_root_created and temporary_root is None:
        raise ValueError("temporaryRootCreated=true 时必须提供 temporaryRunDirectory")
    if temporary_root_created and temporary_root is not None and temporary_root.exists():
        try:
            next(temporary_root.iterdir())
        except StopIteration:
            residual_paths.append(temporary_root.relative_to(resolved_target.parent).as_posix())

    allowed = allowed_entries(baseline_entries, generated_files)
    unexpected = sorted(current_entries - allowed)
    residual_paths = sorted(set(residual_paths))
    return CleanupValidationResult(
        passed=not residual_paths and not unexpected,
        residual_paths=residual_paths,
        unexpected_output_entries=unexpected,
    )


def main() -> int:
    args = parse_args()
    result = validate(
        Path(args.output_target),
        Path(args.temporary_run_directory) if args.temporary_run_directory else None,
        args.baseline_entry,
        args.generated_file,
        args.temporary_root_created,
    )
    print(
        json.dumps(
            {
                "passed": result.passed,
                "residualPaths": result.residual_paths,
                "unexpectedOutputEntries": result.unexpected_output_entries,
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0 if result.passed else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as exc:
        print(f"temporary workspace validation failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
