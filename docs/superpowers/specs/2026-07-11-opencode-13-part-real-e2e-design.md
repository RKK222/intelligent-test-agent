# OpenCode 1.17.7 全部 Part 真实 E2E 设计

## 背景与官方口径

项目当前使用 OpenCode 1.17.7。经官方 `v1.17.7` tag 的 `packages/sdk/js/src/gen/types.gen.ts` 复核，`Part` 联合类型实际包含 **12 种**：`text`、`subtask`、`reasoning`、`file`、`tool`、`step-start`、`step-finish`、`snapshot`、`patch`、`agent`、`retry`、`compaction`。此前“13 种 Part”是错误计数；Question/Permission 是独立交互协议，不属于 Part。

现有单元测试已经覆盖类型归一化，但不能证明真实 OpenCode、平台后端、历史恢复和浏览器展示的完整链路都无损。本设计建立 12 种官方 Part 的可重复真实 E2E 矩阵，并把 Question/Permission 作为额外强制回归门禁。不得用前端 mock、伪造平台 RunEvent 或只跑组件测试替代真实链路。

## 验收口径

每一种 Part 必须具备以下证据：

1. OpenCode 1.17.7 原生 `/session/{id}/message` 返回目标 Part，保留真实 `sessionID/messageID/partID`。
2. 平台通过既有 runtime 路由读取同一远端会话，Session messages/tree 投影包含目标 Part。
3. 自然触发的类型必须通过真实 RunEvent SSE 接收目标 Part；原生构造的类型明确只验证 OpenCode HTTP、平台 recovery/history，不得声称实时 SSE 已通过。
4. 浏览器真实工作台展示对应 Timeline 行、正文、工具卡或原生 fallback，不能静默丢弃。
5. 切换到新对话后再从历史打开，目标 Part 仍可恢复。
6. 适用的展开、复制或子 Agent 跳转需要验证；不适用交互可标记 N/A，但必须说明原因。

只有 12 行官方 Part 矩阵和额外 Question/Permission 门禁全部有证据，且发现的缺陷已经回归验证，任务才算完成。

## 触发策略

采用用户确认的“方案 2 + 方案 1”：先真实模型自然触发，第一次无法触发时立即改用 OpenCode 原生会话构造。

### 第一优先级：真实模型自然触发

自然触发每项最多等待 45 秒、最多消耗一轮模型请求；目标 Part 未出现在 OpenCode 原始消息响应时立即判定未命中，不重复碰运气。`retry` 最多允许上游重试一次；`compaction` 的上下文准备上限为 50 条短消息或 48,000 字符。

| Part | 首次自然触发操作 | 目标消息和命中判定 |
| --- | --- | --- |
| `text` | 要求只返回唯一标记 `E2E_TEXT_<id>` | root assistant 最新消息含 `type=text` 和唯一标记 |
| `reasoning` | 使用当前支持 reasoning 的模型分析两个固定数字并返回标记 | root assistant 消息含非空 `type=reasoning` |
| `file` | 通过既有 Run prompt parts 上传临时 `e2e-part.txt` | root user 消息含 `type=file`，保留 mime/url/source |
| `tool` | 要求 `read` 读取临时工作区 `README.md` | root assistant 消息含 `type=tool`、`tool=read`、完成态 state |
| `subtask` | 要求 build agent 调用 task/subagent 执行固定只读检查 | 先确认 root task tool；只有 OpenCode API 中真实出现 `type=subtask` 的父消息才算命中，task tool 不能代替 |
| `step-start` | 执行上述 read 工具任务 | 同一 root assistant 步骤消息含 `type=step-start` |
| `step-finish` | 等待上述 read 工具任务完成 | 同一 root assistant 步骤消息含 `type=step-finish` 和 tokens/cost |
| `snapshot` | 要求模型编辑临时工作区文件一次 | 编辑所在 root assistant 消息含 `type=snapshot`；未出现则构造 |
| `patch` | 同一受控编辑任务 | 编辑所在 root assistant 消息含 `type=patch`；未出现则构造 |
| `agent` | prompt part 选择真实可用 Agent，并要求它返回固定标记 | root user/assistant 消息含 `type=agent` 和 name；选择器本身不算命中 |
| `retry` | 测试专属 Provider 仅允许一次可控 429/503 后恢复 | root assistant 消息含 `type=retry`；环境不能安全注入 Provider 时直接构造 |
| `compaction` | 在测试临时会话准备受限上下文后调用既有 summarize/compact API | compact 产生的 user 消息含 `type=compaction`；API 未生成则构造 |

### 第二优先级：OpenCode 原生会话构造

首次自然触发未命中的类型，使用与 OpenCode 1.17.7 SQLite 结构一致的测试夹具写入专用临时会话，再通过 OpenCode 自身 HTTP message API读取。

本地 manager 已把每个进程的数据物理隔离在 `.tmp/dev-services/opencode-manager-session/{port}/opencode/opencode.db`。测试先通过平台创建带唯一 `e2e_part_<runId>` 标题的 Session 和真实远端 mapping，再从 manager state JSON 校验该用户进程的 port/PID，并只打开该 port 对应数据库。数据库路径必须位于项目 `.tmp/dev-services/opencode-manager-session/`；路径不满足时拒绝运行。

夹具复用远端 Session 已有 `project_id/directory/version`，在单个 SQLite transaction 中新增唯一 message/part 行。写入后轮询同一端口的 OpenCode `/session/{remoteId}/message`，只有 HTTP 返回完全相同的 message/part ID 和字段才算构造成功。先以一个探针 Part 验证运行中 SQLite 写入可见；5 秒内 HTTP 不可见时测试失败并保留证据，不直接重启或绕过 manager 修改进程状态。

finally 按顺序通过 OpenCode API删除远端测试 Session、删除平台测试 Session，并查询 SQLite/HTTP确认唯一 `e2e_part_` message/part ID 均为零。构造只允许改测试 Session，禁止改用户历史、用户配置、平台数据库、RunEvent SSE 或 generated SDK。

## 字段级无损矩阵

各层不能只断言 `type`。必须比较以下关键字段；`id/sessionID/messageID` 对全部类型通用。

| Part | 必须逐层一致的关键字段 |
| --- | --- |
| `text` | `text`、`synthetic`、`ignored`、`time`、`metadata` |
| `subtask` | `prompt`、`description`、`agent` |
| `reasoning` | `text`、`time.start/end`、`metadata` |
| `file` | `mime`、`filename`、`url`、`source.type/path/text/range/name/kind`（按 source 变体） |
| `tool` | `callID`、`tool`、`state.status/input/raw/title/output/error/metadata/time/attachments`（按 state 变体） |
| `step-start` | `snapshot` |
| `step-finish` | `reason`、`snapshot`、`cost`、`tokens.input/output/reasoning/cache.read/write` |
| `snapshot` | `snapshot` |
| `patch` | `hash`、`files` |
| `agent` | `name`、`source.value/start/end` |
| `retry` | `attempt`、`error.name/data`、`time.created` |
| `compaction` | `auto` |

OpenCode 原始值、Java/平台投影值、当前浏览器恢复状态和历史浏览器恢复状态必须使用相同测试标记关联；平台模型确实没有承载的字段必须先判定为缺陷，不能静默从断言中删除。

UI 验收不等于 12 类都展示。对照 OpenCode 原生 Web UI，assistant timeline 只直接渲染
`text`、`reasoning`、`tool`、`compaction`；`patch/step-start/step-finish` 在同步层跳过，
`subtask/file/snapshot/agent/retry` 没有 assistant timeline renderer。后八类仍需完成数据层无损验证，
但 UI 应验证不额外产生可见卡片。`file/agent` 在用户输入附件场景的展示不视为 assistant Part 展示。

## 测试架构与证据产物

扩展现有 `frontend/apps/agent-web/tests/workbench.real-spec.ts` 和 `playwright.real.config.ts`，复用 `.env.test` / Spring `test` profile、opencode-manager 用户进程、平台 Workspace/Session/Run/runtime API、Playwright 登录态和临时工作区清理。

每次运行写入 `.tmp/e2e/opencode-parts/<runId>/`：

- `manifest.json`：测试时间、OpenCode 版本、git commit、触发方式、自然触发超时原因、port、PID、session/message/part ID；
- `opencode-raw.json`：OpenCode 原始消息响应；
- `platform-messages.json` 和 `platform-tree.json`：平台响应；
- `run-events.ndjson`：自然触发类型的真实 SSE；
- `current-ui.png`、`history-ui.png` 和 Playwright trace；
- `matrix.json`：12 种 Part 与 Question/Permission 的逐项状态和证据路径。

证据目录保留用于审计，但不得包含 Authorization、Cookie、模型密钥或用户非测试会话内容。

## 数据流

```text
真实模型自然触发
  或测试 Session 内 OpenCode SQLite 原生构造
        ↓
OpenCode 1.17.7 HTTP message API
        ↓
现有 Java runtime facade / session message recovery
        ↓
现有平台 Session messages/tree（自然触发另验 RunEvent SSE）
        ↓
真实浏览器当前会话 Timeline
        ↓
新建对话 → 历史会话恢复 Timeline
```

## 缺陷处理

1. OpenCode 原始消息不存在：记录自然触发未命中，进入原生构造；不修改模型提示路由。
2. OpenCode 有、Java 映射丢失：先给 `OpencodeRunEventMapper` 或 session message facade 增加失败测试，再最小修复。
3. Java 有、平台快照或历史丢失：先给 recovery/snapshot 服务增加失败测试，再修复既有投影路径。
4. 平台有、前端 reducer 丢失：先给 `runtime-reducer` 增加失败测试，再修复归一化。
5. reducer 有、Timeline 不展示：先给 `FigmaChatPanel` / `OpencodeTimeline` 增加失败测试，再修复既有原生 fallback 或对应视图。

所有生产修改遵守 TDD：先复现 RED，再最小实现 GREEN，最后回归。禁止重做聊天视觉、改变原生 Timeline 结构或顺手重构。

## Question/Permission 额外门禁

虽然 Question/Permission 不是 Part，最终仍必须真实验证：当前弹框、切换新对话、历史恢复、Question 提交与忽略、Permission once/always/reject 中至少一次允许和一次拒绝、最终正文显示、Run 终态收敛。Part 映射修复不得破坏这些路径。

## 安全与兼容性

- 不修改 `.env.local`、`.env.test` 或用户 OpenCode 配置。
- 不修改 generated SDK，不新增生产 API、RunEvent wire name、DTO 或数据库结构。
- 原生构造仅使用 manager 隔离数据目录中的测试 Session，测试结束清理并验证。
- 保持未知 Part fallback，新增类型不能导致既有类型被过滤。
- 保留 Huangzhenren 的 Timeline、ask/reasoning/tool/subagent 和终态逻辑。

## 验证与交付

最终至少执行：

1. 12 种官方 Part 的真实 Playwright E2E 矩阵和 Question/Permission 门禁。
2. 所有受影响模块的定向 RED/GREEN 回归测试。
3. 前端对话测试、typecheck、lint、生产 build。
4. 后端受影响 reactor 测试。
5. `.env.test` / `test` profile 重启，health/readiness/frontend 检查。
6. `git diff --check`、冲突标记、generated SDK 未变、原生 Timeline 基线审计。

交付报告逐项列出 12 种 Part 的触发方式、字段断言和证据路径；任何未命中、未展示或未完成的清理都必须明确列为未完成，不能用总体测试通过替代。
