#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
BACKEND_DIR="${ROOT_DIR}/backend"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_file() {
  local file="$1"
  [[ -f "${file}" ]] || fail "missing file ${file}"
}

assert_dir() {
  local dir="$1"
  [[ -d "${dir}" ]] || fail "missing directory ${dir}"
}

assert_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq "${needle}" "${file}" || fail "${file} does not contain ${needle}"
}

modules=(
  test-agent-common
  test-agent-domain
  test-agent-observability
  test-agent-opencode-sdk-generated
  test-agent-opencode-client
  test-agent-persistence
  test-agent-event
  test-agent-test-support
  test-agent-app
)

assert_file "${BACKEND_DIR}/pom.xml"
assert_contains "${BACKEND_DIR}/pom.xml" "<artifactId>spring-boot-starter-parent</artifactId>"
assert_contains "${BACKEND_DIR}/pom.xml" "<version>4.1.0</version>"
assert_contains "${BACKEND_DIR}/pom.xml" "<java.version>21</java.version>"

for module in "${modules[@]}"; do
  assert_dir "${BACKEND_DIR}/${module}"
  assert_file "${BACKEND_DIR}/${module}/pom.xml"
  assert_file "${BACKEND_DIR}/${module}/README.md"
  assert_contains "${BACKEND_DIR}/${module}/README.md" "工程定位"
  assert_contains "${BACKEND_DIR}/${module}/README.md" "技术栈"
  assert_contains "${BACKEND_DIR}/${module}/README.md" "后续 AI 编码指引"
done

[[ ! -d "${BACKEND_DIR}/test-agent-gateway" ]] || fail "test-agent-gateway must not exist"
[[ ! -d "${BACKEND_DIR}/test-agent-control-plane" ]] || fail "test-agent-control-plane must not exist"

assert_file "${BACKEND_DIR}/test-agent-app/src/main/java/com/example/testagent/app/TestAgentApplication.java"
assert_file "${BACKEND_DIR}/test-agent-opencode-sdk-generated/src/main/java/com/example/opencode/sdk/ApiClient.java"
assert_dir "${BACKEND_DIR}/test-agent-opencode-sdk-generated/src/main/java/com/example/opencode/sdk/api"
assert_dir "${BACKEND_DIR}/test-agent-opencode-sdk-generated/src/main/java/com/example/opencode/sdk/model"

boot_jar_count="$(find "${BACKEND_DIR}" -path '*/target/*.jar' -name 'test-agent-*.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' | grep -c '/test-agent-app/target/' || true)"
[[ "${boot_jar_count}" -le 1 ]] || fail "expected at most one runnable app jar, found ${boot_jar_count}"

assert_contains "${ROOT_DIR}/docs/implementation-plan.md" "test-agent-app"
assert_contains "${ROOT_DIR}/docs/implementation-plan.md" "Maven multi-module"
assert_contains "${ROOT_DIR}/docs/implementation-plan.md" "生成可执行 Spring Boot jar"

echo "Backend skeleton verification passed."
