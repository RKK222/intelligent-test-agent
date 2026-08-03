#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

for script in \
  deploy/internal/package-workflow-offline.sh \
  deploy/internal/workflow/analysis-network.sh \
  deploy/internal/workflow/redis-workflow-acl.sh \
  deploy/internal/workflow/workflow-docker.sh; do
  bash -n "${ROOT_DIR}/${script}"
done

build_env="${TMP_DIR}/build.env"
sed \
  -e 's/TEST_AGENT_ANALYSIS_TOOLS_IMAGE_ID=.*/TEST_AGENT_ANALYSIS_TOOLS_IMAGE_ID=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/' \
  "${ROOT_DIR}/deploy/internal/workflow/build.env.example" >"${build_env}"
bash "${ROOT_DIR}/deploy/internal/package-workflow-offline.sh" \
  --env-file "${build_env}" --output-dir "${TMP_DIR}/output" --validate-only

bash "${ROOT_DIR}/deploy/internal/workflow/analysis-network.sh" \
  --env-file "${ROOT_DIR}/deploy/internal/workflow/workflow.env.example" --validate-only

# 浏览器工作流入口必须直达Python upstream；Java upstream不能出现在该location内。
grep -Fq '${TEST_AGENT_WORKFLOW_LOCATION}' "${ROOT_DIR}/deploy/internal/nginx/gateway.conf.template" || {
  echo "Nginx template is missing the direct workflow route" >&2
  exit 1
}
grep -Fq 'proxy_pass http://test_agent_workflow_python;' \
  "${ROOT_DIR}/deploy/internal/configure-nginx.sh"

# 只有Runner控制器获得Docker Socket；API/Worker创建段必须保持无Socket。
control_block="$(sed -n '/^start_control()/,/^}/p' "${ROOT_DIR}/deploy/internal/workflow/workflow-docker.sh")"
if grep -Fq 'docker.sock' <<<"${control_block}"; then
  echo "workflow API/Worker unexpectedly mounts Docker Socket" >&2
  exit 1
fi
runner_block="$(sed -n '/^start_runner()/,/^}/p' "${ROOT_DIR}/deploy/internal/workflow/workflow-docker.sh")"
grep -Fq '/var/run/docker.sock:/var/run/docker.sock' <<<"${runner_block}"

# 分析任务无特权降级，且宿主机链只允许模型网关后拒绝所有其余出站。
if rg -n -- '--privileged|cap-add' "${ROOT_DIR}/runner-controller/src/testagent_runner/docker_runtime.py"; then
  echo "Analysis container contains a privileged fallback" >&2
  exit 1
fi
grep -Fq -- '--cap-drop' "${ROOT_DIR}/runner-controller/src/testagent_runner/docker_runtime.py"
grep -Fq '"10001:10003"' "${ROOT_DIR}/runner-controller/src/testagent_runner/docker_runtime.py"
grep -Fq 'StrictHostKeyChecking=yes' "${ROOT_DIR}/runner-controller/src/testagent_runner/git_workspace.py"
grep -Fq 'GlobalKnownHostsFile=/dev/null' "${ROOT_DIR}/runner-controller/src/testagent_runner/git_workspace.py"
grep -Fq 'O_NOFOLLOW' "${ROOT_DIR}/runner-controller/src/testagent_runner/analyzer_executor.py"
grep -Fq 'task_root / "control" / "operations"' \
  "${ROOT_DIR}/runner-controller/src/testagent_runner/service.py"
grep -Fq '"OPENCODE_CONFIG_CONTENT"' \
  "${ROOT_DIR}/analysis-task/test-agent-analysis.py"
grep -Fq '"enabled_providers": [OPENCODE_PROVIDER_ID]' \
  "${ROOT_DIR}/analysis-task/test-agent-analysis.py"
grep -Fq '"shell": SAFE_OPENCODE_SHELL' \
  "${ROOT_DIR}/analysis-task/test-agent-analysis.py"
grep -Fq 'exec /usr/bin/env -i' \
  "${ROOT_DIR}/analysis-task/test-agent-safe-shell"
grep -Fq 'COPY test-agent-safe-shell /usr/local/bin/test-agent-safe-shell' \
  "${ROOT_DIR}/analysis-task/Dockerfile"
grep -Fq 'COPY test-agent-model-relay.py /usr/local/bin/test-agent-model-relay' \
  "${ROOT_DIR}/analysis-task/Dockerfile"
grep -Fq 'COPY test-agent-clean-output.py /usr/local/bin/test-agent-clean-output' \
  "${ROOT_DIR}/analysis-task/Dockerfile"
grep -Fq '"10002:10002"' \
  "${ROOT_DIR}/runner-controller/src/testagent_runner/model_relay.py"
grep -Fq 'restart_clean' \
  "${ROOT_DIR}/runner-controller/src/testagent_runner/analyzer_executor.py"
grep -Fq '"upstreamGrant": self._upstream_grant' \
  "${ROOT_DIR}/runner-controller/src/testagent_runner/model_relay.py"
if grep -Fq 'safe_payload["modelGrant"]' \
  "${ROOT_DIR}/runner-controller/src/testagent_runner/analyzer_executor.py"; then
  echo "Platform model grant unexpectedly enters the analyzer payload" >&2
  exit 1
fi
grep -Fq 'wire_api = "responses"' \
  "${ROOT_DIR}/analysis-task/test-agent-analysis.py"
grep -Fq 'TEST_AGENT_WORKFLOW_ANALYSIS_MODEL_NAME' \
  "${ROOT_DIR}/deploy/internal/workflow/workflow-docker.sh"
grep -Fq 'workflow-git-known-hosts' "${ROOT_DIR}/deploy/internal/workflow/workflow-docker.sh"
grep -Fq 'iptables -A "${CHAIN}" -j REJECT' \
  "${ROOT_DIR}/deploy/internal/workflow/analysis-network.sh"
grep -Fq 'first_docker_user_rule=' \
  "${ROOT_DIR}/deploy/internal/workflow/analysis-network.sh"

# Redis账号从全拒绝开始，只增加三条命令；SQL使用独立库、owner和运行账号。
grep -Fq "reset on '~test-agent:token:*' -@all +ping +get +pttl" \
  "${ROOT_DIR}/deploy/internal/workflow/redis-workflow-acl.sh"
grep -Fq 'CREATE DATABASE test_agent_workflow OWNER test_agent_workflow_owner' \
  "${ROOT_DIR}/deploy/internal/workflow/bootstrap-workflow.sql"
grep -Fq 'CREATE ROLE test_agent_workflow LOGIN' \
  "${ROOT_DIR}/deploy/internal/workflow/bootstrap-workflow.sql"

# Java只允许窄能力命名，不得出现Python工作流业务表或业务Controller。
if rg -n -i \
  'class Workflow(Conversation|Message|Task|Run|Report|Event).*Controller|create table workflow_(conversations|messages|tasks|runs|reports|events)' \
  "${ROOT_DIR}/backend/test-agent-api/src/main" \
  "${ROOT_DIR}/backend/test-agent-persistence/src/main"; then
  echo "Java contains forbidden workflow business endpoints or tables" >&2
  exit 1
fi
grep -Fq 'class WorkflowCapabilityController' \
  "${ROOT_DIR}/backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/WorkflowCapabilityController.java"

# 场景2~4不能注册成可执行定义；首版注册表只有场景1。
definition_count="$(rg -n 'WorkflowDefinition\(' "${ROOT_DIR}/workflow-service/src/testagent_workflow/workflows" | wc -l | tr -d ' ')"
[[ "${definition_count}" == "1" ]] || {
  echo "Unexpected executable workflow definition count: ${definition_count}" >&2
  exit 1
}
grep -Fq 'code-change-impact-analysis' \
  "${ROOT_DIR}/workflow-service/src/testagent_workflow/workflows/impact_analysis.py"

echo "Workflow architecture, isolation and offline deployment invariants verified"
