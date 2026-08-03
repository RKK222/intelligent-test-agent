from __future__ import annotations

import hashlib
import hmac
import json

import httpx
import pytest

from testagent_workflow.platform import PlatformCapabilityClient, PlatformRequestIdentity


@pytest.mark.asyncio
async def test_platform_capability_default_client_ignores_environment_proxy() -> None:
    client = PlatformCapabilityClient(
        "http://platform.test",
        b"0123456789abcdef0123456789abcdef",
    )

    assert client._http._trust_env is False  # noqa: SLF001 - 锁定内部调用不继承宿主代理
    await client.aclose()


@pytest.mark.asyncio
async def test_platform_capability_request_uses_fixed_client_and_never_forwards_bearer() -> None:
    observed: dict[str, object] = {}

    async def handler(request: httpx.Request) -> httpx.Response:
        observed["headers"] = dict(request.headers)
        observed["body"] = request.content
        return httpx.Response(200, json={"data": [{"id": "repo_12345678"}]})

    client = PlatformCapabilityClient(
        "http://platform.test",
        b"0123456789abcdef0123456789abcdef",
        http_client=httpx.AsyncClient(transport=httpx.MockTransport(handler)),
        clock=lambda: 1_800_000_000,
        nonce_factory=lambda: "nonce_1234567890abcdef",
    )
    identity = PlatformRequestIdentity(
        user_id="usr_12345678",
        session_digest=hashlib.sha256(b"secret-token").hexdigest(),
    )

    repositories = await client.list_repositories(identity)

    headers = observed["headers"]
    assert isinstance(headers, dict)
    assert headers["x-workflow-client-id"] == "workflow"
    assert headers["x-workflow-user-id"] == "usr_12345678"
    assert "secret-token" not in json.dumps(headers)
    assert repositories[0]["id"] == "repo_12345678"

    body_digest = hashlib.sha256(observed["body"]).hexdigest()  # type: ignore[arg-type]
    canonical = "\n".join(
        [
            "GET",
            "/api/internal/workflow-capabilities/v1/repositories",
            body_digest,
            "usr_12345678",
            identity.session_digest,
            "1800000000",
            "nonce_1234567890abcdef",
            "workflow",
        ]
    )
    expected = hmac.new(
        b"0123456789abcdef0123456789abcdef",
        canonical.encode(),
        hashlib.sha256,
    ).hexdigest()
    assert hmac.compare_digest(headers["x-workflow-signature"], expected)


@pytest.mark.asyncio
async def test_checkout_ticket_and_model_grant_are_scoped_to_task_and_run() -> None:
    requests: list[dict[str, object]] = []

    async def handler(request: httpx.Request) -> httpx.Response:
        requests.append(json.loads(request.content))
        if request.url.path.endswith("checkout-tickets"):
            return httpx.Response(200, json={"data": {"ticketId": "ticket_once"}})
        return httpx.Response(200, json={"data": {"grant": "opaque", "expiresIn": 300}})

    client = PlatformCapabilityClient(
        "http://platform.test",
        b"0123456789abcdef0123456789abcdef",
        http_client=httpx.AsyncClient(transport=httpx.MockTransport(handler)),
    )
    identity = PlatformRequestIdentity("usr_12345678", "f" * 64)

    ticket = await client.issue_checkout_ticket(
        identity,
        repository_id="repo_12345678",
        task_id="task_12345678",
        run_id="run_12345678",
        runner_id="runner-a",
        runner_public_key="age1public",
        target_branch="feature/impact",
    )
    grant = await client.issue_model_grant(
        identity,
        task_id="task_12345678",
        run_id="run_12345678",
        analyzer_ids=["codex"],
    )

    assert ticket == "ticket_once"
    assert grant["grant"] == "opaque"
    assert all(request["taskId"] == "task_12345678" for request in requests)
    assert all(request["runId"] == "run_12345678" for request in requests)
    assert requests[0]["targetBranch"] == "feature/impact"


@pytest.mark.asyncio
async def test_run_scoped_revoke_does_not_require_worker_local_grant_id() -> None:
    observed: dict[str, object] = {}

    async def handler(request: httpx.Request) -> httpx.Response:
        observed["path"] = request.url.path
        observed["body"] = json.loads(request.content)
        return httpx.Response(200, json={"data": {"revoked": True}})

    client = PlatformCapabilityClient(
        "http://platform.test",
        b"0123456789abcdef0123456789abcdef",
        http_client=httpx.AsyncClient(transport=httpx.MockTransport(handler)),
    )

    await client.revoke_model_grants_for_run(
        PlatformRequestIdentity("usr_12345678", "f" * 64),
        task_id="task_12345678",
        run_id="run_12345678",
    )

    assert observed == {
        "path": "/api/internal/workflow-capabilities/v1/model-grants/revoke-run",
        "body": {"runId": "run_12345678", "taskId": "task_12345678"},
    }
