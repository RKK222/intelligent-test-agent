# test-agent-opencode-runtime

## 工程定位

与 agent 运行相关的后端业务编排模块，承载 Session、Run、RunEvent 编排、通过 `AgentRuntimeRegistry` 调用 agent、Diff/revert 和受控 PTY terminal 业务；当前唯一真实 agent 实现为 opencode。

## 主要职责

- Session 创建、查询、消息追加和归档。
- Run 启动、取消、远端 agent session 懒创建/复用、事件订阅和终态处理。
- 当前用户 opencode 进程状态查询、初始化契约、防绕过 Run 校验、runtime 代理用户进程路由、manager WebSocket 命令网关，以及用户进程到兼容 `ExecutionNode` 的投影。
- `AgentRuntimeTargetResolver` 统一封装用户进程节点、固定节点 fallback、远端 session 创建/复用以及 binding 节点不一致时的自动覆盖。
- RunEvent 持久化策略、实时发布和 agent projected messages 恢复。
- 从完成态 `write`/`edit`/`apply_patch` tool part 派生运行中 `diff.proposed`，供前端实时追踪文件变化和行数统计。
- Run Diff 查询、接受和拒绝。
- agent runtime 能力映射，包括 catalog/fs/vcs/lsp/mcp、config、provider auth/OAuth、worktree、session share、permission/question 和 MCP auth；opencode 原路径作为当前标准适配形态。
- Model 目录编排：`opencode` 来源保持旧代理；`bailian` 来源直连百炼 `/models` 并把外网 provider 配置同步给 opencode；`internal` 来源读取 `ai_model_configs` 表并按 openclaw 企业 patch 的 `icbc-openai` 兼容配置同步给 opencode。
- PTY terminal ticket、限流、active session registry、进程适配和审计。

## Model 目录配置

`test-agent.model-catalog.source` 控制模型来源：

| source | 行为 |
|---|---|
| `opencode` | 保持旧行为，`/api/model`、`/api/provider` 直接代理 opencode。 |
| `bailian` | 外网测试模式，后端请求 `external.base-url + /models` 获取模型列表；获取失败时回退到配置内置外网模型。 |
| `internal` | 企业内模式，启动时把 openclaw 企业 patch 中的模型清单 seed 到 `ai_model_configs`，接口从数据库读取启用模型。默认模型为 `DeepSeek-V4-Flash-W8A8`。 |

在 `bailian` 和 `internal` 模式下，Run 启动和模型/Provider 目录读取前会尽力 `PATCH /global/config` 到当前 opencode 执行节点，写入 OpenAI-compatible provider、默认模型和请求头配置。同步失败只记录告警，Run 仍走原有错误处理路径。

## 测试覆盖

- `RunApplicationServiceTest` 覆盖 Run 创建、通用 binding 保存/复用、远端 session 懒创建/复用、用户进程 binding 不一致自动重建、sticky node、prompt parts、终态事件、瞬态消息事件、tool part 实时 Diff 派生和取消编排。
- `UserOpencodeProcessAssignmentServiceTest` 覆盖未绑定状态、READY 复用、同服务器重建、端口选择、manager 不可用和绑定/节点投影。
- `ManagerControlMessageCodecTest`、`ManagerConnectionRegistryTest`、`SocketOpencodeProcessManagerGatewayTest`、`BackendJavaProcessLifecycleServiceTest` 覆盖 manager 控制面消息、连接路由、命令等待和后端实例心跳。
- `RunDiffApplicationServiceTest` 覆盖 Diff 事件优先读取、agent runtime Diff fallback、接受/拒绝动作和缺失 messageID 冲突。
- `RunEventPersistencePolicyTest` 覆盖消息投影只走实时通道、关键状态事件持久化、tool payload 清洗和 rawPayload 移除。
- `RunMessageRecoveryServiceTest` 覆盖 agent projected messages 恢复为 transient SSE snapshot，以及未绑定/远端失败时降级为空。
- `SessionApplicationServiceTest` 覆盖 Session 创建前 Workspace 校验、归档隐藏、标题/置顶更新和消息追加默认 role。
- `OpencodeRuntimeApplicationServiceTest` 覆盖 agent/provider/MCP runtime path、config/provider OAuth/worktree/share/MCP auth、workspace directory 透传和 permission reply body 兼容。
- `ModelCatalogApplicationServiceTest` 覆盖企业内模型 seed、`DeepSeek-V4-Flash-W8A8` 默认模型和 opencode provider 配置同步请求。
- `OpencodeRuntimeApplicationServiceTest` 覆盖 agent/provider/MCP runtime path、用户进程节点路由、固定节点 fallback、session binding 自动重建、config/provider OAuth/worktree/share/MCP auth、workspace directory 透传和 permission reply body 兼容。
- `Terminal*Test` 覆盖 ticket 签发/消费/过期、active session 互斥、输入/输出限流、WebSocket envelope 编解码和本地进程适配。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-event`。
- `test-agent-agent-runtime`。
- Reactor、Jackson、Spring Context。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-persistence` 实现类。
- `test-agent-opencode-sdk-generated`。

## 后续 AI 编码指引

新增与会话、运行、事件、Diff、permission/question、runtime catalog、terminal 相关业务编排时改这里；新增 agent 适配器应放在 `test-agent-agent-runtime`。Controller 和 URL 映射必须放在 `test-agent-api`。
高频文本 delta、message projection 和大段 tool/bash 输出不应写入 `run_events`；消息内容刷新恢复只从 agent projected messages 拉取，Run 状态、Diff、permission/question 等平台关键事件继续依赖 durable RunEvent。
生产 `OpencodeProcessManagerGateway` 通过 manager WebSocket 控制面下发 `start`/`health` 命令；无连接、超时或异常必须转换为平台 opencode 错误码。测试仍可使用 fake gateway 固定初始化和健康检查结果。
runtime 代理入口有认证用户时必须通过 `AgentRuntimeTargetResolver` 使用用户专属 opencode 进程；无用户主体的 static-token 或本地兼容调用才允许使用固定 `execution_nodes` fallback。
