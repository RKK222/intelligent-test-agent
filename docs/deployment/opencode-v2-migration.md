# OpenCode V2 迁移基线

本分支把运行时固定到 OpenCode `2.0.18`，对应上游 release commit
`cd9a14a6b688d4021bee381dfd39d2cef9c0f862`。二进制使用 `@opencode/cli-*`
官方平台包，Node 运行时固定 `@opencode/client@2.0.18`、
`@opencode/plugin@2.0.18` 和 `effect@4.0.0-rc.112`；V1 的
`@opencode-ai/sdk`、`@opencode-ai/plugin` 不再进入运行时依赖。

Java SDK 由 `tools/generate-opencode-java-sdk.sh` 从冻结的 V2 `/openapi.json`
重新生成。业务代码只依赖 `OpencodeSdkGateway`，V1 兼容路径集中由
`OpencodeV2RouteMapper` 转成 `/api/...`；平台内部 API 和 RunEvent SSE wire
format 保持不变。

V2 事件订阅使用 `/api/event`。每个 SSE frame 的 `data` 是完整事件对象，
常见形态为 `{id,type,data}`，`context/location/metadata` 按事件类型可选；
mapper 从 `data` 及可选上下文提取身份，并保留原始事件 ID、
root/child session scope、未知事件和终态派生事件，并执行重复事件去重链路。
V2 的 `form.created/replied/cancelled` 会继续投影为平台 `question.asked/replied/rejected`，
旧 `answers[]` 回复按 Form 字段的真实 `key` 转为 `{answer:{key:value}}`。
V2 没有独立的 session Todo 查询，平台从最近 200 条 session 消息的 `todowrite`
工具 part 恢复最新快照；更久远且没有持久化事件的 Todo 依赖历史消息回放。

启动器使用 V2 `plugins` 配置数组，每项为 `{ package, options }` 形态。
本地 `file://` package 必须指向含 `index.mjs` 的目录；指向单个 `.mjs`
文件会被 V2 拒绝，Observability 和 RTK 将无法加载。Worker、Mac 开发运行时与
本地客户端都按目录投影插件；
observability 和 RTK 插件通过 `Plugin.define({ id, setup })` 注册 domain hook。
Observability 插件另外注册 `testagent.runtime/tools` RPC，通过
`POST /api/rpc/testagent.runtime/tools` 读取实际 `ctx.tool.list()`；原生
`/api/plugin` 是插件目录，不能当作已注册工具列表。
Observability 使用 `ctx.session.hook("prompt")` 记录待接收的用户输入，
`context/compaction/generate/title` 分别记录主请求和辅助请求的最终模型上下文；
prompt hook 在 durable admission 前执行，所以 Trace 的 `CHAT_MESSAGE.payload.admission=pending`
仅代表提交尝试。V2 `ctx.tool.hook` 的调用身份来自 `event.id`，插件将其映射到既有
`callId`；`session.tool.failed` 与 `execute.after` 同时到达时按该 ID 只保留一条终态。
V2 原生 `session.step.*` 和 `session.text/reasoning.delta` 直接生成 Step、TTFT、decode、
token 与费用指标，模型响应边界优先取 `session.step.streamed`；事件原文继续脱敏入队。
公共工具通过本地 `tool-compat` 把旧 `args/execute` 写法转换为 V2
`Tool.Info`，这样工具 DTO 不会泄漏到 Java 业务模块。

V2 `serve` 强制 Basic auth。部署时应把同一受控 secret 注入 Java backend 的
`TEST_AGENT_OPENCODE_SERVER_PASSWORD` 和 worker 的同名环境变量；launcher 会
将它传给 `OPENCODE_PASSWORD`，gateway 只在变量存在时发送
`Authorization: Basic opencode:<secret>`，日志不会打印 secret。
V2 `/api/session/{id}/prompt` 会一直等待模型、工具或 permission/question Form 完成；平台
Run 已在后台持有该连接，因此 client facade 对 prompt 和 command 使用统一 24 小时硬上限，
禁止普通 30 秒超时和自动重发。收到 `question.asked` 后由平台现有 question reply API
回答，原请求继续返回，避免用户尚未回答时被误报为 OpenCode 失败。
V2 Bun server 收到受控 `SIGTERM` 后会先关闭 watcher，再以退出码 `130` 结束；
启动器只在已转发该停止信号时将其归一化为成功停止，Worker 镜像的
`docker stop` 验证仍要求最终容器退出码为 `0`。

V2 没有 V1 的原生 session share 合约。旧 runtime `/session/{id}/share` 兼容入口
现在返回 `API_GONE`，平台分享必须使用已有的
`/api/internal/platform/opencode-runtime/sessions/{id}/collaboration-share`
资源；分享成员、过期、撤销和审计继续由 TestAgent 自己维护。

平台 compact 入口保留前端 `{providerID,modelID}` DTO。V2 `/api/session/{id}/compact`
只接受 `id/delivery`，因此应用服务先调用 session model 选择，再发送 compact；省略
模型时沿用会话当前选择，不会把旧模型字段直接转发给严格的 V2 请求体。

平台运行态代理把 V2 的 location envelope 保留在通用目录中供前端解包；
MCP 状态、资源、工具和 VCS 分支/文件状态则投影回平台稳定 DTO。
V2 模型、Provider 和配置来源会回传受控供应商凭据。client 网关对这些目录响应
递归删除 `apiKey` 等密钥字段、`headers/settings/body/environment` 等可容纳任意凭据的
容器及插件/MCP 配置，再返回平台 API；`location/data`、模型能力和 provider 策略仍保留。
V2 `/api/fs/read/*` 直接返回带 MIME 的文件字节，不再返回 V1 `FileContent` JSON；
client 适配器在边界按严格 UTF-8 与 NUL 检测恢复 `{type:"text",content}` 或
`{type:"binary",content,encoding:"base64",mimeType}`，平台 runtime 的文件读取响应保持兼容。
V2 `/api/config` 是配置来源数组，不再返回旧 `enabled_providers` 合并对象；
冻结的 2.0.18 会把受控旧配置里的 `provider/enabled_providers/small_model`
规范化为 V2 `providers`、`experimental.policies` 和 title agent model。
原生 `/api/model` 与 `/api/provider` 在配置重载完成后按这些策略收敛，
平台目录还按归一化的 `provider.use` 策略和 `enabled/activation` 字段过滤，
避免原生目录热加载尚未完成时短暂显示被禁止的 Provider。启动器把递归深度写在
`experimental.subagent_depth`；顶层同名字段在 V2 会被忽略。V2 没有实时
LSP 状态接口，平台只报告 `unknown` 或显式关闭时的 `disabled`。

真实 E2E 的原生消息读取使用 `/api/session/{id}/message` 的
`{data,cursor}` 分页。V2 `session_message.data` 内联 assistant 的
`text/reasoning/tool` content，旧版 `message/part` 十二类 fixture 仅用于
`TEST_AGENT_PART_PHASE=fallback-v1` 回滚验收；V2 的
`TEST_AGENT_PART_PHASE=fallback` 使用独立 `session_v2/session_message` fixture，
并通过原生 HTTP、平台历史和 session-tree 三层核对。

取消路径对 V2 `abort` 的迟到事件采用终态仲裁：平台先写入 `CANCELLED`，随后到达的
`run.failed`、`run.succeeded` 或非终态事件不能覆盖该结果；只有先发生的传输型
`FAILED` 可以由确认的 root `SUCCEEDED` 事实纠正。Redis durable/transient Lua、legacy
Run 投影和终态重试路径使用同一规则，避免 V2 abort 关闭 SSE 时把用户取消误报为失败。

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH \
  mvn -pl test-agent-opencode-client -am -DskipTests compile
node --test tools/test-opencode-observability-plugin.mjs
```

观测插件单测覆盖 V2 五种 session hook、脱敏、调用 ID、工具失败去重、
root/child Trace、未知事件透传、失败 Step 不伪造耗时，以及
`session.step.*` 耗时和 token 投影。使用冻结的 `2.0.18` Darwin CLI、临时隔离 HOME、
`file://` 插件目录与本地固定依赖做原生加载探针时，`/api/info` 和
`POST /api/rpc/testagent.runtime/tools`（请求体 `{"input":{}}`）均返回 200，
后者返回 62 个已注册工具；此探针只证明插件成功加载和 RPC 可用，真实模型调用、
上传归档与前端 Trace 仍需在专用 Jenkins 栈做端到端验收。

Worker 构建还会校验固定 npm runtime lockfile、V2 平台包摘要、V2 插件和
client 入口。生产发布仍需在隔离 Worker 上执行 `/api/info`、session、prompt、
command、compact、interrupt、diff 和 `/api/event` smoke；V1 发布包继续作为
独立回滚包保留。V1 回滚不覆盖原 V2 release：专用 Jenkins 任务的
`ROLLBACK_ABI=V1` 会以 `ROLLBACK_TAG` 指向的 immutable release 为平台制品来源，
生成 `rollback-v1-<build>-<source-commit-prefix>` 新清单，重新构建并校验
OpenCode `1.18.4` worker，再通过同一公共 manager 生命周期部署。清单记录
`runtimeAbi/runtimeVersion`，发布后的 verify 阶段会从运行中的 worker 读取
`/usr/local/lib/opencode/VERSION`，确保实际进程确实是 V1；`ROLLBACK_ABI=V2` 才是
原 V2 release 的直接重部署。

测试机的远端验收使用独立 Jenkins 任务 `intelligent-test-agent-opencode-v2`，
只检出 `codex/opencode-v2-migration`，从 `Jenkinsfile.opencode-v2` 发布到
`http://192.168.8.100:3100`（后端 `18182`）。数据库 `testagent_v2_acceptance`、
宿主数据根 `v2-acceptance/data`、Redis、Worker 端口池和 Compose 项目均与
`release` 的 `3000/18082` 栈隔离；资源表和首次初始化步骤见
[`deploy/local/README.md`](../../deploy/local/README.md)。远端验收必须以 Jenkins
最终 `SUCCESS`、实际 URL、原生 V2 smoke 和平台 RunEvent/历史对照为准，
仅本地测试通过不能视为部署完成。

隔离栈正式启动前会按 `26380` 端口和 Compose 项目清理可确认属于 V2 的残留 Redis 容器；发现其它容器或宿主进程
占用时会 fail-closed 并保留占用诊断，不会为了发布停止 release 服务或未知进程。
隔离 Redis 配置仍以 `0600` 文件保存在专用 `shared` 目录；Jenkins 启动前先验证该文件可挂载并在容器内复制，
运行时再由 `redis` 用户读取容器内副本，以兼容测试机的 FUSE 目录权限。
V2 后端 Compose 同时固定 `3100` 为浏览器 CORS 来源，并在发布后验证登录接口的预检响应；仅检查
`/actuator/health/readiness` 不足以证明前端可登录。

隔离 Jenkins #22（提交 `d336bf9b6`）已完成 V2 worker 镜像、后端/前端构建、数据库升级、
Compose 重建和 readiness/CORS/部署清单校验；远端平台 API 已验证进程状态、agent/model/provider/config、
文件树/文件读取/搜索、VCS、LSP、MCP、worktree、session 历史、Run SSE 和消息持久化。
远端取消验收得到 `CANCELLED` 与 `run.cancelled`，模型指定后的 compact 返回成功并可继续读取消息。
目录凭据脱敏修复仍需在下一次 Jenkins 发布中复验；本机 Docker worker 仍因无内部镜像未执行镜像 smoke。

本地 `tools/dev-phase11-real-e2e.sh`、`tools/dev-runnable-loop-check.sh` 和
`tools/verify-opencode-user-process-scenarios.sh` 共用
OpenCode V2 `/api/info` 就绪探针；
启用 Basic auth 时从 `TEST_AGENT_OPENCODE_SERVER_PASSWORD`（或
`OPENCODE_PASSWORD`）读取凭据，不在命令行或日志中输出密码。
