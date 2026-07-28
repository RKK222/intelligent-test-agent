#!/usr/bin/env python3
"""从两套锁定上游源码及其中英文翻译生成平台离线工具目录。"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

import yaml


CATALOG_VERSION = "2026-07-27.it-tools-2024.10.22-7ca5933.omni-tools-0.6.0"
IT_VERSION = "2024.10.22-7ca5933"
OMNI_VERSION = "0.6.0"

IT_CATEGORY_MAP = {
    "Crypto": "SECURITY",
    "Converter": "ENCODING",
    "Web": "WEB",
    "Images and videos": "IMAGE",
    "Development": "DEVELOPMENT",
    "Network": "NETWORK",
    "Math": "MATH",
    "Measurement": "MATH",
    "Text": "TEXT",
    "Data": "DATA",
}

# 上游 zh locale 中仍缺少这些条目；平台目录必须保持可检索的中英双语契约。
IT_NAME_ZH_OVERRIDES = {
    "xml-to-json": "XML 转 JSON",
    "json-to-xml": "JSON 转 XML",
    "markdown-to-html": "Markdown 转 HTML",
    "safelink-decoder": "Outlook 安全链接解码器",
    "email-normalizer": "邮箱地址规范化",
    "regex-tester": "正则表达式测试器",
    "regex-memo": "正则表达式速查表",
    "ascii-text-drawer": "ASCII 艺术字生成器",
}

IT_DESCRIPTION_ZH_OVERRIDES = {
    "toml-to-yaml": "解析 TOML 并转换为 YAML。",
    "xml-to-json": "将 XML 转换为 JSON。",
    "json-to-xml": "将 JSON 转换为 XML。",
    "markdown-to-html": "将 Markdown 转换为 HTML，并支持打印为 PDF。",
    "safelink-decoder": "解码 Outlook SafeLink 安全链接。",
    "email-normalizer": "将邮箱地址规范化为便于比较的统一格式，适用于去重和数据清洗。",
    "regex-tester": "使用示例文本测试正则表达式。",
    "regex-memo": "JavaScript 正则表达式速查表。",
    "ascii-text-drawer": "使用多种字体和样式生成 ASCII 艺术字。",
}

OMNI_CATEGORY_MAP = {
    "string": "TEXT",
    "image-generic": "IMAGE",
    "png": "IMAGE",
    "gif": "IMAGE",
    "number": "MATH",
    "list": "DATA",
    "json": "DATA",
    "csv": "DATA",
    "xml": "DATA",
    "time": "DATE_TIME",
    "video": "AUDIO_VIDEO",
    "audio": "AUDIO_VIDEO",
    "pdf": "PDF",
}


def nested(data: dict[str, Any], dotted_key: str) -> str:
    """读取上游点分隔 i18n key，并把复杂值安全降级为空字符串。"""
    current: Any = data
    for part in dotted_key.split("."):
        if not isinstance(current, dict) or part not in current:
            return ""
        current = current[part]
    return str(current).strip() if isinstance(current, (str, int, float)) else ""


def merge_dict(target: dict[str, Any], source: dict[str, Any]) -> None:
    """递归合并 IT-Tools 根翻译与工具私有翻译。"""
    for key, value in source.items():
        if isinstance(value, dict) and isinstance(target.get(key), dict):
            merge_dict(target[key], value)
        else:
            target[key] = value


def load_it_locale(root: Path, locale: str) -> dict[str, Any]:
    data = yaml.safe_load((root / "locales" / f"{locale}.yml").read_text()) or {}
    for locale_file in sorted((root / "src" / "tools").glob(f"*/locales/{locale}.yml")):
        merge_dict(data, yaml.safe_load(locale_file.read_text()) or {})
    return data


def quoted_list(source: str, field: str) -> list[str]:
    match = re.search(rf"{re.escape(field)}\s*:\s*\[(.*?)\]", source, re.S)
    if not match:
        return []
    return re.findall(r"['\"]([^'\"]+)['\"]", match.group(1))


def it_categories(root: Path) -> dict[str, str]:
    index = (root / "src" / "tools" / "index.ts").read_text()
    aliases = dict(re.findall(r"import \{ tool as (\w+) \} from './([^']+)'", index))
    result: dict[str, str] = {}
    blocks = re.findall(r"\{\s*name:\s*'([^']+)',\s*components:\s*\[(.*?)\]\s*,?\s*\}", index, re.S)
    for category, component_source in blocks:
        for alias in re.findall(r"\b[A-Za-z][A-Za-z0-9]*\b", component_source):
            if alias in aliases:
                result[aliases[alias]] = category
    return result


def generate_it_tools(root: Path) -> list[dict[str, Any]]:
    en = load_it_locale(root, "en")
    zh = load_it_locale(root, "zh")
    categories = it_categories(root)
    tools: list[dict[str, Any]] = []
    for slug, upstream_category in categories.items():
        if slug == "camera-recorder":
            continue
        source = (root / "src" / "tools" / slug / "index.ts").read_text()
        path_match = re.search(r"path:\s*'(/[^']+)'", source)
        name_match = re.search(r"name:\s*(?:translate\('([^']+)'\)|'([^']+)')", source)
        description_match = re.search(r"description:\s*(?:translate\('([^']+)'\)|'([^']+)')", source)
        if not path_match or not name_match or not description_match:
            raise ValueError(f"无法解析 IT-Tools 元数据: {slug}")
        route = path_match.group(1).lstrip("/")
        name_key, literal_name = name_match.groups()
        description_key, literal_description = description_match.groups()
        name_en = literal_name or nested(en, name_key)
        name_zh = IT_NAME_ZH_OVERRIDES.get(slug) or (nested(zh, name_key) if name_key else "") or name_en
        description_zh = IT_DESCRIPTION_ZH_OVERRIDES.get(slug) or (
            (nested(zh, description_key) if description_key else "")
            or (nested(en, description_key) if description_key else "")
            or literal_description
        )
        keywords = quoted_list(source, "keywords") or [slug.replace("-", " ")]
        tools.append({
            "toolId": f"it-tools.{slug}",
            "source": "IT_TOOLS",
            "sourceVersion": IT_VERSION,
            "nameZh": name_zh,
            "nameEn": name_en,
            "descriptionZh": description_zh,
            "category": IT_CATEGORY_MAP[upstream_category],
            "keywords": list(dict.fromkeys([*keywords, name_en, name_zh, upstream_category])),
            "launchPath": f"/toolbox/apps/it-tools/{route}",
        })
    return tools


def omni_translation(root: Path, locale: str, full_key: str) -> str:
    namespace, dotted_key = full_key.split(":", 1)
    data = json.loads((root / "public" / "locales" / locale / f"{namespace}.json").read_text())
    return nested(data, dotted_key)


def parse_omni_definition(root: Path, source: str, source_file: Path) -> dict[str, Any]:
    category_match = re.search(r"defineTool\('([^']+)'", source)
    path_match = re.search(r"\bpath:\s*'([^']+)'", source)
    name_match = re.search(r"\bname:\s*'([^']+)'", source)
    description_match = re.search(r"\bdescription:\s*'([^']+)'", source)
    if not category_match or not path_match or not name_match or not description_match:
        raise ValueError(f"无法解析 OmniTools 元数据: {source_file}")
    category = category_match.group(1)
    path = path_match.group(1)
    name_key = name_match.group(1)
    description_key = description_match.group(1)
    name_en = omni_translation(root, "en", name_key)
    name_zh = omni_translation(root, "zh", name_key) or name_en
    description_zh = (
        omni_translation(root, "zh", description_key)
        or omni_translation(root, "en", description_key)
    )
    route = f"{category}/{path}"
    stable_slug = path.replace("/", ".")
    keywords = quoted_list(source, "keywords") or [stable_slug.replace("-", " ")]
    return {
        "toolId": f"omni-tools.{category}.{stable_slug}",
        "source": "OMNI_TOOLS",
        "sourceVersion": OMNI_VERSION,
        "nameZh": name_zh,
        "nameEn": name_en,
        "descriptionZh": description_zh,
        "category": OMNI_CATEGORY_MAP[category],
        "keywords": list(dict.fromkeys([*keywords, name_en, name_zh, category])),
        "launchPath": f"/toolbox/apps/omni-tools/{route}",
    }


def generate_omni_tools(root: Path) -> list[dict[str, Any]]:
    tools: list[dict[str, Any]] = []
    generic_meta = root / "src" / "pages" / "tools" / "number" / "generic-calc" / "meta.ts"
    # SimplePDF 编辑器是外部托管 iframe，企业离线环境不可用，因此与摄像头一样不进入目录或路由。
    external_pdf_editor = root / "src" / "pages" / "tools" / "pdf" / "editor" / "meta.ts"
    for meta_file in sorted((root / "src" / "pages" / "tools").glob("**/meta.ts")):
        if meta_file in {generic_meta, external_pdf_editor}:
            continue
        tools.append(parse_omni_definition(root, meta_file.read_text(), meta_file))

    for data_file in sorted((generic_meta.parent / "data").glob("*.ts")):
        if data_file.name in {"index.ts", "types.ts"}:
            continue
        source = data_file.read_text()
        # 动态计算器的数据文件不含 defineTool，补入固定 category 后复用同一解析器。
        synthetic = "defineTool('number', {\n" + source + "\n})"
        parsed = parse_omni_definition(root, synthetic, data_file)
        parsed["toolId"] = "omni-tools.number.generic-calc." + parsed["toolId"].split(".")[-1]
        parsed["launchPath"] = "/toolbox/apps/omni-tools/number/generic-calc/" + parsed["launchPath"].split("/")[-1]
        parsed["keywords"] = list(dict.fromkeys(["calculator", "math", *parsed["keywords"]]))
        tools.append(parsed)
    return sorted(tools, key=lambda tool: tool["launchPath"])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--it-tools", required=True, type=Path)
    parser.add_argument("--omni-tools", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    tools = [*generate_it_tools(args.it_tools), *generate_omni_tools(args.omni_tools)]
    if len(tools) != 193:
        raise ValueError(f"离线目录必须固定为 193 项，实际为 {len(tools)}")
    for order, tool in enumerate(tools):
        tool["catalogOrder"] = order
    if len({tool["toolId"] for tool in tools}) != len(tools):
        raise ValueError("toolId 不唯一")
    if len({tool["launchPath"] for tool in tools}) != len(tools):
        raise ValueError("launchPath 不唯一")
    for tool in tools:
        if not re.search(r"[\u3400-\u9fff]", tool["nameZh"]):
            raise ValueError(f"中文名不含汉字: {tool['toolId']}")
        if not re.search(r"[\u3400-\u9fff]", tool["descriptionZh"]):
            raise ValueError(f"中文说明不含汉字: {tool['toolId']}")
    payload = {"catalogVersion": CATALOG_VERSION, "tools": tools}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
