#!/usr/bin/env bash
set -euo pipefail

catalog_file="${1:-}"
if [[ -z "${catalog_file}" || $# -ne 1 || ! -f "${catalog_file}" || ! -r "${catalog_file}" ]]; then
  echo "Usage: verify-opencode-model-priority.sh <opencode-models.json>" >&2
  exit 64
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "Required command not found: jq" >&2
  exit 64
fi
# OpenCode V2 仍把 models.dev 的 release_date 转成 time.released，并按时间倒序返回
# /api/model。本门禁只约束本次发布快照，不参与 worker 对 `.114` 灰度旧快照的常规校验。
if ! jq -e '
  .["enterprise-qwen"].models["Qwen3.6-27B"].release_date as $qwen
  | .["enterprise-deepseek"].models["DeepSeek-V4-Flash-W8A8"].release_date as $deepseek
  | ($qwen | test("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))
    and ($deepseek | test("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))
    and ($qwen > $deepseek)
' "${catalog_file}" >/dev/null; then
  echo "OpenCode release catalog must sort Qwen before DeepSeek by release_date: ${catalog_file}" >&2
  exit 64
fi
