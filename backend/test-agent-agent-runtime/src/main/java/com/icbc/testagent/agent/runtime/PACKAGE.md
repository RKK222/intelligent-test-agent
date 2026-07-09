# 包说明：com.icbc.testagent.agent.runtime

## 职责

定义多 agent 运行时统一接口、注册表、观测包装和 opencode 适配器。

## 主要程序清单

- `AgentRuntime`：不同 agent 必须实现的运行时接口，包含远端 session 创建、存在性校验和运行/取消/消息能力。
- `AgentRuntimeRegistry`：按 agentId 选择实现并统一包装日志与指标。
- `OpencodeAgentRuntime`：把通用命令转为 `OpencodeClientFacade` 调用，远端 session 404 缺失会经 facade 转为可重建的 `false`。
- `OtherAgentRuntime`：其他 agent 的抽象占位，不作为 Spring Bean 注册；未注册 agent 由 registry 转换为统一 `NOT_FOUND`。

## 允许依赖

- common、domain、opencode-client。
- Reactor、Jackson、Micrometer、Spring Context。

## 禁止依赖

- API Controller。
- Persistence 实现。
- generated SDK。
