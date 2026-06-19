#!/usr/bin/env bash
set -euo pipefail

OPENCODE_BASE_URL="${OPENCODE_BASE_URL:-http://127.0.0.1:4096}"
OPENCODE_BASE_URL="${OPENCODE_BASE_URL%/}"
GENERATOR_VERSION="7.23.0"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd -P)"
SDK_DIR="${REPO_ROOT}/tools/opencode-sdk-generator"
RAW_SPEC_FILE="${SDK_DIR}/pinned-opencode-spec.raw.json"
SPEC_FILE="${SDK_DIR}/pinned-opencode-spec.json"
CONFIG_FILE="${SDK_DIR}/openapi-generator-config.yaml"
OPENAPI_TOOLS_FILE="${SDK_DIR}/openapitools.json"

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
}

java_spec_version() {
  local java_binary="$1"
  "${java_binary}" -XshowSettings:properties -version 2>&1 \
    | awk -F= '/java.specification.version/ { gsub(/[[:space:]]/, "", $2); print $2; exit }'
}

java_major_version() {
  local spec_version="$1"
  if [[ "${spec_version}" == 1.* ]]; then
    echo "${spec_version#1.}" | cut -d. -f1
  else
    echo "${spec_version}" | cut -d. -f1
  fi
}

resolve_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    echo "${JAVA_HOME}"
    return
  fi

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    if /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
      /usr/libexec/java_home -v 21
      return
    fi
  fi

  local java_binary
  java_binary="$(command -v java || true)"
  if [[ -n "${java_binary}" ]]; then
    cd "$(dirname "${java_binary}")/.." && pwd -P
    return
  fi

  echo "Unable to locate Java. Install Java 21 or set JAVA_HOME." >&2
  exit 1
}

write_readme() {
  cat > "${SDK_DIR}/README.md" <<EOF
# opencode Java SDK Generator

This directory contains the generated Java SDK for opencode server plus the
configuration required to reproduce it.

## Client Choice

The SDK uses OpenAPI Generator's Java \`webclient\` library because the platform
architecture is based on Java 21, Spring Boot 4, WebFlux, SSE/event streaming,
and a future \`OpencodeClientFacade\` wrapper.

## Source

- OpenAPI source: \`${OPENCODE_BASE_URL}/doc\`
- Raw snapshot: \`pinned-opencode-spec.raw.json\`
- Normalized snapshot: \`pinned-opencode-spec.json\`
- Generator version: \`${GENERATOR_VERSION}\`

The normalized spec only de-duplicates top-level \`tags\` by name and injects a
default \`servers[0].url\`. It does not modify \`paths\`, \`components\`, or
operation schemas.

## Regenerate

\`\`\`bash
tools/generate-opencode-java-sdk.sh
\`\`\`

Override the opencode server URL when needed:

\`\`\`bash
OPENCODE_BASE_URL=http://127.0.0.1:4096 tools/generate-opencode-java-sdk.sh
\`\`\`

## Verify

\`\`\`bash
tools/opencode-sdk-generator/gradlew -p tools/opencode-sdk-generator clean build -x test --no-daemon
\`\`\`

## Rules

- Do not hand edit generated Java sources.
- If generation or compilation fails, fix the generator config or safe spec
  normalization step first.
- Business modules should depend on a facade layer, not directly on generated
  SDK DTOs or API classes.
EOF
}

require_command curl
require_command jq
require_command openapi-generator-cli

RESOLVED_JAVA_HOME="$(resolve_java_home)"
JAVA_BINARY="${RESOLVED_JAVA_HOME}/bin/java"
JAVA_SPEC_VERSION="$(java_spec_version "${JAVA_BINARY}")"
JAVA_MAJOR_VERSION="$(java_major_version "${JAVA_SPEC_VERSION}")"

if [[ -z "${JAVA_MAJOR_VERSION}" || "${JAVA_MAJOR_VERSION}" -lt 21 ]]; then
  echo "Java 21 or newer is required. Resolved ${JAVA_BINARY} reports ${JAVA_SPEC_VERSION}." >&2
  exit 1
fi

export JAVA_HOME="${RESOLVED_JAVA_HOME}"
export PATH="${JAVA_HOME}/bin:${PATH}"

mkdir -p "${SDK_DIR}"

echo "Downloading opencode OpenAPI spec from ${OPENCODE_BASE_URL}/doc"
curl -fsSL "${OPENCODE_BASE_URL}/doc" -o "${RAW_SPEC_FILE}"

echo "Normalizing spec metadata"
jq --arg baseUrl "${OPENCODE_BASE_URL}" '
  .tags = ((.tags // []) | unique_by(.name))
  | .servers = [{"url": $baseUrl}]
' "${RAW_SPEC_FILE}" > "${SPEC_FILE}.tmp"
mv "${SPEC_FILE}.tmp" "${SPEC_FILE}"

echo "Verifying normalized spec preserves paths and components"
jq -e --slurp '.[0].paths == .[1].paths and .[0].components == .[1].components' \
  "${RAW_SPEC_FILE}" \
  "${SPEC_FILE}" >/dev/null

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/opencode-sdk-generator.XXXXXX")"
cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

GENERATED_DIR="${TMP_ROOT}/sdk"
mkdir -p "${GENERATED_DIR}"

echo "Generating Java SDK with OpenAPI Generator ${GENERATOR_VERSION}"
openapi-generator-cli --openapitools "${OPENAPI_TOOLS_FILE}" generate \
  -i "${SPEC_FILE}" \
  -g java \
  -o "${GENERATED_DIR}" \
  -c "${CONFIG_FILE}" \
  --global-property apiDocs=false,modelDocs=false,apiTests=false,modelTests=false \
  --type-mappings AnyOf=Object \
  --import-mappings AnyOf=java.lang.Object

echo "Refreshing generated SDK project in ${SDK_DIR}"
rm -rf \
  "${SDK_DIR}/.github" \
  "${SDK_DIR}/.openapi-generator" \
  "${SDK_DIR}/api" \
  "${SDK_DIR}/build" \
  "${SDK_DIR}/docs" \
  "${SDK_DIR}/gradle" \
  "${SDK_DIR}/src"

rm -f \
  "${SDK_DIR}/.gitignore" \
  "${SDK_DIR}/.openapi-generator-ignore" \
  "${SDK_DIR}/.travis.yml" \
  "${SDK_DIR}/build.gradle" \
  "${SDK_DIR}/build.sbt" \
  "${SDK_DIR}/git_push.sh" \
  "${SDK_DIR}/gradle.properties" \
  "${SDK_DIR}/gradlew" \
  "${SDK_DIR}/gradlew.bat" \
  "${SDK_DIR}/pom.xml" \
  "${SDK_DIR}/README.md" \
  "${SDK_DIR}/settings.gradle"

cp -R "${GENERATED_DIR}/." "${SDK_DIR}/"
chmod +x "${SDK_DIR}/gradlew"
write_readme

echo "Building generated SDK"
"${SDK_DIR}/gradlew" -p "${SDK_DIR}" clean build -x test --no-daemon

echo "opencode Java SDK generation and build completed."
