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
  "tools/verify-opencode-process-deployment.sh"
  "docs/README.md"
  "docs/guides/ai-workflow.md"
  "docs/guides/self-checklist.md"
  "docs/standards/backend.md"
  "docs/standards/frontend.md"
  "docs/standards/security.md"
  "docs/api/http-api.md"
  "docs/api/event-stream.md"
  "docs/architecture/dependency-rules.md"
  "docs/architecture/module-map.md"
  "docs/deployment/backend.md"
  "docs/deployment/database.md"
  "docs/deployment/frontend.md"
  "deploy/internal/FULL-UPGRADE-RUNBOOK.md"
  ".agents/skills/enterprise-offline-deploy/SKILL.md"
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
  "frontend/packages/terminal/README.md"
  "frontend/packages/terminal/src/PACKAGE.md"
  "frontend/packages/ui-kit/README.md"
  "frontend/packages/ui-kit/src/PACKAGE.md"
  "frontend/packages/shared-types/README.md"
  "frontend/packages/shared-types/src/PACKAGE.md"
)

for file in "${required_files[@]}"; do
  require_file "${file}"
done

package_docs=(
  "backend/test-agent-api/src/main/java/com/enterprise/testagent/api/PACKAGE.md"
  "backend/test-agent-app/src/main/java/com/enterprise/testagent/app/PACKAGE.md"
  "backend/test-agent-common/src/main/java/com/enterprise/testagent/common/PACKAGE.md"
  "backend/test-agent-domain/src/main/java/com/enterprise/testagent/domain/PACKAGE.md"
  "backend/test-agent-event/src/main/java/com/enterprise/testagent/event/PACKAGE.md"
  "backend/test-agent-integration/src/main/java/com/enterprise/testagent/integration/PACKAGE.md"
  "backend/test-agent-observability/src/main/java/com/enterprise/testagent/observability/PACKAGE.md"
  "backend/test-agent-opencode-client/src/main/java/com/enterprise/testagent/opencode/client/PACKAGE.md"
  "backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/PACKAGE.md"
  "backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/PACKAGE.md"
  "backend/test-agent-system-management/src/main/java/com/enterprise/testagent/system/management/PACKAGE.md"
  "backend/test-agent-test-support/src/main/java/com/enterprise/testagent/testsupport/PACKAGE.md"
  "backend/test-agent-workspace-management/src/main/java/com/enterprise/testagent/workspace/PACKAGE.md"
)

for file in "${package_docs[@]}"; do
  require_file "${file}"
  require_text "${file}" "## 职责"
  require_text "${file}" "## 修改时必须同步更新"
done

require_text "AGENTS.md" "只改与任务直接相关的最小范围"
require_text "AGENTS.md" "generated SDK 不能手改"
require_text "AGENTS.md" "API 必须有文档"
require_text "docs/guides/ai-workflow.md" "先读"
require_text "docs/guides/self-checklist.md" "兼容性"
require_text "docs/standards/backend.md" "改什么补什么测试"
require_text "docs/standards/backend.md" "SSE"
require_text "docs/standards/backend.md" "Flyway migration"
require_text "docs/standards/backend.md" "统一错误格式"
require_text "docs/standards/backend.md" "traceId"
require_text "docs/standards/security.md" "日志脱敏"
require_text "docs/architecture/dependency-rules.md" "Controller 不得直接调用 generated SDK"
require_text "docs/deployment/backend.md" "后端 Java 进程"
require_text "docs/deployment/backend.md" "PostgreSQL、XXL MySQL、Redis 和 opencode server 都是外部服务"
require_text "docs/deployment/backend.md" "tools/dev-backend-run.sh"
require_text "docs/deployment/backend.md" "tools/verify-opencode-process-deployment.sh"
require_text "docs/deployment/backend.md" "多服务器用户进程拓扑规划"
require_text "docs/deployment/database.md" "V10 opencode 用户进程管理表"
require_text "deploy/internal/FULL-UPGRADE-RUNBOOK.md" '中转机不创建、不使用 `/data/0709`'
require_text "deploy/internal/FULL-UPGRADE-RUNBOOK.md" 'cd ~/Desktop/mimoagent/0709'
require_text "deploy/internal/FULL-UPGRADE-RUNBOOK.md" 'OPENCODE_MANAGER_MAX_PROCESSES` 改为 `1000'
require_text "deploy/internal/FULL-UPGRADE-RUNBOOK.md" '运行容器时不传 `--platform`'
require_text ".agents/skills/enterprise-offline-deploy/SKILL.md" '目标机执行 `docker run` 时不得再强制传 `--platform`'
require_text "docs/standards/security.md" "用户专属 opencode server 默认监听"
require_text "backend/README.md" "Maven multi-module"
require_text "backend/test-agent-app/README.md" ".env.local"
require_text "docs/api/http-api.md" "所有对外 API 新增或变更都必须更新本文件"
require_text "docs/api/event-stream.md" "Last-Event-ID"
require_text "docs/standards/frontend.md" "完全自研"
require_text "docs/standards/frontend.md" "不得直连 opencode server"
require_text "docs/standards/frontend.md" "RunEvent SSE"
require_text "docs/standards/frontend.md" '只能通过 `packages/backend-api`'
require_text "docs/architecture/module-map.md" "RunEvent SSE"
require_text "docs/architecture/module-map.md" '`packages/backend-api` 是前端访问后端的唯一入口'
require_text "docs/api/event-stream.md" "diff.accepted"
require_text "docs/api/event-stream.md" "diff.rejected"
require_text "frontend/README.md" "corepack pnpm"
require_text "frontend/README.md" "不得直连 opencode server"
require_text "frontend/apps/agent-web/README.md" "dockview-vue"
require_text "frontend/packages/backend-api/src/PACKAGE.md" "opencode server"
require_text "frontend/packages/event-stream-client/src/PACKAGE.md" "RunEvent SSE"
require_text "frontend/packages/terminal/src/PACKAGE.md" "opencode server"

if find "${ROOT_DIR}/backend/test-agent-opencode-sdk-generated/src/main/java" -name PACKAGE.md | grep -q .; then
  fail "generated SDK source tree must not contain PACKAGE.md"
fi

echo "AI documentation verification passed."
