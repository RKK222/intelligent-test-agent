#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

require_file() {
  local file="$1"
  [[ -f "${ROOT_DIR}/${file}" ]] || fail "missing ${file}"
}

require_text() {
  local file="$1"
  local text="$2"
  grep -Fq "${text}" "${ROOT_DIR}/${file}" || fail "${file} does not contain: ${text}"
}

required_files=(
  "AGENTS.md"
  "CLAUDE.md"
  "docs/README.md"
  "docs/development/ai-coding-rules.md"
  "docs/development/task-workflow.md"
  "docs/development/ai-self-checklist.md"
  "docs/backend/backend-coding-standards.md"
  "docs/backend/backend-testing-standards.md"
  "docs/backend/backend-performance-standards.md"
  "docs/backend/error-handling-standards.md"
  "docs/backend/observability-standards.md"
  "docs/backend/data-change-standards.md"
  "docs/api/backend-api.md"
  "docs/api/event-stream-api.md"
  "docs/architecture/dependency-rules.md"
  "docs/security/security-standards.md"
  "docs/frontend/frontend-requirements.md"
  "docs/frontend/frontend-architecture.md"
  "docs/frontend/frontend-coding-standards.md"
  "docs/frontend/frontend-testing-standards.md"
  "docs/frontend/frontend-performance-standards.md"
  "docs/frontend/frontend-backend-contract.md"
  "docs/plan/00-roadmap.md"
  "docs/plan/01-backend-domain-and-contracts.md"
  "docs/plan/02-persistence-and-routing.md"
  "docs/plan/03-opencode-client-and-events.md"
  "docs/plan/04-backend-api-runtime.md"
  "docs/plan/05-local-integration-and-devops.md"
  "docs/plan/06-frontend-foundation.md"
  "docs/plan/07-workbench-shell-and-files.md"
  "docs/plan/08-agent-chat-diff-and-run-mvp.md"
  "docs/plan/09-test-reports-and-skill-studio.md"
  "docs/plan/10-e2e-hardening-and-release.md"
)

for file in "${required_files[@]}"; do
  require_file "${file}"
done

package_docs=(
  "backend/test-agent-app/src/main/java/com/example/testagent/app/PACKAGE.md"
  "backend/test-agent-common/src/main/java/com/example/testagent/common/PACKAGE.md"
  "backend/test-agent-domain/src/main/java/com/example/testagent/domain/PACKAGE.md"
  "backend/test-agent-event/src/main/java/com/example/testagent/event/PACKAGE.md"
  "backend/test-agent-observability/src/main/java/com/example/testagent/observability/PACKAGE.md"
  "backend/test-agent-opencode-client/src/main/java/com/example/testagent/opencode/client/PACKAGE.md"
  "backend/test-agent-persistence/src/main/java/com/example/testagent/persistence/PACKAGE.md"
  "backend/test-agent-test-support/src/main/java/com/example/testagent/testsupport/PACKAGE.md"
)

for file in "${package_docs[@]}"; do
  require_file "${file}"
  require_text "${file}" "## 职责"
  require_text "${file}" "## 主要程序清单"
  require_text "${file}" "## 允许依赖"
  require_text "${file}" "## 禁止依赖"
  require_text "${file}" "## 上游调用方"
  require_text "${file}" "## 下游依赖"
  require_text "${file}" "## 修改时必须同步更新"
done

require_text "AGENTS.md" "只改与任务直接相关的最小范围"
require_text "AGENTS.md" "generated SDK 不能手改"
require_text "AGENTS.md" "API 必须有文档"
require_text "docs/development/ai-coding-rules.md" "先读"
require_text "docs/development/ai-self-checklist.md" "兼容性"
require_text "docs/backend/backend-testing-standards.md" "改什么补什么测试"
require_text "docs/backend/backend-performance-standards.md" "SSE"
require_text "docs/backend/data-change-standards.md" "Flyway migration"
require_text "docs/backend/error-handling-standards.md" "统一错误格式"
require_text "docs/backend/observability-standards.md" "traceId"
require_text "docs/security/security-standards.md" "日志脱敏"
require_text "docs/architecture/dependency-rules.md" "Controller 不得直接调用 generated SDK"
require_text "docs/api/backend-api.md" "所有对外 API 新增或变更都必须更新本文件"
require_text "docs/api/event-stream-api.md" "Last-Event-ID"
require_text "docs/frontend/frontend-architecture.md" "完全自研"
require_text "docs/frontend/frontend-architecture.md" "前端不得直连 opencode server"
require_text "docs/frontend/frontend-architecture.md" "RunEvent SSE"
require_text "docs/frontend/frontend-coding-standards.md" '只能通过 `packages/backend-api`'
require_text "docs/frontend/frontend-coding-standards.md" "不得直连 opencode server"
require_text "docs/frontend/frontend-backend-contract.md" "RunEvent SSE"
require_text "docs/frontend/frontend-backend-contract.md" '通过 `packages/backend-api`'
require_text "docs/plan/00-roadmap.md" "完全自研"
require_text "docs/plan/06-frontend-foundation.md" "Next.js"
require_text "docs/plan/08-agent-chat-diff-and-run-mvp.md" "DiffActionCard"

if find "${ROOT_DIR}/backend/test-agent-opencode-sdk-generated/src/main/java" -name PACKAGE.md | grep -q .; then
  fail "generated SDK source tree must not contain PACKAGE.md"
fi

echo "AI documentation verification passed."
