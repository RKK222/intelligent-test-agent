#!/usr/bin/env python3
"""核对生成 SDK 的 Java 公共类型与 Git 路径大小写，避免 macOS 掩盖 Linux 编译失败。"""

from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOTS = (
    Path("tools/opencode-sdk-generator/src/main/java"),
    Path("backend/test-agent-opencode-sdk-generated/src/main/java"),
)
PUBLIC_TYPE = re.compile(
    r"(?m)^public\s+(?:abstract\s+|final\s+)?(?:class|interface|enum|record)\s+(\w+)"
)


def main() -> int:
    tracked = subprocess.check_output(
        ["git", "ls-files", "-z", "--", *(str(path) for path in SOURCE_ROOTS)],
        cwd=ROOT,
    ).decode().split("\0")
    actual = {
        path.relative_to(ROOT).as_posix(): path
        for source_root in SOURCE_ROOTS
        for path in (ROOT / source_root).rglob("*.java")
    }
    errors = []

    # 在大小写不敏感的文件系统上，exists() 无法发现 Git 索引仍保留旧类名的路径。
    for name in filter(None, tracked):
        if name.endswith(".java") and name not in actual:
            errors.append(f"Git 路径与实际文件大小写不一致或文件缺失: {name}")

    for name, path in actual.items():
        match = PUBLIC_TYPE.search(path.read_text(encoding="utf-8"))
        if match and match.group(1) != path.stem:
            errors.append(f"Java 公共类型与文件名不一致: {name} ({match.group(1)})")

    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("Generated OpenCode SDK path casing verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
