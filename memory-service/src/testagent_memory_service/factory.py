"""Uvicorn 生产装配入口。"""

from testagent_memory_service.api import create_app, production_dependencies
from testagent_memory_service.settings import MemoryServiceSettings


settings = MemoryServiceSettings()
app = create_app(production_dependencies(settings))
