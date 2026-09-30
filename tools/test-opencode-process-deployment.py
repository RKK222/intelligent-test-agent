#!/usr/bin/env python3
"""用只读 HTTP fixture 验证运行管理验收脚本不再访问已废弃的 manager 诊断接口。"""

import json
import os
from pathlib import Path
import subprocess
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


SCRIPT = Path(__file__).with_name("verify-opencode-process-deployment.sh")


class FixtureHandler(BaseHTTPRequestHandler):
    paths = []
    manager_status = "CONNECTED"
    backend_status = "READY"

    def do_GET(self):
        type(self).paths.append(self.path)
        if self.path == "/actuator/health":
            self.respond(200, {"status": "UP"})
            return
        if self.path.startswith("/api/internal/platform/opencode-runtime/management/overview?"):
            if self.headers.get("Authorization") != "Bearer fixture-jwt":
                self.respond(401, {"success": False})
                return
            self.respond(200, {
                "success": True,
                "data": {
                    "summary": {"linuxServers": 1, "backendProcesses": 1, "containers": 1,
                                "managers": 1, "opencodeProcesses": 0},
                    "backendProcesses": [{"backendProcessId": "backend_fixture", "linuxServerId": "srv_fixture",
                                          "status": self.backend_status}],
                    "managers": [{"managerId": "mgr_fixture", "linuxServerId": "srv_fixture",
                                  "connectionStatus": self.manager_status}],
                    "managerBackendConnections": [{"managerId": "mgr_fixture", "backendProcessId": "backend_fixture",
                                                   "status": self.manager_status}],
                },
            })
            return
        self.respond(410, {"success": False, "code": "API_GONE"})

    def respond(self, status, body):
        raw = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, format, *args):
        pass


class ProcessDeploymentSmokeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), FixtureHandler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=5)

    def setUp(self):
        FixtureHandler.paths = []
        FixtureHandler.manager_status = "CONNECTED"
        FixtureHandler.backend_status = "READY"

    def run_smoke(self, *args):
        env = os.environ.copy()
        env.pop("TEST_AGENT_SUPER_ADMIN_TOKEN", None)
        env.pop("TEST_AGENT_AUTH_TOKEN", None)
        return subprocess.run(
            ["bash", str(SCRIPT), "--backend-url", f"http://127.0.0.1:{self.server.server_port}", *args],
            env=env, capture_output=True, text=True, timeout=10, check=False,
        )

    def test_connected_manager_uses_super_admin_overview_only(self):
        result = self.run_smoke("--auth-token", "fixture-jwt", "--require-manager",
                                "--linux-server-id", "srv_fixture")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("connectedManagers=1", result.stdout)
        self.assertEqual(len(FixtureHandler.paths), 2)
        self.assertFalse(any("manager-backends" in path for path in FixtureHandler.paths))

    def test_missing_or_disconnected_target_manager_fails_closed(self):
        wrong_server = self.run_smoke("--auth-token", "fixture-jwt", "--require-manager",
                                      "--linux-server-id", "srv_other")
        self.assertNotEqual(wrong_server.returncode, 0)
        FixtureHandler.manager_status = "DISCONNECTED"
        disconnected = self.run_smoke("--auth-token", "fixture-jwt", "--require-manager",
                                      "--linux-server-id", "srv_fixture")
        self.assertNotEqual(disconnected.returncode, 0)
        self.assertNotIn("fixture-jwt", disconnected.stdout + disconnected.stderr)
        FixtureHandler.manager_status = "CONNECTED"
        FixtureHandler.backend_status = "UNHEALTHY"
        unhealthy_backend = self.run_smoke("--auth-token", "fixture-jwt", "--require-manager",
                                           "--linux-server-id", "srv_fixture")
        self.assertNotEqual(unhealthy_backend.returncode, 0)

    def test_manager_token_alone_cannot_use_retired_http_route(self):
        result = self.run_smoke("--manager-token", "legacy-manager-token", "--require-manager",
                                "--linux-server-id", "srv_fixture")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SUPER_ADMIN token is required", result.stderr)
        self.assertEqual(FixtureHandler.paths, ["/actuator/health"])
        self.assertNotIn("legacy-manager-token", result.stdout + result.stderr)

    def test_manager_requirement_needs_explicit_server_identity(self):
        result = self.run_smoke("--auth-token", "fixture-jwt", "--require-manager")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("needs --linux-server-id", result.stderr)
        self.assertEqual(FixtureHandler.paths, [])


if __name__ == "__main__":
    unittest.main()
