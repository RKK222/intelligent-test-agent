# 对话交互场景造数

本项目的对话场景数据放在测试 fixture 和 mock API 中，不写入 Flyway，也不依赖某个本地用户的历史 Session。这样重启服务、切换 worktree 或清理本地数据库后仍可重复构造。

## 场景清单

| 场景 | 可重复入口 | 覆盖内容 |
| --- | --- | --- |
| 直接对话 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`direct run projects remote question and permission to the platform session and replies` | 直接 Run、root remote session 映射、question、permission、回复 |
| 历史运行中继续 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`switching to a running history maps its remote question event and allows reply` | 历史 Session、运行中 SSE、历史 question、继续回复 |
| 历史已结束 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`switching history restores assistant documents and the file changes summary` | 历史消息、assistant 文档、Diff、结束态 |
| 历史置顶与普通会话隔离 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`history drawer pins and unpins sessions through the existing session update API`；`SessionApplicationServiceTest`；`MyBatisSessionHistoryRepositoryIntegrationTest` | 置顶/取消置顶 PATCH、纯置顶保留 `updatedAt`、取消置顶回到原普通组位置、前端即时分组重排、后端置顶优先分页；置顶操作不切换当前会话，普通会话与目标会话的 `aria-current` 和正文互不串线 |
| 历史 permission | `frontend/apps/agent-web/tests/workbench.spec.ts`：`switching history restores a pending native permission dock and allows reply` | 历史 pending 权限弹框、完整 `patterns[]`、中文原生说明、“允许一次”回复 |
| 历史 child permission | `frontend/apps/agent-web/tests/workbench.spec.ts`：`history root permission snapshot keeps child permission attention from the session tree` | 根 permission 快照只替换 root scope、child pending 保留、目标 task “进行中”前铃铛、并行 child 隔离 |
| Todo | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`renders todos above the composer and expands the task list on demand` | Todo 展示、展开详情、composer 位置 |
| 跨 Run Todo 隔离 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`a superseded title-pending run cannot restore its todos into the next turn` | 两轮 Run、旧 `todo.updated/todowrite/snapshot reset` 迟到、标题同步、4/9 项分轮展示 |
| 后端跨 Run 回放隔离 | `backend/test-agent-opencode-runtime/.../RunMessageRecoveryServiceTest.java`、`RunSessionMessageSnapshotServiceTest.java` | 两轮 OpenCode 消息、旧 `todowrite`、dispatch 锚点跨页/冲突/未到达、root/child、终态 `runId` 不覆盖、Session 全量历史 |
| 后端多轮 dispatch 顺序 | `OpencodeMessageIdGeneratorTest.java`、`AgentRuntimeRegistryTest.java`、`OpencodeAgentRuntimeTest.java`、`RunApplicationServiceTest.java` | 真实随机 UUID 故障样本、OpenCode 时序 ID、同毫秒/时钟回退/并发、观测包装委托、Legacy/Redis 锚点复用、显式旧 ID 兼容 |
| 最后一条用户消息撤销重发 | `RunResendTest`、`RunResendControllerSessionShareTest`、`RunResendApplicationServiceTest`、`RunApplicationServiceTest`、`RuntimeDtosCompatibilityTest`、`RunResendAutomaticServiceTest`、`RunResendExecutionServiceTest`、`SessionMessageRealtimeHubTest`、`SessionShareControllerTest`、`MyBatisRunResendRepositoryIntegrationTest`、`runtime-reducer.test.ts`、`FigmaChatPanel.test.ts`、`workbench.spec.ts` 中 `session share sender can edit...` / `session share owner repairs historical...` / `session share owner keeps one...` / `retrying a failed chat run resends...` / `retrying a reopened failed chat...` / `manual resend keeps...`、`workbench.real-spec.ts` | 生产 Spring 装配注入分享服务、仅源消息实际发送人可操作且分享发送人持有 `canChat`、会话所属人与其它成员改写他人消息均拒绝、预留替代 Run 与 USER 消息投影的发送人均不被执行所属人覆盖、旧错误归因由 requester 审计修复、最后轮次/终态/幂等、输入框预填/编辑/取消/失败保留、`editedPrompt` 立即替换原气泡、压缩历史与 assistant 先到时仍按替代 Run/远端消息别名原位接管、迟到 compact Part 按 `partId` 保留在替代轮次之前、原附件与其它 part 保留、1/2/4 分钟与自动 3 次上限、WAITING 停止、会话锁、运行中安全拒绝、revert/稳定消息探测/响应丢失恢复、源明细清理、提交后跨 Java 消息变更信号、前端标签与原子接管、真实文件改动恢复 |
| 撤回重发原位实时同步 | `runtime-reducer.test.ts`：`keeps the source turn as an anchor when resend started arrives before the authoritative user message`；`workbench.spec.ts`：`session share owner keeps one...` / `session share synchronizes an edited resend after the backend commits its message change` | 观察方错过前置 resend 事件时仍保留源 USER 锚点；后端在替代 USER 与 Session 修订提交后即时唤醒当前会话，前端单次读取 `refresh=false` 数据库快照；A/B 均不重载历史树、不闪现 history loading，B 的滚动位置保持不变 |
| 分享运行态与压缩实时同步 | `SessionShareControllerTest`、`OpencodeRuntimeApplicationServiceTest`、`SessionApplicationServiceTest`、`event-stream-client.test.ts`、`opencode-like-state.test.ts`、`opencode-timeline.test.ts`、`FigmaChatPanel.test.ts`、`workbench.spec.ts` 中 `enterprise native slash commands...` / `session share reconciles a disappeared active run...` / `session share refreshes a compacted summary...` | `/compact` 长请求期间持续展示局部收拢动效、阻止并发发送，完成后原位切换成功态且兼容减少动态效果；分享 active Run 消失后按精确 Run 详情收敛终态、解除“思考中”与发送锁，历史/终态 text part 的残留 `running` 不再显示“生成中”；compact 成功推进 `sessionUpdatedAt`、其它参与方自动刷新消息；压缩标记可跨无正文原生 user envelope 及摘要前 reasoning part 与 text 摘要归并，进行态不进入时间线，完成后默认只显示唯一结果按钮、不平铺摘要，点击后仅展开中文摘要 |
| permission | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`renders a pending permission and emits every native permission decision` | OpenCode 1.18.4 中文标题/说明、完整路径、未知类型与旧字段兼容、拒绝/始终允许/允许一次三种决策且不展示内部类型或 request id |
| permission 后端摘要 | `RedisRunRuntimeStoreIntegrationTest`、`MyBatisSessionRuntimeStateRepositoryIntegrationTest`、`MyBatisSessionRuntimeStatePostgresqlIntegrationTest` | Redis question/permission 同 ID 隔离、并发回退、详情容量与终态清理；legacy H2/真实 PostgreSQL 顶层请求 ID、Run seq 因果和 occurredAt 回拨 |
| question | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`renders a single-choice question with option descriptions and emits selected labels` | 单选、多选、选项描述、提交/拒绝 |
| subagent | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`keeps native pending task visible and converts it to a clickable subagent card` | task part、child Session、子 Agent 卡片和点击进入 |
| 历史 subagent | `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`：`makes historical subagent cards clickable from session tree snapshot indexes` | 历史树恢复、子 Agent 导航、子时间线 |
| UI 执行 subagent | 公共配置 `test-execution-ui.md` / `ui_test_execute.ts` 与独立 UI 平台契约测试 | 必填被测系统环境、单行四列传递、缺环境中断、Tool 直连、一次提交、同 executionId 轮询和终态结果 |
| 宠物旁路成功 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`pet side-question streams progress, survives outside clicks, and calibrates replayed deltas` |旁路 Run、阶段进度、增量、最终答案、重放去重 |
| 宠物旁路失败/重试 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`pet side-question keeps a failure editable and starts a fresh run on retry` | 失败弹层、问题保留、重新提交 |
| 宠物形象策略 | `frontend/apps/agent-web/tests/pet-companions.test.ts` 与 `FigmaShell.test.ts`：`lets the user choose a companion and persists the selected mode` | 本地日期轮换、每日随机稳定、异常存储回退、固定角色与名册交互 |
| 会话协作分享管理 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`session share management and received list preserve one link and inactive history` | 我的会话/分享给我、有效分享以新标签页打开且原工作台不跳转、唯一链接、成员与可对话权限、1/3/7 天、待执行任务提示、取消与失效历史 |
| 会话协作分享工作台 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`session share read-only workbench shows sender identity colors and fixed scope`、`session share model picker selects from the fixed owner workspace catalog`、`session share busy run blocks every participant and only sender can stop`、`session share participant receives the owner's authoritative user message without an empty bubble`、`session share owner history keeps a shared actor run snapshot when history enrichment arrives late` | 固定 Session/Workspace、分享头、所属人工作区模型目录与模型选择、只读限权、分享页新建对话返回本人工作台、发送人姓名/固定两色、运行互斥、非发送人停止限制、空远端 envelope 不展示、平台 USER 正文实时原位归并，以及所属人中途打开被分享人发起的活动 Run 时对思考、停止按钮和工具事件的无覆盖同步 |
| 会话协作分享失效与所属人路由 | `frontend/apps/agent-web/tests/workbench.spec.ts`：`session share invalid page and owner link redirect remain isolated` | 移除/过期/取消提示、所属人回普通工作台、兼容旧 `/s/{sessionId}`、分享头隔离 |
| 会话协作分享后端 | `SessionShareControllerTest`、`SessionCollaborationShareServiceTest`、`DelegatedOperationContextResolverTest`、`MyBatisSessionShareRepositoryIntegrationTest`、`ConversationContextControllerTest`、`RunControllerSessionShareTest`、`ManagedWorkspaceControllerSessionShareTest`、`WorkspaceControllerSessionShareTest` | 唯一 256 位链接、50 人/7 天上限、乐观锁、代操作边界、分享 RunContext 使用所属人执行身份、跨工作区拒绝、文件/终端票据、审计脱敏 |

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
  --grep 'direct run projects|history drawer pins|running history maps|pending native question|pending native permission|history root permission snapshot|retrying a failed chat run resends|manual retry refuses|manual resend keeps|superseded title-pending run|pet side-question'
```

会话卡片主按钮与置顶按钮拥有包含同一标题的可访问名称。端到端测试切换历史会话时统一限定
`.figma-chat-history-card-main`，置顶操作再按“置顶对话/取消置顶对话”可访问名称定位，避免模糊标题选择器
同时命中两个按钮而把测试基础设施问题误报为会话功能失败。

会话协作分享额外使用 Chromium、Firefox 和 WebKit 三浏览器固定回归：

```bash
cd /Users/huang/workspace/intelligent-test-agent-gitee/frontend
corepack pnpm e2e:session-share
```

首次执行前如本机未安装对应 Playwright 运行包，先执行
`corepack pnpm exec playwright install chromium firefox webkit`。三浏览器配置只筛选标题以
`session share` 开头的专项场景，不扩大现有全量桌面 E2E 范围。

## 真实 OpenCode 三轮回复验收

修复消息排序或 Run dispatch 锚点后，必须重启测试环境并新建 Session，连续发送至少三轮普通对话。正常前端不显式传 `messageId`，每轮远端 USER ID 应匹配 `msg_[0-9a-f]{12}[0-9A-Za-z]{14}`；第二、三轮 USER ID 必须按字典序大于前一轮最后 assistant ID。

每轮都要从原始事件或消息接口确认对应 USER、至少一个新的 ASSISTANT message/part，以及最后的 root idle / `run.succeeded`。第二、三轮不得在任何 assistant 输出前直接 `busy → idle → run.succeeded`。包含 Todo 的提示还要确认 Todo 只归属各自 Run；最后刷新 Session 消息接口时应恢复三轮全部 user/assistant，不能把旧轮 parts 重新归给最新 Run。验收只覆盖部署后的新 Run，不迁移已经产生空回复的历史 Run。

## 真实 OpenCode 撤销重发验收

启动 PostgreSQL、Redis、backend、opencode-manager、用户 OpenCode 与前端后，使用专用测试用户执行：

```bash
cd frontend
TEST_AGENT_RUN_REAL_E2E=1 TEST_AGENT_API_TOKEN='<专用测试 Token>' \
  corepack pnpm exec playwright test --config=playwright.real.config.ts --grep 'native resend'
```

用例只复用一个已有 ACTIVE 工作区并创建独立测试 Session。首轮模型在工作区创建唯一 marker 文件；手动重发经每分钟已注册夜间调度入口执行原生 revert，必须确认旧 USER、旧 ASSISTANT 和工具明细从 OpenCode 会话消失，marker 被回退为不存在，替代轮使用新 USER message ID 再次创建 marker 并成功。平台刷新后不得恢复源 Run 明细，只展示替代 Run；finally 必须删除原生 Session、归档平台 Session 并删除唯一 marker。测试 Token 只通过进程环境传入，不写日志、源码或数据库。

每个入口内都包含可直接复用的 prompt、RunEvent、远端消息/Part、Todo、pending question/permission、子 Agent 树和旁路 SSE 回包；这些 fixture 是“可重复造数模板”，不会污染生产数据库。需要验证真实 OpenCode pending request 时，应在服务重启后重新发起对应 prompt；OpenCode 的 question/permission request 属于进程内存态，重启前 requestId 不能继续回复。历史回放只展示当前远端仍 pending 的交互，已经失效的旧事件会被过滤；permission/question 列表按绑定的 remote session 过滤，不会把 A Session 的 ask 泄漏到 B Session。Todo 必须同时验证 Run ID、用户消息 ID 和 root/child scope，无法归属的 session 快照不得赋给最新轮。后端 Run 级 SSE/HTTP/终态快照必须用稳定 dispatch user 选择当前轮，无法确认时返回空投影；Session 级历史仍保留完整多轮，且普通刷新不能改写已有 `runId`。验收应使用修复后新建会话，已污染的历史数据不做懒修复或迁移。历史切换先渲染分页正文，树快照、Todo、工作区目录和 active-run 终态校准在后台增强。

## UI 子 agent 真实验收

先由超级管理员在“系统管理 → 通用参数管理”把 `UITEST_BASE_URL` 设置为 worker 可达的独立 UI
平台地址；无需重启 Manager 或当前用户 OpenCode 进程。在对话中直接 `@test-execution-ui`，提供被测
系统环境和一行案例：

```text
被测系统环境：F-COSS SIT，https://sit.example.test
案例名称：功能测试-登录-正常登录-首页展示
测试步骤：1. 打开登录页；2. 输入用户名和密码；3. 点击登录
测试数据：用户名=tester；密码=<测试账号密码>
预期结果：进入首页并显示欢迎语
```

环境也可以由用户明确指定材料或文件路径，再由父 `test-execution-agent` 使用现有读取能力取得原文后
传给 UI 子 agent；UI 子 agent 不增加文件权限。只提供路径但未成功读取、环境为空或未提供环境时，
必须返回 `BLOCKED` 且不得出现 `ui_test_execute` 调用。

验收必须同时确认：环境文本与四列案例均原样到达；子 agent 只调用一次 `ui_test_execute`；Tool
先读取最新地址，再直连独立 UI 平台且只有一个创建执行的 POST；后续请求均查询同一个 `executionId`；
终态与独立 UI 平台一致。修改通用参数后还需记录 OpenCode PID，确认不重启同一进程时下一次调用已命中
新地址。没有可用的独立 UI 平台真实环境时只能报告
契约测试通过，不能声称真实浏览器自动化已执行。
