from __future__ import annotations

from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import threading


RELAY_PATH = Path(__file__).parents[1] / "test-agent-model-relay.py"
SPEC = importlib.util.spec_from_file_location("test_agent_model_relay", RELAY_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class GatewayHandler(BaseHTTPRequestHandler):
    requests: list[dict[str, object]] = []

    def do_POST(self) -> None:  # noqa: N802
        body = self.rfile.read(int(self.headers["Content-Length"]))
        self.requests.append(
            {
                "path": self.path,
                "authorization": self.headers.get("Authorization"),
                "evil": self.headers.get("X-Evil"),
                "body": body,
            }
        )
        streaming = self.path.endswith("/chat/completions")
        response = b'data: {"delta":"ok"}\n\ndata: [DONE]\n\n' if streaming else b'{"ok":true}'
        self.send_response(200)
        self.send_header(
            "Content-Type",
            "text/event-stream" if streaming else "application/json",
        )
        self.send_header("Set-Cookie", "must-not-cross=1")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def do_GET(self) -> None:  # noqa: N802
        self.requests.append(
            {
                "path": self.path,
                "authorization": self.headers.get("Authorization"),
                "evil": self.headers.get("X-Evil"),
                "body": b"",
            }
        )
        response = b'{"object":"list","data":[]}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def log_message(self, *_arguments: object) -> None:
        return None


def test_initial_responses_turn_requires_a_tool_then_restores_auto_choice() -> None:
    initial = {
        "model": "workflow-code-analysis",
        "input": [{"type": "message", "role": "user", "content": "inspect"}],
        "tools": [{"type": "function", "name": "exec_command"}],
        "tool_choice": "auto",
    }

    rewritten = json.loads(
        MODULE.require_initial_response_tool_call(json.dumps(initial).encode())
    )
    assert rewritten["tool_choice"] == "required"

    allowed_tools_auto = {
        **initial,
        "tool_choice": {
            "type": "allowed_tools",
            "mode": "auto",
            "tools": [{"type": "function", "name": "exec_command"}],
        },
    }
    rewritten_allowed_tools = json.loads(
        MODULE.require_initial_response_tool_call(
            json.dumps(allowed_tools_auto).encode()
        )
    )
    assert rewritten_allowed_tools["tool_choice"] == "required"

    followup = {
        **initial,
        "input": [
            *initial["input"],
            {"type": "function_call_output", "call_id": "call_1", "output": "ok"},
        ],
    }
    preserved = MODULE.require_initial_response_tool_call(json.dumps(followup).encode())
    assert json.loads(preserved)["tool_choice"] == "auto"


def test_relay_keeps_platform_grant_out_of_the_analyzer_process() -> None:
    GatewayHandler.requests = []
    upstream = ThreadingHTTPServer(("127.0.0.1", 0), GatewayHandler)
    upstream_thread = threading.Thread(target=upstream.serve_forever, daemon=True)
    upstream_thread.start()
    relay = subprocess.Popen(
        [sys.executable, str(RELAY_PATH)],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    assert relay.stdin is not None
    assert relay.stdout is not None
    assert relay.stderr is not None
    configuration = {
        "localToken": "relay_rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr",
        "port": 18082,
        "upstreamGrant": "wfg_gggggggggggggggggggggggggggggggg",
        "upstreamUrl": (
            f"http://127.0.0.1:{upstream.server_port}"
            "/api/internal/platform/model-gateway/v1"
        ),
    }
    relay.stdin.write(json.dumps(configuration) + "\n")
    relay.stdin.flush()

    try:
        assert relay.stdout.readline() == "READY\n"
        unauthorized = HTTPConnection("127.0.0.1", 18082, timeout=5)
        unauthorized.request("POST", "/v1/responses", body=b"{}")
        assert unauthorized.getresponse().status == 401
        unauthorized.close()
        assert GatewayHandler.requests == []

        client = HTTPConnection("127.0.0.1", 18082, timeout=5)
        client.request(
            "POST",
            "/v1/responses",
            body=b'{"model":"workflow-code-analysis"}',
            headers={
                "Authorization": "Bearer relay_rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr",
                "Content-Type": "application/json",
                "Accept": "application/json",
                "X-Evil": "must-not-cross",
            },
        )
        response = client.getresponse()
        assert response.status == 200
        assert response.read() == b'{"ok":true}'
        assert response.getheader("Set-Cookie") is None
        client.close()

        streaming = HTTPConnection("127.0.0.1", 18082, timeout=5)
        streaming.request(
            "POST",
            "/v1/chat/completions",
            body=b'{"stream":true}',
            headers={
                "Authorization": "Bearer relay_rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr",
                "Content-Type": "application/json",
            },
        )
        stream_response = streaming.getresponse()
        assert stream_response.getheader("Content-Type") == "text/event-stream"
        assert stream_response.read().endswith(b"data: [DONE]\n\n")
        streaming.close()

        models = HTTPConnection("127.0.0.1", 18082, timeout=5)
        models.request(
            "GET",
            "/v1/models",
            headers={"Authorization": "Bearer relay_rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr"},
        )
        models_response = models.getresponse()
        assert models_response.status == 200
        assert models_response.read() == b'{"object":"list","data":[]}'
        models.close()

        forbidden = HTTPConnection("127.0.0.1", 18082, timeout=5)
        forbidden.request(
            "POST",
            "/v1/embeddings",
            body=b"{}",
            headers={"Authorization": "Bearer relay_rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr"},
        )
        assert forbidden.getresponse().status == 404
        forbidden.close()

        assert GatewayHandler.requests == [
            {
                "path": "/api/internal/platform/model-gateway/v1/responses",
                "authorization": "Bearer wfg_gggggggggggggggggggggggggggggggg",
                "evil": None,
                "body": b'{"model":"workflow-code-analysis"}',
            },
            {
                "path": "/api/internal/platform/model-gateway/v1/chat/completions",
                "authorization": "Bearer wfg_gggggggggggggggggggggggggggggggg",
                "evil": None,
                "body": b'{"stream":true}',
            },
            {
                "path": "/api/internal/platform/model-gateway/v1/models",
                "authorization": "Bearer wfg_gggggggggggggggggggggggggggggggg",
                "evil": None,
                "body": b"",
            },
        ]
    finally:
        relay.stdin.close()
        relay.wait(timeout=5)
        upstream.shutdown()
        upstream.server_close()
        upstream_thread.join(timeout=5)

    output = relay.stdout.read() + relay.stderr.read()
    assert "wfg_gggggggggggggggggggggggggggggggg" not in output
    assert relay.returncode == 0
