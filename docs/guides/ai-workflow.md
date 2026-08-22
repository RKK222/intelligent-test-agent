# AI 编码工作流

所有修改任务（Codex、Claude、人工开发）按本流程执行。本文件合并并取代原 `ai-coding-rules.md`、`task-workflow.md`。入口规范见 `AGENTS.md`。

## 0. 先选择目标分支

开始设计和修改前，先写明目标分支及判断依据。分支选择只有一个判据：交付是否必须新增部署节点；没有明确的新增部署节点需求时选择 `release`，不能仅因功能较大、改动复杂或存在部署影响而选择 `dev`。

### `dev`：仅用于新增部署节点

只有满足以下条件时才进入 `dev`：

- 交付方案必须在现有拓扑之外增加至少一个需要独立部署和运维的物理机、虚拟机或同等部署节点。
- 现有节点资源或职责无法承载目标能力，必须通过新增节点扩容或承载新能力；新增节点是交付前提，而不是可选优化。

新增 Java/Python/Go 服务、常驻进程、容器、独立 worker、端口、upstream、中间件、外部系统、凭据、强制环境变量、数据目录或资源配额，以及调整启动/停止顺序、网络拓扑、路由归属、安装、升级、扩容、灾备、回滚流程，本身都不触发 `dev`；只要这些变化由现有部署节点承载，就使用 `release`。API、事件、数据库或配置兼容风险、功能规模和跨模块协同范围也不是 `dev` 判据。

`dev` 中新增部署节点的功能不能因为代码已经完成就自动进入 `release`。只有交付范围得到明确确认，并完成部署说明、升级与回滚方案、兼容验证和目标环境运行验证后，才按功能选择性合入；`dev` 同时包含其它未交付节点能力时，禁止整体合并到 `release`。

### `release`：不新增部署节点时的默认交付分支

只要交付不新增部署节点，就进入 `release`，包括：

- 所有 Bug 修复和功能开发，不以功能大小、改动文件数或模块数量区分。
- 在现有节点内新增或调整服务、进程、容器、worker、端口、upstream、中间件、外部系统、配置、数据目录和资源配额。
- 调整现有节点的启动/停止顺序、网络和路由、安装、升级、扩容、灾备或回滚流程。
- 新增生产 Flyway migration、API、事件、数据库或配置兼容变更，以及需要多模块协同发布的能力。

上述变更仍必须按数据库、安全、API 和企业发布规范完成相应的存量升级、回滚、兼容性与真实环境验证，不能因位于 `release` 而降低交付门禁。如果实施中确认现有节点无法承载、必须新增部署节点，应停止继续按 `release` 交付，明确新增节点范围后再按用户或集成人指示处理分支切换；AI 不得自行切换长期分支。

### 跨分支同步（仅显式执行）

1. AI 不主动切换长期分支，也不因 `release` 改动完成验证就自动合并或 cherry-pick 回 `dev`；只有用户或集成人明确要求同步时才执行。
2. 收到同步要求后，先列出精确提交和文件范围；回合 `dev` 时保留 `dev` 已有的新增节点能力和默认关闭策略，不用 release 文件覆盖 dev 的对应实现。
3. 从 `dev` 提升功能到 `release` 前，先列出精确提交和文件范围，确认没有夹带其它未批准的新增部署节点或关联能力。
4. `main` 只按明确的稳定基线发布决策更新，不作为日常功能开发目标。

## 1. 读文档与定位

1. 先读 `AGENTS.md` 和 `docs/README.md`，确认任务类型对应文档。
2. 后端任务读 `backend/README.md`、目标模块 `README.md`，再读 `docs/standards/backend.md` 和 `docs/architecture/dependency-rules.md`。
3. 前端任务读 `frontend/README.md`、目标 app/package `README.md`，再读 `docs/standards/frontend.md`。
4. API、事件、数据库、安全、性能任务必须读 `docs/api/`、`docs/deployment/database.md`、`docs/standards/security.md`。
5. 涉及 OpenCode 行为、版本、SDK 或源码参考时，必须读 `docs/standards/opencode.md` 和对应升级文档；`opencode-source/` 只读，平台适配不得通过修改快照实现。
6. 用 `rg`、`find` 定位相关代码，确认入口、调用链、依赖方向和测试位置；确认是否涉及 generated SDK、数据库 migration、API 或事件文档。
7. 先分析影响范围（分支归属、部署拓扑、兼容性、安全、性能、错误处理、可观测性），再开始修改；不允许在未理解边界时直接搜索替换或大范围重构。

## 2. 修改范围

1. 只改与任务直接相关的最小范围。
2. 不顺手重命名、格式化无关文件、调整无关依赖。
3. 遇到无关问题记录风险，不在当前任务扩大范围；必须扩大时在结果中说明原因和影响。
4. 保持既有包结构和模块边界，新增后端文件前先按 `docs/architecture/dependency-rules.md` 列出现有合适工程，无合适工程时按业务边界新建 Maven module。

## 3. 注释要求

1. 人工维护代码新增类、接口、方法、复杂逻辑、状态流转、边界和异常分支必须写中文注释或中文 Javadoc，说明意图、关键入参、返回语义和边界条件。
2. 注释解释业务意图和原因，不重复描述显而易见的赋值。
3. generated SDK 不手工补注释。

## 4. generated SDK

1. `backend/test-agent-opencode-sdk-generated/` 是从 opencode OpenAPI spec 生成的 Java 源码，禁止手改。
2. SDK 变更必须先运行 `tools/generate-opencode-java-sdk.sh` 重新生成，再按规范同步到后端模块。
3. 生成代码编译失败只能修 generator 配置、spec 元数据处理或依赖配置，不能直接改 generated Java。
4. 除 `test-agent-opencode-client` 外，业务模块不得直接 import `com.example.opencode.sdk.*`。

## 5. 文档同步

任何修改都必须检查是否需要同步：

- 根目录或工程 `README.md`、模块/包 `README.md`。
- `docs/api/`（HTTP 接口、SSE 事件）。
- `docs/deployment/database.md`（migration）。
- `docs/standards/`、`docs/architecture/` 相关规范。
- `docs/standards/opencode.md` 以及 OpenCode 升级/兼容性文档（涉及 OpenCode 行为或版本时）。

代码、接口、配置、数据结构和行为变更不能只改实现不改文档。`requirements/` 下的历史文档不是编码依据，不在此同步范围。

## 6. 测试与构建

1. 后端默认 `mvn clean package -DskipTests`，需要 JDK 21；涉及行为逻辑时补并运行对应单元/集成测试。详细分层与命令见 `docs/standards/backend.md`。
2. 前端命令统一通过 Corepack 调用 pnpm（`corepack pnpm lint|typecheck|test|build|e2e`），详见 `docs/standards/frontend.md`。
3. API、事件、数据库、前端交互改动按对应专题测试规范执行。
4. 测试失败或未覆盖的风险必须在回复中说明。
5. Flyway 改动不得只测空库。先留存所有目标环境 `flyway_schema_history` 的 `version/checksum/success`，再用真实 PostgreSQL 分别验证空库、已部署企业基线和每套已知分叉历史。已执行 migration 的原始字节和 checksum 必须由回归测试锁定。
6. 企业包构建后必须从最终 JAR/ZIP 解出 migration 计算 SHA-256，确认与通过升级测试的源码完全一致。源码测试通过但包内资源未校验时，不得声称可部署。

## 7. 本地服务重启

需要重新编译并重启本地前后端联调服务时，从仓库根目录执行：

```bash
./restart-dev-services.sh
```

本机 `test` 验收固定读取根目录 `.env.test`，其 PostgreSQL 默认使用 `192.168.8.100:15432/testagent_dev`。不得为了让本机启动或某个页面临时可用而改用本机 PostgreSQL、`.env.local` 或临时 dotenv；只有用户明确要求时才切换数据库。`.env.test` 将 `TEST_AGENT_OPENCODE_BASE_URL` 指向远端时，脚本按设计跳过本机 opencode-manager，这不表示重启失败。

共享库会持久化工作区和用户 Agent 的服务器归属。重启后进入对话前必须同时确认：所选工作区与当前用户 ACTIVE Agent binding 的 `linuxServerId` 一致；目标服务器已存在且可读取工作区根目录。不得只更新 `workspaces.linux_server_id` 后就认为搬迁完成。同一共享验收账号不要轮流在本机和远端初始化 Agent；确需双端验证时使用不同的专用测试账号。发现 `工作空间与 agent 不在同一服务器` 时，检查个人工作区迁移状态和目标物理目录；迁移连续进入 `RETRY_WAIT` 不是等待即可恢复的瞬时状态，应保留两端数据并处理目标冲突或鉴权问题，禁止绕过同服与路径校验。

从独立 Git worktree 运行当前分支、但需要复用主工作区已经初始化的本机测试数据时，不能只把主工作区的 `.env.test` 作为 `--env-file` 传入。启动脚本会把兼容变量 `TESTAGENT` 默认设置为当前 worktree，而存量 macOS 测试库的 `SYS_DATA_ROOT_DIR` 通用参数可能仍保存为 `$TESTAGENT/.testagent`；此时用户进程会把公共配置解析到当前 worktree 的空目录并报“公共 Agent 配置源目录不可用”。使用下面的完整命令块，同时保留当前 worktree 作为代码根、主工作区作为持久化数据根：

```bash
test_agent_primary_root=/absolute/path/to/intelligent-test-agent
test_agent_worktree_root=/absolute/path/to/feature-worktree
cd "$test_agent_worktree_root"
export JAVA_VERSION=25
export JAVA_HOME=$(/usr/libexec/java_home -v "$JAVA_VERSION")
export PATH="$JAVA_HOME/bin:$test_agent_worktree_root/.tmp/dev-bin:/opt/homebrew/opt/libpq/bin:$PATH"
export TESTAGENT="$test_agent_primary_root"
export SYS_DATA_ROOT_DIR="$TESTAGENT/.testagent"
"$JAVA_HOME/bin/java" -version
./restart-dev-services.sh --profile test \
  --env-file "$test_agent_primary_root/.env.test"
```

不要显式把 `TEST_AGENT_ROOT` 改成主工作区；它应继续由脚本设置为当前 worktree，确保构建产物、运行 JAR 和日志都属于当前分支。`TESTAGENT` 负责 Java 对历史 `$TESTAGENT/...` 通用参数的展开，`SYS_DATA_ROOT_DIR` 负责启动脚本写入并让 manager 读取同一份 `.serverid/.serverhost`，两者必须指向同一数据根。

当 dotenv 已启用 ClickHouse 且 JDBC 地址精确指向项目托管的本机
`127.0.0.1|localhost|[::1]:18123/testagent_analytics` 时，默认重启会自动进入本地 ClickHouse 管理路径，启动固定
版本容器并用 `.tmp/dev-services/clickhouse` 中同一套运行凭据覆盖 Java 配置；无需另外添加参数。其它地址不会被
自动接管。需要在 dotenv 未启用时强制联调运营分析，可显式增加 `--with-clickhouse`；需要同时验证通用记忆时再加
`--with-memory`。本地 ClickHouse 只监听回环地址，运行密钥和 Java JDBC 配置不写回 `.env.test`；数据由版本化
Docker volume 保留，停容器或重启平台不得删除该 volume。

LobeHub 默认不参与本地重启；只有需要企业问答联调时显式执行
`./restart-dev-services.sh --profile test --env-file .env.test --with-lobehub`。该模式要求同级
`../lobehub-platform`，生成的密钥只写入 `.tmp/dev-services/lobehub-dev.env`，不修改任何 `.env.local`；开发
helper 会同时启动并在停止时回收 fork 自带的 loopback 定时调度进程。显式模式只接受回环平台 PostgreSQL，
通过后端现有通用参数审计服务初始化本地 origin、邮箱域和唯一 owner，最后启用入口；候选 owner 不唯一时使用
`TEST_AGENT_LOBEHUB_DEV_OWNER_UNIFIED_AUTH_ID` 明确指定。helper 使用兼容仓库临时 shim 的 `corepack pnpm`
调用形式，并在启动容器前核对 fork `packageManager` 锁定的 pnpm 版本。生成的运行环境会把聊天自身 origin 与
`TEST_AGENT_FRONTEND_URL` 对应的平台前端 origin 一并写入 Better Auth 的可信来源，保证平台 POST 一次性票据时不被
origin 校验拒绝。参数初始化或 fork readiness 失败时会审计并补偿关闭入口。

需要联调 LobeHub Community 当前在线 Agent 时，改用
`./restart-dev-services.sh --profile test --env-file .env.test --with-lobehub --lobehub-mode online`。
在线模式使用 LobeHub 自身登录并开放 Marketplace 网络请求，同时关闭只适用于企业离线模式的平台票据入口；
生成设置仍只写入 `.tmp/dev-services/lobehub-dev.env`。完成在线联调后，企业交付验收必须重新以默认
`offline` 模式启动。

脚本默认使用 `test` profile、读取 `.env.test`、先编译后端和自研前端，再按「后端 → 可选的 opencode-manager → 前端」逐个 kill 旧进程并启动新进程。只有 OpenCode 地址为本机，或显式设置 `TEST_AGENT_START_OPENCODE_MANAGER=true` 时才启动本机 manager；OpenCode 地址为远端且未强制启动时会输出跳过提示。后端启动前会从 `deploy/internal/experience-workspace-template/` 仅补齐体验目录缺失的标准文件，不覆盖已有内容。前端构建和 dev server 启动前会检查 `frontend/node_modules/.modules.yaml` 是否落后于 `pnpm-lock.yaml`、workspace 配置或各包 `package.json`，过期时自动执行 `corepack pnpm install --frozen-lockfile`。后端构建完成后会校验 Maven `target` JAR，并复制为 `.tmp/dev-services/backend-runtime/` 下本次启动专属的不可变副本；Java 只运行该副本，避免并行企业打包或其它 Maven 构建覆盖 `target` 后破坏 Spring Boot 的按需类加载。后端 Java 进程同时会清空 JVM 代理系统属性，避免本机系统代理影响 PostgreSQL JDBC 与 Redis 直连。macOS 上若没有显式 `HTTPS_PROXY`/`https_proxy`，脚本会把已启用的静态系统 HTTPS（或 HTTP）代理仅传给 opencode-manager 及其 OpenCode 子进程，避免 Bun 绕过系统代理后经 TUN 假地址出现间歇性 TLS 失败；显式代理优先，`TEST_AGENT_OPENCODE_USE_SYSTEM_PROXY=false` 可关闭自动探测，证书校验不会被放宽。停止 opencode-manager 时会同步清理其 state 目录中记录的用户 `opencode serve` 子进程、端口池内残留监听进程和 `.tmp/dev-services/opencode-manager-state/processes/*.json`，避免重启后旧端口状态继续占用；新 manager 注册时，后端会在开放控制连接前冻结数据库仍为 `RUNNING/STARTING` 且 binding 为 `ACTIVE` 的原用户进程，完整配置应用并发送首个心跳后自动恢复，显式停止、失败、非活跃或无主进程不会恢复。需要连接 `local` 或 `guo` 环境时显式传入 `--profile local|guo` 和对应 dotenv 文件。服务日志写入 `.tmp/dev-services/`，不得打印 dotenv 中的敏感值。

Windows 需要联调当前 test 环境时，可在 PowerShell 中执行 `powershell -ExecutionPolicy Bypass -File .\restart-dev-services.ps1 -Profile test -EnvFile .env.test`；脚本同样只解析 dotenv 的 `KEY=VALUE` 行，不执行文件内容，并按后端、opencode-manager、前端顺序重启。WSL/Git Bash 中继续使用 `./restart-dev-services.sh --profile test --env-file .env.test`。仅启动 Java 后端时，可用 IDEA Run Configuration，但必须把 `.env.test` 中的数据库、Redis、模型和 manager token 环境变量配置进去并使用 `-Dspring.profiles.active=test`；已提交的 `TestAgentApplication guo` 只服务 legacy guo profile。

开发脚本变更后，必须运行轻量校验，确认根目录重启脚本在 Bash 入口和误用 `sh` 入口下都不会解析失败：

```bash
tools/verify-dev-scripts.sh
```

后端单独启动可用 `tools/dev-backend-run.sh [--profile test|guo] [--env-file <path>]`；脚本只解析 `KEY=VALUE` 行，不执行 dotenv 内容，并同样清空后端 JVM 代理系统属性。

## 8. 自检与提交

1. 完成前按 `docs/guides/self-checklist.md` 自检，并在回复中说明目标分支、分支归属依据和验证结果。
2. 提交前必须回顾所有 `.agents/session-log*.md`（含已冻结的 `.agents/session-log.md` 旧档和各 `.agents/session-log.{id}.md`）中近期条目记录的变更、坑点和未完成事项，确认本次暂存内容不会覆盖、丢弃或误合并其他开发者/智能体已经提交的成果。
3. 如果本次会话收尾时确有值得保留给后续开发者和智能体的新增信息，按本机提交者身份写入对应的 `.agents/session-log.{id}.md`（`{id}` 为清洗后的 `git config user.name`，转小写、连续非 `[a-z0-9]` 字符折叠为单个 `-` 并去首尾 `-`，为空时回退 `hostname -s`），用 `Why / What / How / Result` 记录交接信息；同一会话内的零散改动合并为一条，不按文件或步骤频繁追加；不再向已冻结的 `.agents/session-log.md` 追加。
4. 自检通过后提交 git，commit message 使用中文并准确概括修改。
5. `.agents/session-log.{id}.md` 属于仓库内容，应随本次提交一起保留；如需要同步到远程，随代码一并 push。旧的共享 `.agents/session-log.md` 已冻结为历史归档，仅回顾时阅读，不再续写。
6. 只暂存本次任务相关文件，工作区无关改动保持原状。
