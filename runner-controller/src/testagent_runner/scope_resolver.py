"""在冻结源码上确定性解析局部重分析范围，不把路径决策交给模型。"""

from __future__ import annotations

from pathlib import Path
import subprocess
from typing import Any

from testagent_runner.git_workspace import FrozenCheckout


class ScopeResolver:
    """唯一匹配自动执行；零匹配或多匹配返回可直接渲染的AG-UI输入数据。"""

    _MAX_CANDIDATES = 50

    def resolve(
        self,
        repositories: list[FrozenCheckout],
        selectors: list[dict[str, Any]],
    ) -> dict[str, list[dict[str, Any]]]:
        by_id = {value.repository_id: value for value in repositories}
        resolved: list[dict[str, Any]] = []
        required: list[dict[str, Any]] = []
        for selector in selectors:
            repository_id = str(selector.get("repositoryId", ""))
            kind = str(selector.get("kind", ""))
            value = str(selector.get("value", "")).strip()
            repository = by_id.get(repository_id)
            if repository is None or not value:
                required.append(self._required(selector, "NOT_FOUND", []))
                continue
            candidates = self._candidates(repository, kind, value)
            if len(candidates) == 1:
                resolved.append(
                    {
                        "repositoryId": repository.repository_id,
                        "repositoryAlias": repository.alias,
                        "kind": kind,
                        "value": value,
                        "path": candidates[0],
                    }
                )
                continue
            required.append(
                self._required(
                    selector,
                    "NOT_FOUND" if not candidates else "AMBIGUOUS",
                    candidates,
                )
            )
        return {"resolvedSelectors": resolved, "requiredInput": required}

    def _candidates(
        self,
        repository: FrozenCheckout,
        kind: str,
        value: str,
    ) -> list[str]:
        if kind == "SYMBOL":
            return self._symbol_candidates(repository, value)
        root = repository.repository_path
        normalized = value.replace("\\", "/").strip("/")
        candidates: set[str] = set()
        for path in root.rglob("*"):
            relative = path.relative_to(root)
            if ".git" in relative.parts or path.is_symlink():
                continue
            relative_text = relative.as_posix()
            if kind == "FILE" and path.is_file():
                if self._path_matches(relative_text, path.name, normalized):
                    candidates.add(relative_text)
            elif kind == "DIRECTORY" and path.is_dir():
                if self._path_matches(relative_text, path.name, normalized):
                    candidates.add(relative_text)
            elif kind in {"PROGRAM", "MODULE"}:
                if self._path_matches(relative_text, path.name, normalized) or path.stem == normalized:
                    candidates.add(relative_text)
        return sorted(candidates)

    @staticmethod
    def _path_matches(relative: str, name: str, requested: str) -> bool:
        return relative == requested or relative.endswith("/" + requested) or name == requested

    def _symbol_candidates(self, repository: FrozenCheckout, symbol: str) -> list[str]:
        result = subprocess.run(
            [
                "git",
                "grep",
                "-z",
                "-l",
                "-I",
                "-F",
                "-e",
                symbol,
                "--",
            ],
            cwd=repository.repository_path,
            capture_output=True,
            timeout=120,
            check=False,
        )
        if result.returncode == 1:
            return []
        if result.returncode != 0:
            return []
        return sorted(
            {
                value.decode("utf-8", errors="surrogateescape")
                for value in result.stdout.split(b"\0")
                if value and not value.startswith(b".git/")
            }
        )

    def _required(
        self,
        selector: dict[str, Any],
        reason: str,
        candidates: list[str],
    ) -> dict[str, Any]:
        return {
            "selector": {
                "repositoryId": selector.get("repositoryId"),
                "kind": selector.get("kind"),
                "value": selector.get("value"),
            },
            "reason": reason,
            "candidatePaths": candidates[: self._MAX_CANDIDATES],
            "truncated": len(candidates) > self._MAX_CANDIDATES,
        }
