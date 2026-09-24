#!/usr/bin/env python3
"""依据当前完整模板组和上下文清单验证正式 Markdown 未偏离模板。"""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Mapping


def load_renderer():
    """按绝对文件路径加载同目录渲染器，兼容 Windows 扩展长度路径。"""
    renderer_path = Path(__file__).resolve().with_name("render_api_script.py")
    spec = importlib.util.spec_from_file_location("api_automation_template_renderer", renderer_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载渲染器: {renderer_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


renderer = load_renderer()


@dataclass
class VerificationResult:
    issues: list[str] = field(default_factory=list)

    @property
    def passed(self) -> bool:
        return not self.issues


def validate(
    values: Mapping[str, Any],
    output_path: Path,
    output_target: Path,
    manifest: Mapping[str, Any],
) -> VerificationResult:
    result = VerificationResult()
    resolved_output, resolved_target = renderer.validate_output_path(output_path, output_target)
    template_root = renderer.template_root_for_script(Path(renderer.__file__))
    expected_checks: list[tuple[str, Any, Any]] = [
        ("schema_version_mismatch", manifest.get("schemaVersion"), 2),
        (
            "renderer_hash_mismatch",
            manifest.get("rendererSha256"),
            renderer.sha256_file(Path(renderer.__file__).resolve()),
        ),
        ("template_hashes_mismatch", manifest.get("templateHashes"), renderer.template_hashes(template_root)),
        (
            "values_hash_mismatch",
            manifest.get("valuesSha256"),
            renderer.sha256_bytes(renderer.canonical_values_bytes(values)),
        ),
        ("output_hash_mismatch", manifest.get("outputSha256"), renderer.sha256_file(resolved_output)),
        (
            "output_file_mismatch",
            manifest.get("outputFile"),
            resolved_output.relative_to(resolved_target).as_posix(),
        ),
    ]
    for issue, actual, expected in expected_checks:
        if actual != expected:
            result.issues.append(issue)

    expected_output = renderer.render_document(values, template_root).encode("utf-8")
    actual_output = renderer.read_bytes(resolved_output)
    if actual_output != expected_output:
        result.issues.append("rendered_output_mismatch")
    try:
        renderer.validate_rendered_request_payload(actual_output.decode("utf-8"), values)
    except (UnicodeDecodeError, ValueError):
        result.issues.append("rendered_request_payload_invalid")
    result.issues = list(dict.fromkeys(result.issues))
    return result


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        default="-",
        help='包含 values 和 manifest 的 JSON 对象；使用 - 从 stdin 读取',
    )
    parser.add_argument("--output", required=True, help="042-测试执行中的正式 Markdown")
    parser.add_argument("--output-target", required=True, help="042-测试执行目录")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    raw_input = sys.stdin.read() if args.input == "-" else Path(args.input).read_text(encoding="utf-8")
    document = json.loads(raw_input)
    if not isinstance(document, dict):
        raise ValueError("输入根节点必须是对象")
    values = document.get("values")
    manifest = document.get("manifest")
    if not isinstance(values, dict) or not isinstance(manifest, dict):
        raise ValueError("输入必须包含对象类型的 values 和 manifest")
    result = validate(values, Path(args.output), Path(args.output_target), manifest)
    print(json.dumps({"passed": result.passed, "issues": result.issues}, ensure_ascii=False, indent=2))
    return 0 if result.passed else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError, UnicodeDecodeError) as exc:
        print(f"rendered script verification failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
