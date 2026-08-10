#!/usr/bin/env python3
"""
本地 OpenAI-compatible 模型 mock 服务。

用途：在不部署、不连接真实企业端点的情况下，端到端验证「企业内部模型调用可观测性」——
把内部模型 provider 的 baseURL 指向本服务，即可复现成功/上游错误/超时/连接失败等
场景，并核对代理插桩、失败分类、探活状态与观测 API。

仅使用 Python 标准库，无第三方依赖。

用法：
    python3 tools/mock-model-server.py --mode ok --port 19070
    python3 tools/mock-model-server.py --mode http500 --port 19070
    python3 tools/mock-model-server.py --mode first-output-timeout --port 19070

故障模式：
    ok        按请求 stream 参数返回合法 JSON 或 SSE
    sse       返回 200 text/event-stream，含 data: 与 [DONE]
    finish-reason-eof 返回有效输出和 finish_reason 后直接 EOF，模拟不发 [DONE] 的企业网关
    nonstream-200 强制返回 200 JSON，用于验证流式探活拒绝非 SSE 成功响应
    http400   返回 400 非 SSE 错误正文
    http500   返回 500 非 SSE 错误正文
    header-timeout       响应头前挂起，模拟首响应超时（timeout 为兼容别名）
    first-output-timeout 发 SSE 响应头后只发注释/伪心跳，模拟首有效输出超时
    idle-timeout         发首个有效输出后只发注释，模拟输出空闲超时
    empty     返回 200 text/event-stream 后立即 EOF，不发任何事件，模拟空/截断流
"""
import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from socketserver import TCPServer


def ok_body():
    return json.dumps(
        {"id": "mock-1", "object": "chat.completion", "model": "mock-model",
         "choices": [{"index": 0, "message": {"role": "assistant", "content": "本地 mock 回答"},
                      "finish_reason": "stop"}],
         "usage": {"prompt_tokens": 3, "completion_tokens": 4, "total_tokens": 7}}
    ).encode("utf-8")


class MockHandler(BaseHTTPRequestHandler):
    mode = "ok"

    def do_POST(self):  # noqa: N802 - BaseHTTPRequestHandler 命名约定
        # 消费并丢弃请求体，避免 keep-alive 残留。
        length = int(self.headers.get("Content-Length") or 0)
        request_body = self.rfile.read(length) if length > 0 else b"{}"
        try:
            stream_requested = bool(json.loads(request_body).get("stream"))
        except (json.JSONDecodeError, AttributeError):
            stream_requested = False

        if self.mode == "http400":
            body = b"upstream rejected"
            self.send_response(400)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        if self.mode == "http500":
            body = b"upstream internal error"
            self.send_response(500)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        if self.mode in ("timeout", "header-timeout"):
            # 响应头发送前挂起，确保分类为首响应超时。
            time.sleep(120)
            return

        # 200 分支。
        if self.mode == "sse" or (self.mode == "ok" and stream_requested):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.wfile.write('data: {"choices":[{"delta":{"content":"流式"}}]}\n\n'.encode("utf-8"))
            self.wfile.flush()
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
            return

        if self.mode == "finish-reason-eof":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.wfile.write(
                b'data: {"choices":[{"delta":{"content":"finished"},"finish_reason":null}]}\n\n'
            )
            self.wfile.flush()
            self.wfile.write(
                b'data: {"choices":[{"delta":{},"finish_reason":"stop"}]}\n\n'
            )
            self.wfile.flush()
            return

        if self.mode == "first-output-timeout":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            for _ in range(1200):
                try:
                    self.wfile.write(b": keepalive\n\ndata: ping\n\n")
                    self.wfile.flush()
                except (BrokenPipeError, ConnectionResetError):
                    break
                time.sleep(0.1)
            return

        if self.mode == "idle-timeout":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            self.wfile.write(b'data: {"choices":[{"delta":{"content":"first"}}]}\n\n')
            self.wfile.flush()
            for _ in range(1200):
                try:
                    self.wfile.write(b": keepalive\n\n")
                    self.wfile.flush()
                except (BrokenPipeError, ConnectionResetError):
                    break
                time.sleep(0.1)
            return

        if self.mode == "empty":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            self.wfile.flush()
            return

        # nonstream-200 或 stream=false 的 ok：返回普通 JSON。
        body = ok_body()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):  # noqa: A003 - 覆盖基类默认访问日志
        # 保持日志简洁；请求路径与来源包含在标准格式里。
        print(f"[mock] {self.address_string()} {fmt % args}")


class LocalThreadingHTTPServer(ThreadingHTTPServer):
    def server_bind(self):
        # HTTPServer 默认在监听前反向解析 127.0.0.1；本地 DNS 异常时会让冒烟探活长时间超时。
        TCPServer.server_bind(self)
        host, port = self.server_address[:2]
        self.server_name = host
        self.server_port = port


def main():
    parser = argparse.ArgumentParser(description="OpenAI-compatible 模型 mock 服务")
    parser.add_argument("--port", type=int, default=19070)
    parser.add_argument("--mode", choices=[
        "ok", "sse", "finish-reason-eof", "nonstream-200", "http400", "http500", "timeout",
        "header-timeout", "first-output-timeout", "idle-timeout", "empty"
    ],
                        default="ok", help="故障模式（默认 ok）")
    args = parser.parse_args()

    MockHandler.mode = args.mode
    server = LocalThreadingHTTPServer(("127.0.0.1", args.port), MockHandler)
    print(f"[mock] 监听 http://127.0.0.1:{args.port}  模式={args.mode}")
    print("[mock] 请求端点: POST /chat/completions")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[mock] 已停止")
        server.server_close()


if __name__ == "__main__":
    main()
