#!/usr/bin/env python3
"""校验只读 V1 平台发布包，避免把 V2 后端与 V1 Worker 组合成伪回滚。"""

import argparse
import hashlib
import io
import json
import re
import sys
import zipfile
from pathlib import Path


def fail(message: str) -> None:
    raise ValueError(message)


def regular_file(path: Path) -> Path:
    if not path.is_file() or path.is_symlink():
        fail(f"Missing or linked V1 platform artifact: {path}")
    return path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with regular_file(path).open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def checked_member(root: Path, relative: str, prefix: str) -> Path:
    member = Path(relative)
    if member.is_absolute() or ".." in member.parts or not relative.startswith(prefix):
        fail(f"Unsafe V1 release checksum path: {relative}")
    path = root / member
    if not path.resolve().is_relative_to(root.resolve()):
        fail(f"V1 release checksum escapes its root: {relative}")
    return regular_file(path)


def verify_checksum_list(root: Path, name: str, prefix: str) -> None:
    lines = regular_file(root / name).read_text(encoding="utf-8").splitlines()
    if not lines:
        fail(f"Empty V1 release checksum list: {name}")
    seen: set[str] = set()
    for line in lines:
        if not re.fullmatch(r"[0-9a-f]{64}  .+", line):
            fail(f"Malformed V1 release checksum entry: {name}")
        expected, relative = line[:64], line[66:]
        if relative in seen:
            fail(f"Duplicate V1 release checksum entry: {relative}")
        seen.add(relative)
        if sha256(checked_member(root, relative, prefix)) != expected:
            fail(f"V1 release checksum mismatch: {relative}")


def verify_source(root: Path, tag: str) -> str:
    """返回已验证 V1 发布包的源码提交；不要求旧 Worker 镜像仍在宿主机。"""
    if root.is_symlink() or not root.is_dir():
        fail(f"Missing or linked V1 platform release: {root}")
    if not re.fullmatch(r"release-[1-9][0-9]*-[0-9a-f]{8}", tag) or root.name != tag:
        fail("Invalid V1 platform release tag or directory")
    manifest = json.loads(regular_file(root / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") not in (1, 2) or manifest.get("tag") != tag:
        fail("Invalid V1 platform release manifest")
    commit = manifest.get("commit")
    if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-f]{40}", commit):
        fail("Invalid V1 platform source commit")
    if not tag.endswith(commit[:8]):
        fail("V1 platform release tag does not match its source commit")
    if manifest.get("runtimeAbi", "V1") != "V1" or manifest.get("runtimeVersion", "1.18.4") != "1.18.4":
        fail("V1 platform release manifest identifies a different OpenCode ABI")
    for name, field in (
        ("backend.jar", "backendJarSha256"),
        ("nginx.conf", "nginxSha256"),
        ("stack.json", "stackSha256"),
    ):
        if sha256(root / name) != manifest.get(field):
            fail(f"V1 platform artifact checksum mismatch: {name}")
    if manifest["schemaVersion"] == 2 and sha256(root / "worker-stack.json") != manifest.get("workerStackSha256"):
        fail("V1 platform worker manifest checksum mismatch")
    verify_checksum_list(root, "frontend.sha256", "frontend/")
    verify_checksum_list(root, "source.sha256", "source/")

    # 旧清单没有 runtimeAbi；用随发布包冻结的依赖与网关源码确认后端协议身份。
    package = json.loads(regular_file(root / "source/deploy/internal/opencode-node-runtime.package.json").read_text(encoding="utf-8"))
    deps = package.get("dependencies", {})
    if package.get("version") != "1.18.4" or deps.get("@opencode-ai/sdk") != "1.18.4" or deps.get("@opencode-ai/plugin") != "1.18.4":
        fail("V1 platform source does not contain the pinned 1.18.4 runtime")
    gateway = regular_file(root / "source/backend/test-agent-opencode-client/src/main/java/com/enterprise/testagent/opencode/client/GeneratedOpencodeSdkGateway.java").read_text(encoding="utf-8")
    if "globalHealth()" not in gateway or '"/session"' not in gateway:
        fail("V1 platform source does not contain the V1 Java gateway")
    with zipfile.ZipFile(regular_file(root / "backend.jar")) as archive:
        clients = [name for name in archive.namelist() if re.fullmatch(r"BOOT-INF/lib/test-agent-opencode-client-[^/]+\.jar", name)]
        if len(clients) != 1:
            fail("V1 platform backend JAR must contain one OpenCode client module")
        with zipfile.ZipFile(io.BytesIO(archive.read(clients[0]))) as client:
            if "com/enterprise/testagent/opencode/client/GeneratedOpencodeSdkGateway.class" not in client.namelist():
                fail("V1 platform backend JAR does not contain its OpenCode gateway")
    regular_file(root / "frontend/index.html")
    return commit


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("release_dir", type=Path)
    parser.add_argument("tag")
    args = parser.parse_args()
    try:
        print(verify_source(args.release_dir, args.tag))
    except (ValueError, OSError, KeyError, json.JSONDecodeError, zipfile.BadZipFile) as error:
        print(f"V1 rollback source rejected: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
