#!/usr/bin/env bash
set -euo pipefail

catalog_file="${1:-}"
public_config_file="${2:-}"
if [[ -z "${catalog_file}" || $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: validate-opencode-models.sh <opencode-models.json> [opencode.jsonc]" >&2
  exit 64
fi

if [[ ! -f "${catalog_file}" || ! -r "${catalog_file}" ]]; then
  echo "OpenCode models catalog is not a readable regular file: ${catalog_file}" >&2
  exit 64
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "Required command not found: jq" >&2
  exit 64
fi

# OpenCode V2 的 models.dev 插件仍直接读取 limit 和能力字段；只检查 JSON 根对象会让
# 缺字段的目录进入运行时并使 /config、/provider 等接口整体失败，因此在重建 worker 前失败关闭。
if ! jq -e '
  def nonempty_string:
    type == "string" and length > 0;
  def string_array:
    type == "array" and all(.[]; nonempty_string);
  def modality:
    . == "text" or . == "audio" or . == "image" or . == "video" or . == "pdf";
  def modalities:
    type == "object"
    and (.input | type == "array" and all(.[]; modality))
    and (.output | type == "array" and all(.[]; modality));
  def interleaved:
    . == true
    or (
      type == "object"
      and (.field == "reasoning" or .field == "reasoning_content" or .field == "reasoning_details")
    );
  type == "object"
  and length > 0
  and all(
    to_entries[];
    .key as $provider_key
    | .value
    | type == "object"
      and .id == $provider_key
      and (.id | nonempty_string)
      and (.name | nonempty_string)
      and (.env | string_array)
      and ((has("api") | not) or (.api | nonempty_string))
      and ((has("npm") | not) or (.npm | nonempty_string))
      and (.models | type == "object" and length > 0)
      and all(
        .models | to_entries[];
        .key as $model_key
        | .value
        | type == "object"
          and .id == $model_key
          and (.id | nonempty_string)
          and (.name | nonempty_string)
          and (.release_date | nonempty_string)
          and (.attachment | type == "boolean")
          and (.reasoning | type == "boolean")
          and (.temperature | type == "boolean")
          and (.tool_call | type == "boolean")
          and (.limit | type == "object")
          and (.limit.context | type == "number" and . > 0)
          and (.limit.output | type == "number" and . > 0)
          and ((.limit | has("input") | not) or (.limit.input | type == "number" and . > 0))
          and ((has("interleaved") | not) or (.interleaved | interleaved))
          and ((has("modalities") | not) or (.modalities | modalities))
      )
  )
' "${catalog_file}" >/dev/null; then
  echo "OpenCode models catalog is incompatible with the required OpenCode 2.0.18 models.dev structure: ${catalog_file}" >&2
  exit 64
fi

# 仓库固定样例是严格 JSON 语法的 JSONC。Mac 打包时额外检查两份配置的一致性；worker 现场仅传
# 第一个参数，因为公共配置由平台 Git 发布且不属于容器挂载文件。
if [[ -n "${public_config_file}" ]]; then
  if [[ ! -f "${public_config_file}" || ! -r "${public_config_file}" ]]; then
    echo "OpenCode public config is not a readable regular file: ${public_config_file}" >&2
    exit 64
  fi
  if ! jq -e --slurpfile catalog "${catalog_file}" '
    def ref_exists($ref):
      $ref
      | type == "string"
        and (
          split("/") as $parts
          | ($parts | length) > 1
            and ($catalog[0][$parts[0]].models[($parts[1:] | join("/"))] | type == "object")
        );
    type == "object"
    and (.provider | type == "object" and length > 0)
    and ref_exists(.model)
    and ref_exists(.small_model)
    and all(
      .provider | to_entries[];
      .key as $provider_id
      | .value as $provider
      | ($catalog[0][$provider_id] | type == "object")
        and (($provider.npm // null) == ($catalog[0][$provider_id].npm // null))
        and ($provider.models | type == "object" and length > 0)
        and all(
          $provider.models | to_entries[];
          .key as $model_id
          | .value as $model
          | ($catalog[0][$provider_id].models[$model_id] | type == "object")
            and (($model.id // $model_id) == $catalog[0][$provider_id].models[$model_id].id)
            and ($model.reasoning == $catalog[0][$provider_id].models[$model_id].reasoning)
            and ($model.temperature == $catalog[0][$provider_id].models[$model_id].temperature)
            and ($model.tool_call == $catalog[0][$provider_id].models[$model_id].tool_call)
            and ($model.interleaved == $catalog[0][$provider_id].models[$model_id].interleaved)
            and ($model.limit.context == $catalog[0][$provider_id].models[$model_id].limit.context)
            and ($model.limit.output == $catalog[0][$provider_id].models[$model_id].limit.output)
        )
    )
    and (
      .mcp.code_analysis.environment as $mcp
      | ($mcp.TEST_AGENT_CODEX_CONTEXT_WINDOW | tonumber) as $context
      | [
          $catalog[0][]
          | .models[]
          | select(.id == $mcp.TEST_AGENT_CODEX_MODEL and .limit.context == $context)
        ]
        | length == 1
    )
  ' "${public_config_file}" >/dev/null; then
    echo "OpenCode public config and models catalog are inconsistent: ${public_config_file} ${catalog_file}" >&2
    exit 64
  fi
fi
