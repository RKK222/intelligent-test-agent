"""Runner直接兑换一次性checkout ticket；workflow-service看不到SSH明文。"""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import hmac
import json
import secrets
import time

import httpx


@dataclass(frozen=True, slots=True)
class AuthorizedSubmoduleMaterial:
    repository_id: str
    remote_url: str
    encrypted_private_key: str | None


@dataclass(frozen=True, slots=True)
class CheckoutMaterial:
    repository_id: str
    remote_url: str
    default_branch: str
    target_branch: str
    encrypted_private_key: str | None
    authorized_submodules: tuple[AuthorizedSubmoduleMaterial, ...] = ()


class CheckoutTicketClient:
    def __init__(
        self,
        base_url: str,
        runner_id: str,
        secret: bytes,
        *,
        http_client: httpx.AsyncClient | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._runner_id = runner_id
        self._secret = secret
        self._http = http_client or httpx.AsyncClient(timeout=60)

    async def consume(self, ticket_id: str, task_id: str, run_id: str) -> CheckoutMaterial:
        path = f"/api/internal/workflow-capabilities/v1/checkout-tickets/{ticket_id}/consume"
        body = json.dumps(
            {"taskId": task_id, "runId": run_id, "runnerId": self._runner_id},
            separators=(",", ":"),
            sort_keys=True,
        ).encode()
        timestamp = str(int(time.time()))
        nonce = secrets.token_hex(24)
        digest = hashlib.sha256(body).hexdigest()
        canonical = "\n".join(
            ["POST", path, digest, self._runner_id, timestamp, nonce]
        )
        signature = hmac.new(self._secret, canonical.encode(), hashlib.sha256).hexdigest()
        response = await self._http.post(
            f"{self._base_url}{path}",
            content=body,
            headers={
                "Content-Type": "application/json",
                "X-Workflow-Runner-Id": self._runner_id,
                "X-Workflow-Timestamp": timestamp,
                "X-Workflow-Nonce": nonce,
                "X-Workflow-Body-SHA256": digest,
                "X-Workflow-Signature": signature,
            },
        )
        if response.is_error:
            raise RuntimeError("checkout ticket兑换失败")
        value = response.json()["data"]
        submodules = tuple(
            AuthorizedSubmoduleMaterial(
                repository_id=str(item["repositoryId"]),
                remote_url=str(item["remoteUrl"]),
                encrypted_private_key=item.get("encryptedPrivateKey"),
            )
            for item in value.get("authorizedSubmodules", [])
        )
        return CheckoutMaterial(
            repository_id=str(value["repositoryId"]),
            remote_url=str(value["remoteUrl"]),
            default_branch=str(value["defaultBranch"]),
            target_branch=str(value["targetBranch"]),
            encrypted_private_key=value.get("encryptedPrivateKey"),
            authorized_submodules=submodules,
        )

    async def aclose(self) -> None:
        await self._http.aclose()
