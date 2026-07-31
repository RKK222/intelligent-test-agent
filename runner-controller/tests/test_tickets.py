from __future__ import annotations

import httpx
import pytest

from testagent_runner.tickets import CheckoutTicketClient


@pytest.mark.asyncio
async def test_checkout_material_carries_the_java_bound_target_branch() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert "Authorization" not in request.headers
        return httpx.Response(
            200,
            json={
                "data": {
                    "repositoryId": "repo_12345678",
                    "remoteUrl": "https://git.example.test/orders.git",
                    "defaultBranch": "main",
                    "targetBranch": "feature/impact",
                    "encryptedPrivateKey": None,
                    "authorizedSubmodules": [],
                }
            },
        )

    client = CheckoutTicketClient(
        "http://platform.test",
        "runner-a",
        b"0123456789abcdef0123456789abcdef",
        http_client=httpx.AsyncClient(transport=httpx.MockTransport(handler)),
    )

    material = await client.consume(
        "wfcheckout_12345678",
        "task_12345678",
        "run_12345678",
    )

    assert material.target_branch == "feature/impact"
    await client.aclose()
