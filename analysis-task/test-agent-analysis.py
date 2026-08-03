#!/usr/bin/env python3
"""在只读源码容器中调用Codex/OpenCode并规范化结构化结果。"""

from __future__ import annotations

import json
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Any
from urllib.parse import urlsplit


MAX_TOOL_OUTPUT_BYTES = 50 * 1024 * 1024
MAX_GIT_COMMAND_OUTPUT_BYTES = 2 * 1024 * 1024
MAX_PROMPT_DIFF_BYTES = 12 * 1024
MAX_PROMPT_CHANGED_FILE_BYTES = 8 * 1024
MAX_PROMPT_CHANGED_FILES = 200
MODEL_NAME_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$")
REPOSITORY_ALIAS_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
COMMIT_ID_PATTERN = re.compile(r"^[0-9a-f]{40,64}$")
OPENCODE_PROVIDER_ID = "test-agent-workflow"
SAFE_OPENCODE_SHELL = "/usr/local/bin/test-agent-safe-shell"


def main() -> int:
    # 与Runner控制器通过gid 10003共享输出，禁止产物落到other权限。
    os.umask(0o007)
    if len(sys.argv) != 4:
        return 64
    analyzer_id, request_name, result_name = sys.argv[1:]
    if analyzer_id not in {"codex", "opencode"}:
        return 64
    request_path = _safe_output_path(Path(request_name))
    result_path = _safe_output_path(Path(result_name))
    request = json.loads(request_path.read_text(encoding="utf-8"))
    relay_token_path = _safe_output_path(Path(request["modelRelayTokenFile"]))
    relay_token = relay_token_path.read_text(encoding="utf-8").strip()
    relay_token_path.unlink(missing_ok=True)
    environment = _tool_environment(request, relay_token)
    _prepare_tool_home(analyzer_id, request, environment)
    repository_context = collect_repository_context(request)
    request["outputSchema"] = constrain_output_schema(
        request.get("outputSchema"),
        repository_context,
    )
    prompt = _prompt(request, repository_context)
    analyzer_root = result_path.parent
    log_path = analyzer_root / "tool.log"
    raw_path = analyzer_root / "last-message.txt"
    schema_path = analyzer_root / "output-schema.json"
    schema_path.write_text(json.dumps(request["outputSchema"], ensure_ascii=False), encoding="utf-8")

    command = _tool_command(analyzer_id, request, prompt, schema_path, raw_path)
    with log_path.open("wb") as log:
        completed = subprocess.run(
            command,
            cwd="/workspace/repos",
            env=environment,
            stdin=subprocess.DEVNULL,
            stdout=log,
            stderr=subprocess.STDOUT,
            check=False,
        )
    if completed.returncode != 0:
        return 70
    raw = raw_path.read_text(encoding="utf-8") if raw_path.exists() else log_path.read_text(
        encoding="utf-8", errors="replace"
    )
    if len(raw.encode()) > MAX_TOOL_OUTPUT_BYTES:
        return 74
    result = extract_structured_result(raw)
    validate_result_evidence(result, repository_context)
    temporary = result_path.with_suffix(".tmp")
    temporary.write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")
    temporary.replace(result_path)
    return 0


def extract_structured_result(raw: str) -> dict[str, Any]:
    """兼容纯JSON、Markdown代码块及OpenCode JSONL中的嵌套文本。"""

    candidates: list[str] = [raw.strip()]
    for line in raw.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            candidates.append(line)
            continue
        candidates.extend(_nested_strings(event))
        if isinstance(event, dict):
            candidates.append(json.dumps(event, ensure_ascii=False))
    decoder = json.JSONDecoder()
    for candidate in reversed(candidates):
        cleaned = candidate.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
        for index, character in enumerate(cleaned):
            if character != "{":
                continue
            try:
                value, _ = decoder.raw_decode(cleaned[index:])
            except json.JSONDecodeError:
                continue
            if isinstance(value, dict) and _looks_like_result(value):
                return value
    raise ValueError("智能体没有返回约定的结构化JSON")


def _nested_strings(value: Any) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, list):
        return [item for child in value for item in _nested_strings(child)]
    if isinstance(value, dict):
        return [item for child in value.values() for item in _nested_strings(child)]
    return []


def _looks_like_result(value: dict[str, Any]) -> bool:
    required = {
        "summary",
        "impactedFeatures",
        "crossRepositoryImpacts",
        "risks",
        "codeEvidence",
        "recommendedRegressionTests",
        "uncertainties",
    }
    return required <= value.keys()


def _prompt(
    request: dict[str, Any],
    repository_context: list[dict[str, Any]] | None = None,
) -> str:
    repair = request.get("repairResult")
    repair_instruction = (
        "上次输出未通过Schema，请只修复格式和缺失字段："
        + json.dumps(repair, ensure_ascii=False)
        if repair is not None
        else ""
    )
    return "\n".join(
        [
            "你正在执行代码变动影响分析。/workspace/repos 下每个一级目录是一个只读仓库。",
            "开始分析前必须先调用当前代码智能体的仓库读取工具：Codex必须调用exec_command，"
            "OpenCode必须调用bash/read/grep；没有实际工具结果时禁止输出分析结论。",
            "至少对每个仓库执行git diff --stat mergeBase..targetHead和"
            "git diff mergeBase..targetHead，并继续读取受影响文件、调用方和测试。",
            "只能分析冻结坐标 mergeBase..targetHead，不得修改源码，不得访问互联网或其他地址。",
            "工具调用失败或无法取得diff时必须在uncertainties中如实说明，"
            "严禁推测、举例或编造文件、行号、符号与业务影响。",
            "联合分析跨仓库调用、接口与契约影响；每项结论给出仓库、文件、行号或符号证据。",
            "codeEvidence每项必须使用精确字段repositoryAlias和path；path必须逐字复制仓库相对路径，"
            "不得翻译、缩写、改名或省略目录。文档/配置变更不得推断成已实现的运行时代码功能。",
            "输出必须严格符合JSON Schema，不要使用Markdown包裹。",
            "仓库坐标：" + json.dumps(request.get("repositories", []), ensure_ascii=False),
            "冻结差异证据（分析入口已只读执行Git，必须以此为准）："
            + json.dumps(_public_repository_context(repository_context or []), ensure_ascii=False),
            "局部范围：" + json.dumps(request.get("scopeSelectors", []), ensure_ascii=False),
            "JSON Schema：" + json.dumps(request.get("outputSchema", {}), ensure_ascii=False),
            repair_instruction,
        ]
    )


def collect_repository_context(
    request: dict[str, Any],
    repository_root: Path = Path("/workspace/repos"),
) -> list[dict[str, Any]]:
    """在模型调用前读取冻结Git差异，避免弱模型凭空补造源码证据。"""

    repositories = request.get("repositories")
    if not isinstance(repositories, list) or not repositories:
        raise ValueError("分析请求缺少冻结仓库坐标")
    root = repository_root.resolve(strict=True)
    contexts: list[dict[str, Any]] = []
    remaining_diff_bytes = MAX_PROMPT_DIFF_BYTES
    remaining_file_bytes = MAX_PROMPT_CHANGED_FILE_BYTES
    for value in repositories:
        if not isinstance(value, dict):
            raise ValueError("冻结仓库坐标格式无效")
        alias = str(value.get("repositoryAlias", ""))
        merge_base = str(value.get("mergeBase", ""))
        target_head = str(value.get("targetHead", ""))
        if not REPOSITORY_ALIAS_PATTERN.fullmatch(alias):
            raise ValueError("冻结仓库别名格式无效")
        if not COMMIT_ID_PATTERN.fullmatch(merge_base) or not COMMIT_ID_PATTERN.fullmatch(
            target_head
        ):
            raise ValueError("冻结提交坐标格式无效")
        repository = (root / alias).resolve(strict=True)
        try:
            repository.relative_to(root)
        except ValueError as exception:
            raise ValueError("冻结仓库路径越界") from exception
        coordinate = f"{merge_base}..{target_head}"
        stat = _run_git(repository, "diff", "--no-ext-diff", "--no-textconv", "--stat", coordinate)
        raw_names = _run_git(
            repository,
            "diff",
            "--no-ext-diff",
            "--no-textconv",
            "--name-only",
            "-z",
            coordinate,
        )
        changed_files = [item for item in raw_names.split("\0") if item]
        prompt_files: list[str] = []
        for changed_file in changed_files:
            encoded_size = len(changed_file.encode("utf-8")) + 1
            if (
                len(prompt_files) >= MAX_PROMPT_CHANGED_FILES
                or encoded_size > remaining_file_bytes
            ):
                break
            prompt_files.append(changed_file)
            remaining_file_bytes -= encoded_size
        changed_files_truncated = len(prompt_files) != len(changed_files)
        diff = ""
        if prompt_files and remaining_diff_bytes > 0:
            diff = _run_git(
                repository,
                "diff",
                "--no-ext-diff",
                "--no-textconv",
                "--unified=12",
                coordinate,
                "--",
                *prompt_files,
            )
        encoded_diff = diff.encode("utf-8")
        diff_truncated = len(encoded_diff) > remaining_diff_bytes
        if diff_truncated:
            diff = encoded_diff[:remaining_diff_bytes].decode("utf-8", errors="ignore")
            remaining_diff_bytes = 0
        else:
            remaining_diff_bytes -= len(encoded_diff)
        contexts.append(
            {
                "repositoryId": str(value.get("repositoryId", "")),
                "repositoryAlias": alias,
                "mergeBase": merge_base,
                "targetHead": target_head,
                "diffStat": stat,
                "changedFiles": prompt_files,
                "changedFilesTruncated": changed_files_truncated,
                "diffExcerpt": diff,
                "diffTruncated": diff_truncated or changed_files_truncated,
                "_repositoryPath": str(repository),
                "_allChangedFiles": changed_files,
            }
        )
    return contexts


def _public_repository_context(
    repository_context: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """只把冻结证据送给模型，不暴露Runner内部仓库绝对路径。"""

    return [
        {key: value for key, value in context.items() if not key.startswith("_")}
        for context in repository_context
    ]


def constrain_output_schema(
    output_schema: Any,
    repository_context: list[dict[str, Any]],
) -> dict[str, Any]:
    """收紧通用分析结果中的证据字段，阻止模型用任意别名绕过校验。"""

    if not isinstance(output_schema, dict):
        raise ValueError("分析输出Schema格式无效")
    schema = json.loads(json.dumps(output_schema))
    properties = schema.get("properties")
    evidence = properties.get("codeEvidence") if isinstance(properties, dict) else None
    items = evidence.get("items") if isinstance(evidence, dict) else None
    if not isinstance(items, dict):
        raise ValueError("分析输出Schema缺少codeEvidence对象定义")
    aliases = [str(value["repositoryAlias"]) for value in repository_context]
    item_properties = items.setdefault("properties", {})
    if not isinstance(item_properties, dict):
        raise ValueError("分析输出Schema的codeEvidence属性定义无效")
    item_properties["repositoryAlias"] = {
        "type": "string",
        "enum": aliases,
        "description": "必须逐字复制冻结仓库的repositoryAlias",
    }
    item_properties["path"] = {
        "type": "string",
        "minLength": 1,
        "description": "冻结仓库内的精确相对路径，不得翻译、缩写或改名",
    }
    required = items.setdefault("required", [])
    if not isinstance(required, list):
        raise ValueError("分析输出Schema的codeEvidence必填定义无效")
    for name in ("repositoryAlias", "path"):
        if name not in required:
            required.append(name)
    if any(context.get("_allChangedFiles") for context in repository_context):
        evidence["minItems"] = 1
    return schema


def validate_result_evidence(
    result: dict[str, Any],
    repository_context: list[dict[str, Any]],
) -> None:
    """拒绝不存在或越界的代码证据；删除文件可由冻结changedFiles证明。"""

    evidence = result.get("codeEvidence")
    has_changes = any(context.get("_allChangedFiles") for context in repository_context)
    if not isinstance(evidence, list) or (has_changes and not evidence):
        raise ValueError("非空差异必须返回代码证据")
    for item in evidence:
        if not isinstance(item, dict):
            raise ValueError("代码证据格式无效")
        raw_path = next(
            (
                item.get(name)
                for name in ("path", "file", "filePath")
                if isinstance(item.get(name), str) and item.get(name)
            ),
            None,
        )
        if raw_path is None or not _evidence_path_exists(raw_path, item, repository_context):
            raise ValueError("代码证据文件不属于冻结仓库")


def _evidence_path_exists(
    raw_path: str,
    item: dict[str, Any],
    repository_context: list[dict[str, Any]],
) -> bool:
    if "\0" in raw_path or Path(raw_path).is_absolute():
        return False
    requested_repository = str(
        item.get("repositoryAlias") or item.get("repositoryId") or item.get("repository") or ""
    )
    for context in repository_context:
        aliases = {
            str(context.get("repositoryAlias", "")),
            str(context.get("repositoryId", "")),
        }
        if requested_repository and requested_repository not in aliases:
            continue
        normalized = raw_path
        alias_prefix = str(context.get("repositoryAlias", "")) + "/"
        if normalized.startswith(alias_prefix):
            normalized = normalized[len(alias_prefix) :]
        candidate = Path(str(context["_repositoryPath"])) / normalized
        try:
            candidate.resolve(strict=False).relative_to(
                Path(str(context["_repositoryPath"])).resolve(strict=True)
            )
        except (OSError, ValueError):
            continue
        if normalized in set(context.get("_allChangedFiles", [])) or candidate.is_file():
            return True
    return False


def _run_git(repository: Path, *arguments: str) -> str:
    git_directory = repository / ".git"
    if not git_directory.is_dir():
        raise ValueError("冻结仓库缺少Git元数据")
    try:
        completed = subprocess.run(
            [
                "git",
                f"--git-dir={git_directory}",
                f"--work-tree={repository}",
                *arguments,
            ],
            stdin=subprocess.DEVNULL,
            capture_output=True,
            check=False,
            timeout=120,
        )
    except (OSError, subprocess.TimeoutExpired) as exception:
        raise ValueError("冻结Git差异读取失败") from exception
    if completed.returncode != 0 or len(completed.stdout) > MAX_GIT_COMMAND_OUTPUT_BYTES:
        raise ValueError("冻结Git差异读取失败或超过上限")
    return completed.stdout.decode("utf-8", errors="replace")


def _tool_environment(
    request: dict[str, Any],
    relay_token: str,
    *,
    home: Path | None = None,
) -> dict[str, str]:
    """只传递工具运行所需白名单环境；这里的key仅能访问本容器回环relay。"""

    model_name = _model_name(request)
    relay_url = _relay_url(request)
    home_value = str(home) if home is not None else os.environ.get("HOME", "/tmp")
    path = "/usr/local/lib/codex/bin:/usr/local/lib/opencode/bin:/usr/local/bin:/usr/bin:/bin"
    opencode_config = {
        "$schema": "https://opencode.ai/config.json",
        "formatter": False,
        "lsp": False,
        "autoupdate": False,
        "share": "disabled",
        "shell": SAFE_OPENCODE_SHELL,
        # 只开放代码阅读所需工具，外部路径和联网工具保持不可见。
        "permission": {
            "*": "deny",
            "read": "allow",
            "glob": "allow",
            "grep": "allow",
            "list": "allow",
            "bash": "allow",
            "external_directory": "deny",
        },
        "enabled_providers": [OPENCODE_PROVIDER_ID],
        "model": f"{OPENCODE_PROVIDER_ID}/{model_name}",
        "provider": {
            OPENCODE_PROVIDER_ID: {
                "id": OPENCODE_PROVIDER_ID,
                "name": "TestAgent workflow model gateway",
                "env": ["OPENAI_API_KEY"],
                "npm": "@ai-sdk/openai-compatible",
                "models": {
                    model_name: {
                        "id": model_name,
                        "name": model_name,
                        "attachment": False,
                        "reasoning": False,
                        "temperature": False,
                        "tool_call": True,
                        "modalities": {"input": ["text"], "output": ["text"]},
                    }
                },
                "options": {
                    "apiKey": "{env:OPENAI_API_KEY}",
                    "baseURL": "{env:OPENAI_BASE_URL}",
                    # 企业兼容网关不依赖OpenCode追加stream_options.include_usage。
                    "includeUsage": False,
                },
            }
        },
    }
    return {
        "HOME": home_value,
        "XDG_CACHE_HOME": os.environ.get("XDG_CACHE_HOME", f"{home_value}/.cache"),
        "PATH": path,
        "LANG": "C.UTF-8",
        "LC_ALL": "C.UTF-8",
        "OPENAI_API_KEY": relay_token,
        "OPENAI_BASE_URL": relay_url,
        "OPENCODE_CONFIG_CONTENT": json.dumps(opencode_config, ensure_ascii=True),
        "OPENCODE_DISABLE_PROJECT_CONFIG": "1",
        "OPENCODE_DISABLE_AUTOUPDATE": "1",
        "CODEX_HOME": f"{home_value}/.codex",
        "RUST_LOG": "off",
    }


def _prepare_tool_home(
    analyzer_id: str,
    request: dict[str, Any],
    environment: dict[str, str],
) -> None:
    if analyzer_id != "codex":
        return
    codex_home = Path(environment["CODEX_HOME"])
    if codex_home.is_symlink():
        raise ValueError("Codex HOME不能是符号链接")
    codex_home.mkdir(mode=0o700, parents=True, exist_ok=True)
    if not codex_home.is_dir():
        raise ValueError("Codex HOME不是目录")
    codex_home.chmod(0o700)
    configuration = _codex_configuration(request, environment["HOME"])
    temporary = codex_home / "config.toml.tmp"
    try:
        temporary.unlink()
    except FileNotFoundError:
        pass
    descriptor = os.open(
        temporary,
        os.O_WRONLY
        | os.O_CREAT
        | os.O_EXCL
        | getattr(os, "O_NOFOLLOW", 0)
        | getattr(os, "O_CLOEXEC", 0),
        0o600,
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", closefd=False) as stream:
            stream.write(configuration)
            stream.flush()
            os.fchmod(descriptor, 0o600)
    finally:
        os.close(descriptor)
    temporary.replace(codex_home / "config.toml")


def _codex_configuration(request: dict[str, Any], home: str) -> str:
    """固定Codex provider；只从OPENAI_API_KEY读取本地relay token，不落盘。"""

    model_name = _model_name(request)
    gateway_url = _relay_url(request)
    command_path = "/usr/local/bin:/usr/bin:/bin"

    def quoted(value: object) -> str:
        return json.dumps(str(value), ensure_ascii=True)

    return "\n".join(
        [
            f"model = {quoted(model_name)}",
            f"model_provider = {quoted(OPENCODE_PROVIDER_ID)}",
            'web_search = "disabled"',
            'approval_policy = "never"',
            'default_permissions = "test-agent-whitebox-readonly"',
            "allow_login_shell = false",
            "project_root_markers = []",
            "",
            "[shell_environment_policy]",
            'inherit = "none"',
            "ignore_default_excludes = false",
            f"set = {{ PATH = {quoted(command_path)}, HOME = {quoted(home)} }}",
            'include_only = ["PATH", "HOME"]',
            "",
            f"[model_providers.{OPENCODE_PROVIDER_ID}]",
            'name = "TestAgent workflow model gateway"',
            f"base_url = {quoted(gateway_url)}",
            'env_key = "OPENAI_API_KEY"',
            'wire_api = "responses"',
            "supports_websockets = false",
            "request_max_retries = 1",
            "stream_max_retries = 1",
            "stream_idle_timeout_ms = 600000",
            "",
        ]
    )


def _tool_command(
    analyzer_id: str,
    request: dict[str, Any],
    prompt: str,
    schema_path: Path,
    raw_path: Path,
) -> list[str]:
    model_name = _model_name(request)
    if analyzer_id == "codex":
        return [
            "/usr/local/lib/codex/bin/codex-official",
            "exec",
            "--sandbox",
            "read-only",
            "--skip-git-repo-check",
            "--output-schema",
            str(schema_path),
            "--output-last-message",
            str(raw_path),
            prompt,
        ]
    if analyzer_id == "opencode":
        return [
            "/usr/local/lib/opencode/bin/opencode-official",
            "run",
            "--pure",
            "--model",
            f"{OPENCODE_PROVIDER_ID}/{model_name}",
            "--format",
            "json",
            prompt,
        ]
    raise ValueError("未注册的代码智能体")


def _model_name(request: dict[str, Any]) -> str:
    model_name = str(request.get("modelName", ""))
    if not MODEL_NAME_PATTERN.fullmatch(model_name):
        raise ValueError("模型ID格式不安全")
    return model_name


def _relay_url(request: dict[str, Any]) -> str:
    try:
        value = str(request["modelGatewayUrl"])
        parsed = urlsplit(value)
        port = parsed.port
    except (KeyError, TypeError, ValueError) as exception:
        raise ValueError("模型relay地址无效") from exception
    safe = (
        parsed.scheme == "http"
        and parsed.hostname == "127.0.0.1"
        and port in {18080, 18081, 18082}
        and parsed.path == "/v1"
        and parsed.username is None
        and parsed.password is None
        and not parsed.query
        and not parsed.fragment
    )
    if not safe:
        raise ValueError("模型relay地址不在容器回环白名单")
    return value


def _safe_output_path(path: Path) -> Path:
    resolved = path.resolve()
    root = Path("/workspace/output").resolve()
    try:
        resolved.relative_to(root)
    except ValueError as exception:
        raise ValueError("输入输出路径越界") from exception
    return resolved


def _normalize_private_output(analyzer_id: str) -> None:
    """任务正常或失败退出时恢复私有HOME权限，确保Runner后续能完整清理。"""

    subprocess.run(
        [
            "/usr/local/bin/test-agent-clean-output",
            f"/workspace/output/{analyzer_id}",
        ],
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        timeout=60,
        check=False,
    )


def entrypoint() -> int:
    analyzer_id = (
        sys.argv[1]
        if len(sys.argv) == 4 and sys.argv[1] in {"codex", "opencode"}
        else None
    )
    try:
        return main()
    finally:
        if analyzer_id is not None:
            _normalize_private_output(analyzer_id)


if __name__ == "__main__":
    raise SystemExit(entrypoint())
