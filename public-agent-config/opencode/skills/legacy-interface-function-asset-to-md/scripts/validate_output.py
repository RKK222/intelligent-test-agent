#!/usr/bin/env python3
"""Validate generated interface automation Markdown assets."""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Sequence

REQUIRED_HEADINGS = [
    "## 1. 功能信息",
    "## 2. 请求生成模型",
    "### 2.1 场景字段差异",
    "### 2.2 完整请求示例",
    "### 2.3 请求实现观察",
    "## 3. SQL 数据准备与恢复",
    "## 4. Mock 与非 SQL 依赖",
    "## 5. 断言模型",
    "### 5.3 数据库断言",
    "## 6. 差异、缺口与待确认项",
    "## 7. 人工补充",
]
REPORT_REQUIRED_HEADINGS = [
    "## 1. 转换概览",
    "## 2. Excel 提取结果",
    "## 3. 输出接口功能",
    "## 4. 冲突与缺口",
    "## 5. 被排除测试数据",
    "## 6. 输出验证",
    "## 7. 建议确认顺序",
]
FORBIDDEN_PATTERNS = [
    (re.compile(r"\.\.\.|…"), "存在省略号"),
    (re.compile(r"\b同上\b|类似场景|其余字段"), "存在模糊替代描述"),
    (re.compile(r"\$\{field_name\}|\$\{scenario\.[^}]+\}"), "存在通用占位符"),
    (re.compile(r"\{\{[^{}]+\}\}"), "存在未替换模板变量"),
    (re.compile(r"\bvalues\s*\(\s*\)", re.IGNORECASE), "存在空 VALUES"),
    (re.compile(r"来源|SHA-256|文件路径|代码位置|Sheet/行号|证据说明"), "存在禁止输出的来源或证据信息"),
]
FORBIDDEN_HEADINGS = [
    "来源资产与证据",
    "接口调用",
    "测试场景矩阵",
    "公共生成流程",
    "完整公共请求字段",
]
REPORT_FORBIDDEN_PATTERNS = FORBIDDEN_PATTERNS + [
    (
        re.compile(r"输入目录/文件|输出文件|文件/对象|文件/Sheet/行|输出位置|配对依据"),
        "转换报告存在禁止输出的文件、位置或配对信息",
    ),
]


def generated_region(text: str) -> str:
    start = text.find("<!-- GENERATED:START -->")
    end = text.find("<!-- GENERATED:END -->")
    if start < 0 or end < 0 or start >= end:
        return text
    return text[start:end]


def parse_scenario_count(text: str) -> int | None:
    match = re.search(r"^scenario_count:\s*(\d+)\s*$", text, re.MULTILINE)
    return int(match.group(1)) if match else None


def validate_payload(text: str) -> list[str]:
    errors = []
    match = re.search(
        r"### 2\.2 完整请求示例\s*```([^\n]*)\n(.*?)\n```",
        text,
        re.DOTALL,
    )
    if not match:
        return ["缺少完整请求示例代码块"]
    language = match.group(1).strip().lower()
    payload = match.group(2).strip()
    if not payload:
        return ["完整请求示例为空"]
    if language == "json":
        try:
            json.loads(payload)
        except json.JSONDecodeError as exc:
            errors.append(f"完整 JSON 请求无法解析：第 {exc.lineno} 行 {exc.msg}")
    return errors


def validate_db_assertions(text: str) -> list[str]:
    match = re.search(
        r"### 5\.3 数据库断言\s*(.*?)(?=\n## 6\.)",
        text,
        re.DOTALL,
    )
    if not match:
        return ["缺少数据库断言章节内容"]
    lines = [line for line in match.group(1).splitlines() if line.startswith("|")]
    if not lines:
        return ["数据库断言缺少表格"]
    header = [
        cell.strip()
        for cell in re.split(r"(?<!\\)\|", lines[0].strip().strip("|"))
    ]
    errors = []
    if "实际 SQL" not in header:
        errors.append("数据库断言表缺少“实际 SQL”列")
    for forbidden in ("执行状态", "来源"):
        if forbidden in header:
            errors.append(f"数据库断言表不得包含“{forbidden}”列")
    if "实际 SQL" in header:
        sql_index = header.index("实际 SQL")
        for row_number, line in enumerate(lines[2:], start=1):
            cells = [
                cell.strip()
                for cell in re.split(r"(?<!\\)\|", line.strip().strip("|"))
            ]
            if len(cells) <= sql_index or not cells[sql_index].strip("` "):
                errors.append(f"数据库断言第 {row_number} 行缺少实际 SQL")
    return errors


def validate_sql_blocks(text: str) -> list[str]:
    errors = []
    for index, block in enumerate(re.findall(r"```sql\s*\n(.*?)\n```", text, re.DOTALL), start=1):
        if not block.strip():
            errors.append(f"第 {index} 个 SQL 代码块为空")
    return errors


def validate_text(text: str, label: str = "<memory>") -> list[str]:
    errors: list[str] = []
    for marker in (
        "<!-- GENERATED:START -->",
        "<!-- GENERATED:END -->",
        "<!-- MANUAL:START -->",
        "<!-- MANUAL:END -->",
    ):
        if text.count(marker) != 1:
            errors.append(f"{marker} 必须且只能出现一次")
    for heading in REQUIRED_HEADINGS:
        if heading not in text:
            errors.append(f"缺少固定章节：{heading}")
    present_positions = [text.find(heading) for heading in REQUIRED_HEADINGS]
    if all(position >= 0 for position in present_positions):
        if present_positions != sorted(present_positions):
            errors.append("固定章节顺序不符合模板")
    generated = generated_region(text)
    for heading in FORBIDDEN_HEADINGS:
        if re.search(rf"^#+\s+\d+(?:\.\d+)?\.\s+{re.escape(heading)}\s*$", generated, re.MULTILINE):
            errors.append(f"存在已删除章节：{heading}")
    for pattern, message in FORBIDDEN_PATTERNS:
        if pattern.search(generated):
            errors.append(message)
    declared = parse_scenario_count(text)
    if declared is None:
        errors.append("frontmatter 缺少有效 scenario_count")
    errors.extend(validate_payload(generated))
    errors.extend(validate_db_assertions(generated))
    errors.extend(validate_sql_blocks(generated))
    return [f"{label}: {item}" for item in errors]


def validate_report_text(text: str, label: str = "<memory>") -> list[str]:
    errors: list[str] = []
    for heading in REPORT_REQUIRED_HEADINGS:
        if heading not in text:
            errors.append(f"缺少固定章节：{heading}")
    present_positions = [text.find(heading) for heading in REPORT_REQUIRED_HEADINGS]
    if all(position >= 0 for position in present_positions):
        if present_positions != sorted(present_positions):
            errors.append("固定章节顺序不符合模板")
    for pattern, message in REPORT_FORBIDDEN_PATTERNS:
        if pattern.search(text):
            errors.append(message)
    return [f"{label}: {item}" for item in errors]


def validate_document(text: str, label: str = "<memory>") -> list[str]:
    if "存量接口自动化资产转换报告" in text:
        return validate_report_text(text, label)
    return validate_text(text, label)


def validate_output_path(path: Path, workspace_root: Path) -> list[str]:
    expected_dir = workspace_root.resolve() / "docs" / "功能模块" / "待确认"
    errors = []
    if path.resolve().parent != expected_dir:
        errors.append("输出文件必须位于工作空间固定目录 docs/功能模块/待确认")
    if not path.name.endswith(
        ("-接口自动化资产.md", "-资产转换报告.md")
    ):
        errors.append("默认输出只能是接口自动化资产或资产转换报告 Markdown")
    return errors


def self_test() -> int:
    valid = """---
scenario_count: 1
---
<!-- GENERATED:START -->
## 1. 功能信息
## 2. 请求生成模型
### 2.1 场景字段差异
### 2.2 完整请求示例
```json
{"date":"${env.currentDate:yyyyMMdd}","amount":"101"}
```
### 2.3 请求实现观察
无
## 3. SQL 数据准备与恢复
## 4. Mock 与非 SQL 依赖
## 5. 断言模型
### 5.3 数据库断言
| 断言编号 | 场景 ID | 实际 SQL | 实际字段/表达式 | 预期值 | 断言方式 |
| --- | --- | --- | --- | --- | --- |
| DB-001 | 001 | `SELECT state FROM orders WHERE id = 1` | state | SUCCESS | equals |
## 6. 差异、缺口与待确认项
<!-- GENERATED:END -->
<!-- MANUAL:START -->
## 7. 人工补充
<!-- MANUAL:END -->
"""
    if validate_text(valid):
        print("self-test failed: valid fixture rejected", file=sys.stderr)
        return 1
    invalid = valid.replace('"101"', '"${scenario.amount}"')
    if not validate_text(invalid):
        print("self-test failed: invalid fixture accepted", file=sys.stderr)
        return 1
    invalid_db = valid.replace("实际 SQL", "执行状态")
    if not validate_text(invalid_db):
        print("self-test failed: invalid database assertion fixture accepted", file=sys.stderr)
        return 1
    valid_report = """# Batch - 存量接口自动化资产转换报告
## 1. 转换概览
## 2. Excel 提取结果
## 3. 输出接口功能
## 4. 冲突与缺口
## 5. 被排除测试数据
## 6. 输出验证
## 7. 建议确认顺序
"""
    if validate_report_text(valid_report):
        print("self-test failed: valid report fixture rejected", file=sys.stderr)
        return 1
    if not validate_report_text(valid_report + "\n来源：case.xlsx\n"):
        print("self-test failed: invalid report fixture accepted", file=sys.stderr)
        return 1
    workspace = Path("/workspace/project")
    valid_path = workspace / "docs" / "功能模块" / "待确认" / "接口-接口自动化资产.md"
    invalid_path = workspace / "docs" / "支付模块" / "待确认" / "接口-接口自动化资产.md"
    extra_path = workspace / "docs" / "功能模块" / "待确认" / "额外文件.md"
    if validate_output_path(valid_path, workspace):
        print("self-test failed: valid output path rejected", file=sys.stderr)
        return 1
    if not validate_output_path(invalid_path, workspace):
        print("self-test failed: invalid output path accepted", file=sys.stderr)
        return 1
    if not validate_output_path(extra_path, workspace):
        print("self-test failed: extra output file accepted", file=sys.stderr)
        return 1
    print("validate_output.py self-test passed")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate interface automation Markdown assets and conversion reports"
    )
    parser.add_argument("files", nargs="*", help="Markdown files to validate")
    parser.add_argument(
        "--workspace-root",
        help="Validate that output files are under docs/功能模块/待确认",
    )
    parser.add_argument("--self-test", action="store_true", help="Run built-in regression checks")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.self_test:
        return self_test()
    if not args.files:
        print("error: provide Markdown files or --self-test", file=sys.stderr)
        return 64
    all_errors: list[str] = []
    workspace_root = Path(args.workspace_root) if args.workspace_root else None
    for raw in args.files:
        path = Path(raw)
        if workspace_root is not None:
            all_errors.extend(
                f"{path}: {item}"
                for item in validate_output_path(path, workspace_root)
            )
        if not path.is_file():
            all_errors.append(f"{path}: 文件不存在")
            continue
        all_errors.extend(validate_document(path.read_text(encoding="utf-8"), str(path)))
    if all_errors:
        for error in all_errors:
            print(f"[ERROR] {error}", file=sys.stderr)
        return 2
    print(f"[OK] validated {len(args.files)} Markdown output(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
