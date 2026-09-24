#!/usr/bin/env python3
"""查询一体化平台接口结构，并且只输出后续同步所需的最小数据。"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import PurePath
from typing import Any, Iterable

API_URL = "http://interface.sdc.cs.icbc/contract-api/opencode/interface/getInterfaceInfoByName"
TIMEOUT_SECONDS = 20
MAX_RESPONSE_BYTES = 4 * 1024 * 1024
MAX_STRUCT_NODES = 20000
MAX_STRUCT_DEPTH = 64
INTERFACE_NAME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_.-]{0,127}$")
DATE_TOKEN_RE = re.compile(r"^\d{8}$")
LOGICAL_DATE_RE = re.compile(r"^(?:appworkspace|personalworktree):(\d{8})$", re.IGNORECASE)


class ContractError(RuntimeError):
    """可安全展示、且不包含接口原始响应的查询错误。"""


def _split_path(path_value: str) -> list[str]:
    if not isinstance(path_value, str) or not path_value.strip():
        raise ContractError("工作空间路径不能为空")
    return [part for part in re.split(r"[\\/]+", path_value.strip()) if part]


def _parse_date_token(token: str) -> dt.date:
    try:
        return dt.datetime.strptime(token, "%Y%m%d").date()
    except ValueError as exc:
        raise ContractError(f"工作空间路径包含非法 YYYYMMDD 日期段：{token}") from exc


def parse_workspace_identity(workspace_root: str) -> tuple[str, str]:
    """从物理或逻辑工作空间路径中确定应用名和月度版本。"""
    parts = _split_path(workspace_root)

    app_candidates: list[str] = []
    for index, part in enumerate(parts):
        if part.lower() == "workspace":
            if index == 0 or not parts[index - 1].strip():
                raise ContractError("workspace 路径段前缺少应用名")
            app_candidates.append(parts[index - 1].strip())
    if not app_candidates:
        raise ContractError("工作空间路径中未找到 workspace 路径段")
    unique_apps = list(dict.fromkeys(app_candidates))
    if len(unique_apps) != 1:
        raise ContractError("工作空间路径包含多个不同的应用名候选")
    app_name = unique_apps[0]
    if app_name in {".", ".."} or ":" in app_name:
        raise ContractError("从工作空间路径解析出的应用名不合法")

    date_tokens: list[str] = []
    for part in parts:
        logical_match = LOGICAL_DATE_RE.fullmatch(part)
        if logical_match:
            date_tokens.append(logical_match.group(1))
        elif DATE_TOKEN_RE.fullmatch(part):
            date_tokens.append(part)
    if not date_tokens:
        raise ContractError("工作空间路径中未找到完整 YYYYMMDD 日期段")

    parsed_dates = [_parse_date_token(token) for token in date_tokens]
    unique_dates = list(dict.fromkeys(parsed_dates))
    if len(unique_dates) != 1:
        rendered = "、".join(date.strftime("%Y%m%d") for date in unique_dates)
        raise ContractError(f"工作空间路径包含多个不同日期：{rendered}")
    version_date = unique_dates[0]
    return app_name, f"{version_date.year}年{version_date.month}月"


def validate_interface_name(interface_en_name: str) -> str:
    if not isinstance(interface_en_name, str) or not INTERFACE_NAME_RE.fullmatch(interface_en_name):
        raise ContractError("接口英文名必须匹配 ^[A-Za-z][A-Za-z0-9_.-]{0,127}$")
    return interface_en_name


def validate_req_param_struct(value: Any) -> list[dict[str, Any]]:
    """递归校验结构，拒绝空树、重名兄弟节点和异常深度。"""
    if not isinstance(value, list) or not value:
        raise ContractError("接口返回缺少非空 interfaceInfo.reqParamStruct")

    count = 0

    def visit(nodes: Any, depth: int, parent_path: str) -> list[dict[str, Any]]:
        nonlocal count
        if depth > MAX_STRUCT_DEPTH:
            raise ContractError("reqParamStruct 层级过深")
        if not isinstance(nodes, list):
            raise ContractError(f"reqParamStruct 节点 children 不是数组：{parent_path}")
        names: set[str] = set()
        result: list[dict[str, Any]] = []
        for index, node in enumerate(nodes):
            count += 1
            if count > MAX_STRUCT_NODES:
                raise ContractError("reqParamStruct 节点数量超过安全上限")
            if not isinstance(node, dict):
                raise ContractError(f"reqParamStruct 节点不是对象：{parent_path}[{index}]")
            name = node.get("name")
            if not isinstance(name, str) or not name.strip():
                raise ContractError(f"reqParamStruct 节点缺少有效 name：{parent_path}[{index}]")
            name = name.strip()
            if name in names:
                raise ContractError(f"reqParamStruct 存在同级重名字段：{parent_path}.{name}")
            names.add(name)
            children = node.get("children", [])
            if children is None:
                children = []
            if not isinstance(children, list):
                raise ContractError(f"reqParamStruct 节点 children 不是数组：{parent_path}.{name}")
            normalized = dict(node)
            normalized["name"] = name
            normalized["children"] = visit(children, depth + 1, f"{parent_path}.{name}") if children else []
            result.append(normalized)
        return result

    return visit(value, 1, "$")


def _read_limited(response: Any) -> bytes:
    content_length = response.headers.get("Content-Length") if getattr(response, "headers", None) else None
    if content_length:
        try:
            if int(content_length) > MAX_RESPONSE_BYTES:
                raise ContractError("一体化平台响应超过 4 MiB 上限")
        except ValueError:
            pass
    body = response.read(MAX_RESPONSE_BYTES + 1)
    if len(body) > MAX_RESPONSE_BYTES:
        raise ContractError("一体化平台响应超过 4 MiB 上限")
    return body


def query_contract(workspace_root: str, interface_en_name: str) -> dict[str, Any]:
    app_name, version = parse_workspace_identity(workspace_root)
    interface_en_name = validate_interface_name(interface_en_name)
    request_payload = {
        "appName": app_name,
        "version": version,
        "interfaceEnName": interface_en_name,
    }
    request = urllib.request.Request(
        API_URL,
        data=json.dumps(request_payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8"),
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            status = getattr(response, "status", None) or response.getcode()
            if status < 200 or status >= 300:
                raise ContractError(f"一体化平台返回 HTTP {status}")
            raw = _read_limited(response)
    except urllib.error.HTTPError as exc:
        raise ContractError(f"一体化平台返回 HTTP {exc.code}") from exc
    except urllib.error.URLError as exc:
        reason = getattr(exc, "reason", None)
        reason_name = reason.__class__.__name__ if reason is not None else "network error"
        raise ContractError(f"一体化平台请求失败：{reason_name}") from exc
    except TimeoutError as exc:
        raise ContractError("一体化平台请求超时") from exc

    try:
        decoded = json.loads(raw.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ContractError("一体化平台响应不是有效 UTF-8 JSON") from exc
    if not isinstance(decoded, dict):
        raise ContractError("一体化平台响应根节点不是对象")
    interface_info = decoded.get("interfaceInfo")
    if not isinstance(interface_info, dict):
        raise ContractError("一体化平台响应缺少 interfaceInfo")
    req_param_struct = validate_req_param_struct(interface_info.get("reqParamStruct"))
    return {
        "appName": app_name,
        "version": version,
        "interfaceEnName": interface_en_name,
        "reqParamStruct": req_param_struct,
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="查询一体化平台接口请求结构")
    parser.add_argument("--workspace-root", required=True, help="应用工作空间根目录或其下路径")
    parser.add_argument("--interface-en-name", required=True, help="明确的接口英文名")
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        result = query_contract(args.workspace_root, args.interface_en_name)
    except ContractError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
