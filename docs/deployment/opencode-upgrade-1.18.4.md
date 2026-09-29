# OpenCode 1.18.4 升级与兼容性评估（历史 V1 基线）

本文保留 1.18.4 的历史审计和回滚证据；当前默认运行时已迁移到 2.0.18，实施和发布以
[OpenCode V2 迁移基线](./opencode-v2-migration.md) 为准。文中旧 `@opencode-ai/*` 依赖、路径和摘要只适用于该 V1 回滚包。

## 固定版本与来源

| 项目 | 升级前 | 当前固定值 |
|---|---:|---:|
| OpenCode | 1.17.8 | 1.18.4 |
| OpenCode tag commit | `11e47f91496005aab4d7c5a2d0a7da5d2651b4ac` | `49c69c5ed3ccf706b61b3febb43c8aaff7f8325e` |
| Java SDK 生成器 | 7.23.0 | 7.24.0 |
| `@opencode-ai/plugin` / `@opencode-ai/sdk` | 1.17.8 | 1.18.4 |
| `effect` | 4.0.0-beta.74 | 4.0.0-beta.83 |

企业 worker 使用上游 release 的 `opencode-linux-x64-baseline.tar.gz`，不从源码构建。1.18.4 归档大小为 59,265,643 bytes，SHA-256 为 `4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc`，解压后二进制 SHA-256 为 `6ce6570e7db9a40e7bd3304ebdfff607920bde8cafd2eb5587bd7a26f89ba0b5`。源码快照只用于审计和行为参考，本项目严格禁止修改 `opencode-source/opencode-1.18.4/` 中的 OpenCode 源码、测试、配置、构建脚本、资源或临时补丁。

## API 差异

从 1.17.8 和 1.18.4 官方 `/doc` 比较得到：paths 从 150 增至 162，schemas 从 339 增至 472，HTTP operations 从 175 增至 188；没有删除 operation。新增 operation 为：

- `GET /experimental/capabilities`
- `GET /api/session/active`
- `POST /api/session/{sessionID}/agent`
- `POST /api/session/{sessionID}/model`
- `POST /api/session/{sessionID}/revert/stage`
- `POST /api/session/{sessionID}/revert/clear`
- `POST /api/session/{sessionID}/revert/commit`
- `GET /api/session/{sessionID}/history`
- `GET /api/session/{sessionID}/event`
- `POST /api/session/{sessionID}/interrupt`
- `GET /api/session/{sessionID}/message/{messageID}`
- `POST /api/session/{sessionID}/permission`
- `GET /api/session/{sessionID}/permission/{requestID}`

平台现有调用使用的 method、path、参数和响应契约未发生破坏性变化。1.18.4 规范已使用 OpenAPI Generator 7.24.0 重新生成，生成工程和后端 `test-agent-opencode-sdk-generated` 均已编译；生成源码禁止手改。

模型层变化中，`FileSystemEntry.mime` 已移除；前端现有转换优先读取 `type` 并仅把 `mime` 作为旧版回退，因此无需业务改造。事件联合类型删除部分旧 `session.next.*` 定义并增加 revert 事件；平台 mapper 对未知 raw event 保持 `opencode.event.unknown` 透传，旧事件测试继续保留。

## 行为影响与适配

- 1.18.2 起增加 `subagent_depth`，上游默认值为 1。平台启动器根据随包 `VERSION` 判断能力：1.18.2 及以上以 `OPENCODE_CONFIG_CONTENT` 强制设为 2，支持且仅要求 root → child → grandchild；1.17.8 回滚运行时会移除该版本不识别的字段。已有配置内容会合并，非法内容直接失败，避免静默丢配置。
- `RunSessionScopeRouter` 现在按 task part 所属 session 建立 parent。root task 仍创建 child，child task 创建 grandchild；task part 保持在发起它的 scope，不再把 grandchild 错挂 root。
- 官方单文件程序在完全断网时仍会检查配置目录依赖元数据。用户 `opencode serve` 启动器只为 XDG 全局配置、`OPENCODE_CONFIG_DIR`、当前工作目录 `.opencode` 和工作目录 `node_modules` 做固定数量的非覆盖式链接，其中包含公共浏览器 Tool 需要的 `playwright-core`，不再递归扫描工作区。每台 worker 唯一的后台维护循环在 manager 启动后等待 60 秒，再按周期临时启动单次扫描 Node 进程；扫描结束即退出，不常驻递归文件监听器。已有及新建工作区内的 `.opencode` 在下一轮自动补齐，默认等待 60 秒加本轮实际扫描耗时。该工作目录是个人 worktree 的共同祖先，因此深层应用 workspace 的 `.opencode/tools` 也能按 Node 标准规则解析模块；内网运行不执行 npm 下载。
- 本地浏览器 Tool 仍复用 `playwright-core@1.61.0` 的页面、Locator 和 CDP 适配，但连接阶段改用 OpenCode/Bun 原生
  WebSocket 实现公开 `ConnectOverCDPTransport`。这避开 Node `ws` 在企业 360 已返回 HTTP 101 后不触发 open 的兼容问题；
  客户端不要求目标用户安装 Node，依赖继续由签名公共能力包离线提供。端点再次限制为 Relay 返回的同一 `127.0.0.1` 端口。
- 企业后台部署入口通过 `verify-opencode-tool-runtime.sh` 对 programs 归档和目标机安装目录执行同一失败关闭校验：runtime manifest、lockfile 及 7 个固定直接依赖的包元数据和入口文件必须同名同版本存在，`@modelcontextprotocol/sdk`、`@opencode-ai/plugin`、`@opencode-ai/sdk`、`effect`、`jsonc-parser`、`playwright-core`、`zod` 缺少任一项都不能继续替换 Java、加载镜像或重启 worker；增量 `reuse` 包同样核对现有目录。
- 1.18.4 上游只在 `.gitignore` 不存在时一次性写入运行文件规则，已有但不完整的文件不会补齐。企业交付改为复用 `deploy/internal/opencode-runtime.gitignore`：节点升级先修复已经初始化的标准公共配置目录，新节点或尚未初始化的目录由官方启动器在创建依赖链接前幂等补齐；已有自定义规则保留，运行文件不会进入公共仓库脏状态，Agent/Skill/Tool 和用户配置仍正常参与 Git 检测。
- `includeUsage=false` 仍须保留。1.18.4 对 openai-compatible provider 仍会在未显式关闭时设置 `includeUsage=true`，企业内部不支持该字段的接口会受影响。
- `FilePartInput.source` 在 1.18.4 中仍为可选，但一旦提供，`FileSource` 必须完整包含 `text/type/path` 且不能混入平台字段。平台路径型原生附件因此只发送已校验的 `file://`、mime 和 filename，不发送 source；只有内联正文生成完整 FileSource。非原生工作区附件的 `contextType/deliveryMode` 仅用于平台分流和历史展示，转换为 OpenCode text part 时移除。
- 1.18.4 的 `GET /session/status` 返回当前 busy/retry session map；session 进入 idle 时上游发布 idle 事件并从 map 删除该 key。平台的交互回复终态补偿据此只把“root key 不存在”视为 idle，空值、非对象、请求异常或 root key 仍存在均失败关闭，不能仅凭某条 assistant `finish=stop` 判定整轮结束。
- 1.18.4 的旧 Provider 配置 schema 虽接受模型 `release_date`，旧 `/provider` 也会回显该值，但 v1→v2 配置迁移和 `ConfigV2.Model` 不传递发布时间；平台实际使用的 `/api/model` 对这类本地 JSONC 模型返回 `time.released=0`。企业前端只能保持该接口的原生目录顺序，不能把 `enabled_providers` 数组或 JSONC 的键顺序解释为展示排序配置。随 worker 通过 `OPENCODE_MODELS_PATH` 注入的 models.dev 元数据是另一条输入：它会生成 `time.released`，`/api/model` 按该时间倒序返回。
- [opencode-models.json](../../deploy/internal/opencode-models.json) 是 models.dev 兼容的全局模型元数据固定快照，不是 OpenCode 会按文件名自动发现的公共配置。当前 Qwen 上下文为 `200000`，DeepSeek 为 `262144`；Qwen 的目录排序日期固定高于 DeepSeek，使 `/api/model` 返回时 Qwen 优先，但公共默认/小模型和 `code_analysis` MCP 仍为 DeepSeek。标准发布时两台后台统一把随包文件安装到宿主机 `/data/testagent/config/opencode-models.json`；经明确批准的单节点灰度可只在 `.4` 替换并重启 worker，必须留存 `.4/.114` 各自 SHA，回滚时恢复 `.4` 备份，不能误把灰度文件同步到 `.114`。worker 将文件只读挂载为 `/etc/test-agent/opencode-models.json` 并设置 `OPENCODE_MODELS_PATH`，manager 启动的所有用户 OpenCode 进程继承后生效。该文件只维护 Provider/Model 元数据，不放 token、UCID 或内部代理密钥；实际 provider、`includeUsage=false` 和企业代理路由仍由公共配置 Git 的 [opencode.jsonc.example](../../deploy/internal/opencode.jsonc.example) 管理。宿主启动脚本在删除当前容器前、worker entrypoint 在 manager 启动前均复用 [validate-opencode-models.sh](../../deploy/internal/validate-opencode-models.sh)，常规校验 ID 对齐、必填能力、`release_date`、正数 `limit` 和可选模态；Mac 封包另由 [verify-opencode-model-priority.sh](../../deploy/internal/verify-opencode-model-priority.sh) 锁定本次快照优先级，不改变 worker 指纹，同时允许 `.114` 的旧快照继续通过常规 worker 重启校验。替换文件后必须重启目标 worker；已有用户进程不能只靠刷新浏览器取得新目录。
- OpenCode 1.18.4 本身不改变既有聊天 HTTP API 或 RunEvent SSE wire shape。平台在源码外新增独立 Observability HTTP/WSS 协议和 ClickHouse 目录表；它不修改 OpenCode 源码或既有聊天鉴权。PostgreSQL 只新增进程代次字段用于 generation fencing，不保存任何 Trace 正文。

## Observability 插件装配

平台不修改只读 `opencode-source/opencode-1.18.4/`。共享
`deploy/internal/opencode-observability-plugin.mjs` 由服务端 official launcher 强制追加到 `OPENCODE_CONFIG_CONTENT.plugin`，
本地 Java 监管 OpenCode 时追加同一文件 URI；两端不复制 hook 逻辑，也不覆盖用户已配置的其它插件。
根目录 `restart-dev-services.sh` 也会在 `.tmp/dev-services/opencode-observability-runtime` 组装同一 launcher，manager 的
`OPENCODE_BIN` 必须指向该入口而不是本机原始二进制；这样自动恢复的开发进程不会绕过插件。该临时运行时优先复用已安装的
固定依赖树，依赖缺失时才按随仓库 lockfile 安装，不修改 OpenCode 源码或用户配置。

插件适配 1.18.4 的 `tool.execute.before/after`、`chat.message`、`experimental.chat.system.transform`、
`experimental.chat.messages.transform` 和 `event` hook。before 以 `callID + tool + args` 固化调用身份，after 只补状态、结果和耗时；
Skill 名只取 `args.name` 或 `metadata.name`。hook 热路径只入有界队列，后台微任务完成脱敏、序列化和批量提交；任何采集 I/O
异常都必须被插件吞掉并增加 dropped/error 事实，不能改变 OpenCode 返回。
其中 1.18.4 的 `experimental.chat.messages.transform` 输入对象为空，Session 必须从
`output.messages[0].info.sessionID` 取得；普通 Tool 抛错不会调用 `tool.execute.after`，必须从
`message.part.updated` 的 `part.type=tool/state.status=error` 补齐一次失败事实，而 `task` 失败会以空 output 调用 after。
这些分支均以只读快照中的真实 schema 和源码路径锁定，不能按其它 OpenCode 版本猜测字段。

对齐 DeepSeek Harness 的轨迹模型时，插件将 system/user/context/message/tool/subtool 作为闭集记录类型，使用 Turn/Step/Call
关联和 Input/Model/Tools 三泳道。`step-start`、首个非空 `message.part.delta`、`step-finish` 投影
`step-finish` 只暂存 usage，直到 `message.updated.info.time.completed` 才投影
`startedAt/durationMs/ttftMs/decodeMs` 以及 input/output/reasoning/cache-read/cache-write 五类 token；中断 Step
仍计入 steps/turns，但标记 `timingRecorded=false` 且不伪造墙钟耗时。每次
`session.idle` 再输出累计 `turns/steps/llmMs/toolMs/ttftMs/ttftSteps/decodeMs/decodeTokens`。这些是独立 Trace
事实，不写入 RunEvent，也不改变聊天终态判断。
子 Agent 的 `session.created.properties.info.parentID` 会把子会话绑定到根会话 Trace，后续子会话 Tool/Skill 事件沿用根 Trace ID；服务端再按
现有 `run_session_scope_sessions` MyBatis 映射补齐平台 Run ID，并校验 Run 属于专用令牌绑定用户。
专用令牌中的 generation 在公共启动程序写入 `opencode_server_processes.observability_generation`；该列不会被健康检查使用的
业务 `trace_id` 覆盖。插件请求必须同时匹配令牌 generation、当前数据库 generation 和运行中进程身份，旧代次一律拒绝。
令牌 TTL 由 `TEST_AGENT_OBSERVABILITY_TOKEN_TTL`（Spring 属性 `test-agent.observability.token-ttl`）配置，默认 7 天，
只接受 1 分钟至 30 天。该时限覆盖长期运行进程且不复用长期平台凭据；进程重启时 generation 变化会立即让旧令牌失效，
连续运行超过 TTL 后由公共启动/重启程序签发新令牌。

worker 镜像、完整发布包和本地客户端发布包必须包含该插件，发布校验脚本同时校验文件存在和 SHA-256。关闭或回滚插件时移除
launcher 注入即可；已经归档的 Trace/目录不自动删除。升级其它 OpenCode 版本前必须重新以真实 hook payload 运行
`tools/test-opencode-observability-plugin.mjs`，不能假定 1.18.4 的字段名继续有效。

## 交付、升级与回滚

完整升级包包含 worker image tar 和 `test-agent-programs.tar.gz`。镜像内置路径与外挂 programs 路径都使用 `/usr/local/lib/opencode` 布局，入口为 `bin/opencode`，官方程序为 `bin/opencode-official`，`RELEASE` 文件记录版本、asset、归档/二进制 SHA 和 release commit。

升级时必须同时替换 worker image 和 programs，再重启用户 OpenCode 进程；不能只覆盖单个二进制或 `node_modules`。1.17.8 回滚包使用同名官方 baseline asset（54,769,220 bytes）、GitHub release 记录的归档 SHA-256 `9b34bf34bdc66ea34ddd5858a131febf28b6247693acbfb5fb5c9ad94d90388b`，构建时还执行归档内程序的 `--version` 校验，并配套 1.17.8 plugin/SDK lockfile。回滚同样同时替换 image 和 programs，不清理用户 session、manager state 或平台数据库。

## 验证

```bash
node --test tools/test-opencode-official-launcher.mjs
node --test tools/test-opencode-observability-plugin.mjs
bun test ./tools/test-local-browser-native-transport.ts
node tools/benchmark-opencode-observability-plugin.mjs
tools/verify-opencode-runtime-gitignore.sh
tools/verify-opencode-tool-runtime-deploy.sh
deploy/internal/validate-opencode-models.sh deploy/internal/opencode-models.json
tools/generate-opencode-java-sdk.sh
mvn -f backend/pom.xml -pl test-agent-opencode-client,test-agent-opencode-runtime -am test
corepack pnpm --dir frontend vitest run packages/agent-chat/tests/opencode-like-state.test.ts
tools/verify-opencode-node-worker-image.sh test-agent-opencode-worker:internal
EXPECTED_OPENCODE_VERSION=1.17.8 \
EXPECTED_OPENCODE_ASSET_SHA256=9b34bf34bdc66ea34ddd5858a131febf28b6247693acbfb5fb5c9ad94d90388b \
EXPECTED_OPENCODE_SUBAGENT_DEPTH=unsupported \
tools/verify-opencode-node-worker-image.sh test-agent-opencode-worker:1.17.8
```

worker 验证覆盖 glibc 2.31、官方版本/asset 元数据、断网启动、健康检查、按统一认证号隔离的 PID 1 HOME/XDG/TMP/config 环境、`/path` 解析和各 `opencode` 普通子目录、两处自定义 Tool、1.18.4 的 `subagent_depth=2`、1.17.8 的旧配置兼容、配置目录与共享工作区祖先依赖链接、运行文件 Git 忽略规则和五秒内优雅停止。
