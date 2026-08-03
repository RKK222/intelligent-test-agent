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
grep -Fx 'AUTH_TRUSTED_ORIGINS=http://127.0.0.1:3210,http://127.0.0.1:3000' "${GENERATED_ENV}" >/dev/null || {
  echo 'LobeHub dev helper must trust both the chat origin and platform frontend origin.' >&2
  exit 1
}
grep -Fx 'TEST_AGENT_LOBEHUB_DEV_BOOTSTRAP_ENABLED=true' "${GENERATED_ENV}" >/dev/null || {
  echo 'LobeHub dev helper must opt the platform backend into local parameter bootstrap.' >&2
  exit 1
}
grep -Fx 'TEST_AGENT_LOBEHUB_DEV_TARGET_ENABLED=true' "${GENERATED_ENV}" >/dev/null || {
  echo 'LobeHub offline dev mode must keep the platform entry enabled.' >&2
  exit 1
}
grep -Fx 'PLATFORM_SSO_ENABLED=1' "${GENERATED_ENV}" >/dev/null || {
  echo 'LobeHub offline dev mode must keep platform SSO enabled.' >&2
  exit 1
}
grep -Fx 'LOBEHUB_ENTERPRISE_OFFLINE=1' "${GENERATED_ENV}" >/dev/null || {
  echo 'LobeHub offline dev mode must keep the enterprise network policy enabled.' >&2
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

# 在线模式只切换认证与网络策略，不改写任何用户 dotenv，并关闭不可用的平台票据入口。
ONLINE_ENV="${TEST_ROOT}/lobehub-online.env"
TEST_AGENT_LOBEHUB_DEV_MODE=online \
  TEST_AGENT_DEV_LOG_DIR="${TEST_ROOT}/logs" \
  LOBEHUB_DEV_ENV_FILE="${ONLINE_ENV}" \
  bash "${HELPER}" prepare >/dev/null
grep -Fx 'TEST_AGENT_LOBEHUB_DEV_MODE=online' "${ONLINE_ENV}" >/dev/null || {
  echo 'LobeHub online dev mode must be recorded in its generated runtime environment.' >&2
  exit 1
}
grep -Fx 'PLATFORM_SSO_ENABLED=0' "${ONLINE_ENV}" >/dev/null || {
  echo 'LobeHub online dev mode must disable platform SSO.' >&2
  exit 1
}
grep -Fx 'LOBEHUB_ENTERPRISE_OFFLINE=0' "${ONLINE_ENV}" >/dev/null || {
  echo 'LobeHub online dev mode must allow the live Community catalog.' >&2
  exit 1
}
grep -Fx 'TEST_AGENT_LOBEHUB_DEV_TARGET_ENABLED=false' "${ONLINE_ENV}" >/dev/null || {
  echo 'LobeHub online dev mode must disable the incompatible platform ticket entry.' >&2
  exit 1
}

# prepare 必须在读取或写入前拒绝软链接，避免覆盖链接指向的任意文件。
SYMLINK_TARGET="${TEST_ROOT}/symlink-target"
SYMLINK_ENV="${TEST_ROOT}/symlink.env"
printf '%s\n' 'sentinel-content' >"${SYMLINK_TARGET}"
ln -s "${SYMLINK_TARGET}" "${SYMLINK_ENV}"
set +e
symlink_output="$(TEST_AGENT_DEV_LOG_DIR="${TEST_ROOT}/logs" \
  LOBEHUB_DEV_ENV_FILE="${SYMLINK_ENV}" bash "${HELPER}" prepare 2>&1)"
symlink_status=$?
set -e
[[ ${symlink_status} -ne 0 ]] || {
  echo 'LobeHub dev helper must reject a symbolic-link environment file.' >&2
  exit 1
}
[[ "${symlink_output}" == *'must not be a symbolic link'* ]] || {
  echo 'LobeHub dev helper must explain the symbolic-link rejection.' >&2
  exit 1
}
[[ "$(cat "${SYMLINK_TARGET}")" == 'sentinel-content' ]] || {
  echo 'LobeHub dev helper must not overwrite the symbolic-link target.' >&2
  exit 1
}

# restart 必须使用仓库 shim 支持的 `corepack pnpm` 形式，并在拉起容器前校验 fork 锁定的版本。
FAKE_FORK="${TEST_ROOT}/lobehub-platform"
DOCKER_CALLS="${TEST_ROOT}/docker.calls"
mkdir -p "${FAKE_FORK}/.git"
printf '%s\n' '{"packageManager":"pnpm@10.33.0+sha512-test"}' >"${FAKE_FORK}/package.json"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'if [[ "$*" == "pnpm --version" ]]; then printf "%s\n" "9.0.0"; exit 0; fi' \
  'printf "%s\n" "unexpected corepack arguments: $*" >&2' \
  'exit 97' \
  >"${TEST_ROOT}/bin/corepack"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'printf "%s\n" "$*" >>"${TEST_DOCKER_CALLS:?}"' \
  'exit 98' \
  >"${TEST_ROOT}/bin/docker"
chmod 0755 "${TEST_ROOT}/bin/corepack" "${TEST_ROOT}/bin/docker"
set +e
pnpm_output="$(PATH="${TEST_ROOT}/bin:${PATH}" \
  TEST_DOCKER_CALLS="${DOCKER_CALLS}" \
  TEST_AGENT_LOBEHUB_FORK_DIR="${FAKE_FORK}" \
  TEST_AGENT_DEV_LOG_DIR="${TEST_ROOT}/logs" \
  LOBEHUB_DEV_ENV_FILE="${TEST_ROOT}/pnpm-version.env" \
  bash "${HELPER}" restart 2>&1)"
pnpm_status=$?
set -e
[[ ${pnpm_status} -ne 0 && "${pnpm_output}" == *'requires pnpm 10.33.0, but Corepack resolved 9.0.0'* ]] || {
  echo 'LobeHub dev helper must reject a pnpm version that differs from packageManager.' >&2
  exit 1
}
[[ ! -e "${DOCKER_CALLS}" ]] || {
  echo 'LobeHub dev helper must validate pnpm before starting Docker dependencies.' >&2
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
