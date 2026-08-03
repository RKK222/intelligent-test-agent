import argparse

import uvicorn


def main() -> None:
    parser = argparse.ArgumentParser(description="Test Agent Analysis Runner")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8091)
    arguments = parser.parse_args()
    uvicorn.run(
        "testagent_runner.factory:build_app",
        host=arguments.host,
        port=arguments.port,
        factory=True,
        proxy_headers=False,
        access_log=True,
    )


if __name__ == "__main__":
    main()
