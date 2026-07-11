# 对话交互场景造数

本项目的对话场景数据放在测试 fixture 和 mock API 中，不写入 Flyway，也不依赖某个本地用户的历史 Session。这样重启服务、切换 worktree 或清理本地数据库后仍可重复构造。

## 场景清单

| 场景 | 可重复入口 | 覆盖内容 |
| --- | --- | --- |
| 直接对话 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`direct run projects remote question and permission to the platform session and replies` | 直接 Run、root remote session 映射、question、permission、回复 |
| 历史运行中继续 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`switching to a running history maps its remote question event and allows reply` | 历史 Session、运行中 SSE、历史 question、继续回复 |
| 历史已结束 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`switching history restores assistant documents and the file changes summary` | 历史消息、assistant 文档、Diff、结束态 |
| 历史 permission | `frontend/apps/agent-web/tests/workbench.spec.ts`：`switching history restores a pending native permission dock and allows reply` | 历史 pending 权限弹框、详情、once 回复 |
| Todo | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`renders todos above the composer and expands the task list on demand` | Todo 展示、展开详情、composer 位置 |
| permission | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`renders a pending permission and emits every native permission decision` | once/always/reject 三种决策 |
| question | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`renders a single-choice question with option descriptions and emits selected labels` | 单选、多选、选项描述、提交/拒绝 |
| subagent | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`keeps native pending task visible and converts it to a clickable subagent card` | task part、child Session、子 Agent 卡片和点击进入 |
| 历史 subagent | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`makes historical subagent cards clickable from session tree snapshot indexes` | 历史树恢复、子 Agent 导航、子时间线 |
| 宠物旁路成功 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`pet side-question streams progress, survives outside clicks, and calibrates replayed deltas` |旁路 Run、阶段进度、增量、最终答案、重放去重 |
| 宠物旁路失败/重试 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`pet side-question keeps a failure editable and starts a fresh run on retry` | 失败弹层、问题保留、重新提交 |

## 一次性复现全部前端场景

```bash
cd /Users/kaka/Desktop/intelligent-test-agent/frontend
corepack pnpm test --run \
  apps/agent-web/tests/workbench-utils.test.ts \
  apps/agent-web/tests/FigmaChatPanel.test.ts \
  apps/agent-web/tests/useSideQuestionRun.test.ts \
  packages/agent-chat/tests/runtime-reducer.test.ts \
  packages/agent-chat/tests/opencode-timeline.test.ts
corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts \
  --grep 'direct run projects|running history maps|pending native question|pending native permission|pet side-question'
```

每个入口内都包含可直接复用的 prompt、RunEvent、远端消息/Part、Todo、pending question/permission、子 Agent 树和旁路 SSE 回包；这些 fixture 是“可重复造数模板”，不会污染生产数据库。需要验证真实 OpenCode pending request 时，应在服务重启后重新发起对应 prompt；OpenCode 的 question/permission request 属于进程内存态，重启前 requestId 不能继续回复。历史回放只展示当前远端仍 pending 的交互，已经失效的旧事件会被过滤。
