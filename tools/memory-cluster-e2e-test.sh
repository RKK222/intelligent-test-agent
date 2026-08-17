#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-memory-cluster.XXXXXX")"
trap 'rm -rf "${TMP_ROOT}"' EXIT

mkdir -p "${TMP_ROOT}/bin" "${TMP_ROOT}/state"
export MEMORY_CLUSTER_TEST_SCENARIOS="${TMP_ROOT}/scenarios"
export MEMORY_CLUSTER_TEST_HOOKS="${TMP_ROOT}/hooks"
export MEMORY_CLUSTER_TEST_CONCURRENCY="${TMP_ROOT}/concurrency"
export TEST_AGENT_MEMORY_E2E_STATE_FILE="${TMP_ROOT}/state/memory-e2e.json"

# 这个回归只验证编排顺序、状态传递和 hook 环境，不伪装真实浏览器结果；
# 真正的发布准入仍由同一脚本调用 Playwright/企业 hook 完成。
cat >"${TMP_ROOT}/bin/corepack" <<'SCRIPT'
#!/usr/bin/env bash
set -euo pipefail
scenario="${TEST_AGENT_MEMORY_E2E_SCENARIO:?}"
prefix="${TEST_AGENT_MEMORY_E2E_STATE_PREFIX:-}"
printf '%s\n' "${scenario}" >>"${MEMORY_CLUSTER_TEST_SCENARIOS}"

if [[ "${scenario}" == full ]]; then
  python3 - "${TEST_AGENT_MEMORY_E2E_STATE_FILE}" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
path.write_text(json.dumps({
    "memoryId": "mem_baseline",
    "sessionId": "ses_baseline",
    "runId": "run_baseline",
    "rawTranscriptMarker": "RAW_TRANSCRIPT_STATIC_GATE_1234",
    "applicationName": "Memory Static App",
    "workspaceAlias": "memory-static-workspace",
    "teamMemoryId": "mem_team",
    "governanceMemoryId": "mem_governance",
    "governancePlatformVersion": 4,
    "governanceLogicalVersion": 4,
}) + "\n", encoding="utf-8")
path.chmod(0o600)
PY
elif [[ "${scenario}" == learn-during-fault ]]; then
  [[ -n "${prefix}" ]] || { echo "fault learning requires state prefix" >&2; exit 1; }
  python3 - "${TEST_AGENT_MEMORY_E2E_STATE_FILE}" "${prefix}" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
prefix = sys.argv[2]
state = json.loads(path.read_text(encoding="utf-8"))
state.update({
    f"{prefix}MemoryId": f"mem_{prefix}",
    f"{prefix}SessionId": f"ses_{prefix}",
    f"{prefix}RunId": f"run_{prefix}",
})
path.write_text(json.dumps(state) + "\n", encoding="utf-8")
path.chmod(0o600)
PY
elif [[ "${scenario}" == concurrency ]]; then
  printf '%s:%s\n' \
    "${TEST_AGENT_MEMORY_E2E_PARTITION_MODE:?}" \
    "${TEST_AGENT_MEMORY_E2E_CONCURRENCY_ROUNDS:?}" >>"${MEMORY_CLUSTER_TEST_CONCURRENCY}"
fi
SCRIPT
chmod +x "${TMP_ROOT}/bin/corepack"
export PATH="${TMP_ROOT}/bin:${PATH}"

export TEST_AGENT_BASE_URL=http://backend.invalid
export TEST_AGENT_FRONTEND_URL=http://frontend.invalid
export TEST_AGENT_MEMORY_E2E_ADMIN_USERNAME=admin
export TEST_AGENT_MEMORY_E2E_ADMIN_PASSWORD=admin-password
export TEST_AGENT_MEMORY_E2E_MEMBER_USERNAME=member
export TEST_AGENT_MEMORY_E2E_MEMBER_PASSWORD=member-password
export TEST_AGENT_MEMORY_E2E_ADMIN_USER_QUERY=admin
export TEST_AGENT_MEMORY_E2E_MEMBER_USER_QUERY=member
export TEST_AGENT_MEMORY_E2E_REPOSITORY_NAME=repository
export TEST_AGENT_MEMORY_E2E_BRANCH=main
export TEST_AGENT_MEMORY_E2E_DIRECTORY=project
export TEST_AGENT_MEMORY_E2E_USERS_JSON='[{"username":"user-a","password":"password-a"},{"username":"user-b","password":"password-b"}]'

export TEST_AGENT_MEM0_NODE_1_STOP_CMD='printf "%s\n" mem0-1-stop >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_MEM0_NODE_1_START_CMD='printf "%s\n" mem0-1-start >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_MEM0_NODE_2_STOP_CMD='printf "%s\n" mem0-2-stop >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_MEM0_NODE_2_START_CMD='printf "%s\n" mem0-2-start >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_MEM0_NODE_3_STOP_CMD='printf "%s\n" mem0-3-stop >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_MEM0_NODE_3_START_CMD='printf "%s\n" mem0-3-start >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_MEM0_SCALE_OUT_CMD='printf "%s\n" mem0-scale-out >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_MEM0_SCALE_IN_CMD='printf "%s\n" mem0-scale-in >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_JAVA_NODE_1_STOP_CMD='printf "%s\n" java-1-stop >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_JAVA_NODE_1_START_CMD='printf "%s\n" java-1-start >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_JAVA_NODE_2_STOP_CMD='printf "%s\n" java-2-stop >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_JAVA_NODE_2_START_CMD='printf "%s\n" java-2-start >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_ENTERPRISE_EMBEDDING_STOP_CMD='printf "%s\n" enterprise-stop >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_ENTERPRISE_EMBEDDING_START_CMD='printf "%s\n" enterprise-start >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_CPU_EMBEDDING_STOP_CMD='printf "%s\n" cpu-stop >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_CPU_EMBEDDING_START_CMD='printf "%s\n" cpu-start >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_MEMORY_E2E_READY_CMD='printf "%s\n" ready >>"$MEMORY_CLUSTER_TEST_HOOKS"'
export TEST_AGENT_MEMORY_E2E_AUDIT_CMD='printf "audit:%s:%s:%s:%s:%s\n" "$TEST_AGENT_MEMORY_E2E_ENTERPRISE_FAULT_SESSION_ID" "$TEST_AGENT_MEMORY_E2E_ENTERPRISE_FAULT_MEMORY_ID" "$TEST_AGENT_MEMORY_E2E_GOVERNANCE_MEMORY_ID" "$TEST_AGENT_MEMORY_E2E_GOVERNANCE_PLATFORM_VERSION" "$TEST_AGENT_MEMORY_E2E_GOVERNANCE_LOGICAL_VERSION" >>"$MEMORY_CLUSTER_TEST_HOOKS"'

output="$(bash "${ROOT_DIR}/tools/memory-cluster-e2e.sh" \
  --enterprise --all --concurrency 2 --rounds 2 --partition both)"

cat >"${TMP_ROOT}/expected-scenarios" <<'EXPECTED'
full
recall
learn-during-fault
recall
learn-during-fault
recall
recall
learn-during-fault
projection-backlog
recall
projection-recovery
recall
recall
fail-open
recall
concurrency
concurrency
EXPECTED
diff -u "${TMP_ROOT}/expected-scenarios" "${MEMORY_CLUSTER_TEST_SCENARIOS}"

cat >"${TMP_ROOT}/expected-concurrency" <<'EXPECTED'
same:2
distinct:2
EXPECTED
diff -u "${TMP_ROOT}/expected-concurrency" "${MEMORY_CLUSTER_TEST_CONCURRENCY}"

grep -Fqx mem0-1-stop "${MEMORY_CLUSTER_TEST_HOOKS}"
grep -Fqx mem0-2-stop "${MEMORY_CLUSTER_TEST_HOOKS}"
grep -Fqx mem0-scale-out "${MEMORY_CLUSTER_TEST_HOOKS}"
grep -Fqx mem0-scale-in "${MEMORY_CLUSTER_TEST_HOOKS}"
grep -Fqx java-1-stop "${MEMORY_CLUSTER_TEST_HOOKS}"
grep -Fqx java-2-stop "${MEMORY_CLUSTER_TEST_HOOKS}"
grep -Fqx enterprise-stop "${MEMORY_CLUSTER_TEST_HOOKS}"
grep -Fqx cpu-stop "${MEMORY_CLUSTER_TEST_HOOKS}"
grep -Fqx 'audit:ses_enterpriseFault:mem_enterpriseFault:mem_governance:4:4' "${MEMORY_CLUSTER_TEST_HOOKS}"
[[ "${output}" == *"browser-full faults concurrency-same concurrency-distinct storage-audit"* ]]

if bash "${ROOT_DIR}/tools/memory-cluster-e2e.sh" --enterprise --audit --rounds 2 >/dev/null 2>&1; then
  echo "--rounds without --concurrency should fail" >&2
  exit 1
fi

if stat -f '%Lp' "${TEST_AGENT_MEMORY_E2E_STATE_FILE}" >/dev/null 2>&1; then
  [[ "$(stat -f '%Lp' "${TEST_AGENT_MEMORY_E2E_STATE_FILE}")" == 600 ]]
else
  [[ "$(stat -c '%a' "${TEST_AGENT_MEMORY_E2E_STATE_FILE}")" == 600 ]]
fi

echo "Memory cluster E2E orchestration checks passed."
