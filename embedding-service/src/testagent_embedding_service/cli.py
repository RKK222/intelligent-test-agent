"""Uvicorn 启动入口。"""

import argparse
import os

import uvicorn


def main() -> None:
    parser = argparse.ArgumentParser(description="Test Agent CPU Embedding Service")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=18989)
    args = parser.parse_args()
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    os.environ["HF_DATASETS_OFFLINE"] = "1"
    uvicorn.run(
        "testagent_embedding_service.factory:app",
        host=args.host,
        port=args.port,
        access_log=False,
    )


if __name__ == "__main__":
    main()
