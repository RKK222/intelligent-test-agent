"""通过平台短期 mfg_ 授权执行记忆候选抽取，不持久化输入消息。"""

from __future__ import annotations

import json
import re
from typing import Any, Protocol

import httpx

from testagent_memory_service.settings import MemoryServiceSettings


EXTRACTION_SYSTEM_PROMPT = """你是 QA Agent 的长期工作习惯抽取器。只抽取测试人员稳定、可复用的工作习惯、偏好、要求和判断标准。
不得抽取聊天原文、项目业务事实、账号密钥、团队统一规范、一次任务的临时要求或可泛化的通用测试方法。
每个候选必须说明适用任务类型、建议范围、是否为用户明确要求、是否临时，以及置信度。明确的新习惯可以标记 replacesExisting=true；不确定冲突不得自行覆盖。
只输出 JSON：{"candidates":[{"content":"...","scopeSuggestion":"PERSONAL_GLOBAL|PERSONAL_APPLICATION|TEAM_APPLICATION","taskTypes":["GENERAL|TEST_CASE_GENERATION|TEST_DATA_PREPARATION|REQUIREMENT_ANALYSIS|TEST_PLAN_DESIGN|DEFECT_ANALYSIS|ROOT_CAUSE_ANALYSIS|AUTOMATION_TESTING|RISK_ANALYSIS|TEST_REPORTING|RESULT_ACCEPTANCE"],"explicit":true,"temporary":false,"replacesExisting":false,"confidence":0.0,"reason":"..."}]}。没有合格候选时返回空数组。"""


class ExtractionUnavailable(RuntimeError):
    pass


class Extractor(Protocol):
    async def extract(
        self,
        *,
        model: str,
        grant: str,
        user_id: str,
        run_id: str,
        messages: list[dict[str, str]],
        task_type: str,
        application_id: str | None,
        trace_id: str,
    ) -> list[dict[str, Any]]: ...


class ModelGatewayExtractor:
    """OpenAI-compatible 调用只在内存中处理消息和 mfg_ token。"""

    def __init__(
        self,
        settings: MemoryServiceSettings,
        client: httpx.AsyncClient | None = None,
    ):
        self.settings = settings
        self._owns_client = client is None
        self.client = client or httpx.AsyncClient(
            timeout=settings.request_timeout_seconds,
            follow_redirects=False,
            trust_env=False,
        )

    async def close(self) -> None:
        if self._owns_client:
            await self.client.aclose()

    async def extract(
        self,
        *,
        model: str,
        grant: str,
        user_id: str,
        run_id: str,
        messages: list[dict[str, str]],
        task_type: str,
        application_id: str | None,
        trace_id: str,
    ) -> list[dict[str, Any]]:
        base_url = self.settings.extraction_gateway_url
        if base_url is None:
            raise ExtractionUnavailable("平台抽取模型网关未配置")
        if not re.fullmatch(r"mfg_[A-Za-z0-9_-]{20,256}", grant):
            raise PermissionError("记忆模型授权格式无效")
        context = {
            "userId": user_id,
            "runId": run_id,
            "taskType": task_type,
            "applicationId": application_id,
            "messages": messages,
        }
        body = {
            "model": model,
            "messages": [
                {"role": "system", "content": EXTRACTION_SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": json.dumps(context, ensure_ascii=False, separators=(",", ":")),
                },
            ],
            "temperature": 0.1,
            "max_tokens": 1400,
            "stream": False,
            "response_format": {"type": "json_object"},
        }
        try:
            response = await self.client.post(
                f"{base_url}/chat/completions",
                headers={
                    "Authorization": f"Bearer {grant}",
                    "X-Trace-Id": trace_id,
                    "X-Memory-User-Id": str(context.get("userId", "")),
                    "X-Memory-Run-Id": str(context.get("runId", "")),
                },
                json=body,
            )
        except httpx.HTTPError as exception:
            raise ExtractionUnavailable("平台抽取模型暂不可用") from exception
        if response.status_code in {401, 403}:
            raise PermissionError("记忆模型授权无效或已过期")
        if response.status_code < 200 or response.status_code >= 300:
            raise ExtractionUnavailable("平台抽取模型返回失败")
        try:
            payload = response.json()
            content = payload["choices"][0]["message"]["content"]
            parsed = json.loads(_strip_code_fence(str(content)))
            candidates = parsed.get("candidates", [])
        except (KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError) as exception:
            raise ExtractionUnavailable("平台抽取模型响应格式无效") from exception
        return candidates if isinstance(candidates, list) else []


def _strip_code_fence(content: str) -> str:
    normalized = content.strip()
    if normalized.startswith("```") and normalized.endswith("```"):
        normalized = re.sub(r"^```(?:json)?\s*", "", normalized, count=1)
        normalized = re.sub(r"\s*```$", "", normalized, count=1)
    return normalized
