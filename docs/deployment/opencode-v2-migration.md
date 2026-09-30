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
MCP 状态、资源、工具和 VCS 分支/文件状态则投影回平台稳定 DTO。V2 MCP 必须使用
`mcp.servers.<name>`，不能继续使用 V1 的 `mcp.<name>` 和 `enabled` 字段；需要把 MCP
工具直接放进模型原生工具目录的服务显式设置 `codemode: false`，否则服务虽显示
`connected`，模型只会看到 Code Mode 的聚合入口，不会看到 `<server>_<tool>`。
V2 模型、Provider 和配置来源会回传受控供应商凭据。client 网关对这些目录响应
递归删除 `apiKey` 等密钥字段、`headers/settings/body/environment` 等可容纳任意凭据的
容器及插件/MCP 配置，再返回平台 API；`location/data`、模型能力和 provider 策略仍保留。
V2 `/api/fs/read/*` 直接返回带 MIME 的文件字节，不再返回 V1 `FileContent` JSON；
client 适配器在边界按严格 UTF-8 与 NUL 检测恢复 `{type:"text",content}` 或
`{type:"binary",content,encoding:"base64",mimeType}`，平台 runtime 的文件读取响应保持兼容。
V2 `session.diff` 的可选定位参数是 `from`、`to`、`context`，其中 `from` 和 `to`
必须是 USER message。平台现有 `messageId` 参数继续保留，但在 client 适配层映射为
`from`；Run Diff 使用持久化的 `dispatchMessageId`，不能把终态投影里的 assistant
`lastRemoteMessageId` 当作 V2 Diff 锚点。旧内部调用仍传 `messageID/messageId` 时，网关
会在边界归一化为 `from`，避免把旧查询名发给 2.0.18。
V2 `/api/config` 是配置来源数组，不再返回旧 `enabled_providers` 合并对象；
交付样例直接使用 V2 `providers`、`experimental.policies` 和 `agents.title.model`。
`LocalClientManagedModelConfigService` 从 V2 公共配置提取已放行且具备有效 Java 路由的供应商，
只在既有 `MANAGED_MODEL_CONFIG_V1` 握手边界转换为旧客户端可用的 loopback 配置；
当前现场公共 Git 若仍是 V1 格式，转换器也继续兼容读取。
V2 没有可写的 Provider/model HTTP PATCH；旧 `/api/config` 同步代码已移除，
平台配置 PATCH 只接受 shell 并映射到 `/api/experimental/config`。
Provider/模型由公共配置发布和 `/api/location/reload` 生效；回滚 V1 平台前须把独立公共配置
恢复到与归档 V1 release 对应的 Git 快照，不能让 V1 进程读取 V2-only JSONC。
原生 `/api/model` 与 `/api/provider` 在配置重载完成后按这些策略收敛，
平台目录还按归一化的 `provider.use` 策略和 `enabled/activation` 字段过滤，
避免原生目录热加载尚未完成时短暂显示被禁止的 Provider。启动器把递归深度写在
`experimental.subagent_depth`；顶层同名字段在 V2 会被忽略。V2 没有实时
LSP 状态接口，平台只报告 `unknown` 或显式关闭时的 `disabled`。

真实 E2E 的原生消息读取使用 `/api/session/{id}/message` 的
`{data,cursor}` 分页；第一页可传 `order`，跟随 `cursor.next/previous` 时不能再次传 `order`，
游标自身已经编码了顺序和方向。V2 `session_message.data` 内联 assistant 的
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
TEST_AGENT_OPENCODE_SERVER_PASSWORD='<受控本地密码>' \
  OPENCODE_BASE_URL=http://127.0.0.1:4296 \
  node tools/test-opencode-v2-native-smoke.mjs
OPENCODE_V2_BIN=/path/to/opencode-2.0.18 \
  node tools/test-opencode-v2-mcp-smoke.mjs
```

观测插件单测覆盖 V2 五种 session hook、脱敏、调用 ID、工具失败去重、
root/child Trace、未知事件透传、失败 Step 不伪造耗时，以及
`session.step.*` 耗时和 token 投影。使用冻结的 `2.0.18` Darwin CLI、临时隔离 HOME、
`file://` 插件目录与本地固定依赖做原生加载探针时，`/api/info` 和
`POST /api/rpc/testagent.runtime/tools`（请求体 `{"input":{}}`）均返回 200，
后者返回 62 个已注册工具；此探针只证明插件成功加载和 RPC 可用，真实模型调用、
上传归档与前端 Trace 仍需在具备 Linux amd64 Worker 的发布环境做镜像级验收。

本地隔离启动已经在独立工作树完成：前端 `http://127.0.0.1:3100`、Java 后端
`http://127.0.0.1:18182`、OpenCode V2 Worker 使用端口 `4296`，Redis 使用
独立的本地端口 `16380`；共享测试 PostgreSQL 仍按根目录 `.env.test` 指向固定
测试库。后端 readiness 和前端首页均返回 200。原生 V2 `/api/info`、`/api/event`、
文件树/读取/搜索、VCS、PTY、MCP 目录通过 smoke；平台 agent/model/provider/config、
session、prompt、command、SSE、消息分页、diff、compact、fork、side-question、archive
通过 API 验收。体验 Workspace 的文件、VCS 和 worktree 平台入口按既有权限策略返回
403，因此本次没有用它冒充普通 Workspace 的平台代理通过。Run SSE 收到
`run.succeeded`，重启后重新执行矩阵仍保持重复事件数为 0；`/api/health` 在冻结的
2.0.18 中不存在，部署健康检查必须使用 `/api/info`。

独立的 `tools/test-opencode-v2-native-smoke.mjs` 会在系统临时目录建立并删除自有 Git
工作区和 Session，验证原生 `2.0.18`、`/api/event`、18 个目录/文件/VCS/PTY/Worktree
只读入口、session 创建/查询、shell 消息、wait、prompt 后 user/assistant 消息、diff、
fork、revert stage/commit、已有消息上的 compact 终态，以及 interrupt。隔离假模型矩阵
还验证命令执行、Form 创建/回复、Permission 的 `ask → pending → once`、子 Agent
创建及子会话消息和 SSE 事件、两页消息游标、JSON 400/404 后恢复查询。子 Agent
prompt 接受请求时工具仍可能为 `running`，必须在 `/wait` 后核对子会话和工具终态。
第二页只能发送 `cursor` 和 `limit`，不能重发 `order`；错误组合会得到 400。空会话的
compact 虽返回 200，却可能发出 `session.compaction.failed`；脚本以真实
`session.compaction.ended` 为成功条件。2026-10-01 在本机 `4296` 运行通过，
SSE 同时捕获 `session.execution.succeeded` 和 `session.compaction.ended`。
该脚本直接验证原生协议，不能代替平台授权、RunEvent、真实 Worker 镜像或 V1 回滚验收。

独立的 `tools/test-opencode-v2-mcp-smoke.mjs` 不复用 `4296` 或用户配置；它在临时
HOME、配置目录和随机 loopback 端口启动冻结的 V2 CLI，以自有 stdio MCP fixture
验证 `initialize`、`tools/list`、`resources/list`、`resources/templates/list`，并通过
`/api/mcp` 和 `/api/mcp/resource` 确认连接状态及资源目录。2026-10-01 本机运行返回
`connected` 和测试资源 `testagent://v2-probe`。此结果证明 V2 MCP 主机与发现协议可用，
并在 `codemode:false` 下使用隔离的 OpenAI-compatible 假模型完成一次模型可见性和
`tools/call` 验收；输出 `toolVisibleToModel:true/toolInvoked:true`。此结果不证明企业
`code_analysis` 二进制或 Linux Worker 镜像已通过；这些仍需在原生 amd64 隔离部署中验证。

2026-09-30 增加现有自有 Workspace 的真实浏览器验收：使用
`TEST_AGENT_REAL_E2E_WORKSPACE_ID` 和 `TEST_AGENT_REAL_E2E_WORKSPACE_ROOT` 选择账号
确有权限且与进程服务器归属一致的工作区，设置受控 OpenCode Basic Auth secret，执行
`workbench.real-spec.ts` 中 `runs a real V2 conversation and PTY` 用例。该用例验证平台
Session/Run、RunEvent SSE 终态与 durable 去重、V2 原生 user/assistant 消息、平台
USER 历史、session-tree assistant 和浏览器 PTY 回显，并只删除自己创建的远端 Session。
本地 `3100/18182/4296` 栈通过 1/1；发现并修正 PTY 在 test profile 配置单个
`TEST_AGENT_CORS_ALLOWED_ORIGINS=*` 时错误拒绝合法浏览器 Origin 的问题，Origin
仍需格式合法且 PTY ticket 仍一次性消费。原有需现场可访问测试仓库及 SSH key 的
托管 Workspace 创建 fixture 在当前测试账号下未通过，不能据此宣称该路径完成验收。
反复重启时还观察到旧 manager WebSocket 的断线清理可能删除同 containerId 的新控制连接，
使 `/processes/me` 在后端仍健康时返回 `UNAVAILABLE`；现按当前 socket sender 身份
条件清理，并通过“旧 socket 后关闭，新 socket 仍能接收命令”的回归测试锁定。
修复后本地受管重启的进程状态恢复为 `READY`，真实浏览器 Session/Run/SSE/PTY
再次通过。中途有一次上游模型超过本地 Run 超时，平台产生 `run.failed`，随后不改代码
重跑通过；该结果说明模型上游延迟仍会影响真实 E2E，不能由前端或 PTY 冒充成功。

本地启动会把受控 `TEST_AGENT_OPENCODE_SERVER_PASSWORD` 通过公共
`OpencodeProcessStartupService` 同时注入 Worker 的 `TEST_AGENT_OPENCODE_SERVER_PASSWORD`
和 `OPENCODE_PASSWORD`，避免 V2 `serve` 默认 Basic Auth 随机密码导致平台 gateway
收到 401。启动器会检查 MCP、client、plugin、effect、jsonc-parser、playwright 和
zod 的实际入口文件，缺依赖时重新安装固定 lockfile，而不是复用不完整的前端
`node_modules`。

Worker 构建还会校验固定 npm runtime lockfile、V2 平台包摘要、V2 插件和
client 入口。生产发布仍需在隔离 Worker 上执行 `/api/info`、session、prompt、
command、compact、interrupt、diff 和 `/api/event` smoke；V1 发布包继续作为
独立回滚包保留。V1 回滚不覆盖原 V2 release：专用 Jenkins 任务的
`ROLLBACK_ABI=V1` 要求 `ROLLBACK_TAG` 指向日常 Jenkins 只读归档中的真实 V1
平台 release，而不是隔离目录中的 V2 release。发布前核对旧清单及前后端、源码摘要，
并检查旧 JAR 的 OpenCode 网关、`1.18.4` 依赖锁定；不匹配时在停服前失败。
随后复制 V1 前后端，生成 `rollback-v1-<build>-<source-commit-prefix>` 新清单，
重新构建并校验 OpenCode `1.18.4` worker，再通过同一公共 manager 生命周期部署。清单记录
`runtimeAbi/runtimeVersion`，发布后的 verify 阶段会从运行中的 worker 读取
`/usr/local/lib/opencode/VERSION`，确保实际进程确实是 V1；`ROLLBACK_ABI=V2` 才是
隔离 V2 release 的直接重部署。V1 回滚仍须在隔离数据库上做会话和配置 smoke；
旧 Java 对 V2 数据状态的兼容性不能仅凭 Worker 版本检查推断。

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
Jenkins 主机预检默认对 Docker daemon、Java runtime image 和隔离 Redis 配置使用 180 秒单项上限，
覆盖测试机冷缓存镜像层解压；超时仍直接终止发布，不跳过检查或切换到未验证的宿主依赖。
隔离 Redis 配置仍以 `0600` 文件保存在专用 `shared` 目录；Jenkins 启动前先验证该文件可挂载并在容器内复制，
运行时再由 `redis` 用户读取容器内副本，以兼容测试机的 FUSE 目录权限。
V2 后端 Compose 同时固定 `3100` 为浏览器 CORS 来源，并在发布后验证登录接口的预检响应；仅检查
`/actuator/health/readiness` 不足以证明前端可登录。

隔离 Jenkins #22（提交 `d336bf9b6`）已完成 V2 worker 镜像、后端/前端构建、数据库升级、
Compose 重建和 readiness/CORS/部署清单校验；远端平台 API 已验证进程状态、agent/model/provider/config、
文件树/文件读取/搜索、VCS、LSP、MCP、worktree、session 历史、Run SSE 和消息持久化。
远端取消验收得到 `CANCELLED` 与 `run.cancelled`，模型指定后的 compact 返回成功并可继续读取消息。
目录凭据脱敏修复仍需在下一次 Jenkins 发布中复验；本机 Docker worker 仍因 ARM
模拟器无法完成镜像 smoke。

2026-10-01 的本地补充验收将 V2 权限 `action` 投影回 V1 RunEvent 的 `permission`
别名，并用 V1/V2 对照测试固定权限请求与文字增量的核心字段。client 至 runtime 的
Maven 测试、前端 282 项定向 Vitest、7 个权限/提问/历史/compact 浏览器场景、
Jenkins 发布脚本静态门禁，以及麒麟 ARM64、Windows 本地客户端打包与自动回滚
脚本均通过。浏览器场景的首个用例第一次停在工作台加载页并超时，重试通过，随后
单独关闭重试再跑也通过；这不是首次运行全绿的证据。更新后的本地后端包已从
独立工作树重启，`18182` readiness 为 `UP`，`3100` 首页与登录 CORS 预检均为 200，
manager WebSocket 已连接。`admin/admin` 对本地登录仍返回 401；当前没有可用的
登录 Token，因此不能把这些 mock 浏览器用例当作本次提交的真实平台 Run/SSE 验收。

运行管理只读 smoke 脚本原先仍用 manager token 请求已作废的
`manager-backends` HTTP 入口，本地后端实际返回 410。现在脚本通过超级管理员
`management/overview` 检查指定 `linuxServerId` 的 `CONNECTED` manager 和 backend
connection，并保留不发送 manager token 的旧参数兼容。无用户 Token 时，本机只能证明
backend health 与 manager WebSocket 日志连接，不能把该结果当作已通过运行管理 API 验收。

本次本地 ARM Mac 还尝试了 `deploy/internal/package-release.sh --opencode-only`。
Docker 构建使用清单中固定的 OpenCode、Codex 资源摘要；下载阶段改为单文件下载
后再执行长度和 SHA-256 校验，避免 CDN 并发 Range 返回短分片。构建在 amd64
模拟执行 OpenCode Bun `--version` 时因 QEMU/CPU 不支持 AVX 触发段错误，随后按
固定字节校验失败关闭。因此本机已验证启动器、运行时依赖和平台 API，但不能把
该模拟结果当作 Linux amd64 Worker 镜像验收；原生 amd64 构建机仍需完成镜像和
V1 回滚包 smoke。

独立的 `codex-download` Docker 阶段已按固定 `0.145.0`、归档 SHA、bwrap 二进制 SHA
和许可证摘要构建通过。封包读取 `TEST_AGENT_CODEX_VERSION`，不会受 Codex Desktop
导出的通用 `CODEX_VERSION` 影响。

本地 `tools/dev-phase11-real-e2e.sh`、`tools/dev-runnable-loop-check.sh` 和
`tools/verify-opencode-user-process-scenarios.sh` 共用
OpenCode V2 `/api/info` 就绪探针；
启用 Basic auth 时从 `TEST_AGENT_OPENCODE_SERVER_PASSWORD`（或
`OPENCODE_PASSWORD`）读取凭据，不在命令行或日志中输出密码。
