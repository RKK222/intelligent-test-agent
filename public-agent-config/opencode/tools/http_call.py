#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
通用 HTTP 请求工具
支持 GET / POST / PUT / DELETE 方法，自动在请求头携带可配置的 toolId。

仅使用 Python 标准库（urllib），无需 pip install requests，Linux/Mac 通用。

日志：
  - 所有诊断日志输出到 stderr，不影响 stdout 的格式化结果（便于上层解析）。
  - 通过环境变量 HTTP_CALL_LOG_LEVEL 控制级别，默认 INFO，设为 DEBUG 可看完整请求/响应细节。
"""

import argparse
import json
import logging
import os
import platform
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# ===== 可修改配置 =====
TOOL_ID = "66f36bfa5c1c6105572b0118880261d6"
DEFAULT_TIMEOUT = 30  # 秒
# =====================


def setup_logging():
    """初始化日志，输出到 stderr，带时间戳和级别。"""
    level_name = os.environ.get("HTTP_CALL_LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
        stream=sys.stderr,
    )
    return logging.getLogger("http_call")


log = setup_logging()


class SimpleResponse:
    """urllib 响应包装，提供与 requests.Response 类似的最小接口。"""

    def __init__(self, status_code, headers, text, elapsed_ms):
        self.status_code = status_code
        # headers 统一为 list[(k, v)]，便于遍历输出
        self.headers = headers
        self.text = text
        self.elapsed_ms = elapsed_ms

    def json(self):
        return json.loads(self.text)


def build_headers(extra_headers=None):
    """组装请求头，固定带上 toolId。"""
    headers = {"toolId": TOOL_ID}
    if extra_headers:
        headers.update(extra_headers)
    log.debug("build_headers 完成 | toolId=%s | 额外头键=%s", TOOL_ID, list(extra_headers.keys()) if extra_headers else [])
    return headers


def http_request(method, url, params=None, body=None, headers=None, timeout=DEFAULT_TIMEOUT):
    """
    发起 HTTP 请求（基于标准库 urllib）。

    :param method:  请求方式 GET/POST/PUT/DELETE
    :param url:     目标 URL
    :param params:  查询参数 dict，拼到 query string
    :param body:    请求体 dict，以 JSON 形式发送（POST/PUT 常用）
    :param headers: 额外请求头 dict
    :param timeout: 超时秒数
    :return: SimpleResponse
    """
    method = (method or "").upper()
    if method not in ("GET", "POST", "PUT", "DELETE"):
        log.error("不支持的请求方式: %s", method)
        raise ValueError(f"不支持的请求方式: {method}，仅支持 GET/POST/PUT/DELETE")

    log.info("构建请求 | method=%s | url=%s | timeout=%ss", method, url, timeout)

    final_headers = build_headers(headers)

    if "toolId" not in final_headers:
        final_headers["toolId"] = TOOL_ID
    # 有 body 时默认按 JSON 发送
    if body is not None and "Content-Type" not in final_headers:
        final_headers["Content-Type"] = "application/json"

    # 拼接 query string
    full_url = url
    if params:
        query = urllib.parse.urlencode(params)
        sep = "&" if "?" in full_url else "?"
        full_url = f"{full_url}{sep}{query}"
        log.debug("拼接查询参数 | full_url=%s", full_url)
    else:
        log.debug("无查询参数")

    # 构造请求体
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        log.debug("请求体(原始)=%s", json.dumps(body, ensure_ascii=False))
        log.debug("请求体字节数=%d", len(data))

    req = urllib.request.Request(
        url=full_url,
        data=data,
        method=method,
        headers=final_headers,
    )
    log.debug("urllib.request.Request 已构造 | method=%s | url=%s", method, full_url)
    log.debug("最终请求头=%s", final_headers)

    start = time.time()
    try:
        log.info("发起请求 → %s %s", method, full_url)
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            elapsed_ms = int((time.time() - start) * 1000)
            status_code = resp.status
            resp_headers = list(resp.headers.items())
            raw_body = resp.read()
            log.info("响应到达 | status=%s | 耗时=%dms | 响应字节数=%d", status_code, elapsed_ms, len(raw_body))
            log.debug("响应头=%s", resp_headers)
    except urllib.error.HTTPError as e:
        # 4xx/5xx 也要读响应体，便于排错
        elapsed_ms = int((time.time() - start) * 1000)
        status_code = e.code
        resp_headers = list(e.headers.items()) if e.headers else []
        try:
            raw_body = e.read()
        except Exception:
            raw_body = b""
        log.warning("HTTP 错误 | status=%s | reason=%s | 耗时=%dms | 响应字节数=%d", e.code, e.reason, elapsed_ms, len(raw_body))
        log.debug("错误响应头=%s", resp_headers)
        log.debug("错误响应体=%s", raw_body.decode("utf-8", errors="replace"))
    except urllib.error.URLError as e:
        elapsed_ms = int((time.time() - start) * 1000)
        log.error("URL 错误 | reason=%s | 耗时=%dms", e.reason, elapsed_ms)
        raise RuntimeError(f"请求失败: {e.reason}") from e
    except Exception as e:
        elapsed_ms = int((time.time() - start) * 1000)
        log.error("未预期异常 | type=%s | msg=%s | 耗时=%dms", type(e).__name__, e, elapsed_ms)
        raise

    # 解码响应体
    try:
        text = raw_body.decode("utf-8")
        log.debug("响应体解码(UTF-8)成功 | 长度=%d", len(text))
    except UnicodeDecodeError:
        text = raw_body.decode("utf-8", errors="replace")
        log.warning("响应体非 UTF-8，已用 replace 解码 | 长度=%d", len(text))

    return SimpleResponse(
        status_code=status_code,
        headers=resp_headers,
        text=text,
        elapsed_ms=elapsed_ms,
    )


def _parse_kv(items):
    """把 ['k=v', ...] 解析成 dict。"""
    result = {}
    for item in items or []:
        if "=" not in item:
            log.error("参数格式错误，应为 key=value: %s", item)
            raise ValueError(f"参数格式错误，应为 key=value: {item}")
        k, v = item.split("=", 1)
        result[k.strip()] = v.strip()
    return result


def main():
    log.info("启动 http_call.py | python=%s | platform=%s | pid=%s", platform.python_version(), platform.platform(), os.getpid())

    parser = argparse.ArgumentParser(
        description="通用 HTTP 请求工具（GET/POST/PUT/DELETE），仅依赖 Python 标准库"
    )
    parser.add_argument("method", help="请求方式: GET/POST/PUT/DELETE")
    parser.add_argument("url", help="目标 URL")
    parser.add_argument(
        "-p", "--params", nargs="*", default=[],
        help="查询参数，格式 key=value，可多个，如 -p page=1 size=10",
    )
    parser.add_argument(
        "-b", "--body", help="请求体 JSON 字符串，如 -b '{\"name\":\"test\"}'"
    )
    parser.add_argument(
        "-H", "--header", nargs="*", default=[],
        help="额外请求头，格式 key=value，如 -H Authorization=Bearerxxx",
    )
    parser.add_argument(
        "-t", "--timeout", type=int, default=DEFAULT_TIMEOUT,
        help=f"超时秒数，默认 {DEFAULT_TIMEOUT}",
    )
    args = parser.parse_args()

    log.info("参数解析完成 | method=%s | url=%s | timeout=%ss", args.method, args.url, args.timeout)
    log.debug("原始 params=%s | 原始 headers=%s | 原始 body=%s", args.params, args.header, args.body)

    params = _parse_kv(args.params)
    headers = _parse_kv(args.header)
    body = json.loads(args.body) if args.body else None
    log.debug("解析后 params=%s | headers=%s | body=%s", params, headers, body)

    try:
        resp = http_request(
            method=args.method,
            url=args.url,
            params=params or None,
            body=body,
            headers=headers or None,
            timeout=args.timeout,
        )
    except ValueError as e:
        log.error("参数错误退出 | exit=2 | msg=%s", e)
        print(f"❌ 参数错误: {e}", file=sys.stderr)
        sys.exit(2)
    except RuntimeError as e:
        log.error("请求失败退出 | exit=1 | msg=%s", e)
        print(f"❌ 请求失败: {e}", file=sys.stderr)
        sys.exit(1)

    log.debug("准备格式化输出 | status=%s | text长度=%d", resp.status_code, len(resp.text))

    print("━━━ HTTP 调用结果 ━━━")
    print(f"请求:  {args.method.upper()} {args.url}")
    print(f"状态码: {resp.status_code}")
    print(f"耗时:   {resp.elapsed_ms}ms")
    print()
    print("响应头:")
    for k, v in resp.headers:
        print(f"  {k}: {v}")
    print()
    print("响应体:")
    try:
        print(json.dumps(resp.json(), ensure_ascii=False, indent=2))
        log.debug("响应体 JSON 解析成功，已格式化输出")
    except ValueError:
        print(resp.text)
        log.debug("响应体非 JSON，按原文输出")
    print("━━━━━━━━━━━━━━━━━━━━")

    log.info("结束 | exit=0 | status=%s | 耗时=%dms", resp.status_code, resp.elapsed_ms)


if __name__ == "__main__":
    main()
