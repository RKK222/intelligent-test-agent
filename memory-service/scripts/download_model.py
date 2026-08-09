"""构建期下载并锁定 BGE 权重；运行期不会调用此脚本。"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from huggingface_hub import snapshot_download
from sentence_transformers import SentenceTransformer


MODEL_ID = "BAAI/bge-small-zh-v1.5"
REVISION = "7999e1d3359715c523056ef9478215996d62a620"
DIMENSION = 512


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--destination", required=True, type=Path)
    parser.add_argument("--model", default=MODEL_ID, choices=[MODEL_ID])
    parser.add_argument("--revision", default=REVISION, choices=[REVISION])
    args = parser.parse_args()
    if not args.destination.is_absolute():
        raise SystemExit("模型目标目录必须为绝对路径")
    args.destination.mkdir(parents=True, exist_ok=True)
    snapshot_download(
        repo_id=args.model,
        revision=args.revision,
        local_dir=args.destination,
    )
    model = SentenceTransformer(
        str(args.destination),
        device="cpu",
        local_files_only=True,
        trust_remote_code=False,
    )
    dimension = int(model.get_sentence_embedding_dimension())
    if dimension != DIMENSION:
        raise SystemExit(f"模型维度不符合合同：{dimension}")
    manifest = {
        "model": args.model,
        "revision": args.revision,
        "dimension": dimension,
        "normalized": True,
    }
    (args.destination / ".qa-memory-model.json").write_text(
        json.dumps(manifest, ensure_ascii=False, sort_keys=True) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
