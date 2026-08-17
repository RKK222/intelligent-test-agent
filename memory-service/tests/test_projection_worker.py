import asyncio
from types import SimpleNamespace
from threading import Event

import pytest

from testagent_memory_service.projection_worker import ProjectionWorker
from testagent_memory_service.settings import MemoryServiceSettings


class RecoveringControl:
    def __init__(self) -> None:
        self.calls = 0
        self.recovered = Event()

    def reconcile_projection_outbox(self, profile_keys: list[str], limit: int) -> int:
        assert profile_keys == ["cpu-profile"]
        assert limit == 2
        self.calls += 1
        if self.calls == 1:
            raise RuntimeError("database temporarily unavailable")
        self.recovered.set()
        return 0

    def claim_outbox(self, worker_id: str, limit: int) -> list[object]:
        del worker_id, limit
        return []


@pytest.mark.asyncio
async def test_projection_worker_survives_transient_control_database_failure() -> None:
    control = RecoveringControl()
    store = SimpleNamespace(
        control=control,
        profiles=[SimpleNamespace(profile=SimpleNamespace(profile_key="cpu-profile"))],
    )
    settings = MemoryServiceSettings(
        _env_file=None,
        api_key="service-key-" + "k" * 32,
        model_gateway_hmac_secret="gateway-secret-" + "s" * 32,
        projection_batch_size=2,
        projection_poll_seconds=0.1,
    )
    worker = ProjectionWorker(store, settings)  # type: ignore[arg-type]

    worker.start()
    await asyncio.wait_for(asyncio.to_thread(control.recovered.wait), timeout=1.0)
    await worker.close()

    assert control.calls >= 2
