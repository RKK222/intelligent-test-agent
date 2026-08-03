#!/usr/bin/env python3
"""以分析UID恢复其私有输出权限，供非特权Runner完成可验证删除。"""

from __future__ import annotations

import os
from pathlib import Path
import stat
import sys


OUTPUT_ROOT = Path("/workspace/output")
ANALYZER_IDS = {"codex", "opencode"}
RUNNER_UID = 10003
OUTPUT_GID = 10003


def normalize(root: Path) -> bool:
    try:
        resolved = root.resolve(strict=True)
        allowed = resolved == OUTPUT_ROOT or (
            resolved.parent == OUTPUT_ROOT and resolved.name in ANALYZER_IDS
        )
        if not allowed:
            return False
    except OSError:
        return False
    current_uid = os.geteuid()
    succeeded = True
    walk_errors: list[OSError] = []
    for directory, child_directories, files in os.walk(
        root,
        topdown=True,
        onerror=walk_errors.append,
        followlinks=False,
    ):
        base = Path(directory)
        safe_children: list[str] = []
        for name in child_directories:
            path = base / name
            try:
                metadata = path.lstat()
                if stat.S_ISLNK(metadata.st_mode):
                    continue
                if not stat.S_ISDIR(metadata.st_mode):
                    succeeded = False
                    continue
                if metadata.st_uid == current_uid:
                    os.chown(path, -1, OUTPUT_GID, follow_symlinks=False)
                    os.chmod(path, 0o770, follow_symlinks=False)
                elif metadata.st_uid != RUNNER_UID:
                    succeeded = False
                    continue
                safe_children.append(name)
            except OSError:
                succeeded = False
        child_directories[:] = safe_children
        for name in files:
            path = base / name
            try:
                metadata = path.lstat()
                if stat.S_ISLNK(metadata.st_mode):
                    continue
                if metadata.st_uid == current_uid:
                    os.chown(path, -1, OUTPUT_GID, follow_symlinks=False)
                    os.chmod(path, 0o660, follow_symlinks=False)
                elif metadata.st_uid != RUNNER_UID:
                    succeeded = False
            except OSError:
                succeeded = False
    return succeeded and not walk_errors


def main() -> int:
    if len(sys.argv) != 2:
        return 64
    requested = Path(sys.argv[1])
    return 0 if normalize(requested) else 74


if __name__ == "__main__":
    raise SystemExit(main())
