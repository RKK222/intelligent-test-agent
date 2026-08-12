"""记忆服务命令行入口。"""

from __future__ import annotations

import argparse
import os

import uvicorn


def main() -> None:
    parser = argparse.ArgumentParser(description="Test Agent Memory Service")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=18888)
    args = parser.parse_args()
    os.environ["MEM0_TELEMETRY"] = "False"
    uvicorn.run(
        "testagent_memory_service.factory:app",
        host=args.host,
        port=args.port,
        access_log=False,
    )


if __name__ == "__main__":
    main()
