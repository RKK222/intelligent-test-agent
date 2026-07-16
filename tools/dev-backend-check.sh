#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
BACKEND_DIR="${ROOT_DIR}/backend"

usage() {
  cat <<'USAGE'
Usage: tools/dev-backend-check.sh [--help]

Run the backend checks expected before local integration.

The script reads the current environment and does not create or write secrets.
It sets JAVA_HOME to JDK 21 on macOS when /usr/libexec/java_home is available.
USAGE
}

for arg in "$@"; do
  case "${arg}" in
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: ${arg}" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${JAVA_HOME:-}" ]] && [[ -x /usr/libexec/java_home ]]; then
  export JAVA_HOME
  JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

cd "${BACKEND_DIR}"
mvn -q test
mvn clean package -DskipTests
mvn -pl test-agent-app -am dependency:tree \
  -Dincludes=com.enterprise.testagent:test-agent-opencode-sdk-generated

if rg "com\\.example\\.opencode\\.sdk" \
  test-agent-app/src/main/java \
  test-agent-domain/src/main/java \
  test-agent-event/src/main/java \
  test-agent-persistence/src/main/java; then
  echo "Generated opencode SDK leaked outside test-agent-opencode-client." >&2
  exit 1
fi

cd "${ROOT_DIR}"
tools/verify-ai-docs.sh
tools/verify-backend-skeleton.sh
git diff --check
