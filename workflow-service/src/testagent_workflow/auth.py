"""直接复用平台 Redis Token 的认证边界。"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
import hmac
import json
from typing import Protocol


class AuthenticationError(RuntimeError):
    """表示请求没有可用的平台登录身份。"""


class AsyncRedisReader(Protocol):
    """认证只依赖精确键读取，接口刻意不暴露扫描或写入能力。"""

    async def get(self, key: str) -> str | bytes | None: ...

    async def pttl(self, key: str) -> int: ...


@dataclass(frozen=True, slots=True)
class AuthenticatedPrincipal:
    """从平台 AuthPrincipal 安全投影出的最小工作流身份。"""

    user_id: str
    username: str
    unified_auth_id: str
    roles: tuple[str, ...]
    issued_at: datetime
    expires_at: datetime
    session_digest: str


class RedisTokenAuthenticator:
    """按原始 Bearer Token 精确读取平台 Redis，不复制平台用户数据库。"""

    KEY_PREFIX = "test-agent:token:"
    MAX_TOKEN_LENGTH = 4096

    def __init__(self, redis: AsyncRedisReader) -> None:
        self._redis = redis

    async def authenticate(self, authorization: str | None) -> AuthenticatedPrincipal:
        token = self._extract_bearer(authorization)
        key = self.KEY_PREFIX + token
        raw = await self._redis.get(key)
        if raw is None:
            raise AuthenticationError("登录状态已失效")
        ttl_millis = await self._redis.pttl(key)
        if ttl_millis <= 0:
            raise AuthenticationError("登录状态已失效")

        try:
            payload = json.loads(raw.decode("utf-8") if isinstance(raw, bytes) else raw)
            stored_token = self._required_text(payload, "token")
            if not hmac.compare_digest(stored_token, token):
                raise AuthenticationError("登录凭证不匹配")
            user_id_value = payload.get("userId")
            if isinstance(user_id_value, dict):
                user_id = self._required_text(user_id_value, "value")
            else:
                user_id = self._required_text(payload, "userId")
            issued_at = self._parse_time(self._required_text(payload, "issuedAt"))
            expires_at = self._parse_time(self._required_text(payload, "expiresAt"))
            if expires_at <= datetime.now(UTC):
                raise AuthenticationError("登录凭证已过期")
            roles_value = payload.get("roles")
            if roles_value is None:
                roles: tuple[str, ...] = ()
            elif isinstance(roles_value, list) and all(
                isinstance(role, str) and role.strip() for role in roles_value
            ):
                roles = tuple(dict.fromkeys(role.strip() for role in roles_value))
            else:
                raise ValueError("roles invalid")
            return AuthenticatedPrincipal(
                user_id=user_id,
                username=self._required_text(payload, "username"),
                unified_auth_id=self._required_text(payload, "unifiedAuthId"),
                roles=roles,
                issued_at=issued_at,
                expires_at=expires_at,
                session_digest=self.digest(token),
            )
        except AuthenticationError:
            raise
        except (AttributeError, KeyError, TypeError, ValueError, json.JSONDecodeError) as exception:
            raise AuthenticationError("登录凭证格式无效") from exception

    @staticmethod
    def digest(token: str) -> str:
        import hashlib

        return hashlib.sha256(token.encode("utf-8")).hexdigest()

    @staticmethod
    def _extract_bearer(authorization: str | None) -> str:
        if authorization is None or not authorization.startswith("Bearer "):
            raise AuthenticationError("缺少登录凭证")
        token = authorization[7:].strip()
        if not token:
            raise AuthenticationError("缺少登录凭证")
        if len(token) > RedisTokenAuthenticator.MAX_TOKEN_LENGTH:
            raise AuthenticationError("登录凭证格式无效")
        return token

    @staticmethod
    def _required_text(payload: dict[str, object], field: str) -> str:
        value = payload.get(field)
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"{field} invalid")
        return value.strip()

    @staticmethod
    def _parse_time(value: str) -> datetime:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            raise ValueError("timestamp must carry timezone")
        return parsed.astimezone(UTC)
