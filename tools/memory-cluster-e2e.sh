#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
FRONTEND_DIR="${ROOT_DIR}/frontend"
COMPOSE_FILE="${ROOT_DIR}/deploy/dev/memory-compose.yml"
DEV_ENV_FILE="${TEST_AGENT_MEMORY_DEV_ENV_FILE:-${ROOT_DIR}/.tmp/dev-services/memory/memory-dev.env}"
STATE_FILE="${TEST_AGENT_MEMORY_E2E_STATE_FILE:-${ROOT_DIR}/.tmp/memory-e2e-state.json}"

RUN_FULL=false
RUN_FAULTS=false
RUN_AUDIT=false
ENTERPRISE=false
CONCURRENCY=0
PARTITION_MODE=both
PASSED_GATES=()
RESTORE_REQUIRED=false

usage() {
  cat <<'USAGE'
Usage: tools/memory-cluster-e2e.sh [options]

Run the browser-only generic-memory release gate against real services. The
script never substitutes Python API/database calls for business acceptance.
Database, filesystem and log checks are a separate post-acceptance audit.

Options:
  --full                 Create an Application in the UI and run learning,
                         cross-session recall, team review and transcript ACL.
  --faults               Stop Mem0/embedding/Java nodes and run browser probes.
  --concurrency N        Run N browser sessions and enforce run-start p99 <=2s;
                         the measurement includes routing and retrieval.
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

Distinct-partition concurrency:
  TEST_AGENT_MEMORY_E2E_USERS_JSON='[{"username":"...","password":"..."},...]'

Operator-supplied hooks (commands are intentionally supplied by the release
operator and are not stored in the repository; stop/start hooks only apply to
enterprise mode, while the platform audit hook is required in both modes):
  TEST_AGENT_MEM0_NODE_{1,2,3}_{STOP,START}_CMD
  TEST_AGENT_JAVA_NODE_{1,2}_{STOP,START}_CMD
  TEST_AGENT_ENTERPRISE_EMBEDDING_{STOP,START}_CMD
  TEST_AGENT_CPU_EMBEDDING_{STOP,START}_CMD
  TEST_AGENT_MEMORY_E2E_READY_CMD
  TEST_AGENT_MEMORY_E2E_AUDIT_CMD              Required platform DB/Session/log audit;
                                               enterprise mode may cover the full external cluster.
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
  local name="$1" value="$2"
  [[ "${value}" =~ ^[1-9][0-9]*$ && "${value}" -le 64 ]] \
    || die "${name} must be an integer between 1 and 64"
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

  # 依次摘除两个副本，第二次探测时只剩一个副本，history/幂等仍来自共享 PostgreSQL。
  stop_mem0_node 1
  run_browser_scenario recall
  stop_mem0_node 2
  run_browser_scenario recall
  start_mem0_node 1
  start_mem0_node 2
  ready

  if [[ "${ENTERPRISE}" == true ]]; then
    # 两个 Java 节点逐台摘除，浏览器始终经 Nginx 访问，不绕过负载均衡。
    run_hook "Java node 1 stop" TEST_AGENT_JAVA_NODE_1_STOP_CMD
    run_browser_scenario recall
    run_hook "Java node 1 start" TEST_AGENT_JAVA_NODE_1_START_CMD
    ready
    run_hook "Java node 2 stop" TEST_AGENT_JAVA_NODE_2_STOP_CMD
    run_browser_scenario recall
    run_hook "Java node 2 start" TEST_AGENT_JAVA_NODE_2_START_CMD
    ready

    # 企业 profile 中断时 CPU 集合继续召回；期间产生的变更进入共享 outbox。
    run_hook "enterprise embedding stop" TEST_AGENT_ENTERPRISE_EMBEDDING_STOP_CMD
    run_browser_scenario recall
    run_hook "enterprise embedding start" TEST_AGENT_ENTERPRISE_EMBEDDING_START_CMD
    ready
    run_browser_scenario projection-recovery
    run_browser_scenario recall

    # CPU 中断时企业集合继续召回。
    stop_cpu_embedding
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

  # 无数据迁移重建一个无状态副本，确认扩缩容不要求停其余节点。
  stop_mem0_node 3
  start_mem0_node 3
  ready
  run_browser_scenario recall
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

browser_raw_marker() {
  if [[ -n "${TEST_AGENT_MEMORY_E2E_RAW_TRANSCRIPT_MARKER:-}" ]]; then
    printf '%s' "${TEST_AGENT_MEMORY_E2E_RAW_TRANSCRIPT_MARKER}"
    return
  fi
  [[ -f "${STATE_FILE}" ]] || return
  python3 - "${STATE_FILE}" <<'PY'
import json
import pathlib
import sys

try:
    value = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
    marker = value.get("rawTranscriptMarker", "")
    print(marker if isinstance(marker, str) else "")
except (OSError, ValueError):
    print("")
PY
}

run_local_audit() {
  local raw_marker service container_id mounts readonly collection scan_status
  ready
  assert_zero_query "logical versions are monotonic" \
    "select count(*) from memory_logical_records r where exists (select 1 from memory_logical_history h where h.logical_memory_id=r.logical_memory_id and h.version > r.version);"
  assert_zero_query "logical history has no gaps or duplicate versions" \
    "select count(*) from memory_logical_records r left join lateral (select count(*) as events, min(version) as first_version, max(version) as last_version from memory_logical_history h where h.logical_memory_id=r.logical_memory_id) h on true where h.events <> r.version or h.first_version <> 1 or h.last_version <> r.version;"
  assert_zero_query "projection versions never exceed logical versions" \
    "select count(*) from memory_projections p join memory_logical_records r using(logical_memory_id) where p.projected_version > r.version;"
  assert_zero_query "projection outbox has no pending, processing or dead records" \
    "select count(*) from memory_projection_outbox where status in ('PENDING','PROCESSING','DEAD');"
  assert_zero_query "memory metadata contains no raw transcript fields" \
    "select count(*) from memory_logical_records where metadata ?| array['messages','rawMessages','raw_messages','transcript','conversation'];"

  for service in memory-service-1 memory-service-2 memory-service-3 embedding-service; do
    container_id="$(compose ps -q "${service}")"
    [[ -n "${container_id}" ]] || die "Audit failed: ${service} is not running"
    mounts="$(docker inspect --format '{{json .Mounts}}' "${container_id}")"
    readonly="$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "${container_id}")"
    [[ "${mounts}" == "[]" && "${readonly}" == true ]] \
      || die "Audit failed: ${service} must be stateless and read-only"
  done
  container_id="$(compose ps -q memory-lb)"
  [[ -n "${container_id}" ]] || die "Audit failed: memory-lb is not running"
  mounts="$(docker inspect --format '{{range .Mounts}}{{.Destination}}:{{.RW}};{{end}}' "${container_id}")"
  readonly="$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "${container_id}")"
  [[ "${mounts}" == "/etc/nginx/nginx.conf:false;" && "${readonly}" == true ]] \
    || die "Audit failed: memory-lb may only mount its read-only Nginx configuration"
  echo "OK audit: Mem0 replicas, CPU embedding and VIP are read-only and have no runtime data mounts."

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
    assert_zero_query "raw transcript marker is absent from ${collection}" \
      "select count(*) from \"${collection}\" where payload::text like '%${raw_marker}%';"
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
  echo "OK audit: raw transcript marker is absent from memory data-plane logs."
  run_hook "platform canonical transcript audit" TEST_AGENT_MEMORY_E2E_AUDIT_CMD
}

preflight() {
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
    require_env TEST_AGENT_MEMORY_E2E_EXISTING_APPLICATION_NAME
    require_env TEST_AGENT_MEMORY_E2E_EXISTING_WORKSPACE_ALIAS
  fi
  if [[ "${RUN_FAULTS}" == true && "${RUN_FULL}" == false \
    && -z "${TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_ID:-}" && ! -f "${STATE_FILE}" ]]; then
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
      TEST_AGENT_MEM0_NODE_3_STOP_CMD TEST_AGENT_MEM0_NODE_3_START_CMD \
      TEST_AGENT_JAVA_NODE_1_STOP_CMD TEST_AGENT_JAVA_NODE_1_START_CMD \
      TEST_AGENT_JAVA_NODE_2_STOP_CMD TEST_AGENT_JAVA_NODE_2_START_CMD \
      TEST_AGENT_ENTERPRISE_EMBEDDING_STOP_CMD TEST_AGENT_ENTERPRISE_EMBEDDING_START_CMD \
      TEST_AGENT_CPU_EMBEDDING_STOP_CMD TEST_AGENT_CPU_EMBEDDING_START_CMD \
      TEST_AGENT_MEMORY_E2E_READY_CMD; do
      require_env "${key}"
    done
  fi
  if [[ "${RUN_AUDIT}" == true ]]; then
    require_command python3
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
      require_positive_integer --concurrency "$2"
      CONCURRENCY="$2"
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
  if [[ "${ENTERPRISE}" == true ]]; then
    run_hook "enterprise storage and log audit" TEST_AGENT_MEMORY_E2E_AUDIT_CMD
  else
    run_local_audit
  fi
  PASSED_GATES+=(storage-audit)
fi

RESTORE_REQUIRED=false
echo "Memory cluster selected gates passed: ${PASSED_GATES[*]}."
