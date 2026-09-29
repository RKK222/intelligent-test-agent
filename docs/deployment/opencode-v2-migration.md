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
公共工具通过本地 `tool-compat` 把旧 `args/execute` 写法转换为 V2
`Tool.Info`，这样工具 DTO 不会泄漏到 Java 业务模块。

V2 `serve` 强制 Basic auth。部署时应把同一受控 secret 注入 Java backend 的
`TEST_AGENT_OPENCODE_SERVER_PASSWORD` 和 worker 的同名环境变量；launcher 会
将它传给 `OPENCODE_PASSWORD`，gateway 只在变量存在时发送
`Authorization: Basic opencode:<secret>`，日志不会打印 secret。
V2 Bun server 收到受控 `SIGTERM` 后会先关闭 watcher，再以退出码 `130` 结束；
启动器只在已转发该停止信号时将其归一化为成功停止，Worker 镜像的
`docker stop` 验证仍要求最终容器退出码为 `0`。

V2 没有 V1 的原生 session share 合约。旧 runtime `/session/{id}/share` 兼容入口
现在返回 `API_GONE`，平台分享必须使用已有的
`/api/internal/platform/opencode-runtime/sessions/{id}/collaboration-share`
资源；分享成员、过期、撤销和审计继续由 TestAgent 自己维护。

平台运行态代理把 V2 的 location envelope 保留在通用目录中供前端解包；
MCP 状态、资源、工具和 VCS 分支/文件状态则投影回平台稳定 DTO。
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

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH \
  mvn -pl test-agent-opencode-client -am -DskipTests compile
```

Worker 构建还会校验固定 npm runtime lockfile、V2 平台包摘要、V2 插件和
client 入口。生产发布仍需在隔离 Worker 上执行 `/api/info`、session、prompt、
command、compact、interrupt、diff 和 `/api/event` smoke；V1 发布包继续作为
独立回滚包保留。

测试机的远端验收使用独立 Jenkins 任务 `intelligent-test-agent-opencode-v2`，
只检出 `codex/opencode-v2-migration`，从 `Jenkinsfile.opencode-v2` 发布到
`http://192.168.8.100:3100`（后端 `18182`）。数据库 `testagent_v2_acceptance`、
宿主数据根 `v2-acceptance/data`、Redis、Worker 端口池和 Compose 项目均与
`release` 的 `3000/18082` 栈隔离；资源表和首次初始化步骤见
[`deploy/local/README.md`](../../deploy/local/README.md)。远端验收必须以 Jenkins
最终 `SUCCESS`、实际 URL、原生 V2 smoke 和平台 RunEvent/历史对照为准，
仅本地测试通过不能视为部署完成。

隔离栈正式启动前会按 `16381` 端口和 Compose 项目清理可确认属于 V2 的残留 Redis 容器；发现其它容器或宿主进程
占用时会 fail-closed 并保留占用诊断，不会为了发布停止 release 服务或未知进程。

本地 `tools/dev-phase11-real-e2e.sh`、`tools/dev-runnable-loop-check.sh` 和
`tools/verify-opencode-user-process-scenarios.sh` 共用
OpenCode V2 `/api/info` 就绪探针；
启用 Basic auth 时从 `TEST_AGENT_OPENCODE_SERVER_PASSWORD`（或
`OPENCODE_PASSWORD`）读取凭据，不在命令行或日志中输出密码。
