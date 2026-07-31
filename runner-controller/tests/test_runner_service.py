from __future__ import annotations

import asyncio
from datetime import UTC, datetime, timedelta
import json
from contextlib import nullcontext
from pathlib import Path
import stat

import pytest

from testagent_runner.service import RunnerService, RunnerServiceError
from testagent_runner.settings import RunnerSettings
from testagent_runner.tickets import CheckoutMaterial


class NeverUsedTickets:
    async def consume(self, ticket_id: str, task_id: str, run_id: str):  # type: ignore[no-untyped-def]
        raise AssertionError("本测试不应兑换checkout ticket")


class NeverUsedCredentials:
    pass


class ReadyDocker:
    def __init__(self) -> None:
        self.server_checks = 0
        self.network_checks: list[str] = []
        self.stop_calls: list[str] = []
        self.quiesce_calls: list[str] = []
        self.sanitize_calls: list[str] = []
        self.verified_specs = []

    def verify_server(self) -> None:
        self.server_checks += 1

    def verify_network(self, network: str) -> str:
        self.network_checks.append(network)
        return "172.31.250.0/24"

    def stop_retained(self, task_id: str) -> None:
        self.stop_calls.append(task_id)

    def quiesce_for_cleanup(self, task_id: str) -> None:
        self.quiesce_calls.append(task_id)

    def verify_running(self, spec):  # type: ignore[no-untyped-def]
        self.verified_specs.append(spec)

    def sanitize_workspace(self, spec):  # type: ignore[no-untyped-def]
        self.sanitize_calls.append(spec.task_id)


class RecordingAnalyzers:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str, dict[str, object]]] = []

    async def execute(
        self,
        task_id: str,
        analyzer_id: str,
        output_root: Path,
        payload: dict[str, object],
    ) -> dict[str, object]:
        self.calls.append((task_id, analyzer_id, payload))
        return {"summary": "ok"}


def settings(tmp_path: Path) -> RunnerSettings:
    known_hosts = tmp_path / "known_hosts"
    if known_hosts.exists():
        known_hosts.chmod(0o644)
    known_hosts.write_text("git.example.test ssh-ed25519 AAAATEST\n", encoding="utf-8")
    known_hosts.chmod(0o444)
    return RunnerSettings(
        runner_id="runner-analysis-1",
        worker_hmac_secret="w" * 32,
        platform_hmac_secret="p" * 32,
        root=tmp_path / "runner",
        credential_root=tmp_path / "credentials",
        private_key_path=tmp_path / "runner.pem",
        known_hosts_path=known_hosts,
        analysis_image="test-agent-analysis@sha256:" + "a" * 64,
        analysis_network="test-agent-analysis-egress",
        analysis_network_subnet="172.31.250.0/24",
        model_gateway_url=(
            "http://10.20.30.40:8080/api/internal/platform/model-gateway/v1"
        ),
        model_gateway_cidr="10.20.30.40/32",
        model_gateway_port=8080,
        minimum_free_bytes=1024 * 1024 * 1024,
    )


def service(tmp_path: Path) -> tuple[RunnerService, ReadyDocker, RecordingAnalyzers]:
    configured = settings(tmp_path)
    docker = ReadyDocker()
    analyzers = RecordingAnalyzers()
    value = RunnerService(
        configured,
        NeverUsedTickets(),  # type: ignore[arg-type]
        NeverUsedCredentials(),  # type: ignore[arg-type]
        docker,  # type: ignore[arg-type]
        analyzers,  # type: ignore[arg-type]
    )
    task_root = configured.root / "task_12345678"
    (task_root / "workspace" / "output" / "codex").mkdir(parents=True)
    (task_root / "state.json").write_text(
        json.dumps(
            {
                "taskId": "task_12345678",
                "runId": "run_12345678",
                "status": "ACTIVE",
                "analyzerIds": ["codex"],
                "repositories": [],
                "imageDigest": configured.analysis_image,
            }
        ),
        encoding="utf-8",
    )
    return value, docker, analyzers


@pytest.mark.asyncio
async def test_runner_readiness_rechecks_docker_network_and_disk(tmp_path: Path) -> None:
    runner, docker, _ = service(tmp_path)

    result = await runner.readiness()

    assert result == {"status": "UP", "network": "test-agent-analysis-egress"}
    assert docker.server_checks == 1
    assert docker.network_checks == ["test-agent-analysis-egress"]


@pytest.mark.asyncio
@pytest.mark.parametrize("analyzer_ids", [["../../escape"], ["codex", "codex"], []])
async def test_prepare_rejects_unregistered_duplicate_or_empty_analyzers_before_writing(
    tmp_path: Path,
    analyzer_ids: list[str],
) -> None:
    runner, _, _ = service(tmp_path)
    configured = settings(tmp_path)

    with pytest.raises(RunnerServiceError) as captured:
        await runner.prepare(
            "task_87654321",
            {
                "runId": "run_87654321",
                "operationKey": "run_87654321:freeze",
                "repositories": [],
                "analyzerIds": analyzer_ids,
            },
        )

    assert captured.value.code == "ANALYZER_SELECTION_INVALID"
    assert not (configured.root / "task_87654321").exists()
    assert not (configured.root / "escape").exists()


@pytest.mark.asyncio
async def test_analyze_rejects_unselected_analyzer_and_gateway_override(tmp_path: Path) -> None:
    runner, _, analyzers = service(tmp_path)
    base = {
        "runId": "run_12345678",
        "operationKey": "run_12345678:analyze:codex",
        "modelGrant": "wfg_" + "x" * 32,
        "modelGatewayUrl": (
            "http://10.20.30.40:8080/api/internal/platform/model-gateway/v1"
        ),
        "modelName": "workflow-code-analysis",
        "outputSchema": {},
    }

    with pytest.raises(RunnerServiceError, match="未在任务中注册"):
        await runner.analyze("task_12345678", "opencode", base)
    with pytest.raises(RunnerServiceError, match="模型网关地址"):
        await runner.analyze(
            "task_12345678",
            "codex",
            {
                **base,
                "modelGatewayUrl": (
                    "http://10.20.30.41:8080/api/internal/platform/model-gateway/v1"
                ),
            },
        )
    assert analyzers.calls == []


@pytest.mark.asyncio
async def test_prepare_rejects_a_target_branch_not_bound_to_the_checkout_ticket(
    tmp_path: Path,
) -> None:
    class MismatchedTickets:
        async def consume(self, ticket_id, task_id, run_id):  # type: ignore[no-untyped-def]
            del ticket_id, task_id, run_id
            return CheckoutMaterial(
                repository_id="repo_12345678",
                remote_url="https://git.example.test/orders.git",
                default_branch="main",
                target_branch="feature/authorized",
                encrypted_private_key=None,
            )

    configured = settings(tmp_path)
    runner = RunnerService(
        configured,
        MismatchedTickets(),  # type: ignore[arg-type]
        NeverUsedCredentials(),  # type: ignore[arg-type]
        ReadyDocker(),  # type: ignore[arg-type]
        RecordingAnalyzers(),  # type: ignore[arg-type]
    )

    with pytest.raises(RunnerServiceError, match="目标分支不匹配"):
        await runner.prepare(
            "task_87654321",
            {
                "runId": "run_87654321",
                "operationKey": "run_87654321:freeze:coordinates",
                "repositories": [
                    {
                        "repositoryId": "repo_12345678",
                        "repositoryAlias": "orders",
                        "targetBranch": "feature/attacker-choice",
                        "checkoutTicketId": "ticket_12345678",
                    }
                ],
                "analyzerIds": ["codex"],
            },
        )

    assert not (configured.root / "task_87654321").exists()


@pytest.mark.asyncio
async def test_prepare_removes_started_container_when_state_persistence_fails(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from testagent_runner.git_workspace import FrozenCheckout
    import testagent_runner.service as service_module

    class Tickets:
        async def consume(self, ticket_id, task_id, run_id):  # type: ignore[no-untyped-def]
            del ticket_id, task_id, run_id
            return CheckoutMaterial(
                repository_id="repo_12345678",
                remote_url="https://git.example.test/orders.git",
                default_branch="main",
                target_branch="feature/impact",
                encrypted_private_key=None,
            )

    class Credentials:
        def materialize(self, encrypted):  # type: ignore[no-untyped-def]
            del encrypted
            return nullcontext(None)

    class Workspace:
        def __init__(self, root, **kwargs):  # type: ignore[no-untyped-def]
            del kwargs
            self.repositories_root = root / "repos"
            self.repositories_root.mkdir(parents=True)

        def checkout(self, spec):  # type: ignore[no-untyped-def]
            repository_path = self.repositories_root / spec.alias
            repository_path.mkdir()
            return FrozenCheckout(
                spec.repository_id,
                spec.alias,
                repository_path,
                spec.default_branch,
                "a" * 40,
                spec.target_branch,
                "b" * 40,
                "a" * 40,
                (),
            )

    class Docker(ReadyDocker):
        def __init__(self) -> None:
            super().__init__()
            self.removed: list[str] = []

        def create_and_start(self, spec):  # type: ignore[no-untyped-def]
            del spec
            return "container_12345678"

        def remove(self, task_id: str) -> None:
            self.removed.append(task_id)

    configured = settings(tmp_path)
    docker = Docker()
    runner = RunnerService(
        configured,
        Tickets(),  # type: ignore[arg-type]
        Credentials(),  # type: ignore[arg-type]
        docker,  # type: ignore[arg-type]
        RecordingAnalyzers(),  # type: ignore[arg-type]
    )
    monkeypatch.setattr(service_module, "GitWorkspace", Workspace)

    def fail_state_write(path, value):  # type: ignore[no-untyped-def]
        del path, value
        raise OSError("simulated state write failure")

    monkeypatch.setattr(RunnerService, "_write_state", staticmethod(fail_state_write))

    with pytest.raises(OSError, match="state write failure"):
        await runner.prepare(
            "task_87654321",
            {
                "runId": "run_87654321",
                "operationKey": "run_87654321:freeze:coordinates",
                "repositories": [
                    {
                        "repositoryId": "repo_12345678",
                        "repositoryAlias": "orders",
                        "targetBranch": "feature/impact",
                        "checkoutTicketId": "ticket_12345678",
                    }
                ],
                "analyzerIds": ["codex"],
            },
        )

    assert docker.removed == ["task_87654321"]
    assert not (configured.root / "task_87654321").exists()


@pytest.mark.asyncio
async def test_analyzer_operation_key_returns_cached_result_without_second_model_call(
    tmp_path: Path,
) -> None:
    runner, _, analyzers = service(tmp_path)
    payload = {
        "runId": "run_12345678",
        "operationKey": "run_12345678:analyze:codex",
        "modelGrant": "wfg_" + "x" * 32,
        "modelGatewayUrl": (
            "http://10.20.30.40:8080/api/internal/platform/model-gateway/v1"
        ),
        "modelName": "workflow-code-analysis",
        "outputSchema": {},
    }

    first = await runner.analyze("task_12345678", "codex", payload)
    second = await runner.analyze("task_12345678", "codex", payload)

    assert first == second == {"result": {"summary": "ok"}}
    assert len(analyzers.calls) == 1
    assert len(runner._docker.verified_specs) == 1  # noqa: SLF001 - 锁定容器使用前复核
    cached = list(
        (settings(tmp_path).root / "task_12345678/control/operations/codex").glob(
            "*.json"
        )
    )
    assert len(cached) == 1
    assert "modelGrant" not in cached[0].read_text(encoding="utf-8")


@pytest.mark.asyncio
async def test_multi_analyzer_calls_are_serialized_per_task_before_container_reset(
    tmp_path: Path,
) -> None:
    class BlockingAnalyzers(RecordingAnalyzers):
        def __init__(self) -> None:
            super().__init__()
            self.active = 0
            self.maximum_active = 0

        async def execute(
            self,
            task_id: str,
            analyzer_id: str,
            output_root: Path,
            payload: dict[str, object],
        ) -> dict[str, object]:
            self.calls.append((task_id, analyzer_id, payload))
            self.active += 1
            self.maximum_active = max(self.maximum_active, self.active)
            await asyncio.sleep(0.02)
            self.active -= 1
            return {"summary": analyzer_id}

    configured = settings(tmp_path)
    analyzers = BlockingAnalyzers()
    runner = RunnerService(
        configured,
        NeverUsedTickets(),  # type: ignore[arg-type]
        NeverUsedCredentials(),  # type: ignore[arg-type]
        ReadyDocker(),  # type: ignore[arg-type]
        analyzers,
    )
    task_root = configured.root / "task_12345678"
    (task_root / "workspace" / "output").mkdir(parents=True)
    (task_root / "state.json").write_text(
        json.dumps(
            {
                "taskId": "task_12345678",
                "runId": "run_12345678",
                "status": "ACTIVE",
                "analyzerIds": ["codex", "opencode"],
                "repositories": [],
                "imageDigest": configured.analysis_image,
            }
        ),
        encoding="utf-8",
    )
    base = {
        "runId": "run_12345678",
        "modelGrant": "wfg_" + "x" * 32,
        "modelGatewayUrl": configured.model_gateway_url,
        "modelName": "workflow-code-analysis",
        "outputSchema": {},
    }

    await asyncio.gather(
        runner.analyze(
            "task_12345678",
            "codex",
            {**base, "operationKey": "run_12345678:analyze:codex"},
        ),
        runner.analyze(
            "task_12345678",
            "opencode",
            {**base, "operationKey": "run_12345678:analyze:opencode"},
        ),
    )

    assert analyzers.maximum_active == 1


@pytest.mark.asyncio
async def test_retain_is_idempotent_by_run_bound_operation_key(tmp_path: Path) -> None:
    runner, docker, _ = service(tmp_path)
    operation_key = "run_12345678:workspace:stop-retain-48h"

    first = await runner.retain("task_12345678", "run_12345678", 48, operation_key)
    second = await runner.retain("task_12345678", "run_12345678", 48, operation_key)

    assert first == second
    assert docker.stop_calls == ["task_12345678"]


@pytest.mark.asyncio
async def test_retain_rolls_expiry_for_a_stopped_workspace_without_stopping_twice(
    tmp_path: Path,
) -> None:
    runner, docker, _ = service(tmp_path)
    first = await runner.retain(
        "task_12345678",
        "run_12345678",
        48,
        "run_12345678:workspace:initial-retain",
    )

    second = await runner.retain(
        "task_12345678",
        "run_12345678",
        48,
        "run_12345678:workspace:followup-retain",
    )

    assert second["workspace"]["expiresAt"] > first["workspace"]["expiresAt"]
    assert docker.stop_calls == ["task_12345678"]


@pytest.mark.asyncio
async def test_resume_rejects_non_retained_state_instead_of_rebinding_an_active_workspace(
    tmp_path: Path,
) -> None:
    runner, _, _ = service(tmp_path)

    with pytest.raises(RunnerServiceError, match="未处于可恢复状态"):
        await runner.resume("task_12345678", "run_87654321")

    state = json.loads(
        (settings(tmp_path).root / "task_12345678/state.json").read_text(encoding="utf-8")
    )
    assert state["runId"] == "run_12345678"
    assert state["status"] == "ACTIVE"


@pytest.mark.asyncio
async def test_expiry_cleanup_ignores_active_workspace_even_if_stale_expiry_is_present(
    tmp_path: Path,
) -> None:
    class CleanupDocker(ReadyDocker):
        def __init__(self) -> None:
            super().__init__()
            self.removed: list[str] = []

        def remove(self, task_id: str) -> None:
            self.removed.append(task_id)

    configured = settings(tmp_path)
    docker = CleanupDocker()
    runner = RunnerService(
        configured,
        NeverUsedTickets(),  # type: ignore[arg-type]
        NeverUsedCredentials(),  # type: ignore[arg-type]
        docker,  # type: ignore[arg-type]
        RecordingAnalyzers(),  # type: ignore[arg-type]
    )
    task_root = configured.root / "task_12345678"
    task_root.mkdir(parents=True)
    (task_root / "state.json").write_text(
        json.dumps(
            {
                "taskId": "task_12345678",
                "runId": "run_12345678",
                "status": "ACTIVE",
                "expiresAt": (datetime.now(UTC) - timedelta(minutes=1)).isoformat(),
            }
        ),
        encoding="utf-8",
    )

    assert await runner.cleanup_expired() == []
    assert docker.removed == []
    assert task_root.exists()


@pytest.mark.asyncio
async def test_cleanup_preserves_state_when_container_removal_fails_then_retries(
    tmp_path: Path,
) -> None:
    class RetryDocker(ReadyDocker):
        def __init__(self) -> None:
            super().__init__()
            self.attempts = 0

        def remove(self, task_id: str) -> None:
            assert task_id == "task_12345678"
            self.attempts += 1
            if self.attempts == 1:
                raise RuntimeError("container still running")

    configured = settings(tmp_path)
    docker = RetryDocker()
    runner = RunnerService(
        configured,
        NeverUsedTickets(),  # type: ignore[arg-type]
        NeverUsedCredentials(),  # type: ignore[arg-type]
        docker,  # type: ignore[arg-type]
        RecordingAnalyzers(),  # type: ignore[arg-type]
    )
    task_root = configured.root / "task_12345678"
    (task_root / "workspace" / "repos").mkdir(parents=True)
    (task_root / "state.json").write_text(
        json.dumps(
            {
                "taskId": "task_12345678",
                "runId": "run_12345678",
                "status": "CLEANUP_FAILED",
                "analyzerIds": ["codex"],
                "imageDigest": configured.analysis_image,
                "repositories": [],
            }
        ),
        encoding="utf-8",
    )

    with pytest.raises(RuntimeError, match="container still running"):
        await runner.cleanup("task_12345678", "run_12345678")

    assert (task_root / "state.json").is_file()
    assert (task_root / "workspace" / "repos").is_dir()

    await runner.cleanup("task_12345678", "run_12345678")

    assert docker.attempts == 2
    assert not task_root.exists()


@pytest.mark.asyncio
async def test_cleanup_normalizes_analyzer_owned_private_directories_before_removal(
    tmp_path: Path,
) -> None:
    class SanitizingDocker(ReadyDocker):
        def __init__(self, root: Path) -> None:
            super().__init__()
            self.root = root
            self.removed: list[str] = []

        def sanitize_workspace(self, spec):  # type: ignore[no-untyped-def]
            super().sanitize_workspace(spec)
            private = self.root / spec.task_id / "workspace/output/codex/home/.codex"
            private.chmod(0o770)

        def remove(self, task_id: str) -> None:
            self.removed.append(task_id)

    configured = settings(tmp_path)
    docker = SanitizingDocker(configured.root)
    runner = RunnerService(
        configured,
        NeverUsedTickets(),  # type: ignore[arg-type]
        NeverUsedCredentials(),  # type: ignore[arg-type]
        docker,  # type: ignore[arg-type]
        RecordingAnalyzers(),  # type: ignore[arg-type]
    )
    task_root = configured.root / "task_12345678"
    private = task_root / "workspace/output/codex/home/.codex"
    private.mkdir(parents=True)
    private.chmod(0o000)
    (task_root / "state.json").write_text(
        json.dumps(
            {
                "taskId": "task_12345678",
                "runId": "run_12345678",
                "status": "STOPPED_RETAINED",
                "analyzerIds": ["codex"],
                "imageDigest": configured.analysis_image,
                "repositories": [],
            }
        ),
        encoding="utf-8",
    )

    await runner.cleanup("task_12345678", "run_12345678")

    assert docker.sanitize_calls == ["task_12345678"]
    assert docker.removed == ["task_12345678"]
    assert not task_root.exists()


@pytest.mark.asyncio
async def test_cleanup_stops_untrusted_process_before_opening_private_output_directories(
    tmp_path: Path,
) -> None:
    class OrderedCleanupDocker(ReadyDocker):
        def __init__(self, output_root: Path) -> None:
            super().__init__()
            self.output_root = output_root
            self.events: list[str] = []

        def quiesce_for_cleanup(self, task_id: str) -> None:
            super().quiesce_for_cleanup(task_id)
            assert stat.S_IMODE(self.output_root.stat().st_mode) == 0o710
            assert stat.S_IMODE((self.output_root / "codex").stat().st_mode) == 0
            self.events.append("quiesced")

        def sanitize_workspace(self, spec):  # type: ignore[no-untyped-def]
            super().sanitize_workspace(spec)
            assert stat.S_IMODE(self.output_root.stat().st_mode) == 0o750
            assert stat.S_IMODE((self.output_root / "codex").stat().st_mode) == 0o770
            self.events.append("sanitized")

        def remove(self, task_id: str) -> None:
            self.events.append("removed")

    configured = settings(tmp_path)
    task_root = configured.root / "task_12345678"
    output_root = task_root / "workspace/output"
    analyzer_root = output_root / "codex"
    analyzer_root.mkdir(parents=True)
    output_root.chmod(0o710)
    analyzer_root.chmod(0o000)
    (task_root / "state.json").write_text(
        json.dumps(
            {
                "taskId": "task_12345678",
                "runId": "run_12345678",
                "status": "ACTIVE",
                "analyzerIds": ["codex"],
                "imageDigest": configured.analysis_image,
                "repositories": [],
            }
        ),
        encoding="utf-8",
    )
    docker = OrderedCleanupDocker(output_root)
    runner = RunnerService(
        configured,
        NeverUsedTickets(),  # type: ignore[arg-type]
        NeverUsedCredentials(),  # type: ignore[arg-type]
        docker,  # type: ignore[arg-type]
        RecordingAnalyzers(),  # type: ignore[arg-type]
    )

    await runner.cleanup("task_12345678", "run_12345678")

    assert docker.events == ["quiesced", "quiesced", "sanitized", "removed"]


@pytest.mark.asyncio
async def test_cleanup_fences_waiting_analysis_before_deleting_workspace(tmp_path: Path) -> None:
    class BlockingAnalyzers(RecordingAnalyzers):
        def __init__(self) -> None:
            super().__init__()
            self.started = asyncio.Event()
            self.finish = asyncio.Event()

        async def execute(
            self,
            task_id: str,
            analyzer_id: str,
            output_root: Path,
            payload: dict[str, object],
        ) -> dict[str, object]:
            self.calls.append((task_id, analyzer_id, payload))
            self.started.set()
            await self.finish.wait()
            return {"summary": "ok"}

    class CancelDocker(ReadyDocker):
        def __init__(self, analyzers: BlockingAnalyzers) -> None:
            super().__init__()
            self.analyzers = analyzers
            self.removed: list[str] = []

        def quiesce_for_cleanup(self, task_id: str) -> None:
            super().quiesce_for_cleanup(task_id)
            self.analyzers.finish.set()

        def remove(self, task_id: str) -> None:
            self.removed.append(task_id)

    configured = settings(tmp_path)
    analyzers = BlockingAnalyzers()
    docker = CancelDocker(analyzers)
    runner = RunnerService(
        configured,
        NeverUsedTickets(),  # type: ignore[arg-type]
        NeverUsedCredentials(),  # type: ignore[arg-type]
        docker,  # type: ignore[arg-type]
        analyzers,
    )
    task_root = configured.root / "task_12345678"
    (task_root / "workspace/repos").mkdir(parents=True)
    (task_root / "workspace/output/codex").mkdir(parents=True)
    (task_root / "state.json").write_text(
        json.dumps(
            {
                "taskId": "task_12345678",
                "runId": "run_12345678",
                "status": "ACTIVE",
                "analyzerIds": ["codex"],
                "imageDigest": configured.analysis_image,
                "repositories": [],
            }
        ),
        encoding="utf-8",
    )
    base = {
        "runId": "run_12345678",
        "modelGrant": "wfg_" + "x" * 32,
        "modelGatewayUrl": configured.model_gateway_url,
        "modelName": "workflow-code-analysis",
        "outputSchema": {},
    }
    active = asyncio.create_task(
        runner.analyze(
            "task_12345678",
            "codex",
            {**base, "operationKey": "run_12345678:analyze:first"},
        )
    )
    await analyzers.started.wait()
    waiting = asyncio.create_task(
        runner.analyze(
            "task_12345678",
            "codex",
            {**base, "operationKey": "run_12345678:analyze:waiting"},
        )
    )

    await runner.cleanup("task_12345678", "run_12345678")

    assert await active == {"result": {"summary": "ok"}}
    with pytest.raises(RunnerServiceError) as captured:
        await waiting
    assert captured.value.code == "WORKSPACE_NOT_ACTIVE"
    assert len(analyzers.calls) == 1
    assert docker.removed == ["task_12345678"]
    assert not task_root.exists()
