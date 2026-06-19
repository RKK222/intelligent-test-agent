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
  "tools/dev-backend-run.sh"
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
  "docs/deployment/backend-docker-deployment.md"
  "docs/security/security-standards.md"
  "docs/frontend/frontend-requirements.md"
  "docs/frontend/frontend-architecture.md"
  "docs/frontend/frontend-coding-standards.md"
  "docs/frontend/frontend-testing-standards.md"
  "docs/frontend/frontend-performance-standards.md"
  "docs/frontend/frontend-backend-contract.md"
  "docs/design/00-roadmap-design.md"
  "docs/design/01-backend-domain-and-contracts-design.md"
  "docs/design/02-persistence-and-routing-design.md"
  "docs/design/03-opencode-client-and-events-design.md"
  "docs/design/04-backend-api-runtime-design.md"
  "docs/design/05-local-integration-and-devops-design.md"
  "docs/design/06-frontend-foundation-design.md"
  "docs/design/07-workbench-shell-and-files-design.md"
  "docs/design/08-agent-chat-diff-and-run-mvp-design.md"
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
  "frontend/README.md"
  "frontend/apps/agent-web/README.md"
  "frontend/apps/agent-web/src/PACKAGE.md"
  "frontend/packages/backend-api/README.md"
  "frontend/packages/backend-api/src/PACKAGE.md"
  "frontend/packages/event-stream-client/README.md"
  "frontend/packages/event-stream-client/src/PACKAGE.md"
  "frontend/packages/workbench-shell/README.md"
  "frontend/packages/workbench-shell/src/PACKAGE.md"
  "frontend/packages/file-explorer/README.md"
  "frontend/packages/file-explorer/src/PACKAGE.md"
  "frontend/packages/editor/README.md"
  "frontend/packages/editor/src/PACKAGE.md"
  "frontend/packages/diff-viewer/README.md"
  "frontend/packages/diff-viewer/src/PACKAGE.md"
  "frontend/packages/agent-chat/README.md"
  "frontend/packages/agent-chat/src/PACKAGE.md"
  "frontend/packages/test-runner/README.md"
  "frontend/packages/test-runner/src/PACKAGE.md"
  "frontend/packages/ui-kit/README.md"
  "frontend/packages/ui-kit/src/PACKAGE.md"
  "frontend/packages/shared-types/README.md"
  "frontend/packages/shared-types/src/PACKAGE.md"
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
require_text "docs/deployment/backend-docker-deployment.md" "后端 Java 进程"
require_text "docs/deployment/backend-docker-deployment.md" "PostgreSQL、Redis 和 opencode server 都是外部服务"
require_text "docs/deployment/backend-docker-deployment.md" "tools/dev-backend-run.sh"
require_text "backend/README.md" "tools/dev-backend-run.sh"
require_text "backend/test-agent-app/README.md" ".env.local"
require_text "docs/api/backend-api.md" "所有对外 API 新增或变更都必须更新本文件"
require_text "docs/api/event-stream-api.md" "Last-Event-ID"
require_text "docs/frontend/frontend-architecture.md" "完全自研"
require_text "docs/frontend/frontend-architecture.md" "前端不得直连 opencode server"
require_text "docs/frontend/frontend-architecture.md" "RunEvent SSE"
require_text "docs/frontend/frontend-coding-standards.md" '只能通过 `packages/backend-api`'
require_text "docs/frontend/frontend-coding-standards.md" "不得直连 opencode server"
require_text "docs/frontend/frontend-backend-contract.md" "RunEvent SSE"
require_text "docs/frontend/frontend-backend-contract.md" '通过 `packages/backend-api`'
require_text "docs/frontend/frontend-backend-contract.md" "diff.accepted"
require_text "docs/frontend/frontend-backend-contract.md" "diff.rejected"
require_text "docs/plan/00-roadmap.md" "完全自研"
require_text "docs/plan/06-frontend-foundation.md" "Next.js"
require_text "docs/plan/08-agent-chat-diff-and-run-mvp.md" "DiffActionCard"
require_text "frontend/README.md" "corepack pnpm"
require_text "frontend/README.md" "不得直连 opencode server"
require_text "frontend/apps/agent-web/README.md" "Dockview"
require_text "frontend/packages/backend-api/src/PACKAGE.md" "opencode server"
require_text "frontend/packages/event-stream-client/src/PACKAGE.md" "RunEvent SSE"

if find "${ROOT_DIR}/backend/test-agent-opencode-sdk-generated/src/main/java" -name PACKAGE.md | grep -q .; then
  fail "generated SDK source tree must not contain PACKAGE.md"
fi

echo "AI documentation verification passed."
