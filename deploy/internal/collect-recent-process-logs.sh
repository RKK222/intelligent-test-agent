#!/usr/bin/env bash
set -euo pipefail

ROLE="auto"
DAYS=3
OUTPUT_DIR="/data/0709"
INSTALL_ROOT="/data/testagent"
NGINX_HOME="/data/apps/nginx"
BACKEND_SERVICE="test-agent-backend"
NODE_LABEL=""
INCLUDE_SENSITIVE=0
MAX_SOURCE_LINES=20000
MAX_MANAGED_LOG_FILES=40
MAX_ARCHIVE_BYTES=67108864

usage() {
  cat <<'USAGE'
Usage: collect-recent-process-logs.sh --include-sensitive [options]

Collect a bounded, read-only diagnostic bundle for recent enterprise process logs.
The default window is the latest 3 days. No service is restarted or modified.

Options:
  --include-sensitive       Required acknowledgement: logs may contain business data.
  --days <1-14>             Recent log window in days. Default: 3.
  --role <auto|backend|frontend|all>
                            Node role. Default: auto.
  --output-dir <path>       Archive output directory. Default: /data/0709.
  --install-root <path>     Test Agent install root. Default: /data/testagent.
  --nginx-home <path>       Frontend Nginx home. Default: /data/apps/nginx.
  --backend-service <name>  Backend systemd service. Default: test-agent-backend.
  --node-label <label>      Archive node label. Defaults to .serverhost or hostname.
  -h, --help                Show this help.

The bundle contains sanitized copies only. It never includes dotenv files, private
keys, database dumps, manager process state JSON, JAR files or Docker inspect env.
USAGE
}

require_option_value() {
  local option="$1"
  local value="${2:-}"
  if [[ -z "${value}" || "${value}" == -* ]]; then
    printf 'Missing value for %s\n' "${option}" >&2
    usage >&2
    exit 2
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --include-sensitive)
      INCLUDE_SENSITIVE=1
      shift
      ;;
    --days)
      require_option_value "$1" "${2:-}"
      DAYS="$2"
      shift 2
      ;;
    --role)
      require_option_value "$1" "${2:-}"
      ROLE="$2"
      shift 2
      ;;
    --output-dir)
      require_option_value "$1" "${2:-}"
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --install-root)
      require_option_value "$1" "${2:-}"
      INSTALL_ROOT="$2"
      shift 2
      ;;
    --nginx-home)
      require_option_value "$1" "${2:-}"
      NGINX_HOME="$2"
      shift 2
      ;;
    --backend-service)
      require_option_value "$1" "${2:-}"
      BACKEND_SERVICE="$2"
      shift 2
      ;;
    --node-label)
      require_option_value "$1" "${2:-}"
      NODE_LABEL="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf 'Unknown option: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ "${INCLUDE_SENSITIVE}" -ne 1 ]]; then
  printf 'Refusing to collect process logs without --include-sensitive acknowledgement\n' >&2
  exit 2
fi
if [[ ! "${DAYS}" =~ ^[0-9]+$ ]] || (( DAYS < 1 || DAYS > 14 )); then
  printf -- '--days must be an integer from 1 to 14: %s\n' "${DAYS}" >&2
  exit 2
fi
if [[ "${ROLE}" != "auto" && "${ROLE}" != "backend" \
    && "${ROLE}" != "frontend" && "${ROLE}" != "all" ]]; then
  printf -- '--role must be auto, backend, frontend or all: %s\n' "${ROLE}" >&2
  exit 2
fi
for checked_path in "${OUTPUT_DIR}" "${INSTALL_ROOT}" "${NGINX_HOME}"; do
  if [[ ! "${checked_path}" =~ ^/[A-Za-z0-9._/-]+$ ]]; then
    printf 'Paths must be absolute and contain only safe characters: %s\n' "${checked_path}" >&2
    exit 2
  fi
done
if [[ ! "${BACKEND_SERVICE}" =~ ^[A-Za-z0-9_.@-]+$ ]]; then
  printf 'Invalid backend service name: %s\n' "${BACKEND_SERVICE}" >&2
  exit 2
fi

if CUTOFF_UTC="$(date -u -d "${DAYS} days ago" '+%Y-%m-%dT%H:%M:%S' 2>/dev/null)"; then
  :
elif CUTOFF_UTC="$(date -u -v-"${DAYS}"d '+%Y-%m-%dT%H:%M:%S' 2>/dev/null)"; then
  :
else
  printf 'Unable to calculate UTC log cutoff with date command\n' >&2
  exit 2
fi
if CUTOFF_LOCAL="$(date -d "${DAYS} days ago" '+%Y-%m-%dT%H:%M:%S' 2>/dev/null)"; then
  :
elif CUTOFF_LOCAL="$(date -v-"${DAYS}"d '+%Y-%m-%dT%H:%M:%S' 2>/dev/null)"; then
  :
else
  printf 'Unable to calculate local log cutoff with date command\n' >&2
  exit 2
fi

safe_label() {
  printf '%s' "$1" | tr -cs 'A-Za-z0-9._-' '-' | sed 's/^-*//; s/-*$//'
}

sha256_file() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${file}" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${file}" | awk '{print $1}'
  else
    printf 'Neither sha256sum nor shasum is available\n' >&2
    return 1
  fi
}

sha256_text() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 | awk '{print $1}'
  else
    printf 'Neither sha256sum nor shasum is available\n' >&2
    return 1
  fi
}

file_size() {
  local file="$1"
  if stat -c '%s' "${file}" >/dev/null 2>&1; then
    stat -c '%s' "${file}"
  else
    stat -f '%z' "${file}"
  fi
}

file_mtime() {
  local file="$1"
  if stat -c '%y' "${file}" >/dev/null 2>&1; then
    stat -c '%y' "${file}"
  else
    stat -f '%Sm' -t '%Y-%m-%dT%H:%M:%S%z' "${file}"
  fi
}

# 诊断包只能保留脱敏后的日志副本。整行敏感字段宁可丢失上下文，也不能把凭据或用户正文带出。
sanitize_stream() {
  LC_ALL=C awk '
    BEGIN { private_block = 0 }
    {
      line = $0
      lower = tolower(line)

      if (lower ~ /-----begin [^-]*private key-----/) {
        private_block = 1
        print "[REDACTED_PRIVATE_KEY_BLOCK]"
        next
      }
      if (private_block == 1) {
        if (lower ~ /-----end [^-]*private key-----/) {
          private_block = 0
        }
        next
      }

      sub(/\?[^[:space:]\"]*/, "?[REDACTED_QUERY]", line)
      gsub(/\/home\/[^\/[:space:]]+/, "/home/[REDACTED]", line)
      gsub(/\/Users\/[^\/[:space:]]+/, "/Users/[REDACTED]", line)
      gsub(/\/agent-opencode\/manager\/worker\/logs\/[^[:space:]\"]+/, "/agent-opencode/manager/worker/logs/[REDACTED_MANAGED_LOG]", line)
      gsub(/\/agent-opencode\/workspace\/[^[:space:]\"]+/, "/agent-opencode/workspace/[REDACTED_WORKSPACE_PATH]", line)
      lower = tolower(line)

      if (lower ~ /(authorization|proxy-authorization|cookie|set-cookie)[[:space:]]*[:=]/ ||
          lower ~ /(password|passwd|secret|token|auth_token|authtoken|tokenvalue|api[_-]?key|access[_-]?key|clientsecret|client_secret|ticket|modelgrant|checkoutgrant|contexttoken|sessiondigest|encryptedprivatekey|privatekey|credential)[[:space:]\"'\''_-]*[:=]/ ||
          lower ~ /\"(prompt|messages|parts|toolinput|tool_input|tooloutput|tool_output|reasoning|input|output|content)\"[[:space:]]*:/ ||
          lower ~ /(unifiedauthid|unified_auth_id)[[:space:]\"'\''_-]*[:=]/ ||
          lower ~ /(ssh-rsa|ssh-ed25519|ecdsa-sha2-nistp)/ ||
          lower ~ /bearer[[:space:]]+[a-z0-9._~+\/-]+/ ||
          lower ~ /eyj[a-z0-9_-]*\.[a-z0-9_-]+\.[a-z0-9_-]+/) {
        print "[REDACTED_SENSITIVE_LOG_LINE]"
        next
      }

      if (length(line) > 16384) {
        print substr(line, 1, 16384) " [TRUNCATED_LONG_LINE]"
      } else {
        print line
      }
    }
  '
}

umask 077
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-process-logs.XXXXXX")"
BUNDLE_ROOT="${TMP_ROOT}/bundle"
RAW_ROOT="${TMP_ROOT}/raw"
WARNINGS_FILE="${BUNDLE_ROOT}/COLLECTION-WARNINGS.txt"
mkdir -p "${BUNDLE_ROOT}/logs" "${BUNDLE_ROOT}/snapshots" "${RAW_ROOT}" "${OUTPUT_DIR}"
: >"${WARNINGS_FILE}"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

warn() {
  printf '%s\n' "$*" >>"${WARNINGS_FILE}"
  printf '[WARN] %s\n' "$*" >&2
}

CAPTURE_SEQUENCE=0
capture_command() {
  local destination="$1"
  local title="$2"
  local command_name
  local raw_file
  local command_status
  shift 2
  command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    warn "Skipped ${title}: command not found (${command_name})"
    return 0
  fi

  CAPTURE_SEQUENCE=$((CAPTURE_SEQUENCE + 1))
  raw_file="${RAW_ROOT}/command-${CAPTURE_SEQUENCE}.log"
  mkdir -p "$(dirname "${destination}")"
  set +e
  "$@" 2>&1 | tail -n "${MAX_SOURCE_LINES}" >"${raw_file}"
  command_status="${PIPESTATUS[0]}"
  set -e
  {
    printf '# %s\n' "${title}"
    printf '# collected_at=%s exit_status=%s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "${command_status}"
    sanitize_stream <"${raw_file}"
  } >"${destination}"
  if [[ "${command_status}" -ne 0 ]]; then
    warn "Command for ${title} exited with status ${command_status}; partial output kept"
  fi
}

# 仅凭文件 mtime 会把长寿命 manager/OpenCode 日志中的历史错误带入最近窗口。
# 这里识别现场三类稳定时间戳，并让无时间戳的续行继承上一条记录的窗口判定。
filter_recent_log_stream() {
  LC_ALL=C awk -v cutoff_utc="${CUTOFF_UTC}" -v cutoff_local="${CUTOFF_LOCAL}" '
    BEGIN {
      month["Jan"] = "01"; month["Feb"] = "02"; month["Mar"] = "03"
      month["Apr"] = "04"; month["May"] = "05"; month["Jun"] = "06"
      month["Jul"] = "07"; month["Aug"] = "08"; month["Sep"] = "09"
      month["Oct"] = "10"; month["Nov"] = "11"; month["Dec"] = "12"
      keep = 0
    }
    {
      line = $0
      recognized = 0
      marker = index(line, "timestamp=")
      if (marker > 0) {
        stamp = substr(line, marker + 10, 19)
        if (stamp ~ /^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]$/) {
          recognized = 1
          keep = (stamp >= cutoff_utc)
        }
      }
      slash_stamp = substr(line, 1, 19)
      if (!recognized && slash_stamp ~ /^[0-9][0-9][0-9][0-9]\/[0-9][0-9]\/[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9]$/) {
        recognized = 1
        normalized = slash_stamp
        gsub(/\//, "-", normalized)
        normalized = substr(normalized, 1, 10) "T" substr(normalized, 12, 8)
        keep = (normalized >= cutoff_local)
      }
      bracket = index(line, "[")
      access_stamp = bracket > 0 ? substr(line, bracket + 1, 20) : ""
      if (!recognized && access_stamp ~ /^[0-9][0-9]\/[A-Z][a-z][a-z]\/[0-9][0-9][0-9][0-9]:[0-9][0-9]:[0-9][0-9]:[0-9][0-9]$/) {
        split(access_stamp, access_parts, /[\/:]/)
        if (month[access_parts[2]] != "") {
          recognized = 1
          normalized = access_parts[3] "-" month[access_parts[2]] "-" access_parts[1] \
            "T" access_parts[4] ":" access_parts[5] ":" access_parts[6]
          keep = (normalized >= cutoff_local)
        }
      }
      if (recognized) {
        if (keep) print line
        next
      }
      if (keep) print line
    }
  '
}

collect_plain_log_file() {
  local source="$1"
  local destination="$2"
  local title="$3"
  local raw_file
  local -a pipeline_status
  CAPTURE_SEQUENCE=$((CAPTURE_SEQUENCE + 1))
  raw_file="${RAW_ROOT}/file-${CAPTURE_SEQUENCE}.log"
  mkdir -p "$(dirname "${destination}")"
  if [[ "${source}" == *.gz ]]; then
    if ! command -v gzip >/dev/null 2>&1; then
      warn "Skipped ${title}: gzip is unavailable"
      return 0
    fi
    set +e
    gzip -cd "${source}" 2>&1 | filter_recent_log_stream | tail -n "${MAX_SOURCE_LINES}" >"${raw_file}"
    pipeline_status=("${PIPESTATUS[@]}")
    set -e
    if [[ "${pipeline_status[0]}" -ne 0 || "${pipeline_status[1]}" -ne 0 ]]; then
      warn "Unable to read compressed log ${title}; partial output kept"
    fi
  else
    set +e
    filter_recent_log_stream <"${source}" | tail -n "${MAX_SOURCE_LINES}" >"${raw_file}" 2>&1
    pipeline_status=("${PIPESTATUS[@]}")
    set -e
    if [[ "${pipeline_status[0]}" -ne 0 ]]; then
      warn "Unable to read ${title}"
      return 0
    fi
  fi
  {
    printf '# %s\n' "${title}"
    printf '# source_mtime=%s source_bytes=%s\n' "$(file_mtime "${source}")" "$(file_size "${source}")"
    sanitize_stream <"${raw_file}"
  } >"${destination}"
}

BACKEND_SELECTED=0
FRONTEND_SELECTED=0
case "${ROLE}" in
  backend)
    BACKEND_SELECTED=1
    ;;
  frontend)
    FRONTEND_SELECTED=1
    ;;
  all)
    BACKEND_SELECTED=1
    FRONTEND_SELECTED=1
    ;;
  auto)
    if [[ -f "${INSTALL_ROOT}/config/backend.env" ]] \
        || [[ -d "${INSTALL_ROOT}/data/agent-opencode/manager/worker" ]]; then
      BACKEND_SELECTED=1
    fi
    if [[ -d "${NGINX_HOME}/logs" ]] || [[ -x "${NGINX_HOME}/sbin/nginx" ]]; then
      FRONTEND_SELECTED=1
    fi
    if [[ "${BACKEND_SELECTED}" -eq 0 && "${FRONTEND_SELECTED}" -eq 0 ]]; then
      printf 'Unable to detect node role; pass --role backend, frontend or all\n' >&2
      exit 2
    fi
    ;;
esac

if [[ -z "${NODE_LABEL}" && -f "${INSTALL_ROOT}/data/.serverhost" ]]; then
  NODE_LABEL="$(head -n 1 "${INSTALL_ROOT}/data/.serverhost" 2>/dev/null || true)"
fi
if [[ -z "${NODE_LABEL}" ]]; then
  NODE_LABEL="$(hostname -s 2>/dev/null || printf unknown)"
fi
NODE_LABEL="$(safe_label "${NODE_LABEL}")"
NODE_LABEL="${NODE_LABEL:-unknown}"

# 宿主机快照不读取命令行参数和环境变量，避免把启动参数中的密钥带入归档。
capture_command "${BUNDLE_ROOT}/snapshots/host-date.txt" "Host date and timezone" date '+%Y-%m-%dT%H:%M:%S%z'
capture_command "${BUNDLE_ROOT}/snapshots/kernel.txt" "Kernel information" uname -a
capture_command "${BUNDLE_ROOT}/snapshots/uptime.txt" "Host uptime" uptime
capture_command "${BUNDLE_ROOT}/snapshots/disk.txt" "Install-root disk usage" df -h "${INSTALL_ROOT}"
capture_command "${BUNDLE_ROOT}/snapshots/memory.txt" "Host memory" free -m
capture_command "${BUNDLE_ROOT}/snapshots/processes.txt" "Selected process snapshot without arguments" \
  sh -c "ps -eo pid,ppid,user,lstart,etime,%cpu,%mem,comm | grep -E '(^[[:space:]]*PID|java|nginx|opencode|docker|redis|postgres|mysql|node|test-agent)'"
capture_command "${BUNDLE_ROOT}/snapshots/listening-ports.txt" "Listening TCP ports" ss -lntp

collect_backend_logs() {
  local container_list="${RAW_ROOT}/containers.txt"
  local container
  local safe_container
  local managed_dir="${INSTALL_ROOT}/data/agent-opencode/manager/worker/logs"
  local managed_list="${RAW_ROOT}/managed-files.txt"
  local managed_source
  local managed_index=0
  local managed_digest
  local managed_destination
  local managed_matches
  local -a managed_status

  capture_command "${BUNDLE_ROOT}/snapshots/backend-systemd-show.txt" "Backend systemd state" \
    systemctl show "${BACKEND_SERVICE}" \
      -p ActiveState -p SubState -p MainPID -p ExecMainStatus -p NRestarts \
      -p ActiveEnterTimestamp -p FragmentPath
  capture_command "${BUNDLE_ROOT}/snapshots/backend-systemd-status.txt" "Backend systemd status" \
    systemctl status "${BACKEND_SERVICE}" --no-pager -l
  capture_command "${BUNDLE_ROOT}/logs/backend-journal.log" "Backend journal for latest ${DAYS} day(s)" \
    journalctl -u "${BACKEND_SERVICE}" --since "${DAYS} days ago" --no-pager -o short-iso
  capture_command "${BUNDLE_ROOT}/logs/docker-journal.log" "Docker daemon journal for latest ${DAYS} day(s)" \
    journalctl -u docker --since "${DAYS} days ago" --no-pager -o short-iso
  capture_command "${BUNDLE_ROOT}/snapshots/backend-health.txt" "Backend health" \
    curl -sS --max-time 5 http://127.0.0.1:8080/actuator/health
  capture_command "${BUNDLE_ROOT}/snapshots/backend-readiness.txt" "Backend readiness" \
    curl -sS --max-time 5 http://127.0.0.1:8080/actuator/health/readiness

  if command -v docker >/dev/null 2>&1; then
    capture_command "${BUNDLE_ROOT}/snapshots/docker-containers.txt" "Test Agent Docker containers" \
      docker ps -a --filter 'name=test-agent-' \
        --format '{{.Names}}|{{.Image}}|{{.Status}}|{{.Ports}}'
    set +e
    docker ps -a --filter 'name=test-agent-' --format '{{.Names}}' 2>/dev/null \
      | grep -E '^test-agent-[A-Za-z0-9_.-]+$' | sort -u | sed -n '1,50p' >"${container_list}"
    local list_status="${PIPESTATUS[0]}"
    set -e
    if [[ "${list_status}" -ne 0 ]]; then
      warn "Unable to list Test Agent Docker containers"
    fi
    while IFS= read -r container || [[ -n "${container}" ]]; do
      [[ -n "${container}" ]] || continue
      safe_container="$(safe_label "${container}")"
      [[ -n "${safe_container}" ]] || continue
      capture_command "${BUNDLE_ROOT}/snapshots/container-${safe_container}.txt" \
        "Container state ${safe_container}" \
        docker inspect --format \
          'name={{.Name}} image={{.Config.Image}} status={{.State.Status}} running={{.State.Running}} started={{.State.StartedAt}} finished={{.State.FinishedAt}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}} restarts={{.RestartCount}}{{if .State.Health}} health={{.State.Health.Status}}{{end}}' \
          "${container}"
      capture_command "${BUNDLE_ROOT}/logs/container-${safe_container}.log" \
        "Container log ${safe_container} for latest ${DAYS} day(s)" \
        docker logs --since "$((DAYS * 24))h" --tail "${MAX_SOURCE_LINES}" "${container}"
    done <"${container_list}"
  else
    warn "Skipped container snapshots and logs: docker command not found"
  fi

  : >"${BUNDLE_ROOT}/snapshots/managed-process-log-index.txt"
  if [[ -d "${managed_dir}" ]]; then
    find "${managed_dir}" -maxdepth 1 -type f -name '*.log' -mtime "-${DAYS}" -print 2>/dev/null \
      | sort | tail -n "${MAX_MANAGED_LOG_FILES}" >"${managed_list}"
    while IFS= read -r managed_source || [[ -n "${managed_source}" ]]; do
      [[ -f "${managed_source}" ]] || continue
      managed_index=$((managed_index + 1))
      managed_digest="$(printf '%s' "$(basename "${managed_source}")" | sha256_text)"
      managed_destination="${BUNDLE_ROOT}/logs/managed-process-$(printf '%03d' "${managed_index}")-${managed_digest}.log"
      managed_matches="${RAW_ROOT}/managed-${managed_index}.log"
      set +e
      filter_recent_log_stream <"${managed_source}" | LC_ALL=C grep -n -E \
        'OutOfMemoryError|StackOverflowError|Exception|ERROR|FATAL|panic|failed|failure|unhealthy|health.*(fail|error)|Address already in use|EADDRINUSE|ECONNREFUSED|ECONNRESET|ENOSPC|EMFILE|ENOMEM|SIGTERM|SIGKILL|connection (refused|reset|timed out)|timeout|exited? (with )?(code|status)|listen(ing)? on' \
        | tail -n "${MAX_SOURCE_LINES}" >"${managed_matches}"
      managed_status=("${PIPESTATUS[@]}")
      set -e
      if [[ "${managed_status[0]}" -ne 0 ]]; then
        warn "Unable to apply recent timestamp window to managed log ${managed_digest}"
      fi
      {
        printf '# Managed process diagnostic excerpts\n'
        printf '# source_name_sha256=%s source_mtime=%s source_bytes=%s\n' \
          "${managed_digest}" "$(file_mtime "${managed_source}")" "$(file_size "${managed_source}")"
        if [[ "${managed_status[1]}" -eq 0 ]]; then
          sanitize_stream <"${managed_matches}"
        else
          printf '[INFO] No selected technical diagnostic signature in this log.\n'
        fi
      } >"${managed_destination}"
      printf '%03d sha256=%s mtime=%s bytes=%s\n' \
        "${managed_index}" "${managed_digest}" "$(file_mtime "${managed_source}")" \
        "$(file_size "${managed_source}")" \
        >>"${BUNDLE_ROOT}/snapshots/managed-process-log-index.txt"
    done <"${managed_list}"
  else
    warn "Managed OpenCode process log directory not found: ${managed_dir}"
  fi
}

collect_frontend_logs() {
  local nginx_binary="${NGINX_HOME}/sbin/nginx"
  local nginx_list="${RAW_ROOT}/nginx-files.txt"
  local nginx_source
  local nginx_index=0
  local nginx_name

  capture_command "${BUNDLE_ROOT}/snapshots/nginx-processes.txt" "Nginx process snapshot without arguments" \
    sh -c "ps -eo pid,ppid,user,lstart,etime,%cpu,%mem,comm | grep -E '(^[[:space:]]*PID|nginx)'"
  if [[ -x "${nginx_binary}" ]]; then
    capture_command "${BUNDLE_ROOT}/snapshots/nginx-config-test.txt" "Nginx configuration test" \
      "${nginx_binary}" -p "${NGINX_HOME}/" -c "${NGINX_HOME}/conf/nginx.conf" -t
  else
    warn "Skipped Nginx configuration test: binary not executable (${nginx_binary})"
  fi
  capture_command "${BUNDLE_ROOT}/snapshots/frontend-http.txt" "Frontend loopback HTTP status" \
    curl -sS --max-time 5 -o /dev/null -w 'http_code=%{http_code} total_seconds=%{time_total}\n' \
      http://127.0.0.1/

  if [[ -d "${NGINX_HOME}/logs" ]]; then
    find "${NGINX_HOME}/logs" -maxdepth 1 -type f \
      \( -name 'access.log*' -o -name 'error.log*' \) -mtime "-${DAYS}" -print 2>/dev/null \
      | sort >"${nginx_list}"
    while IFS= read -r nginx_source || [[ -n "${nginx_source}" ]]; do
      [[ -f "${nginx_source}" ]] || continue
      nginx_index=$((nginx_index + 1))
      nginx_name="$(safe_label "$(basename "${nginx_source}")")"
      collect_plain_log_file "${nginx_source}" \
        "${BUNDLE_ROOT}/logs/nginx-$(printf '%03d' "${nginx_index}")-${nginx_name}.log" \
        "Nginx log ${nginx_name} within latest ${DAYS} day(s)"
    done <"${nginx_list}"
    if [[ "${nginx_index}" -eq 0 ]]; then
      warn "No Nginx access/error log files modified within latest ${DAYS} day(s)"
    fi
  else
    warn "Nginx log directory not found: ${NGINX_HOME}/logs"
  fi
}

if [[ "${BACKEND_SELECTED}" -eq 1 ]]; then
  collect_backend_logs
fi
if [[ "${FRONTEND_SELECTED}" -eq 1 ]]; then
  collect_frontend_logs
fi

append_finding() {
  local code="$1"
  local description="$2"
  local pattern="$3"
  local matches="${RAW_ROOT}/finding-${code}.txt"
  local count
  set +e
  LC_ALL=C grep -R -n -E "${pattern}" "${BUNDLE_ROOT}/logs" >"${matches}" 2>/dev/null
  set -e
  count="$(wc -l <"${matches}" | tr -d '[:space:]')"
  printf '%s|%s|%s\n' "${code}" "${count:-0}" "${description}" \
    >>"${BUNDLE_ROOT}/DIAGNOSTIC-SUMMARY.txt"
  if [[ "${count:-0}" -gt 0 ]]; then
    printf '\n[%s] first matches (maximum 20):\n' "${code}" \
      >>"${BUNDLE_ROOT}/DIAGNOSTIC-SUMMARY.txt"
    sed -n '1,20p' "${matches}" >>"${BUNDLE_ROOT}/DIAGNOSTIC-SUMMARY.txt"
  fi
}

{
  printf 'Test Agent recent process log diagnostic summary\n\n'
  printf 'Node: %s\n' "${NODE_LABEL}"
  printf 'Requested role: %s\n' "${ROLE}"
  printf 'Collected backend role: %s\n' "${BACKEND_SELECTED}"
  printf 'Collected frontend role: %s\n' "${FRONTEND_SELECTED}"
  printf 'Window: latest %s day(s)\n' "${DAYS}"
  printf 'Generated: %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')"
  printf '\nKeyword counts are diagnostic clues, not confirmed defects.\n'
  printf 'CODE|COUNT|MEANING\n'
} >"${BUNDLE_ROOT}/DIAGNOSTIC-SUMMARY.txt"

append_finding "RUNTIME_FATAL" \
  "JVM/native fatal failure or forced process exit; inspect exact timestamp and host resources." \
  'OutOfMemoryError|StackOverflowError|SIGSEGV|Killed process|exit( code| status)?[=: ]+(137|139)|panic:|fatal error'
append_finding "MIGRATION" \
  "Flyway validation or migration mismatch; stop rollout and compare immutable migration bytes." \
  'FlywayValidateException|Migration checksum mismatch|checksum.*(mismatch|failed)|Validate failed: Migration'
append_finding "RESOURCE" \
  "Host/container resource exhaustion may be present." \
  'Too many open files|Cannot allocate memory|No space left on device|resource temporarily unavailable|OOMKilled|ENOSPC|EMFILE|ENOMEM'
append_finding "PORT_BIND" \
  "Port collision or bind failure may prevent Java, manager, OpenCode or Nginx startup." \
  'Address already in use|EADDRINUSE|bind.*(failed|failure)|port.*already.*use'
append_finding "DEPENDENCY" \
  "Database, Redis, manager, model or cross-node dependency may be unavailable." \
  'Connection refused|Connection reset|connection timed out|OPENCODE_UNAVAILABLE|RUNTIME_STATE_UNAVAILABLE|Redis.*unavailable|PostgreSQL.*unavailable|manager.*unavailable'
append_finding "PROXY" \
  "Nginx/upstream timeout, reset or 5xx evidence may exist." \
  'upstream timed out|connect\(\) failed|prematurely closed|upstream.*(502|504)| HTTP/[0-9.]+\" (502|504) '
append_finding "PROCESS_ASSIGNMENT" \
  "Concurrent OpenCode process state writes may have rejected an old start result." \
  '进程分配已变化|SAVING_CANDIDATE|HEALTH_CHECKING'
append_finding "MANAGER_LINK" \
  "Manager WebSocket/configuration link may be unstable." \
  'websocket disconnected|manager.*disconnect|config update.*failed|event=manager_command_exit.*status=(FAILED|UNHEALTHY)'
append_finding "GENERAL_ERROR" \
  "General error signatures need traceId/timestamp correlation before bug classification." \
  '(^|[^A-Za-z])(ERROR|FATAL|Exception|FAILED|unhealthy)([^A-Za-z]|$)'

warning_count="$(wc -l <"${WARNINGS_FILE}" | tr -d '[:space:]')"
printf '\nCollection warnings: %s (see COLLECTION-WARNINGS.txt)\n' "${warning_count:-0}" \
  >>"${BUNDLE_ROOT}/DIAGNOSTIC-SUMMARY.txt"

cat >"${BUNDLE_ROOT}/README-SENSITIVE.txt" <<EOF
Test Agent bounded recent process log bundle.

Node: ${NODE_LABEL}
Requested role: ${ROLE}
Recent window: ${DAYS} day(s)
Collected: $(date '+%Y-%m-%dT%H:%M:%S%z')
Per-source line limit: ${MAX_SOURCE_LINES}
Managed process file limit: ${MAX_MANAGED_LOG_FILES}
Maximum archive bytes: ${MAX_ARCHIVE_BYTES}

This archive contains sanitized log copies and diagnostic snapshots only. Sanitization
removes recognized credentials, query strings, private-key blocks, prompt/message/tool
payload fields, home/workspace path segments and managed-log identity filenames.

Logs can still contain business identifiers or unexpected application text. Keep this
mode-0600 archive inside approved enterprise diagnostic channels and delete it according
to the incident retention policy. Do not treat keyword matches as confirmed defects.

Never included: dotenv/config contents, private keys, JAR/lib, database/Redis data,
manager processes/*.json state, Docker inspect environment or full managed process logs.
EOF

find "${BUNDLE_ROOT}" -type f -print | sed "s#^${BUNDLE_ROOT}/##" | sort \
  >"${BUNDLE_ROOT}/CONTENTS.txt"

timestamp="$(date '+%Y%m%d-%H%M%S')"
archive="${OUTPUT_DIR}/test-agent-process-logs-SENSITIVE-${NODE_LABEL}-${DAYS}d-${timestamp}.tar.gz"
tar -C "${BUNDLE_ROOT}" -czf "${archive}" .
archive_bytes="$(file_size "${archive}")"
if (( archive_bytes > MAX_ARCHIVE_BYTES )); then
  rm -f "${archive}"
  printf 'Diagnostic archive exceeds 64 MiB (%s bytes); archive removed\n' "${archive_bytes}" >&2
  exit 1
fi
chmod 0600 "${archive}"
archive_digest="$(sha256_file "${archive}")"
printf '%s  %s\n' "${archive_digest}" "$(basename "${archive}")" >"${archive}.sha256"
chmod 0600 "${archive}.sha256"

printf 'Diagnostic archive: %s\n' "${archive}"
printf 'Archive bytes: %s (limit %s)\n' "${archive_bytes}" "${MAX_ARCHIVE_BYTES}"
printf 'Checksum: %s.sha256\n' "${archive}"
printf 'Next: copy the archive and checksum to the controlled enterprise staging host.\n'
