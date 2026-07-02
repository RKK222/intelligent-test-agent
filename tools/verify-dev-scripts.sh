#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

run_check() {
  local label="$1"
  shift

  echo "Checking ${label}: $*"
  "$@" || fail "${label} failed"
}

# 开发入口脚本需要兼容误用 sh 启动时的解析路径，避免服务构建完成后才在 Bash 专属语法处失败。
run_check "restart script bash syntax" bash -n "${ROOT_DIR}/restart-dev-services.sh"
run_check "restart script sh parse guard" sh -n "${ROOT_DIR}/restart-dev-services.sh"
run_check "restart script sh help entry" sh "${ROOT_DIR}/restart-dev-services.sh" --help
WINDOWS_RESTART_SCRIPT="${ROOT_DIR}/win-restart-dev-services-fixed-v4.ps1"
[[ -f "${WINDOWS_RESTART_SCRIPT}" ]] || fail "windows restart script missing: ${WINDOWS_RESTART_SCRIPT}"
powershell_bin=""
for candidate in pwsh powershell.exe powershell; do
  if command -v "${candidate}" >/dev/null 2>&1; then
    powershell_bin="${candidate}"
    break
  fi
done
if [[ -n "${powershell_bin}" ]]; then
  run_check "windows restart script PowerShell parse" env WINDOWS_RESTART_SCRIPT="${WINDOWS_RESTART_SCRIPT}" "${powershell_bin}" -NoProfile -Command '$parseErrors = @(); [void][System.Management.Automation.PSParser]::Tokenize((Get-Content -Raw -LiteralPath $env:WINDOWS_RESTART_SCRIPT), [ref]$parseErrors); if ($parseErrors.Count -gt 0) { $parseErrors | Format-List | Out-String | Write-Error; exit 1 }'
else
  echo "Skipping windows restart script PowerShell parse: pwsh/powershell not found."
fi
run_check "dev backend script bash syntax" bash -n "${ROOT_DIR}/tools/dev-backend-run.sh"

restart_help="$(sh "${ROOT_DIR}/restart-dev-services.sh" --help)"
if [[ "${restart_help}" != *"backend profile: test"* ]]; then
  echo "${restart_help}" >&2
  fail "restart script help should document test as the default profile"
fi
if [[ "${restart_help}" != *"backend env:     .env.test"* ]]; then
  echo "${restart_help}" >&2
  fail "restart script help should document .env.test as the default dotenv file"
fi
if ! grep -Fq 'export TEST_AGENT_ROOT="${TEST_AGENT_ROOT:-${ROOT_DIR}}"' "${ROOT_DIR}/restart-dev-services.sh"; then
  fail "restart script should default TEST_AGENT_ROOT without overriding an explicit value"
fi
if grep -Fq '"/tmp/test-agent/fcoss/' "${ROOT_DIR}/restart-dev-services.sh"; then
  fail "restart script should not recreate legacy /tmp/test-agent demo workspaces"
fi
if ! grep -Fq '"${TEST_AGENT_ROOT}/temp/fcoss/' "${ROOT_DIR}/restart-dev-services.sh"; then
  fail "restart script should create demo workspaces under the project temp directory"
fi
if grep -Eq '\benglish_name\b' "${ROOT_DIR}/tools/cleanup-old-path-data.sql"; then
  fail "cleanup SQL must use the real common_parameters.parameter_english column"
fi
if ! grep -Fq "parameter_english" "${ROOT_DIR}/tools/cleanup-old-path-data.sql"; then
  fail "cleanup SQL should inspect common_parameters.parameter_english"
fi
run_check "opencode process deployment smoke script bash syntax" bash -n "${ROOT_DIR}/tools/verify-opencode-process-deployment.sh"
run_check "opencode process deployment smoke script help" bash "${ROOT_DIR}/tools/verify-opencode-process-deployment.sh" --help
run_check "opencode user process scenario script bash syntax" bash -n "${ROOT_DIR}/tools/verify-opencode-user-process-scenarios.sh"

# 用 xtrace 验证误用 sh 执行时确实重进 Bash，而不是继续留在 sh/POSIX 模式。
restart_trace="$(sh -x "${ROOT_DIR}/restart-dev-services.sh" --help 2>&1 >/dev/null || true)"
if [[ "${restart_trace}" != *"exec /usr/bin/env bash"* ]]; then
  fail "restart script did not re-exec bash from sh entry"
fi

# 用临时命令隔离进程发现逻辑，执行到后端启动前置校验，证明误用 sh 时不会再卡在 PID 采集语法处。
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-dev-scripts.XXXXXX")"
cleanup() {
  rm -rf "${tmp_dir}"
}
trap cleanup EXIT

mkdir -p "${tmp_dir}/bin" "${tmp_dir}/logs"
screen_calls="${tmp_dir}/screen.calls"
printf '#!/usr/bin/env bash\nexit 0\n' >"${tmp_dir}/bin/ps"
printf '#!/usr/bin/env bash\nif [[ "${1:-}" == "-list" ]]; then exit 1; fi\nprintf "%%s\\n" "$*" >>%q\nexit 0\n' "${screen_calls}" >"${tmp_dir}/bin/screen"
printf '#!/usr/bin/env bash\nexit 0\n' >"${tmp_dir}/bin/curl"
printf '#!/usr/bin/env bash\necho "   interface: en0"\n' >"${tmp_dir}/bin/route"
printf '#!/usr/bin/env bash\nif [[ "${1:-}" == "getifaddr" && "${2:-}" == "en0" ]]; then echo "10.8.0.115"; exit 0; fi\nexit 1\n' >"${tmp_dir}/bin/ipconfig"
printf '#!/usr/bin/env bash\necho "go should not run for remote opencode base URL" >&2\nexit 99\n' >"${tmp_dir}/bin/go"
chmod +x "${tmp_dir}/bin/ps" "${tmp_dir}/bin/screen" "${tmp_dir}/bin/curl" "${tmp_dir}/bin/route" "${tmp_dir}/bin/ipconfig" "${tmp_dir}/bin/go"
printf 'PLACEHOLDER=1\n' >"${tmp_dir}/env.local"

set +e
restart_output="$(
  PATH="${tmp_dir}/bin:${PATH}" sh "${ROOT_DIR}/restart-dev-services.sh" \
    --skip-backend-build \
    --skip-frontend-build \
    --env-file "${tmp_dir}/env.local" \
    --log-dir "${tmp_dir}/logs" 2>&1
)"
restart_status=$?
set -e

if [[ "${restart_status}" -eq 0 ]]; then
  fail "restart script isolated execution unexpectedly succeeded"
fi
if [[ "${restart_output}" == *"syntax error near unexpected token"* ]]; then
  echo "${restart_output}" >&2
  fail "restart script still fails with shell syntax error"
fi
if [[ "${restart_output}" != *"TEST_AGENT_OPENCODE_BASE_URL is required"* ]]; then
  echo "${restart_output}" >&2
  fail "restart script did not reach backend startup precondition"
fi
if [[ "${restart_output}" != *"Defaulting TEST_AGENT_BASE_URL to detected local IPv4 for browser access: http://10.8.0.115:8080"* ]]; then
  echo "${restart_output}" >&2
  fail "restart script did not default TEST_AGENT_BASE_URL to detected local IPv4"
fi

# auto 模式必须把默认路由网卡的 IPv4 识别为本机地址；.env.test 使用局域网 IP 时也要启动 manager。
printf '#!/usr/bin/env bash\nprintf "%%s\\n" "$*" >>%q\nexit 0\n' "${tmp_dir}/go.calls" >"${tmp_dir}/bin/go"
chmod +x "${tmp_dir}/bin/go"
printf 'TEST_AGENT_BASE_URL=http://10.8.0.115:8080\nTEST_AGENT_FRONTEND_URL=http://10.8.0.115:3000\nTEST_AGENT_OPENCODE_BASE_URL=http://10.8.0.115:4096\n' >"${tmp_dir}/env-local-ip.local"
set +e
restart_local_ip_output="$(
  PATH="${tmp_dir}/bin:${PATH}" sh "${ROOT_DIR}/restart-dev-services.sh" \
    --skip-backend-build \
    --skip-frontend-build \
    --env-file "${tmp_dir}/env-local-ip.local" \
    --log-dir "${tmp_dir}/logs" 2>&1
)"
restart_local_ip_status=$?
set -e
if [[ "${restart_local_ip_status}" -eq 0 ]]; then
  fail "restart script isolated local-IP execution unexpectedly succeeded"
fi
if [[ "${restart_local_ip_output}" != *"Starting opencode-manager"* ]]; then
  echo "${restart_local_ip_output}" >&2
  fail "restart script should auto-start opencode-manager for the detected local IPv4"
fi

if grep -q "OPENCODE_MANAGER_LINUX_SERVER_ID" "${ROOT_DIR}/restart-dev-services.sh"; then
  fail "restart script should not inject OPENCODE_MANAGER_LINUX_SERVER_ID"
fi
if grep -q "TEST_AGENT_SERVER_IP_FILE" "${ROOT_DIR}/restart-dev-services.sh"; then
  fail "restart script should not inject server IP file path; Java uses SYS_DATA_ROOT_DIR/.serverip"
fi
if grep -q "OPENCODE_MANAGER_SERVER_IP_FILE" "${ROOT_DIR}/restart-dev-services.sh"; then
  fail "restart script should not inject manager server IP file path; manager derives SYS_DATA_ROOT_DIR/.serverip"
fi
if grep -q "OPENCODE_MANAGER_ID" "${ROOT_DIR}/restart-dev-services.sh"; then
  fail "restart script should not inject OPENCODE_MANAGER_ID; manager derives it from container and process name"
fi
if grep -q "OPENCODE_MANAGER_BACKEND_DISCOVERY_URL" "${ROOT_DIR}/restart-dev-services.sh"; then
  fail "restart script should not inject legacy HTTP discovery URL"
fi
if grep -q "OPENCODE_MANAGER_MAX_PROCESSES" "${ROOT_DIR}/restart-dev-services.sh"; then
  fail "restart script should not inject manager max processes; run mode reads common_parameters via WebSocket"
fi
if grep -q "OPENCODE_SESSION_ROOT" "${ROOT_DIR}/restart-dev-services.sh"; then
  fail "restart script should not inject manager session root; run mode reads OPENCODE_SESSION_DIR from common_parameters"
fi
if grep -q "OPENCODE_CONFIG_DIR" "${ROOT_DIR}/restart-dev-services.sh"; then
  fail "restart script should not inject manager config dir; run mode reads OPENCODE_PUBLIC_CONFIG_DIR from common_parameters"
fi

printf 'TEST_AGENT_BASE_URL=http://10.8.0.115:8080\nTEST_AGENT_FRONTEND_URL=http://10.8.0.115:3000\nTEST_AGENT_OPENCODE_BASE_URL=http://10.8.0.115:4096\nTEST_AGENT_START_OPENCODE_MANAGER=false\n' >"${tmp_dir}/env-frontend-host.local"
restart_frontend_output="$(
  PATH="${tmp_dir}/bin:${PATH}" sh "${ROOT_DIR}/restart-dev-services.sh" \
    --skip-backend-build \
    --skip-frontend-build \
    --env-file "${tmp_dir}/env-frontend-host.local" \
    --log-dir "${tmp_dir}/logs" 2>&1
)"
if [[ "${restart_frontend_output}" != *"Starting frontend on 0.0.0.0:3000"* ]]; then
  echo "${restart_frontend_output}" >&2
  fail "restart script should bind frontend to 0.0.0.0 for non-loopback access URLs"
fi

printf 'TEST_AGENT_OPENCODE_BASE_URL=http://10.8.0.116:4096\n' >"${tmp_dir}/env-remote-opencode.local"
set +e
restart_remote_opencode_output="$(
  PATH="${tmp_dir}/bin:${PATH}" sh "${ROOT_DIR}/restart-dev-services.sh" \
    --skip-backend-build \
    --skip-frontend-build \
    --env-file "${tmp_dir}/env-remote-opencode.local" \
    --log-dir "${tmp_dir}/logs" 2>&1
)"
restart_remote_opencode_status=$?
set -e
if [[ "${restart_remote_opencode_status}" -ne 0 ]]; then
  echo "${restart_remote_opencode_output}" >&2
  fail "restart script should skip manager build/start for remote opencode base URL"
fi
if [[ "${restart_remote_opencode_output}" == *"go should not run"* ]]; then
  echo "${restart_remote_opencode_output}" >&2
  fail "restart script should not build opencode-manager for remote opencode base URL"
fi
if [[ "${restart_remote_opencode_output}" != *"Skipping opencode-manager startup."* ]]; then
  echo "${restart_remote_opencode_output}" >&2
  fail "restart script should report skipped opencode-manager startup for remote opencode base URL"
fi
if [[ "$(cat "${screen_calls}" 2>/dev/null || true)" != *"-Djava.net.useSystemProxies=false"* ]]; then
  cat "${screen_calls}" >&2 || true
  fail "restart script backend launch should disable JVM system proxies"
fi
if [[ "$(cat "${screen_calls}" 2>/dev/null || true)" != *"-DsocksProxyHost="* ]]; then
  cat "${screen_calls}" >&2 || true
  fail "restart script backend launch should clear JVM SOCKS proxy host"
fi
if [[ "$(sed -n '1,220p' "${ROOT_DIR}/tools/dev-backend-run.sh")" != *"-Djava.net.useSystemProxies=false"* ]]; then
  fail "dev backend script should disable JVM system proxies"
fi
if [[ "$(sed -n '1,220p' "${ROOT_DIR}/tools/dev-backend-run.sh")" != *"-DsocksProxyHost="* ]]; then
  fail "dev backend script should clear JVM SOCKS proxy host"
fi

echo "Development script verification passed."
