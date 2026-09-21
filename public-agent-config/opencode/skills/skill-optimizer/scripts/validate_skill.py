#!/usr/bin/env python3
"""使用 Python 标准库执行可离线运行的 Skill 结构校验。"""

import json
import re
import argparse
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple


ALLOWED_TOP_LEVEL_KEYS = {
    "name",
    "description",
    "license",
    "allowed-tools",
    "metadata",
    "compatibility",
}
FORBIDDEN_NAMES = {
    ".env",
    ".env.local",
    ".DS_Store",
    "id_rsa",
    "id_ed25519",
    "node_modules",
    "__pycache__",
}
PLACEHOLDER_PATTERNS = ("TODO", "TBD", "待补充", "PLACEHOLDER")
BILINGUAL_DESCRIPTION_PATTERN = re.compile(
    r"^[A-Za-z][A-Za-z0-9 &+./_-]*?（[^）\r\n]+）[。；]"
)


def parse_frontmatter(content: str) -> Tuple[Dict[str, str], Dict[str, str], str]:
    """解析顶层标量和 metadata 标量，保持校验器无第三方 YAML 依赖。"""
    match = re.match(r"^---\r?\n(.*?)\r?\n---\r?\n(.*)$", content, re.DOTALL)
    if not match:
        raise ValueError("SKILL.md 缺少完整 YAML frontmatter")

    fields: Dict[str, str] = {}
    metadata: Dict[str, str] = {}
    in_metadata = False
    for line in match.group(1).splitlines():
        if not line or line.lstrip().startswith("#"):
            continue
        if line[0].isspace():
            if in_metadata:
                nested_match = re.match(r"^\s+([A-Za-z0-9_-]+):(?:\s*(.*))?$", line)
                if nested_match:
                    value = (nested_match.group(2) or "").strip().strip("'\"")
                    metadata[nested_match.group(1)] = value
            continue
        key_match = re.match(r"^([A-Za-z0-9_-]+):(?:\s*(.*))?$", line)
        if not key_match:
            raise ValueError(f"无法解析 frontmatter 顶层字段：{line}")
        value = (key_match.group(2) or "").strip().strip("'\"")
        fields[key_match.group(1)] = value
        in_metadata = key_match.group(1) == "metadata"
    return fields, metadata, match.group(2).strip()


def validate(skill_dir: Path, workspace_root: Optional[Path] = None, public_config_root: Optional[Path] = None) -> List[str]:
    """返回全部校验错误，避免用户逐次修复单个问题。"""
    errors: List[str] = []
    if workspace_root is not None and public_config_root is not None:
        errors.append("--workspace-root 与 --public-config-root 互斥")
    if workspace_root is not None:
        expected_parent = workspace_root.expanduser().resolve() / ".opencode" / "skills"
        if skill_dir.parent != expected_parent:
            errors.append(f"技能目录必须位于 {expected_parent}/<skill-name>")
    if public_config_root is not None:
        expected_parent = public_config_root.expanduser().resolve() / "skills"
        if skill_dir.parent != expected_parent:
            errors.append(f"公共配置技能目录必须位于 {expected_parent}/<skill-name>")
    skill_file = skill_dir / "SKILL.md"
    if not skill_file.is_file():
        return ["缺少 SKILL.md"]

    try:
        content = skill_file.read_text(encoding="utf-8")
        fields, metadata, body = parse_frontmatter(content)
    except (OSError, UnicodeError, ValueError) as exc:
        return [str(exc)]

    unknown = sorted(set(fields) - ALLOWED_TOP_LEVEL_KEYS)
    if unknown:
        errors.append(f"frontmatter 包含未知顶层字段：{', '.join(unknown)}")

    name = fields.get("name", "")
    description = fields.get("description", "")
    if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", name):
        errors.append("name 必须为 kebab-case")
    elif len(name) > 64:
        errors.append("name 不能超过 64 个字符")
    elif skill_dir.name != name:
        errors.append(f"目录名 {skill_dir.name} 与 name {name} 不一致")

    if not description:
        errors.append("description 不能为空")
    elif len(description) > 1024:
        errors.append("description 不能超过 1024 个字符")
    elif "<" in description or ">" in description:
        errors.append("description 不能包含尖括号")
    elif not BILINGUAL_DESCRIPTION_PATTERN.match(description):
        errors.append("description 必须以 English（中文）。开头")

    display_name = metadata.get("display-name", "")
    display_name_zh = metadata.get("display-name-zh", "")
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9 &+./_-]*", display_name):
        errors.append("metadata.display-name 必须是非空英文展示名")
    if not display_name_zh or not re.search(r"[\u3400-\u9fff]", display_name_zh):
        errors.append("metadata.display-name-zh 必须包含中文展示名")
    if not metadata.get("source"):
        errors.append("metadata.source 不能为空")

    if not body:
        errors.append("SKILL.md 正文不能为空")

    for path in skill_dir.rglob("*"):
        if path.name in FORBIDDEN_NAMES:
            errors.append(f"包含不应打包的文件或目录：{path.relative_to(skill_dir)}")
        if path.is_symlink():
            errors.append(f"不允许符号链接：{path.relative_to(skill_dir)}")

    eval_file = skill_dir / "evals" / "evals.json"
    if eval_file.exists():
        try:
            payload = json.loads(eval_file.read_text(encoding="utf-8"))
            evals = payload.get("evals", []) if isinstance(payload, dict) else []
            if len(evals) < 3:
                errors.append("evals/evals.json 至少需要 3 个测试场景")
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            errors.append(f"evals/evals.json 无法解析：{exc}")

    for marker in PLACEHOLDER_PATTERNS:
        if marker in content:
            errors.append(f"SKILL.md 仍包含占位标记：{marker}")

    return errors


def main() -> int:
    """校验命令入口。"""
    parser = argparse.ArgumentParser(description="校验 Skill 结构和落点")
    parser.add_argument("skill_directory")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--workspace-root", help="普通应用的 Working directory")
    group.add_argument("--public-config-root", help="公共 Agent 的 opencode 配置根")
    args = parser.parse_args()

    skill_dir = Path(args.skill_directory).expanduser().resolve()
    workspace_root = Path(args.workspace_root).expanduser().resolve() if args.workspace_root else None
    public_config_root = Path(args.public_config_root).expanduser().resolve() if args.public_config_root else None
    errors = validate(skill_dir, workspace_root, public_config_root)
    if errors:
        print("Skill 校验失败：")
        for error in errors:
            print(f"- {error}")
        return 1

    print(f"Skill 校验通过：{skill_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
