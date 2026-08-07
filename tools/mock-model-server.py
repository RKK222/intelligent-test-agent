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
    python3 tools/mock-model-server.py --mode timeout --port 19070

故障模式：
    ok        返回 200 非流式 JSON（{"choices":[{"message":{"content":"ok"}}]}）
    sse       返回 200 text/event-stream，含 data: 与 [DONE]
    http400   返回 400 非 SSE 错误正文
    http500   返回 500 非 SSE 错误正文
    timeout   响应头后静默不发送正文，模拟首响应/首事件超时
    empty     返回 200 text/event-stream 但永远不发任何事件，模拟首事件超时
"""
import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


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
        if length > 0:
            self.rfile.read(length)

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

        # 200 分支。
        if self.mode == "sse":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.wfile.write('data: {"choices":[{"delta":{"content":"流式"}}]}\n\n'.encode("utf-8"))
            self.wfile.flush()
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
            return

        if self.mode == "empty":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            self.wfile.flush()
            return

        if self.mode == "timeout":
            # 发响应头后挂起，正文迟迟不到，触发首响应/首事件超时。
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.flush()
            time.sleep(120)
            return

        # 默认 ok：非流式 200。
        body = ok_body()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):  # noqa: A003 - 覆盖基类默认访问日志
        # 保持日志简洁；请求路径与来源包含在标准格式里。
        print(f"[mock] {self.address_string()} {fmt % args}")


def main():
    parser = argparse.ArgumentParser(description="OpenAI-compatible 模型 mock 服务")
    parser.add_argument("--port", type=int, default=19070)
    parser.add_argument("--mode", choices=["ok", "sse", "http400", "http500", "timeout", "empty"],
                        default="ok", help="故障模式（默认 ok）")
    args = parser.parse_args()

    MockHandler.mode = args.mode
    server = ThreadingHTTPServer(("127.0.0.1", args.port), MockHandler)
    print(f"[mock] 监听 http://127.0.0.1:{args.port}  模式={args.mode}")
    print("[mock] 请求端点: POST /chat/completions")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[mock] 已停止")
        server.server_close()


if __name__ == "__main__":
    main()
