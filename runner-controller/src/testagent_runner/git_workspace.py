"""Runner节点上的冻结检出与确定性Git diff manifest。"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import os
import posixpath
import re
import shlex
import shutil
import subprocess
from typing import Any
from urllib.parse import urlsplit, urlunsplit


class GitWorkspaceError(RuntimeError):
    """Git工作区安全失败。"""


class GitBranchError(GitWorkspaceError):
    """目标或默认分支不存在。"""


class GitHistoryError(GitWorkspaceError):
    """分支历史无法按约定比较。"""


@dataclass(frozen=True, slots=True)
class AuthorizedSubmoduleSpec:
    repository_id: str
    remote_url: str
    private_key_path: str | None = None


@dataclass(frozen=True, slots=True)
class RepositoryCheckoutSpec:
    repository_id: str
    alias: str
    remote_url: str
    default_branch: str
    target_branch: str
    private_key_path: str | None = None
    authorized_submodules: tuple[AuthorizedSubmoduleSpec, ...] = ()


@dataclass(frozen=True, slots=True)
class FrozenCheckout:
    repository_id: str
    alias: str
    repository_path: Path
    default_branch: str
    default_head: str
    target_branch: str
    target_head: str
    merge_base: str
    missing_dependencies: tuple[dict[str, Any], ...]


@dataclass(frozen=True, slots=True)
class GitDiffManifest:
    files: list[dict[str, Any]]
    statistics: dict[str, int]
    hunks: list[dict[str, Any]]


class GitWorkspace:
    """只在Runner受控根目录中操作，冻结SHA后再生成分析输入。"""

    _SAFE_ALIAS = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$")

    def __init__(self, root: Path, *, known_hosts_path: Path | None = None) -> None:
        self.root = root.resolve()
        self._known_hosts_path = (
            validate_known_hosts_file(known_hosts_path) if known_hosts_path is not None else None
        )
        self.repositories_root = self.root / "repos"
        self.repositories_root.mkdir(parents=True, exist_ok=True)

    def checkout(self, spec: RepositoryCheckoutSpec) -> FrozenCheckout:
        if not self._SAFE_ALIAS.fullmatch(spec.alias):
            raise GitWorkspaceError("仓库别名不安全")
        repository_path = (self.repositories_root / spec.alias).resolve()
        if repository_path.parent != self.repositories_root:
            raise GitWorkspaceError("仓库目录越界")
        if repository_path.exists():
            raise GitWorkspaceError("仓库工作区已存在")

        environment = self._git_environment(spec.private_key_path)
        self._run(
            self.root,
            ["git", "clone", "--no-checkout", "--", spec.remote_url, str(repository_path)],
            environment,
        )
        try:
            self._run(
                repository_path,
                [
                    "git",
                    "fetch",
                    "--force",
                    "origin",
                    f"+refs/heads/{spec.default_branch}:refs/remotes/origin/{spec.default_branch}",
                    f"+refs/heads/{spec.target_branch}:refs/remotes/origin/{spec.target_branch}",
                ],
                environment,
            )
            default_head = self._rev_parse(repository_path, f"origin/{spec.default_branch}", environment)
            target_head = self._rev_parse(repository_path, f"origin/{spec.target_branch}", environment)
            merge_base = self._merge_base(repository_path, default_head, target_head, environment)
            self._run(repository_path, ["git", "checkout", "--detach", target_head], environment)
            self._pull_lfs_if_required(repository_path, target_head, environment)
            missing_dependencies = self._checkout_submodules(
                repository_path,
                spec.remote_url,
                spec.authorized_submodules,
            )
        except Exception:
            shutil.rmtree(repository_path, ignore_errors=True)
            raise

        return FrozenCheckout(
            repository_id=spec.repository_id,
            alias=spec.alias,
            repository_path=repository_path,
            default_branch=spec.default_branch,
            default_head=default_head,
            target_branch=spec.target_branch,
            target_head=target_head,
            merge_base=merge_base,
            missing_dependencies=missing_dependencies,
        )

    def build_manifest(self, repositories: list[FrozenCheckout]) -> GitDiffManifest:
        files: list[dict[str, Any]] = []
        hunks: list[dict[str, Any]] = []
        additions = 0
        deletions = 0
        binary_files = 0

        for repository in repositories:
            coordinate = f"{repository.merge_base}..{repository.target_head}"
            for change in self._name_status(repository.repository_path, coordinate):
                old_path = change.get("oldPath")
                path = str(change["path"])
                numstat = self._numstat(repository.repository_path, coordinate, path)
                binary = numstat is None
                if binary:
                    binary_files += 1
                    file_additions = 0
                    file_deletions = 0
                else:
                    file_additions, file_deletions = numstat
                    additions += file_additions
                    deletions += file_deletions
                entry = {
                    "repositoryId": repository.repository_id,
                    "repositoryAlias": repository.alias,
                    "path": path,
                    "status": change["status"],
                    "binary": binary,
                    "generated": self._looks_generated(path),
                    "additions": file_additions,
                    "deletions": file_deletions,
                }
                if old_path is not None:
                    entry["oldPath"] = old_path
                files.append(entry)
                if binary:
                    # 二进制内容不进入数据库或模型上下文；文件状态、统计和路径仍完整保留。
                    patch = "Binary file changed; content omitted."
                else:
                    patch_paths = [value for value in (old_path, path) if value]
                    patch = self._run(
                        repository.repository_path,
                        [
                            "git",
                            "diff",
                            "--no-ext-diff",
                            "--unified=3",
                            coordinate,
                            "--",
                            *patch_paths,
                        ],
                    ).stdout
                hunks.append(
                    {
                        "repositoryAlias": repository.alias,
                        "path": path,
                        "patch": patch,
                    }
                )

        files.sort(key=lambda item: (str(item["repositoryAlias"]), str(item["path"])))
        hunks.sort(key=lambda item: (str(item["repositoryAlias"]), str(item["path"])))
        return GitDiffManifest(
            files=files,
            statistics={
                "files": len(files),
                "additions": additions,
                "deletions": deletions,
                "binaryFiles": binary_files,
            },
            hunks=hunks,
        )

    def _name_status(self, repository: Path, coordinate: str) -> list[dict[str, str]]:
        raw = self._run(
            repository,
            ["git", "diff", "--name-status", "--find-renames=20%", "-z", coordinate],
            text=False,
        ).stdout
        fields = raw.decode("utf-8", errors="surrogateescape").split("\0")
        if fields and fields[-1] == "":
            fields.pop()
        changes: list[dict[str, str]] = []
        index = 0
        while index < len(fields):
            code = fields[index]
            index += 1
            if code.startswith(("R", "C")):
                old_path, path = fields[index], fields[index + 1]
                index += 2
                changes.append({"status": "RENAMED" if code.startswith("R") else "COPIED", "oldPath": old_path, "path": path})
            else:
                path = fields[index]
                index += 1
                status = {
                    "A": "ADDED",
                    "M": "MODIFIED",
                    "D": "DELETED",
                    "T": "TYPE_CHANGED",
                    "U": "UNMERGED",
                }.get(code[:1], "MODIFIED")
                changes.append({"status": status, "path": path})
        return changes

    def _numstat(self, repository: Path, coordinate: str, path: str) -> tuple[int, int] | None:
        output = self._run(
            repository,
            ["git", "diff", "--numstat", coordinate, "--", path],
        ).stdout.strip()
        if not output:
            return (0, 0)
        first = output.splitlines()[0].split("\t", 2)
        if first[0] == "-" or first[1] == "-":
            return None
        return int(first[0]), int(first[1])

    def _rev_parse(self, repository: Path, reference: str, environment: dict[str, str]) -> str:
        try:
            value = self._run(repository, ["git", "rev-parse", "--verify", reference], environment).stdout.strip()
        except GitWorkspaceError as exception:
            raise GitBranchError(f"分支不存在: {reference.removeprefix('origin/')}") from exception
        if not re.fullmatch(r"[0-9a-f]{40,64}", value):
            raise GitBranchError("远端分支HEAD无效")
        return value

    def _merge_base(
        self,
        repository: Path,
        default_head: str,
        target_head: str,
        environment: dict[str, str],
    ) -> str:
        try:
            value = self._run(
                repository,
                ["git", "merge-base", default_head, target_head],
                environment,
            ).stdout.strip()
        except GitWorkspaceError as exception:
            raise GitHistoryError("默认分支与目标分支没有共同祖先") from exception
        if not value:
            raise GitHistoryError("默认分支与目标分支没有共同祖先")
        return value

    def _pull_lfs_if_required(
        self,
        repository: Path,
        target_head: str,
        environment: dict[str, str],
    ) -> None:
        # 只读取冻结提交内受Git跟踪的属性文件，既覆盖嵌套目录，也不跟随工作树符号链接。
        attributes = self._run(
            repository,
            ["git", "ls-files", "-z", "--", ":(glob)**/.gitattributes", ".gitattributes"],
            environment,
        ).stdout.split("\0")
        uses_lfs = any(
            "filter=lfs"
            in self._run(
                repository,
                ["git", "show", f"{target_head}:{path}"],
                environment,
            ).stdout
            for path in attributes
            if path
        )
        if not uses_lfs:
            return
        self._run(repository, ["git", "lfs", "version"], environment)
        # pull只接受一个remote；当前HEAD已经detach到冻结SHA，清空include/exclude后完整拉取该ref。
        self._run(
            repository,
            ["git", "lfs", "pull", "--include=", "--exclude=", "origin"],
            environment,
        )

    def _checkout_submodules(
        self,
        repository: Path,
        parent_remote_url: str,
        authorized: tuple[AuthorizedSubmoduleSpec, ...],
        *,
        prefix: str = "",
        depth: int = 0,
    ) -> tuple[dict[str, Any], ...]:
        if depth > 8:
            return ({"path": prefix or ".", "reason": "SUBMODULE_DEPTH_EXCEEDED"},)
        modules = repository / ".gitmodules"
        if not modules.exists():
            return ()
        output = self._run(
            repository,
            [
                "git",
                "config",
                "--file",
                ".gitmodules",
                "--get-regexp",
                r"^submodule\..*\.(path|url)$",
            ],
            allow_failure=True,
        ).stdout
        entries: dict[str, dict[str, str]] = {}
        for line in output.splitlines():
            key_value = line.split(maxsplit=1)
            if len(key_value) != 2:
                continue
            match = re.fullmatch(r"submodule\.(.+)\.(path|url)", key_value[0])
            if match:
                entries.setdefault(match.group(1), {})[match.group(2)] = key_value[1]

        authorized_by_remote = {
            self._normalize_remote(value.remote_url): value for value in authorized
        }
        missing: list[dict[str, Any]] = []
        for entry in entries.values():
            path = entry.get("path")
            raw_url = entry.get("url")
            if not path or not raw_url:
                continue
            safe_path = self._safe_submodule_path(repository, path)
            display_path = posixpath.join(prefix, path) if prefix else path
            resolved_url = self._resolve_submodule_url(parent_remote_url, raw_url)
            material = authorized_by_remote.get(self._normalize_remote(resolved_url))
            if material is None:
                missing.append({"path": display_path, "reason": "UNAUTHORIZED_OR_UNMAPPED"})
                continue
            commit = self._submodule_commit(repository, path)
            environment = self._git_environment(material.private_key_path)
            if safe_path.exists():
                if not safe_path.is_dir() or any(safe_path.iterdir()):
                    raise GitWorkspaceError("submodule目标目录不是安全空目录")
                safe_path.rmdir()
            self._run(
                repository,
                ["git", "clone", "--no-checkout", "--", material.remote_url, str(safe_path)],
                environment,
            )
            try:
                present = self._run(
                    safe_path,
                    ["git", "cat-file", "-e", f"{commit}^{{commit}}"],
                    environment,
                    allow_failure=True,
                ).returncode == 0
                if not present:
                    self._run(safe_path, ["git", "fetch", "origin", commit], environment)
                self._run(safe_path, ["git", "checkout", "--detach", commit], environment)
                missing.extend(
                    self._checkout_submodules(
                        safe_path,
                        material.remote_url,
                        authorized,
                        prefix=display_path,
                        depth=depth + 1,
                    )
                )
            except Exception:
                shutil.rmtree(safe_path, ignore_errors=True)
                raise
        return tuple(missing)

    @staticmethod
    def _safe_submodule_path(repository: Path, value: str) -> Path:
        path = Path(value)
        if path.is_absolute() or ".." in path.parts or value.strip() != value:
            raise GitWorkspaceError("submodule路径不安全")
        resolved = (repository / path).resolve()
        if repository.resolve() not in resolved.parents:
            raise GitWorkspaceError("submodule路径越界")
        return resolved

    def _submodule_commit(self, repository: Path, path: str) -> str:
        output = self._run(
            repository,
            ["git", "ls-tree", "HEAD", "--", path],
        ).stdout.strip()
        match = re.fullmatch(r"160000 commit ([0-9a-f]{40,64})\t.+", output)
        if match is None:
            raise GitWorkspaceError("submodule gitlink无效")
        return match.group(1)

    @staticmethod
    def _resolve_submodule_url(parent_remote_url: str, value: str) -> str:
        if not value.startswith(("./", "../")):
            return value
        if "://" in parent_remote_url:
            parsed = urlsplit(parent_remote_url)
            parent_path = posixpath.dirname(parsed.path)
            resolved_path = posixpath.normpath(posixpath.join(parent_path, value))
            return urlunsplit((parsed.scheme, parsed.netloc, resolved_path, "", ""))
        scp_match = re.fullmatch(r"(?:(?P<user>[^@/:]+)@)?(?P<host>[^/:]+):(?P<path>.+)", parent_remote_url)
        if scp_match:
            resolved_path = posixpath.normpath(
                posixpath.join(posixpath.dirname(scp_match.group("path")), value)
            )
            user = f"{scp_match.group('user')}@" if scp_match.group("user") else ""
            return f"{user}{scp_match.group('host')}:{resolved_path}"
        return str((Path(parent_remote_url).parent / value).resolve())

    @staticmethod
    def _normalize_remote(value: str) -> str:
        normalized = value.strip().rstrip("/")
        if "://" in normalized:
            parsed = urlsplit(normalized)
            host = parsed.hostname or ""
            port = f":{parsed.port}" if parsed.port else ""
            path = parsed.path.rstrip("/")
            if path.endswith(".git"):
                path = path[:-4]
            return f"{parsed.scheme.lower()}://{host.lower()}{port}{path}"
        scp_match = re.fullmatch(r"(?:[^@/:]+@)?(?P<host>[^/:]+):(?P<path>.+)", normalized)
        if scp_match:
            path = scp_match.group("path").removesuffix(".git")
            return f"ssh://{scp_match.group('host').lower()}/{path}"
        try:
            return str(Path(normalized).resolve()).removesuffix(".git")
        except OSError:
            return normalized.removesuffix(".git")

    @staticmethod
    def _looks_generated(path: str) -> bool:
        normalized = path.lower()
        name = Path(normalized).name
        return (
            "/generated/" in f"/{normalized}/"
            or name.endswith((".min.js", ".min.css", ".designer.cs"))
            or name in {"package-lock.json", "pnpm-lock.yaml", "yarn.lock", "uv.lock"}
        )

    def _git_environment(self, private_key_path: str | None) -> dict[str, str]:
        environment = os.environ.copy()
        environment.update(
            {
                "GIT_TERMINAL_PROMPT": "0",
                "GIT_CONFIG_NOSYSTEM": "1",
                "GIT_CONFIG_GLOBAL": "/dev/null",
                "GIT_CONFIG_SYSTEM": "/dev/null",
                "GIT_ASKPASS": "/bin/false",
                "SSH_ASKPASS": "/bin/false",
                "GIT_LFS_SKIP_SMUDGE": "0",
            }
        )
        if private_key_path:
            if self._known_hosts_path is None:
                raise GitWorkspaceError("SSH检出必须配置受信任known_hosts")
            environment["GIT_SSH_COMMAND"] = (
                f"ssh -i {shlex.quote(private_key_path)} -o IdentitiesOnly=yes "
                f"-o UserKnownHostsFile={shlex.quote(str(self._known_hosts_path))} "
                "-o GlobalKnownHostsFile=/dev/null -o StrictHostKeyChecking=yes -o BatchMode=yes"
            )
        return environment

    @staticmethod
    def _run(
        directory: Path,
        command: list[str],
        environment: dict[str, str] | None = None,
        *,
        text: bool = True,
        allow_failure: bool = False,
    ) -> subprocess.CompletedProcess[Any]:
        result = subprocess.run(
            command,
            cwd=directory,
            env=environment,
            capture_output=True,
            text=text,
            timeout=600,
            check=False,
        )
        if result.returncode != 0 and not allow_failure:
            # Git URL和凭据可能出现在stderr，异常不回显命令、URL或原始输出。
            raise GitWorkspaceError(f"Git命令失败，退出码 {result.returncode}")
        return result


def validate_known_hosts_file(path: Path) -> Path:
    """拒绝可被低权限主体替换的SSH主机信任文件。"""

    if path.is_symlink():
        raise GitWorkspaceError("known_hosts禁止使用符号链接")
    try:
        resolved = path.resolve(strict=True)
        stat = resolved.stat()
    except OSError as exception:
        raise GitWorkspaceError("known_hosts文件不存在或不可读") from exception
    if not resolved.is_file() or not 0 < stat.st_size <= 1024 * 1024:
        raise GitWorkspaceError("known_hosts文件为空、过大或不是普通文件")
    if stat.st_mode & 0o022:
        raise GitWorkspaceError("known_hosts文件权限禁止group或other写入")
    try:
        with resolved.open("rb") as stream:
            stream.read(1)
    except OSError as exception:
        raise GitWorkspaceError("known_hosts文件不可读") from exception
    return resolved
