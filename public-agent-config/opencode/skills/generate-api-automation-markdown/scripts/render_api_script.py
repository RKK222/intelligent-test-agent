#!/usr/bin/env python3
"""使用当前模板组和结构化 JSON 确定性渲染完整接口自动化脚本。"""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import re
import sys
from pathlib import Path
from typing import Any, Mapping


PLACEHOLDER_PATTERN = re.compile(r"{{\s*([A-Za-z][A-Za-z0-9_]*)\s*}}")
REQUEST_PAYLOAD_PATTERN = re.compile(
    r"(?ms)^##\s+3\.[^\r\n]*\r?\n\s*```[^\r\n]*\r?\n(?P<payload>.*?)\r?\n```"
)
MAIN_TEMPLATE = "api-script-template.md"
PARTIALS = {
    "dependency_table": "partials/dependency-table-section.md",
    "dependency_sql": "partials/dependency-sql-section.md",
    "response_assertion_row": "partials/response-assertion-row.md",
    "database_assertion_container": "partials/database-assertion-container.md",
    "database_assertion_section": "partials/database-assertion-section.md",
    "database_assertion_row": "partials/database-assertion-row.md",
}
SCALAR_FIELDS = (
    "caseName",
    "testType",
    "transactionType",
    "testPoint",
    "designMethod",
    "coverageDimension",
    "caseDescription",
    "apiId",
    "apiName",
    "apiUrl",
    "payloadCodeBlockLanguage",
    "requestPayload",
)


def read_bytes(path: Path) -> bytes:
    return path.read_bytes()


def read_text(path: Path) -> str:
    return read_bytes(path).decode("utf-8")


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(read_bytes(path))


def template_root_for_script(script_path: Path | None = None) -> Path:
    source = script_path or Path(__file__)
    return source.resolve().parents[1] / "templates"


def template_hashes(template_root: Path) -> dict[str, str]:
    return {
        path.relative_to(template_root).as_posix(): sha256_file(path)
        for path in sorted(template_root.rglob("*.md"), key=lambda item: item.as_posix())
    }


def load_payload_validator():
    """加载同一公共配置中的结构校验器，避免渲染未经 reqParamStruct 校验的报文。"""
    validator_path = (
        Path(__file__).resolve().parents[2]
        / "resolve-api-automation-references"
        / "scripts"
        / "validate_generated_payload.py"
    )
    spec = importlib.util.spec_from_file_location("api_payload_structure_validator", validator_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载请求结构校验器: {validator_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def validate_request_payload(values: Mapping[str, Any]) -> None:
    """存在平台规范结构时，在正式 Markdown 渲染前拒绝缺失、额外或空指针字段。"""
    struct = values.get("canonicalReqParamStruct")
    if struct in (None, []):
        return
    if not isinstance(struct, list):
        raise ValueError("values.canonicalReqParamStruct 必须是数组")
    expected_missing = values.get("expectedMissingPaths", [])
    if not isinstance(expected_missing, list) or any(not isinstance(path, str) for path in expected_missing):
        raise ValueError("values.expectedMissingPaths 必须是字符串数组")
    legacy_additional = values.get("expectedAdditionalPaths", [])
    if legacy_additional not in (None, []):
        raise ValueError("reqParamStruct 是字段白名单，不再允许 expectedAdditionalPaths")
    validator = load_payload_validator()
    result = validator.validate(
        {
            "payload": values.get("requestPayload"),
            "reqParamStruct": struct,
            "expectedMissingPaths": expected_missing,
        },
        [],
    )
    if not result.passed:
        issues = {
            "unexpectedMissingPaths": result.unexpected_missing_paths,
            "expectedMissingButPresentPaths": result.expected_missing_but_present_paths,
            "unknownExpectedMissingPaths": result.unknown_expected_missing_paths,
            "unexpectedAdditionalPaths": result.unexpected_additional_paths,
            "nullPaths": result.null_paths,
            "invalidContainers": result.invalid_containers,
        }
        raise ValueError(
            "requestPayload 与 canonicalReqParamStruct 不一致: "
            + json.dumps(issues, ensure_ascii=False, separators=(",", ":"))
        )


def extract_rendered_request_payload(rendered: str) -> str:
    """从正式 Markdown 第 3 章中提取唯一请求报文代码块。"""
    matches = list(REQUEST_PAYLOAD_PATTERN.finditer(rendered))
    if len(matches) != 1:
        raise ValueError("正式 Markdown 必须且只能包含一个第 3 章请求报文代码块")
    return matches[0].group("payload")


def validate_rendered_request_payload(rendered: str, values: Mapping[str, Any]) -> None:
    """复验正式 Markdown 中的实际报文，禁止渲染后绕过结构白名单。"""
    actual_payload = extract_rendered_request_payload(rendered)
    expected_payload = required_string(values, "requestPayload")
    if actual_payload != expected_payload:
        raise ValueError("正式 Markdown 中的请求报文与 values.requestPayload 不一致")
    actual_values = dict(values)
    actual_values["requestPayload"] = actual_payload
    validate_request_payload(actual_values)


def render_template(template: str, values: Mapping[str, str], template_name: str) -> str:
    required = set(PLACEHOLDER_PATTERN.findall(template))
    missing = sorted(required - set(values))
    if missing:
        raise ValueError(f"模板 {template_name} 缺少渲染值: {', '.join(missing)}")

    def replace(match: re.Match[str]) -> str:
        return values[match.group(1)]

    return PLACEHOLDER_PATTERN.sub(replace, template)


def require_object(value: Any, path: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{path} 必须是对象")
    return value


def require_list(value: Any, path: str) -> list[Any]:
    if not isinstance(value, list):
        raise ValueError(f"{path} 必须是数组")
    return value


def require_string(value: Any, path: str) -> str:
    if not isinstance(value, str):
        raise ValueError(f"{path} 必须是字符串")
    return value


def required_string(document: Mapping[str, Any], key: str, path: str = "values") -> str:
    if key not in document:
        raise ValueError(f"{path}.{key} 缺失")
    return require_string(document[key], f"{path}.{key}")


def markdown_cell(value: Any, path: str) -> str:
    text = require_string(value, path)
    return text.replace("|", r"\|").replace("\r\n", "<br>").replace("\r", "<br>").replace("\n", "<br>")


def inline_code_label(value: Any, path: str) -> str:
    text = require_string(value, path)
    if "`" in text or "\r" in text or "\n" in text:
        raise ValueError(f"{path} 不能包含反引号或换行")
    return text


def render_dependency_sections(values: Mapping[str, Any], template_root: Path) -> str:
    sections: list[str] = []
    dependencies = require_list(values.get("dependencies", []), "values.dependencies")
    for index, raw_dependency in enumerate(dependencies, start=1):
        dependency = require_object(raw_dependency, f"values.dependencies[{index - 1}]")
        kind = required_string(dependency, "kind", f"values.dependencies[{index - 1}]").casefold()
        section_number = f"4.{index}"
        if kind == "sql":
            sections.append(
                render_template(
                    read_text(template_root / PARTIALS["dependency_sql"]),
                    {
                        "sectionNumber": section_number,
                        "databaseName": inline_code_label(
                            dependency.get("databaseName"),
                            f"values.dependencies[{index - 1}].databaseName",
                        ),
                        "sql": required_string(dependency, "sql", f"values.dependencies[{index - 1}]"),
                    },
                    PARTIALS["dependency_sql"],
                ).rstrip("\r\n")
            )
            continue
        if kind != "table":
            raise ValueError(f"values.dependencies[{index - 1}].kind 仅支持 table/sql")

        columns = require_list(dependency.get("columns"), f"values.dependencies[{index - 1}].columns")
        if not columns:
            raise ValueError(f"values.dependencies[{index - 1}].columns 不能为空")
        column_text = [markdown_cell(item, f"values.dependencies[{index - 1}].columns") for item in columns]
        rows = require_list(dependency.get("rows"), f"values.dependencies[{index - 1}].rows")
        rendered_rows: list[str] = []
        for row_index, raw_row in enumerate(rows):
            row = require_list(raw_row, f"values.dependencies[{index - 1}].rows[{row_index}]")
            if len(row) != len(column_text) + 1:
                raise ValueError(
                    f"values.dependencies[{index - 1}].rows[{row_index}] 必须包含 "
                    f"{len(column_text)} 个数据列和 1 个说明列"
                )
            rendered_rows.append(
                "| "
                + " | ".join(
                    markdown_cell(cell, f"values.dependencies[{index - 1}].rows[{row_index}]")
                    for cell in row
                )
                + " |"
            )
        sections.append(
            render_template(
                read_text(template_root / PARTIALS["dependency_table"]),
                {
                    "sectionNumber": section_number,
                    "tableName": inline_code_label(
                        dependency.get("tableName"), f"values.dependencies[{index - 1}].tableName"
                    ),
                    "columnHeaders": " | ".join(column_text),
                    "columnSeparators": " | ".join("-" * max(4, len(item)) for item in column_text),
                    "tableRows": "\n".join(rendered_rows),
                },
                PARTIALS["dependency_table"],
            ).rstrip("\r\n")
        )
    return "\n\n".join(sections)


def render_response_assertions(values: Mapping[str, Any], template_root: Path) -> str:
    assertions = require_list(values.get("responseAssertions"), "values.responseAssertions")
    if not assertions:
        raise ValueError("values.responseAssertions 不能为空")
    row_template = read_text(template_root / PARTIALS["response_assertion_row"])
    rows: list[str] = []
    for index, raw_assertion in enumerate(assertions):
        assertion = require_object(raw_assertion, f"values.responseAssertions[{index}]")
        rows.append(
            render_template(
                row_template,
                {
                    "fieldPath": markdown_cell(
                        assertion.get("fieldPath"), f"values.responseAssertions[{index}].fieldPath"
                    ),
                    "expectedValue": markdown_cell(
                        assertion.get("expectedValue"), f"values.responseAssertions[{index}].expectedValue"
                    ),
                    "assertionMethod": markdown_cell(
                        assertion.get("assertionMethod"), f"values.responseAssertions[{index}].assertionMethod"
                    ),
                    "assertDescription": markdown_cell(
                        assertion.get("assertDescription"),
                        f"values.responseAssertions[{index}].assertDescription",
                    ),
                },
                PARTIALS["response_assertion_row"],
            ).rstrip("\r\n")
        )
    return "\n".join(rows)


def render_database_assertions(values: Mapping[str, Any], template_root: Path) -> str:
    databases = require_list(values.get("databaseAssertions", []), "values.databaseAssertions")
    if not databases:
        return ""
    section_template = read_text(template_root / PARTIALS["database_assertion_section"])
    row_template = read_text(template_root / PARTIALS["database_assertion_row"])
    sections: list[str] = []
    for index, raw_database in enumerate(databases, start=1):
        database = require_object(raw_database, f"values.databaseAssertions[{index - 1}]")
        assertions = require_list(
            database.get("assertions"), f"values.databaseAssertions[{index - 1}].assertions"
        )
        if not assertions:
            raise ValueError(f"values.databaseAssertions[{index - 1}].assertions 不能为空")
        rows: list[str] = []
        for row_index, raw_assertion in enumerate(assertions):
            assertion = require_object(
                raw_assertion, f"values.databaseAssertions[{index - 1}].assertions[{row_index}]"
            )
            row_path = f"values.databaseAssertions[{index - 1}].assertions[{row_index}]"
            rows.append(
                render_template(
                    row_template,
                    {
                        "fieldName": markdown_cell(assertion.get("fieldName"), f"{row_path}.fieldName"),
                        "expectedValue": markdown_cell(
                            assertion.get("expectedValue"), f"{row_path}.expectedValue"
                        ),
                        "assertionMethod": markdown_cell(
                            assertion.get("assertionMethod"), f"{row_path}.assertionMethod"
                        ),
                        "assertDescription": markdown_cell(
                            assertion.get("assertDescription"), f"{row_path}.assertDescription"
                        ),
                    },
                    PARTIALS["database_assertion_row"],
                ).rstrip("\r\n")
            )
        sections.append(
            render_template(
                section_template,
                {
                    "sectionNumber": f"5.2.{index}",
                    "tableName": inline_code_label(
                        database.get("tableName"),
                        f"values.databaseAssertions[{index - 1}].tableName",
                    ),
                    "sql": required_string(database, "sql", f"values.databaseAssertions[{index - 1}]"),
                    "assertionRows": "\n".join(rows),
                },
                PARTIALS["database_assertion_section"],
            ).rstrip("\r\n")
        )
    return render_template(
        read_text(template_root / PARTIALS["database_assertion_container"]),
        {"databaseAssertionSections": "\n\n".join(sections)},
        PARTIALS["database_assertion_container"],
    ).rstrip("\r\n")


def render_document(values: Mapping[str, Any], template_root: Path) -> str:
    # 先渲染重复区块，再只通过主模板槽位组装全文，避免代码复制固定章节内容。
    validate_request_payload(values)
    main_values = {
        field: required_string(values, field)
        if field in {"payloadCodeBlockLanguage", "requestPayload"}
        else markdown_cell(values.get(field), f"values.{field}")
        for field in SCALAR_FIELDS
    }
    if not re.fullmatch(r"[A-Za-z0-9_+.-]+", main_values["payloadCodeBlockLanguage"]):
        raise ValueError("values.payloadCodeBlockLanguage 必须是单行代码块语言标识")
    main_values.update(
        {
            "dependencySections": render_dependency_sections(values, template_root),
            "responseAssertionRows": render_response_assertions(values, template_root),
            "databaseAssertionSection": render_database_assertions(values, template_root),
        }
    )
    rendered = render_template(read_text(template_root / MAIN_TEMPLATE), main_values, MAIN_TEMPLATE)
    rendered = rendered if rendered.endswith(("\n", "\r")) else rendered + "\n"
    validate_rendered_request_payload(rendered, values)
    return rendered


def resolve_inside(path: Path, parent: Path, label: str, require_exists: bool = False) -> Path:
    resolved = path.resolve(strict=require_exists)
    try:
        resolved.relative_to(parent.resolve(strict=False))
    except ValueError as exc:
        raise ValueError(f"{label} 必须位于 {parent}") from exc
    return resolved


def validate_output_path(output_path: Path, output_target: Path) -> tuple[Path, Path]:
    resolved_target = output_target.resolve(strict=True)
    resolved_output = resolve_inside(output_path, resolved_target, "output")
    relative_parts = resolved_output.relative_to(resolved_target).parts
    if ".reference-work" in relative_parts or ".tmp" in relative_parts:
        raise ValueError("正式输出不得位于临时目录")
    if resolved_output.suffix.casefold() != ".md":
        raise ValueError("正式输出必须是 Markdown")
    if not resolved_output.parent.exists():
        raise ValueError("正式输出父目录必须已存在")
    return resolved_output, resolved_target


def canonical_values_bytes(values: Mapping[str, Any]) -> bytes:
    return json.dumps(
        values,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def render_to_output(
    values: Mapping[str, Any],
    output_path: Path,
    output_target: Path,
) -> dict[str, Any]:
    resolved_output, resolved_target = validate_output_path(output_path, output_target)
    template_root = template_root_for_script()
    output_bytes = render_document(values, template_root).encode("utf-8")
    resolved_output.write_bytes(output_bytes)
    persisted_output = read_text(resolved_output)
    validate_rendered_request_payload(persisted_output, values)
    if persisted_output.encode("utf-8") != output_bytes:
        raise ValueError("正式 Markdown 写入后内容与渲染结果不一致")

    # 清单仅通过 stdout 返回到 Agent 上下文，不生成 manifest 中间文件。
    return {
        "schemaVersion": 2,
        "rendererSha256": sha256_file(Path(__file__).resolve()),
        "templateHashes": template_hashes(template_root),
        "valuesSha256": sha256_bytes(canonical_values_bytes(values)),
        "outputSha256": sha256_bytes(output_bytes),
        "outputFile": resolved_output.relative_to(resolved_target).as_posix(),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", default="-", help="结构化渲染值 JSON；使用 - 从 stdin 读取")
    parser.add_argument("--output", required=True, help="042-测试执行中的正式 Markdown")
    parser.add_argument("--output-target", required=True, help="042-测试执行目录")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    raw_input = sys.stdin.read() if args.input == "-" else Path(args.input).read_text(encoding="utf-8")
    values = json.loads(raw_input)
    if not isinstance(values, dict):
        raise ValueError("values 根节点必须是对象")
    manifest = render_to_output(values, Path(args.output), Path(args.output_target))
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"template rendering failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
