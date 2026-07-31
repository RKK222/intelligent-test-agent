#!/usr/bin/env bash
set -euo pipefail

# 为分析任务容器创建唯一受控桥接网络，并在 DOCKER-USER 中只放行模型网关。
# Docker 18.09 不支持现代沙箱网络策略，因此必须由宿主机在任务启动前执行本脚本。

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/workflow.env"
MODE="apply"
CHAIN="TEST_AGENT_ANALYSIS_EGRESS"

usage() {
  cat <<'USAGE'
Usage: deploy/internal/workflow/analysis-network.sh [options]

Options:
  --env-file <path>   Workflow Runner dotenv path.
  --apply             Create/verify network and firewall rules (default).
  --verify-only       Verify current Docker network and firewall rules without writes.
  --validate-only     Validate values without calling Docker or iptables.
  -h, --help          Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --apply)
      MODE="apply"
      shift
      ;;
    --verify-only)
      MODE="verify"
      shift
      ;;
    --validate-only)
      MODE="validate"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

load_dotenv() {
  local file="$1" line key value duplicates
  [[ -f "${file}" ]] || {
    echo "Workflow env file not found: ${file}" >&2
    exit 1
  }
  duplicates="$(awk '
    {
      line = $0
      sub(/\r$/, "", line)
      sub(/^[[:space:]]+/, "", line)
      if (line == "" || line ~ /^#/) next
      sub(/^export[[:space:]]+/, "", line)
      separator = index(line, "=")
      if (!separator) next
      key = substr(line, 1, separator - 1)
      gsub(/[[:space:]]/, "", key)
      if (key ~ /^[A-Za-z_][A-Za-z0-9_]*$/ && ++seen[key] == 2) print key
    }
  ' "${file}")"
  [[ -z "${duplicates}" ]] || {
    echo "Duplicate workflow dotenv key(s): ${duplicates//$'\n'/, }" >&2
    exit 1
  }
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    [[ "${line}" == export\ * ]] && line="${line#export }"
    [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    key="${key//[[:space:]]/}"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    case "${key}" in
      TEST_AGENT_ANALYSIS_NETWORK|TEST_AGENT_ANALYSIS_SUBNET|TEST_AGENT_MODEL_GATEWAY_CIDR|TEST_AGENT_MODEL_GATEWAY_PORT)
        ;;
      *)
        # 此脚本不读取数据库、Redis或HMAC值，避免把完整运行配置传播给子进程。
        continue
        ;;
    esac
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    printf -v "${key}" '%s' "${value}"
    export -n "${key}" 2>/dev/null || true
  done <"${file}"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

validate_ipv4_cidr() {
  local value="$1" name="$2" ip prefix octet
  local -a octets
  [[ "${value}" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}/([0-9]|[12][0-9]|3[0-2])$ ]] || {
    echo "${name} must be an IPv4 CIDR: ${value}" >&2
    exit 1
  }
  ip="${value%/*}"
  prefix="${value#*/}"
  IFS='.' read -r -a octets <<<"${ip}"
  for octet in "${octets[@]}"; do
    (( 10#${octet} <= 255 )) || {
      echo "${name} contains an invalid IPv4 octet: ${value}" >&2
      exit 1
    }
  done
  (( prefix >= 0 && prefix <= 32 )) || exit 1
}

load_dotenv "${ENV_FILE}"

NETWORK="${TEST_AGENT_ANALYSIS_NETWORK:-test-agent-analysis-egress}"
SUBNET="${TEST_AGENT_ANALYSIS_SUBNET:-172.31.250.0/24}"
GATEWAY_CIDR="${TEST_AGENT_MODEL_GATEWAY_CIDR:-}"
GATEWAY_PORT="${TEST_AGENT_MODEL_GATEWAY_PORT:-}"

[[ "${NETWORK}" =~ ^test-agent-analysis-[A-Za-z0-9_.-]{1,80}$ ]] || {
  echo "TEST_AGENT_ANALYSIS_NETWORK must use the test-agent-analysis-* namespace" >&2
  exit 1
}
validate_ipv4_cidr "${SUBNET}" TEST_AGENT_ANALYSIS_SUBNET
validate_ipv4_cidr "${GATEWAY_CIDR}" TEST_AGENT_MODEL_GATEWAY_CIDR
[[ "${GATEWAY_PORT}" =~ ^[0-9]{1,5}$ ]] && (( GATEWAY_PORT >= 1 && GATEWAY_PORT <= 65535 )) || {
  echo "TEST_AGENT_MODEL_GATEWAY_PORT is invalid" >&2
  exit 1
}

if [[ "${MODE}" == "validate" ]]; then
  echo "Workflow analysis network values are valid."
  exit 0
fi

require_command docker
require_command iptables

docker_version="$(docker version --format '{{.Server.Version}}')"
[[ "${docker_version}" =~ ^([0-9]+)\.([0-9]+) ]] || {
  echo "Unable to read Docker Server version" >&2
  exit 1
}
docker_major="${BASH_REMATCH[1]}"
docker_minor="${BASH_REMATCH[2]}"
(( docker_major > 18 || (docker_major == 18 && docker_minor >= 9) )) || {
  echo "Analysis Runner requires Docker 18.09 or newer" >&2
  exit 1
}

network_matches() {
  local actual
  actual="$(docker network inspect --format '{{.Name}}|{{.Driver}}|{{.Scope}}|{{.Internal}}|{{index .Labels "com.enterprise.testagent.egress-policy"}}|{{index .Labels "com.enterprise.testagent.model-gateway-cidr"}}|{{index .Labels "com.enterprise.testagent.model-gateway-port"}}|{{(index .IPAM.Config 0).Subnet}}' "${NETWORK}" 2>/dev/null)" || return 1
  [[ "${actual}" == "${NETWORK}|bridge|local|false|model-gateway-only|${GATEWAY_CIDR}|${GATEWAY_PORT}|${SUBNET}" ]]
}

if ! docker network inspect "${NETWORK}" >/dev/null 2>&1; then
  [[ "${MODE}" == "apply" ]] || {
    echo "Restricted Docker network is missing: ${NETWORK}" >&2
    exit 1
  }
  docker network create \
    --driver bridge \
    --subnet "${SUBNET}" \
    --label com.enterprise.testagent.egress-policy=model-gateway-only \
    --label "com.enterprise.testagent.model-gateway-cidr=${GATEWAY_CIDR}" \
    --label "com.enterprise.testagent.model-gateway-port=${GATEWAY_PORT}" \
    "${NETWORK}" >/dev/null
fi

network_matches || {
  echo "Existing Docker network does not match the locked workflow policy: ${NETWORK}" >&2
  exit 1
}

iptables -nL DOCKER-USER >/dev/null 2>&1 || {
  echo "DOCKER-USER chain is unavailable; Docker firewall integration is not ready" >&2
  exit 1
}

if [[ "${MODE}" == "apply" ]]; then
  if docker ps --format '{{.Names}}' | grep -Eq '^test-agent-analysis-'; then
    echo "Stop all analysis task containers before changing egress rules" >&2
    exit 1
  fi
  iptables -nL "${CHAIN}" >/dev/null 2>&1 || iptables -N "${CHAIN}"
  iptables -F "${CHAIN}"
  iptables -A "${CHAIN}" -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
  iptables -A "${CHAIN}" -d "${GATEWAY_CIDR}" -p tcp --dport "${GATEWAY_PORT}" -j ACCEPT
  iptables -A "${CHAIN}" -j REJECT --reject-with icmp-port-unreachable
  iptables -C DOCKER-USER -s "${SUBNET}" -j "${CHAIN}" >/dev/null 2>&1 \
    || iptables -I DOCKER-USER 1 -s "${SUBNET}" -j "${CHAIN}"
fi

iptables -C DOCKER-USER -s "${SUBNET}" -j "${CHAIN}" >/dev/null 2>&1
first_docker_user_rule="$(iptables -S DOCKER-USER | awk '$1 == "-A" {print; exit}')"
[[ "${first_docker_user_rule}" == "-A DOCKER-USER -s ${SUBNET} -j ${CHAIN}" ]] || {
  echo "Analysis egress jump is not the first DOCKER-USER rule; an earlier ACCEPT could bypass isolation" >&2
  exit 1
}
iptables -C "${CHAIN}" -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT >/dev/null 2>&1
iptables -C "${CHAIN}" -d "${GATEWAY_CIDR}" -p tcp --dport "${GATEWAY_PORT}" -j ACCEPT >/dev/null 2>&1
iptables -C "${CHAIN}" -j REJECT --reject-with icmp-port-unreachable >/dev/null 2>&1

rule_count="$(iptables -S "${CHAIN}" | awk '$1 == "-A" {count += 1} END {print count + 0}')"
[[ "${rule_count}" == "3" ]] || {
  echo "Unexpected rules exist in ${CHAIN}; refusing an ambiguous egress policy" >&2
  exit 1
}

echo "Workflow analysis egress is restricted to ${GATEWAY_CIDR}:${GATEWAY_PORT}."
