from __future__ import annotations

import importlib.util
import os
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "test-agent-clean-output.py"
SPEC = importlib.util.spec_from_file_location("test_agent_clean_output", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def test_normalizes_analyzer_owned_private_tree_without_following_symlinks(
    tmp_path: Path,
) -> None:
    root = tmp_path / "output"
    private = root / "codex/home/.codex"
    private.mkdir(parents=True)
    state = private / "session.json"
    state.write_text("{}", encoding="utf-8")
    private.chmod(0o700)
    state.chmod(0o600)
    victim = tmp_path / "victim"
    victim.write_text("unchanged", encoding="utf-8")
    (root / "codex/link").symlink_to(victim)
    MODULE.OUTPUT_ROOT = root.resolve()
    MODULE.OUTPUT_GID = os.getgid()
    MODULE.RUNNER_UID = os.geteuid() + 1

    assert MODULE.normalize(root) is True

    assert private.stat().st_mode & 0o777 == 0o770
    assert state.stat().st_mode & 0o777 == 0o660
    assert victim.read_text(encoding="utf-8") == "unchanged"


def test_rejects_a_path_outside_the_fixed_output_tree(tmp_path: Path) -> None:
    MODULE.OUTPUT_ROOT = (tmp_path / "output").resolve()
    MODULE.OUTPUT_ROOT.mkdir()
    outside = tmp_path / "outside"
    outside.mkdir()

    assert MODULE.normalize(outside) is False
