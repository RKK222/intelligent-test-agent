# test-agent-local-client-protocol

本模块定义后台 Java 与本地 OpenCode 客户端共同使用的反向 WebSocket 协议。协议版本固定为
`local-opencode-client.v1`；所有消息都通过 `LocalClientFrame` 封装，并携带请求、链路和连接换代字段。

文件与 HTTP 数据使用不超过 256 KiB 的原始字节分片，分片内容以 Base64 放入 JSON 帧。协议模块只定义
稳定线格式和校验，不依赖服务端领域模型，也不包含任何密钥持久化逻辑。

版本帧方向固定：客户端发送 `VERSION_CHECK`、`UPDATE_PREPARED`、`UPDATE_STATUS`；服务端发送
`VERSION_POLICY`、`UPDATE_COMMAND`、`UPDATE_APPLY`、`UPDATE_CANCEL`、`UPDATE_STATUS_ACK`。`REGISTER` 保留旧八字段
`clientKey/clientInstanceId/clientName/platform/architecture/clientVersion/opencodeVersion/reportedAddresses`，并以可选
字段追加 `unifiedAuthId`、`launcherVersion`、`capabilities`；只有恰好旧八字段且版本为 `0.1.0` 的客户端允许省略统一
认证号，新协议缺失或空白值失败关闭。
帧不含 Key、下载 URL 或签名原文；`UPDATE_*` 以 `commandId + clientInstanceId` 幂等，并受 connection generation
fencing 约束。只有同时携带 launcher 版本并声明 `SELF_UPDATE_V1` 的客户端才可双向使用版本帧。客户端持久化终态
result marker 并在重连后重报；服务端完成幂等持久化才返回精确匹配原命令 generation/status 的 ACK，客户端收到匹配
ACK 后清 marker。deadline 失败可由迟到 `SUCCEEDED/AUTO_ROLLED_BACK` 条件纠正；其它持久化终态冲突不返回 ACK。
目标版本大于当前为 `UPDATE`、小于为 `ROLLBACK`、相同为 `SAME`。
平台没有配置任何有效目标版本时保持当前版本且不发送 `VERSION_POLICY`。兼容已经发布的旧服务端时，客户端只把
`targetVersion=null/direction=SAME/policyRevision=0/force=false` 识别为无动作空策略；其它版本策略以及所有
`UPDATE_*` 命令仍要求正数 policy revision，不能以该兼容哨兵绕过 fencing。

声明 `MANAGED_MODEL_CONFIG_V1` 的客户端会在 `REGISTERED.managedModelConfig` 接收无密钥 OpenCode 模型配置；字段仅包含
`model/small_model/enabled_providers/provider`，provider 的地址与 API key 使用客户端进程内的 loopback 环境变量占位符。
企业来源从平台当前公共 `opencode.jsonc` 读取 OpenCode provider 名称、模型和
`X-Enterprise-Model-Provider` Java 路由 ID，并只保留数据库运行快照中已启用且已配置 Token 的映射；不得用历史
`ModelCatalogProperties.providerId` 覆盖公共配置中的路由 ID。
旧客户端或未声明该能力的客户端继续收到不含此字段的原 JSON 形状。该配置不写入公共能力包，也不携带平台地址、
Client key、统一认证号或上游模型密钥。

声明 `MANAGED_RTK_CONFIG_V1` 的客户端会在 `REGISTERED.managedRuntimeConfig` 接收全局 RTK 开关；当前只包含
`rtkEnabled`，默认关闭。旧客户端不会收到该字段，客户端只有在下一次受管启动或重启时才应用开关。

声明 `PUBLIC_CAPABILITY_PERSONAL_EDIT_V1` 的客户端还接受浏览器经平台文件 WebSocket 转发的 `agent-config.*` 个人副本操作。

公共能力更新请求和安装命令带 `confirmedDiscardPersonalChanges` 标记。客户端存在个人副本时，缺少该标记会
拒绝覆盖并保留草稿；确认后先备份，激活失败恢复备份。服务端使用 `lcpcd_` commandId 区分已确认 attempt，
复用现有持久化表，不新增数据库字段。
服务端 ticket 固定 `scope=PUBLIC`、`runtimeKind=LOCAL_CLIENT`、`clientInstanceId` 和 `connectionGeneration`，客户端只把
`agents/**`、`skills/**`、`tools/**` 映射到不可变签名基线的个人副本；manifest、根级依赖、`node_modules`、符号链接和特殊文件
始终拒绝。`agent-config.restore` 不带 path 时清除整份个人副本，恢复签名基线；每条 RPC 都重新校验 capability、用户归属和代次。

客户端桌面主动注册工作区使用 `WORKSPACE_REGISTER {name,rootPath}`，后台只采用已认证连接中的 userId、
clientInstanceId 和 generation，不接受载荷伪造身份；平台完成客户端真实根校验和事务持久化后返回同 requestId 的
`WORKSPACE_REGISTERED {workspaceId,name,rootPath}`。业务失败用同 requestId 的 `ERROR` 收敛单次请求，不关闭连接。

声明 `WORKSPACE_GIT_ACCESS_V1` 的客户端额外接受 `FILE_REQUEST.operation=workspace.git-access.check`。请求继续绑定
已注册的 `workspaceId + rootDigest`，不携带任意路径；客户端在对应真实根目录执行只读 Git 远端检查并通过
`FILE_RESPONSE` 返回固定 `{status,reason,message}`。状态为 `ACCESSIBLE/INACCESSIBLE/UNKNOWN`，成功时 reason/message
为空，其余状态为固定脱敏值；非 Git 目录使用 `UNKNOWN + NOT_GIT_REPOSITORY` 表示 Git 能力不适用但目录仍可作为工作区。
旧客户端未声明能力时服务端不得发送该操作。
