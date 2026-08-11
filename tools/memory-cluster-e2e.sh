#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
FRONTEND_DIR="${ROOT_DIR}/frontend"
COMPOSE_FILE="${ROOT_DIR}/deploy/dev/memory-compose.yml"
DEV_ENV_FILE="${TEST_AGENT_MEMORY_DEV_ENV_FILE:-${ROOT_DIR}/.tmp/dev-services/memory/memory-dev.env}"
STATE_FILE="${TEST_AGENT_MEMORY_E2E_STATE_FILE:-${ROOT_DIR}/.tmp/memory-e2e-state.json}"
export TEST_AGENT_MEMORY_E2E_STATE_FILE="${STATE_FILE}"

RUN_FULL=false
RUN_FAULTS=false
RUN_AUDIT=false
ENTERPRISE=false
CONCURRENCY=0
CONCURRENCY_ROUNDS=1
PARTITION_MODE=both
PASSED_GATES=()
RESTORE_REQUIRED=false
SCALE_OUT_ACTIVE=false

usage() {
  cat <<'USAGE'
Usage: tools/memory-cluster-e2e.sh [options]

Run the browser-only generic-memory release gate against real services. The
script never substitutes Python API/database calls for business acceptance.
Database, filesystem and log checks are a separate post-acceptance audit.

Options:
  --full                 Create an Application in the UI and run learning,
                         cross-session recall, team review, transcript ACL and
                         update/promote/pause/archive projection governance.
  --faults               Stop Mem0/embedding/Java nodes; run browser learning
                         as well as recall in the degraded windows.
  --concurrency N        Run N browser sessions and enforce run-start p99 <=2s;
                         the measurement includes routing and retrieval.
  --rounds N             Repeat the synchronized concurrency batch N times;
                         default 1, maximum 20.
  --partition MODE       same, distinct or both; default both.
  --audit                Check shared DB version/outbox integrity and absence of
                         raw transcript storage outside the platform Session.
  --enterprise           Use operator-supplied remote hooks instead of local
                         Docker Compose controls; requires all release hooks.
  --all                  Equivalent to --full --faults --audit. Add
                         --concurrency N to include the capacity gate.
  -h, --help             Show this help.

Browser-flow required environment (`--full`, `--faults` or `--concurrency`):
  TEST_AGENT_BASE_URL, TEST_AGENT_FRONTEND_URL
  TEST_AGENT_MEMORY_E2E_ADMIN_USERNAME/PASSWORD
  TEST_AGENT_MEMORY_E2E_EXISTING_APPLICATION_NAME
  TEST_AGENT_MEMORY_E2E_EXISTING_WORKSPACE_ALIAS
  TEST_AGENT_MEMORY_E2E_STATE_FILE              Browser-observed baseline ID state.
  TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_ID      Alternative baseline for standalone fault runs.

Full-flow additions:
  TEST_AGENT_MEMORY_E2E_MEMBER_USERNAME/PASSWORD
  TEST_AGENT_MEMORY_E2E_ADMIN_USER_QUERY
  TEST_AGENT_MEMORY_E2E_MEMBER_USER_QUERY
  TEST_AGENT_MEMORY_E2E_REPOSITORY_NAME
  TEST_AGENT_MEMORY_E2E_BRANCH
  TEST_AGENT_MEMORY_E2E_DIRECTORY
  TEST_AGENT_MEMORY_E2E_ISOLATION_APPLICATION_NAME/WORKSPACE_ALIAS
                                               Optional pre-created isolation target;
                                               otherwise full creates a second Application in the UI.

Distinct-partition concurrency:
  TEST_AGENT_MEMORY_E2E_USERS_JSON='[{"username":"...","password":"...","directoryQuery":"...","applicationName":"...","workspaceAlias":"...","expectedMemoryId":"..."},...]'
                                               At least N unique username/Application actors for concurrency N.
                                               Actors without applicationName join the full-flow Application;
                                               actors using another Application require expectedMemoryId.
  TEST_AGENT_MEMORY_E2E_CONCURRENCY_LEARNING_SAMPLES  Browser evidence samples; default 4, max 8.
  TEST_AGENT_MEMORY_E2E_CONCURRENCY_ROUNDS     Exported from --rounds.

Operator-supplied hooks (commands are intentionally supplied by the release
operator and are not stored in the repository; stop/start hooks only apply to
enterprise mode, while the platform audit hook is required in both modes):
  TEST_AGENT_MEM0_NODE_{1,2}_{STOP,START}_CMD
  TEST_AGENT_MEM0_SCALE_{OUT,IN}_CMD            Add/remove a genuinely new stateless replica.
  TEST_AGENT_JAVA_NODE_{1,2}_{STOP,START}_CMD
  TEST_AGENT_ENTERPRISE_EMBEDDING_{STOP,START}_CMD
  TEST_AGENT_CPU_EMBEDDING_{STOP,START}_CMD
  TEST_AGENT_MEMORY_E2E_READY_CMD
  TEST_AGENT_MEMORY_E2E_AUDIT_CMD              Required platform DB/Session/log audit;
                                               enterprise mode must also verify one native operation
                                               for the exported fault Session and both projections.
USAGE
}

die() {
  echo "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

require_env() {
  local key="$1"
  [[ -n "${!key:-}" ]] || die "Missing required environment: ${key}"
}

require_positive_integer() {
  local name="$1" value="$2" max="$3"
  [[ "${value}" =~ ^[1-9][0-9]*$ && "${value}" -le "${max}" ]] \
    || die "${name} must be an integer between 1 and ${max}"
}

compose() {
  docker compose --env-file "${DEV_ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

run_hook() {
  local label="$1" variable="$2" command
  command="${!variable:-}"
  [[ -n "${command}" ]] || die "Missing enterprise hook: ${variable}"
  echo "Running operator hook: ${label}"
  # 发布 hook 只继承调用方显式环境，不加载个人登录 profile，避免非交互发布被
  # ~/.bash_profile 的本机命令污染或意外失败。
  bash -c "${command}"
}

optional_hook() {
  local label="$1" variable="$2" command
  command="${!variable:-}"
  [[ -n "${command}" ]] || return 0
  echo "Running operator hook: ${label}"
  bash -c "${command}"
}

run_browser_scenario() {
  local selected_scenario="$1"
  echo "Running real browser memory scenario: ${selected_scenario}"
  (
    cd "${FRONTEND_DIR}"
    TEST_AGENT_RUN_MEMORY_E2E=1 \
      TEST_AGENT_MEMORY_E2E_SCENARIO="${selected_scenario}" \
      TEST_AGENT_MEMORY_E2E_STATE_FILE="${STATE_FILE}" \
      corepack pnpm exec playwright test \
        --config playwright.real.config.ts \
        apps/agent-web/tests/memory.real-spec.ts \
        --project chromium --workers 1
  )
}

ready() {
  if [[ "${ENTERPRISE}" == true ]]; then
    run_hook "cluster readiness" TEST_AGENT_MEMORY_E2E_READY_CMD
  else
    "${ROOT_DIR}/tools/memory-dev-services.sh" status >/dev/null
  fi
}

restore_services() {
  [[ "${RESTORE_REQUIRED}" == true ]] || return 0
  set +e
  echo "Restoring all services touched by the fault gate."
  if [[ "${ENTERPRISE}" == true ]]; then
    if [[ "${SCALE_OUT_ACTIVE}" == true ]]; then
      optional_hook "Mem0 scale in" TEST_AGENT_MEM0_SCALE_IN_CMD
      SCALE_OUT_ACTIVE=false
    fi
    optional_hook "Mem0 node 1 start" TEST_AGENT_MEM0_NODE_1_START_CMD
    optional_hook "Mem0 node 2 start" TEST_AGENT_MEM0_NODE_2_START_CMD
    optional_hook "Mem0 node 3 start" TEST_AGENT_MEM0_NODE_3_START_CMD
    optional_hook "Java node 1 start" TEST_AGENT_JAVA_NODE_1_START_CMD
    optional_hook "Java node 2 start" TEST_AGENT_JAVA_NODE_2_START_CMD
    optional_hook "enterprise embedding start" TEST_AGENT_ENTERPRISE_EMBEDDING_START_CMD
    optional_hook "CPU embedding start" TEST_AGENT_CPU_EMBEDDING_START_CMD
    optional_hook "cluster readiness" TEST_AGENT_MEMORY_E2E_READY_CMD
  elif [[ -f "${DEV_ENV_FILE}" ]]; then
    compose start memory-service-1 memory-service-2 memory-service-3 embedding-service >/dev/null
    "${ROOT_DIR}/tools/memory-dev-services.sh" status >/dev/null
  fi
  set -e
}

trap restore_services EXIT INT TERM

stop_mem0_node() {
  local index="$1"
  RESTORE_REQUIRED=true
  if [[ "${ENTERPRISE}" == true ]]; then
    run_hook "Mem0 node ${index} stop" "TEST_AGENT_MEM0_NODE_${index}_STOP_CMD"
  else
    compose stop --timeout 60 "memory-service-${index}" >/dev/null
  fi
}

start_mem0_node() {
  local index="$1"
  if [[ "${ENTERPRISE}" == true ]]; then
    run_hook "Mem0 node ${index} start" "TEST_AGENT_MEM0_NODE_${index}_START_CMD"
  else
    compose start "memory-service-${index}" >/dev/null
  fi
}

stop_cpu_embedding() {
  RESTORE_REQUIRED=true
  if [[ "${ENTERPRISE}" == true ]]; then
    run_hook "CPU embedding stop" TEST_AGENT_CPU_EMBEDDING_STOP_CMD
  else
    compose stop --timeout 60 embedding-service >/dev/null
  fi
}

start_cpu_embedding() {
  if [[ "${ENTERPRISE}" == true ]]; then
    run_hook "CPU embedding start" TEST_AGENT_CPU_EMBEDDING_START_CMD
  else
    compose start embedding-service >/dev/null
  fi
}

run_fault_gate() {
  ready

  # 依次摘除两个副本；只剩一个副本时继续执行真实原生学习和随后的跨会话召回，
  # 证明 history、幂等与逻辑 ID 都来自共享 PostgreSQL，而非节点本地状态。
  stop_mem0_node 1
  run_browser_scenario recall
  stop_mem0_node 2
  TEST_AGENT_MEMORY_E2E_STATE_PREFIX=replicaFault \
    run_browser_scenario learn-during-fault
  start_mem0_node 1
  start_mem0_node 2
  ready
  TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_STATE_KEY=replicaFaultMemoryId \
    run_browser_scenario recall

  if [[ "${ENTERPRISE}" == true ]]; then
    # 两个 Java 节点逐台摘除；第一台停止期间继续学习，第二台停止期间召回
    # 同一平台记忆，浏览器始终经 Nginx 访问，不绕过负载均衡。
    run_hook "Java node 1 stop" TEST_AGENT_JAVA_NODE_1_STOP_CMD
    TEST_AGENT_MEMORY_E2E_STATE_PREFIX=javaFault \
      run_browser_scenario learn-during-fault
    run_hook "Java node 1 start" TEST_AGENT_JAVA_NODE_1_START_CMD
    ready
    TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_STATE_KEY=javaFaultMemoryId \
      run_browser_scenario recall
    run_hook "Java node 2 stop" TEST_AGENT_JAVA_NODE_2_STOP_CMD
    TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_STATE_KEY=javaFaultMemoryId \
      run_browser_scenario recall
    run_hook "Java node 2 start" TEST_AGENT_JAVA_NODE_2_START_CMD
    ready

    # 企业 profile 中断时在 CPU profile 上执行唯一一次原生抽取，再经浏览器编辑
    # 强制产生新逻辑版本；企业投影必须进入共享 outbox，CPU 集合仍能召回。
    run_hook "enterprise embedding stop" TEST_AGENT_ENTERPRISE_EMBEDDING_STOP_CMD
    TEST_AGENT_MEMORY_E2E_STATE_PREFIX=enterpriseFault \
      TEST_AGENT_MEMORY_E2E_FORCE_PROJECTION_MUTATION=1 \
      run_browser_scenario learn-during-fault
    TEST_AGENT_MEMORY_E2E_EXPECT_PROFILE_COUNT=2 \
      run_browser_scenario projection-backlog
    TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_STATE_KEY=enterpriseFaultMemoryId \
      run_browser_scenario recall
    run_hook "enterprise embedding start" TEST_AGENT_ENTERPRISE_EMBEDDING_START_CMD
    ready
    TEST_AGENT_MEMORY_E2E_EXPECT_PROFILE_COUNT=2 \
      run_browser_scenario projection-recovery
    TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_STATE_KEY=enterpriseFaultMemoryId \
      run_browser_scenario recall

    # CPU 中断时企业集合继续召回。
    stop_cpu_embedding
    TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_STATE_KEY=enterpriseFaultMemoryId \
      run_browser_scenario recall
    start_cpu_embedding
    ready

    # 两个 profile 同时不可用时仍从浏览器验证 Run fail-open。
    run_hook "enterprise embedding stop" TEST_AGENT_ENTERPRISE_EMBEDDING_STOP_CMD
    stop_cpu_embedding
    run_browser_scenario fail-open
    run_hook "enterprise embedding start" TEST_AGENT_ENTERPRISE_EMBEDDING_START_CMD
    start_cpu_embedding
    ready
  else
    # 无企业 Embedding 的环境只有 CPU profile；停止它即覆盖双 profile 全不可用场景。
    stop_cpu_embedding
    run_browser_scenario fail-open
    start_cpu_embedding
    ready
  fi

  if [[ "${ENTERPRISE}" == true ]]; then
    # 通过现场编排真正增加一个新副本，再删除该副本；其余节点全程服务且不迁移本地数据。
    RESTORE_REQUIRED=true
    run_hook "Mem0 scale out" TEST_AGENT_MEM0_SCALE_OUT_CMD
    SCALE_OUT_ACTIVE=true
    ready
    TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_STATE_KEY=replicaFaultMemoryId \
      run_browser_scenario recall
    run_hook "Mem0 scale in" TEST_AGENT_MEM0_SCALE_IN_CMD
    SCALE_OUT_ACTIVE=false
    ready
  else
    # 本地固定三副本编排只能证明无状态重建；真实扩容由企业 scale hook 验收。
    stop_mem0_node 3
    start_mem0_node 3
    ready
    TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_STATE_KEY=replicaFaultMemoryId \
      run_browser_scenario recall
  fi
}

query_local_memory_db() {
  local sql="$1"
  compose exec -T memory-postgres psql -v ON_ERROR_STOP=1 -At \
    -U testagent_memory -d testagent_memory -c "${sql}"
}

assert_zero_query() {
  local label="$1" sql="$2" result
  result="$(query_local_memory_db "${sql}")"
  [[ "${result}" == 0 ]] || die "Audit failed (${label}): ${result}"
  echo "OK audit: ${label}."
}

assert_query_equals() {
  local label="$1" expected="$2" sql="$3" result
  result="$(query_local_memory_db "${sql}")"
  [[ "${result}" == "${expected}" ]] \
    || die "Audit failed (${label}): expected ${expected}, got ${result}"
  echo "OK audit: ${label}."
}

browser_state_value() {
  local key="$1"
  [[ -f "${STATE_FILE}" ]] || { printf ''; return 0; }
  python3 - "${STATE_FILE}" "${key}" <<'PY'
import json
import pathlib
import sys

try:
    value = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
    selected = value.get(sys.argv[2], "") if isinstance(value, dict) else ""
    print(selected if isinstance(selected, (str, int)) and not isinstance(selected, bool) else "")
except (OSError, ValueError):
    print("")
PY
}

browser_state_has_workspace() {
  [[ -n "$(browser_state_value applicationName)" \
    && -n "$(browser_state_value workspaceAlias)" ]]
}

browser_raw_marker() {
  if [[ -n "${TEST_AGENT_MEMORY_E2E_RAW_TRANSCRIPT_MARKER:-}" ]]; then
    printf '%s' "${TEST_AGENT_MEMORY_E2E_RAW_TRANSCRIPT_MARKER}"
    return
  fi
  browser_state_value rawTranscriptMarker
}

run_local_audit() {
  local raw_marker service container_id mounts readonly collection scan_status user caps security tmpfs
  local dimension governance_memory_id governance_logical_version governance_platform_version
  local governance_operation_id native_session secret_key secret_value
  ready
  assert_query_equals "Alembic is at the expected single head" "20260809_01" \
    "select version_num from alembic_version;"
  assert_zero_query "logical versions are monotonic" \
    "select count(*) from memory_logical_records r where exists (select 1 from memory_logical_history h where h.logical_memory_id=r.logical_memory_id and h.version > r.version);"
  assert_zero_query "logical history has no gaps or duplicate versions" \
    "select count(*) from memory_logical_records r left join lateral (select count(*) as events, min(version) as first_version, max(version) as last_version from memory_logical_history h where h.logical_memory_id=r.logical_memory_id) h on true where h.events <> r.version or h.first_version <> 1 or h.last_version <> r.version;"
  assert_zero_query "projection versions never exceed logical versions" \
    "select count(*) from memory_projections p join memory_logical_records r using(logical_memory_id) where p.projected_version > r.version;"
  assert_zero_query "every known profile has one projection row per logical memory" \
    "with profiles as (select distinct profile_key from memory_projections) select count(*) from memory_logical_records r cross join profiles f left join memory_projections p on p.logical_memory_id=r.logical_memory_id and p.profile_key=f.profile_key where p.logical_memory_id is null;"
  assert_zero_query "projection status and version match the logical record" \
    "select count(*) from memory_projections p join memory_logical_records r using(logical_memory_id) where p.projected_version <> r.version or (r.deleted and p.status <> 'DELETED') or (not r.deleted and p.status <> 'SYNCED');"
  assert_zero_query "projection outbox has no pending, processing or dead records" \
    "select count(*) from memory_projection_outbox where status in ('PENDING','PROCESSING','DEAD');"
  assert_zero_query "one native operation result never repeats a logical memory ID" \
    "select count(*) from (select o.operation_id, item->>'id' as logical_id, count(*) from memory_operations o cross join lateral jsonb_array_elements(coalesce(o.result_json, '[]'::jsonb)) item where o.native_started group by o.operation_id, item->>'id' having count(*) > 1) duplicated;"
  assert_zero_query "memory metadata contains no raw transcript fields" \
    "select count(*) from memory_logical_records where metadata ?| array['messages','rawMessages','raw_messages','transcript','conversation'];"

  native_session="$(browser_state_value enterpriseFaultSessionId)"
  [[ -n "${native_session}" ]] || native_session="$(browser_state_value replicaFaultSessionId)"
  if [[ -n "${native_session}" ]]; then
    [[ "${native_session}" =~ ^[A-Za-z0-9_-]{1,128}$ ]] \
      || die "Audit native-learning Session ID format is invalid"
    assert_query_equals "fault-window learning performed exactly one native operation" 1 \
      "select count(*) from memory_operations o where o.native_started and o.status='COMPLETED' and exists (select 1 from jsonb_array_elements(coalesce(o.result_json, '[]'::jsonb)) item where item->'metadata'->>'sessionId'='${native_session}');"
  fi

  governance_memory_id="$(browser_state_value governanceMemoryId)"
  governance_logical_version="$(browser_state_value governanceLogicalVersion)"
  governance_platform_version="$(browser_state_value governancePlatformVersion)"
  if [[ -n "${governance_memory_id}" || -n "${governance_logical_version}" \
    || -n "${governance_platform_version}" ]]; then
    [[ "${governance_memory_id}" =~ ^mem_[A-Za-z0-9_-]{1,120}$ \
      && "${governance_logical_version}" =~ ^[1-9][0-9]*$ \
      && "${governance_platform_version}" =~ ^[1-9][0-9]*$ ]] \
      || die "Audit governance state format is invalid"
    governance_operation_id="manual:${governance_memory_id}:archive:$((governance_platform_version - 1))"
    assert_query_equals "governance update/promote/archive reached one deleted logical record" 1 \
      "select count(*) from memory_logical_records where metadata->>'operationId'='${governance_operation_id}' and deleted=true and version=${governance_logical_version};"
  fi

  for service in memory-service-1 memory-service-2 memory-service-3 embedding-service; do
    container_id="$(compose ps -q "${service}")"
    [[ -n "${container_id}" ]] || die "Audit failed: ${service} is not running"
    mounts="$(docker inspect --format '{{json .Mounts}}' "${container_id}")"
    readonly="$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "${container_id}")"
    user="$(docker inspect --format '{{.Config.User}}' "${container_id}")"
    caps="$(docker inspect --format '{{json .HostConfig.CapDrop}}' "${container_id}")"
    security="$(docker inspect --format '{{json .HostConfig.SecurityOpt}}' "${container_id}")"
    tmpfs="$(docker inspect --format '{{json .HostConfig.Tmpfs}}' "${container_id}")"
    [[ "${mounts}" == "[]" && "${readonly}" == true \
      && -n "${user}" && "${user}" != 0 && "${user}" != 0:* \
      && "${caps}" == '["ALL"]' \
      && "${security}" == *'no-new-privileges:true'* \
      && "${tmpfs}" == *noexec* && "${tmpfs}" == *nosuid* && "${tmpfs}" == *nodev* ]] \
      || die "Audit failed: ${service} must be non-root, stateless, read-only, no-new-privileges and capability-free"
  done
  container_id="$(compose ps -q memory-lb)"
  [[ -n "${container_id}" ]] || die "Audit failed: memory-lb is not running"
  mounts="$(docker inspect --format '{{range .Mounts}}{{.Destination}}:{{.RW}};{{end}}' "${container_id}")"
  readonly="$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "${container_id}")"
  user="$(docker inspect --format '{{.Config.User}}' "${container_id}")"
  caps="$(docker inspect --format '{{json .HostConfig.CapDrop}}' "${container_id}")"
  security="$(docker inspect --format '{{json .HostConfig.SecurityOpt}}' "${container_id}")"
  tmpfs="$(docker inspect --format '{{json .HostConfig.Tmpfs}}' "${container_id}")"
  [[ "${mounts}" == "/etc/nginx/nginx.conf:false;" && "${readonly}" == true \
    && "${user}" == "101:101" && "${caps}" == '["ALL"]' \
    && "${security}" == *'no-new-privileges:true'* \
    && "${tmpfs}" == *noexec* && "${tmpfs}" == *nosuid* && "${tmpfs}" == *nodev* ]] \
    || die "Audit failed: memory-lb security identity/mount/capability contract changed"
  echo "OK audit: Mem0 replicas, CPU embedding and VIP are non-root, read-only and capability-free."

  raw_marker="${TEST_AGENT_MEMORY_E2E_RAW_TRANSCRIPT_MARKER:-}"
  [[ -n "${raw_marker}" ]] || die "Audit requires the browser-generated raw transcript marker state"
  [[ "${raw_marker}" =~ ^[A-Za-z0-9_-]{16,128}$ ]] \
    || die "Audit raw transcript marker format is invalid"

  assert_zero_query "raw transcript marker is absent from memory control/history" \
    "select (select count(*) from memory_logical_records where content like '%${raw_marker}%' or metadata::text like '%${raw_marker}%') + (select count(*) from memory_logical_history where coalesce(old_memory, '') like '%${raw_marker}%' or coalesce(new_memory, '') like '%${raw_marker}%') + (select count(*) from memory_mem0_history where coalesce(old_memory, '') like '%${raw_marker}%' or coalesce(new_memory, '') like '%${raw_marker}%') + (select count(*) from memory_operations where coalesce(result_json::text, '') like '%${raw_marker}%');"
  while IFS= read -r collection; do
    [[ -n "${collection}" ]] || continue
    [[ "${collection}" =~ ^memory_(cpu|enterprise)_d[0-9]+_[a-f0-9]{16}$ ]] \
      || die "Audit encountered an unexpected collection name"
    [[ "${collection}" =~ _d([0-9]+)_ ]] || die "Audit cannot parse vector dimension"
    dimension="${BASH_REMATCH[1]}"
    assert_zero_query "raw transcript marker is absent from ${collection}" \
      "select count(*) from \"${collection}\" where payload::text like '%${raw_marker}%';"
    assert_zero_query "${collection} has no duplicate logical memory vectors" \
      "select count(*) from (select payload->>'logicalMemoryId' as logical_id, count(*) from \"${collection}\" where coalesce(payload->>'logicalMemoryId','') <> '' group by payload->>'logicalMemoryId' having count(*) > 1) duplicated;"
    assert_zero_query "${collection} vectors match declared dimension ${dimension}" \
      "select count(*) from \"${collection}\" where vector_dims(vector) <> ${dimension};"
  done < <(query_local_memory_db \
    "select tablename from pg_tables where schemaname='public' and tablename ~ '^memory_(cpu|enterprise)_d[0-9]+_[a-f0-9]{16}$' order by tablename;")

  for service in memory-service-1 memory-service-2 memory-service-3 embedding-service memory-lb; do
    container_id="$(compose ps -q "${service}")"
    set +e
    docker exec "${container_id}" sh -c '
      marker="$1"; shift
      for root in "$@"; do
        [ -d "$root" ] || continue
        match="$(grep -R -F -l -- "$marker" "$root" 2>/dev/null | head -n 1)"
        [ -z "$match" ] || exit 42
      done
    ' sh "${raw_marker}" /tmp /var/tmp /var/cache/nginx /var/run >/dev/null 2>&1
    scan_status=$?
    set -e
    [[ "${scan_status}" -eq 0 ]] \
      || { [[ "${scan_status}" -eq 42 ]] \
        && die "Audit failed: raw transcript marker remained in ${service} runtime filesystem"; \
        die "Audit failed: unable to scan ${service} runtime filesystem"; }
  done
  echo "OK audit: raw transcript marker is absent from memory DB and runtime container filesystems."

  if compose logs --no-color memory-service-1 memory-service-2 memory-service-3 memory-lb embedding-service \
      | grep -F -- "${raw_marker}" >/dev/null; then
      die "Audit failed: raw transcript marker appeared in memory data-plane logs"
  fi
  for secret_key in \
    TEST_AGENT_MEMORY_SERVICE_API_KEY \
    TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET \
    TEST_AGENT_EMBEDDING_API_KEY \
    TEST_AGENT_MEMORY_DEV_POSTGRES_PASSWORD; do
    secret_value="$(awk -F= -v key="${secret_key}" '$1 == key {print substr($0, index($0, "=") + 1); exit}' "${DEV_ENV_FILE}")"
    [[ -n "${secret_value}" ]] || continue
    if compose logs --no-color memory-service-1 memory-service-2 memory-service-3 memory-lb embedding-service memory-postgres \
        | grep -F -- "${secret_value}" >/dev/null; then
      die "Audit failed: ${secret_key} appeared in memory data-plane logs"
    fi
  done
  echo "OK audit: raw transcript marker and service secrets are absent from memory data-plane logs."
  run_hook "platform canonical transcript audit" TEST_AGENT_MEMORY_E2E_AUDIT_CMD
}

preflight() {
  require_command python3
  if [[ "${RUN_FULL}" == true || "${RUN_FAULTS}" == true || "${CONCURRENCY}" -gt 0 ]]; then
    require_command corepack
    require_env TEST_AGENT_BASE_URL
    require_env TEST_AGENT_FRONTEND_URL
    require_env TEST_AGENT_MEMORY_E2E_ADMIN_USERNAME
    require_env TEST_AGENT_MEMORY_E2E_ADMIN_PASSWORD
  fi

  if [[ "${RUN_FULL}" == true ]]; then
    require_env TEST_AGENT_MEMORY_E2E_MEMBER_USERNAME
    require_env TEST_AGENT_MEMORY_E2E_MEMBER_PASSWORD
    require_env TEST_AGENT_MEMORY_E2E_ADMIN_USER_QUERY
    require_env TEST_AGENT_MEMORY_E2E_MEMBER_USER_QUERY
    require_env TEST_AGENT_MEMORY_E2E_REPOSITORY_NAME
    require_env TEST_AGENT_MEMORY_E2E_BRANCH
    require_env TEST_AGENT_MEMORY_E2E_DIRECTORY
  fi
  if [[ "${RUN_FAULTS}" == true || "${CONCURRENCY}" -gt 0 ]]; then
    if [[ "${RUN_FULL}" == false ]] && ! browser_state_has_workspace; then
      require_env TEST_AGENT_MEMORY_E2E_EXISTING_APPLICATION_NAME
      require_env TEST_AGENT_MEMORY_E2E_EXISTING_WORKSPACE_ALIAS
    fi
  fi
  if [[ "${RUN_FAULTS}" == true && "${RUN_FULL}" == false \
    && -z "${TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_ID:-}" \
    && -z "$(browser_state_value memoryId)" ]]; then
    die "Fault gate requires a browser-observed baseline: run --full first or set TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_ID"
  fi
  if [[ "${PARTITION_MODE}" != same && "${PARTITION_MODE}" != distinct && "${PARTITION_MODE}" != both ]]; then
    die "--partition must be same, distinct or both"
  fi
  if [[ "${CONCURRENCY}" -gt 0 && ( "${PARTITION_MODE}" == distinct || "${PARTITION_MODE}" == both ) ]]; then
    require_env TEST_AGENT_MEMORY_E2E_USERS_JSON
  fi

  if [[ "${ENTERPRISE}" == true && "${RUN_FAULTS}" == true ]]; then
    local key
    for key in \
      TEST_AGENT_MEM0_NODE_1_STOP_CMD TEST_AGENT_MEM0_NODE_1_START_CMD \
      TEST_AGENT_MEM0_NODE_2_STOP_CMD TEST_AGENT_MEM0_NODE_2_START_CMD \
      TEST_AGENT_MEM0_SCALE_OUT_CMD TEST_AGENT_MEM0_SCALE_IN_CMD \
      TEST_AGENT_JAVA_NODE_1_STOP_CMD TEST_AGENT_JAVA_NODE_1_START_CMD \
      TEST_AGENT_JAVA_NODE_2_STOP_CMD TEST_AGENT_JAVA_NODE_2_START_CMD \
      TEST_AGENT_ENTERPRISE_EMBEDDING_STOP_CMD TEST_AGENT_ENTERPRISE_EMBEDDING_START_CMD \
      TEST_AGENT_CPU_EMBEDDING_STOP_CMD TEST_AGENT_CPU_EMBEDDING_START_CMD \
      TEST_AGENT_MEMORY_E2E_READY_CMD; do
      require_env "${key}"
    done
  fi
  if [[ "${RUN_AUDIT}" == true ]]; then
    require_env TEST_AGENT_MEMORY_E2E_AUDIT_CMD
  fi
  if [[ "${ENTERPRISE}" == false ]]; then
    require_command docker
    [[ -f "${DEV_ENV_FILE}" ]] || die "Local memory env not found: ${DEV_ENV_FILE}; run tools/memory-dev-services.sh prepare"
    docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required"
  fi
}

if [[ $# -eq 0 ]]; then
  usage >&2
  exit 2
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --full) RUN_FULL=true; shift ;;
    --faults) RUN_FAULTS=true; shift ;;
    --audit) RUN_AUDIT=true; shift ;;
    --enterprise) ENTERPRISE=true; shift ;;
    --all) RUN_FULL=true; RUN_FAULTS=true; RUN_AUDIT=true; shift ;;
    --concurrency)
      [[ $# -ge 2 ]] || die "--concurrency requires a value"
      require_positive_integer --concurrency "$2" 64
      CONCURRENCY="$2"
      shift 2
      ;;
    --rounds)
      [[ $# -ge 2 ]] || die "--rounds requires a value"
      require_positive_integer --rounds "$2" 20
      CONCURRENCY_ROUNDS="$2"
      shift 2
      ;;
    --partition)
      [[ $# -ge 2 ]] || die "--partition requires a value"
      PARTITION_MODE="$2"
      shift 2
      ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown option: $1" ;;
  esac
done

if [[ "${RUN_FULL}" == false && "${RUN_FAULTS}" == false && "${RUN_AUDIT}" == false && "${CONCURRENCY}" -eq 0 ]]; then
  die "Select at least one of --full, --faults, --concurrency or --audit"
fi
if [[ "${CONCURRENCY}" -eq 0 && "${CONCURRENCY_ROUNDS}" -ne 1 ]]; then
  die "--rounds requires --concurrency"
fi

preflight

if [[ "${RUN_FULL}" == true ]]; then
  mkdir -p "$(dirname "${STATE_FILE}")"
  rm -f "${STATE_FILE}"
  run_browser_scenario full
  PASSED_GATES+=(browser-full)
fi
if [[ "${RUN_FAULTS}" == true ]]; then
  run_fault_gate
  PASSED_GATES+=(faults)
fi
if [[ "${CONCURRENCY}" -gt 0 ]]; then
  export TEST_AGENT_MEMORY_E2E_CONCURRENCY="${CONCURRENCY}"
  export TEST_AGENT_MEMORY_E2E_CONCURRENCY_ROUNDS="${CONCURRENCY_ROUNDS}"
  if [[ "${PARTITION_MODE}" == same || "${PARTITION_MODE}" == both ]]; then
    export TEST_AGENT_MEMORY_E2E_PARTITION_MODE=same
    run_browser_scenario concurrency
    PASSED_GATES+=(concurrency-same)
  fi
  if [[ "${PARTITION_MODE}" == distinct || "${PARTITION_MODE}" == both ]]; then
    export TEST_AGENT_MEMORY_E2E_PARTITION_MODE=distinct
    run_browser_scenario concurrency
    PASSED_GATES+=(concurrency-distinct)
  fi
fi
if [[ "${RUN_AUDIT}" == true ]]; then
  raw_marker="$(browser_raw_marker)"
  [[ -n "${raw_marker}" ]] || die "Audit requires the browser-generated raw transcript marker state"
  export TEST_AGENT_MEMORY_E2E_RAW_TRANSCRIPT_MARKER="${raw_marker}"
  export TEST_AGENT_MEMORY_E2E_ENTERPRISE_FAULT_SESSION_ID="$(browser_state_value enterpriseFaultSessionId)"
  export TEST_AGENT_MEMORY_E2E_ENTERPRISE_FAULT_RUN_ID="$(browser_state_value enterpriseFaultRunId)"
  export TEST_AGENT_MEMORY_E2E_ENTERPRISE_FAULT_MEMORY_ID="$(browser_state_value enterpriseFaultMemoryId)"
  export TEST_AGENT_MEMORY_E2E_GOVERNANCE_MEMORY_ID="$(browser_state_value governanceMemoryId)"
  export TEST_AGENT_MEMORY_E2E_GOVERNANCE_PLATFORM_VERSION="$(browser_state_value governancePlatformVersion)"
  export TEST_AGENT_MEMORY_E2E_GOVERNANCE_LOGICAL_VERSION="$(browser_state_value governanceLogicalVersion)"
  if [[ "${ENTERPRISE}" == true ]]; then
    run_hook "enterprise storage and log audit" TEST_AGENT_MEMORY_E2E_AUDIT_CMD
  else
    run_local_audit
  fi
  PASSED_GATES+=(storage-audit)
fi

RESTORE_REQUIRED=false
echo "Memory cluster selected gates passed: ${PASSED_GATES[*]}."
