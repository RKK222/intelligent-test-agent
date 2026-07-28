#!/usr/bin/env python3
"""下载并校验 OmniTools 抠图工具离线运行所需的锁定资源。"""

from __future__ import annotations

import argparse
import hashlib
import json
import urllib.request
from pathlib import Path


VERSION = "1.7.0"
BASE_URL = (
    "https://staticimgly.com/@imgly/background-removal-data/"
    f"{VERSION}/dist/"
)
REQUIRED_KEYS = (
    "/onnxruntime-web/ort-wasm-simd-threaded.wasm",
    "/onnxruntime-web/ort-wasm-simd-threaded.mjs",
    "/models/isnet_fp16",
)


def download(url: str) -> bytes:
    """仅从锁定的 IMG.LY 资源根地址读取文件。"""
    if not url.startswith(BASE_URL):
        raise ValueError(f"拒绝下载非锁定来源: {url}")
    request = urllib.request.Request(url, headers={"User-Agent": "test-agent-toolbox/1"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    manifest = json.loads(download(f"{BASE_URL}resources.json"))
    selected = {key: manifest[key] for key in REQUIRED_KEYS}
    args.output.mkdir(parents=True, exist_ok=True)

    # IMG.LY 按内容哈希切分大文件；逐块校验可保证离线包可复现且未损坏。
    for entry in selected.values():
        for chunk in entry["chunks"]:
            name = chunk["name"]
            target = args.output / name
            if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == name:
                continue
            payload = download(f"{BASE_URL}{name}")
            actual = hashlib.sha256(payload).hexdigest()
            if actual != name:
                raise ValueError(f"资源哈希不一致: {name} != {actual}")
            target.write_bytes(payload)

    (args.output / "resources.json").write_text(
        json.dumps(selected, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"IMG.LY {VERSION} 离线资源已写入 {args.output}")


if __name__ == "__main__":
    main()
