"""Python调用Java白名单共享能力接口的固定HMAC客户端。"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
import hashlib
import hmac
import json
import secrets
import time
from typing import Any

import httpx


CAPABILITY_PREFIX = "/api/internal/workflow-capabilities/v1"
WORKFLOW_CLIENT_ID = "workflow"


@dataclass(frozen=True, slots=True)
class PlatformRequestIdentity:
    user_id: str
    session_digest: str


class PlatformCapabilityError(RuntimeError):
    """共享能力接口拒绝或不可用；异常不携带原始Bearer。"""

    def __init__(self, code: str, message: str, status_code: int) -> None:
        super().__init__(message)
        self.code = code
        self.status_code = status_code


class PlatformCapabilityClient:
    """固定client ID和签名格式，调用方无法覆盖身份边界。"""

    def __init__(
        self,
        base_url: str,
        hmac_secret: bytes,
        *,
        http_client: httpx.AsyncClient | None = None,
        clock: Callable[[], float] = time.time,
        nonce_factory: Callable[[], str] = lambda: secrets.token_hex(24),
    ) -> None:
        if len(hmac_secret) < 32:
            raise ValueError("workflow HMAC密钥至少需要32字节")
        self._base_url = base_url.rstrip("/")
        self._secret = hmac_secret
        self._http = http_client or httpx.AsyncClient(timeout=30.0)
        self._clock = clock
        self._nonce_factory = nonce_factory

    async def list_repositories(self, identity: PlatformRequestIdentity) -> list[dict[str, Any]]:
        data = await self._request("GET", f"{CAPABILITY_PREFIX}/repositories", identity)
        return list(data)

    async def list_branches(
        self,
        identity: PlatformRequestIdentity,
        repository_id: str,
    ) -> list[dict[str, Any]]:
        data = await self._request(
            "GET",
            f"{CAPABILITY_PREFIX}/repositories/{repository_id}/branches",
            identity,
        )
        return list(data)

    async def authorize_repositories(
        self,
        identity: PlatformRequestIdentity,
        repository_ids: list[str],
    ) -> list[dict[str, Any]]:
        data = await self._request(
            "POST",
            f"{CAPABILITY_PREFIX}/repositories/authorize",
            identity,
            {"repositoryIds": repository_ids},
        )
        return list(data)

    async def issue_checkout_ticket(
        self,
        identity: PlatformRequestIdentity,
        *,
        repository_id: str,
        task_id: str,
        run_id: str,
        runner_id: str,
        runner_public_key: str,
        target_branch: str,
        baseline_branch: str | None = None,
    ) -> str:
        data = await self._request(
            "POST",
            f"{CAPABILITY_PREFIX}/checkout-tickets",
            identity,
            {
                "repositoryId": repository_id,
                "taskId": task_id,
                "runId": run_id,
                "runnerId": runner_id,
                "runnerPublicKey": runner_public_key,
                "targetBranch": target_branch,
                "baselineBranch": baseline_branch,
            },
        )
        return str(data["ticketId"])

    async def issue_model_grant(
        self,
        identity: PlatformRequestIdentity,
        *,
        task_id: str,
        run_id: str,
        analyzer_ids: list[str],
    ) -> dict[str, Any]:
        return dict(await self._request(
            "POST",
            f"{CAPABILITY_PREFIX}/model-grants",
            identity,
            {
                "taskId": task_id,
                "runId": run_id,
                "analyzerIds": analyzer_ids,
            },
        ))

    async def refresh_model_grant(
        self,
        identity: PlatformRequestIdentity,
        *,
        task_id: str,
        run_id: str,
        grant_id: str,
    ) -> dict[str, Any]:
        return dict(await self._request(
            "POST",
            f"{CAPABILITY_PREFIX}/model-grants/{grant_id}/refresh",
            identity,
            {"taskId": task_id, "runId": run_id},
        ))

    async def revoke_model_grant(
        self,
        identity: PlatformRequestIdentity,
        *,
        task_id: str,
        run_id: str,
        grant_id: str,
    ) -> None:
        await self._request(
            "POST",
            f"{CAPABILITY_PREFIX}/model-grants/{grant_id}/revoke",
            identity,
            {"taskId": task_id, "runId": run_id},
        )

    async def revoke_model_grants_for_run(
        self,
        identity: PlatformRequestIdentity,
        *,
        task_id: str,
        run_id: str,
    ) -> None:
        await self._request(
            "POST",
            f"{CAPABILITY_PREFIX}/model-grants/revoke-run",
            identity,
            {"taskId": task_id, "runId": run_id},
        )

    async def verify_super_admin(self, identity: PlatformRequestIdentity) -> bool:
        data = await self._request(
            "POST",
            f"{CAPABILITY_PREFIX}/permissions/super-admin/verify",
            identity,
            {},
        )
        return bool(data.get("allowed", False))

    async def aclose(self) -> None:
        await self._http.aclose()

    async def _request(
        self,
        method: str,
        path: str,
        identity: PlatformRequestIdentity,
        payload: dict[str, Any] | None = None,
    ) -> Any:
        body = (
            json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode()
            if payload is not None
            else b""
        )
        timestamp = str(int(self._clock()))
        nonce = self._nonce_factory()
        body_digest = hashlib.sha256(body).hexdigest()
        canonical = "\n".join(
            [
                method,
                path,
                body_digest,
                identity.user_id,
                identity.session_digest,
                timestamp,
                nonce,
                WORKFLOW_CLIENT_ID,
            ]
        )
        signature = hmac.new(self._secret, canonical.encode(), hashlib.sha256).hexdigest()
        response = await self._http.request(
            method,
            f"{self._base_url}{path}",
            content=body,
            headers={
                "Content-Type": "application/json",
                "X-Workflow-Client-Id": WORKFLOW_CLIENT_ID,
                "X-Workflow-User-Id": identity.user_id,
                "X-Workflow-Session-Digest": identity.session_digest,
                "X-Workflow-Timestamp": timestamp,
                "X-Workflow-Nonce": nonce,
                "X-Workflow-Body-SHA256": body_digest,
                "X-Workflow-Signature": signature,
            },
        )
        if response.is_error:
            try:
                error = response.json()
            except ValueError:
                error = {}
            raise PlatformCapabilityError(
                str(error.get("code", "PLATFORM_CAPABILITY_FAILED")),
                str(error.get("message", "平台共享能力调用失败")),
                response.status_code,
            )
        envelope = response.json()
        return envelope.get("data")
