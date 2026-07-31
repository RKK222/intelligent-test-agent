from __future__ import annotations

from pathlib import Path
import subprocess

from testagent_runner.git_workspace import FrozenCheckout
from testagent_runner.scope_resolver import ScopeResolver


def git(directory: Path, *args: str) -> str:
    return subprocess.run(
        ["git", *args],
        cwd=directory,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def repository(tmp_path: Path) -> FrozenCheckout:
    root = tmp_path / "orders"
    (root / "src" / "orders").mkdir(parents=True)
    (root / "src" / "orders" / "service.py").write_text(
        "class OrderService:\n    pass\n",
        encoding="utf-8",
    )
    (root / "src" / "orders" / "facade.py").write_text(
        "from .service import OrderService\n",
        encoding="utf-8",
    )
    git(root, "init", "-b", "main")
    git(root, "config", "user.name", "Scope Test")
    git(root, "config", "user.email", "scope@example.test")
    git(root, "add", "-A")
    git(root, "commit", "-m", "initial")
    head = git(root, "rev-parse", "HEAD")
    return FrozenCheckout(
        repository_id="repo_12345678",
        alias="orders",
        repository_path=root,
        default_branch="main",
        default_head=head,
        target_branch="main",
        target_head=head,
        merge_base=head,
        missing_dependencies=(),
    )


def test_unique_file_scope_is_resolved_automatically(tmp_path: Path) -> None:
    result = ScopeResolver().resolve(
        [repository(tmp_path)],
        [
            {
                "repositoryId": "repo_12345678",
                "kind": "FILE",
                "value": "service.py",
            }
        ],
    )

    assert result["requiredInput"] == []
    assert result["resolvedSelectors"][0]["path"] == "src/orders/service.py"


def test_ambiguous_symbol_returns_safe_candidate_paths_for_agui_input(tmp_path: Path) -> None:
    result = ScopeResolver().resolve(
        [repository(tmp_path)],
        [
            {
                "repositoryId": "repo_12345678",
                "kind": "SYMBOL",
                "value": "OrderService",
            }
        ],
    )

    assert result["resolvedSelectors"] == []
    required = result["requiredInput"][0]
    assert required["reason"] == "AMBIGUOUS"
    assert required["candidatePaths"] == [
        "src/orders/facade.py",
        "src/orders/service.py",
    ]
    assert all(not path.startswith("/") for path in required["candidatePaths"])


def test_missing_scope_requests_more_input_without_guessing(tmp_path: Path) -> None:
    result = ScopeResolver().resolve(
        [repository(tmp_path)],
        [
            {
                "repositoryId": "repo_12345678",
                "kind": "FILE",
                "value": "missing.py",
            }
        ],
    )

    assert result["resolvedSelectors"] == []
    assert result["requiredInput"] == [
        {
            "selector": {
                "repositoryId": "repo_12345678",
                "kind": "FILE",
                "value": "missing.py",
            },
            "reason": "NOT_FOUND",
            "candidatePaths": [],
            "truncated": False,
        }
    ]
