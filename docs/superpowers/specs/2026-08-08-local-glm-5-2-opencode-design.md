# 本地 OpenCode 接入 GLM 5.2 设计

## 目标

在当前本地 `test` 平台新增一个 OpenAI-compatible 的 GLM 5.2 上游，并让运行中的用户 OpenCode 进程在平台对话模型下拉中展示、选择和调用 `enterprise-glm/glm-5.2`。

本次只修改本地运行配置和本地公共 OpenCode 配置，不把该供应商加入企业部署模板，不改变现有默认模型。

## 安全边界

- 用户提供的 API Key 只通过平台现有“内部模型 Token”管理能力写入本地数据库，不进入当前仓库、公共配置 Git、OpenCode JSONC、浏览器持久化、日志、提交信息或验证报告。
- OpenCode 不直连外部服务。它继续调用 Java 内部模型代理，由代理根据 `X-Enterprise-Model-Provider` 注入供应商 Token。
- 最终回复和会话日志只记录 Token 已配置，不回显明文、密文或可用于认证的片段。
- 不修改 `.env.test`、`.env.local`、generated SDK 或只读的 `opencode-source/opencode-1.18.4/`。

## 标识与路由

| 项目 | 值 |
|---|---|
| 平台内部供应商 ID | `glm-prod` |
| OpenCode Provider ID | `enterprise-glm` |
| 上游模型 ID | `glm-5.2` |
| OpenCode 模型 ID | `glm-5.2` |
| 显示名 | `GLM 5.2` |
| 候选上游 Base URL | `https://api.kjdfhl.school/v1` |

正式写入前使用用户提供的 Token 对 `GET https://api.kjdfhl.school/v1/models` 做一次只读验证，确认端点可访问且目录包含 `glm-5.2`。如果服务不提供 `/v1/models`，则以一次最小非流式 `chat/completions` 调用验证协议；认证失败、模型不存在或协议不兼容时停止配置，不以猜测值继续。

## 配置变更

### 平台内部模型供应商

通过现有超级管理员管理入口完成以下操作：

1. 新增一个本地 Token 定义并写入用户提供的 Key。
2. 新增或更新 `glm-prod`，Base URL 为验证通过的 `/v1` 根路径，关联上述 Token并启用。
3. 不直接写数据库，不新增 Flyway migration，也不修改其他供应商。

平台内部代理会在 Base URL 后追加 OpenAI-compatible 端点路径，因此 Base URL 不包含 `/chat/completions`。

### OpenCode 公共配置

通过现有公共配置管理链路编辑本地 `opencode.jsonc`：

1. 将 `enterprise-glm` 加入 `enabled_providers`，保持既有顺序和默认模型不变。
2. 新增 `enterprise-glm` Provider，继续使用 `@ai-sdk/openai-compatible`、内部代理 Base URL和内部代理 API Key 环境变量。
3. Provider 固定携带 `X-Enterprise-Model-Provider: glm-prod` 和既有 `ucid` Header。
4. 新增 `glm-5.2` 模型，声明文本输入/输出、推理、工具调用和温度能力，并使用 `reasoning_content` 交错推理字段。

如果上游目录或响应能提供可信限制，则采用上游限制；否则本地配置使用保守的 `context=128000`、`output=8192`。这些值只用于 OpenCode 能力展示和本地上下文控制，不宣称是供应商官方上限。

## 发布与运行态收敛

公共配置保存后使用平台既有提交、发布和 rollout 流程，使当前本地共享运行副本生效。已有用户 OpenCode 进程需要通过平台既有重载或重启流程重新读取公共配置；不直接修改用户运行目录，也不绕过 `OpencodeProcessStartupService`、`OpencodeProcessStopService` 或 `OpencodeProcessStatusQueryService`。

## 验证

按以下顺序验收：

1. 上游只读目录或最小对话探测确认 `glm-5.2` 可用。
2. 平台内部供应商快照显示 `glm-prod` 已启用且 Token 已配置，但响应不含 Token。
3. 用户 OpenCode 进程的有效配置包含 `enterprise-glm`，`/api/provider` 和 `/api/model` 能返回该 Provider 与模型。
4. 平台对话模型选择器显示 `GLM 5.2`，选中值为 `enterprise-glm/glm-5.2`。
5. 发起一次不含敏感内容的最小对话，确认 Run 成功且实际模型选择保持为 `enterprise-glm/glm-5.2`。
6. 检查后端、manager 和 OpenCode 日志，不得出现用户提供的 API Key。

## 失败与回滚

- 上游验证失败时不发布 OpenCode 配置；如果 Token 或供应商记录已经创建，则禁用或删除本次新增的未引用配置。
- 发布后目录缺失时，恢复公共配置到发布前提交，并通过同一 rollout 流程使用户进程重新加载。
- 最小对话失败时保留现有默认模型，禁用 `glm-prod` 并从 `enabled_providers` 移除 `enterprise-glm`，不影响原有 Qwen 和 DeepSeek。

## 影响范围

- API：不新增或变更 HTTP API。
- 事件：不新增或变更 RunEvent。
- 数据库：只通过既有管理能力新增本地业务数据，不变更结构或 Flyway。
- 性能：仅增加一个可选 Provider 和一次验收调用，无常驻轮询变化。
- 安全：新增一个本地供应商密钥，沿用既有 Token 隔离、响应脱敏和 Java 代理注入边界。
- 兼容性：默认模型与原 Provider 保持不变；失败时可独立移除 GLM 配置。
