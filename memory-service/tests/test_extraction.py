import json
from pathlib import Path

import httpx
import pytest

from testagent_memory_service.extraction import ModelGatewayExtractor
from testagent_memory_service.settings import MemoryServiceSettings


@pytest.mark.asyncio
async def test_model_gateway_extractor_binds_memory_user_and_run_headers(tmp_path: Path) -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(
            200,
            json={
                "choices": [
                    {"message": {"content": json.dumps({"candidates": []})}}
                ]
            },
        )

    settings = MemoryServiceSettings(
        _env_file=None,
        api_key="service-key-" + "k" * 32,
        history_db_path=tmp_path / "history.db",
        model_root=tmp_path / "models",
        model_path=tmp_path / "models" / "bge",
        extraction_gateway_url="http://platform/api/internal/platform/model-gateway/v1",
    )
    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    extractor = ModelGatewayExtractor(settings, client)

    result = await extractor.extract(
        model="internal-chat",
        grant="mfg_" + "g" * 32,
        user_id="usr_1",
        run_id="run_1",
        messages=[{"role": "user", "content": "结论必须带证据"}],
        task_type="DEFECT_ANALYSIS",
        application_id="app_1",
        trace_id="trace_1",
    )
    await client.aclose()

    assert result == []
    assert len(captured) == 1
    assert captured[0].headers["x-memory-user-id"] == "usr_1"
    assert captured[0].headers["x-memory-run-id"] == "run_1"
    assert captured[0].headers["authorization"] == "Bearer mfg_" + "g" * 32
    assert captured[0].url.path.endswith("/chat/completions")
