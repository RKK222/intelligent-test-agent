#!/usr/bin/env python3
"""用最小不可变发布包验证 V1 回滚来源门禁。"""

import hashlib
import importlib.util
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("v1_rollback_source", ROOT / "deploy/local/verify-v1-rollback-source.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class V1RollbackSourceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="v1-rollback-source-")
        self.addCleanup(self.temp.cleanup)
        self.tag = "release-3-01234567"
        self.commit = "0123456789abcdef0123456789abcdef01234567"
        self.release = Path(self.temp.name) / self.tag
        self.release.mkdir()
        self._write("source/deploy/internal/opencode-node-runtime.package.json", json.dumps({
            "version": "1.18.4",
            "dependencies": {"@opencode-ai/sdk": "1.18.4", "@opencode-ai/plugin": "1.18.4"},
        }))
        self._write(
            "source/backend/test-agent-opencode-client/src/main/java/com/enterprise/testagent/opencode/client/GeneratedOpencodeSdkGateway.java",
            'new GlobalApi(apiClient).globalHealth(); invoke("/session");',
        )
        self._write("frontend/index.html", "<html></html>")
        self._write("nginx.conf", "server {}")
        self._write("stack.json", "{}")
        self._write("worker-stack.json", "{}")
        client_bytes = io.BytesIO()
        with zipfile.ZipFile(client_bytes, "w") as client:
            client.writestr("com/enterprise/testagent/opencode/client/GeneratedOpencodeSdkGateway.class", b"fixture")
        with zipfile.ZipFile(self.release / "backend.jar", "w") as archive:
            archive.writestr("BOOT-INF/lib/test-agent-opencode-client-0.1.0-SNAPSHOT.jar", client_bytes.getvalue())
        self.refresh_checksums()

    def _write(self, relative: str, content: str) -> None:
        path = self.release / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def refresh_checksums(self) -> None:
        for folder in ("source", "frontend"):
            members = sorted((self.release / folder).rglob("*"))
            self._write(f"{folder}.sha256", "".join(
                f"{digest(member)}  {member.relative_to(self.release)}\n"
                for member in members if member.is_file()
            ))
        self.manifest = {
            "schemaVersion": 2,
            "tag": self.tag,
            "commit": self.commit,
            "backendJarSha256": digest(self.release / "backend.jar"),
            "nginxSha256": digest(self.release / "nginx.conf"),
            "stackSha256": digest(self.release / "stack.json"),
            "workerStackSha256": digest(self.release / "worker-stack.json"),
        }
        self._write("manifest.json", json.dumps(self.manifest))

    def test_accepts_pinned_v1_platform_and_legacy_manifest(self) -> None:
        self.assertEqual(self.commit, MODULE.verify_source(self.release, self.tag))

    def test_rejects_v2_manifest_even_with_matching_artifacts(self) -> None:
        self.manifest["runtimeAbi"] = "V2"
        self._write("manifest.json", json.dumps(self.manifest))
        with self.assertRaisesRegex(ValueError, "different OpenCode ABI"):
            MODULE.verify_source(self.release, self.tag)

    def test_rejects_v2_gateway_and_runtime(self) -> None:
        self._write("source/deploy/internal/opencode-node-runtime.package.json", json.dumps({
            "version": "2.0.18", "dependencies": {"@opencode/client": "2.0.18"},
        }))
        self.refresh_checksums()
        with self.assertRaisesRegex(ValueError, "pinned 1.18.4 runtime"):
            MODULE.verify_source(self.release, self.tag)

    def test_rejects_tampered_backend_jar(self) -> None:
        with (self.release / "backend.jar").open("ab") as target:
            target.write(b"tampered")
        with self.assertRaisesRegex(ValueError, "backend.jar"):
            MODULE.verify_source(self.release, self.tag)

    def test_rejects_backend_without_client_module(self) -> None:
        with zipfile.ZipFile(self.release / "backend.jar", "w") as archive:
            archive.writestr("BOOT-INF/lib/unrelated.jar", b"fixture")
        self.refresh_checksums()
        with self.assertRaisesRegex(ValueError, "one OpenCode client module"):
            MODULE.verify_source(self.release, self.tag)

    def test_rejects_source_symlink_escape(self) -> None:
        gateway = self.release / "source/backend/test-agent-opencode-client/src/main/java/com/enterprise/testagent/opencode/client/GeneratedOpencodeSdkGateway.java"
        gateway.unlink()
        gateway.symlink_to("/etc/passwd")
        with self.assertRaisesRegex(ValueError, "escapes its root"):
            MODULE.verify_source(self.release, self.tag)


if __name__ == "__main__":
    unittest.main()
