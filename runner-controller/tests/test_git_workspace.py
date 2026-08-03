from __future__ import annotations

from pathlib import Path
import subprocess

import pytest

from testagent_runner.git_workspace import (
    AuthorizedSubmoduleSpec,
    GitHistoryError,
    GitWorkspace,
    GitWorkspaceError,
    RepositoryCheckoutSpec,
)


def git(directory: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=directory,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def commit(directory: Path, message: str) -> str:
    git(directory, "add", "-A")
    git(directory, "commit", "-m", message)
    return git(directory, "rev-parse", "HEAD")


@pytest.fixture
def source_repo(tmp_path: Path) -> Path:
    repository = tmp_path / "origin"
    repository.mkdir()
    git(repository, "init", "-b", "main")
    git(repository, "config", "user.name", "Runner Test")
    git(repository, "config", "user.email", "runner@example.test")
    (repository / "order.py").write_text("def submit():\n    return 'old'\n")
    commit(repository, "initial")
    git(repository, "checkout", "-b", "feature/impact")
    (repository / "order.py").write_text("def submit():\n    return 'new'\n")
    (repository / "contract.json").write_text('{"version": 2}\n')
    commit(repository, "feature")
    git(repository, "checkout", "main")
    return repository


def test_freezes_exact_heads_and_builds_deterministic_manifest(
    tmp_path: Path,
    source_repo: Path,
) -> None:
    workspace = GitWorkspace(tmp_path / "workspace")

    frozen = workspace.checkout(
        RepositoryCheckoutSpec(
            repository_id="repo_12345678",
            alias="orders",
            remote_url=str(source_repo),
            default_branch="main",
            target_branch="feature/impact",
        )
    )
    manifest = workspace.build_manifest([frozen])

    assert frozen.default_head == git(source_repo, "rev-parse", "main")
    assert frozen.target_head == git(source_repo, "rev-parse", "feature/impact")
    assert frozen.merge_base == frozen.default_head
    assert [item["path"] for item in manifest.files] == ["contract.json", "order.py"]
    assert manifest.statistics == {"files": 2, "additions": 2, "deletions": 1, "binaryFiles": 0}
    assert all(item["repositoryAlias"] == "orders" for item in manifest.files)
    assert any("return 'new'" in hunk["patch"] for hunk in manifest.hunks)


def test_manifest_tracks_renames_and_binary_files(tmp_path: Path, source_repo: Path) -> None:
    git(source_repo, "checkout", "feature/impact")
    git(source_repo, "mv", "order.py", "order_service.py")
    (source_repo / "logo.bin").write_bytes(bytes(range(256)))
    commit(source_repo, "rename and binary")
    workspace = GitWorkspace(tmp_path / "workspace")
    frozen = workspace.checkout(
        RepositoryCheckoutSpec(
            "repo_12345678", "orders", str(source_repo), "main", "feature/impact"
        )
    )

    manifest = workspace.build_manifest([frozen])

    renamed = next(item for item in manifest.files if item["path"] == "order_service.py")
    binary = next(item for item in manifest.files if item["path"] == "logo.bin")
    assert renamed["status"] == "RENAMED"
    assert renamed["oldPath"] == "order.py"
    assert binary["binary"] is True
    assert manifest.statistics["binaryFiles"] == 1
    binary_hunk = next(item for item in manifest.hunks if item["path"] == "logo.bin")
    assert binary_hunk["patch"] == "Binary file changed; content omitted."


def test_unrelated_histories_fail_closed(tmp_path: Path, source_repo: Path) -> None:
    git(source_repo, "checkout", "--orphan", "unrelated")
    git(source_repo, "rm", "-rf", ".")
    (source_repo / "other.txt").write_text("unrelated\n")
    commit(source_repo, "unrelated")
    workspace = GitWorkspace(tmp_path / "workspace")

    with pytest.raises(GitHistoryError, match="共同祖先"):
        workspace.checkout(
            RepositoryCheckoutSpec(
                "repo_12345678", "orders", str(source_repo), "main", "unrelated"
            )
        )


def test_lfs_pull_detects_nested_attributes_and_uses_the_frozen_current_ref(
    tmp_path: Path,
) -> None:
    class RecordingLfsWorkspace(GitWorkspace):
        def __init__(self, root: Path) -> None:
            super().__init__(root)
            self.commands: list[list[str]] = []

        def _run(  # type: ignore[override]
            self,
            directory: Path,
            command: list[str],
            environment: dict[str, str],
        ) -> subprocess.CompletedProcess[str]:
            del directory, environment
            self.commands.append(command)
            if command[1:3] == ["ls-files", "-z"]:
                return subprocess.CompletedProcess(command, 0, "assets/.gitattributes\0", "")
            if command[1] == "show":
                return subprocess.CompletedProcess(
                    command,
                    0,
                    "*.bin filter=lfs diff=lfs merge=lfs -text\n",
                    "",
                )
            return subprocess.CompletedProcess(command, 0, "", "")

    repository = tmp_path / "repository"
    repository.mkdir()
    workspace = RecordingLfsWorkspace(tmp_path / "workspace-lfs")
    frozen_head = "a" * 40

    workspace._pull_lfs_if_required(repository, frozen_head, {})

    assert workspace.commands == [
        ["git", "ls-files", "-z", "--", ":(glob)**/.gitattributes", ".gitattributes"],
        ["git", "show", f"{frozen_head}:assets/.gitattributes"],
        ["git", "lfs", "version"],
        ["git", "lfs", "pull", "--include=", "--exclude=", "origin"],
    ]


def submodule_parent(tmp_path: Path) -> tuple[Path, Path]:
    dependency = tmp_path / "dependency-origin"
    dependency.mkdir()
    git(dependency, "init", "-b", "main")
    git(dependency, "config", "user.name", "Runner Test")
    git(dependency, "config", "user.email", "runner@example.test")
    (dependency / "contract.py").write_text("VERSION = 1\n")
    commit(dependency, "dependency")

    parent = tmp_path / "parent-origin"
    parent.mkdir()
    git(parent, "init", "-b", "main")
    git(parent, "config", "user.name", "Runner Test")
    git(parent, "config", "user.email", "runner@example.test")
    (parent / "app.py").write_text("print('main')\n")
    commit(parent, "initial")
    subprocess.run(
        [
            "git",
            "-c",
            "protocol.file.allow=always",
            "submodule",
            "add",
            str(dependency),
            "vendor/contracts",
        ],
        cwd=parent,
        check=True,
        capture_output=True,
        text=True,
    )
    commit(parent, "add dependency")
    git(parent, "checkout", "-b", "feature/impact")
    (parent / "app.py").write_text("print('feature')\n")
    commit(parent, "feature")
    git(parent, "checkout", "main")
    return parent, dependency


def test_authorized_platform_submodule_is_checked_out_at_gitlink_commit(tmp_path: Path) -> None:
    parent, dependency = submodule_parent(tmp_path)
    workspace = GitWorkspace(tmp_path / "workspace-authorized")

    frozen = workspace.checkout(
        RepositoryCheckoutSpec(
            "repo_parent_12345678",
            "parent",
            str(parent),
            "main",
            "feature/impact",
            authorized_submodules=(
                AuthorizedSubmoduleSpec("repo_dependency_12345678", str(dependency)),
            ),
        )
    )

    assert (frozen.repository_path / "vendor/contracts/contract.py").read_text() == "VERSION = 1\n"
    assert frozen.missing_dependencies == ()


def test_unmapped_submodule_is_not_fetched_and_only_safe_path_is_reported(tmp_path: Path) -> None:
    parent, dependency = submodule_parent(tmp_path)
    workspace = GitWorkspace(tmp_path / "workspace-unauthorized")

    frozen = workspace.checkout(
        RepositoryCheckoutSpec(
            "repo_parent_12345678",
            "parent",
            str(parent),
            "main",
            "feature/impact",
        )
    )

    assert not (frozen.repository_path / "vendor/contracts/contract.py").exists()
    assert frozen.missing_dependencies == (
        {"path": "vendor/contracts", "reason": "UNAUTHORIZED_OR_UNMAPPED"},
    )
    assert str(dependency) not in str(frozen.missing_dependencies)


def test_ssh_checkout_uses_only_explicit_strict_known_hosts(tmp_path: Path) -> None:
    known_hosts = tmp_path / "known_hosts"
    known_hosts.write_text("git.example.test ssh-ed25519 AAAATEST\n", encoding="utf-8")
    known_hosts.chmod(0o444)
    workspace = GitWorkspace(tmp_path / "workspace-known-hosts", known_hosts_path=known_hosts)

    environment = workspace._git_environment("/run/credentials/checkout-key")

    command = environment["GIT_SSH_COMMAND"]
    assert "StrictHostKeyChecking=yes" in command
    assert f"UserKnownHostsFile={known_hosts.resolve()}" in command
    assert "GlobalKnownHostsFile=/dev/null" in command
    assert "accept-new" not in command
    assert environment["GIT_CONFIG_GLOBAL"] == "/dev/null"
    assert environment["GIT_ASKPASS"] == "/bin/false"


def test_ssh_checkout_fails_closed_without_trusted_known_hosts(tmp_path: Path) -> None:
    workspace = GitWorkspace(tmp_path / "workspace-no-known-hosts")

    with pytest.raises(GitWorkspaceError, match="known_hosts"):
        workspace._git_environment("/run/credentials/checkout-key")


@pytest.mark.parametrize("mode", [0o466, 0o666])
def test_rejects_writable_known_hosts_file(tmp_path: Path, mode: int) -> None:
    known_hosts = tmp_path / "known_hosts"
    known_hosts.write_text("git.example.test ssh-ed25519 AAAATEST\n", encoding="utf-8")
    known_hosts.chmod(mode)

    with pytest.raises(GitWorkspaceError, match="权限"):
        GitWorkspace(tmp_path / "workspace-unsafe-known-hosts", known_hosts_path=known_hosts)
