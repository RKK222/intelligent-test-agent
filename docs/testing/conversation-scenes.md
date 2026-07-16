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
| 跨 Run Todo 隔离 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`a superseded title-pending run cannot restore its todos into the next turn` | 两轮 Run、旧 `todo.updated/todowrite/snapshot reset` 迟到、标题同步、4/9 项分轮展示 |
| 后端跨 Run 回放隔离 | `backend/test-agent-opencode-runtime/.../RunMessageRecoveryServiceTest.java`、`RunSessionMessageSnapshotServiceTest.java` | 两轮 OpenCode 消息、旧 `todowrite`、dispatch 锚点跨页/冲突/未到达、root/child、终态 `runId` 不覆盖、Session 全量历史 |
| 后端多轮 dispatch 顺序 | `OpencodeMessageIdGeneratorTest.java`、`AgentRuntimeRegistryTest.java`、`OpencodeAgentRuntimeTest.java`、`RunApplicationServiceTest.java` | 真实随机 UUID 故障样本、OpenCode 时序 ID、同毫秒/时钟回退/并发、观测包装委托、Legacy/Redis 锚点复用、显式旧 ID 兼容 |
| permission | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`renders a pending permission and emits every native permission decision` | once/always/reject 三种决策 |
| question | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`renders a single-choice question with option descriptions and emits selected labels` | 单选、多选、选项描述、提交/拒绝 |
| subagent | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`keeps native pending task visible and converts it to a clickable subagent card` | task part、child Session、子 Agent 卡片和点击进入 |
| 历史 subagent | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`makes historical subagent cards clickable from session tree snapshot indexes` | 历史树恢复、子 Agent 导航、子时间线 |
| 宠物旁路成功 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`pet side-question streams progress, survives outside clicks, and calibrates replayed deltas` |旁路 Run、阶段进度、增量、最终答案、重放去重 |
| 宠物旁路失败/重试 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`pet side-question keeps a failure editable and starts a fresh run on retry` | 失败弹层、问题保留、重新提交 |
| 宠物形象策略 | `frontend/apps/agent-web/tests/pet-companions.test.ts` 与 `FigmaShell.test.ts`：`lets the user choose a companion and persists the selected mode` | 本地日期轮换、每日随机稳定、异常存储回退、固定角色与名册交互 |

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
  --grep 'direct run projects|running history maps|pending native question|pending native permission|superseded title-pending run|pet side-question'
```

## 真实 OpenCode 三轮回复验收

修复消息排序或 Run dispatch 锚点后，必须重启测试环境并新建 Session，连续发送至少三轮普通对话。正常前端不显式传 `messageId`，每轮远端 USER ID 应匹配 `msg_[0-9a-f]{12}[0-9A-Za-z]{14}`；第二、三轮 USER ID 必须按字典序大于前一轮最后 assistant ID。

每轮都要从原始事件或消息接口确认对应 USER、至少一个新的 ASSISTANT message/part，以及最后的 root idle / `run.succeeded`。第二、三轮不得在任何 assistant 输出前直接 `busy → idle → run.succeeded`。包含 Todo 的提示还要确认 Todo 只归属各自 Run；最后刷新 Session 消息接口时应恢复三轮全部 user/assistant，不能把旧轮 parts 重新归给最新 Run。验收只覆盖部署后的新 Run，不迁移已经产生空回复的历史 Run。

每个入口内都包含可直接复用的 prompt、RunEvent、远端消息/Part、Todo、pending question/permission、子 Agent 树和旁路 SSE 回包；这些 fixture 是“可重复造数模板”，不会污染生产数据库。需要验证真实 OpenCode pending request 时，应在服务重启后重新发起对应 prompt；OpenCode 的 question/permission request 属于进程内存态，重启前 requestId 不能继续回复。历史回放只展示当前远端仍 pending 的交互，已经失效的旧事件会被过滤；permission/question 列表按绑定的 remote session 过滤，不会把 A Session 的 ask 泄漏到 B Session。Todo 必须同时验证 Run ID、用户消息 ID 和 root/child scope，无法归属的 session 快照不得赋给最新轮。后端 Run 级 SSE/HTTP/终态快照必须用稳定 dispatch user 选择当前轮，无法确认时返回空投影；Session 级历史仍保留完整多轮，且普通刷新不能改写已有 `runId`。验收应使用修复后新建会话，已污染的历史数据不做懒修复或迁移。历史切换先渲染分页正文，树快照、Todo、工作区目录和 active-run 终态校准在后台增强。
