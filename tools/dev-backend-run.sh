#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

usage() {
  cat <<'USAGE'
Usage: tools/dev-backend-run.sh [--profile local|test] [--env-file <path>] [--help]

Start the backend test-agent-app executable jar with environment variables
loaded from an untracked dotenv file. The script parses KEY=VALUE lines and
does not source the file, so dotenv content is not executed as shell code.

Defaults:
  --profile local  reads .env.local
  --profile test   reads .env.test

Options:
  --profile   Spring profile to run, local or test. Default: local.
  --env-file  Override the dotenv file path.
  --help      Show this help.
USAGE
}

profile="local"
env_file=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile)
      [[ $# -ge 2 ]] || {
        echo "--profile requires a value." >&2
        exit 2
      }
      profile="$2"
      shift 2
      ;;
    --env-file)
      [[ $# -ge 2 ]] || {
        echo "--env-file requires a path." >&2
        exit 2
      }
      env_file="$2"
      shift 2
      ;;
    --help|-h)
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

case "${profile}" in
  local|test)
    ;;
  *)
    echo "Unsupported profile: ${profile}. Expected local or test." >&2
    exit 2
    ;;
esac

if [[ -z "${env_file}" ]]; then
  env_file="${ROOT_DIR}/.env.${profile}"
elif [[ "${env_file}" != /* ]]; then
  env_file="${ROOT_DIR}/${env_file}"
fi

if [[ ! -f "${env_file}" ]]; then
  echo "Missing env file: ${env_file}" >&2
  echo "Create it from the backend README example. Do not commit it." >&2
  exit 1
fi

load_env_file() {
  local file="$1"
  local line key value

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    if [[ "${line}" == export\ * ]]; then
      line="${line#export }"
    fi
    if [[ "${line}" != *=* ]]; then
      echo "Invalid env line in ${file}: expected KEY=VALUE." >&2
      exit 1
    fi

    key="${line%%=*}"
    value="${line#*=}"
    if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "Invalid env key in ${file}: ${key}" >&2
      exit 1
    fi

    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi

    export "${key}=${value}"
  done < "${file}"
}

load_env_file "${env_file}"
export SPRING_PROFILES_ACTIVE="${profile}"

# 设置 JAVA_HOME
java_version="${JAVA_VERSION:-21}"
if [[ -n "${JAVA_VERSION:-}" ]] || [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ "$(uname -s)" == "Darwin" ]]; then
    detected_home="$(/usr/libexec/java_home -v "${java_version}" 2>/dev/null || true)"
    if [[ -n "${detected_home}" ]]; then
      JAVA_HOME="${detected_home}"
    fi
  fi
  if [[ -n "${JAVA_HOME:-}" ]]; then
    export JAVA_HOME
    echo "JAVA_HOME set to: ${JAVA_HOME}"
  fi
fi

if [[ -z "${TEST_AGENT_OPENCODE_BASE_URL:-}" ]]; then
  echo "TEST_AGENT_OPENCODE_BASE_URL is required in ${env_file}." >&2
  exit 1
fi

echo "Starting backend with profile '${profile}' using $(basename "${env_file}")."
echo "Sensitive environment values are loaded but not printed."

cd "${ROOT_DIR}/backend"
mvn -pl test-agent-app -am -DskipTests package
exec java -jar "${ROOT_DIR}/backend/test-agent-app/target/test-agent-app-0.1.0-SNAPSHOT.jar" \
  --spring.profiles.active="${profile}"
