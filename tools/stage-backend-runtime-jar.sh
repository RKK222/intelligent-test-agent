#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "Usage: tools/stage-backend-runtime-jar.sh <source-jar> <runtime-dir>" >&2
  exit 2
fi

source_jar="$1"
runtime_dir="$2"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_command cp
require_command jar
require_command mktemp
require_command mv

mkdir -p "${runtime_dir}"
temporary_jar="$(mktemp "${runtime_dir}/test-agent-app.XXXXXX")"
runtime_jar="${temporary_jar}.jar"
mv "${temporary_jar}" "${runtime_jar}"

# Spring Boot 会按需从可执行 JAR 读取类；必须复制为本次启动专属文件，避免后续 Maven 构建原地覆盖运行产物。
if ! cp "${source_jar}" "${runtime_jar}"; then
  rm -f "${runtime_jar}"
  echo "Failed to stage backend runtime jar from ${source_jar}." >&2
  exit 1
fi
if ! jar tf "${runtime_jar}" >/dev/null; then
  rm -f "${runtime_jar}"
  echo "Staged backend runtime jar is invalid: ${runtime_jar}." >&2
  exit 1
fi

printf '%s\n' "${runtime_jar}"
