#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
ENV_FILE="${TEST_AGENT_E2E_ENV_FILE:-${ROOT_DIR}/.env.test}"
USER_ID="${TEST_AGENT_E2E_USER_ID:-usr_test_dev}"
LINUX_SERVER_ID="${TEST_AGENT_E2E_LINUX_SERVER_ID:-$(hostname)}"
FIXTURE_SOURCE="${ROOT_DIR}/backend/test-agent-app/src/test/resources/fixtures/conversation-workspace-e2e"
FIXTURE_ROOT="${ROOT_DIR}/.tmp/conversation-workspace-e2e"
WORKSPACE_ROOT="${FIXTURE_ROOT}/workspace"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "FAIL: 本地测试环境文件不存在：${ENV_FILE}" >&2
  exit 1
fi
if [[ ! -d "${FIXTURE_SOURCE}" ]]; then
  echo "FAIL: E2E 工作空间模板不存在：${FIXTURE_SOURCE}" >&2
  exit 1
fi
if [[ ! "${USER_ID}" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "FAIL: TEST_AGENT_E2E_USER_ID 只能包含字母、数字、点、下划线和连字符" >&2
  exit 1
fi
if [[ ! "${LINUX_SERVER_ID}" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "FAIL: TEST_AGENT_E2E_LINUX_SERVER_ID 只能包含字母、数字、点、下划线和连字符" >&2
  exit 1
fi

mkdir -p "${WORKSPACE_ROOT}"
cp -R "${FIXTURE_SOURCE}/." "${WORKSPACE_ROOT}/"

if [[ ! -d "${WORKSPACE_ROOT}/.git" ]]; then
  git init -q -b main "${WORKSPACE_ROOT}"
  git -C "${WORKSPACE_ROOT}" config user.name "TestAgent E2E Fixture"
  git -C "${WORKSPACE_ROOT}" config user.email "testagent-e2e@example.invalid"
  git -C "${WORKSPACE_ROOT}" remote add origin "https://github.com/spring-projects/spring-petclinic.git"
fi
git -C "${WORKSPACE_ROOT}" add README.md requirements docs .opencode test-data
if ! git -C "${WORKSPACE_ROOT}" diff --cached --quiet; then
  git -C "${WORKSPACE_ROOT}" commit -q -m "更新对话与工作空间 E2E 测试材料"
fi
FIXTURE_COMMIT="$(git -C "${WORKSPACE_ROOT}" rev-parse HEAD)"

export JAVA_VERSION="${JAVA_VERSION:-25}"
if [[ "$(uname -s)" == "Darwin" ]]; then
  JAVA_HOME="$(/usr/libexec/java_home -v "${JAVA_VERSION}")"
  export JAVA_HOME
fi
export PATH="${JAVA_HOME:+${JAVA_HOME}/bin:}${ROOT_DIR}/.tmp/dev-bin:/opt/homebrew/opt/libpq/bin:${PATH}"

cd "${ROOT_DIR}/backend"
mvn -pl test-agent-app -am \
  -Dtest=ConversationWorkspaceE2eDataFixtureTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtestagent.conversation-workspace.e2e.enabled=true \
  -Dtestagent.conversation-workspace.e2e.env-file="${ENV_FILE}" \
  -Dtestagent.conversation-workspace.e2e.workspace-root="${WORKSPACE_ROOT}" \
  -Dtestagent.conversation-workspace.e2e.commit="${FIXTURE_COMMIT}" \
  -Dtestagent.conversation-workspace.e2e.user-id="${USER_ID}" \
  -Dtestagent.conversation-workspace.e2e.linux-server-id="${LINUX_SERVER_ID}" \
  test

echo "E2E 测试数据已写入：用户=${USER_ID}，服务器=${LINUX_SERVER_ID}，工作空间=${WORKSPACE_ROOT}，会话标题前缀=[E2E]"
