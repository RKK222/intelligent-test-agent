#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
HELPER="${ROOT_DIR}/tools/lobehub-dev-services.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-lobehub-dev-helper.XXXXXX")"

cleanup() {
  rm -rf "${TEST_ROOT}"
}
trap cleanup EXIT

mkdir -p "${TEST_ROOT}/bin" "${TEST_ROOT}/logs"
SCREEN_CALLS="${TEST_ROOT}/screen.calls"
KILL_CALLS="${TEST_ROOT}/kill.calls"

# 本地 helper 生成的运行环境必须把 fork dev server 限制到 loopback，不能只把访问 URL 写成 127.0.0.1。
GENERATED_ENV="${TEST_ROOT}/lobehub-dev.env"
TEST_AGENT_DEV_LOG_DIR="${TEST_ROOT}/logs" \
  LOBEHUB_DEV_ENV_FILE="${GENERATED_ENV}" \
  LOBEHUB_DEV_APP_URL="http://127.0.0.1:3210" \
  bash "${HELPER}" prepare >/dev/null
grep -Fx 'LOBEHUB_DEV_HOST=127.0.0.1' "${GENERATED_ENV}" >/dev/null || {
  echo 'LobeHub dev helper must bind the fork server to 127.0.0.1.' >&2
  exit 1
}
grep -Fx 'TEST_AGENT_LOBEHUB_DEV_BOOTSTRAP_ENABLED=true' "${GENERATED_ENV}" >/dev/null || {
  echo 'LobeHub dev helper must opt the platform backend into local parameter bootstrap.' >&2
  exit 1
}
grep -Fx 'TEST_AGENT_LOBEHUB_DEV_BASE_URL=http://127.0.0.1:3210' "${GENERATED_ENV}" >/dev/null || {
  echo 'LobeHub dev helper must pass the fixed loopback chat origin to the platform bootstrap.' >&2
  exit 1
}
grep -Fx 'TEST_AGENT_LOBEHUB_DEV_EMAIL_DOMAIN=lobehub.local' "${GENERATED_ENV}" >/dev/null || {
  echo 'LobeHub dev helper must replace the disabled email-domain placeholder for local bootstrap.' >&2
  exit 1
}
[[ "$(stat -c '%a' "${GENERATED_ENV}" 2>/dev/null || stat -f '%Lp' "${GENERATED_ENV}")" == 600 ]] || {
  echo 'Generated LobeHub dev environment must keep mode 0600.' >&2
  exit 1
}

# 同时伪造主会话、重复 scheduler 和相似前缀，验证 stop 只按完整 screen ID 清理目标。
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'if [[ "${1:-}" == "-list" ]]; then' \
  '  printf "%s\n" "There are screens on:" "  101.test-agent-lobehub (Detached)" "  102.test-agent-lobehub-scheduler (Detached)" "  103.test-agent-lobehub-scheduler (Detached)" "  104.test-agent-lobehub-extra (Detached)"' \
  '  exit 0' \
  'fi' \
  'printf "%s\n" "$*" >>"${TEST_SCREEN_CALLS:?}"' \
  >"${TEST_ROOT}/bin/screen"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'printf "%s\n" "101 201" "102 202" "103 203" "104 204"' \
  >"${TEST_ROOT}/bin/ps"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'printf "%s\n" "$*" >>"${TEST_KILL_CALLS:?}"' \
  >"${TEST_ROOT}/bin/kill"
printf '%s\n' 'enable -n kill' >"${TEST_ROOT}/bash-env"
chmod 0755 "${TEST_ROOT}/bin/screen" "${TEST_ROOT}/bin/ps" "${TEST_ROOT}/bin/kill"

PATH="${TEST_ROOT}/bin:${PATH}" \
  BASH_ENV="${TEST_ROOT}/bash-env" \
  TEST_SCREEN_CALLS="${SCREEN_CALLS}" \
  TEST_KILL_CALLS="${KILL_CALLS}" \
  TEST_AGENT_DEV_LOG_DIR="${TEST_ROOT}/logs" \
  LOBEHUB_DEV_ENV_FILE="${TEST_ROOT}/missing.env" \
  bash "${HELPER}" stop

expected_calls="$(printf '%s\n' \
  '-S 101.test-agent-lobehub -X quit' \
  '-S 102.test-agent-lobehub-scheduler -X quit' \
  '-S 103.test-agent-lobehub-scheduler -X quit')"
actual_calls="$(cat "${SCREEN_CALLS}" 2>/dev/null || true)"
if [[ "${actual_calls}" != "${expected_calls}" ]]; then
  printf 'Unexpected screen stop calls.\nExpected:\n%s\nActual:\n%s\n' \
    "${expected_calls}" "${actual_calls}" >&2
  exit 1
fi

expected_kills="$(printf '%s\n' '-TERM -201' '-TERM -202' '-TERM -203')"
actual_kills="$(cat "${KILL_CALLS}" 2>/dev/null || true)"
if [[ "${actual_kills}" != "${expected_kills}" ]]; then
  printf 'Unexpected screen process-group kills.\nExpected:\n%s\nActual:\n%s\n' \
    "${expected_kills}" "${actual_kills}" >&2
  exit 1
fi

echo 'LobeHub dev service helper tests passed.'
