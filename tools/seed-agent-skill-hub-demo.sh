#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="/Users/kaka/Desktop/intelligent-test-agent"
DEMO_ROOT="$PROJECT_ROOT/.tmp/agent-skill-hub-demo"
WORKTREE_ROOT="$DEMO_ROOT/worktree"
PERSONAL_ROOT="$DEMO_ROOT/personal-worktree"
REMOTE_ROOT="$DEMO_ROOT/remote.git"

mkdir -p "$DEMO_ROOT"
if [[ ! -d "$REMOTE_ROOT" ]]; then
  git init --bare "$REMOTE_ROOT"
fi
if [[ ! -d "$WORKTREE_ROOT/.git" ]]; then
  git init -b main "$WORKTREE_ROOT"
  git -C "$WORKTREE_ROOT" config user.name "Hub Demo Fixture"
  git -C "$WORKTREE_ROOT" config user.email "hub-demo@example.invalid"
  git -C "$WORKTREE_ROOT" remote add origin "$REMOTE_ROOT"
fi

if ! git -C "$WORKTREE_ROOT" show-ref --verify --quiet refs/tags/hub-demo-v1; then
  mkdir -p "$WORKTREE_ROOT/.opencode/agents"
  mkdir -p "$WORKTREE_ROOT/.opencode/skills/hub-demo-api-check/templates"
  mkdir -p "$WORKTREE_ROOT/.opencode/skills/hub-demo-unpublished"
  cat > "$WORKTREE_ROOT/.opencode/agents/hub-demo-reviewer.md" <<'EOF'
---
description: Hub 演示评审 Agent（第一版，用于验证发布与更新提醒）。
---
# Hub Demo Reviewer v1

检查接口测试设计是否覆盖正常、异常和边界路径。
EOF
  cat > "$WORKTREE_ROOT/.opencode/skills/hub-demo-api-check/SKILL.md" <<'EOF'
---
name: hub-demo-api-check
description: Hub 演示接口检查 Skill（第一版）。
metadata:
  display-name-zh: Hub 演示接口检查
---
# API Check v1

先读取接口契约，再输出基础检查清单。
EOF
  cat > "$WORKTREE_ROOT/.opencode/skills/hub-demo-api-check/templates/checklist.md" <<'EOF'
- 正常请求
- 参数缺失
- 权限不足
EOF
  cat > "$WORKTREE_ROOT/.opencode/skills/hub-demo-unpublished/SKILL.md" <<'EOF'
---
name: hub-demo-unpublished
description: Hub 演示未发布 Skill，用于验证发布入口。
metadata:
  display-name-zh: Hub 演示待发布 Skill
---
# Unpublished demo
EOF
  git -C "$WORKTREE_ROOT" add .opencode
  git -C "$WORKTREE_ROOT" commit -m "添加 Hub 演示资产第一版"
  git -C "$WORKTREE_ROOT" tag hub-demo-v1
fi

if ! git -C "$WORKTREE_ROOT" show-ref --verify --quiet refs/tags/hub-demo-v2; then
  cat > "$WORKTREE_ROOT/.opencode/agents/hub-demo-reviewer.md" <<'EOF'
---
description: Hub 演示评审 Agent（第二版，增加安全与兼容性检查）。
---
# Hub Demo Reviewer v2

检查接口测试设计是否覆盖正常、异常、边界、安全和向后兼容路径。
EOF
  cat > "$WORKTREE_ROOT/.opencode/skills/hub-demo-api-check/SKILL.md" <<'EOF'
---
name: hub-demo-api-check
description: Hub 演示接口检查 Skill（第二版，增加幂等与限流检查）。
metadata:
  display-name-zh: Hub 演示接口检查
---
# API Check v2

读取接口契约后，输出功能、幂等、限流和权限检查清单。
EOF
  cat > "$WORKTREE_ROOT/.opencode/skills/hub-demo-api-check/templates/checklist.md" <<'EOF'
- 正常请求
- 参数缺失
- 权限不足
- 重复提交与幂等
- 限流与恢复
EOF
  git -C "$WORKTREE_ROOT" add .opencode
  git -C "$WORKTREE_ROOT" commit -m "更新 Hub 演示资产第二版"
  git -C "$WORKTREE_ROOT" tag hub-demo-v2
fi

git -C "$WORKTREE_ROOT" push -u origin main
git -C "$WORKTREE_ROOT" push origin refs/tags/hub-demo-v1 refs/tags/hub-demo-v2

# 单独的个人 worktree 让“当前应用”与取消引用可在真实页面验收，不污染来源 main 工作树。
if [[ ! -e "$PERSONAL_ROOT/.git" ]]; then
  git -C "$WORKTREE_ROOT" worktree add -B feature-hub-demo-user "$PERSONAL_ROOT" main
fi

OLD_COMMIT="$(git -C "$WORKTREE_ROOT" rev-parse hub-demo-v1^{commit})"
NEW_COMMIT="$(git -C "$WORKTREE_ROOT" rev-parse hub-demo-v2^{commit})"

export JAVA_VERSION=25
export JAVA_HOME="$(/usr/libexec/java_home -v "$JAVA_VERSION")"
export PATH="$JAVA_HOME/bin:$PROJECT_ROOT/.tmp/dev-bin:/opt/homebrew/opt/libpq/bin:$PATH"

cd "$PROJECT_ROOT/backend"
mvn -pl test-agent-app -am \
  -Dtest=AgentSkillHubDemoDataFixtureTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtestagent.hub.demo.enabled=true \
  -Dtestagent.hub.demo.env-file="$PROJECT_ROOT/.env.test" \
  -Dtestagent.hub.demo.repo-root="$WORKTREE_ROOT" \
  -Dtestagent.hub.demo.personal-root="$PERSONAL_ROOT" \
  -Dtestagent.hub.demo.remote-root="$REMOTE_ROOT" \
  -Dtestagent.hub.demo.old-commit="$OLD_COMMIT" \
  -Dtestagent.hub.demo.new-commit="$NEW_COMMIT" \
  test

echo "Hub 演示数据已写入：应用=Hub 演示应用，旧提交=${OLD_COMMIT}，新提交=${NEW_COMMIT}"
