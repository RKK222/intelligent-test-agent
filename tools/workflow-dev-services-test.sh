#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
HELPER="${ROOT_DIR}/tools/workflow-dev-services.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

file_mode() {
  if stat -f '%Lp' "$1" >/dev/null 2>&1; then
    stat -f '%Lp' "$1"
  else
    stat -c '%a' "$1"
  fi
}

env_value() {
  local file="$1" key="$2"
  awk -F= -v key="${key}" '$1 == key {print substr($0, index($0, "=") + 1); exit}' "${file}"
}

[[ -x "${HELPER}" ]] || fail "workflow development helper missing or not executable: ${HELPER}"

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-workflow-dev.XXXXXX")"
api_pid=""
worker_pid=""
mismatched_pid=""
api_screen_session="ta-wf-api-$$"
worker_screen_session="ta-wf-worker-$$"
cleanup() {
  if [[ -x "${HELPER}" ]]; then
    TEST_AGENT_DEV_LOG_DIR="${tmp_dir}/logs" \
      TEST_AGENT_WORKFLOW_API_SCREEN_SESSION="${api_screen_session}" \
      TEST_AGENT_WORKFLOW_WORKER_SCREEN_SESSION="${worker_screen_session}" \
      bash "${HELPER}" stop >/dev/null 2>&1 || true
  fi
  for pid in "${api_pid}" "${worker_pid}" "${mismatched_pid}"; do
    if [[ -n "${pid}" ]]; then
      kill "${pid}" >/dev/null 2>&1 || true
    fi
  done
  rm -rf "${tmp_dir}"
}
trap cleanup EXIT

mkdir -p "${tmp_dir}/bin" "${tmp_dir}/captured" "${tmp_dir}/venv/bin"

cat >"${tmp_dir}/bin/openssl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
counter_file="${WORKFLOW_TEST_CAPTURE_DIR}/openssl.counter"
case "${1:-}" in
  rand)
    count=0
    [[ ! -f "${counter_file}" ]] || count="$(cat "${counter_file}")"
    count=$((count + 1))
    printf '%s\n' "${count}" >"${counter_file}"
    printf '%064d\n' "${count}"
    ;;
  genpkey)
    output=""
    while [[ $# -gt 0 ]]; do
      if [[ "$1" == "-out" ]]; then
        output="$2"
        break
      fi
      shift
    done
    [[ -n "${output}" ]]
    printf '%s\n' '-----BEGIN PRIVATE KEY-----' 'test-private-key' '-----END PRIVATE KEY-----' >"${output}"
    ;;
  pkey)
    output=""
    while [[ $# -gt 0 ]]; do
      if [[ "$1" == "-out" ]]; then
        output="$2"
        break
      fi
      shift
    done
    [[ -n "${output}" ]]
    printf '%s\n' '-----BEGIN PUBLIC KEY-----' 'test-public-key' '-----END PUBLIC KEY-----' >"${output}"
    ;;
  *)
    echo "unsupported openssl invocation: $*" >&2
    exit 2
    ;;
esac
EOF

cat >"${tmp_dir}/bin/psql" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${WORKFLOW_TEST_CAPTURE_DIR}/psql.argv"
if [[ " $* " == *" -U test_agent_workflow_owner "* \
  || " $* " == *" -U test_agent_workflow "* ]]; then
  [[ -f "${WORKFLOW_TEST_CAPTURE_DIR}/postgres.ready" ]] || exit 1
  printf '1\n'
  exit 0
fi
cat >>"${WORKFLOW_TEST_CAPTURE_DIR}/psql.stdin"
touch "${WORKFLOW_TEST_CAPTURE_DIR}/postgres.ready"
EOF

cat >"${tmp_dir}/venv/bin/uv" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${WORKFLOW_TEST_CAPTURE_DIR}/uv.argv"
EOF

cat >"${tmp_dir}/venv/bin/python-real" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${WORKFLOW_TEST_CAPTURE_DIR}/python.argv"
if [[ " $* " == *" testagent_workflow.cli api "* ]] || [[ " $* " == *" testagent_workflow.cli worker "* ]]; then
  trap 'exit 0' TERM INT
  while true; do
    /bin/sleep 1
  done
fi
if [[ "${1:-}" == "-" ]]; then
  cat >"${WORKFLOW_TEST_CAPTURE_DIR}/python.stdin"
fi
exit 0
EOF
ln -s python-real "${tmp_dir}/venv/bin/python"

cat >"${tmp_dir}/bin/curl" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

chmod +x "${tmp_dir}/bin/openssl" "${tmp_dir}/bin/psql" "${tmp_dir}/bin/curl" \
  "${tmp_dir}/venv/bin/uv" "${tmp_dir}/venv/bin/python-real"

common_env=(
  env
  "PATH=${tmp_dir}/bin:${PATH}"
  "WORKFLOW_TEST_CAPTURE_DIR=${tmp_dir}/captured"
  "TEST_AGENT_DEV_LOG_DIR=${tmp_dir}/logs"
  "TEST_AGENT_WORKFLOW_VENV=${tmp_dir}/venv"
  "TEST_AGENT_WORKFLOW_PYTHON=${tmp_dir}/venv/bin/python"
  "TEST_AGENT_WORKFLOW_API_SCREEN_SESSION=${api_screen_session}"
  "TEST_AGENT_WORKFLOW_WORKER_SCREEN_SESSION=${worker_screen_session}"
  "TEST_AGENT_BASE_URL=http://127.0.0.1:8080"
  "TEST_AGENT_TEST_DB_HOST=127.0.0.1"
  "TEST_AGENT_TEST_DB_PORT=5432"
  "TEST_AGENT_TEST_DB_NAME=test_agent"
  "TEST_AGENT_TEST_DB_USERNAME=test_agent"
  "TEST_AGENT_TEST_DB_PASSWORD=admin-secret"
  "TEST_AGENT_REDIS_HOST=127.0.0.1"
  "TEST_AGENT_REDIS_PORT=6379"
  "TEST_AGENT_REDIS_PASSWORD="
)

"${common_env[@]}" bash "${HELPER}" prepare >/dev/null

state_dir="${tmp_dir}/logs/workflow"
platform_env="${state_dir}/workflow-dev.env"
runtime_env="${state_dir}/workflow-runtime.env"
secrets_env="${state_dir}/workflow-secrets.env"

[[ "$(file_mode "${state_dir}")" == "700" ]] || fail "workflow state directory must use mode 0700"
for sensitive_file in "${platform_env}" "${runtime_env}" "${secrets_env}" \
  "${state_dir}/runner-private.pem" "${state_dir}/runner-public.pem"; do
  [[ -f "${sensitive_file}" ]] || fail "missing generated file: ${sensitive_file}"
  [[ "$(file_mode "${sensitive_file}")" == "600" ]] || fail "sensitive file must use mode 0600: ${sensitive_file}"
done

grep -Fq 'TEST_AGENT_WORKFLOW_DATABASE_URL=postgresql+asyncpg://' "${runtime_env}" \
  || fail "runtime env must contain the workflow database URL"
grep -Fq 'TEST_AGENT_WORKFLOW_REDIS_URL=redis://workflow-dev:' "${runtime_env}" \
  || fail "runtime env must contain the restricted Redis URL"
grep -Fq 'TEST_AGENT_WORKFLOW_SERVER_MODEL_GATEWAY_URL=http://127.0.0.1:8080/api/internal/platform/model-gateway/v1' "${runtime_env}" \
  || fail "runtime env must keep host-side AgentScope on the local model gateway route"
if grep -Eq 'DATABASE_URL|REDIS_URL' "${platform_env}"; then
  fail "platform env must not expose workflow database or Redis credentials to Java"
fi
if grep -Rq 'admin-secret' "${tmp_dir}/captured"/*.argv; then
  fail "platform administrator password leaked into process arguments"
fi

platform_hmac="$(env_value "${runtime_env}" TEST_AGENT_WORKFLOW_PLATFORM_HMAC_SECRET)"
worker_hmac="$(env_value "${runtime_env}" TEST_AGENT_WORKFLOW_RUNNER_HMAC_SECRET)"
runner_platform_hmac="$(env_value "${platform_env}" TEST_AGENT_WORKFLOW_RUNNER_PLATFORM_HMAC_SECRET)"
[[ ${#platform_hmac} -ge 64 && ${#worker_hmac} -ge 64 && ${#runner_platform_hmac} -ge 64 ]] \
  || fail "generated HMAC values must contain at least 32 random bytes"
[[ "${platform_hmac}" != "${worker_hmac}" && "${platform_hmac}" != "${runner_platform_hmac}" \
  && "${worker_hmac}" != "${runner_platform_hmac}" ]] || fail "the three HMAC values must be distinct"

first_checksum="$(shasum -a 256 "${platform_env}" "${runtime_env}" "${secrets_env}")"
"${common_env[@]}" bash "${HELPER}" prepare >/dev/null
second_checksum="$(shasum -a 256 "${platform_env}" "${runtime_env}" "${secrets_env}")"
[[ "${first_checksum}" == "${second_checksum}" ]] || fail "repeated prepare must reuse stable local secrets"
admin_bootstrap_count="$(grep -Fc -- '-U test_agent -d test_agent' "${tmp_dir}/captured/psql.argv" || true)"
[[ "${admin_bootstrap_count}" == "1" ]] \
  || fail "repeated prepare must reuse initialized workflow roles without administrator bootstrap"

set +e
partial_runner_output="$({
  "${common_env[@]}" TEST_AGENT_WORKFLOW_RUNNER_BASE_URL=http://runner.example:8091 \
    bash "${HELPER}" prepare
} 2>&1)"
partial_runner_status=$?
set -e
[[ ${partial_runner_status} -ne 0 ]] || fail "partial external Runner configuration must be rejected"
[[ "${partial_runner_output}" == *"外部Runner配置必须成组提供"* ]] \
  || fail "partial Runner failure must explain the complete configuration requirement"

set +e
remote_redis_output="$({
  "${common_env[@]}" TEST_AGENT_REDIS_HOST=10.20.30.40 TEST_AGENT_REDIS_PASSWORD= \
    bash "${HELPER}" prepare
} 2>&1)"
remote_redis_status=$?
set -e
[[ ${remote_redis_status} -ne 0 ]] || fail "remote passwordless Redis bootstrap must be rejected"
[[ "${remote_redis_output}" == *"非回环Redis禁止无密码管理员连接"* ]] \
  || fail "remote passwordless Redis failure must be explicit"

"${common_env[@]}" bash "${HELPER}" start >/dev/null
api_pid="$(cat "${state_dir}/workflow-api.pid")"
worker_pid="$(cat "${state_dir}/workflow-worker.pid")"
/bin/sleep 1
kill -0 "${api_pid}" >/dev/null 2>&1 || fail "workflow API process did not stay running"
kill -0 "${worker_pid}" >/dev/null 2>&1 || fail "workflow Worker process did not stay running"
if command -v screen >/dev/null 2>&1; then
  screen_list="$(screen -list 2>/dev/null || true)"
  grep -F ".${api_screen_session}" >/dev/null <<<"${screen_list}" \
    || fail "workflow API screen session did not stay running"
  grep -F ".${worker_screen_session}" >/dev/null <<<"${screen_list}" \
    || fail "workflow Worker screen session did not stay running"
fi

status_output="$("${common_env[@]}" bash "${HELPER}" status)"
[[ "${status_output}" == *"workflow-api: RUNNING"* && "${status_output}" == *"workflow-worker: RUNNING"* ]] \
  || fail "status must report both workflow processes"
if [[ "${status_output}" == *"postgresql+"* || "${status_output}" == *"redis://"* \
  || "${status_output}" == *"${platform_hmac}"* ]]; then
  fail "status output must not expose runtime credentials"
fi

"${common_env[@]}" bash "${HELPER}" stop >/dev/null
"${common_env[@]}" bash "${HELPER}" stop >/dev/null
if kill -0 "${api_pid}" >/dev/null 2>&1 || kill -0 "${worker_pid}" >/dev/null 2>&1; then
  fail "stop must terminate both managed processes"
fi
if command -v screen >/dev/null 2>&1; then
  screen_list="$(screen -list 2>/dev/null || true)"
  if grep -E "\.(${api_screen_session}|${worker_screen_session})[[:space:]]" \
    >/dev/null <<<"${screen_list}"; then
    fail "stop must terminate both workflow screen sessions"
  fi
fi
api_pid=""
worker_pid=""

/bin/sleep 60 &
mismatched_pid=$!
printf '%s\n' "${mismatched_pid}" >"${state_dir}/workflow-api.pid"
set +e
"${common_env[@]}" bash "${HELPER}" stop >/dev/null 2>&1
mismatch_status=$?
set -e
[[ ${mismatch_status} -ne 0 ]] || fail "PID command mismatch must make stop fail closed"
kill -0 "${mismatched_pid}" >/dev/null 2>&1 || fail "PID mismatch must not terminate an unrelated process"
kill "${mismatched_pid}" >/dev/null 2>&1 || true
wait "${mismatched_pid}" 2>/dev/null || true
mismatched_pid=""

echo "Workflow development service verification passed."
