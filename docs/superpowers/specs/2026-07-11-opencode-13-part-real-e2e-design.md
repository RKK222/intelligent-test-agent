# OpenCode 13 种 Part 真实 E2E 设计

## 背景

项目当前使用 OpenCode 1.17.7。官方 `Part` 联合类型包含 13 种：`text`、`subtask`、`reasoning`、`file`、`tool`、`step-start`、`step-finish`、`snapshot`、`patch`、`agent`、`retry`、`compaction`。现有单元测试已经覆盖类型归一化，但不能证明真实 OpenCode、平台后端、历史恢复和浏览器展示的完整链路都无损。

本设计建立可重复的真实 E2E 矩阵，并在发现问题时按项目规范直接修复。不得用前端 mock、伪造平台 RunEvent 或只跑组件测试替代真实链路。

## 验收口径

每一种 Part 必须同时具备以下证据：

1. OpenCode 1.17.7 原生会话消息接口返回目标 Part，保留真实 `sessionID/messageID/partID`。
2. 平台通过既有 runtime 路由读取同一远端会话，Session messages/tree 投影包含目标 Part。
3. 当前会话通过真实 RunEvent SSE 或消息恢复链路接收目标 Part。
4. 浏览器真实工作台展示目标 Part 对应的 Timeline 行、正文、工具卡或原生 fallback，不能静默丢弃。
5. 切换到新对话后再从历史打开，目标 Part 仍可恢复。
6. 不适用交互允许标记 N/A，但必须说明原因；适用的展开、复制或子 Agent 跳转需要验证。

只有 13 行矩阵全部有上述证据，且发现的缺陷已经回归验证，任务才算完成。

## 触发策略

采用“方案 2 + 方案 1”的顺序。

### 第一优先级：真实模型自然触发

每种 Part 先执行一次明确、可审计的真实模型场景：

| Part | 首次自然触发场景 |
| --- | --- |
| `text` | 要求只返回固定标记文本 |
| `reasoning` | 使用支持 reasoning 的当前模型完成简单分析 |
| `file` | 发送真实文本或图片附件 |
| `tool` | 调用 `read` 读取工作区文件 |
| `subtask` | 调用 task/subagent 并等待子会话结果 |
| `step-start` / `step-finish` | 完成一轮包含模型步骤和工具调用的任务 |
| `snapshot` / `patch` | 要求编辑受控临时工作区文件并检查 OpenCode 产生的原生变更 Part |
| `agent` | 显式使用 Agent 选择或原生 agent Part 场景 |
| `retry` | 使用可控的临时失败场景观察 provider retry |
| `compaction` | 构造超过上下文阈值的临时会话并调用原生 compact/summarize |

每种类型只进行一次自然触发尝试。未出现目标 Part 时记录“自然触发未命中”和原始消息证据，不重复消耗模型进行碰运气。

### 第二优先级：OpenCode 原生会话构造

首次自然触发未命中的类型，使用与当前 OpenCode 1.17.7 存储结构一致的测试夹具写入专用临时会话，再通过 OpenCode 自身 HTTP message API读取。构造只允许发生在测试创建的 OpenCode 数据目录和测试会话内，禁止改用户历史、生产配置或项目 generated SDK。

这条路径仍必须经过真实 OpenCode 服务、平台后端 runtime 路由、消息恢复、浏览器工作台和历史恢复；不得直接向平台数据库、RunEvent SSE 或前端状态注入 Part。

如果 OpenCode 当前存储不支持安全、隔离地构造，则改用测试专属 OpenCode 插件/进程启动目录生成原生 Part，不能退化成平台 mock。

## 测试架构

扩展现有 `frontend/apps/agent-web/tests/workbench.real-spec.ts` 和 `playwright.real.config.ts`，复用：

- `.env.test` / Spring `test` profile 的真实后端；
- opencode-manager 管理的用户专属 OpenCode 1.17.7 进程；
- 既有平台 Workspace、Session、Run、runtime message/tree API；
- Playwright 真实浏览器与工作台登录态；
- 临时工作区和 finally 清理机制。

新增测试辅助能力只负责创建隔离资源、轮询真实状态、采集三层证据和清理，不新增生产 API。测试报告输出每个 Part 的触发方式（model/native-fixture）、OpenCode 原始类型、平台投影类型、当前 UI、历史 UI和结果。

## 数据流

```text
真实模型自然触发
  或测试专属 OpenCode 原生会话构造
        ↓
OpenCode 1.17.7 原生消息存储与 HTTP API
        ↓
现有 Java runtime facade / session message recovery
        ↓
现有平台 Session messages/tree 与 RunEvent SSE
        ↓
真实浏览器当前会话 Timeline
        ↓
新建对话 → 历史会话恢复 Timeline
```

## 缺陷处理

发现缺陷时按层定位：

1. OpenCode 原始消息不存在：记录自然触发未命中，按约定进入原生构造；不修改模型提示路由。
2. OpenCode 有、Java 映射丢失：先给 `OpencodeRunEventMapper` 或 session message facade 增加失败测试，再最小修复。
3. Java 有、平台快照或历史丢失：先给 recovery/snapshot 服务增加失败测试，再修复现有投影路径。
4. 平台有、前端 reducer 丢失：先给 `runtime-reducer` 增加失败测试，再修复归一化。
5. reducer 有、Timeline 不展示：先给 `FigmaChatPanel` / `OpencodeTimeline` 增加失败测试，再修复现有原生 fallback 或对应视图。

所有生产修改遵守 TDD：先复现 RED，再最小实现 GREEN，最后回归。禁止借此重做聊天视觉、改变原生 Timeline 结构或顺手重构。

## 安全与兼容性

- 不修改 `.env.local`、`.env.test` 或用户 OpenCode 配置。
- 不修改 generated SDK。
- 不新增或改变 HTTP API、RunEvent wire name、DTO 或数据库结构。
- 原生构造仅使用测试临时目录和测试会话，测试结束清理。
- 保持未知 Part fallback，新增类型不能导致既有类型被过滤。
- 保留 Huangzhenren 的 Timeline、ask/reasoning/tool/subagent 和终态逻辑。

## 验证与交付

最终至少执行：

1. 13 种 Part 的真实 Playwright E2E 矩阵。
2. 所有受影响模块的定向 RED/GREEN 回归测试。
3. 前端对话测试、typecheck、lint、生产 build。
4. 后端受影响 reactor 测试。
5. `.env.test` / `test` profile 重启，health/readiness/frontend 检查。
6. `git diff --check`、冲突标记、generated SDK 未变、原生 Timeline 基线审计。

交付报告逐项列出 13 种 Part 的触发方式和证据；任何未命中、未展示或未完成的清理都必须明确列为未完成，不能用总体测试通过替代。
