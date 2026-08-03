from __future__ import annotations

import importlib.util
import json
import os
from pathlib import Path
import subprocess

import pytest


MODULE_PATH = Path(__file__).parents[1] / "test-agent-analysis.py"
SAFE_SHELL_PATH = Path(__file__).parents[1] / "test-agent-safe-shell"
SPEC = importlib.util.spec_from_file_location("test_agent_analysis", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def result() -> dict:
    return {
        "summary": "影响订单提交",
        "impactedFeatures": [],
        "crossRepositoryImpacts": [],
        "risks": [],
        "codeEvidence": [],
        "recommendedRegressionTests": [],
        "uncertainties": [],
    }


def test_extracts_plain_markdown_and_nested_jsonl_results() -> None:
    expected = result()
    assert MODULE.extract_structured_result(json.dumps(expected)) == expected
    assert MODULE.extract_structured_result(f"```json\n{json.dumps(expected)}\n```") == expected
    event = json.dumps({"type": "text", "part": {"text": json.dumps(expected)}})
    assert MODULE.extract_structured_result(event) == expected


def gateway_request() -> dict:
    return {
        "modelGatewayUrl": "http://127.0.0.1:18080/v1",
        "modelName": "workflow-code-analysis",
    }


def test_prompt_requires_real_repository_tool_inspection() -> None:
    prompt = MODULE._prompt(
        {
            "repositories": [
                {
                    "repositoryAlias": "orders",
                    "mergeBase": "a" * 40,
                    "targetHead": "b" * 40,
                }
            ],
            "scopeSelectors": [],
            "outputSchema": {"type": "object"},
        }
    )

    assert "Codex必须调用exec_command" in prompt
    assert "git diff --stat mergeBase..targetHead" in prompt
    assert "没有实际工具结果时禁止输出分析结论" in prompt
    assert "严禁推测、举例或编造文件" in prompt
    assert "path必须逐字复制仓库相对路径" in prompt


def test_collects_frozen_git_diff_and_rejects_invented_evidence(tmp_path: Path) -> None:
    repository = tmp_path / "orders"
    source = repository / "src" / "OrderService.java"
    source.parent.mkdir(parents=True)
    subprocess.run(["git", "init", "-q", str(repository)], check=True)
    source.write_text("class OrderService {}\n", encoding="utf-8")
    subprocess.run(["git", "-C", str(repository), "add", "."], check=True)
    subprocess.run(
        [
            "git",
            "-C",
            str(repository),
            "-c",
            "user.name=Test Agent",
            "-c",
            "user.email=test-agent@example.invalid",
            "commit",
            "-qm",
            "baseline",
        ],
        check=True,
    )
    merge_base = subprocess.run(
        ["git", "-C", str(repository), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    source.write_text(
        "class OrderService { boolean submit() { return true; } }\n",
        encoding="utf-8",
    )
    subprocess.run(["git", "-C", str(repository), "add", "."], check=True)
    subprocess.run(
        [
            "git",
            "-C",
            str(repository),
            "-c",
            "user.name=Test Agent",
            "-c",
            "user.email=test-agent@example.invalid",
            "commit",
            "-qm",
            "target",
        ],
        check=True,
    )
    target_head = subprocess.run(
        ["git", "-C", str(repository), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    request = {
        "repositories": [
            {
                "repositoryId": "repo-orders",
                "repositoryAlias": "orders",
                "mergeBase": merge_base,
                "targetHead": target_head,
            }
        ],
        "scopeSelectors": [],
        "outputSchema": {
            "type": "object",
            "properties": {
                "codeEvidence": {
                    "type": "array",
                    "items": {"type": "object", "additionalProperties": True},
                }
            },
        },
    }

    context = MODULE.collect_repository_context(request, tmp_path)
    prompt = MODULE._prompt(request, context)

    assert context[0]["changedFiles"] == ["src/OrderService.java"]
    assert "boolean submit()" in context[0]["diffExcerpt"]
    assert "src/OrderService.java" in prompt
    assert str(repository) not in prompt
    assert "_repositoryPath" not in prompt
    schema = MODULE.constrain_output_schema(request["outputSchema"], context)
    evidence_schema = schema["properties"]["codeEvidence"]
    assert evidence_schema["minItems"] == 1
    assert evidence_schema["items"]["required"] == ["repositoryAlias", "path"]
    assert evidence_schema["items"]["properties"]["repositoryAlias"]["enum"] == [
        "orders"
    ]
    MODULE.validate_result_evidence(
        {
            "codeEvidence": [
                {
                    "repositoryAlias": "orders",
                    "path": "src/OrderService.java",
                }
            ]
        },
        context,
    )
    with pytest.raises(ValueError, match="不属于冻结仓库"):
        MODULE.validate_result_evidence(
            {
                "codeEvidence": [
                    {
                        "repositoryAlias": "orders",
                        "path": "src/InventedService.java",
                    }
                ]
            },
            context,
        )


def test_opencode_is_pinned_to_the_platform_gateway_provider() -> None:
    relay_token = "relay_local_token_12345678901234567890"

    environment = MODULE._tool_environment(gateway_request(), relay_token)
    configuration = json.loads(environment["OPENCODE_CONFIG_CONTENT"])
    command = MODULE._tool_command(
        "opencode",
        gateway_request(),
        "analyze",
        Path("/workspace/output/opencode/output-schema.json"),
        Path("/workspace/output/opencode/last-message.txt"),
    )

    assert configuration["enabled_providers"] == ["test-agent-workflow"]
    assert configuration["shell"] == "/usr/local/bin/test-agent-safe-shell"
    assert configuration["permission"] == {
        "*": "deny",
        "read": "allow",
        "glob": "allow",
        "grep": "allow",
        "list": "allow",
        "bash": "allow",
        "external_directory": "deny",
    }
    provider = configuration["provider"]["test-agent-workflow"]
    assert provider["options"] == {
        "apiKey": "{env:OPENAI_API_KEY}",
        "baseURL": "{env:OPENAI_BASE_URL}",
        "includeUsage": False,
    }
    assert "workflow-code-analysis" in provider["models"]
    assert command[command.index("--model") + 1] == (
        "test-agent-workflow/workflow-code-analysis"
    )
    assert "--pure" in command
    assert environment["OPENCODE_DISABLE_PROJECT_CONFIG"] == "1"
    assert relay_token not in environment["OPENCODE_CONFIG_CONTENT"]


def test_opencode_safe_shell_uses_an_environment_allowlist() -> None:
    environment = {
        **os.environ,
        "HOME": "/tmp/analysis-home",
        "XDG_CACHE_HOME": "/tmp/analysis-cache",
        "PATH": "/usr/local/bin:/usr/bin:/bin",
        "LANG": "C.UTF-8",
        "LC_ALL": "C.UTF-8",
        "OPENAI_API_KEY": "wfg_must_not_escape",
        "OPENAI_BASE_URL": "http://model-gateway.internal/v1",
        "OPENCODE_CONFIG_CONTENT": "sensitive-runtime-config",
        "UNEXPECTED_SECRET": "must-not-escape",
    }

    completed = subprocess.run(
        ["/bin/sh", str(SAFE_SHELL_PATH), "-c", "env"],
        check=True,
        capture_output=True,
        text=True,
        env=environment,
    )

    values = dict(
        line.split("=", 1)
        for line in completed.stdout.splitlines()
        if "=" in line
    )
    assert set(values) <= {
        "HOME",
        "XDG_CACHE_HOME",
        "PATH",
        "LANG",
        "LC_ALL",
        "PWD",
        "SHLVL",
        "_",
    }
    assert values["HOME"] == "/tmp/analysis-home"
    assert values["XDG_CACHE_HOME"] == "/tmp/analysis-cache"
    assert values["PATH"] == (
        "/usr/local/lib/codex/bin:/usr/local/lib/opencode/bin:"
        "/usr/local/bin:/usr/bin:/bin"
    )
    assert values["LANG"] == values["LC_ALL"] == "C.UTF-8"
    assert values["PWD"] == os.getcwd()
    assert "wfg_must_not_escape" not in completed.stdout
    assert "model-gateway.internal" not in completed.stdout
    assert "must-not-escape" not in completed.stdout


def test_codex_config_uses_local_relay_token_only_via_environment(tmp_path: Path) -> None:
    relay_token = "relay_local_token_12345678901234567890"
    home = tmp_path / "home"
    home.mkdir()
    environment = MODULE._tool_environment(gateway_request(), relay_token, home=home)

    MODULE._prepare_tool_home("codex", gateway_request(), environment)

    configuration = (home / ".codex" / "config.toml").read_text(encoding="utf-8")
    assert 'model = "workflow-code-analysis"' in configuration
    assert 'model_provider = "test-agent-workflow"' in configuration
    assert (
        'base_url = "http://127.0.0.1:18080/v1"'
        in configuration
    )
    assert 'env_key = "OPENAI_API_KEY"' in configuration
    assert 'wire_api = "responses"' in configuration
    assert 'web_search = "disabled"' in configuration
    assert relay_token not in configuration
    assert environment["OPENAI_API_KEY"] == relay_token


@pytest.mark.parametrize(
    "url",
    [
        "http://10.20.30.40:8080/api/internal/platform/model-gateway/v1",
        "http://127.0.0.1:18080/v1?target=other",
        "http://user@127.0.0.1:18080/v1",
        "http://127.0.0.1:19000/v1",
    ],
)
def test_analysis_tool_rejects_any_non_relay_model_url(url: str) -> None:
    request = {**gateway_request(), "modelGatewayUrl": url}

    with pytest.raises(ValueError, match="relay"):
        MODULE._tool_environment(request, "relay_local_token_12345678901234567890")
