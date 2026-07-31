from __future__ import annotations

from datetime import UTC, datetime, timedelta
import json

import pytest

from testagent_workflow.auth import AuthenticationError, RedisTokenAuthenticator


class FakeRedis:
    def __init__(self, values: dict[str, str], ttls: dict[str, int]) -> None:
        self.values = values
        self.ttls = ttls
        self.calls: list[tuple[str, str]] = []

    async def get(self, key: str) -> str | None:
        self.calls.append(("get", key))
        return self.values.get(key)

    async def pttl(self, key: str) -> int:
        self.calls.append(("pttl", key))
        return self.ttls.get(key, -2)


def principal_json(token: str, *, expires_at: datetime | None = None) -> str:
    expires_at = expires_at or datetime.now(UTC) + timedelta(hours=1)
    return json.dumps(
        {
            "token": token,
            "userId": {"value": "usr_1234567890abcdef"},
            "username": "测试用户",
            "unifiedAuthId": "001177621",
            "roles": ["USER"],
            "issuedAt": (datetime.now(UTC) - timedelta(minutes=1)).isoformat(),
            "expiresAt": expires_at.isoformat(),
        }
    )


@pytest.mark.asyncio
async def test_authenticate_reads_only_the_exact_platform_token_key() -> None:
    redis = FakeRedis(
        {"test-agent:token:secret-token": principal_json("secret-token")},
        {"test-agent:token:secret-token": 3_600_000},
    )

    principal = await RedisTokenAuthenticator(redis).authenticate("Bearer secret-token")

    assert principal.user_id == "usr_1234567890abcdef"
    assert principal.unified_auth_id == "001177621"
    assert redis.calls == [
        ("get", "test-agent:token:secret-token"),
        ("pttl", "test-agent:token:secret-token"),
    ]


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("stored", "ttl", "message"),
    [
        (None, -2, "登录状态已失效"),
        (principal_json("another-token"), 3_600_000, "登录凭证不匹配"),
        (principal_json("secret-token"), 0, "登录状态已失效"),
        (
            principal_json("secret-token", expires_at=datetime.now(UTC) - timedelta(seconds=1)),
            3_600_000,
            "登录凭证已过期",
        ),
    ],
)
async def test_authenticate_rejects_invalid_platform_principals(
    stored: str | None,
    ttl: int,
    message: str,
) -> None:
    redis = FakeRedis(
        {"test-agent:token:secret-token": stored} if stored is not None else {},
        {"test-agent:token:secret-token": ttl},
    )

    with pytest.raises(AuthenticationError, match=message):
        await RedisTokenAuthenticator(redis).authenticate("Bearer secret-token")


@pytest.mark.asyncio
async def test_authenticate_never_exposes_the_token_in_parse_errors() -> None:
    redis = FakeRedis(
        {"test-agent:token:very-secret-token": "not-json"},
        {"test-agent:token:very-secret-token": 3_600_000},
    )

    with pytest.raises(AuthenticationError) as caught:
        await RedisTokenAuthenticator(redis).authenticate("Bearer very-secret-token")

    assert "very-secret-token" not in str(caught.value)


@pytest.mark.asyncio
async def test_authenticate_rejects_oversized_token_before_redis_lookup() -> None:
    redis = FakeRedis({}, {})

    with pytest.raises(AuthenticationError, match="格式无效"):
        await RedisTokenAuthenticator(redis).authenticate("Bearer " + "x" * 4097)

    assert redis.calls == []
