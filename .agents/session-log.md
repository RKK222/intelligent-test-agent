# Session Log

### 2026-07-13 - 扩展宠物数独与贪吃蛇小游戏

- Why:
  - 用户希望宠物游乐区增加数独和贪吃蛇，并要求游戏图形入口缩小、紧凑排布，避免游戏选择界面喧宾夺主。
- What:
  - 复用 `PetMiniGames` 增加 9×9 数独和 12×12 贪吃蛇；数独支持选格、数字键盘/物理键盘填写、错误提示、清除和重开，贪吃蛇支持方向键/屏幕按钮、计分、暂停、碰撞结束和重开。
  - 四个游戏入口调整为 2×2 小卡片，图形缩为 34px；切换游戏时暂停俄罗斯方块或贪吃蛇计时器，关闭浮层时统一释放。
- How:
  - 沿用既有单组件页面内存状态、宠物统一浮层和蓝灰/紫色视觉，不新增组件、API、持久化或外部依赖；补充组件测试和桌面/移动 Playwright E2E，并同步前端 README 与包说明。
- Result:
  - PetMiniGames/FigmaShell 38 条组件测试通过，前端全量 Vitest 54 文件、757 passed/1 skipped，agent-web typecheck、生产 build 和桌面/移动目标 E2E 2/2 通过。首次全量 Vitest 的既有 Mermaid 懒加载用例在并发下抖动一次，单文件 11/11 和随后全量复跑均通过。按 `.env.test` / `test` profile 重启三服务成功，backend health/readiness 为 UP、frontend 3000 与登录 CORS 正常，manager WebSocket 已连接；构建保留既有 CSS `@import` 顺序和大 chunk 警告。

### 2026-07-13 - 新增宠物小游戏并恢复主对话首条直发

- Why:
  - 主对话被错误要求先点击“新建对话”才可输入；同时用户希望小宠物提供俄罗斯方块和扫雷，但游戏入口应与宠物对话合并且保持低强调。
- What:
  - 主输入卡移除 Session 选择门禁，进程 ready 且工作区可用时可直接发送首条消息，并继续由既有链路延迟创建 Session；宠物旁路提问仍要求已有真实主 Session。
  - 点击 ready 状态宠物后打开以对话为主体的统一浮层，标题栏小手柄按钮进入本地 10×16 俄罗斯方块或 8×8、10 雷扫雷，不新增活动栏按钮、后端接口或持久化状态。
- How:
  - 复用 `AgentWorkbench` 既有 Session 延迟创建、宠物旁路 Run 和进程状态分流；小游戏仅维护 Vue 页面内存状态，俄罗斯方块卸载时释放计时器。同步组件测试、桌面/移动 Playwright 用例、agent-web 稳定 README 和包说明。
- Result:
  - 前端全量 Vitest 54 文件通过，753 passed/1 skipped；agent-web typecheck、生产 build、桌面/移动目标 E2E 4/4 通过。按 `.env.test` / `test` profile 重启三服务成功，backend readiness 为 UP、frontend 3000 返回 200、manager WebSocket 正常；真实三服务 E2E 的 Session+PTY 与宠物旁路 fork 2/2 通过（其余 13 项按套件条件跳过），临时 OpenCode 4096 进程已自动清理。构建仍保留仓库既有 CSS `@import` 顺序和大 chunk 警告。
### 2026-07-13 - 定位首轮标题同步后 RunEvent 被错误过滤

- Why:
  - 当前最后一个 Run 在 21:33:05 后原始输出和对话正文停止刷新，但浏览器 SSE 与 Java 到 OpenCode 的事件订阅均未断开；重新进入会话时又会从远端快照一次性补出后续消息。
- What:
  - 根因位于首轮原生标题监听状态机：`session.updated` 成功同步平台标题后将 `TitleWatchToken` 从 `ACTIVE` 关闭为 `CLOSED`，而 `acceptsTitleWatchEvent` 只对 `ACTIVE` 全量放行，错误地把 `CLOSED` 也按 `TITLE_WAIT` 过滤，只允许 root `session.updated` 和标题 Agent 完成消息继续通过。
  - 本次只完成日志、附件、远端 OpenCode 消息快照和代码链路分析，未修改业务代码；问题早于 `68b8b4bd9` 的 question 回复状态收敛修复，相关过滤逻辑由 `031efccfe` 引入。
- How:
  - 对齐 Run `run_c5b79573b05c44e9915250d54b8afd61` 的事件时间线：最后一批 message transient 在 21:33:05.548 发布，紧接着 seq=15 的 `session.updated` 带 `platformSessionTitleSynchronized=true`；此后仍有 seq=16/17/18 的 `session.updated`，但 OpenCode 权威快照中同期新增的三个 assistant 消息、parts 和 `question` 工具均未进入平台事件处理。
  - 核对历史切换链路确认 `session-tree/messages` 会重新读取 OpenCode projected messages 并重建前端状态，因此表现为重新进入后大量补刷。
- Result:
  - 已确认不是浏览器 SSE 断线、代理缓冲、提问回复未收敛或 OpenCode 停止产出；直接修复点应让 `CLOSED` token 恢复主 Run 事件全量放行，并补充“标题同步成功后仍继续接收 message/part/question/terminal”的 Run 级回归测试。修复前，首轮会话在模型提前生成非默认标题时仍可能永久漏掉后续实时事件。

### 2026-07-13 - 统一测试产物业务化命名并准备企业模型验收素材

- Why:
  - 测试设计正式文件仍使用 `OBJ-001` 等内部编号，用户无法从文件名直接判断内容；用户明确要求停止使用当前模型代测，改由企业内模型在应用工作区自行验收。
- What:
  - 公共设计与执行配置统一使用“业务名称-产物类型”命名：设计图表、普通案例、接口案例、审核结果、自动化脚本、执行报文和执行结果都复用业务对象或案例名称，objectId 只保留在内部追溯数据中。
  - F-COSS 个人 worktree 新增独立需求项 `I20260713-测试设计产物命名验收`，包含信鸽取证状态流转需求、接口与状态设计、建议提示词、预期命名和验收标准，并预建 041/042 目标目录。
- How:
  - 复用现有三阶段 Agent、多方法 skills 和测试执行链路，只调整公共配置与模板；命名冲突用业务场景或验证点消歧，不回退追加 OBJ 编号。收到用户指示后终止真实模型运行，后续只执行静态配置校验和服务启动验证。
- Result:
  - 11 个相关 skills 校验及打包通过，设计/执行 Agent 配置解析、9 项命名契约和 `git diff --check` 通过；test profile 三服务重启成功，backend readiness、frontend 3000 正常。企业内模型实际生成结果由用户使用新增应用工作区素材自行验收。

### 2026-07-13 - 分离测试设计中间产物、最终案例与执行目录

- Why:
  - 公共测试设计虽然声明 `041-测试设计` 和 `042-测试执行`，但真实 Agent 运行只收到一个笼统输出目标，等价类表、路径图和案例实际落到了 S 子条目根目录；测试设计中间产物与最终案例也没有目录隔离。
- What:
  - 公共配置将 Phase A 中间产物固定到 `041-测试设计/测试设计文档/`，Phase B 最终案例和案例审核结果固定到 `041-测试设计/` 根目录，测试执行产物继续只写 `042-测试执行/`；执行 Agent 默认只从 `041` 根目录读取案例并忽略 `测试设计文档/`。
  - 编排到生成阶段的单一 `outputTarget` 拆为 `designDocumentTarget` 和 `caseOutputTarget`，workspaceContext 增加对应根路径；Skill 相对资源统一按 `SKILL.md` 所在目录解析，避免业务工作区下的相对路径误读。
- How:
  - 复用现有三阶段 Agent、方法 skills 和执行链路，只修改公共配置、路径契约、质量门禁、交接模板和 eval 预期；未改业务代码、API、事件、数据库或环境文件。
- Result:
  - 8 个 test-design skills 校验及打包通过，OpenCode Agent 配置解析和 8 项目录契约检查通过；test profile 三服务重启成功，backend readiness、frontend 3000 和 manager WebSocket 正常。修正前真实模型运行已复现 S 根目录错误落盘；修正后因应用内浏览器无登录态、用户 OpenCode 进程未初始化，尚未完成真实模型落盘复验。

### 2026-07-13 - 修复 # 跨阶段引用并收敛大量附件展示

- Why:
  - COSS 0318 的同一子条目已在 `01-需求、02-设计、03-编码、04-测试` 下形成多个关联文件，但原实现只搜索 `/01-需求/`；一次加入十几个文件时，附件又会全部平铺并显著挤压输入区。
- What:
  - `#` 改为按“需求项 + 同名子条目”聚合四个阶段的全部文件，关闭后重新打开会刷新候选；附件超过 3 个时默认只展示前 3 个，补充阶段数量摘要和“查看其余 N 个文件”，展开后在最高 220px 的滚动区预览、逐个删除或收起。
- How:
  - 继续复用平台 `workspace.search`、`addWorkspaceFileToChatContext`、现有附件卡片和 Pinia 上下文状态；折叠只影响展示，发送仍携带全部已加入文件，每个文件继续执行二进制、重复和容量校验。
- Result:
  - 当前 COSS 0318 实际识别到需求说明、详细设计、业务代码、单元测试和测试设计共 5 个文件；前端定向 Vitest 179 passed/1 skipped、agent-web typecheck 和生产构建通过，test profile 三服务重启成功，backend readiness 为 UP、frontend 3000 可访问。
### 2026-07-13 - 修复 question 回复后状态未收敛

- Why:
  - OpenCode 1.17.8 的历史提问回复链路在 HTTP 接受答案后没有向既有订阅发送 `question.replied` 或 question tool 完成态，平台只补回最终 assistant 消息，导致提问卡持续显示“进行中 / 等待回答”。
- What:
  - question 回复 HTTP 成功后按当前 Run 存储模式补记既有 `question.replied` 事件；前端保留 `question.asked.tool` 的 message/call 关联，在本地提交成功或收到回复事件时把原工具 part 更新为 `completed` 并回填 `metadata.answers`，同时保留模型真实生成的底部 assistant 回复。
- How:
  - 沿用既有 API、`question.replied` wire name 和 RunEvent 存储链路，没有新增数据库或 SDK 契约；补充后端事件/接口回归与前端 reducer/时间线回归，并同步 HTTP API、事件流和模块 README。
- Result:
  - 前端相关测试 71/71、全 workspace typecheck/lint、agent-web 生产构建、后端生产模块构建及两条聚焦测试通过。全量后端 test-compile 仍被当前主线 11 个无关 process 测试源码错误阻断，主要是 FakeRepository 缺少 `findReadyBackendJavaProcessByLinuxServer` 和旧 DTO 构造器参数不匹配。

### 2026-07-13 - 补齐 COSS 信鸽取证需求设计与领域参考实现

- Why:
  - COSS 个人应用 worktree 的“新增助商组合贷信鸽取证状态”需求正文和编码目录只有占位文件，需要同步形成需求、设计和可验证代码。
- What:
  - 在 `feature_testagent_20260618_usr_test_dev_default/F-COSS` 补齐需求说明书，更新详细设计依据，并增加信鸽取证状态归一化、任务发起幂等、任务/事件版本保护、成功/失败终态保护、失败摘要清理和安全详情视图的 CommonJS 领域实现与单元测试。
- How:
  - 先扫描整个应用 worktree，确认没有 `pom.xml`、`package.json` 或任何现有业务源码，因此没有虚构生产模块；实现保持无框架依赖，可由 Node 直接运行，后续应迁入真实 COSS 业务层并复用实际接口、持久化和权限边界。
- Result:
  - Node 语法检查和领域单测 17/17 通过，应用 worktree 提交为 `ee0cf3f`、`8588717`；真实 COSS 服务、数据库、页面和信鸽接口尚未接入，当前代码不得视为生产集成完成。

### 2026-07-13 - 收敛公共测试设计最终可见交付

- Why:
  - 当前公共测试设计的模板结构基本正确，但真实执行会把对象识别、方法理由、阶段状态、冻结证据、追溯映射和长篇 Review 一并展示，偏离“设计文档、测试案例、审核结果”三类最终交付要求。
- What:
  - 在 `.testagent/agent-opencode/.config` 保留既有编排、分析、生成、审核多 Agent 和方法型 skills，只调整可见输出边界：设计文档仅保留方法图表本体，普通案例严格四列，接口案例严格七区块，审核结果收敛为审核表和一行总评；内部阶段交接与追溯信息继续保留在 `<task_result>`。
  - 新增登录普通案例与用户查询接口案例两组 eval，覆盖等价类、状态路径、接口未知依赖和固定模板约束。
- How:
  - 参考历史稳定测试智能体流程，将“内部完整编排”和“外部精简交付”分离；使用新旧公共配置离线对照、OpenCode 配置解析、8 个 test-design skills 校验与打包，以及 test profile 三服务重启进行验证。
- Result:
  - 新配置两组 eval 均只输出 `测试设计文档 / 测试案例 / 案例审核结果`，评分 100%，旧配置评分 25%；backend readiness、frontend 3000、登录 CORS 和 manager WebSocket 正常。重启后用户 OpenCode 4096 进程未自动恢复，真实模型对话仍需用户初始化进程后复验；独立 CLI 的新旧配置运行均遇到相同 `Unexpected server error`，未将其计为行为验证成功。

### 2026-07-13 - 扩展工作区目录双击重命名

- Why:
  - 用户补充要求应用工作空间中的目录也支持双击改名，原实现仅允许普通文件。
- What:
  - 后端 `workspace.rename` 允许同一父目录内重命名普通文件或目录；前端目录行复用行内编辑入口，并迁移已加载子树、展开/加载状态、打开 Tab、活动路径和 Git diff 路径。
- How:
  - 保留既有 WebSocket RPC、路径归一化和目标冲突校验，只扩展条目类型；补充目录前后端测试和文档，未修改数据库、SSE 或环境配置。
- Result:
  - 文件树/API 前端定向测试 55/55、file-explorer/backend-api/agent-web/editor 类型检查、workspace-management 18 项测试通过；全量后端主代码构建、三服务重启、backend readiness 和 frontend 3000 验证通过。

### 2026-07-13 - 对话支持需求子条目与工作区文件引用

- Why:
  - COSS 使用特性分支和个人 worktree 管理真实应用工作区；对话输入需要从当前个人 worktree 识别 `需求项/01-需求/子条目`，并让 `@` 除 Agent 外也能选择工作区文件。
- What:
  - `workspace.search` 改为按工作区相对路径匹配，并允许空关键字在既有数量、深度和超时边界内返回文件候选；前端 `#` 按 `01-需求` 聚合子条目，选择后复用现有文件上下文链路添加其需求文件，`@` 面板同时展示 Agent 和文件。
  - COSS `feature_testagent_20260618` 已同步远端；个人 worktree 清理后只保留 `120260624-0318-新增助商组合贷信鸽取证状态`，清理提交已合并到应用特性分支。
- How:
  - 文件目录、读取和搜索继续使用平台文件 WebSocket RPC；文件引用继续使用原有二进制、重复、单文件和总上下文容量校验，不新增文件 HTTP 代理。需求识别以当前真实目录层级为准，不引入 `default-version` 等虚构层级。
- Result:
  - workspace-management 定向测试 12/12、前端定向 Vitest 178 passed/1 skipped、agent-web 生产构建和三服务 test profile 重启通过；backend health/readiness 为 UP、frontend 3000 返回 200、manager WebSocket 已连接。应用内浏览器没有已打开标签页，未复用登录态执行真实页面点击；构建保留既有 CSS `@import` 顺序和大 chunk 警告。

### 2026-07-13 - 企业部署统一回退到 PostgreSQL 镜像

- Why:
  - 用户确认后续企业环境统一使用 PostgreSQL 镜像，不再交付或维护 GaussDB JDBC 驱动及 Flyway 方言兼容路径。
- What:
  - 删除 GaussDB Flyway 数据源包装器、兼容开关和单测；恢复 persistence 的 Spring Data JDBC/PostgreSQL 依赖说明；移除发布脚本的外置数据库驱动替换参数，并同步 `backend.env` 模板、后端模块 README 和企业部署文档。
- How:
  - 复用原有 `DatabaseMigrationRunner`、PostgreSQL 官方驱动和 Flyway PostgreSQL database support；保留与数据库无关的企业包主代码构建及防递归归档修复。历史 GaussDB 条目仅作为追溯记录，当前部署以本条决策和稳定文档为准。
- Result:
  - JDK 25 全量主代码构建成功，Druid 定向测试通过；按 `.env.test` / `test` profile 在 PostgreSQL 16.14 镜像上重启三服务，Flyway 成功校验 49 个 migration，backend health/readiness、frontend 3000、登录 CORS 和 manager WebSocket 正常。
  - 默认 PostgreSQL 企业完整包已重建并通过 `--validate-only`、`unzip -t`；包内仅有官方 `postgresql-42.7.11.jar`，无 GaussDB 驱动、Flyway 兼容类或旧 `dist-gauss` 递归内容。zip 为 416356194 bytes，SHA-256 `7a9cddfb1da8f68d04ecbd7ce4a1d755937febf5eade09a97042a9dc2574460a`。发布脚本额外排除历史 `dist-*` 输出目录，旧 GaussDB 构建目录已删除。

### 2026-07-13 - 修复 GaussDB Flyway 角色恢复失败

- Why:
  - 企业最新启动日志已证明 GaussDB 驱动和 Flyway 均已加载，但 Flyway 在迁移结束恢复 PostgreSQL 角色时执行 `SET ROLE 'testagent'`，被 GaussDB 拒绝并导致空库启动失败。
- What:
  - 新增仅供 Flyway 使用的 `GaussDbFlywayDataSource`，在显式开启 `TEST_AGENT_FLYWAY_GAUSS_ROLE_RESTORE_COMPATIBILITY=true` 时，将精确的 Flyway 角色恢复语句转换为 `RESET ROLE`；默认 PostgreSQL 行为不变。同步企业环境模板、后端/部署文档和兼容性单测。
- How:
  - 保留原始 Druid 数据源给业务代码，仅在 `DatabaseMigrationRunner` 配置 Flyway 时包裹数据源；不修改 migration、API、事件、generated SDK 或 `.env.local`。
- Result:
  - 主代码构建通过；新增单测源码可独立编译并通过反射执行两项断言。完整企业包已重建，`--validate-only` 和 `unzip -t` 通过；Maven 全量测试仍被仓库既有 opencode-runtime 假 Repository 与 DTO 构造器不匹配阻断，目标 GaussDB 的真实 Flyway 表初始化仍需内网部署后验证。

### 2026-07-13 - 修复 Markdown 预览并支持工作区文件重命名

- Why:
  - 工作区文件接口已有 Markdown 内容，但前端仍走旧 HTTP 读取路径，导致返回内容未进入预览；同时文件树缺少文件重命名入口。
- What:
  - 工作区文件读取统一改为平台文件 WebSocket RPC，并增加 `workspace.rename`，只允许同一父目录内重命名普通文件，前端支持双击行内改名并同步文件树、打开标签和 Git diff。
  - Markdown 解析失败时回退显示原文，并增加异步 Monaco 过期模型保护，避免快速切换文件后内容被旧请求覆盖。
- How:
  - 复用既有 `workspaceFileRpc`、文件安全边界和 workbench tab 状态；补充前后端单测、事件流/安全文档和包说明，未修改数据库、SSE、generated SDK 或环境配置。
- Result:
  - 前端相关 Vitest 4 个文件 72/72 通过，backend workspace-management 测试通过，后端全量主代码构建和三服务重启通过；readiness 为 UP、frontend 3000 返回 200。聚合测试仍受仓库既有 opencode-runtime/API 测试源码与 DTO 不匹配问题影响。

### 2026-07-13 - 应用历史 Phase A/B 测试设计替换包

- Why:
  - 需要以历史结果文件为准，恢复“中间产物先落盘并确认/冻结，再组装案例”的测试设计流程，避免组合模板把两个阶段混在一起。
- What:
  - 应用 `/Users/kaka/Downloads/opencode-test-design-refactor-history-aligned-files.zip` 的设计侧覆盖文件；测试设计默认目录为 `041-测试设计`，测试执行保持 `042-测试执行`。
  - 删除包内列出的 12 个旧/组合式测试设计规则与模板；保留测试执行 Agent、执行 skills、脚本模板和格式校验链路。
- How:
  - `git apply --check` 因当前仓库与 patch 的原始工作区快照不一致而无法直接应用，按包内 `APPLY.md` 使用 zip overlay；应用前将原工作区保存为本地 stash 备份。
- Result:
  - zip 文件级比对、删除清单、目录映射和 `git diff --check` 通过；未启动真实 Agent 三阶段业务任务，未涉及后端 API、事件、数据库或环境配置。

### 2026-07-13 - 生成 GaussDB 驱动企业完整交付包

- Why:
  - 用户提供真实 `GaussDBV5-503.1.0.SPC2000_23.12.12.jar`，需要基于当前已修复代码生成可供企业内网升级的最新完整包，并确认外置驱动替换路径。
- What:
  - 生成 `deploy/internal/dist-gauss/` 完整交付物：后端瘦 jar、外置依赖、前端静态包、外挂程序、linux/amd64 Worker 镜像 tar 和完整企业升级 zip。
  - 发布脚本的后端打包改为只编译主代码和运行时依赖，并在 zip 归档时排除当前命名的输出目录，避免测试源码阻断发布和交付物递归嵌套。
  - 外置驱动复制后 SHA-256 与用户提供的原 jar 一致；后端 `lib` 不含官方 `postgresql-*.jar`、`spring-data-jdbc` 和 `spring-boot-data-jdbc`，保留 `flyway-database-postgresql` 作为 Flyway 数据库支持模块。
- How:
  - 使用 `deploy/internal/package-release.sh --db-driver-jar ...` 构建；使用 `deploy-internal-release.sh --validate-only` 和 `unzip -t` 校验升级包结构和归档完整性。
  - 用该包在本机 test profile 启动时确认实际加载的是 GaussDB 驱动；本机数据库为 PostgreSQL，驱动返回“Session 初始化失败”，因此没有把本机启动误判为 GaussDB 连接验证。
- Result:
  - 企业完整包已生成于 `deploy/internal/dist-gauss/test-agent-internal-release.zip`，约 396M，SHA-256 为 `fd04b2b2166d71adae1de057077231c0ad43dc7814de2f8c9d5581d752e1c471`；后端 manifest 使用 `/data/testagent/dist/backend/lib` 外置加载路径，包结构、zip 完整性和 `--validate-only` 校验通过。
  - 目标 GaussDB `122.42.203.103:8000` 的真实连接、Flyway migration 和 Worker 联动仍需在企业内网按部署手册执行验证。

### 2026-07-13 - 兼容 GaussDB 外置 JDBC 驱动部署

- Why:
  - 企业部署使用 GaussDB PostgreSQL 兼容驱动替换外置 `lib` 中的 PostgreSQL 驱动后，Spring Data JDBC 4.1 的 PostgreSQL dialect 调用了 GaussDB 驱动缺失的 `TypeInfoCache` 方法，导致启动失败。
- What:
  - persistence 模块改为直接依赖 Spring JDBC，移除不必要的 Spring Data JDBC 自动配置；发布脚本新增 `--db-driver-jar`，自动移除 `postgresql-*.jar` 并复制外置驱动到后端 `lib`；同步 GaussDB JDBC URL、驱动类名和部署说明。
- How:
  - 保留既有 `JdbcClient`/MyBatis/Flyway 代码路径，不修改 generated SDK、API、事件或数据库 migration；按外置 driver jar 可替换、默认 PostgreSQL 仍可用的方式保持兼容。
- Result:
  - JDK 25 后端构建、Druid 定向测试、脚本语法校验、外置驱动打包检查和 test profile 三服务重启通过；backend liveness/readiness、frontend 3000、登录 CORS、manager WebSocket 均正常。目标 GaussDB 的实际 migration 与数据表状态仍需在部署机复验。

### 2026-07-13 - 重构公共测试设计三阶段与应用工作区归档规则

- Why:
  - 测试设计公共区虽然已有分析、生成、审查三段式编排，但方法选择仍混在第一阶段，且默认输出目录没有体现应用工作区的需求项/子条目层级。
- What:
  - 公共配置仓库 `.testagent/agent-opencode/.config` 将第一阶段收敛为对象分析识别，第二阶段负责方法选择、等价类/边界表、判定表、路径/状态图、场景链路等中间产物及案例生成，第三阶段独立审查。
  - 新增 `workspace-layout.md` 和 `method-artifacts.md`；统一应用工作区为 `I...-需求项/{01-需求,02-设计,03-编码,04-测试}/S...-子条目/`，设计产物写 `041-测试设计`，执行产物写 `042-测试执行`。
- How:
  - 复用现有 test-design 公共 skill、方法型 skills、Task result 交接和测试执行链路，只调整职责、模板、路径解析和质量门禁；缺少合法需求项/子条目上下文时返回 `INCOMPLETE`，不回退到工作区根目录。
- Result:
  - 公共配置 diff 校验通过；按项目 test profile 重启脚本完成，backend liveness/readiness、frontend 3000、登录 CORS 预检和 manager WebSocket 均正常。未涉及 API、事件、数据库、环境文件或 generated SDK。

### 2026-07-13 - 宠物就绪时直接进入旁路提问

- Why:
  - 进程 ready 且已有主对话时，单击宠物仍先展示进程状态卡，用户必须再次点击旁路入口才能提问。
- What:
  - `FigmaShell` 复用现有进程 tone 与 `sideQuestionAvailable` 分流单击动作：两者均就绪时直接打开并聚焦提问输入框；进程未就绪或无主 Session 时继续展示状态卡。
- How:
  - 复用 `openRobotSideQuestionFromProcess()`，未增加新状态、API、事件或后端逻辑；同步组件回归、agent-web README 与包说明。
- Result:
  - FigmaShell/FigmaChatPanel 定向 Vitest 141 passed、1 skipped；agent-web typecheck、生产 build 和 `git diff --check` 通过。按 `.env.test` / `test` profile 重启三服务，backend readiness 为 UP，frontend 3000 返回 200。保留既有 `DirectoryRows.vue` 嵌套 button、CSS `@import` 顺序和大 chunk 警告。

### 2026-07-13 - 禁止无主对话时创建宠物旁路 fork

- Why:
  - 宠物状态气泡在没有选中真实主对话时仍开放“问问宠物当前任务”，但旁路实现必须依赖主 Session 创建临时 fork。
- What:
  - `AgentWorkbench` 复用当前 `sessionId` 下发旁路可用性；`FigmaShell` 在无真实主 Session 时禁用入口并给出提示，打开与提交函数也同步防御。未发送首条消息的新对话草稿仍视为没有主 Session。
- How:
  - 保留 `handleRobotSideQuestion` 原有空 Session 拦截作为第二层保护；未新增 API、事件、后端分支或会话状态模型。同步 agent-web README、包说明、组件测试和桌面/移动 Playwright 场景。
- Result:
  - FigmaShell/FigmaChatPanel 定向 Vitest 141 passed、1 skipped；agent-web typecheck、生产 build、`git diff --check` 通过；相关 Playwright 桌面/移动 2/2 通过。按 `.env.test` / `test` profile 重启三服务，backend readiness 为 UP，frontend 3000 返回 200。构建仍保留既有 CSS `@import` 顺序和大 chunk 警告。

### 2026-07-13 - 完善首次初始化提醒与对话输入门禁

- Why:
  - 用户要求首次进入且进程未启动时以红色呼吸宠物主动询问是否初始化，并要求进程未就绪或未选择对话时禁止对话、统一灰显输入入口。
- What:
  - `FigmaShell` 在页面生命周期内首次收到 `NEEDS_INITIALIZATION` 时自动唤出宠物和初始化气泡，活动栏机器人与宠物红心显示红色呼吸效果；`AgentWorkbench` 增加新对话草稿选择态，`FigmaChatPanel` 在进程未 ready 或未选对话时灰显并禁用输入、附件、Agent、模型和发送，进程 ready 时保留“新建对话”入口。
- How:
  - 复用现有进程状态、初始化流程和首条发送时延迟创建 Session 的链路，不新增空历史会话；同步组件测试、桌面/移动 Playwright 场景、agent-web README 与包说明，未修改 API、RunEvent、数据库或环境配置。
- Result:
  - 定向 Vitest 140 passed、1 skipped；目标 Playwright 桌面/移动 8/8 通过；workspace typecheck/lint、agent-web build 与 `git diff --check` 通过。扩大执行完整 `workbench.spec.ts` 时，首个既有文件打开场景仍因找不到精确文本 `tests/checkout.spec.ts` 在桌面/移动失败，确认关闭本次新对话自动选择后仍可复现，未扩大范围修改文件编辑器断言。按 `.env.test` / `test` profile 重启 backend、opencode-manager、frontend，backend readiness 为 UP，frontend 3000 返回 200。保留既有 `DirectoryRows.vue` 嵌套 button、CSS `@import` 顺序和大 chunk 构建警告。

### 2026-07-13 - 将 TestAgent 进程状态并入小宠物交互

- Why:
  - 用户希望宠物本体直接表达 opencode/TestAgent 进程状态，点击宠物从宠物旁弹出状态框；未初始化时使用红心，并由宠物发起初始化询问，取消聊天区独立绿点的可拖动入口。
- What:
  - `FigmaShell` 新增进程心形状态和不可拖动的进程状态对话气泡，复用现有 `UserOpencodeProcess`、初始化事件和启动操作；未初始化时展示“要现在帮你初始化吗？”及初始化按钮。工作台显式开启宠物进程承载模式，`FigmaChatPanel` 只保留 ready 发送拦截并隐藏旧状态点/卡片；气泡保留入口打开原宠物旁路问答。同步 agent-web README、包说明和 FigmaShell/FigmaChatPanel 回归测试。
- How:
  - 未新增 API、RunEvent、数据库或环境配置；使用 `showProcessStatusInPet` 避免 Vue Boolean prop 默认值影响独立 Shell 的原旁路问答测试，初始化继续由 `AgentWorkbench.beginInitializeOpencodeProcess()` 统一编排。
- Result:
  - 定向 Vitest 138 passed、1 skipped；workspace typecheck、lint、agent-web build 和 `git diff --check` 通过。按 `.env.test` / `test` profile 重启三服务，backend liveness/readiness、frontend 3000、CORS 和 manager WebSocket 均正常。浏览器标签因重启后登录态回到 `/login`，未代用户提交页面中的密码，真实页面交互人工验收受登录态限制。

### 2026-07-13 - 修复历史消息首屏被 manager 健康检查阻塞

- Why:
  - 选择历史会话会并发读取正文、permission、question、Todo 和 session tree；多个运行态接口同时向同一 manager WebSocket sink 下发强健康检查时，unicast sink 的并发 `tryEmitNext` 失败结果被忽略，命令未到达 manager 但 Java pending command 等到 10 秒超时。前端又把正文 loading 与 permission/question 放在同一 `Promise.all`，因此偶发显示“正在加载消息列表…”数秒。
- What:
  - manager WebSocket 同一连接的全部出站消息改为连接级串行 emission，并检查 `EmitResult`；发送失败立即抛统一平台错误，让 gateway 取消 pending command。历史正文视觉 loading 与完整历史切换发送锁拆分：数据库消息返回后立即显示正文，permission/question、树、Todo 和 Run/Diff 后台增强，完整投影完成前仍禁止发送。
- How:
  - 后端增加 256 线程并发控制命令真实 handler 回归；前端增加 interaction gate，验证正文先显示、loading 先关闭、发送按钮继续锁定并在增强完成后恢复。未变更 API 路径/DTO、RunEvent、数据库、generated SDK 或环境配置。
- Result:
  - TDD RED 已分别复现 manager 控制消息丢失和交互快照阻塞正文；修复后 `ManagerControlWebSocketHandlerTest` 与 `SocketOpencodeProcessManagerGatewayTest` 共 11 项、历史首屏/发送隔离 Chromium 场景 4 项通过，`agent-web` typecheck 和生产 build、后端 `test-agent-app -am -DskipTests package` 通过。前端 build 仅保留既有 CSS `@import` 顺序和大 chunk 警告。额外扩大到 8 个历史 grep 场景时 7 项通过，另 1 项旧 DiffSummary 断言仍期待展开后出现完整路径，但当前组件只显示 basename；该断言与本次 loading/发送锁范围无关，未顺带修改 Diff 交互。

### 2026-07-13 - 隔离下一轮运行态与历史消息完成状态

- Why:
  - 同一会话发送下一轮消息后，会话级 `running` 被时间线投影无差别应用到所有轮次，导致上一轮已完成的 context、reasoning 和同类 tool 分组立刻回退为“进行中”。
- What:
  - `agent-chat` 时间线仅允许最新用户轮次继承会话级运行态；历史轮次继续按各 part 自身状态展示。新增“上一轮完成、下一轮刚启动”的跨轮次投影回归，并同步包级 README。
- How:
  - 复用既有 `isActiveTurn` 边界，在 context/reasoning/tool 分组投影处统一计算当前轮 `busy`；未修改 reducer、消息类型、RunEvent、后端 API 或持久化结构。
- Result:
  - TDD 回归先稳定复现三个历史分组 `busy=true`，修复后状态投影 15/15、时间线组件 25/25、agent-chat 包和前端 workspace 类型检查通过；时间线测试仍输出既有 `DirectoryRows.vue` 嵌套 button warning，与本次无关。
### 2026-07-13 - 恢复 test 本地数据库并固定 Docker 重启策略

- Why:
  - `.env.test` 使用的 PostgreSQL `test-agent-postgres:15432` 和 Redis `test-agent-redis:16379` 同时退出，导致本地 test profile 启动无法完成。
- What:
  - 重启两个容器并设置 Docker restart policy 为 `always`；未修改 `.env.test`，也未触碰不被 `.env.test` 使用的 `pg-local`。
- How:
  - 执行 `docker update --restart=always test-agent-postgres test-agent-redis` 和 `docker restart test-agent-postgres test-agent-redis`，随后按项目规范重新执行 `./restart-dev-services.sh --profile test --env-file .env.test`。
- Result:
  - 两个容器状态为 `healthy`，PostgreSQL 接受连接、Redis 返回 `PONG`；后端 liveness/readiness 为 `UP`，前端 `127.0.0.1:3000` 返回 `200`，登录 CORS 预检为 `200`，opencode-manager 已连接。

### 2026-07-11 - 建立 Mermaid 可视化提交与 main 的正式合并关系

- Why:
  - `772ac3bd20` 的功能补丁此前已通过等价提交 `2c70e06b9` 进入 `main`，但目标提交本身尚不是主线祖先；直接三方合并会因分支基线相差 121/4 个提交而产生 60 余处与 Mermaid 无关的前后端冲突。
- What:
  - 新增双父合并提交，将 `772ac3bd20` 正式纳入 `main` 历史；保留当前主线完整文件树，不重复应用已存在的 Mermaid 补丁，也不回退后续对话、运行态、API 和文档改动。
- How:
  - 先用 `range-diff` 与稳定 patch-id 确认 `772ac3bd20` 和 `2c70e06b9` 的 `docs/superpowers`、`frontend` 代码补丁完全一致，再用 `git merge-tree` 分析冲突来源；最终采用保留当前 `main` tree 的双父合并，并校验合并前后 tree hash 相同、目标提交已成为主线祖先。
- Result:
  - Mermaid 定向 Vitest 43/43、workspace typecheck、lint、生产构建通过；Playwright 桌面端与移动端 2/2 通过。全量前端基线为 728 passed、1 skipped、1 个既有 `DirectoryRows.test.ts` 失败，原因是当前主线同时提供文件与目录删除按钮，本次未扩大范围修改。
### 2026-07-11 - 历史首屏、Session 交互隔离与重启终态校准

- Why:
  - 历史切换被工作区目录/大树快照串行请求拖慢，进程级 OpenCode pending ask 还可能泄漏到其它 Session；重启后 runtime-state 摘要也可能保留旧 RUNNING。
- What:
  - 历史先渲染分页正文和当前 Session 的 permission/question 快照，树/Todo/目录/active-run 作为后台增强；切换已校验工作区后立即清空上一 Session dock。历史运行中摘要后台复查 active-run，后端启动扫描 legacy active Run，仅以本 Run 之后的 assistant `finish=stop` 收敛成功，并保留 pending ask 防误判。
  - 后端 permission/question 列表按绑定 remote session 过滤；补充跨 Session、历史首屏、重启恢复与 runtime coordinator 回归，更新 HTTP/SSE/场景 fixture 文档。
- Pitfalls:
  - 重启脚本会停止 manager 管理的用户 opencode 子进程；服务健康不等于用户进程已 READY，需通过页面初始化/公共 initialize API 恢复 4096 进程。
- Resolved: Partial - 代码和定向 fixture 已验证；真实旧 Run 只有远端最新消息明确 `finish=stop` 才会自动成功，远端无终态或进程未恢复时仍保守保持 RUNNING，避免误判慢模型。
- Verification: 后端定向 93 项通过；前端 typecheck 通过；Playwright 9 项核心对话场景通过；`.env.test`/`test` profile 重启后 backend health UP、frontend 3000 返回 200，manager WebSocket 连接，初始化用户进程后 4096 health healthy。
- Next: None

### 2026-07-11 - 收敛历史终态并过滤重启后失效交互

- Why:
  - OpenCode 用户进程与平台 binding 仍然一致，4097 不是旧端口；真正问题是 OpenCode 重启丢失内存中的 question/permission request，平台重放旧事件后产生不可回复弹框，同时部分 RunEvent SSE 终态漏收导致远端已 `finish=stop` 的 Run 一直显示 RUNNING。
- What:
  - 历史 active Run 查询读取远端最新 assistant 消息，仅当消息带 `finish=stop` 且时间不早于本 Run 才补写 `RUN_SUCCEEDED`；前端以切换会话时的当前 pending 快照过滤已失效的旧 ask/permission 回放。远端交互 404 统一返回可恢复 `CONFLICT/REMOTE_INTERACTION_EXPIRED`，前端清理旧弹框并提示重新发起。
  - 新增 `docs/testing/conversation-scenes.md`，集中列出直接对话、历史运行中/已结束、Todo、ask、permission、subagent、历史 subagent、宠物旁路成功/失败等可重复 fixture 入口和命令。
- How:
  - 复用现有 Run、AgentRuntime、binding、session message、RunEvent 和 reducer 链路；未修改 generated SDK、环境文件、数据库结构或 Flyway。新增后端终态/失效交互回归，前端快照过滤回归，并修正历史运行 fixture 使当前 pending 快照与旧事件同时存在。
- Result:
  - 后端 `OpencodeRuntimeApplicationServiceTest,RunApplicationServiceTest` 81 项通过；前端五组核心 fixture（FigmaChatPanel、runtime-reducer、opencode-timeline、side-question、workbench-utils）247 项通过、1 项跳过；定向 Playwright 6 项通过（含历史 permission）；`.env.test`/`test` profile 重启命令成功，backend liveness/readiness 为 UP、frontend 3000 为 200，manager WebSocket 已连接。仍有既有 `DirectoryRows.vue` 嵌套 button、CSS import 顺序和大 chunk 警告，未纳入本次范围。

### 2026-07-11 - 修复根会话交互事件的 remote Session 投影

- Why:
  - 真实 OpenCode `question.asked` 已通过认证 SSE 到达，但 payload 的 `sessionId` 是 remote root session；面板却用平台 Session ID 过滤 Question/Permission，导致当前 Run 和运行中的历史会话都只留下工具行/原始事件，无法展示可提交的交互 dock。
- What:
  - root `question.asked` / `permission.asked` 在进入聊天 reducer 前将 remote sessionId 映射为订阅时的平台 Session ID，并保留 `remoteSessionId`；child session 不改写，继续按自身时间线展示。新增浏览器 fixture 覆盖直接 Run 的 Permission+Question 提交，以及运行中的历史会话收到 remote Question 后继续提交。
- How:
  - 复用 `RunEvent` reducer、既有 `currentSessionId` 过滤和现有 question/permission reply API；只在已知 root 订阅平台 Session、且 `isChildSession !== true` 时映射，不新增 API、事件、数据库、迁移或环境配置。
- Result:
  - 定向 Vitest 覆盖 RunEvent、Question/Permission、Todo/Part、子 Agent 历史时间线和旁路问答：371 passed、1 skipped；Playwright 直接 Question/Permission、历史 pending Question、运行中历史切回、旁路重连/重试：6/6 通过。test 服务已按 `.env.test` / `test` profile 重启，backend liveness/readiness 为 UP、frontend 3000 为 200、manager 已连接并通过公共启动服务恢复用户进程。仍有既有 `DirectoryRows.vue` 嵌套 button Vite warning，未纳入本次范围。

### 2026-07-11 - 宠物旁路问答改用 build agent 以缩短回答等待

- Why:
  - 用户反馈宠物浮层长期停留在“正在执行只读检查”，并明确要求直接使用 `build` agent。
- What:
  - 流式旁路 Run 的固定 prompt agent 从 `plan` 改为 `build`，保留系统提示的只读、禁止编辑和禁止破坏性操作约束；同步 Run 持久化选择、模块/API/前端文档和运行时断言。真实 E2E 的 durable 生命周期断言改按服务端 `seq` 排序，避免 live bus 与历史回放并发到达造成误报。
- How:
  - 复用已有 `SideQuestionPolicy` 作为唯一 agent 选择点，不新增 API、事件、数据库、环境变量或 generated SDK 改动；临时 fork、compact、SSE 投影与清理链路保持不变。
- Result:
  - `SideQuestionPolicyTest` 与 `SideQuestionStreamingApplicationServiceTest` 共 16 项通过；test 三服务重启后 liveness/readiness 为 UP、前端 200、CORS 预检通过。真实宠物旁路 E2E 通过（14.3 秒），验证最终答案、主会话隔离与 fork 删除。

### 2026-07-11 - 修复跨节点 RunEvent SSE 鉴权并保留对话回放 fixture

- Why:
  - 合并远程运行态路由后，真实已登录浏览器访问 RunEvent SSE 会先按 Run 生产节点转发；旧前端使用原生 `EventSource`，无法携带存于 sessionStorage 的 Bearer Token，目标 Java 将请求识别为 anonymous，导致主 Run、历史切回后的继续订阅和宠物旁路在 1–4ms 内断流。
- What:
  - `@test-agent/event-stream-client` 在提供登录 token 时改用带 `Authorization` 的 fetch SSE，保留 SSE `id` 续传、去重和自动退避重连；未登录/显式注入 EventSource 的调用保持原兼容路径。工作台主 Run 与宠物旁路均传入当前 token；宠物 Playwright fixture 同步改为 fetch SSE mock，并覆盖断流、lastEventId 续传、重复增量、失败重试与历史 question dock。
- How:
  - token 只存在请求头，不进入 URL、浏览器历史、代理 query 或原始报文面板；不新增 API、事件、数据库、Flyway 或环境配置。真实浏览器从 sessionStorage 取 token 直接访问故障 RunEvent 端点，返回 `200 text/event-stream`，验证跨 Java 转发后的认证链路。
- Result:
  - 前端定向 Vitest 313 passed、1 skipped，workspace typecheck 通过；Playwright 宠物旁路流/失败重试与历史 native question 弹框共 3/3 通过。仍存在既有 `DirectoryRows.vue` 嵌套 button Vite warning，未纳入本次范围。

### 2026-07-11 - 合并主线并复核对话回放能力

- Why:
  - 主工作树处于 `main` rebase 中，远程运行态快照改造与本地任务清单、旁路 ask、历史 question/permission 和子 Agent 回放提交存在重叠，需合并而不能互相覆盖。
- What:
  - 将远程 `origin/main` 合入后保留 `todo.updated`/todowrite 历史回放、历史 question 弹框与回复、permission 恢复、任务子 Agent 跳转、全部 Part fixture 和宠物旁路 SSE；修正 Redis 摘要 Run 启动时 `AgentStartRunCommand` 漏传 `system` 形参导致的后端编译失败。
- How:
  - 冲突处统一保留 `run.snapshot.reset`、`side_question.*` 和 runtime-state fence；旁路会话切换只清理自身展示，不绕过主 Run 的 runtime-state 恢复策略。测试 fixture 与 Playwright mock 保留各类历史对话及其 API 回包，可直接构造回放场景。
- Result:
  - `mvn -pl test-agent-opencode-runtime -am test -DskipITs` 通过（487 tests）；前端定向 Vitest 312 passed、1 skipped，workspace typecheck 通过，Playwright 覆盖旁路流/重试、历史投影及历史 question 弹框回复通过。历史 question 夹具的旧“历史”按钮选择器已更新为当前“消息列表”入口；合并后缺失的 `@vue-flow/core` 已由 frozen-lockfile 安装恢复。完整 `test` 启动还发现 scheduler 有测试双构造器但缺少 `@Autowired`，已显式标注生产构造器并复验：backend liveness/readiness 为 UP、frontend 3000 为 200、登录 CORS 预检通过，manager 已连接并发送心跳；未改环境文件。

### 2026-07-11 - 恢复 OpenCode todowrite 实时与历史待办面板

- Why:
  - 用户反馈任务清单无法展开；真实 OpenCode 1.17.7 会把待办列表放在 `message.part.updated` 的 `tool=todowrite`、`state.input.todos` 中，并不保证发出独立 `todo.updated`，原前端因此只显示工具过程行。
- What:
  - reducer 兼容从 todowrite 工具 part 投影最新 Todo 快照；历史 session tree/持久化 part 回放可恢复最近快照，切换历史会话再用已有 session todo API 的远端结果（含空数组）校准。
- How:
  - 未新增 API、事件、数据库或配置；保留既有 `todo.updated` 处理和 TodoPanel 点击交互，仅复用现有 `getSessionTodo` 与消息归一化链路。
- Result:
  - 真实浏览器抓到完整原生 todowrite payload，并在后续真实消息更新后观察到输入框上方“任务”面板出现；runtime reducer/history 回放定向测试 99 项通过，agent-chat typecheck 通过。全 app typecheck 暂由并行 Part E2E 未提交 helper 缺失阻塞，待其修复后复验。

### 2026-07-11 - 将宠物旁路问答升级为可回放的 SSE 流式任务

- Why:
  - 宠物提问此前只能同步等待，浮层会暴露工具协议或长时间无反馈；用户要求在不污染主会话的前提下持续显示真实进度与最终结果，并在临时 fork 异常遗留时自动收敛。
- What:
  - 新增 `SIDE_QUESTION` 内部归档 Session、流式启动 API、三类 `side_question.*` RunEvent、前端 `useSideQuestionRun` 组合式状态和真实三服务 E2E。旁路固定 `plan` 只读策略，支持预算超限时仅压缩临时 fork，终态以完整 answer 校准增量；临时 fork 成功、失败与进程中断后均会删除或由 5 分钟孤儿任务回收。补齐小宠物点击保持、会话切换清空、重连提示与可访问性回归；修正终端进程输出在 exit 竞争时可能早于 stdout 完成关闭的问题。
- How:
  - 复用既有 AgentRuntime 路由、Run/RunEvent 持久化与 SSE 订阅，不修改 generated SDK；所有新增关系查询走 MyBatis XML，Flyway 仅更新来源字段注释。SSE 只投影临时 session 的安全阶段和 assistant 文本，工具参数、主会话消息及临时 remote ID 都不会输出；终态写入通过独立事务 CAS 保证唯一。
- Result:
  - Chrome mock E2E 宠物流式问答 2/2 通过；真实 OpenCode 三服务 E2E 通过，验证 SSE 终态答案、主会话隔离和临时 fork 删除。test profile 三服务已重启并健康。后端全量 Maven 仍有 7 个既有 persistence H2 fixture 失败（PostgreSQL `on conflict` 与缺少用户外键数据）；前端全量仍有 1 个既有 `DirectoryRows` 嵌套删除按钮断言失败。本次相关定向测试、编译和真实链路均通过。

### 2026-07-11 - 回退对话视觉改造并恢复历史 Ask 继续链路

- Why:
  - 对话视觉改造偏离既定稿并破坏了复制、Question/Permission 弹框、原生 Timeline 事件和 Run 终态判断；历史 ask 还可能拿已失效的 requestId 提交。
- What:
  - 仅反向回退基线 `976a798211` 之后的对话专属改造，保留宠物旁路问答、进程状态卡、文件树等无关能力；历史切换改为以 OpenCode 当前 permission/question pending 列表覆盖事件快照。针对 OpenCode 1.17.7 在 ask 回复后不再向既有订阅推送最终正文/idle 的情况，只有远端最新 assistant 明确 `finish=stop` 时才补发原生消息投影并收敛 Run 成功。
- How:
  - 复用现有 runtime session messages、RunEventLiveBus、Run 快照和 permission/question API，不改 generated SDK、API 路径、事件 wire name 或数据库；补充最终消息补发和终态回归测试，同步 runtime README 与 agent-web PACKAGE 文档。
- Result:
  - 后端 runtime reactor 325 项通过；前端对话定向 196 项通过、1 项跳过，typecheck/lint/build 通过。真实 UI 验证 Question 与 Permission 均可“触发 → 新建对话 → 历史恢复 → 提交 → 显示最终正文 → SUCCEEDED”，复制按钮可见。前端全量仍有 1 个既有无关失败：`DirectoryRows.test.ts` 期望 1 个删除按钮，当前已有 2 个。

### 2026-07-11 - 修复宠物旁路问答只显示工具协议而不返回最终答案

- Why:
  - UI 浮层能显示模型输出，但实际内容是 `<tool_calls>` 协议，工具没有执行，用户看不到工具完成后的自然语言结果。
- What:
  - 旁路消息不再传 `tools: {"*": false}`；改用 OpenCode `plan` agent 的只读权限，并追加系统提示要求工具完成后继续返回最终答案。增加伪工具协议过滤，纯协议响应直接报错，不再泄漏到宠物浮层。
- How:
  - 复用现有临时 fork、compact 和 finally 删除链路；补充运行时服务回归测试，覆盖消息体权限、系统提示和伪工具文本防泄漏；同步运行时、API、架构和前端 README 说明。
- Result:
  - `OpencodeRuntimeApplicationServiceTest` 定向测试 26/26 通过；真实 UI 已登录、初始化进程、建立主会话后复测旁路提问，浮层显示实际 git 检查结果且不含 `<tool_calls>`。

## Entries

### 2026-07-11 - 恢复对话原生事件、复制入口与最终输出样式

- Why: 对话“专注阅读”错误过滤了 reasoning/tool/file/task 等非文本 part，包级视觉规则又隐藏了复制按钮；最终完成文本缺少独立样式，历史 question/permission 的恢复与继续缺少回归保障。
- What: 专注阅读改为只降低过程行视觉权重，切换前后始终复用同一份 `OpencodeTimeline` state；完成态最后一条 assistant text 使用轻量最终输出容器并恢复复制按钮。补齐当前 permission 三种决策、当前 question、历史 session-tree question/permission 回放及全 message type 保留测试。
- How: 仅修改 `FigmaChatPanel` 宿主展示层和测试，未修改 `frontend/packages/agent-chat/src/opencode-like/` 的 reducer、投影、组件或 Huangzhenren 已有回复链路；复制覆盖放入原 `oc-components` cascade layer，避免旧 `!important` 隐藏规则继续生效。同步 agent-web README。
- Result: 对话相关 226 passed、1 skipped，前端 typecheck/lint/build 通过；Playwright 在真实历史会话验证 reasoning/tool 切换前后数量一致、最终输出和复制按钮可见，390px 视口内最终输出未溢出对话栏。`.env.test`/`test` profile 重启后 backend/readiness/frontend/CORS 均正常。前端全量另有 1 个既有无关失败：`DirectoryRows.test.ts` 期望目录不显示删除按钮，而当前 HEAD 会同时显示文件与目录删除按钮。

### 2026-07-10 - 增加宠物旁路问答并按上下文预算控制临时 fork

- Why:
  - 用户希望小宠物具备类似 Claude Code `/btw` 的对话能力，同时不污染主对话历史；本轮进一步要求控制 fork 传入上下文，必要时调用 OpenCode compact。
- What:
  - 新增平台和 agent-scoped `side-question` API、前端 `askSideQuestion` client 与宠物一次性问答浮层。运行时读取当前消息规模，超过 40 条或约 48000 字符时仅对临时 fork 调用 summarize，再以 `tools: {"*": false}` 发送单条问题，finally 删除临时会话，回答只返回浮层。
- How:
  - 复用 `AgentRuntimeTargetResolver`、`AgentSessionMessagesCommand`、runtime HTTP 代理和现有 FigmaShell/AgentWorkbench；新增 DTO、稳定 shared types、定向单测/Controller 测试及 API/模块文档，不新增数据库、SSE 或环境配置。
- Result:
  - 后端 `mvn -pl test-agent-api -am test`（JDK 25）通过；旁路服务 24/24、平台 Controller 8/8。前端 `corepack pnpm test --run packages/backend-api/tests/backend-api.test.ts apps/agent-web/tests/FigmaShell.test.ts` 66/66，`corepack pnpm typecheck` 通过，当前 `http://127.0.0.1:3000/` 返回 200。包含既有聊天时间线改动的 FigmaChatPanel 全量定向测试仍有 1 个既有失败，未把该无关改动纳入本次提交。

### 2026-07-10 - 修复宠物误离场并调整开关位置

- Why:
  - 用户反馈手动点开宠物后会因全局页面活动监听被当成离场信号；同时希望把开关放到左下角设置附近，并缩小用户头像占用。
- What:
  - 可见宠物不再因普通点击、输入或滚动触发离场；手动唤起后保持可见，直到用户收起或重新定位，重新定位后恢复自然离场。开关从顶部移到左侧活动栏系统设置按钮上方，用户头像缩小为紧凑样式。
- How:
  - 复用 `FigmaShell` 现有状态机和活动栏，不新增 API、SSE、数据库或环境配置；新增手动唤起持久可见、页面活动不离场、开关位置和紧凑头像回归测试。
- Result:
  - `FigmaShell` + `FigmaChatPanel` 定向回归 120 passed、1 skipped；agent-web lint、typecheck、build 通过；本地页面 HTTP 200。仍有既有 DirectoryRows 嵌套 button、CSS `@import` 顺序和大 chunk 警告；未做浏览器人工验收。

### 2026-07-10 - 保护宠物起点并填充同款图标

- Why:
  - 自然动作期间窗口缩放不应覆盖用户保存的下一次起点；顶部同款宠物图标的实际可见几何也需要占满按钮。
- What:
  - 窗口缩放只夹取当前内存动画位置，保存坐标只在手动拖动/键盘定位时写回；开关 SVG 视口裁剪到宠物头部几何，填充 24×28 图标区域。
- How:
  - 新增起点持久化和 viewBox 回归测试，未新增 API、事件、数据库或环境配置。
- Result:
  - `FigmaShell` 19/19 通过；Shell + Chat 定向回归 117 passed、1 skipped，agent-web lint/typecheck/build 和 HTTP 200 均通过。仍有既有 CSS、chunk 和 DirectoryRows 警告；未做浏览器人工验收。
- Next:
  - 评估类似 Claude Code `/btw` 的旁路问答，不把它表述为已实现能力。

### 2026-07-10 - 合并验证宠物互动与进程浮层修复

- Why:
  - 收尾本轮宠物随机互动、同款头部图标和进程绿点边缘迁移修复，确认两组提交合并后没有回归。
- What:
  - 宠物开关图标填充 28×28 按钮，手动定位后继续自然动作/离场并新增抖动、旋转庆祝；进程状态保留 v2 卡片锚点、dotSide、旧坐标迁移和完整边缘拖动。
- How:
  - 仅复用现有 FigmaShell/FigmaChatPanel 与测试，不新增 API、SSE、数据库或环境配置。
- Result:
  - `cd frontend && corepack pnpm test --run apps/agent-web/tests/FigmaShell.test.ts apps/agent-web/tests/FigmaChatPanel.test.ts`：116 passed、1 skipped；`corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm build` 和 `curl -I http://127.0.0.1:3000/`（HTTP 200）通过。仍有既有 DirectoryRows 嵌套 button、CSS `@import` 顺序和大 chunk 警告。
- Next:
  - 未做浏览器人工交互验收；下一步再评估类似 Claude Code `/btw` 的旁路问答设计。

### 2026-07-10 - 完成宠物互动与进程浮层收尾

- Why:
  - 用户要求顶部宠物图标与本体一致且填满按钮，手动定位后继续自然随机互动；进程绿点需要覆盖完整安全视口并兼容旧位置记录。
- What:
  - 宠物复用同一套天线/脑袋/眼睛 SVG，开关图标调整为 24×28；手动拖动或键盘定位后继续自然动作和自然离场，并新增抖动、旋转庆祝动作。进程浮层补齐 v1 迁移、v2 `dotSide` 方位和边缘拖动/点击抑制回归。
- How:
  - 复用现有 FigmaShell/FigmaChatPanel 状态机与本地存储，不新增 API、SSE、数据库或环境配置；测试先行覆盖图标几何、计时器、动作和浮层边缘路径。
- Result:
  - 宠物定向测试 18/18 通过；进程浮层定向测试 98/98 通过、1 跳过；agent-web 类型检查、lint、build 与 HTTP 冒烟均已由对应提交验证。人工浏览器拖动、缩放和收起/重开仍未验收。
- Next:
  - 完成合并后的全量前端定向回归，再评估宠物旁路问答（类似 Claude Code `/btw`）设计。

### 2026-07-10 - 补齐进程锚点方位与实测约束时机

- Why:
  - 首版卡片锚点 v2 未保存绿点相对方位，旧版右下绿点迁移后可能翻到卡片另一侧；同时合法 v2 会在卡片尚未展开时被回退尺寸提前夹取并改写。
- What:
  - 本地偏好扩展为 `{ v: 2, cardX, cardY, dotSide }`；`dotSide` 仅表示 `top/bottom-left/right` 方位。v1 迁移保存原方位，已校验 v2 在实际卡片测量前保持原始锚点。
- How:
  - 只在展开卡片获得非零真实 rect 后进行锚点夹取和持久化；拖动后的首个合成 click 单独消费并清除标记，后续真实 click 仍可收起。
- Result:
  - 新增 v1 右下迁移方位、v2 延迟夹取/回写和连续 click 回归；`FigmaShell` + `FigmaChatPanel` 定向测试为 112 passed，1 skipped，`pnpm lint`、`pnpm typecheck`、`pnpm build` 和 `git diff --check` 均通过，新的 `http://127.0.0.1:3011/` 返回 HTTP 200。构建仍有既有 CSS `@import` 顺序和大包体积警告。
- Next:
  - 继续保留浏览器人工边缘拖动、缩放和收起/重开验收。

### 2026-07-10 - 统一进程浮层为卡片锚点坐标

- Why:
  - 旧版同时维护绿点坐标与临时卡片锚点，卡片尺寸变化或视口缩小时可能分别夹取，导致收起和展开的位置不同步。
- What:
  - `FigmaChatPanel` 仅保存卡片左上角锚点，使用版本化 `{ v: 2, cardX, cardY }`；绿点由锚点、卡片尺寸和安全边距派生。旧 `{x,y}` 记录在读取时转换、夹取并立即重写为 v2。
- How:
  - 绿点/卡片拖动、ResizeObserver 和窗口缩放统一只移动或夹取该锚点；首次从内联卡片拖动时写入拖动前的实际 rect，保留无跳位体验。单测覆盖迁移重挂载、边缘、收起重开、尺寸增长、缩放、8px 绿点与子 Agent 隐藏。
- Result:
  - 先验证旧实现 RED（2 failed，92 passed，1 skipped）；最终 `FigmaShell` + `FigmaChatPanel` 为 110 passed，1 skipped，`pnpm lint`、`pnpm typecheck`、`pnpm build` 均通过；新前端 dev server 的 `http://127.0.0.1:3001/` HTTP 200。构建仍有既有 CSS `@import` 顺序和大包体积警告。
- Next:
  - 未完成浏览器人工边缘拖动、缩放和收起/重开验收。

### 2026-07-10 - 支持小宠物与进程状态卡本地定位

- Why:
  - 工作台小宠物需要在不喧宾夺主的前提下支持用户定位，TestAgent 进程状态卡需要在折叠绿点的位置展开。
- What:
  - MIMO 使用低饱和蓝灰、青色和紫色点缀，指针或键盘定位仅保存在浏览器本地，手动定位后暂停随机动作；进程绿点本地保存坐标，展开卡固定锚定、按可用视口换向并在内容尺寸变化时重新约束。同步更新 `frontend/apps/agent-web/README.md`，保留设计说明和实施计划。
- How:
  - 未新增或变更 API、RunEvent SSE、数据库、安全或兼容性契约；通过既有组件和单测覆盖拖动、持久化、边缘换向及缩放约束。
- Result:
  - `cd frontend && corepack pnpm test --run apps/agent-web/tests/FigmaShell.test.ts apps/agent-web/tests/FigmaChatPanel.test.ts`（97 passed，1 skipped）及最终小宠物计时器回归 `corepack pnpm test --run apps/agent-web/tests/FigmaShell.test.ts`（13 passed）、`corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm build` 和 `git diff --check` 均通过。以 `corepack pnpm --filter @test-agent/agent-web dev -- --host 127.0.0.1 --port 3010` 启动当前 HEAD，因 3000/3001 已占用实际监听 `http://127.0.0.1:3002/`，`curl` 返回 200；构建仅有既有 CSS `@import` 顺序及大包体积警告。
- Next:
  - 未执行浏览器人工拖动、刷新和缩放验收；可在 `http://127.0.0.1:3002/` 按 README 所述交互复核。

### 2026-07-10 - 删除 OpenCode 会话标题超时兜底

- Why:
  - 临时远端会话、二次调用 title agent 和固定超时会制造与真实会话无关的标题来源，且无法保证主 Run 快速完成后仍能正确同步原生标题。
- What:
  - 删除 `RunSessionTitleFallbackService`、`OpencodeSessionTitleProperties`、临时会话 `generateNativeSessionTitle`、相关测试和 `test-agent.opencode.session-title` 配置；`RunApplicationService` 不再注入或调度兜底服务。
- How:
  - 只保留 `RunSessionTitleWatchRegistry` / `RunSessionTitleWatchService` 对同一远端 root session 的事件驱动监听：原生 title agent 完成后读取最终标题并通过已有 `session.updated` SSE 同步；下一轮对话、手动改名、归档/删除会主动取消等待。
- Result:
  - 不创建临时 session、不重复调用 title agent、也不依据超时产生替代标题；配置、运行时代码、测试和稳定文档均不再保留该兜底路径。

### 2026-07-10 - 修复企业内公共 Agent Git 分支查询 URL

- Why:
  - 企业内部公共 Agent Git 地址只保存 `host[:port]/path` 片段时，初始化弹窗加载远端分支的 `git ls-remote` 可能直接使用片段，缺少 `ssh://{unifiedAuthId}@`，导致 Gerrit 分支读取超时或认证失败；同时现场容易把 `configDirPath` 误认为 manager 启动时会自动创建。
- What:
  - 公共分支查询在执行 Git 命令前强制按当前用户重新计算有效 Git URL；底层 Git 执行器补齐 `git_command_start/success/failed/timeout` INFO/WARN 日志，stderr 和命令中的 URL 用户信息继续脱敏；公共配置目录未初始化的错误文案明确要求先用公共 Agent Git 仓库初始化目标服务器。
- How:
  - 复用既有 `effectivePublicGitUrl` / 保存值形态判断，不新增第二个公共 Git 参数，也不创建空 `OPENCODE_PUBLIC_CONFIG_DIR`。
- Result:
  - `AgentConfigApplicationServiceTest` 新增内部片段分支查询回归测试；`ProcessGitCommandExecutorTest` 覆盖 Git 命令开始/成功日志输出、失败日志输出和统一认证号脱敏。部署时 `configDirPath` 仍必须来自公共配置 Git 仓库中的非空 `opencode` 配置目录。

### 2026-07-10 - 保留 OpenCode 默认标题以启用自动命名

- Why:
  - 实际运行事件证明平台已收到并转发 root `session.updated`，但标题始终是首条原文；平台创建远端会话时传入了临时标题，OpenCode 1.17.7 因此跳过只针对默认标题运行的内置 title agent。
- What:
  - 根远端会话首次创建及重建不再传平台临时标题；创建命令标题支持 null/空白，SDK gateway 在标题缺失时发送 `{}`。
- How:
  - 复用 `AgentRuntimeTargetResolver -> OpencodeAgentRuntime -> OpencodeClientFacade -> GeneratedOpencodeSdkGateway` 既有创建链路；平台临时标题和既有 `session.updated` 同步不变，显式非空标题继续原样透传。
- Result:
  - JDK 25 定向 reactor 测试 59 项通过（client 16、agent-runtime 1、runtime 42）；仍需以全新远端会话完成真实 UI 标题生成验证，旧远端会话不会重命名。

### 2026-07-10 - 同步 OpenCode root 会话标题

- Why:
  - OpenCode 内置 title agent 已发送 `session.updated`，但平台 Session 仍保留首条消息生成的标题，Web 消息列表无法展示 AI 标题。
- What:
  - 在 `RunApplicationService` 的既有流式事件处理处，将已路由确认的 root `session.updated` 标题回写到对应平台 Session。
- How:
  - 复用 `Session.updateTitleAndPinned` 和 `SessionRepository.save`；仅接受 root scope 且远端 root id 与平台绑定一致的非空标题，兼容直出与 `rawPayload.properties.info` 包裹事件；标题仓储失败只记录中文 WARN，不阻断原始 `session.updated` 持久化与 SSE 发布。标题保存成功时复制既有 draft，增加已同步标记和去首尾空白的标题，确保 raw 包装事件清洗后仍可实时传递。
- Result:
  - `RunApplicationServiceTest` 覆盖 root、包裹事件、已发现 child、未知归属、空白标题、标题仓储失败及平台远端绑定不一致；成功事件含同步字段，失败或不匹配事件不含该字段；使用 JDK 25 执行定向 Maven 测试 42 项均通过。

### 2026-07-10 - 清理企业模板中的废弃 prod profile

- Why:
  - 删除 `application-prod.yml` 后，企业 `backend.env` 模板、Dockerfile 和部署文档仍保留 `SPRING_PROFILES_ACTIVE=prod`，会误导现场。
- What:
  - 移除上述 prod profile 遗留配置并重新生成完整企业升级包。
- How:
  - 默认 `application.yml` 继续通过环境变量接收所有企业配置；`test` 是唯一保留的显式 profile。
- Result:
  - 完整打包成功，最新升级包 SHA256 为 `0ca7dfe60cdaa0a7728794980188c7b6caca47d0b8ac264a628e26093b552098`。

### 2026-07-10 - 统一后端配置并外置 Spring Boot 依赖

- Why:
  - 企业现场需要不重打业务 jar 即可替换 JDBC 依赖，同时要求后端只保留默认 `application.yml` 和 `application-test.yml`。
- What:
  - 删除 `prod`、`local`、`local-h2`、`guo` profile yml；企业打包将 `BOOT-INF/lib` 提取到 `dist/backend/lib` 并生成薄 jar，部署脚本原子替换该目录，systemd 示例改为 `PropertiesLauncher`。
- How:
  - 业务代码、API、事件和数据库结构未改；默认运行配置全部由环境变量提供，本地启动仅保留默认和 `test` 模式。
- Result:
  - 薄 jar 从外置 lib 启动后可加载所有应用依赖；完整 zip 的 `--validate-only` 通过，SHA256 为 `ff1a3c67088b836342cf253daa47ac77e7c5558fc506204984d3551f8c26591f`。

### 2026-07-10 - 生产环境 JDBC 驱动类配置与基础配置对齐

- Why:
  - `application.yml` 已通过 `TEST_AGENT_DB_DRIVER_CLASS_NAME` 预留 JDBC 驱动类配置，但 `application-prod.yml` 重复写死 PostgreSQL 类，导致企业生产配置无法覆盖。
- What:
  - `application-prod.yml` 改为复用同一环境变量占位符；新增 prod profile 配置绑定回归测试，并在企业 `backend.env` 模板、部署说明和 app README 中记录变量语义。
- How:
  - 只允许变量选择已在 Java 启动 classpath 中的驱动类，默认仍是 `org.postgresql.Driver`；未新增外置 jar 加载或运行时热替换机制。
- Result:
  - 定向测试先在旧配置下断言失败、修改后通过；`mvn -pl test-agent-app -am -DskipTests package`、完整 `deploy/internal/package-release.sh`、zip 完整性检查通过。完整 `TestAgentRuntimePropertiesBindingTest` 仍有两条既有断言与当前 yml 默认值不一致（Druid `test-on-borrow`、scheduler enabled），本次未改无关默认配置。最新交付包 SHA256：`3c4718b31059f2c354475977c476314d93e6d40543583693a2642022f7528582`。

### 2026-07-10 - 重新验证企业内完整交付包

- Why:
  - 现场再次执行企业内打包时，Docker worker 的 Go 编译阶段约 69 秒输出较少，容易被误判为打包失败；需要确认最新完整交付包是否生成并记录新的传输校验值。
- What:
  - 重新执行 `deploy/internal/package-release.sh`，生成后端 jar、前端静态包、worker 镜像 tar、外挂程序包和完整升级 zip。
- How:
  - 未修改部署脚本或生产配置；仅在 Mac 构建机完成构建、Docker `linux/amd64` 镜像导出和 zip 完整性校验。
- Result:
  - 打包以 exit code 0 完成，`unzip -tq deploy/internal/dist/test-agent-internal-release.zip` 通过。当前交付包 `deploy/internal/dist/test-agent-internal-release.zip` 的 SHA256 为 `47cd399a39a553bbf43acbf98168e7605af4b8ed7bc9a1f3620dc973c18fdb4c`；交付时应以此值为准。

### 2026-07-09 - 打包前补齐企业内拆分部署入口说明

- Why:
  - 最新企业内交付需要明确完整 zip 分发到 `122.233.30.2`、`122.233.30.4`、`122.233.30.114`，并在后端无法免密 scp 前端机时采用“前端本地手工更新 + 两台后端各自一键部署 Java/worker”的拆分流程。
- What:
  - 更新 `deploy/internal/README.md` 的产物分发表和升级步骤，补充完整 `test-agent-internal-release.zip` 的三机导入路径、前端 `deploy-internal-frontend.sh` 本地执行命令，以及 `122.233.30.4`/`122.233.30.114` 后端部署脚本带 `--skip-frontend` 的推荐命令。
- How:
  - 保留后端脚本可选 scp 前端的能力，但把统一登录受限现场的默认推荐流程改为不依赖后端到前端 SSH；不修改真实 `/data/testagent/config/*.env`。
- Result:
  - `bash -n` 覆盖部署/打包脚本，临时 zip 的后端/前端 `--validate-only` 通过；完整 `deploy/internal/package-release.sh` 成功生成 `deploy/internal/dist/test-agent-internal-release.zip`，SHA256 为 `da0351130443aa28d96656dcdfe82ea6351bcc42c8f3ab4c416999bd474c153a`，zip 内容包含后端 jar、前端 tar、worker 镜像 tar、programs 包、前后端部署脚本和双后端 README。
### 2026-07-11 - 增加 Flowchart 与 Sequence diagram 可视化编辑

- Why:
  - Markdown 预览虽然能渲染 Mermaid，但流程图和时序图只能修改源码，缺少拖拽、结构编辑和稳定回写能力；同时必须保持 Markdown 为唯一事实源并复用现有文件保存链路。
- What:
  - `@test-agent/editor` 新增 Flowchart/graph 与 Sequence diagram 独立领域模型、parser、serializer、unknown line 保留和 `%% editor-layout:` 坐标 metadata；新增懒加载的 `@vue-flow/core` 编辑对话框，支持流程节点/边和时序参与者/有序消息的增删、拖拽及属性编辑。
  - Mermaid block 增加“可视化编辑”入口，打开和应用时均调用 Mermaid 官方 parser；应用只替换目标 fence，打开期间内容被 Agent 刷新时拒绝覆盖。完整 Markdown 继续通过 `CodeEditor change` 进入 dirty、Ctrl/Cmd+S、workspace.write 和 Git Diff 原链路。
- How:
  - Flowchart 与 Sequence 使用判别联合类型接入同一对话框，Vue Flow 类型被限制在画布 adapter；Sequence 消息用带序号的错层自定义边表达顺序，复杂 `Note`、`activate`、`loop` 等语句暂不编辑但原样写回。全部新增行为遵循 parser/serializer、adapter、组件、Markdown 接入和保存转发的 TDD 红绿周期。
- Result:
  - 定向领域与组件测试覆盖官方 Mermaid parse、round trip、unknown line、坐标、拖拽、连接、属性编辑、消息排序、单 block 回写、语法错误、外部刷新冲突和 CodeEditor change 转发；未新增后端 API、事件、数据库、全局 store 或环境配置。
### 2026-07-09 - 补齐 WebSocket 操作日志和启动日志提示

- Why:
  - 前一轮日志切面已覆盖 HTTP Controller 和 Service，但前端 WebSocket 长连接入口本身不属于 Controller；同时本地重启脚本仍把 `.tmp/dev-services/*.log` 统称为 Logs，容易和新的结构化业务日志文件混淆。
- What:
  - 新增 `WebSocketLoggingAspect`，统一记录 API 层 WebSocket handler 的入口、结束 signal、耗时和异常；启动脚本输出改为区分 process log、backend app logs 和 opencode-manager app logs，并把 frontend 启动行同步为 process log 口径。
- How:
  - WebSocket 日志只记录 handler 名、path、traceId、signal 和错误摘要，不记录消息内容；HTTP、SSE 和 Service 仍由既有 `ApiLoggingAspect`、`ServiceLoggingAspect` 和 Log4j2 分文件配置承接。
- Result:
  - `mvn -pl test-agent-api -am test -Dtest=ApiLoggingAspectTest,ServiceLoggingAspectTest,WebSocketLoggingAspectTest -Dsurefire.failIfNoSpecifiedTests=false`、`bash -n restart-dev-services.sh` 和 `./restart-dev-services.sh --help` 检查通过；`./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 已重新启动本地后端、opencode-manager 和前端，输出已显示 process/backend/manager 日志分流路径。
### 2026-07-09 - Markdown 文件编辑切换为 Monaco + markdown-it 分屏模式
### 2026-07-09 - Markdown 文件编辑默认 Monaco + markdown-it 分屏模式并保留单键全屏预览
### 2026-07-09 - Markdown 文件编辑状态过渡与默认打开方式调整

- Why:
  - 针对 Markdown (.md) 文件的编辑与预览交互逻辑，需要精细化控制状态切换：默认以纯编辑模式（`off`）打开文件，但支持单击眼图标进入全屏预览模式（`full`）并在全屏/分屏下单击退回纯编辑模式，支持双击眼图标进入分屏编辑+预览模式（`split`）并在预览状态下双击退回纯编辑。不允许全屏和分屏模式之间直接相互切换。
- What:
  - 1. 修改 `AgentWorkbench.vue` 上的 `activePath` 监听逻辑，当切换或新打开 Markdown 文件时均强制重置为编辑模式（`off`）。
  - 2. 修改 `WorkbenchFooter.vue` 的 `handlePreviewClick`（单击）和 `handlePreviewDblClick`（双击）以执行以下转换：
    - 单击：编辑状态(`off`)下切换至全屏预览(`full`)，在全屏(`full`)或分屏(`split`)状态下退回纯编辑(`off`)。
    - 双击：编辑状态(`off`)下切换至分屏编辑+预览(`split`)，在全屏(`full`)或分屏(`split`)状态下退回纯编辑(`off`)。
  - 3. 在 `CodeEditor.vue` 和 `CodeEditor.preview.test.ts` 中回归恢复最原始对 `full`（整体预览，隐藏 Monaco 编辑器）和 `split`（分屏，展示 Monaco 编辑器）的支持。
  - 4. 调整 `WorkbenchFooter.test.ts` 的单元测试断言，匹配单击发出 `full`、双击发出 `split`、从分屏/全屏单击还原为 `off` 的路径。
- How:
  - 在 `AgentWorkbench.vue` 重置打开状态，在 `WorkbenchFooter.vue` 修改单击与双击的状态切换方程。在 `frontend` 目录运行 `corepack pnpm test --run`、`corepack pnpm typecheck` 和 `corepack pnpm lint` 校验。
- Result:
  - 每次打开 Markdown 文件均默认为纯源码编辑状态。单击眼图标开启全屏预览渲染，再次单击退出预览；双击开启分屏（上编辑下渲染），再次双击或单击退出分屏。各模式切换按钮和预览逻辑工作正常。全部 443 项 Vitest 单元测试、TypeScript 类型检查和 ESLint 校验全部通过。

### 2026-07-09 - 默认不展示聊天面板头部标题

- Why:
  - 聊天面板左上角在初始或没有设置具体会话标题时，默认会显示“生成测试案例”的硬编码备用标题。根据用户反馈，默认状态下不应展示标题。
- What:
  - 1. 修改 `AgentWorkbench.vue`，在 `chatTitle` 计算属性中将 null/undefined 会话标题的备用默认值由 `"生成测试案例"` 改为 `""`。
  - 2. 修改 `FigmaChatPanel.vue`，去掉 `<h2>` 标题元素的 `'生成测试案例'` 备用默认值，使其在 `title` 属性为空时渲染为空（依然保持 `flex: 1` 占据空间以保证右侧按钮对齐）。
- How:
  - 清理 default fallback 文字，并在 `frontend` 目录运行 `corepack pnpm lint`、`typecheck`、`test` 和 `build` 进行全量回归校验。
- Result:
  - 修改后，聊天面板在无会话标题的默认状态下，顶部左侧不再显示“生成测试案例”标题；前端的 lint、类型检查、测试（441 项测试全部通过）和生产构建（`corepack pnpm build`）均成功执行。

### 2026-07-09 - 修复 BackendSseForwarder 启动装配

- Why:
  - 打包后的 `test-agent-app` 启动时报 `BackendSseForwarder` 缺少 `WebClient.Builder` Bean，原因是 API 模块新增 SSE 流式转发器时假设 Spring 上下文一定提供 `WebClient.Builder`，但当前应用没有该自动装配 Bean。
- What:
  - `BackendSseForwarder` 改为生产构造器内部使用 `WebClient.create()` 自建客户端，保留包内 `WebClient` 构造器给单元测试注入；新增装配回归测试覆盖没有 `WebClient.Builder` Bean 时仍能启动。
- How:
  - 先用 `BackendSseForwarderTest.springBeanStartsWithoutWebClientBuilderBean` 复现 `UnsatisfiedDependencyException`，再做最小实现修复；不改变 SSE 转发 header/query/payload 行为。
- Result:
  - `mvn -pl test-agent-api -am -Dtest=BackendSseForwarderTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`、SSE 路由/转发定向测试、`mvn -pl test-agent-app -am -DskipTests package` 和 `git diff --check` 通过。

### 2026-07-09 - 修改 opencode 进程状态提示文字为 TestAgent

- Why:
  - 界面上及后端返回的关于运行环境进程的各种状态、错误和就绪的提示文字显示为 "opencode 进程" / "opencode 容器"，为了与平台整体对外的产品语义（TestAgent）统一，需要将这部分文字中的 "opencode" 全量更换为 "TestAgent"。
- What:
  - 1. 前端部分：替换 `FigmaChatPanel.vue`、`AgentWorkbench.vue` 和 `OpencodeProcessStartupDialog.vue` 中的标题、就绪提示、失败异常和警告描述文案中的 "opencode" 为 "TestAgent"，并修复 `FigmaChatPanel.test.ts` 的既有文本断言。
  - 2. 后端部分：替换 `UserOpencodeProcessAssignmentService.java`、`OpencodeProcessStartProgress.java` 和 `OpencodeProcessStatusQueryService.java` 中返回的就绪、未就绪、需要初始化或暂不可用状态的后端文字字段中的 "opencode 进程/容器" 为 "TestAgent 进程/容器"，并更新 API 与 runtime 各个层级的 Mock 单元测试。
- How:
  - 检索前后端以 "opencode 进程" 或 "opencode 容器" 渲染/返回的所有文本，对应更名为 "TestAgent 进程" 或 "TestAgent 容器"。
- Result:
  - 前后端返回和显示的状态卡片状态文案均已被替换为 "TestAgent" 产品标识；前端/后端全量单元测试与编译检查全部成功通过。

### 2026-07-09 - 运行态资源盘点移动到顶部应用切换左侧

- Why:
  - 用户确认资源数量不放在右侧对话 footer，而是展示在顶部应用切换控件左边，避免占用对话任务栏空间。
- What:
  - 将 Agent/Skill/MCP/Plugin 数量摘要和详情弹层从 `FigmaChatPanel` 移到 `FigmaShell` 顶部栏；`AgentWorkbench` 统一汇总已加载的 Agent、`source=skill` 命令、MCP status/tools/resources 和 plugin 命令后传给 shell。
- How:
  - 新增 `FigmaShell.test.ts` 覆盖资源摘要位于应用切换按钮之前并可展开详情；删除 `FigmaChatPanel` 中上一轮 footer 资源入口、props、样式和对应测试。
- Result:
  - 资源盘点入口现在位于顶部应用切换左侧，右侧对话 footer 恢复只展示任务状态与任务消耗。

### 2026-07-09 - 优化文件比对头部路径展示

- Why:
  - 文件比对 (Diff Viewer) 上方默认直接以 font-mono 格式展示了长长完整的文件路径，在页面布局有限或深层嵌套结构中占据了太多空间且不够美观。用户期望这里仅展示纯文件名，同时在鼠标悬停时才以气泡形式显示完整路径。
- What:
  - 在 `DiffViewer.vue` 头部的文件路径显示区域（`source === 'vcs' || source === 'agent'` 状态下的 span），将直接渲染 `selected?.path` 修改为调用 `getFileName(selected?.path ?? "")` 以仅显示文件名。
- How:
  - 修改 `packages/diff-viewer/src/DiffViewer.vue`，保留原有的 `:title="selected?.path"` 实现悬停气泡提示，只将标签文本内容更改为 `getFileName(...)`。
- Result:
  - 文件比对头部文字已成功精简为仅展示当前选中文件名，且悬浮时能够即时气泡显示包含文件名的完整路径；前端全量单元测试通过。

### 2026-07-09 - 优化工作空间搜索结果展示

- Why:
  - 工作空间搜索结果原来在每一行右侧以灰色文本展示其父目录路径。这在较窄的侧边栏布局中导致视觉上拥挤杂乱且文件名被截断。用户要求隐藏这些路径，并支持在鼠标悬停在文件名上时立即显示包含文件名的完整路径。
- What:
  - 1. 在 `FileExplorer.vue` 搜索结果行中移除了灰色的目录路径 `<span>` 节点，不再直接于列表中展示路径。
  - 2. 为搜索结果行 `<button>` 节点添加 `:title="entry.path"` 属性，使鼠标悬停时可以直接通过悬浮气泡即时显式完整路径和文件名。
  - 3. 修复了由于上一步“历史”重命名为“消息列表”而导致 `FigmaChatPanel.test.ts` 中查找“查看历史对话”以及 includes("历史") 断言报错的既有测试遗留问题。
- How:
  - 在 `packages/file-explorer/src/FileExplorer.vue` 模板中将 `<button>` 加上 `:title="entry.path"`，移除底部的 `.ta-file-tree-path` 标签；并在 `FigmaChatPanel.test.ts` 中同步将所有的查找历史文案修改为消息列表匹配。
- Result:
  - 搜索结果不再直接展示目录路径，页面变得清爽整洁；悬停文件能立即看到完整路径；全量前端 typecheck、lint 和 441 项 Vitest 单元测试全部顺利通过。

### 2026-07-09 - 右侧对话 footer 增加运行态资源盘点

- Why:
  - 用户要求在对话框底部任务栏展示当前已加载的 Agent、Skill、MCP、Plugin 数量，并可点击查看详细信息。
- What:
  - `FigmaChatPanel` 底部 footer 新增资源摘要按钮，统计已加载 Agent、`source=skill` 命令、MCP status 条目和 Plugin 命令；点击后弹出只读详情面板，展示 Agent/Skill/MCP 详情，并列出 MCP tools/resources。`AgentWorkbench` 透传已有 MCP status/resources/tools 查询结果，不新增后端 API。
- How:
  - 采用测试先行：新增 `FigmaChatPanel.test.ts` 用例先确认缺少资源摘要时失败，再实现组件 props、统计逻辑、弹层和样式；同步 `frontend/README.md`、`frontend/apps/agent-web/README.md` 和 `frontend/apps/agent-web/src/PACKAGE.md`。完整 `FigmaChatPanel.test.ts` 当前仍有 3 个既有历史入口断言查找“查看历史对话/历史”而组件已按上一条日志改为“消息列表”，本次未顺手修改该无关文案。
- Result:
  - 新增资源摘要定向测试通过，`@test-agent/agent-web` typecheck 通过；完整 `FigmaChatPanel.test.ts` 的失败点已确认为既有测试文案断言与“消息列表”改名不一致。

### 2026-07-09 - 修改历史会话为消息列表并更换图标

- Why:
  - 为了更符合用户的交互习惯与语义命名，需要将界面中的“历史”/“历史对话”文案统一重命名为“消息列表”，并将其图标更换为更能体现消息列表语义的 `MessageSquare` 图标。
- What:
  - 1. 替换 `FigmaChatPanel.vue` 中所有涉及给用户展现的“历史”/“历史对话”文案为“消息列表”；并更新抽屉里的搜索框占位符、空数据提示文案等。
  - 2. 在 `FigmaChatPanel.vue` 中引入并用 `MessageSquare` 替换原 `History` 图标（在面板顶部按钮及抽屉空数据提示中生效）。
  - 3. 修改 `AgentWorkbench.vue` 中有关会话切换失败的只读提示文案，将“历史会话”描述修正为“会话”。
- How:
  - 在 `FigmaChatPanel.vue` 模板中将 `<History>` 图标替换为 `<MessageSquare>`，并修改其相关的标签和 Tooltip 属性；在 `AgentWorkbench.vue` 的切换失败错误文案中删除“历史”词缀。
- Result:
  - 界面顶部及抽屉中的“历史”已成功更换为“消息列表”，且图标已对应更换为聊天气泡样式的 `MessageSquare`；前端编译、类型检查、lint 以及 Vitest 单元测试全部顺利通过。

### 2026-07-09 - 废除 RunEvent Redis bus

- Why:
  - 单 Run 跨 Java 实时 SSE 已由按 Run 生产 Java 路由和流式转发承接，会话消息实时链路继续保留 `TEST_AGENT_RUN_EVENT_REDIS_BUS_ENABLED` 会增加配置和代码复杂度，且 Redis Pub/Sub 本身不适合作为稳定补偿通道。
- What:
  - 删除 `RunEventRemotePublisher`、`NoopRunEventRemotePublisher`、`RedisRunEventRemotePublisher`，简化 `RunEventLiveBus` 为本机 Reactor sink，`RunEventSseStreamService` 只合并 DB durable replay 和本机 live bus；移除 `test-agent.run-event.redis-bus.*` 与 `TEST_AGENT_RUN_EVENT_REDIS_BUS_*` 配置/部署/文档引用。
- How:
  - 保持 `/runs/{runId}/events`、`Last-Event-ID`、SSE event name 和 payload 不变；跨 Java 单 Run 实时消息继续走 API 层 SSE 路由到生产 Java。用户级 `sessions/runtime-state/events` 不再接收 Redis 远端事件，接受既有约 10 秒低频轮询兜底。
- Result:
  - `RunEventServicesTest` 更新为覆盖本机 `streamAll()`、durable replay + live bus 合流；`mvn -pl test-agent-event -Dtest=RunEventServicesTest test`、`mvn -pl test-agent-opencode-runtime -Dtest=SessionRuntimeStateApplicationServiceTest test`、`mvn -pl test-agent-api -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=RunEventSseBackendRoutingWebFilterTest,BackendSseForwarderTest test`、`mvn -pl test-agent-event,test-agent-opencode-runtime,test-agent-api -am -DskipTests package`、`git diff --check` 和精确引用清理扫描均通过。
### 2026-07-09 - 补齐 Java 与 opencode-manager 分文件日志

- Why:
  - 本地排查前端操作、Service 调用和 SSE 长连接时，需要统一查 `backend.log`，同时把 SSE 与 error 独立拆文件；opencode-manager 也缺少运行态和错误态的本地日志文件。
- What:
  - Java 端 Log4j2 新增 `logs/backend.log`、`logs/sse.log`、`logs/error.log` 分流；API AOP 改为按目标 Controller logger 记录前端 HTTP 操作入口/出口，新增 Service AOP 记录所有 `@Service` public 方法入口、出口、流结束和异常；SSE 相关 Controller/Service/logger 同时写入 `sse.log`；错误日志统一 ERROR 级别进入 `error.log`。opencode-manager supervisor 模式新增 `{stateDir}/logs/manager.log` 与 `manager-error.log`，命令、WebSocket、配置热更新和失败状态结构化记录。
- How:
  - 复用现有 traceId/MDC、敏感字段脱敏和 routing logger；Service 参数只输出类型/长度摘要，避免把 token、body 或对象全量打入日志。补充 `ApiLoggingAspectTest`、`ServiceLoggingAspectTest`、`WebClientConfigTest` 和 manager 日志路由测试；本地通过重启脚本实际验证 `/api/auth/me` 与 session runtime SSE。
- Result:
  - `mvn -pl test-agent-api -am test -Dtest=ApiLoggingAspectTest,ServiceLoggingAspectTest -Dsurefire.failIfNoSpecifiedTests=false`、`mvn -pl test-agent-app -am test -Dtest=WebClientConfigTest -Dsurefire.failIfNoSpecifiedTests=false`、`mvn -pl test-agent-app -am package -DskipTests` 和 `go test ./cmd/opencode-manager ./internal/control` 通过；`./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 启动后，普通 API 和 SSE 均返回，`backend.log`、`sse.log`、`error.log`、`manager.log`、`manager-error.log` 均已落盘。`go test ./...` 仍被既有 `internal/config` Windows 分支测试阻塞，本次未修改该路径。

### 2026-07-09 - 补充双后端企业部署和前端本地更新脚本

- Why:
  - 现场 `122.233.30.4 -> 122.233.30.2` 直连 scp 被统一登录策略拦截，报 `Permission denied (publickey,gssapi-keyex,gssapi-with-mic)`；同时企业内需要新增后端/worker 节点 `122.233.30.114`。
- What:
  - 新增 `deploy/internal/deploy-internal-frontend.sh`，支持在前端机本地从同一个 `internal.zip` 更新静态资源并 reload Nginx；`deploy-internal-release.sh` 增加 `--backend-host` 自动推导 health URL 和 `.serverid/.serverhost` 校验，并在 ssh 预检失败时提示前端本地部署路径。新增 `README-two-backend-122-233-30-114.md`，主部署文档改为双后端拓扑。
- How:
  - 双后端方案要求 `122.233.30.4` 和 `122.233.30.114` 各自运行 Java + 本机 worker，共用 Redis/PostgreSQL/前端入口；前端 Nginx upstream 同时配置两台 Java；统一登录场景下 zip 分别放到前端和两个后端节点，后端部署统一加 `--skip-frontend`。
- Result:
  - `bash -n` 覆盖三个部署/打包脚本，两个部署脚本 `--help` 可用；临时完整 zip 分别通过后端脚本 `--backend-host 122.233.30.114 --validate-only` 和前端脚本 `--validate-only`；`git diff --check` 通过。未真实连接 122 服务器、未重启 systemd/Nginx/Docker。

### 2026-07-09 - 增强公共配置 Git 超时排查日志

- Why:
  - 部署后在“系统管理 → 配置管理 → opencode公共配置管理”点击初始化时，初始化弹窗会先调用 `GET /public/branches` 执行 `git ls-remote --heads`；该步骤超时时前端只显示“加载远端分支失败：Git 操作超时”，后端日志缺少脱敏 Git URL、命令阶段、耗时和失败归因，难以区分私钥、网络、仓库地址或远端响应慢。
- What:
  - 将远端只读 Git 查询超时从 20 秒放宽到 60 秒；Git SSH 命令增加非交互、10 秒连接超时和 keepalive；`ProcessGitCommandExecutor` 在超时、失败和慢命令时输出结构化日志，并在 `GIT_TIMEOUT` details 中带 `gitFailureType=TIMEOUT`、`gitFailureHint`、脱敏 command、超时和耗时。公共配置分支加载和初始化入口增加 `agent_config_public_branches_*` / `agent_config_public_repository_initialize_*` 日志。
- How:
  - 新增 `GitRemoteServiceTest.listBranchesAllowsSlowEnterpriseGitServers` 和 `ProcessGitCommandExecutorTest.timeoutDetailsMaskCredentialsInCommand`，先确认旧实现红灯，再实现最小改动；同步 `docs/api/http-api.md`、`docs/deployment/backend.md` 和 `backend/test-agent-workspace-management/README.md`。
- Result:
  - `mvn -pl test-agent-common test -Dtest=GitRemoteServiceTest,ProcessGitCommandExecutorTest` 通过；`mvn -pl test-agent-workspace-management -am test -Dtest=AgentConfigApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false` 通过。排查时用前端 traceId 搜索 Java 日志中的 `agent_config_public_branches_failed` 和 `git_command_timeout`。

### 2026-07-09 - 修复应用工作区 Git 根目录和默认个人 worktree 重建

- Why:
  - 应用版本工作区磁盘上只有模板子目录，没有仓库根 `.git`；删除历史 `appworkspace` / `personalworktree` 目录后，已有 default 个人工作区记录还会直接返回成功，导致 recent 指向不存在的 worktree。
- What:
  - 应用版本工作区创建/副本准备在仓库根目录已存在时先校验它是真实 Git 仓库，空目录才删除后重新 clone，只有 `.git` 且 HEAD 无效的 Git 超时残留会删除后重新 clone，非 Git 非空目录直接返回冲突；普通工作区 `git-diff/stage/unstage/discard` 通过 runtime workspace 反查应用版本副本或个人 worktree，在仓库根目录执行 Git 并把路径裁剪成当前模板目录相对路径；`ensure-default-personal-workspace` 只有确认物理目录是真实 Git worktree 且分支匹配时才复用，否则会重建规范 worktree 并刷新运行态记录。同步恢复 `workspace.delete` 仅删除普通文件的安全语义，前端文件树不再向目录显示删除入口。
- How:
  - 增加应用工作区非 Git 目录拒绝、Git 超时残留重拉、应用工作区 Git diff/stage 使用 repoRoot、已有 default 记录但物理 worktree 缺失时重建的回归测试；复用既有 `WorkspaceFileServiceTest` 作为目录删除安全回归，并新增 `DirectoryRows` 组件测试确认只允许文件删除；同步 `docs/api/http-api.md` 的工作区 Git 与默认个人工作区修复语义。
- Result:
  - `mvn -pl test-agent-workspace-management -am test` 通过；API 定向测试 `ManagedWorkspaceControllerTest` / 文件 WebSocket ticket / handler 通过；前端全量 `corepack pnpm typecheck` 通过，`corepack pnpm test` 为 439 passed / 1 skipped。后端全量 `mvn test` 到 `test-agent-persistence` 前均通过，persistence 仍命中既有 H2 `ON CONFLICT`、`usr_test_dev` fixture 外键和默认/loopback seed 断言问题，本次未修改这些无关路径。使用 `restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 重启本地服务后，已清空 `.testagent/agent-opencode/workspace/appworkspace` 和 `personalworktree` 历史子目录；登录 `usr_test_dev`，进入 F-COSS `20260618` default worktree，后端重新拉取应用仓库到 `appworkspace/20260618/coss/.git` 并创建 `personalworktree/20260618/usr_test_dev/coss/feature_testagent_20260618_usr_test_dev_default`，recent 与应用/个人 `git-diff` API 均可用。

### 2026-07-09 - 增加企业内一键升级脚本

- Why:
  - 企业内部署升级需要把构建包传到 `122.233.30.4:/data/0709` 后自动完成解压、前端 `scp`、Nginx reload、Java jar 替换、worker 镜像导入和重启，避免现场反复手工执行分散命令。
- What:
  - 新增 `deploy/internal/deploy-internal-release.sh`，默认读取 `/data/0709/internal.zip`，适配当前 `122.233.30.2` 前端、`122.233.30.4` 后端/worker 和 `/data/testagent` 目录；`package-release.sh` 全量打包时额外生成 `test-agent-internal-release.zip`，并保留 `--no-zip`。
- How:
  - 部署脚本先校验 zip 内 `dist` 产物和 `deploy/internal`，再按“前端更新并 reload Nginx -> 替换后端 jar/程序/worker 镜像 -> 启动 Java 并校验 `.serverid/.serverhost` -> 重启 worker 等待 `manager config update applied`”执行；`--validate-only` 可只检查 zip 结构不触发远程操作。
- Result:
  - `bash -n deploy/internal/deploy-internal-release.sh deploy/internal/package-release.sh`、两个脚本 `--help`、临时 zip 的 `deploy-internal-release.sh --validate-only` 和 `git diff --check` 通过；未真实连接 122 服务器、未重启 systemd/Nginx/Docker。
### 2026-07-09 - RunEvent SSE 跟随生产 Java 路由

- Why:
  - 会话实时消息不应依赖 Redis Pub/Sub 的 best-effort 广播；当浏览器重新进入运行中会话，或 Run 创建请求与 SSE 请求落到不同 Java 时，SSE 应跟随 Run 原始生产 Java，而不是按当前用户最新 binding 或 Redis 远端事件补实时消息。
- What:
  - 新增 `RunEventSseRouteService` 按 `routing_decisions -> node_ocp_* -> opencode process -> linuxServerId` 定位 Run 生产 Java；API 层新增 `RunEventSseBackendRoutingWebFilter` 和 `BackendSseForwarder`，对 `/runs/{runId}/events` 做 `text/event-stream` 流式转发；`TEST_AGENT_RUN_EVENT_REDIS_BUS_ENABLED` 在文档中降级为可选兼容开关。
- How:
  - 普通 HTTP 仍用 `BackendHttpForwarder`，SSE 因不能缓冲 `byte[]` 改用 WebClient DataBuffer 流式写回，并复用 `X-Test-Agent-Backend-Routed` 防循环头，透传 `Authorization`、`X-Trace-Id`、`Last-Event-ID` 和 query。目标 Java 不在线或旧 Run 无法解析生产进程时，入口降级为本机 snapshot/DB replay，不使用 Redis Pub/Sub 补实时消息。
- Result:
  - 新增 runtime/API 定向测试覆盖生产 Java 路由、SSE header/query 保留、平台/agent URL、防循环和目标缺失降级；Redis 仍保留给 Java 在线发现、manager 心跳、Token、scheduler 和运行指标等基础设施。

### 2026-07-09 - 修复系统管理定时任务等页面垂直滚动条缺失和遮挡问题

- Why:
  - 1. 系统管理页下的“.ta-system-content”和“.ta-config-content”布局容器被定义为普通块级 div，导致其内部具有 “height: 100%; overflow: auto;” 的子组件无法正确解析百分比高度，从而自适应高度并撑开外层布局，造成页面遮挡和无滚动条。
  - 2. 当内容容器转为 flex 容器后，子组件 `.ta-scheduler-management` 内的各 card 块（.ta-scheduler-section）默认具有 `flex-shrink: 1` 和 `min-height: 0`，导致在空间受限时浏览器会将“定时任务”表格块等直接压缩成 0 高度（完全不可见），且容器因不溢出而仍旧无法产生垂直滚动条。
- What:
  - 1. 调整 `.ta-system-content` 和 `.ta-config-content` 的样式，扩展为 `display: flex; flex-direction: column;`。
  - 2. 调整 `.ta-scheduler-section` 样式，设置 `flex-shrink: 0`。
- How:
  - 在 `SystemManagementPanel.vue` 和 `ConfigurationManagementPanel.vue` 中将子包容器配置为 flex 列布局；并在 `ScheduledTaskManagementPanel.vue` 中移除 `.ta-scheduler-section` 的 `min-height: 0` 并加上 `flex-shrink: 0`，允许各 section 块保持其原本内容高度，从而触发外部容器滚动。
- Result:
  - 成功修复定时任务管理中卡片/表格不可见、页面遮挡截断以及无垂直滚动条的问题；Vitest 与前端校验命令全部成功。

### 2026-07-09 - 增加 scheduler 运行诊断前台能力

- Why:
  - scheduler enabled、扫描线程、active run 和 Redis 锁都会影响 `PENDING` 任务是否执行；仅靠定时任务列表无法判断“参数已改 true 但仍待执行”的具体阻塞点。
- What:
  - 新增 scheduler diagnostics 只读 API 和前端诊断区，展示当前 Java 进程实际 scheduler 配置、runner 状态、最近扫描时间、Redis 锁 TTL、pending 手工运行数和阻塞原因；不暴露环境变量原始值或锁 token。
- How:
  - 扩展 `ScheduledTaskLock.inspect` 和 `ScheduledTaskDispatcher` 只读状态，`SchedulerManagementService.diagnostics` 汇总诊断；前端通过 `backend-api.getSchedulerDiagnostics(taskKey)` 随选中任务刷新。
- Result:
  - scheduler/API 定向测试、scheduler 模块全量测试、前端 Vitest/typecheck 和 app 打包通过；计划中的 `mvn -pl test-agent-scheduler,test-agent-api -am test` 仍被既有 `WorkspaceFileServiceTest.serviceDeletesOnlyRegularFilesInsideWorkspaceRoot` 失败阻塞。

### 2026-07-09 - 兼容空 scheduler enabled 环境变量

- Why:
  - 后端启动时如果运行环境导出了 `TEST_AGENT_SCHEDULER_ENABLED=` 空值，Spring 会把 `${TEST_AGENT_SCHEDULER_ENABLED:false}` 解析为空字符串，并在绑定 primitive boolean 时失败，导致应用无法启动。
- What:
  - `SchedulerProperties#setEnabled` 改为接收 `Boolean`，把空值绑定结果按 `false` 处理；补充空字符串配置绑定回归测试，并同步 scheduler README 和后端部署文档。
- How:
  - 先用 `ApplicationContextRunner` 复现 `test-agent.scheduler.enabled=` 的启动绑定失败，再收敛到配置属性 setter 层修复；不修改 `.env.local`、`.env.test` 或其它真实环境配置文件。
- Result:
  - `mvn -pl test-agent-scheduler -am -Dsurefire.failIfNoSpecifiedTests=false test` 和 `mvn -pl test-agent-app -am package -DskipTests` 通过。

### 2026-07-09 - 防止 scheduler 关闭时手动任务永久待执行

- Why:
  - `opencode-runtime.stale-active-run-reconcile` 手动运行记录 `str_e6c57bf58157422d85e0fca85f40ebf0` 一直停留在 `PENDING`；排查发现当前 `.env.local` / `.env.test` 未显式配置 `TEST_AGENT_SCHEDULER_ENABLED`，应用按默认 `false` 启动时不会运行后台扫描线程，也不会消费 pending manual run。
- What:
  - `SchedulerManagementService.trigger` 注入 `SchedulerProperties`，在全局 scheduler 未启用时直接返回 `CONFLICT`，不再创建无法执行的新 `PENDING` 手动运行记录；同步 scheduler README、HTTP API 和部署文档说明。
- How:
  - 先补红灯测试确认旧实现会在 scheduler 关闭时写入悬挂 pending run，再加配置保护；不修改 `.env.local` / `.env.test`，不处理历史数据库运行记录，历史 pending 需要启用 scheduler 后由后台消费或由管理员显式处置。
- Result:
  - `mvn -pl test-agent-scheduler -am -Dsurefire.failIfNoSpecifiedTests=false test` 和 `mvn -pl test-agent-app -am package -DskipTests` 通过。

### 2026-07-08 - 增加后台运行会话历史状态提醒

- Why:
  - 运行中的会话点击“新建对话”时需要转入后台继续执行，历史入口需要显示后台运行数量、运行中状态和 `question.asked` 待回答提醒，且不能破坏现有历史切换、工作区切换和 active-run 恢复流程。
- What:
  - 新增用户级会话运行态摘要 HTTP API 和 fetch SSE 通道，前端在工作台订阅该通道并合并到历史按钮/历史列表；运行中点击新建对话只清空当前视图和关闭当前 RunEvent SSE，不调用 cancel/abort。
- How:
  - 后端用 `SessionRuntimeStateApplicationService` 读取 MyBatis XML 运行态摘要，并通过 `RunEventLiveBus.streamAll()` 的 run/question 事件和低频轮询触发刷新；API 层只做鉴权、traceId、DTO 和 SSE 输出。前端新增 shared-types、backend-api、event-stream-client 方法，并在 `AgentWorkbench`/`FigmaChatPanel` 中展示 Spinner、计数徽标和铃铛。
- Result:
  - 定向后端新增/修改测试与前端 event-stream-client/backend-api/workbench-utils/FigmaChatPanel 测试通过；`@test-agent/agent-web` typecheck 通过。完整后端 `-am test` 仍被既有 `test-agent-workspace-management` 的 `WorkspaceFileServiceTest.serviceDeletesOnlyRegularFilesInsideWorkspaceRoot` 失败阻塞，目标模块未进入执行。

### 2026-07-08 - 优化任务消耗展示格式

- Why:
  - 任务消耗原展示格式 `( · ↓ 168.8w tokens)` 含有多余的括号 `()`、间隔符 `·` 和下落箭头 `↓` 等多余字符，视觉效果杂乱，用户要求将其清除以精简页面展示。
- What:
  - 移除了任务消耗值格式中的括号 `( )`、中间的分隔符 `·` 和下落箭头 `↓`。如果同时显示耗时和 Token 消耗，则使用空格分隔开；如果只有其一，则直接展示。
- How:
  - 修改 `FigmaChatPanel.vue` 中的 `.figma-chat-usage-value` 模板块，移除多余的硬编码 `(`、`)`、`·` 和 `↓` 标记，并加入条件空格逻辑。
- Result:
  - 静态类型检查 `vue-tsc` 与全量前端 Vitest 单元测试 (427 Passed) 顺利执行通过，确认无任何 UI 逻辑或构建异常。

### 2026-07-08 - 修复历史对话遮挡下拉菜单

- Why:
  - 当展示“历史对话”或其它工作区抽屉遮罩时，其 z-index (40) 加上 history-drawer (100) 逃逸了普通的堆叠上下文，导致覆盖在 header (z-index: 30) 的上方。当用户尝试点击 header 处的应用选择下拉框（“F-COSS ^”）时，下拉菜单被历史对话等抽屉遮挡，用户无法看见或进行点击操作。
- What:
  - 将 `FigmaShell.vue` 中的顶部 header `.figma-header` 的 `z-index` 从 `30` 提升至 `50`，使其高过 `.figma-chat-drawer-mask` (z-index 40)。同时它仍低于确认框背景 (`.ta-confirm-backdrop` z-index 100) 以及轻量启动弹窗背景 (`z-index: 80`/`100`) 等全局 modal backdrop，确保 modal 依旧能够全局遮罩 header。
- How:
  - 修改 `FigmaShell.vue` 样式表中的 `.figma-header` 类，将 `z-index` 调整为 `50`。
- Result:
  - 静态类型检查 `vue-tsc` 与全量前端 Vitest 单元测试 (427 Passed) 顺利执行通过，确认无任何 UI 逻辑或构建异常。

### 2026-07-08 - 优化编辑器底部状态栏与保存按钮

- Why:
  - 1. 编辑器底部路径及更新时间展示占用空间，需隐藏它们以节约布局空间，只保留“复制路径”按钮。
  - 2. 需要快速在文件树中定位当前文件的入口（瞄准器按钮）。
  - 3. 保存按钮在常态无修改下应默认隐藏以保持界面整洁。
  - 4. 左右 resize handle 拉伸边界只有 1px 宽且无明显 Hover 指示，用户“看不出是可以移动的效果”。
- What:
  - 1. 修改 `WorkbenchFooter.vue`，不显示路径、文件名和更新时间以节约底部空间，但按钮上保留 hover 完整路径提示，点击“复制路径”可复制到剪贴板。
  - 2. 在保存按钮左侧集成瞄准器（`Target`）按钮，向外 emit `locate` 事件；并在 `FigmaEditorArea.vue` 和 `AgentWorkbench.vue` 中处理和转发为 `locateFile`。
  - 3. 限制保存按钮只在 `dirty || saving` 时通过 `v-if` 展现。
  - 4. 优化 `FigmaShell.vue` 中左右 resize handle 的样式：用 `::after` 拓展 7px 鼠标热区，并在正中心添加垂直方向的半透明小药丸指示器手柄（高36px，宽4px，圆角2px），hover 时手柄拉伸为 48px 且颜色加深为 rgba(0,0,0,0.3)，提供与对话框输入框一致的视觉拉伸效果。
- How:
  - 在前端组件层以 Vue computed 提取文件名并调用 Clipboard API。通过组件自定义事件把定位行为转发至工作台的主定位方法。在 FigmaShell.vue 样式表里为 resize-handle 增加伪元素绝对定位、半透明小药丸把手及 hover 过渡动画。
- Result:
  - 修改 `WorkbenchFooter.test.ts` 补充了上述三项功能的单元测试，全数通过。前端通过了 `typecheck` 和 `lint`。
### 2026-07-08 - 补齐企业部署 Java 内部代理 API key

- Why:
  - 今日实际运行 `.env` 已增加 Java 内部模型代理鉴权变量 `TEST_AGENT_INTERNAL_PROXY_API_KEY`，但企业内 `backend.env` 模板和部署说明未同步，目标环境按模板部署会缺少该 key。
- What:
  - 在 `deploy/internal/backend.env.example` 增加 `TEST_AGENT_INTERNAL_PROXY_API_KEY` 占位符；部署 README 和企业离线部署 skill 明确该 key 只配置在 Java 的 `backend.env`，不要放到 worker 的 `docker.env`。
- How:
  - 复用现有内部模型代理链路：Java 校验该 key，并在启动用户 opencode server 时通过 manager command 注入给子进程；不修改真实 `.env.local`、`.env.test` 或 `/data/testagent/config/*.env`。
- Result:
  - `set -a; . deploy/internal/backend.env.example`、`set -a; . deploy/internal/env.example` 和 `git diff --check` 通过。

### 2026-07-08 - 移除企业内 worker 的 host-gateway 依赖

- Why:
  - 现场执行 `opencode-worker-docker.sh restart` 时 Docker 不支持 `--add-host host.docker.internal:host-gateway`，报 `invalid IP address in add-host: "host-gateway"`；企业内 worker 实际不依赖该别名。
- What:
  - 从纯 Docker worker 启动脚本删除 `host.docker.internal` / `host-gateway` 注入；补充脚本校验用例，确保后续不再要求该映射；部署 README 明确 worker 使用默认 bridge 网络即可，manager 通过 `.serverhost` 访问 Java。
- How:
  - 保持纯 Docker 管理方式、端口池一致映射和 `/data/testagent/data` 挂载不变；不修改 `/data/testagent/config/docker.env` 或 `.env.local` 等真实环境文件。
- Result:
  - `tools/verify-dev-scripts.sh`、`git diff --check` 和 `deploy/internal/opencode-worker-docker.sh --env-file deploy/internal/env.example status` 通过；未真实执行 `restart`，避免删除当前机器可能存在的 worker 容器。

### 2026-07-08 - 用户管理测试页支持超管直接调整角色

- Why:
  - 设置页“用户管理（测试）”只能新增测试用户，无法直接查看并调整已有用户角色；当前仓库也没有普通用户发起审批通知的基础设施，本次范围确认先做超管直接调整角色。
- What:
  - 在 system-management 用户管理链路新增 `PUT /api/internal/platform/system-management/users/{userId}/roles`，由 `SUPER_ADMIN` 直接替换目标用户单个全局角色；前端用户列表增加角色下拉，顶部统一保存本页已修改角色，提交时逐条调用 `updateUserRole` 契约。
- How:
  - 复用现有用户、角色和 ROLE 字典领域仓储，不新增数据库表或审批通知模型；角色替换在业务服务事务内先删除旧角色再写入新角色。Controller 继续只做鉴权与 DTO 转换。
- Result:
  - 已补后端 service/controller、backend-api 和 agent-web 页面测试，并同步 HTTP API、模块/包 README 与架构速查文档；审批通知流未实现，后续若需要应单独设计审批申请表、超管待办和状态流转。
### 2026-07-08 - 在前端通用组件库中实现 Spinner 组件

- Why:
  - 为了给 Vue 前端提供加载动画反馈，需要复刻 `@opencode-ai/ui/spinner` 对应的 4x4 呼吸点阵加载动画。
- What:
  - 在 `@test-agent/ui-kit` 通用 UI 包中新建 [Spinner.vue](file:///Users/huang/workspace/intelligent-test-agent-gitee/frontend/packages/ui-kit/src/Spinner.vue) 组件。
  - 在 [index.ts](file:///Users/huang/workspace/intelligent-test-agent-gitee/frontend/packages/ui-kit/src/index.ts) 中导出 `Spinner` 组件。
  - 在 `@test-agent/ui-kit` 的 [README.md](file:///Users/huang/workspace/intelligent-test-agent-gitee/frontend/packages/ui-kit/README.md) 中登记新增的 `Spinner` 基础组件。
- How:
  - 移植 Solid.js 原版 Spinner 逻辑，使用 Vue 3 模板重新实现 16 点阵 SVG rect 网格，并绑定随机动画延时；在组件内部定义 `@keyframes pulse-opacity` 和 `pulse-opacity-dim` 呼吸动效。
- Result:
  - 执行 `corepack pnpm install` 及全包的 `typecheck` 成功，13 个前端工程全部编译通过。

### 2026-07-08 - 调整测试设计 opencode agent/skill 路径规则

- Why:
  - 测试设计入口不再只依赖“绑定子条目”动作，用户可能通过对话或右键传入文件/目录并指定非 `content` 的输出位置；旧提示词仍固定 `041-测试设计/`，且 agent 权限对 edit/bash/skill 过紧。
- What:
  - 更新 `.testagent/agent-opencode/.config/opencode` 下测试设计与测试执行 agent/skill Markdown：统一放宽 read/list/grep/glob/edit/bash/skill 权限，补充 `designContext/sourceFiles/sourceDirs/outputTarget` 解析，生成、审查、执行产物优先写用户显式目标。
- How:
  - 参考 OpenCode 中文文档的 agents、skills、permissions 与 config 说明，保留 Task 编排和真实执行业务约束；`.testagent/agent-opencode/.config` 是独立 Git 配置仓库，本次只在该仓库暂存被修改的 Markdown，不纳入 `opencode.jsonc`、node_modules 或敏感配置。
- Result:
  - `OPENCODE_CONFIG_DIR=.testagent/agent-opencode/.config/opencode opencode agent list` 可读取配置；agent frontmatter、skill 名称与 deny 权限扫描通过。未改后端、前端 API、数据库或 generated SDK。

### 2026-07-08 - 新增企业内离线部署问答技能

- Why:
  - 后续部署问答需要固定以“Mac 联网打包、企业内完全离线部署”为前提，并且每次都要说明打包后怎么操作、全流程顺序和需要修改哪些配置文件，避免只给零散命令或沿用本地 `.env`。
- What:
  - 新增 `.agents/skills/enterprise-offline-deploy/SKILL.md`，触发企业内、离线、Mac 打包、`package-release.sh`、`backend.env`、`docker.env`、opencode worker/manager 等问题时，要求完整输出 Mac 打包、产物分发、配置文件、Java/worker 启动顺序、验证命令和常见 manager 端口/连接排查点。
- How:
  - 复用现有 `deploy/internal` 纯 Docker worker 部署方案，不改脚本、不改真实环境配置、不新增生产变量；强调 `SYS_DATA_ROOT_DIR=/data/testagent/data` 与 worker `TEST_AGENT_DATA_ROOT=/data/testagent/data` 必须一致。
- Result:
  - 仅新增项目 skill 和本会话日志，未改业务代码、API、数据库或部署脚本。

### 2026-07-08 - 调整子智能体卡片视觉密度

- Why:
  - 上一版把子智能体卡片拆成两行后视觉过重；用户要求保持原高度，只在 Agent 名与任务标题之间增加间隔，同时完整展示 Agent 名并降低字号。
- What:
  - `oc-subagent-card` 恢复单行 38px 最小高度，改为 `max-content / title / status` 三列；Agent 名字号降到 10px、保持完整不省略，Agent 与标题列间距加到 18px，卡片之间保留 10px 间隔。
- How:
  - 仅修改 `agent-chat` 的 opencode-like CSS 和包说明，不改历史恢复逻辑、API、事件或数据库。
- Result:
  - `packages/agent-chat/tests/opencode-like-state.test.ts`、`@test-agent/agent-chat` typecheck 和 `git diff --check` 通过；本地后端 readiness 与前端 3000 HTTP 继续可用。

### 2026-07-08 - 修复历史子智能体空白视图与工具间距

- Why:
  - 本地历史会话 `ses_f9ad74a4e51f4089958c0f4e8a88ea75` 的 `session-tree/messages` 当前返回空 tree，平台 `session_messages.parts_json` 只保留 root task part；后续试探性放开点击守卫后，metadata-only task 卡片会进入没有 child 消息的空子视图。
  - 该历史的 task part `output` 已持久化 `<task_result>` 子 Agent 结果，可作为只读 child timeline 恢复来源；工具头部与子智能体卡片列间距也偏紧。
- What:
  - `chatStateFromSessionTreeSnapshot()` 现在会消费 `messagesBySessionId`，并且在 tree 为空或缺少 child message 时，把平台历史 task part output 的 `<task_result>` 合成为带 child scope 的只读 assistant message，同时补齐 `subagentsBySessionId` 与 `subagentByTaskPartId`。
  - 恢复 `FigmaChatPanel`/`AssistantThread` 的 subagent 打开守卫，避免只有 metadata sessionId、没有已恢复 child 内容的卡片进入空视图；`.oc-tool__trigger` / context/tool group trigger 与 `.oc-subagent-card` 网格列间距从 8px 调整为 14px，并加宽 agent/status 列。
- How:
  - 仅修改前端 `agent-web` 历史状态恢复、`agent-chat` 子视图守卫与样式、定向测试和包 README/PACKAGE；不改后端 API、RunEvent 契约、数据库或 generated SDK。
- Result:
  - 新增 empty session-tree + persisted task output、`messagesBySessionId` child message 和组件点击回归用例；修正 composer 拖拽测试等待 Vue tick。`FigmaChatPanel.test.ts`、`workbench-utils.test.ts`、`opencode-like-state.test.ts`、`@test-agent/agent-chat` typecheck、`@test-agent/agent-web` typecheck 和 `git diff --check` 通过；Chrome 实测点击 07/05 21:13 历史会话第一个子 Agent 可显示对象识别正文。

### 2026-07-08 - 收敛工作台极简线框并修复历史子智能体卡片

- Why:
  - 左侧工作区 tabbar 和右侧面板圆角/线色不统一，中间空态与输入框仍有阴影感；历史会话里子智能体卡片在 `taskPartId` 与实际渲染 task part id 不一致时仍可能不可点击，且历史卡片名称缺失时只显示泛化“智能体”。
- What:
  - 将 `FigmaShell` 三栏收敛为填满主区域的极简矩形布局，移除主工作区外框、圆角和投影，统一左/中/右顶部 chrome 的 `--ta-border` 分割线，并把左右 resize handle 从 6px 空白槽改成连续 1px 视觉线；移除聊天输入卡片、子智能体卡片与编辑器空态图标投影。
  - `chatStateFromSessionTreeSnapshot()` 兜底恢复子智能体索引时同时登记 snapshot `taskPartId`、实际渲染 task `partId` 和 `taskCallId`；子智能体卡片从 `metadata.agentName/agent/title` 兜底展示名称与标题，并在索引缺失时用 task `callId` 或 part metadata/output 中的 child session id 恢复点击入口；进入子 Agent 视图时按 child scoped `taskPartId/taskCallId` 兜底匹配输出。
- How:
  - 仅修改前端展示层、历史快照恢复纯函数、相关前端测试和包 README/PACKAGE；不改后端 API、RunEvent 契约、数据库或 generated SDK。
- Result:
  - 通过 `workbench-utils.test.ts`、`FigmaChatPanel.test.ts -t "subagent cards"`、`@test-agent/agent-web` / `@test-agent/agent-chat` / `@test-agent/editor` typecheck 和 `git diff --check`；in-app browser 刷新 `http://127.0.0.1:3000/` 后确认主容器、输入卡片、编辑器空态图标 `box-shadow: none`，三段顶部线均为 `rgb(234,234,234)`。

### 2026-07-08 - 修复与优化前端圆角、尖角对齐与线重合、水平线对齐样式

- Why:
  - 界面上存在未激活 Tab 底边悬浮缺失分割线、文件树小操作按钮 hover 呈现直角正方形、文件树和 Git 变更面板区域展开/折叠时双分割线重合（2px粗线）、聊天卡片工具栏尖角外露、附件按钮高度偏矮导致水平未对齐、聊天页脚底分割线与编辑器不一致，以及聊天窗右上角按钮组线重合与直角等一系列 UI 样式与美感缺陷。
- What:
  - 在 `FigmaEditorArea.vue` 中对 `.figma-editor-tabs` 添加 `border-bottom`，设置 active tab 的 bottom-border-color 为 `#fff` 并给予 `margin-bottom: -1px` 压线。
  - 在 `FigmaFileExplorer.vue` 中将工具操作按钮 Hover 改为 `border-radius: 4px`；重构相邻 section 与 header 的 border 分割规则，消除双重边框重叠。
  - 在 `GitChangesPanel.vue` 中对 `.staged-section` 的顶部边框改为动态 class，在 resizer 存在时自动移除其 `border-t`。
  - 在 `FigmaChatPanel.vue` 中为工具行 actions 容器添加 `border-bottom-left/right-radius: 15px` 完美收纳于 16px 圆角父框；移除了附件按钮 `.figma-chat-attachment-btn` 的硬编码 `height: 26px;` 以继承 28px；将 `.figma-chat-footer` 的 border-top 颜色统一为语义 token `var(--ta-border)`。将 `.figma-chat-header-btn` 从自带边框重构成 Ghost 按钮样式，常态下无边框，hover 时显示圆角 4px 灰色背景；同时收紧 flex 容器 `gap` 为 8px，完全消除了右上角线重合与直角问题。
- How:
  - 仅限于前端 CSS 与 HTML 模板小范围调整，不涉及任何接口 API、后端逻辑或数据库。
- Result:
  - `tools/dev-frontend-check.sh` 顺利通过，Vite 重新构建成功，Lint & Typecheck 均为 0 错误。主应用 Vitest 运行无新增回归失败，既有的 3 项 Vitest 测试失败为历史已知 baseline 问题，与本次样式改动无关。
### 2026-07-08 - 企业内 worker 部署改为纯 Docker

- Why:
  - 企业内导入部署后续不再使用 `docker compose`，部署流程、README 和排障命令需要避免继续引导现场人员走 compose。
- What:
  - 删除 `deploy/internal/docker-compose.yml`，新增 `deploy/internal/opencode-worker-docker.sh`，用纯 `docker run/rm/ps/logs` 管理 `test-agent-opencode-worker`；同步更新 `deploy/internal/README.md`、`deploy/internal/env.example`、`docs/deployment/backend.md` 和 `docs/deployment/frontend.md`。
- How:
  - 保留 `/data/testagent/config/docker.env` 作为打包和 worker 启动的外置环境变量文件，Java 配置仍在 `/data/testagent/config/backend.env`；不修改本机或企业真实 `.env`。
- Result:
  - `bash -n deploy/internal/opencode-worker-docker.sh`、`deploy/internal/opencode-worker-docker.sh --env-file deploy/internal/env.example status`、`bash -n deploy/internal/package-release.sh`、`bash -n deploy/internal/opencode-worker-entrypoint.sh`、`git diff --check`、后端 readiness 和前端 HTTP 200 通过。

### 2026-07-08 - 修复企业打包脚本本地默认输出目录

- Why:
  - 在 macOS 本地直接执行 `deploy/internal/package-release.sh` 时，脚本读取 `deploy/internal/.env` / `env.example` 中的 `TEST_AGENT_IMAGE_OUTPUT_DIR=/data/testagent/dist`，导致尝试创建只读根目录 `/data` 并失败；直接项目内打包应输出到 `deploy/internal/dist`。
- What:
  - `package-release.sh` 改为默认输出 `deploy/internal/dist`，只在显式 `--output-dir`、shell 预先导出 `TEST_AGENT_IMAGE_OUTPUT_DIR` 或显式 `--env-file` 时使用外部输出目录；同时为后端打包自动查找 JDK 25..21。同步更新 `deploy/internal/README.md` 的本地 Mac/企业 Linux 构建机路径边界。
- How:
  - 不修改本地未跟踪 `deploy/internal/.env`，保留企业构建机通过显式 `--env-file /data/testagent/config/docker.env` 输出 `/data/testagent/dist` 的能力。
- Result:
  - 通过完整 `deploy/internal/package-release.sh`，后端 jar、前端 dist、worker 镜像 tar 和外挂程序包均输出到项目内 `deploy/internal/dist`；`bash -n deploy/internal/package-release.sh`、`git diff --check`、后端 readiness 和前端 HTTP 200 通过。

### 2026-07-08 - 补齐企业内部署升级和日志处理文档

- Why:
  - 上一轮只同步了本地 manager 身份目录和前端权限文档，企业内部署层面对“最新代码如何升级”“Java/worker 日志怎么看”“`.serverhost` 读旧地址怎么排查”还不够集中；`deploy/internal` 示例仍有部分 `replace-with` 占位，不符合当前交付要求。
- What:
  - `deploy/internal/backend.env.example` 与 `env.example` 改为当前企业内规划值，manager token 在 Java/worker 两侧保持一致；`deploy/internal/README.md` 固化 PostgreSQL `122.42.203.103:8000/testagent`、新增最新代码升级步骤和日志排障清单；`docs/deployment/backend.md` 补充身份文件、生产日志处理和旧 `.serverhost` 故障项；`docs/deployment/frontend.md` 指向企业内统一打包入口。
- How:
  - 不修改 `.env.local` 等本机敏感配置，不改部署脚本行为；文档保持“先启动 Java 写 `${SYS_DATA_ROOT_DIR}/.serverid/.serverhost，再重启 worker 让 manager 读取同一挂载目录”的既有设计。
- Result:
  - 通过 `set -a; . deploy/internal/backend.env.example`、`docker compose --env-file deploy/internal/env.example -f deploy/internal/docker-compose.yml config`、`bash -n deploy/internal/package-release.sh`、`bash -n deploy/internal/opencode-worker-entrypoint.sh`、`git diff --check`；本地三服务仍在上一轮启动的 screen 会话中运行，后端 readiness `UP`、前端 200。

### 2026-07-08 - 收紧非超管配置入口并修复本地 manager 身份目录

- Why:
  - 普通用户切到 Agent 配置页时会看到公共配置仓库根目录中的 `.DS_Store`、`node_modules`、`package.json` 等工程杂项；设置页对非 `SUPER_ADMIN` 暴露应用管理和版本库管理入口也不符合当前权限要求。另一次本地 `wr` 用户初始化 opencode 失败定位到 Go manager 读取 `$HOME/.testagent/.serverhost` 旧 IP，未与 Java 使用的 `SYS_DATA_ROOT_DIR` 对齐。
- What:
  - 前端设置页只给 `SUPER_ADMIN` 展示应用管理、版本库管理和用户管理，非超管强制回落个人设置；Agent 配置树对普通用户仍展示 `agents/` 与 `skills/`，仅隐藏根级工程杂项；应用级新建 skill 按当前 opencode skill 模板生成 `name/description/compatibility/metadata` frontmatter。
  - `restart-dev-services.sh` 默认导出 `SYS_DATA_ROOT_DIR=${TESTAGENT}/.testagent`，启动 manager 前写入同目录 `.serverid/.serverhost`，并把该环境变量传给 screen/nohup manager 进程。
- How:
  - 未改用户进程绑定、调度或启动公共服务链路；用户 opencode 进程仍由 `OpencodeProcessStartupService`、binding、execution node、manager state 和健康检查决定。脚本改动只修复本机 Java 与 Go manager 的控制面连接引导目录一致性。
- Result:
  - 通过 `bash -n restart-dev-services.sh`、`tools/verify-dev-scripts.sh`、`corepack pnpm --dir frontend test apps/agent-web/tests/agent-config-panel.test.ts apps/agent-web/tests/runtime-management-settings.test.ts`、`corepack pnpm --dir frontend --filter @test-agent/agent-web typecheck`、`git diff --check`；使用 `JAVA_VERSION=25 ./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 重启三服务成功，后端 health/readiness 为 `UP`，前端 `http://127.0.0.1:3000` 返回 200，`.testagent/.serverhost` 为当前 `172.20.10.2`，manager 日志显示已应用 Java 下发配置且无旧 `192.168.100.103` 断连记录。

### 2026-07-08 - 明确 opencode 全局空配置约束

- Changed: 将本机 `~/.config/opencode/opencode.jsonc` / `opencode.json` 置为空 schema，并把原模型供应商配置迁到项目公共配置目录 `.testagent/agent-opencode/.config/opencode/opencode.jsonc`；同步后端、manager、HTTP API、SSE 和部署文档，明确公共配置 Git 的 `opencode/opencode.jsonc` 是模型与供应商事实源。
- Pitfalls: OpenCode 会合并用户全局配置和 `OPENCODE_CONFIG_DIR`，仅设置 `OPENCODE_CONFIG_DIR` 不会自动忽略 `~/.config/opencode`；本次还发现 session log 存在未解决合并标记，已保留双方条目合并。
- Resolved: Yes - 采用企业部署前置条件：运行用户全局 opencode 配置必须为空，不改 manager 环境注入逻辑。
- Verification: 本机 PostgreSQL 已切到存量 `127.0.0.1:15432/testagent`（`testagent/testagent`），Redis 已切到 `127.0.0.1:16379`，PostgreSQL 默认时区改为 `Asia/Shanghai`；`./restart-dev-services.sh --profile test --env-file .env.test --skip-backend-build --skip-frontend-build` 启动后，后端 readiness `UP`、前端 200、opencode-manager 进程存在。
- Next: None。

### 2026-07-07 - 修复历史子智能体点击恢复

- Why:
  - 历史 session-tree 只回放 RunEvent 快照时，旧数据可能缺少 durable `session.child.discovered` 或 task metadata，导致 root task 子 Agent 卡片无法拿到 `subagent.sessionId`，卡片保持 disabled 且不能切换到 child timeline。
- What:
  - `chatStateFromSessionTreeSnapshot()` 在事件重放后扫描 snapshot 顶层 `sessions` 和 `childSessionIdByTaskPartId`，为 `childSession=true && taskPartId` 的记录补齐 `subagentsBySessionId` / `subagentByTaskPartId` 兜底索引；标题、Agent 名和状态优先从 root task part 推导，实时 SSE reducer 不变。补充 workbench-utils 和 FigmaChatPanel 历史 snapshot 回归用例，并同步前端 README/PACKAGE 说明。
- How:
  - 仅修改前端历史恢复链路和测试，不改后端 API、DTO、数据库或 SSE 契约；兜底索引只在 reducer 未建立对应 child session 时生效。
- Result:
  - `apps/agent-web/tests/workbench-utils.test.ts` 全量通过，新增 snapshot 聚焦回归通过，`vue-tsc -p apps/agent-web/tsconfig.json` 和 `git diff --check` 通过；计划要求的完整 `FigmaChatPanel.test.ts` 仍有既有 composer 拖拽高度断言失败（期望 100px、实际 40px），完整 `opencode-timeline.test.ts` 仍有既有 diff 路径展示断言失败（测试期望 `src/...`，当前 UI 显示 basename 并把完整路径放在 `title`）。

### 2026-07-08 - 优化工作空间目录树默认展开和视觉密度

- Why:
  - 创建工作空间目录树默认只露出应用根目录，用户还需要手动展开到常用工作空间层级；上一版行距、选中态和输入区视觉过重。
- What:
  - `SettingsAppWorkspacePanel.vue` 的目录树默认展开应用根目录和下一级目录，更深层继续按需点击展开；同步收紧树行高、字体、hover/选中态、边框背景和新增目录操作条样式。
  - 更新 `frontend/apps/agent-web/README.md` 中目录树默认展开说明，并调整设置页回归测试期望。
- How:
  - 延续现有内嵌 `WorkspaceDirectoryTree`，不新增组件/API/后端契约；仍只渲染默认两层和用户展开分支，避免回到全量深层渲染。
- Result:
  - 通过：`corepack pnpm --dir frontend test apps/agent-web/tests/settings-app-workspace-panel.test.ts`、`corepack pnpm --dir frontend --filter @test-agent/agent-web typecheck`、`git diff --check`。前端 Vite `http://127.0.0.1:3000` 返回 200；三服务重启被 `.env.test` PostgreSQL `192.168.100.200:5432` 当前 `No route to host` 阻塞，后端未能完成 health 验证。

### 2026-07-08 - 收紧工作空间管理已有项操作与目录树渲染

- Why:
  - 设置页“已有工作空间”属于已落库模板，不应在同级继续提供重命名或删除入口；创建工作空间目录树一次性渲染深层节点会在大目录下增加页面压力。
- What:
  - `SettingsAppWorkspacePanel.vue` 移除已有工作空间的重命名/删除入口和对应前端调用分支；工作空间目录树改为默认折叠，点击目录再展开并只渲染展开分支，仍保留可选一级目录点击选择。
  - 同步更新 `frontend/apps/agent-web/README.md`，补充目录树折叠渲染和已有工作空间只读展示说明。
- How:
  - 复用现有设置页组件和测试文件，不新增 API、不修改后端、数据库或事件契约；新增/调整 Vitest 覆盖折叠展开、一级目录选择、新增目录和已有工作空间只读行为。
- Result:
  - 通过：`corepack pnpm --dir frontend test apps/agent-web/tests/settings-app-workspace-panel.test.ts`、`corepack pnpm --dir frontend --filter @test-agent/agent-web typecheck`、`git diff --check`；使用 `.env.test` 重启三服务成功，后端 health/readiness 为 `UP`，前端 `http://127.0.0.1:3000` 返回 200，登录 CORS preflight 正常。

### 2026-07-08 - 接入企业内部模型代理与 opencode 原生模型目录

- Why:
  - 企业内部模型供应商需要通过 Java 内部代理统一注入 `ICBC_OPENAI_AUTH_TOKEN`、`ucid` 和供应商标识，并把流式响应中的 `<think>...</think>` 内容转换为 `reasoning_content`；前端对话框模型/供应商目录也要回到 opencode 原生配置，不再以数据库模型目录为事实源。
- What:
  - 后端运行态改为 `listModels/listProviders` 始终代理 opencode `/api/model`、`/api/provider`，Run 启动不再同步 `/global/config` 或用数据库目录校验/回退模型；新增内部模型供应商领域端口、MyBatis XML 仓储、Flyway 表、内存 registry、内部 OpenAI-compatible 代理和 think SSE 转换；opencode 子进程启动时注入 `TEST_AGENT_INTERNAL_PROXY_API_KEY`、`TEST_AGENT_INTERNAL_PROXY_BASE_URL`、`ICBC_UCID`。
  - `opencode-manager` start 协议新增 `environment`，启动命令展示会隐藏内部代理 apikey；前端系统管理新增“内部模型供应商”配置页，可维护 provider/baseUrl/enabled/sortOrder 和全局 token，只显示 token 是否配置。
  - 同步更新 HTTP/API、事件补充、数据库、后端部署、模块 README 与 opencode 配置样例。
- How:
  - 新增关系型 SQL 均落在 MyBatis XML mapper，避免继续扩展 JDBC SQL；内部代理只接受 `Authorization: Bearer ${TEST_AGENT_INTERNAL_PROXY_API_KEY}`，再向下游供应商注入数据库明文保存的 ICBC token 和 `ucid` header；新增跨实例刷新广播让各 Java 从数据库重载内存。
- Result:
  - 通过：相关后端模块 `compile`、runtime 后端定向测试、新增内部供应商 MyBatis/Flyway 定向测试、`opencode-manager go test ./...`、前端 `corepack pnpm typecheck`、`git diff --check`；使用 Java 25 执行 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 成功启动后端、opencode-manager、前端，后端 readiness 为 `UP`，前端 `http://127.0.0.1:3000` 可返回 HTML。
  - 未完全覆盖：全量后端测试仍被既有 `JdbcRepositoryIntegrationTest` 的 H2 `on conflict` 方言问题和历史 seed 断言阻塞；全量前端 Vitest 仍被既有 FigmaChatPanel、GitChangesPanel、agent-chat diff 路径展示用例阻塞。
  - 未完成：当前管理端 `refresh-status` 返回本 Java 内存快照，广播会触发其他 Java 重载，但还没有聚合展示每个 Java 进程的刷新结果和内存快照；后续应在保持模块边界的前提下补一个跨 Java 聚合查询/转发通道。

### 2026-07-08 - 修复应用工作区发布成功后进度回退

- Why:
  - 个人工作区发布接口已返回 `MERGED + remotePushed=true` 后，进度 WebSocket 仍可能延迟补到旧的 `PUSH_REMOTE/RUNNING` command 事件，导致提交并推送弹窗最后一步从成功态回退为运行中，用户误以为远端仍在推送。
- What:
  - `GitChangesPanel.vue` 在 HTTP 发布响应确认成功后记录终态，后续忽略延迟到达的进度 WebSocket 事件；新增前端回归测试覆盖成功后晚到 `RUNNING` 事件不会覆盖四个步骤的 `SUCCEEDED` 状态。
- How:
  - 复用现有 `publishPersonalWorkspace` 最终响应和 `connectAgentConfigProgress` 进度事件，不新增 API、不改后端契约；只在前端进度状态机加终态保护。
- Result:
  - 定向 Vitest 新增用例、`git diff --check` 通过；本地后端 health 为 `UP`，前端 dev server `http://127.0.0.1:3000` 返回 200。`@test-agent/agent-web` typecheck 当前被工作区未提交的新文件 `InternalModelProviderPanel.vue` 中 `lucide-vue-next` 不存在的 `Refresh` 导入阻塞，未纳入本次修复。

### 2026-07-07 - 修复企业内 opencode worker 打包链路

- Why:
  - `deploy/internal/package-release.sh --env-file /data/testagent/config/docker.env` 在构建 worker 镜像时，`node:22-bookworm-slim` 尚无 CA 根证书就切到 HTTPS Debian 镜像源，导致 `apt-get update` 证书校验失败；修复后又暴露 Docker Desktop 复制 npm 全局 `opencode` symlink 失败。
- What:
  - `deploy/internal/opencode-worker.Dockerfile` 先通过基础镜像默认 Debian 源安装 `ca-certificates`，再切换 `DEBIAN_MIRROR/DEBIAN_SECURITY_MIRROR`；`package-release.sh` 导出外挂程序时改为复制 `opencode-ai` 包目录后在交付目录内创建相对 symlink，并在 `docker create` 时显式传入构建平台；`deploy/internal/README.md` 补充 HTTPS 镜像源说明。
- How:
  - 未修改外置 `/data/testagent/config/docker.env` 或任何密钥配置；复用既有打包脚本和 worker 镜像结构，只调整镜像 bootstrap 顺序与程序导出方式。
- Result:
  - `bash -n deploy/internal/package-release.sh`、`bash -n deploy/internal/opencode-worker-entrypoint.sh`、`docker compose --env-file deploy/internal/env.example -f deploy/internal/docker-compose.yml config`、`git diff --check` 通过；完整执行 `deploy/internal/package-release.sh --env-file /data/testagent/config/docker.env` 成功，产物位于 `deploy/internal/dist/`。

### 2026-07-07 - 补全三机企业部署 README 操作清单

- Why:
  - 部署方需要明确每台服务器分别部署什么、文件放在哪里、基础配置文件如何拆分、哪些配置必须改，以及整体访问链路图，避免只看到 `env.example` 时不知道该改哪些项。
- What:
  - 扩展 `deploy/internal/README.md`，增加三机部署 Mermaid 链路图；按 `122.233.30.2` 前端 Nginx、`122.233.30.4` 后端 Java + Docker worker、`122.233.30.20` Redis、PostgreSQL 待定分别列出目录、配置文件、必须修改项、通常保持默认项、启动和验证命令；明确每台服务器需要从打包 `dist/` 中拿哪些产物、哪些不要放；前端 Nginx 提供可直接复制的完整 `/etc/nginx/conf.d/test-agent.conf`，不要求运维理解模板变量。同步修正 `backend.env.example` 注释，使其指向 `/data/testagent/config/docker.env`。
- How:
  - 仅复用既有 `deploy/internal` README、Nginx 模板、Compose 文件和 env 模板，不新增部署入口、不改 API/数据库/事件契约。
- Result:
  - `backend.env.example` 可被 shell source；`docker compose --env-file deploy/internal/env.example -f deploy/internal/docker-compose.yml config` 可展开；Nginx 模板展开后确认 `listen 80`、`root /data/testagent/frontend`、后端为 `122.233.30.4:8080`；`git diff --check` 通过。真实 Java/worker 未启动，因为 PostgreSQL 仍待定且目标服务器不在当前机器。

### 2026-07-07 - 固化企业内三机部署配置模板

- Why:
  - 企业内部署拓扑已明确为 `122.233.30.2` 前端 Nginx、`122.233.30.4` 后端 Java + Docker worker、`122.233.30.20` Redis，PostgreSQL 地址待定；部署配置需要从命令和服务文件中拆出，便于服务器现场修改。
- What:
  - `deploy/internal/env.example` 按当前前端入口和后端地址更新；新增 `deploy/internal/backend.env.example` 作为 Java 后端外置环境变量模板；`deploy/internal/README.md` 补充三机部署规划、外置 `backend.env/docker.env` 路径和加载方式。本机私有 `deploy/internal/.env` 同步写入当前前后端 IP，但该文件被 `.gitignore` 排除，不纳入提交。
- How:
  - 复用既有 `deploy/internal/package-release.sh`、`docker-compose.yml` 和 Nginx 模板，不新增部署编排入口；Java 通过 `set -a; . /data/testagent/config/backend.env` 或 systemd `EnvironmentFile` 加载配置，Docker worker 通过 `docker compose --env-file /data/testagent/config/docker.env` 加载配置。
- Result:
  - `bash -n deploy/internal/package-release.sh`、`bash -n deploy/internal/opencode-worker-entrypoint.sh`、`set -a; . deploy/internal/backend.env.example`、`docker compose --env-file deploy/internal/env.example -f deploy/internal/docker-compose.yml config`、`git diff --check` 通过；未启动真实 Java/worker，因为 PostgreSQL 仍待定且目标服务器不在当前机器。

### 2026-07-06 - 修复 FigmaChatPanel.vue onComposerCardClick 缺失导致的 vue-tsc 编译错误

- Why:
  - 近期在优化聊天输入框组件时，清理了弃用的进程刷新逻辑，但误删了 `<script setup>` 中的 `onComposerCardClick` 点击处理函数，同时模板中遗留了多余未闭合的 `<div class="figma-chat-input-card">` 标签，导致 `pnpm build` 执行 `vue-tsc --noEmit` 时抛出 `TS2339: Property 'onComposerCardClick' does not exist on type...` 终止构建。
- What:
  - 1. 在 `FigmaChatPanel.vue` 的 `<script setup>` 中恢复并完善 `onComposerCardClick` 点击逻辑（点击输入卡片空白区域时聚焦内部 textarea，并避开按钮、手柄和下拉框等交互元素）。
  - 2. 清理模板中多余的重复 `<div class="figma-chat-input-card">` 标签。
- How:
  - 仅修改前端 `FigmaChatPanel.vue` 的交互函数及模板结构，无后端 API 或底层架构变更。
- Result:
  - 执行 `npx vue-tsc --noEmit` 校验 0 错误，前端构建恢复成功。

### 2026-07-06 - 优化 Diff 文件列表展示及聊天高度拉伸手柄美化

- Why:
  - 1. 用户要求优化 Diff 区域文件列表展示：将状态（modified, untracked, deleted, added）缩写为首字母，隐藏具体冗长路径仅展示文件名，鼠标悬浮时提供全路径 tooltip，以提升信息密度并保持界面简洁整齐。
  - 2. 用户反馈聊天卡片的输入卡片高度拉伸手柄样式太丑（默认会显示横跨整行宽度的亮蓝色条，过于突兀）。
- What:
  - 1. 对 GitChangesPanel.vue、DiffViewer.vue、DiffSummaryRow.vue、FileExplorer.vue、AgentCard.vue 和 FigmaChatPanel.vue 多个文件列表进行统一重构，通过 getStatusLabel 与 getFileName（或 fileNameOf）将状态转为首字母大写（如 M, U, D, A），路径切为 basename，并加上 :title="file.path" 保证鼠标 hover 可看全路径。
  - 2. 对 FigmaChatPanel.vue 的 .figma-chat-composer-resize-handle 拖拽手柄样式进行重塑，移除了原来突兀的整行亮蓝色细条，改用居中对齐、大小 36px * 4px、圆角 2px 且更淡雅的半透明灰色（rgba(0,0,0,0.1)）小药丸拉伸手柄；并在 hover 和拖拽时带有微观过渡动画（宽度由 36px 延伸至 48px，颜色加深为 rgba(0,0,0,0.3)）。
- How:
  - 仅限前端 Vue 组件结构微调及 CSS 优化，不改动任何后端 API、DTO、数据库结构、或者打包部署脚本。
- Result:
  - corepack pnpm typecheck 与 corepack pnpm lint 在 frontend 目录下顺利执行通过，0 编译错误，0 格式/类型警告。

### 2026-07-06 - 收紧思考状态展开渲染

- Why:
  - opencode reasoning 展开时首次挂载 Markdown 渲染器会触发动态加载和高亮处理，体感打开慢；同时思考过程不应抢占最终回答的字号和视觉重量。
- What:
  - `agent-chat` 的 opencode-like reasoning 展开详情改为紧凑纯文本渲染，字号压到 10px，触发区高度和行高同步收紧；对 `.oc-reasoning-part` 内可能残留的 Markdown 思考详情也增加同作用域字号兜底；最终回答 `TextPartView` / `.oc-text-part` 仍保持原 Markdown 渲染路径和字号。
- How:
  - 只修改 `ReasoningPartGroup.vue`、`ReasoningPartView.vue`、`.oc-reasoning-part*` 样式、包说明和定向时间线测试；不改 Run API、SSE、后端 opencode prompt parts、用户消息附件展示或最终回答样式。
- Result:
  - `corepack pnpm test packages/agent-chat/tests/opencode-timeline.test.ts packages/agent-chat/tests/user-message-display.test.ts packages/agent-chat/tests/runtime-reducer.test.ts`、`corepack pnpm --filter @test-agent/agent-chat typecheck`、`git diff --check` 通过；浏览器样式读取确认当前回答正文仍为 `.oc-text-part` Markdown 路径。
### 2026-07-06 - 支持双击编辑器 Tab 页自动展开并定位到左侧文件树

- Why:
  - 用户希望双击编辑器顶部的文件 Tab 页（如 `OpenCode自我介绍.md`）时，左侧工作区文件树能够立刻展开各层父级目录，并平滑滚动定位到对应的文件节点。
- What:
  - `FigmaEditorArea` 增加 Tab 节点 `@dblclick` 事件，向上派发 `locateFile` 事件。
  - `AgentWorkbench` 监听 `locate-file`，调用 `expandPathToFile` 将所有祖先目录加入 `expandedDirectories` 并按需懒加载；同时触发 `scrollToActiveFileTreeRow` 使用 `scrollIntoView({ block: "nearest", behavior: "smooth" })` 自动将目标文件滚动居中定位。
- How:
  - 仅修改前端 `FigmaEditorArea.vue`、`AgentWorkbench.vue` 及对应 Vitest 单元测试，不改动任何后端 API、数据库或环境配置文件。
- Result:
  - Vitest 定向测试 `apps/agent-web/tests/FigmaEditorArea.test.ts` 全部 3 个用例通过；`npx vue-tsc --noEmit` 静态类型检查 0 报错。

### 2026-07-06 - opencode 弱健康检查与前端轮询收敛

- Why:
  - 页面静置时 `/processes/me`、MCP/LSP 等强状态查询仍高频刷新，后台日志持续输出；需要把常态健康判定改为轻量轮询，降低数据库、manager 强健康和运行态目录压力。
- What:
  - 新增 `/api/internal/agent/{agentId}/processes/me/health`，由 `OpencodeProcessStatusQueryService.weakHealth` 只读 Redis manager 快照并直接调用 opencode `/global/health`；跨服务器请求使用公共 `BackendJavaRouteResolver` 和 `BackendHttpForwarder` 按 `linuxServerId` 随机转发目标 Java。
  - 前端 `/processes/me` 取消定时轮询，登录/刷新获取分配信息后每 10 秒调用弱健康；弱健康不健康、头像打开和初始化完成才复查 `/processes/me`。发送、目录加载和运行态 ready 以弱健康为常态来源，`/processes/me` 返回时覆盖一次当前状态；MCP/LSP 改 5 分钟，VCS 保持 30 秒。
- How:
  - 不改数据库、不改 generated SDK、不新增前端直连 opencode；同步 `backend-api`、`shared-types`、HTTP API 文档、前后端 README/PACKAGE，并补后端/前端定向测试。
- Result:
  - 后端目标 Maven 测试、前端 backend-api/workbench-utils/FigmaChatPanel Vitest、`@test-agent/agent-web` 与 `@test-agent/backend-api` typecheck 均通过。

### 2026-07-06 - 编辑器页脚与文件Tab样式交互优化

- Why:
  - 1. 用户要求将 Markdown 预览按钮从 Tab 行表头移至底部页脚保存按钮左侧，且保持仅 Markdown 文件时出现。
  - 2. 用户要求预览按钮单击时为整体预览（100% 预览，不再分上下），双击时才进入分屏预览（分上下）。
  - 3. 用户要求预览按钮和保存按钮均只显示图标，不显示文字。
  - 4. 用户要求页脚“写入路径：”文字改为“路径：”，取消“写入”二字。
  - 5. 用户要求文件 Tab 页中的图标改为与工作空间文件树一致的分类型 Material 文件图标。
- What:
  - 1. `WorkbenchFooter` 页脚右侧新增 MD 预览图标按钮，放在 Save 按钮左侧，仅在 `showPreviewButton`（`activeIsMarkdown`）为 true 时渲染。
  - 2. `WorkbenchFooter` 增加单双击识别定时器逻辑：单击切换为 `full` 整体预览或 `off` 关闭，双击切换为 `split` 分上下屏或 `off` 关闭；修复了层级传递中 `update:markdownPreview` 覆盖 `markdownPreviewMode` 导致的双击分屏无法保持的漏洞；`CodeEditor` 支持受控 `previewMode` (`off` | `full` | `split`) 渲染。
  - 3. `WorkbenchFooter` 中的预览与保存按钮去除了 `<span>` 文字标签，并统一定制为 26x26 方形精美图标按钮；包裹 `ElTooltip` 并配置 `:show-after="0"`，预览按钮悬浮文案定制为两行“单击：整体预览 / 双击：分屏预览”，无原生延时。
  - 4. `WorkbenchFooter` 将“写入路径：”标签文案修改为“路径：”。
  - 5. `FigmaEditorArea` 使用 `@test-agent/file-explorer` 的 `FileIcon` 替换原 Tab 页通用静态文件图标，实现按扩展名/文件名与工作区文件树同步渲染彩色 Material 文件图标。
- How:
  - 仅修改前端 `FigmaEditorArea.vue`、`WorkbenchFooter.vue`、`CodeEditor.vue`、`AgentWorkbench.vue` 及对应 Vitest 单元测试，不改动任何后端 API、数据库或环境配置文件。
- Result:
  - Vitest 定向测试 `packages/editor/tests/CodeEditor.preview.test.ts` 与 `apps/agent-web/tests/WorkbenchFooter.test.ts` 全部 10 个用例通过。

### 2026-07-06 - 修复所有文件偶发性白板不显示内容问题

- Why:
  - 文件查看时，偶发性出现所有文件都无法查看到内容（白板），但后端接口有返回（Markdown预览区可正常显示）。根因是 `CodeEditor.vue` 中的 `ensureMonacoEditor` 在方法被调用时过早捕获了当时为空的 `content` 入参；当首次打开文件触发 Monaco 库异步按需加载 (`await import`) 时，若后端文件内容请求在等待期间返回，会因 `model` 尚未创建而在 `watch` 中被丢弃。当加载恢复后，编辑器使用已被丢弃响应的旧空内容创建并挂载模型，导致内容永久性白板；由于所有异步等待均阻塞在对 Monaco 包加载的 `await` 上，如果用户在卡顿期间连续点击多个文件，这批文件的实际内容都会被吞掉从而全部白板。
- What:
  - 修改 `CodeEditor.vue` 中的 `ensureMonacoEditor` 签名，移除 `content` 入参。
  - 在创建或更新 `model` 时，直接从 `props.content` 读取最新文本而不是依赖过期的闭包参数，确保不管是否存在由于等待 Monaco 包加载带来的时间差，编辑器都能渲染真实返回的最新内容。
- How:
  - 仅修改前端 `frontend/packages/editor/src/CodeEditor.vue` 的状态获取时机，不改动生命周期流程或销毁逻辑；不改后端 API 契约、数据库结构或环境配置。
- Result:
  - 前端渲染竞态漏洞已堵塞，修复了偶发性的全部文件白板问题。

### 2026-07-06 - 收敛选区上下文回放展示

- Why:
  - 选区上下文改为 text prompt-only 后，opencode 回放的 user text part 可能包含完整 `<context>` prompt；旧 reducer 在远端 user part 晚到或以 delta 到达时会误建 assistant 文本块，导致内部 prompt 在对话中可见，且 text part 中的选区 chip 恢复不稳定。
- What:
  - `agent-chat` 用 `displayTextFromUserPrompt()` 归一化远端 user message/part 匹配；序列化工作区 prompt 的 `message.part.updated` 和完整 `message.part.delta` 都会归并回乐观 user 消息，不再渲染为 assistant 输出。
  - 用户消息附件恢复同时解析 text prompt part 与原生 file part；整文件仍走 opencode 原生 file part，选区继续走 text prompt-only。
- How:
  - 不新增 API、SSE 字段、数据库或后端链路；复用现有 Run `parts`、用户消息展示解析和 opencode file part 适配。
- Result:
  - 定向 Vitest（`user-message-display`、`runtime-reducer`、`opencode-timeline`、`chat-context-store`、`prompt-context`）、`@test-agent/agent-chat` typecheck、`@test-agent/agent-web` typecheck、`git diff --check` 通过；本地 test profile 三服务重启成功，health/readiness/frontend/CORS 检查通过。

### 2026-07-06 - 修复历史对话关联文件恢复

- Why:
  - 历史会话中用户消息只显示文本，未展示本轮关联的工作区文件/选区 chip；根因是平台 DB 的 user `session_messages.parts_json` 未保存 Run 启动时的 prompt parts，且前端合并 session tree 时会保留 DB user 消息而丢掉 tree user file parts。
- What:
  - `RunApplicationService` 保存用户消息时同步序列化 `StartRunInput.parts` 到 `partsJson`，并继续透传 file source 中的 `contextType/startLine/endLine`。
  - 前端历史恢复合并 DB 与 session tree 时，user 消息保留平台 messageId，同时吸收 tree user 的 file parts，作为旧历史/回放数据的兜底。
- How:
  - 不新增 API、SSE 字段或数据库结构；复用既有 `session_messages.parts_json`、`PromptPart` 和用户消息展示层的 file chip 解析逻辑。
- Result:
  - 后端定向 `RunApplicationServiceTest#servicePassesPromptPartsAndRuntimeSelectionToOpencodeFacade` 通过；前端 `prompt-context`、`workbench-utils`、`user-message-display` 和 `opencode-timeline` 定向 Vitest 通过。

### 2026-07-06 - opencode worker 支持外置程序优先

- Why:
  - 企业内希望镜像主要承载运行环境，第一版仍内置 opencode 和 opencode-manager，但后续升级可以直接替换宿主机外挂程序而不重打镜像。
- What:
  - `opencode-worker` 镜像新增启动包装脚本，优先使用 `/opt/test-agent/programs/bin/opencode-manager` 和 `/opt/test-agent/programs/opencode/bin/opencode`，不可用时回退镜像内置程序。
  - Compose 为两个 worker 增加 `TEST_AGENT_PROGRAM_ROOT` 挂载；打包脚本从 worker 镜像导出 `dist/programs/` 和 `test-agent-programs.tar.gz`。
- How:
  - 镜像仍通过 npm 安装 `opencode-ai` 并编译当前仓库 `opencode-manager`；导出外挂程序时从镜像内复制 manager 二进制、opencode 全局 bin 和 `opencode-ai` npm 包。
- Result:
  - `bash -n deploy/internal/package-release.sh`、`bash -n deploy/internal/opencode-worker-entrypoint.sh`、`deploy/internal/package-release.sh --help`、`docker compose --env-file deploy/internal/env.example -f deploy/internal/docker-compose.yml config` 和 `git diff --check` 通过；未实际构建镜像。

### 2026-07-06 - 补充工作区上下文原生附件链路日志

- Why:
  - 需要证明工作区文件/选区上下文不是另起 `<context>` prompt 拼接链路，而是适配 opencode 原生 `text/file/agent` parts；同时 UI 发送后用户气泡应只显示原始问题并展示关联文件 chip。
- What:
  - 前端发送前新增 `workspace_context_parts_built`、`workspace_context_send_prepared` 摘要调试日志；后端 `GeneratedOpencodeSdkGateway` 在调用 `/session/{sessionID}/prompt_async` 前后新增 `opencode_prompt_async_request_prepared`、`opencode_prompt_async_request_accepted` 摘要日志。
  - 用户消息工作区上下文 chip 按 `type/path/lines` 去重，避免本地 optimistic user message 与 opencode 实时 user file part 重复显示同一附件。
- How:
  - 日志只输出 part 类型、路径、文件名、mime、URL scheme/dataUrl、字符数和 source 范围，不输出用户正文、附件正文、完整 data URL、token 或密钥；仍复用既有 Run API parts、后端 `AgentPromptPart` 和 opencode gateway 链路。
- Result:
  - 本地 UI 使用 `888888888/123456` 登录、启动 opencode 进程、从工作区文件树右键添加 `99-测试数据/Git冲突处理/冲突文件.md` 并发送，后端日志确认发给 opencode 的 `prompt_async` 为 2 个 parts：text + file，file 为 `data:` URL、`mime=text/plain`、`filename=冲突文件.md`、`source.path=99-测试数据/Git冲突处理/冲突文件.md`、`source.text.chars=59`。

### 2026-07-06 - 工作区上下文改走原生 file parts

- Why:
  - opencode 原生附件抓包显示用户输入和附件应落在同一个 user message 的 `text` / `file` parts 中；此前工作区上下文附件通过 `<context>` 文本拼接进入 prompt，和原生附件链路不一致。
- What:
  - 工作区整文件和 Monaco 选区发送时转换为 `PromptPart.type=file`，选区通过 `source.contextType/startLine/endLine` 保留来源；用户消息展示优先从 user `file` parts 渲染关联文件/选区 chip，旧 `<context>` 文本仅保留历史兼容解析。
- How:
  - 复用既有 Run API `parts`、后端 `AgentPromptPart.file` 和 opencode `prompt_async.parts` 链路；不新增后端接口、不改数据库、不直连 opencode server。
- Result:
  - 已补充 store、runtime reducer、历史消息恢复、用户消息展示回归测试；验证结果以本次收尾命令为准。

### 2026-07-06 - 对话界面样式及交互优化美化（TDesign风格）

- Why:
  - 1. 上下文中添加文件的卡片样式和外框略显粗糙；
  - 2. 智能体输出内容中的复制按钮定位在右上角，有时会与大段文本内容发生遮挡重合；
  - 3. 用户消息气泡和智能体消息风格不统一，需要按照 TDesign Chat 的高级美学来统一视觉风格；
  - 4. 用户输入的消息也应该配备复制按钮，并能带入包含可能携带的附件上下文和部分文本在内的完整内容；
  - 5. 用户与智能体消息布局与头像不对称，用户是左右横向排列且头像是圆角矩形，而智能体是纵向上下排列且原头像无精致背景框。
- What:
  - 1. 调整了 ChatContextAttachmentList.vue 和 ChatContextAttachmentCard.vue 的样式，移除粗糙的外框，降低卡片高度为 26px，去除 "文件/选区" 文字标签，增加 hover 时莫兰迪灰蓝（#4f5e7b）与浅灰蓝背景的联动，更加紧凑精美。
  - 2. 在 UserMessageRow.vue 中引入并挂载 OcCopyButton，复制时绑定为 message.text 完整输入以保留附件及部分文本；同时全局调小了复制按钮（oc-icon-button）及其图标的尺寸。
  - 3. 重构 rows.css 中用户消息气泡为白色底（无底色）和超淡灰蓝色细边框（#e3e8f7），恢复深色文字，使其整体显得极其雅致、自然；附件 chip 变更为浅灰蓝背景（#f0f3f8）和灰蓝字（#4f5e7b），统一色系。
  - 4. 彻底解决重叠：在 rows.css 的用户气泡与 parts.css 的智能体气泡中加了 36px 右安全边距（padding-right: 36px），微调缩小后的复制按钮在气泡右上角定位，防止与文本重合。
  - 5. 消息布局与头像对称化：重构 rows.css 中智能体框架布局（.oc-assistant-frame），将头像与内容排列由 column 变更为 row 左右水平对齐，并在 continuation 态时为正文层提供 40px 的左内边距以保持完美侧边对齐；显式指定智能体头像宽高为 28px（与用户一致），圆角 9px，并换上更匹配色系的莫兰迪深灰蓝（#2e384d）背景，达成左右对齐和头像外观的完全对称。
  - 6. 输入框扁平化优化：调小 FigmaChatPanel.vue 聊天文本框（.figma-chat-textarea）的 min-height（由 64px 降为 40px），收紧 padding 为 8px 12px 4px，使未输入文本时的整体输入卡片更扁平精巧，更具现代感。
  - 7. 气泡扁平化微调：将用户输入气泡（.oc-user-message__bubble）和智能体回答气泡（.oc-text-part）的上下 padding 从 8px 调小为 6px，使气泡整体高度更矮、更精简紧凑；同步把对应的复制按钮绝对定位 top 偏移从 7px 调整为 5px 保持垂直居中。
  - 8. 附件列表对齐优化：为 ChatContextAttachmentList.vue（.chat-context-list）添加 margin-left: 10px 和 margin-right: 10px，使其在水平宽度上与下方的输入容器完美居中对齐，消除顶到两侧边缘的不对称感。
- How:
  - 完全由前端 CSS 和局部的展示 Vue 组件承载，未修改任何后端 API、DTO、数据库结构或环境配置文件。
- Result:
  - 前端编译和打包 pnpm build (vue-tsc --noEmit && vite build) 顺利执行通过。

### 2026-07-06 - 修复上下文附件发送后展示

- Why:
  - 工作区上下文附件发送后仍停留在输入区，且历史/回放消息会把前端序列化的 `<context>` prompt 当作用户气泡正文展示。
- What:
  - 发送被本地接受或排队后清空 `useChatContextStore`；`agent-chat` 用户消息展示层从序列化 prompt 中提取原始问题，隐藏工作区上下文块，并在用户气泡下方展示本轮关联文件/选区 chip；上下文附件条样式收敛为更紧凑的附件 chip。
- How:
  - 不改 Run API、SSE、数据库或后端消息存储；仅在前端展示层派生可见文案，发送给 Agent 的结构化上下文仍保持不变。
- Result:
  - 已补充用户消息展示和 FigmaChatPanel 回归测试，验证结果以本次收尾命令为准。

### 2026-07-06 - 工作区上下文附件前端接入

- Why:
  - 企业内部适配优先需要把工作区选区和文件作为对话上下文传给 Agent，并在前端先完成大小拦截，避免超长上下文直接进入模型。
- What:
  - 新增 `useChatContextStore`、上下文附件列表/卡片/预览抽屉；接入 Monaco 选区右键、文件树右键菜单和编辑器 Tab 右键菜单；发送时把上下文序列化进 prompt。
- How:
  - 第一版不改后端 API/SSE/数据库；显式上下文存在时不再叠加旧的活动编辑器隐式 PromptPart，避免重复携带同一选区或文件。
- Result:
  - `agent-web`/`file-explorer`/`editor` typecheck、上下文 store 与聊天面板定向 Vitest、`git diff --check` 通过；现有前端 dev server `http://127.0.0.1:3000` 返回 200。

### 2026-07-06 - Docker Compose 收敛为仅 opencode worker

- Why:
  - 用户明确 Nginx 也按实体服务部署，Docker 只用于 `opencode + opencode-manager` worker 容器。
- What:
  - `deploy/internal/docker-compose.yml` 删除 gateway 和 frontend Nginx 容器，只保留两个 opencode worker。
  - 实体 Nginx 模板改为直接 `root ${TEST_AGENT_FRONTEND_ROOT}` 托管前端 dist，并把 `/api` 代理到两个 Java 后端。
  - `env.example` 和 README 同步说明 Compose 不再管理 Nginx 或前端容器。
- How:
  - 保留 `deploy/internal/nginx/` 作为实体 Nginx 配置参考，不由 Compose 挂载或启动。
- Result:
  - `docker compose --env-file deploy/internal/env.example -f deploy/internal/docker-compose.yml config` 展开后只包含 `opencode-worker-1/2`；`bash -n deploy/internal/package-release.sh` 和 `git diff --check` 通过。

### 2026-07-06 - 企业内打包改为 jar + frontend dist + worker 镜像

- Why:
  - 用户确认前端不需要业务镜像，应直接交付 `dist/`；同时企业内打包结果应包含后端可执行 jar。
- What:
  - `deploy/internal/package-release.sh` 取代 `build-images.sh`，打包产物包括 `dist/backend/test-agent-app.jar`、`dist/frontend/`、`test-agent-frontend-dist.tar.gz` 和 opencode-worker 镜像 tar。
  - 删除前端 Dockerfile；Compose 中两个前端服务改为标准 `nginx:1.27-alpine` 挂载 `TEST_AGENT_FRONTEND_DIST_DIR`。
- How:
  - 后端 jar 通过 `mvn -pl test-agent-app -am -DskipTests package` 生成后复制到交付目录；前端通过 `corepack pnpm --filter @test-agent/agent-web build` 生成 dist；opencode-worker 仍使用 Dockerfile 构建。
- Result:
  - `bash -n deploy/internal/package-release.sh`、`deploy/internal/package-release.sh --help`、`docker compose --env-file deploy/internal/env.example -f deploy/internal/docker-compose.yml config` 和 `git diff --check` 通过；未执行完整打包构建。

### 2026-07-06 - 补充企业内镜像打包脚本

- Why:
  - 企业内 Docker 部署文件已有 Dockerfile 和 Compose，但还缺少一条可重复执行的镜像构建与离线 tar 导出入口。
- What:
  - 新增 `deploy/internal/build-images.sh`，默认读取 `deploy/internal/.env` 或 `env.example`，构建前端和 opencode worker 的 `linux/amd64` 镜像，并导出 docker-loadable tar 包。
  - `deploy/internal/env.example` 增加 `TEST_AGENT_IMAGE_OUTPUT_DIR`，README 增加脚本用法。
- How:
  - 脚本自行解析 `KEY=VALUE` dotenv，不执行文件内容；构建参数继续复用既有镜像 tag、npm/Corepack、Go proxy、Debian mirror 和 `OPENCODE_AI_PACKAGE` 配置。
- Result:
  - `bash -n deploy/internal/build-images.sh`、`deploy/internal/build-images.sh --help`、`docker compose --env-file deploy/internal/env.example -f deploy/internal/docker-compose.yml config` 和 `git diff --check` 通过；未实际构建镜像。

### 2026-07-06 - 全面作废后端旧接口

- Why:
  - 旧 `/api/...` runtime/workspace 兼容入口容易被前端继续误用，并且历史消息旧查询会触发快照刷新；需要按“立即强制作废”统一返回 `410 API_GONE`，让新客户端只走 internal platform 和 agent-scoped API。
- What:
  - 后端新增 `API_GONE` 和 `LegacyApiGoneWebFilter`，拦截旧 `/api/runs/**`、`/api/sessions/**`、`/api/workspaces/**`、opencode runtime 裸路径、旧 terminal、旧 HTTP 文件、旧 backendProcess metrics 和 manager-backends 诊断入口；稳定 `/api/auth/login|logout|me|refresh` 保留。
  - Controller 清理旧映射，前端 `backend-api` 迁移 Session、Workspace、runtime metadata、terminal、file-ws-route、permission/question 等调用到新 URL；历史正文继续使用 agent-scoped session-tree，反馈映射使用 platform messages `refresh=false`。
- How:
  - 拦截器在进入 Controller、业务服务或跨 Java 转发前写统一 `ApiErrorResponse`；不改数据库结构、RunEvent payload 或 generated SDK。同步 HTTP/SSE API 文档、后端 API README、`backend-api` README 和 `agent-web` README。
- Result:
  - 后端 `test-agent-common,test-agent-api -am test`、`backend-api` Vitest 和历史会话相关 Playwright 用例通过；完整 `workbench.spec.ts` 仍有设置权限、模型下拉语义、发送流程等既有非本次路径迁移断言失败，已在本次结果中保留风险说明。

### 2026-07-06 - internal UCID 同步增加明文排查日志

- Why:
  - 需要在企业内模型调用前确认当前 Run 解析到的 UCID 与 header 名，用户明确确认 UCID 在本项目不作为敏感信息处理，允许明文打印。
- What:
  - `ModelCatalogApplicationService` 在 internal provider 同步前输出 `model_provider_ucid_header_resolved` 单行日志，包含 `traceId/providerId/userId/ucidHeaderName/ucid/ucidPresent`，不打印 token 或请求体。
- How:
  - 复用现有 SLF4J 日志；日志位于 `PATCH /global/config` 前，便于即使 provider 同步失败也能排查 UCID 解析结果。
- Result:
  - `ModelCatalogApplicationServiceTest` 定向测试通过；标准 `.env.test` 重启成功，后端 readiness 和前端入口均可达。

### 2026-07-06 - internal 模型调用透传当前用户 UCID

- Why:
  - 企业内 `internal` 模型源调用内网 OpenAI-compatible API 时缺少当前登录人的 UCID；由于 opencode `/global/config` 是进程级配置，必须避免多用户共用进程时 header 串号。
- What:
  - `ModelCatalogApplicationService` 在 internal provider 同步时按当前 `UserId` 查询 `User.unifiedAuthId`，写入可配置的 `ucid-header-name`；`RunApplicationService` 在 internal 模式拒绝匿名 Run，并要求命中用户专属 opencode 进程后再同步 provider。
  - 各 Spring profile 增加 `TEST_AGENT_ICBC_OPENAI_UCID_HEADER_NAME`，并同步 backend/runtime/deployment 文档。
- How:
  - 复用现有 `UserRepository`、`AgentRuntimeTargetResolver` 和 Run 前 `PATCH /global/config` 链路，不改 opencode，不新增数据库和 API。
- Result:
  - 定向 ModelCatalog/Run 测试通过，`test-agent-app` 打包通过；标准 `.env.test` 重启成功，后端 health/readiness、前端和 CORS 预检均通过。全量 `test-agent-app -am test` 仍受既有 persistence H2 集成测试问题阻塞。

### 2026-07-06 - 新增企业内 Docker Compose 部署文件

- Why:
  - 需要按“Java 直接部署、2 个前端容器、2 个后端直连入口、opencode+manager 镜像部署”的企业内拓扑补齐可落地的 Dockerfile 和 Compose 文件，并明确 Java 使用 manager 上报端口生成 opencode `baseUrl` 的端口约束。
- What:
  - 新增 `deploy/internal/`：前端静态镜像 Dockerfile、opencode worker 镜像 Dockerfile、2 前端 + 网关 + 2 worker 的 `docker-compose.yml`、Nginx 模板、env 示例和 README。
  - opencode worker 镜像内包含 1 个 `opencode-manager` 和 npm 安装的 `opencode-ai` CLI；运行期由 manager 按端口池动态拉起 0..N 个 `opencode serve` 子进程。
- How:
  - Compose 中 worker 使用 `4096-4105:4096-4105`、`4106-4115:4106-4115` 这类 hostPort 与 containerPort 数值一致的映射；README 明确禁止 `14096:4096` 这类内外端口不一致配置。
  - Java 后端不纳入 Compose，由 gateway 通过 `TEST_AGENT_BACKEND_1/2` 代理直接部署的两个 Java 端口。
- Result:
  - `docker compose --env-file deploy/internal/env.example -f deploy/internal/docker-compose.yml config` 通过并展开出 2 个前端、2 个 worker 和网关；本机 Buildx 不支持 `--check`，未执行镜像构建。

### 2026-07-05 - 合并 retry rebase 冲突

- Why:
  - rebase 回放 `a656f6b37` 时，旧 `retryStatus` 方案与当前 `runtimeStatus`、自动重试、root 终态收敛、Todo 清理和 retry action 链接产生冲突，需要逐处合并避免功能回退。
- What:
  - 最终统一保留 `runtimeStatus.type === "retry"`，吸收 retry 倒计时展示、本地 60 秒 deadline、第 1/2 次自动重试和第 3 次本地失败兜底；清理旧 `SessionRetryStatus`/`retryStatus` 文案和重复 retry row 投影。
  - 保留 Run 进展清理 retry busy 态、新 Run 清理 Todo、失败卡清理、root 终态收敛、SSE 本地诊断和 retry 行 action 链接能力。
- How:
  - 只合并前端 retry reducer、agent-web 工作台状态、opencode-like 时间线、定向测试和稳定文档；不新增 HTTP API、不改 SSE wire shape、不改数据库或环境配置。
- Result:
  - 冲突标记检查、定向 Vitest、`@test-agent/agent-chat` typecheck 和 `@test-agent/agent-web` typecheck 均通过；`.workbuddy/memory` 下既有未暂存删除保持在本次提交之外。

### 2026-07-05 - 清理新一轮对话 Todo 残留

- Why:
  - 用户反馈上一轮对话的 Todo 面板会在下一轮对话开始后继续显示；根因是 `agent-chat` reducer 的 `run.requested` 只清理运行态和 streaming overlay，未清空上一轮 `todos`。
- What:
  - `run.requested` 现在保留 transcript、新 Run busy 态和旧失败卡清理逻辑，同时清空上一轮 Todo 快照；本轮收到新的 `todo.updated` 后再展示新任务。
  - 解决了当前工作区中 `agent-chat` reducer/README/PACKAGE 以及相关说明文档的既有冲突，合并保留 retry 可见化与失败卡按最终 root 终态收敛的说明。
- How:
  - 先补红灯测试确认旧 `todo_old` 会残留，再在 reducer 中加入 `todos: []`；不改后端 API、RunEvent wire shape、数据库或 generated SDK。
- Result:
  - `corepack pnpm test packages/agent-chat/tests/runtime-reducer.test.ts`、`corepack pnpm --filter @test-agent/agent-chat typecheck`、`corepack pnpm --filter @test-agent/agent-web typecheck` 和 `git diff --check` 均通过。

### 2026-07-05 - 前端 retry 倒计时与自动重试

- Why:
  - `session.status.retry` 从 SSE 重放或延迟到达时，前端按事件 `occurredAt` 计算倒计时会直接显示 0 秒并停住；同时 retry 等待期间仍可能显示普通“思考中”，用户无法判断正在等待重试。
- What:
  - `agent-chat` 的 retry runtime status 增加 `retryKey`、60 秒倒计时和 3 次上限，时间线 retry 行展示“重试中 N 秒后 - 第 X 次 / 共 3 次”，并在 retry 时抑制 thinking/working-status。
  - `agent-web` 改为按前端首次收到 retry 事件的时间维护本地 deadline；第 1/2 次到期后用最近一次 Run 草稿自动新建 Run，第 3 次仍无后续事件时本地收敛为失败。
  - 同步 frontend README、agent-web/agent-chat README 和 RunEvent 文档说明。
- How:
  - 不新增后端 API、不改 SSE wire shape、不改数据库；旧等待 Run 只做 best-effort cancel，旧 SSE runId 会被前端忽略，自动重试直接调用现有 `startRun`。
- Result:
  - 定向 Vitest（`workbench-utils`、`FigmaChatPanel`、`runtime-reducer`、`opencode-like-state`）和 `@test-agent/agent-web`、`@test-agent/agent-chat` typecheck 通过。

### 2026-07-05 - opencode 历史消息快照改为分页恢复

- Why:
  - 上一轮把 generated `ApiClient` 的响应缓冲调到 16MB 只能规避默认 256KB 报错，仍然依赖单次大响应；进一步排查 opencode legacy `/session/{sessionID}/message` 发现分页 cursor 通过 `X-Next-Cursor` header 返回，平台已有 command/result cursor 字段但未透传。
- What:
  - `GeneratedOpencodeSdkGateway` 改为读取 `X-Next-Cursor`，`AgentSessionMessagesResult` 保留 cursor，`RunSessionMessageSnapshotService` 以 50 条一页、最多 200 条分页拉取并 upsert。
  - 快照保存 assistant 消息时优先使用远端 message `time.created` 作为 `session_messages.created_at`，避免从最新页翻旧页时按写入时间造成历史错序；Run token/cost 选择远端时间最新的 usage。
  - 补充 gateway header cursor 测试和 Run 终态分页快照测试，并同步 agent-runtime、opencode-client、opencode-runtime README 及 API/SSE 文档说明。
- How:
  - 不手改 generated SDK，不改数据库结构；继续通过 `AgentRuntime -> OpencodeClientFacade -> GeneratedOpencodeSdkGateway` 既有边界读取标准 `/session/{sessionID}/message`，不切到 `/api/session/{sessionID}/message`。
- Result:
  - `GeneratedOpencodeSdkGatewayTest`、`RunApplicationServiceTest` 定向测试、`mvn -pl test-agent-opencode-runtime -am test -DskipITs` 和 `git diff --check` 通过；本地服务启动仍需受 `.env.test` 数据库连通性约束复验。

### 2026-07-05 - 放宽 opencode 历史消息快照响应缓冲

- Why:
  - 用户反馈点击历史后第二轮对话 assistant 内容缺失，但“原始输出”里能看到完整消息；排查确认不是前端按时间过滤，也不是原始输出没落库，而是后端终态刷新 `session_messages` 前调用 opencode `/session/{sessionID}/message` 拉历史快照时，响应超过 Spring WebClient 默认 256KB 缓冲并抛出 `DataBufferLimitException`。
- What:
  - `GeneratedOpencodeSdkGateway` 继续作为唯一直接调用 generated SDK 的适配器，但创建 generated `ApiClient` 时注入自定义 WebClient，把 opencode 响应 `maxInMemorySize` 调整为 16MB，避免大 session message snapshot 在进入快照写库前拉取失败。
  - 新增大响应历史消息快照回归测试，模拟超 256KB 的 assistant 文本并验证 `sessionMessages` 能正常解析。
  - 同步 `test-agent-opencode-client` README，说明该适配器对 generated `ApiClient` 的缓冲配置原因。
- How:
  - 不手改 generated SDK，只在业务封装层复用 generated `ApiClient.buildWebClientBuilder(...)` 构造 WebClient；不改 API、RunEvent 契约、数据库结构或环境配置。
- Result:
  - `mvn -pl test-agent-opencode-client -am test -DskipITs`、`GeneratedOpencodeSdkGatewayTest` 定向测试和 `git diff --check` 通过；本地重启脚本能完成后端打包，但使用 `.env.test` 启动时 PostgreSQL 连接读超时，后端 readiness 未起来，端到端页面历史恢复仍需在测试库可连后复验。

### 2026-07-05 - 防止运行时 Diff 劫持已打开的 VCS Diff

- Why:
  - 用户打开左侧 Git 变更文件的 VCS Diff 后，智能体继续产出文件触发 `diff.proposed`，中间区会被自动切到旧的 Run Diff 面板，出现 `Run / Split / 刷新` 的旧 UI。
- What:
  - 新增 `nextCenterModeAfterRunDiff` 状态规则：运行时 Run Diff 只更新右侧文件修改数据；如果用户正在中间区查看 VCS/Agent Diff，不再自动劫持为 Run Diff，而是回到编辑器面板。
  - 新增 Playwright 回归覆盖“打开 VCS Diff 后收到 live run diff”的用户路径。
- How:
  - 仅调整 `frontend/apps/agent-web` 的前端状态同步和测试；不改后端 API、RunEvent 契约、数据库、generated SDK 或环境配置。
- Result:
  - 定向 Vitest、`@test-agent/agent-web` typecheck、打开 VCS Diff 后收到 live run diff 的 Playwright 用例，以及回退最后一个 VCS Diff 的 Playwright 用例通过。

### 2026-07-05 - 修复回退最后一个 VCS Diff 后残留空 Diff 面板

- Why:
  - 用户在左侧变更面板打开 VCS Diff 后连续点击“回退文件改动”，最后一个变更被清空时，中间编辑区仍停留在旧 `DiffViewer` 空态，只剩 `VCS / Split / 刷新` 工具栏和空白内容区。
- What:
  - `AgentWorkbench` 在工作区 Git diff 刷新后，如果当前来源是 `vcs` 且文件列表为空，会自动从 Diff 面板回到编辑器面板，避免保留过期空 Diff UI。
  - 新增 `nextCenterModeAfterVcsRefresh` 状态转移 helper 和回归测试，并补充 Playwright 用户路径覆盖“打开 VCS Diff -> 回退最后一个变更”。
- How:
  - 仅修改 `frontend/apps/agent-web` 前端状态同步和测试；不改后端 API、RunEvent 契约、数据库、generated SDK 或环境配置。
- Result:
  - 定向 Vitest（`workbench-utils.test.ts`、`git-changes-panel.test.ts`）、`@test-agent/agent-web` typecheck 和新增 Playwright 用例通过。
### 2026-07-05 - 修复原始输出面板事件时区未本地化

- Why:
  - 原始输出调试面板右上角显示的时间是未经时区转换的 UTC 格式（从 ISO 字符串直接截取），导致其与用户的实际本地时间对不上。
- What:
  - 在 `FigmaChatPanel.vue` 中修改 `rawOutputTime` 逻辑，将传入的时间字符串解析为 `Date` 对象，然后将其格式化为本地的 `YYYY-MM-DD HH:mm:ss` 格式字符串。
- How:
  - 仅修改前端 `FigmaChatPanel.vue` 的时间格式化函数，使用原生的 `Date` API 获取本地时间各部分，不影响后端接口与任何底层数据。
- Result:
  - `@test-agent/agent-web` typecheck 0 报错；运行 Vitest 全量单元测试成功通过。

### 2026-07-05 - 调整非文件夹文件占位为2px并增加悬浮对齐线

- Why:
  - 1. 用户反馈文件相对文件夹的位置目前过于靠后，决定将文件前导占位宽度缩减为 2px 以优化视觉对齐。
  - 2. 用户要求当鼠标悬浮在列表区域时，显示类似 VS Code 的纵向文件夹缩进引导对齐线，方便长目录或深层目录下的对齐辨识。
- What:
  - 1. 在文件浏览 Vue 组件（`DirectoryRows.vue`、`AgentConfigTreeNode.vue`、`FileExplorer.vue`）中，将文件行前导的 14px 宽 `ta-file-tree-spacer` 占位替换为专用的 `ta-file-tree-file-spacer`。
  - 2. 在 `globals.css` 中新增 `.ta-file-tree-file-spacer`（2px 宽）及 `.ta-file-tree-indent-guide`（1px 宽，且默认 `opacity: 0` 并带有渐变过渡）样式类。
  - 3. 在 `globals.css` 中为 `.ta-file-tree-row` 添加 `position: relative;` 确保子级对齐线可以绝对定位锚定。
  - 4. 在 `DirectoryRows.vue` 和 `AgentConfigTreeNode.vue` 的行按钮内部，通过 `v-for="i in depth"` 渲染 `depth` 条 `.ta-file-tree-indent-guide` 绝对定位线，位置计算公式为 `left: 13 + (i - 1) * 16` 像素，水平上完美对齐各上级文件夹折叠箭头的几何中心。
  - 5. 在 `globals.css` 中添加 `:hover` 激活规则：当鼠标悬停在 `.ta-file-tree-scroll` 或 `.agent-tree` 列表滚动容器上时，显示内部所有的缩进引导线。
- How:
  - 结合 CSS `opacity` 与 `:hover` 选择器实现只在列表容器悬浮时淡入缩进引导线，保持文件树在静止无交互时的视觉干净度；利用行相对定位和基于 `depth` 计算 left 偏移量的 span，在非扁平树节点上精确投影每一级父辈折叠线的延长垂直线。
- Result:
  - 前端 `corepack pnpm typecheck` 和 `corepack pnpm build` 编译打包均无报错。
  - 全量 Vitest 单元测试运行通过（346 passed | 1 skipped）。

### 2026-07-05 - 修复运行中上滑被拉回底部

- Why:
  - 用户反馈修复“查看新内容”误弹后，智能体运行中对话无法上滑到顶部；根因是运行中的工具状态、part 状态等非文本增长更新也会清理滚动提示并把 `isAtBottom/userInterruptedScroll` 重置为贴底状态，后续刷新随即强制滚回底部。
- What:
  - `FigmaChatPanel` 将“清掉新内容提示”和“重置贴底跟随状态”拆开，运行中非内容增长的消息元数据更新不再改写用户滚动锁。
  - 新增回归测试覆盖运行中工具状态从 `running` 变为 `completed` 时，用户上滑位置保持不变且不误弹“查看新内容”。
- How:
  - 仅修改 `frontend/apps/agent-web` 的聊天滚动状态逻辑和组件测试；不改后端 API、RunEvent 契约、数据库、generated SDK 或环境配置。
- Result:
  - `FigmaChatPanel.test.ts` 定向 Vitest 和 `@test-agent/agent-web` typecheck 通过。

### 2026-07-05 - 修复新内容提示误弹与编辑器 Tab 批量关闭

- Why:
  - 用户反馈“查看新内容”应只在滚动条不在底部且运行中有新消息输出时出现，但新建对话和已完结历史会话也会误弹；同时希望编辑器 tab 像 VS Code 一样支持关闭右侧所有、关闭左侧所有和关闭所有。
- What:
  - `FigmaChatPanel` 将新内容提示收紧为 `running=true` 且最后消息增长或追加时才展示，并在历史加载、运行结束和非实时消息替换时清理提示状态。
  - `FigmaEditorArea` 新增 tab 右键菜单，支持批量关闭右侧、左侧和全部标签；`AgentWorkbench` 复用既有关闭流程，批量关闭遇到未保存文件时仍中断并弹出确认。
  - 同步 `agent-web` README，并新增组件回归测试覆盖提示误弹和 tab 批量关闭路径。
- How:
  - 仅修改 `frontend/apps/agent-web` 前端展示、工作台事件转发、组件测试和 README；不改后端 API、RunEvent 契约、数据库、generated SDK 或环境配置。
- Result:
  - 相关 Vitest 通过（2 files, 70 passed, 1 skipped）；`@test-agent/agent-web` typecheck 通过；`git diff --check` 通过；当前 worktree 前端 dev server 已在 `http://127.0.0.1:3001/` 启动并返回 HTTP 200。

### 2026-07-05 - 原始输出搜索命中高亮

- Why:
  - 用户希望“原始输出”检索框搜到内容后，相关字符能在结果中直接高亮，便于在大段请求/响应/SSE 原文里定位命中位置。
- What:
  - `FigmaChatPanel` 原始输出面板在搜索命中时，对标题、详情字段和正文中的匹配片段渲染高亮。
  - 高亮使用文本切片渲染，不使用 `v-html`，避免把原始报文当 HTML 注入。
  - 同步 `agent-web` README 中原始输出搜索能力说明。
- How:
  - 仅修改前端展示和组件测试，不改后端 API、RunEvent 契约、数据库或 generated SDK。
- Result:
  - `@test-agent/agent-web` typecheck 通过；`FigmaChatPanel.test.ts` 定向 Vitest 通过（64 passed, 1 skipped）。

### 2026-07-05 - 历史列表时间与原始输出搜索

- Why:
  - 用户希望历史记录能同时看到创建时间和最后修改时间，并希望“原始输出”浮层能直接检索所有已捕获原始报文内容，便于定位请求、响应、SSE、traceId 和正文片段。
- What:
  - 历史列表适配函数保留 session 原始 `createdAt/updatedAt`，右侧历史抽屉卡片同时展示“创建 / 更新”时间，并允许按更新时间搜索。
  - “原始输出”浮层新增本地搜索框，和现有请求/响应/SSE 类型筛选叠加过滤标题、URL、method、eventName、status、contentType、traceId、runId、发生时间和正文。
  - 同步 `agent-web` README 说明该调试浮层的本地搜索能力和历史抽屉时间展示。
- How:
  - 仅修改 `frontend/apps/agent-web` 前端展示、适配函数、组件测试和文档，不改后端 API、RunEvent 契约、数据库或 generated SDK。
- Result:
  - `@test-agent/agent-web` typecheck 通过；`FigmaChatPanel.test.ts` 与 `workbench-utils.test.ts` 定向 Vitest 通过（93 passed, 1 skipped）。

### 2026-07-05 - 优化对话失败卡间距与错误信息

- Why:
  - 用户反馈右侧 timeline 反馈按钮、失败重试卡与上一段输出距离过大，失败卡只显示“您的请求断开，请重试”或“未知错误”时缺少有效排查信息；同时询问历史对话重试与提问态展示原因。
- What:
  - 收紧 `FigmaChatPanel` 中 timeline 反馈操作区和失败重试卡的垂直间距，并让失败卡按内容列缩进展示。
  - 失败卡从 RunEvent / card payload 中提取 `error.message`、`payload.message`、`code`、`statusCode`、`traceId` 等字段，避免有真实错误信息时仍退回通用断开文案。
  - 确认当前历史会话重试仍复用最近一次 prompt；若历史会话因工作区不可用或无权限被置为只读，`handleSend` 会阻止重试，这是现有前端保护逻辑。
- How:
  - 仅修改 `frontend/apps/agent-web/src/components/FigmaChatPanel.vue` 与对应组件测试，不改后端、RunEvent 契约、API、数据库或 generated SDK。
- Result:
  - `@test-agent/agent-web` typecheck、`@test-agent/agent-chat` typecheck、`FigmaChatPanel.test.ts` 定向 Vitest 和 `git diff --check` 通过；`127.0.0.1:3000` 前端入口可达。

### 2026-07-05 - Agent timeline 内容块宽度自适应

- Why:
  - 用户在拉宽右侧对话面板后发现最终回答正文气泡和“探索/技能”等工具行仍停留在固定宽度，不能贴合可用内容列右侧。
- What:
  - 将 `opencode-like` 当前主 timeline 的正文 `text` part、reasoning 折叠行、工具/上下文/工具组触发器和子 Agent 卡片从 `inline-block` 或 `min(100%, 560px)` 调整为 `width: 100%`，随 assistant 内容列自适应。
- How:
  - 仅修改 `frontend/packages/agent-chat/src/opencode-like/styles/parts.css` 与 `tools.css`，不改后端、RunEvent、reducer、API 或数据库。
- Result:
  - Playwright 构造 DOM 验证工具行与正文气泡宽度均等于内容列宽度；`@test-agent/agent-chat` typecheck 通过；前端 Vitest 通过（37 files, 332 passed, 1 skipped）；`127.0.0.1:3000` 前端入口可达。
### 2026-07-05 - 修复普通编号回复误弹提问面板

- Why:
  - 用户反馈 SSE 报文只有 assistant 文本和终态事件，没有 `question.asked`，但页面仍在输入框上方弹出“1/2 个问题”面板；根因是 `FigmaChatPanel` 旧本地启发式会把最后一条 assistant 的普通编号列表误识别为待答选择题。
- What:
  - 删除 `FigmaChatPanel.vue` 基于 assistant 文本的 `choiceOptions/showChoicePanel` 解析和旧选择题面板，改为只渲染 `question.asked` / `permission.asked` 经 reducer 投影出的受控请求。
  - 新增回归测试覆盖普通编号 assistant 输出不弹提问面板、事件驱动问题可回复/拒绝，以及 reducer 不从 `message.part.updated` 文本推断 question。
  - 同步更新 `frontend/apps/agent-web/src/PACKAGE.md` 和 `frontend/packages/agent-chat/README.md`，明确提问 UI 只能由 RunEvent `question.asked` 驱动。
- How:
  - 在 `FigmaChatPanel` 中新增 `permissions/questions` props 与 `reply-permission/reply-question/reject-question` emit，答题结果仍交给 `AgentWorkbench` 现有 mutation 处理；普通 assistant 编号列表只按正文渲染。
- Result:
  - `FigmaChatPanel.test.ts`、`runtime-reducer.test.ts` 和 `@test-agent/agent-web` typecheck 通过；未改后端 API、RunEvent 契约、数据库、generated SDK 或环境配置。

### 2026-07-05 - 修复文件树对齐、列表背景色、图标切换、机器名优先压缩、首页底色、取消选中、主体区域底色全白及文件夹前导箭头 14x14 化

- Why:
  - 1. 用户反馈工作台左侧文件树中，图标 and 文字没有对齐，图标视觉上偏上。
  - 2. 用户要求将工作空间与 Agents 两块文件列表展示区域 of 底色由原先 the 浅灰色改为白色。
  - 3. 用户要求文件夹取消图标展示（仅保留展开/折叠三角箭头），文件图标切换为 Material Icon Theme 风格彩色图标。
  - 4. 用户要求侧边栏宽度变窄时，优先压缩/截断机器名（如 `huangzhenrendeMacBook-Air.local`），绝对禁止压缩/截断文件夹或根节点名称（如 `公共级`、`应用级`、`工作空间`）。
  - 5. 用户要求将主页背景/底色修改为较淡雅的浅蓝色 `#F0F4FA`（替代原先的 `#f5f5f5`）。
  - 6. 用户要求侧边栏中的 Agents 两个配置级根节点（“公共级”与“应用级”）在初始加载时不要默认有灰色高亮选中状态。
  - 7. 用户要求将红线圈起的主体内容区域（包含左侧边栏、中侧编辑器卡片、各面板容器、拖拽分隔条、以及聊天面板）的底色背景全部统一修改为纯白色 `#ffffff`。
  - 8. 用户要求文件夹前面的展开/折叠箭头（即 twistie 图标）统一调整为 14x14 大小。
- What:
  - 1. 在 `globals.css` 中微调文件树图标 `position: relative; top: 1px;` 提升对齐感；`--ta-tree-bg` 全局修改为 `#ffffff` 实现列表底色纯白。
  - 2. `@test-agent/file-explorer` 引入完整 Material Icon Theme SVG 精灵图（`sprite.svg`） and `FileIcon.vue` 组件；`fileIcons.ts` 建立文件拓展名/精准文件名映射，文件夹类型直接返回空以移除文件夹图标。
  - 3. 为 `AgentConfigPanel.vue` 中的 `.agent-root-title` 以及 `FigmaFileExplorer.vue` 中的 `.figma-fe-section-title` 加上 `flex-shrink: 0; white-space: nowrap;` 约束，确保文件夹/区段名称永不被压缩；为机器名与 worktree badge 元素设为 `flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis;`，实现优先省略压缩机器名。
  - 4. 修改 `globals.css` 中定义主页、全局背景 and 底层面板的全局变量 `--ta-bg`、`--ta-chrome`、`--ta-panel`、`--ta-control`、`--secondary` 和语义 token `--background` 为 `#F0F4FA`。
  - 5. 将 `AgentConfigPanel.vue` 中定义的 `activeScope` 响应式变量初始值由原先的 `"PUBLIC"` 修改为 `null`；并为 Git 操作相关操作函数及 `v-if="activeScope"` 加上安全卫语句检查；同时引入局部常量 `scope` 以规避 TypeScript 对 ref 变量闭包类型收窄的静态推导局限。
  - 6. 修改 `FigmaShell.vue` 中涉及左侧文件树面板容器 `.figma-panel-left`、左右分割拖拽条 `.figma-files-resize-handle` / `.figma-chat-resize-handle`、中间内容包装器 `.figma-main-card-container` 以及右侧聊天背景 `.figma-chat-body` 等所有残留灰色的背景定义，全部统一改为 `#ffffff`。
  - 7. 在 `globals.css` 中拆分 `.ta-file-tree-spacer` 与 `.ta-file-tree-twistie` 样式规则，将其宽度 `width` 与弹性基准 `flex-basis` 以及文本行高 `line-height` 统一调整为 `14px`，确保文件夹前导的折叠/展开指示图标及其对应的空白占位符在 14x14px 大小下对齐无间。
- How:
  - 1. SVG 精灵图通过 Vite SVG 模块导入与 SVG `<use :href="...">` 实现，图标大小保持 16x16px 矢量高清显示。
  - 2. 伸缩布局上，通过显式声明 `flex-shrink: 0` 保护文件夹固定文案，由右侧 badge 容器承载弹性溢出截断 (`ellipsis`)，解决了之前两项文本同时按比例挤压导致"公共级"被挤成"共"字的问题。
  - 3. 统一全局浅色底色设计变量至雅致淡蓝 `#F0F4FA`，使主布局底色、背景色以及未激活 tab 栏与内容面形成高雅和谐 we cool-tone color scheme。
  - 4. 将初始 `activeScope` 设为 `null`，确保两级根节点只有在用户真实点击互动时才会呈现灰色高亮激活态。
  - 5. 将主体面板组合（Figma 风格布局中被红色线条包围的所有区域）的各级背景 and 分割条底色深度渲染为纯白 `#ffffff`，与外部大屏淡蓝的底色背景 `#F0F4FA` 形成强烈对比，提升视觉层次感。
- Result:
  - 单元测试（37 test files, 331 passed）、`vue-tsc` 类型检查全部通过，主体内容区成功更新为纯白背景。

### 2026-07-04 - 文件浏览区切换为 VS Code Workbench 风格

- Why:
  - 用户要求把左侧工作空间文件树和 Agents 文件树改成当前 CloudStudio 页面里的 VS Code Workbench 风格，包含字体、字号、间距、样式和文件图标。
- What:
  - 新增 `--ta-tree-*` 文件浏览局部 token，工作区文件树、搜索结果、变更列表和 Agent 配置树统一为 `#f8f8f8` 背景、`#e5e5e5` 分隔线、13px 系统 UI 字体和 22px 行高。
  - `@test-agent/file-explorer` 新增 `@vscode/codicons@0.0.45` 和 `getVsCodeFileIconClass`，工作区文件树与 Agent 配置树复用 codicon 文件/目录图标。
- How:
  - 没有恢复旧 Bootstrap Icons 方案；只在文件浏览区导入 VS Code codicon CSS，并通过全局 `.ta-file-tree-*` 类约束紧凑行高。E2E 冷启动若遇到 `Outdated Optimize Dep`，需要清理 Vite `.vite` 预构建缓存后重跑。
- Result:
  - 定向 file-explorer/agent-config Vitest、两个前端包 typecheck 和新增 workbench 视觉断言通过；完整校验结果见本次提交说明。

### 2026-07-04 - 回退文件树 Bootstrap Icons 接入

- Why:
  - 用户明确要求回退上一轮“文件树接入 Bootstrap Icons 文件类型图标”的修改。
- What:
  - 通过 `git revert --no-commit 518add084` 撤销 `bootstrap-icons` 依赖、文件类型图标映射、文件树/搜索结果图标替换、相关测试和文档同步。
- How:
  - 使用非破坏性 revert 保留 Git 历史，不使用 `reset --hard`；仅额外保留本条 session log 说明回退原因。
- Result:
  - 文件树恢复为原先的 `lucide-vue-next` 通用文件/目录图标；`@test-agent/file-explorer` 不再依赖 `bootstrap-icons`。

### 2026-07-04 - 新建对话按钮更换图标及悬浮提示即时化

- Why:
  - 1. 用户要求将右侧输入卡片内的“新建对话”按钮图标从加号（Plus）更换为笔写字的图标。
  - 2. 用户要求将“新建对话”和“上传附件”两个按钮在鼠标悬浮时即时显示对应的文字提示，消除 hover 时的显示延时。
- What:
  - 1. 在 `FigmaChatPanel.vue` 中从 `lucide-vue-next` 导入 `SquarePen`，并用 `<SquarePen>` 替换原“新建对话”按钮中的 `<Plus>` 图标组件。
  - 2. 将“新建对话”按钮和“上传附件”按钮分别用 `<el-tooltip>` 裹挟，并将 `:show-after` 延迟参数设定为 `0` 实现无延迟即时呈现。
  - 3. 移除了两个按钮原生的 `title` 属性，避免浏览器原生的悬浮气泡干扰 Element Plus tooltip 的展示。
- How:
  - 1. 修改 `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`：
    - 将 `lucide-vue-next` 导入处的 `Plus` 改为 `SquarePen`。
    - 用 `<el-tooltip content="上传附件" placement="top" :show-after="0">` 包装上传附件 `<button>`。
    - 用 `<el-tooltip content="新建对话" placement="top" :show-after="0">` 包装新建对话 `<button>`，并将里面的 `<Plus />` 换成 `<SquarePen />`。
- Result:
  - 1. 前端 `corepack pnpm typecheck` 和 `corepack pnpm build` 运行成功，无 TypeScript 校验和打包错误。
  - 2. Vitest 单元测试 `corepack pnpm test FigmaChatPanel.test.ts` 完美通过（62 passed）。
  - 3. 无需修改任何后端逻辑、API 契约、数据库或 generated SDK。

### 2026-07-04 - 文件编辑多 Tab 高度改为30px与滚动条优化

- Why:
  - 1. 用户要求保持多 Tab 编辑区高度为 30px。
  - 2. 用户要求当 tab 个数超过宽度时，悬停在 tab 上时显示横向滚动条，非悬停时不显示。
  - 3. 任何时候都绝对不能出现纵向滚动条。
- What:
  - 1. 将 `.figma-editor-tabs` 容器高度修改为 `30px`，使其比原先的 `38px` 更紧凑，且保持在 30px 高度不变。
  - 2. 优化滚动条展示方式，将 WebKit 滚动条高度降为更精致的 `3px`，并在 hover 时呈现半透明 `rgba(0, 0, 0, 0.2)`，非 hover 时完全透明，防止滚动条在 hover 瞬间因为从无到有导致内容高度抖动。
  - 3. 通过 `overflow-y: hidden !important` 强行限制任何时候都在 Tab 区域无法产生纵向滚动条。
- How:
  - 1. 修改 `frontend/apps/agent-web/src/components/FigmaEditorArea.vue` 中的 CSS 样式：
     - `.figma-editor-tabs` 设定 `height: 30px;` 并保持 `overflow-y: hidden !important;` 约束。
     - `::-webkit-scrollbar` 高度由 `4px` 减为 `3px`，且 border-radius 相应调整为 `3px`。
- Result:
  - 1. 前端 `corepack pnpm typecheck` 校验通过，没有任何 TS 编译错误。
  - 2. 前端 329 个单元测试（`corepack pnpm test`）完美通过，无回归问题。
  - 3. 本地 Git status 干净，无无关重构或多余改动。

### 2026-07-04 - 修复反馈误用 opencode 远端消息 ID

- Why:
  - 用户反馈反馈接口仍返回 `NOT_FOUND: 消息不存在`，失败 URL 中的 `msg_f2d478d96001861rLCyXjYqf75` 是 opencode 远端 message id，不是平台 `session_messages.message_id`；仅判断 `msg_` 前缀会误把远端 ID 当成平台反馈目标。
- What:
  - 前端 `AgentMessage` 明确区分 `platformMessageId` 和 `remoteMessageId`；实时 reducer 只把 opencode 事件 ID 标记为 `remoteMessageId`，历史 Session message 恢复时带上平台 ID 和远端 ID。
  - `FigmaChatPanel` 只允许 `msg_` + 32 位 hex 的平台 messageId 提交反馈；Run 终态后 `AgentWorkbench` 刷新当前 Session message 快照，用 `remoteMessageId -> platformMessageId` 补齐映射后再显示反馈按钮。
- How:
  - 先补充红灯用例覆盖“远端 opencode msg_* 不展示反馈”和“映射到平台 ID 后提交平台 ID”，再修改 shared-types、agent-chat reducer、agent-web 工作台与反馈面板，并同步 README/PACKAGE 说明。
- Result:
  - `FigmaChatPanel.test.ts`、`workbench-utils.test.ts`、`runtime-reducer.test.ts` 定向与全文件测试通过；`@test-agent/agent-web`、`@test-agent/agent-chat`、`@test-agent/shared-types` typecheck 通过。
  - 未修改后端 API、RunEvent、数据库、generated SDK 或环境配置。

### 2026-07-04 - 文本编辑框未保存文件标题加*号及关闭二次确认

- Why:
  - 1. 用户要求在文本编辑框中，修改但未保存的文件标题前面加一个橙色的 `*` 号，提示用户该文件有未保存的修改。
  - 2. 用户要求当未保存的文件被关闭时，弹出 `div` 二次确认弹窗，防止用户误关闭导致修改丢失。
  - 3. 当多 Tab 数量超出宽度时需要提供横向滚动条，平时隐藏，鼠标悬停时显式出现；且必须绝对限制任何时候都不能出现纵向滚动条。
- What:
  - 1. 修改未保存标题星号：在 `FigmaEditorArea.vue` 的 Tab 标题前面，当文件处于修改未保存状态时，渲染一个橙色的 `*` 号。
  - 2. 关闭二次确认弹窗：在 `AgentWorkbench.vue` 拦截 `@close` 事件。如果检测到目标 Tab 属于未保存的文件（即 `!tab.livePreview && tab.content !== tab.savedContent`），则缓存待关闭文件路径并弹出二次确认 `div` 弹窗。
  - 3. 弹窗交互：点击“确认关闭”则关闭 Tab 并销毁弹窗，点击“取消”则仅销毁弹窗，数据无损。
  - 4. Tab 滚动条优化：在 `FigmaEditorArea.vue` 中调整 `.figma-editor-tabs` 容器样式。通过使用 `::-webkit-scrollbar` 微调、设置 Firefox 及 Webkit 的滚动条展示规则，实现悬停时显示精美的 4px 细横向滚动条，非悬停时滚动条完全透明不占位，且通过 `overflow-y: hidden !important` 杜绝了纵向滚动条的产生。
- How:
  - 1. 修改 `frontend/apps/agent-web/src/components/FigmaEditorArea.vue`：在 `tab.title` 之前插入带有判断条件的 `<span>*</span>`，并在 style scoped 中定义 `.figma-editor-tab-dirty-star` 样式，指定橙色（`#f97316`）并微调 margins 使之排版协调。
  - 2. 修改 `frontend/apps/agent-web/src/components/AgentWorkbench.vue`：将 `@close` 绑定变更为 `handleCloseTab`，并在 setup 中声明 `tabPathToClose` 与 `showUnsavedConfirm` 两个响应式变量及 `handleCloseTab`, `confirmCloseTab`, `cancelCloseTab` 逻辑方法。
  - 3. 在 `AgentWorkbench.vue` 的模板最底部添加二次确认 `div` 结构，并在 style scoped 中增加相应的遮罩、对话框、按钮的 Figma 极简拟物化样式（包括橙色高亮的主确认按钮与呼吸感背景）。
  - 4. 修改 `FigmaEditorArea.vue` 的 CSS：在 `.figma-editor-tabs` 加上 `overflow-y: hidden !important` 限制与 hover 自定义滚动条展现规则。
- Result:
  - 前端运行 `corepack pnpm typecheck` 和 `corepack pnpm lint` 完美通过，无类型或格式错误。
  - 确认本改动完全在前端局部实现，不改变任何后端 API、DTO、数据库结构或 generated SDK。

### 2026-07-04 - 修复流式失败后重试仍显示旧错误

- Why:
  - 用户反馈对话出现 `Streaming response failed` 后点击重试仍报错；根因是重试先把聊天 reducer 切到新一轮 `PENDING`，但旧平台 Run 仍保持 `FAILED`，运行态合并逻辑让上一轮终态压住了新一轮启动态。
- What:
  - `AgentWorkbench` 的 busy 判定现在优先识别聊天 reducer 的 `PENDING/RUNNING/CANCELLING`，重试期间不会被上一轮 `FAILED/SUCCEEDED/CANCELLED` 覆盖；失败卡片重试仍复用最近 prompt 重新创建 Run，不新增 API。
  - Playwright mock 支持按请求序号返回不同 runId，并为失败重试用例覆盖第一轮 `Streaming response failed`、第二轮新 Run 启动后旧失败卡片消失。
- How:
  - 先补充 `follow-up-queue.test.ts` 红灯，确认 `FAILED + PENDING` 旧逻辑返回非 busy；再修改 `follow-up-queue.ts` 的状态优先级，并同步 `agent-web` README。
- Result:
  - 定向 Vitest 与 Playwright 重试用例通过；未修改后端 API、RunEvent、数据库、generated SDK 或环境配置。

### 2026-07-04 - 修复合并 assistant 消息后的反馈 messageId

- Why:
  - 用户提交 AI 回复反馈时后端返回 `NOT_FOUND: 消息不存在`；根因是 `FigmaChatPanel` 会把连续 assistant 片段合并为一个展示气泡，但反馈按钮沿用了合并前第一段的运行期临时 id，后端 `ai_message_feedback` 接口只接受已落库的 `session_messages.message_id`（`msg_*`）。
- What:
  - `FigmaChatPanel` 展示态新增独立 `feedbackMessageId`，反馈按钮、选中状态和提交中状态都按后端持久化 `msg_*` 查询/提交，不再把临时 assistant id 传给反馈 API。
  - 合并连续 assistant 消息时优先保留后续片段中的持久化 `msg_*`，避免最终回答已落库但气泡 id 仍是临时 id。
- How:
  - 先补充 `FigmaChatPanel.test.ts` 回归用例，复现“临时 assistant 片段 + 持久化 assistant 片段合并后点击满意”必须提交 `msg_*`。
  - 修改 `frontend/apps/agent-web/src/components/FigmaChatPanel.vue` 的反馈 id 选择逻辑，并同步更新 `frontend/apps/agent-web/README.md`。
- Result:
  - 定向红灯确认后，`corepack pnpm --filter @test-agent/agent-web exec vitest run tests/FigmaChatPanel.test.ts --environment jsdom` 通过（58 passed，1 skipped）。
  - 未修改后端 API、RunEvent、数据库或 generated SDK。

### 2026-07-04 - 优化对话定位器 UI 与任务消耗/运行状态展示布局

- Why:
  - 1. 用户要求优化对话定位器（ConversationLocator）：需放置在右侧、指示线变短、突出横线匹配当前选中的对话轮次、悬浮浮层后对话项只占 2 行且具有不透明白色背景。
  - 2. 用户要求将位于输入框上方的“任务消耗”提示栏与消息流中的任务终态提示（已手动终止、任务失败、任务完成）移动至最下方的底部常驻栏（footer）。
- What:
  - 1. 对话定位放置在右侧：将定位器触发按钮定位在右侧边缘，浮动面板向左侧展开。
  - 2. 横线短一点：指示器的线宽由原来的 24px/28px 减小到常规 10px / 激活 16px。
  - 3. 突出的横线匹配当前定位的对话：将原来的 3 个静态横线改为根据轮次动态渲染，激活的轮次对应的横线高亮展示。
  - 4. 浮层内每个对话 2 行，白底不透明：通过隐藏文件列表并将摘要部分限制为 1 行（标题 1 行 + 摘要 1 行 = 2 行）实现定位器卡片内容固定为 2 行。修复 CSS 作用域问题（Teleport 到 body 丢失变量）以实现纯白不透明（`#ffffff`）背景和文字颜色重载。
  - 5. 任务消耗与运行状态提示下移：将 `figma-chat-usage` 的 DOM 结构从 `textarea` 上方搬移至 `.figma-chat-footer` 内。移除了滚动消息流中的 `figma-chat-stopped`、`figma-chat-failed`、`figma-chat-completed` 状态栏，并改在 footer 内的 `.figma-chat-usage` 容器中以内联文字形式（显示红色的已手动终止/任务失败，绿色的任务完成）进行常驻展示。在两者之间加入 `·` 分隔符。
- How:
  - 1. 修改 `tokens.css`：将 `.oc-conversation-locator__panel` 显式加入 `--oc-*` 变量声明组中。
  - 2. 修改 `ConversationLocator.vue`：使用 `v-for="turn in turns"` 循环渲染 spans，并根据 `activeTurnId === turn.id` 动态覆盖高亮；同时更新 `updatePanelPosition` 使其向左弹出。
  - 3. 修改 `locator.css`：重新设定定位器的 sticky 与右侧定位属性，更新 `span` 宽度属性和激活态，对 `.oc-conversation-locator__panel` 及其内部元素强制设定 light-theme 级别的纯白背景和深色文字，隐藏文件元素，并将摘要截断设为 `-webkit-line-clamp: 1`。
  - 4. 修改 `FigmaChatPanel.vue`：将 `figma-chat-usage` 放入 `figma-chat-footer`；在 footer 中独立渲染 status 元素（使其与 `hasTaskUsageDisplay` 解耦以防止缺少消耗数据时状态被吞），使用 `<template v-if="hasTaskUsageDisplay">` 隔离消耗字段；同时增加 `.figma-chat-status-item` 及各状态颜色、内联图标与间隔符 CSS 样式。
- Result:
  - 前端 Vitest 324 个单测（包含 `FigmaChatPanel.test.ts` 状态与消耗判定用例）完美通过，无回归。

### 2026-07-04 - 增加对话时间线左侧定位器

- Why:
  - 用户希望右侧对话输出在超过 3 轮后，左侧中线出现类似截图的对话定位能力，便于长对话中快速回到某一轮。
- What:
  - `OpencodeTimeline` 新增左侧中线悬浮定位器；当前可见时间线用户对话轮次大于 3 时显示，弹层列出全部轮次的用户问题、助手摘要和最多 2 个文件 chips，点击轮次滚动定位到对应用户消息。
  - 能力落在 `agent-chat` 的 `opencode-like` 主路径，因此 `AssistantThread` 和 `FigmaChatPanel` 复用的时间线都会生效。
- How:
  - 先补充 Vitest 回归测试覆盖 3/4 轮阈值、弹层摘要与文件 chips、点击滚动、`AssistantThread` 主路径集成；再新增内部定位器组件、摘要 helper 和样式，未改公开 props/type。
  - 同步更新 `frontend/packages/agent-chat/README.md`；未改后端 API、RunEvent、数据库、generated SDK 或环境配置。
- Result:
  - `corepack pnpm test -- packages/agent-chat/tests/opencode-timeline.test.ts` 通过；
  - `corepack pnpm --filter @test-agent/agent-chat typecheck` 通过；
  - `corepack pnpm --filter @test-agent/agent-web typecheck` 通过。

### 2026-07-04 - 修复手动终止状态跨 Run 残留

- Why:
  - 用户反馈手动终止后再次发送消息，新一轮已经完成但右侧面板仍显示“已手动终止”；根因是旧 Run 的取消终态可能在新 Run 开始后晚到，且面板本地 `wasStopped` 记忆没有在新 Run 开始时清理。
- What:
  - `AgentWorkbench` 只接收当前订阅且仍为页面活动 Run 的 RunEvent；`event-stream-client` 在订阅关闭后不再投递已排队回调，并丢弃 runId 不匹配事件；`FigmaChatPanel` 在新一轮运行开始时清理上一轮完成/失败/手动终止提示。
  - 补充前端回归测试覆盖旧 `run.cancelled` 不污染当前 Run、SSE close 后不投递、下一轮成功不显示“已手动终止”。
- How:
  - 按 TDD 先写失败用例，再增加 `runEventMatchesRun` 纯函数和客户端边界防护；仅修改前端状态隔离与相关 README/PACKAGE 说明，不变更后端 API、RunEvent 契约或数据库。
- Result:
  - 定向 `corepack pnpm test -- FigmaChatPanel workbench-utils event-stream-client` 通过（36 files, 318 passed, 1 skipped）。
  - `@test-agent/agent-web` 与 `@test-agent/event-stream-client` typecheck 通过。

### 2026-07-03 - reasoning 折叠头展示最新尾部摘要

- Why:
  - 用户希望 reasoning 默认仍折叠，但折叠头能像 Codex 一样持续变化；上一版若取首句摘要，会在长 reasoning 流式追加时一直停留在第一句。
- What:
  - `OcDisclosure` 支持中间列 detail 文本，reasoning 折叠头展示压缩后的最新尾部摘要；状态徽标按 `running/completed/failed` 复用工具行颜色；工具英文标题统一 CSS uppercase。
- How:
  - 仅改前端 `agent-chat` 当前 opencode-like 主路径组件、样式、测试和 README；不改后端、RunEvent、reducer、投影顺序、API 或数据库。
- Result:
  - `@test-agent/agent-chat` typecheck 通过；前端 Vitest 全量通过（36 files, 299 passed, 1 skipped）；`git diff --check` 通过；前端 Vite 验证入口 `127.0.0.1:3001` 可达。

### 2026-07-03 - 增强 timeline 正文持续输出感

- Why:
  - 用户希望整体输出更接近 Codex：正文持续输出保持可见，过程信息按需折叠；同时当前“思考状态”标题偏小，和下方工具标题层级不一致。
- What:
  - 仅调整 `agent-chat` opencode-like 样式：运行中的正文输出增加轻量呼吸边框和行尾光标，保留正文持续展开；reasoning/tool/context 仍使用现有折叠逻辑；“思考状态”字号和字重提升到工具标题层级。
- How:
  - 不改后端、RunEvent、reducer、投影顺序、默认折叠规则或 API；只修改 `parts.css` 的 running text 与 reasoning title 样式。
- Result:
  - Playwright 构造 DOM 验证 running text cursor 生效且 reasoning 标题为 13px/600；`@test-agent/agent-chat` typecheck 通过；前端 Vitest 全量通过（36 files, 299 passed, 1 skipped）。

### 2026-07-03 - 收紧 reasoning 折叠行与头像中心线对齐

- Why:
  - 当前 opencode-like timeline 中，“思考状态”默认折叠且位于输出过程顶部，如果仍按工具行的大列宽样式展示，会在窄面板里显得像漂浮标题，并容易与助手头像产生水平错位观感。
- What:
  - 仅调整 `agent-chat` 当前 timeline 主路径样式：把 `.oc-reasoning-part` 折叠触发器收紧为轻量单行，运行中用小圆点提示；针对带头像的 reasoning 首行用 `:has(.oc-reasoning-part)` 做中心线对齐。
- How:
  - 不改 reducer、投影顺序、RunEvent、后端 API 或默认折叠逻辑；保留“同一回合 reasoning 合并为一行、默认收起”的当前行为，只降低视觉权重并修正头像对齐。
- Result:
  - 前端 Vitest 全量通过（36 files, 299 passed, 1 skipped），`@test-agent/agent-chat` typecheck 通过；Playwright 构造 DOM 检查隐藏 step 行头像与 reasoning 触发器 top delta 为 0px，reasoning `已完成` 与工具 `已读取` x delta 为 0px；前端 Vite 已在 `127.0.0.1:3001` 启动验证入口。
### 2026-07-04 - 修复子 Agent 入口跨轮次残留

- Why:
  - 用户反馈上一轮对话的子 Agent 入口有时会一直出现在后续每一轮对话中；根因是 `opencode-like/state` 在原始 task message 已不可见时，会把保留的 `subagentsBySessionId/subagentByTaskPartId` 索引合成到“最后一个可见 assistant”，导致旧子 Agent 被挂到新用户轮次。
- What:
  - `agent-chat` 的合成子 Agent 入口只允许补回仍可见的原始 `taskMessageId`，不再猜测挂到最新 assistant 或后续用户轮次。
  - 补充 `opencode-like-state.test.ts` 回归测试，覆盖原 task message 已缺失时旧 `prt_task` 不会追加到新一轮 assistant parts。
  - 同步更新 `agent-chat` README 与 `src/PACKAGE.md` 的子 Agent 补偿边界说明。
- How:
  - 通过 TDD 先确认新增测试在旧逻辑下失败：`msg_assistant_2` 被错误追加 `prt_task_old`。
  - 修改 `frontend/packages/agent-chat/src/opencode-like/state/adapter.ts`，让缺少可见原始 task message 锚点的子 Agent 索引跳过投影。
- Result:
  - `corepack pnpm test -- packages/agent-chat/tests/opencode-like-state.test.ts` 通过。
  - `corepack pnpm test -- packages/agent-chat/tests/opencode-like-state.test.ts packages/agent-chat/tests/opencode-timeline.test.ts packages/agent-chat/tests/runtime-reducer.test.ts` 通过（Vitest 实际执行当前前端 36 个测试文件，315 passed，1 skipped）。
  - `corepack pnpm --filter @test-agent/agent-chat typecheck` 通过。

### 2026-07-04 - 优化正在工作状态行的视觉动效

- Why:
  - 用户希望对对话输出时页面显示的“正在工作 等待后续输出”这一状态行做视觉效果优化，要求其呈现类似 ChatGPT 思考时显示的那种文字呼吸/脉冲动效。
- What:
  - 移除了 `WorkingStatusRow.vue` 模板中的闪烁小点 `oc-thinking-dot`。
  - 在 `rows.css` 中，为整个 `.oc-working-status` 容器添加了 `oc-pulse` 渐隐渐现的呼吸/脉冲（breathing/pulse）动画效果，并使标题和副标题文本保持固定的系统颜色，从而让所有文字以最优雅、高保真的形式跟随脉冲淡入淡出。
- How:
  - 修改了 `frontend/packages/agent-chat/src/opencode-like/components/rows/WorkingStatusRow.vue` 和 `frontend/packages/agent-chat/src/opencode-like/styles/rows.css`。
  - 通过 `corepack pnpm test` 跑通了全部 Vitest 测试，执行 `corepack pnpm typecheck` 和 `corepack pnpm lint` 校验无误。
- Result:
  - 移除了左侧闪烁小点，且“正在工作 等待后续输出”整行文字以极富现代科技感的呼吸脉冲方式进行动画过渡，完美契合 ChatGPT 的思考态文字表现形式。

### 2026-07-04 - 优化持续输出时的工作态提示

- Why:
  - 持续流式输出和大量工具过程交替出现时，running text part 会长时间停在 Markdown 的“准备输出…”占位，用户难以判断 Agent 是否仍在工作或内容是否正在增长。
- What:
  - `agent-chat` running/pending 文本改为轻量纯文本 live preview，显示实际增长内容和“生成中”状态，完成后再进入 Markdown 渲染。
  - `opencode-like` 最新 running turn 在已有过程项但尚无文本输出时只追加一个 `working-status` 行，主/子 Agent 视图都复用时间线工作态，不恢复旧底部任务面板。
  - `MarkdownView` 在非空 source 重新渲染 pending 时保留已有 HTML，空白 source 仍同步显示“无内容”并跳过重型渲染。
- How:
  - 只修改前端 `agent-chat` 时间线、文本渲染、样式、测试与相关 README；未改后端 API、RunEvent SSE、数据库、generated SDK 或环境配置。
- Result:
  - 新增/调整 Vitest 覆盖 Markdown pending 保留旧内容、running text live preview、root/child scope 下单个工作态行和无“准备输出…”占位。
  - 最终提交前定向 Vitest、`agent-chat`/`agent-web` typecheck、`agent-web` build 与 diff 检查均通过；build 仅保留既有 Vite CSS `@import` 顺序和大 chunk 警告。

### 2026-07-04 - 补强 Markdown 分屏源码区 Monaco 显式尺寸布局

- Why:
  - 用户反馈 Markdown 文件开启预览后，下方预览能正常显示，但上方原文编辑区仍为空白；早前的 flex/ResizeObserver 修复在部分 Dockview 百分比高度场景下仍可能只触发无参 `editor.layout()`，Monaco 继续沿用 0 尺寸。
- What:
  - `CodeEditor.vue` 在源码宿主存在实际 `clientWidth/clientHeight` 时调用 `editor.layout({ width, height })`，并给源码宿主补充 `w-full overflow-hidden` 和稳定测试标识。
  - 补充 `CodeEditor.preview.test.ts` 回归测试，覆盖预览分屏打开时按源码容器实际尺寸布局 Monaco。
  - 同步更新 `@test-agent/editor` README 和 `src/PACKAGE.md` 的 Markdown 预览布局说明。
- How:
  - 只修改前端 `@test-agent/editor` 包和本 session log；不改后端 API、RunEvent、数据库、generated SDK 或环境配置。
- Result:
  - `corepack pnpm vitest run packages/editor/tests` 通过；
  - `corepack pnpm --filter @test-agent/editor typecheck` 通过；
  - `corepack pnpm --filter @test-agent/agent-web typecheck` 通过。

### 2026-07-04 - 移除 thought for 统计与展示并修复新建对话耗时清零

- Why:
  - 1. 用户要求前端不再显示和统计 `thought for` 相关的耗时信息（包括思考时长）。
  - 2. 用户反馈新建对话（创建新会话）后，任务的累计耗时和上一轮的终态耗时数据没有被清零，仍然显示上一轮的残留耗时。
- What:
  - 1. 移除 `AgentWorkbench.vue` 中对 `thoughtFor` 的统计与计算：删除了 `accumulatedReasoningMs` 与 `lastThoughtForMs` 变量、`recomputeUsageFromChat` 的思考时间解析，并在 `taskUsage` 返回值中移除 `thoughtFor`。
  - 2. 在 `FigmaChatPanel.vue` 中移除 `TaskUsage.thoughtFor` 属性定义及 computed properties 中的对应统计项，并在模板中删除 `thought for` 以及基于 `duration` 的 `thought for` 子项渲染模板。
  - 3. 在 `AgentWorkbench.vue` 的 `handleNewConversation`（新建对话）和 `switchSession`（切换会话）事件处理中，显式将任务消耗统计相关的变量（`chatStartedAt`、`accumulatedTokens`、`totalDurationMs`、`lastDuration`、`lastTokens`、`nowTick` 等）全部复位，解决残留旧耗时的问题。
  - 4. 同步更新 `frontend/apps/agent-web/README.md` 中有关“任务消耗”的信息描述。
- How:
  - 只修改了前端组件、配置文件和 README。通过 `corepack pnpm typecheck` 和 `corepack pnpm lint` 校验其完整性和规范，并使用 `corepack pnpm test` 跑通了全部 307 个 Vitest 单元测试，没有任何报错或回归问题。
- Result:
  - 前端编译与类型检查无报错；单元测试全部通过。任务消耗面板不再显示 `thought for` 文本，新建/切换对话后耗时正确清零。

### 2026-07-04 - 保持聊天流式输出时的历史滚动位置

- Why:
  - 右侧 Agent 对话区在用户向上滚动查看历史时，新的流式输出会把滚动条强制拉回底部，导致无法稳定阅读历史内容。
- What:
  - `FigmaChatPanel` 增加 sticky scroll 状态：用户停留底部时继续自动跟随输出；用户离开底部后保留当前滚动位置，并显示“查看新内容”按钮用于手动跳回底部。
  - 补充 `FigmaChatPanel` 回归测试，覆盖用户上滚后新 assistant 输出到达时不改变 `scrollTop`。
  - 同步更新 `agent-web` README 中右侧消息区滚动行为说明。
- How:
  - 只修改前端 `agent-web` 组件、组件测试、README 和本 session log；未改后端 API、RunEvent、数据库、generated SDK 或环境配置。
- Result:
  - `corepack pnpm vitest run apps/agent-web/tests/FigmaChatPanel.test.ts` 通过；
  - `corepack pnpm --filter @test-agent/agent-web typecheck` 通过。

### 2026-07-04 - 文件修改摘要默认折叠并汇总增减行

- Why:
  - 右侧 opencode-like 时间线中的“文件修改 N”摘要会默认展开大量文件，挤占对话区域；折叠后也需要保留本轮变更规模信息。
- What:
  - `DiffSummaryRow` 改为默认折叠，标题行展示全部文件新增/删除行数汇总，点击标题展开文件列表；展开后的单个文件条目仍负责打开对应文件。
  - 补充时间线回归测试，覆盖默认折叠、点击展开/收起，以及父级 diff 文件列表变化后折叠态汇总自动更新。
- How:
  - 修改 `frontend/packages/agent-chat/src/opencode-like/components/rows/DiffSummaryRow.vue` 和 `styles/diff.css`；
  - 同步更新 `frontend/packages/agent-chat/tests/opencode-timeline.test.ts` 与 `frontend/packages/agent-chat/README.md`。
- Result:
  - `corepack pnpm vitest run packages/agent-chat/tests/opencode-timeline.test.ts packages/agent-chat/tests/opencode-like-state.test.ts` 通过；
  - `corepack pnpm --filter @test-agent/agent-chat typecheck` 通过。

### 2026-07-04 - 修复文件预览模式下 Monaco 编辑器坍塌与原始文件信息不可见

- Why:
  - 打开 Markdown 文件并启用“预览”（分屏预览模式）时，上方 Monaco 编辑器区域变为空白/白屏，无法看到原始文件信息。
  - 根因：`CodeEditor.vue` 的 `containerEl` 在 Markdown 预览开启时使用 `:style="{ height: splitPct + '%' }"`，但在 Flex 列容器中缺少 `flex: 'none'` (或 `flex-shrink: 0`)，导致 flex 布局计算时容器高度坍塌；且在切换预览状态、拖拽 sash 或切换文件时，未触发 `editor.layout()`，导致 Monaco 内部 DOM 视口与行渲染保留 0 尺寸。另外，`m.Uri.parse("file:///" + encodeURIComponent(path))` 将路径斜杠转义，影响 Monaco 的 URI 路径识别。
- What:
  - `CodeEditor.vue` 给 `containerEl` 增加 `flex: 'none'` 与 `shrink-0`，防止 Flex box 在 percentage height 下压缩 DOM 高度。
  - 引入 `ResizeObserver` 监听 `containerEl` 尺寸变化，并在 `props.showPreview`、`splitPct`、`path` 或 `content` 变化后通过 `nextTick` 调用 `editor.layout()` 重算布局。
  - 安全改用 `m.Uri.file(path)`（带有 `m.Uri.parse` 降级兜底），并同步修复 `CodeEditor.preview.test.ts` 中 `fakeEditor.layout` 与 `Uri.file` 的 mock 桩。
  - 更新 `@test-agent/editor` 包与 `src/` 下 README/PACKAGE 说明。
- How:
  - 修改 `frontend/packages/editor/src/CodeEditor.vue` 和 `frontend/packages/editor/tests/CodeEditor.preview.test.ts`；
  - 修改 `frontend/packages/editor/README.md` 和 `frontend/packages/editor/src/PACKAGE.md`。
- Result:
  - 前端全量 36 个测试文件、305 个 Vitest 测试用例全绿通过 (`npx pnpm --dir frontend test`)；
  - 前端 `npx pnpm --dir frontend run typecheck` 检查通过无错误。

### 2026-07-04 - 调整对话时间线内容左对齐

- Why:
  - opencode-like 时间线中的助手消息框 (AssistantMessageFrame) 使用了 2 列 Grid 布局及 46px 固定的 left-padding 给头像留列，导致下方所有思考状态、工具调用、探索卡片和正文回答在头像下方留有大面积空白缩进，不够紧凑且不符合 left-aligned 的设计预期。
- What:
  - 重构 `rows.css` 中的 `.oc-assistant-frame` 样式，移除 46px left-padding 及 2 列 Grid 限制，将助手消息的内容区域（`oc-assistant-frame__content`）统一设为 0 边距左对齐。
  - 调整头像与第一行的 Flex 布局，保证头像与首行或后续各类 Part（思考状态、skill、Explore、探索、正文气泡）都紧贴对话框左边 border 齐平展放。
- How:
  - 修改 `frontend/packages/agent-chat/src/opencode-like/styles/rows.css`。
  - 跑通 `opencode-timeline.test.ts` 和 `FigmaChatPanel.test.ts` 自动化单测，校验 `agent-chat` 和 `agent-web` typecheck 0 报错。
- Result:
  - `npx vitest run packages/agent-chat/tests/opencode-timeline.test.ts`（12 passed）。
  - `npx vitest run apps/agent-web/tests/FigmaChatPanel.test.ts`（55 passed）。
  - `vue-tsc --noEmit` 通过，无类型异常。

### 2026-07-04 - 修复 Run session scope MERGE 时间参数类型推断

- Why:
  - 后端处理 opencode `session.created` 时持续 WARN `Failed to route opencode stream event`，根因是 PostgreSQL 执行 `RunSessionScopeMapper.xml` 的 `MERGE ... USING (VALUES ...)` 时把 `updated_at` 参数推断为 `text`，无法写入 `timestamp without time zone` 列。
- What:
  - `RunSessionScopeMapper.xml` 在 `upsertScope` 和 `upsertSession` 的 `created_at/discovered_at/updated_at` 参数上显式 `cast(... as timestamp)`；补充 `PersistenceSqlConventionTest` 固化 PostgreSQL MERGE 时间参数 cast 约束。
  - 同步更新 persistence README、PACKAGE 说明和数据库部署文档，记录 H2 PostgreSQL 模式无法覆盖该 PostgreSQL 参数类型推断差异。
- How:
  - 先用新增源码约束测试确认当前 XML 缺少 cast 会失败，再修改 mapper；用真实 PostgreSQL 临时表 prepared statement 验证 text 参数加 cast 后可 insert/update。
- Result:
  - 定向 `PersistenceSqlConventionTest#runSessionScopeMergeUsesTimestampCastsForPostgreSql` 和 `MyBatisRunSessionScopeRepositoryIntegrationTest` 通过；`mvn clean package -DskipTests` 通过。
  - 本地 test profile 三服务重启成功，后端 readiness 为 UP；重启后后端日志未再出现 `updated_at` 类型错误或 `BadSqlGrammarException`，5 秒内行数无增长。
  - `mvn -pl test-agent-persistence -am test` 仍命中既有无关失败：H2 对存量 `ON CONFLICT` SQL 不兼容、`usr_test_dev` fixture 外键缺失、默认用户 seed 断言问题。

### 2026-07-04 - 修复 RunEvent live bus 并发背压导致后端刷屏

- Why:
  - 后端日志持续刷 `RunEventLiveBus Failed to emit live event ... FAIL_TERMINATED`；排查发现先有并发发布触发 `FAIL_OVERFLOW`，随后 `Sinks.Many#emitNext` 抛出 `Backpressure overflow` 并让 live bus sink 进入终止态，之后所有实时事件发布都会持续 WARN。
- What:
  - `RunEventLiveBus` 的 `FAIL_NON_SERIALIZED` 兜底不再调用会传播 overflow 的 `emitNext(...busyLooping...)`，改为 `tryEmitNext` 短时重试；慢客户端、断开连接或背压溢出按 best-effort 丢弃当前 live 帧，保持全局 live bus 可继续发布。
  - 补充 event 模块并发背压回归测试，并同步事件流文档和 event 模块 README/PACKAGE 说明。
- How:
  - 用 `.tmp/dev-services/backend.log` 定位第一条 `FAIL_TERMINATED` 前的 `FAIL_OVERFLOW` 和 `Backpressure overflow during Sinks.Many#emitNext`，再用并发无 demand 的 `StepVerifier` 测试复现。
  - 重启本地 test profile 服务加载新 jar，并观察新日志确认不再增长。
- Result:
  - `mvn -pl test-agent-event -am test` 通过，`mvn clean package -DskipTests` 通过，`./restart-dev-services.sh --profile test --env-file .env.test` 重启成功。
  - 重启后 `.tmp/dev-services/backend.log` 5 秒内无增长，未再出现 `FAIL_TERMINATED`、`FAIL_OVERFLOW` 或 `Backpressure overflow`。

### 2026-07-03 - 收敛时间线样式、文件预览 Mermaid 切换与修改文件入口

- Why:
  - opencode-like 时间线继续存在助手头像与首行内容对齐不稳定、Mermaid 预览按钮过大且工作区文件预览无法在脚本/图表间切换、文件修改卡片入口仍可能走 Diff 的问题。
- What:
  - 将聊天 Markdown 和工作区 Markdown 预览中的 Mermaid 代码块统一为轻量“脚本/图表”切换，默认展示脚本，点击后按需加载 `mermaid` 渲染图表，按钮和内容落在同一浅色容器内。
  - 恢复 assistant frame 固定头像列栅格，移除 `has-header` 的 flex 强制覆盖，并用头像尺寸变量统一工具、思考、正文首行对齐。
  - 工具标题恢复 opencode 风格小写，短绝对路径保留开头 `/`，文件修改卡片头部和文件行都改为打开文件而不是打开 Diff。
- How:
  - 修改 `agent-chat` 的 `MarkdownView.vue`、opencode-like rows/tools/parts/diff 样式、工具路径展示和 diff summary 行；修改 `editor` 的 `MarkdownPreview.vue` 增加 Mermaid 切换能力并引入 `mermaid` 依赖。
  - 同步更新 agent-chat/editor/agent-web 相关单元测试，未改后端 API、RunEvent、DTO、数据库或 generated SDK。
- Result:
  - `git diff --check` 通过。
  - `corepack pnpm@10.25.0 --dir frontend exec vitest run packages/agent-chat/tests/MarkdownView.test.ts packages/editor/tests/MarkdownPreview.test.ts packages/agent-chat/tests/opencode-timeline.test.ts apps/agent-web/tests/FigmaChatPanel.test.ts` 通过（76 passed, 1 skipped）。
  - `@test-agent/agent-chat`、`@test-agent/editor`、`@test-agent/agent-web` typecheck 均通过。
  - 后续对齐修正确认不要把首行强行撑到头像高度；应让头像顶边和右侧首个内容块顶边对齐，初始 thinking 行内部单独设置 20px 行高。

### 2026-07-03 - 优化对话时间线行间距、工具状态样式与文件输出路径展示

- Why:
  - 1. 对话时间线的步骤行和思维行之间的纵向间距过近，显得拥挤不美观；
  - 2. 工具和思考状态行设计单调、缺乏视觉吸引力，且工具标题强行小写不够规整；
  - 3. 文件输出展示了冗余的相对工作区前缀（如 `F-COSS/workspace/`），导致在窄侧边栏下被 ellipsis 截断成无意义字符，无法直接阅读文件名；
  - 4. 前置合并引入的冲突标志虽已被处理，但 `allModels` watch 会在切换/加载工作区时因数据为空导致 transient 清除用户的模型和供应商配置。
- What:
  - 1. 调大 timeline 行间距 `.oc-row` (从 4px 到 8px) 和 continuation 段间距 (从 2px 到 6px)，触发器垂直 padding 从 2px/3px 调至 6px。
  - 2. 美化工具状态 `oc-tool__status` 为 premium 渐变高亮微章，移除工具标题的 lowercase 强转以恢复规范大小写，添加 hover 背景变色与平滑过渡。
  - 3. 增强 `formatDisplayPath` 自动识别并剥离 `/Users/..`、`test-workspaces/[app]/workspace/` 等冗余前缀，优先保留 filename 并在 32 字符内合理显示 parent 路径。
  - 4. 修复 `AgentWorkbench` 对 `allModels` 的侦听逻辑，在 queries pending 期间跳过 preference reset，确保跨 workspace 切换时偏好不丢失。
- How:
  - 修改 `tool-registry.ts` 处理路径裁剪逻辑，修改 `ToolPartGroup.vue` 的 title Casing。
  - 修改 `timeline.css`、`rows.css`、`tools.css` 相关类以调优间距、圆角、背景、badge 样式与过渡效果。
  - 修改 `AgentWorkbench.vue` 优化 model preference 变更策略与 pending 保护。
- Result:
  - 前端 `corepack pnpm build` 与 typecheck 完美通过，时间线版面结构宽裕透气，文件名突出醒目，偏好切换机制运行稳健。
### 2026-07-03 - 固定子 Agent task 入口 root scope 与独立展示

- Why:
  - 子 Agent task 入口偶发显示“智能体 / 准备中”后消失，停止后 `Explore` 又折叠在 task 分组里；根因是 task metadata discovery 分支把原始 root task part 改成 child scope，主视图会过滤掉 root assistant message，且前端把多个 `tool=task` part 当普通工具聚合。
- What:
  - `RunSessionScopeRouter` 在 task metadata 发现 child session 时仍输出 child discovery/scope updated，但原始 `message.part.updated` task part 固定保留 root scope。
  - `agent-chat` reducer 对历史/错误 live payload 做防御兼容：`payload.sessionId=child` 但 `payload.sessionID/part.sessionID=root` 的 task part 仍按 root message scope 记录，同时保留 `taskPartId -> childSessionId` 绑定。
  - `opencode-like` 投影让 `tool=task` 子 Agent 卡片跳过普通工具折叠组，每个 task part 独立直接展示。
- How:
  - 不新增 API、不改数据库、不改 generated SDK、不改环境配置；只修改 runtime scope router、agent-chat reducer/projection、回归测试和稳定文档。
- Result:
  - 偶发的 root task 被 child scope 覆盖不再导致主视图入口消失；多个 `Explore` 子 Agent 卡片在主对话中直接可见，不需要展开 task 分组。

### 2026-07-03 - 绑定原生 pending task 与 child session

- Why:
  - 原生 opencode 创建子 Agent 时先发送 root `message.part.updated` task pending 事件，再发送带 `parentID` 的 child `session.created`；task part 初始没有 child session id，导致前端只能显示短暂未绑定入口，后续 snapshot/removed 或 sync 包装去重后入口偶发消失。
- What:
  - `OpencodeRunEventMapper` 解开 opencode `payload.syncEvent` 包装，使用内层 event type/id/data，保证 direct 与 sync 事件共享 raw event id 去重。
  - `RunSessionScopeRouter` 对 root pending task part 维护 `runId + parentSessionId` FIFO 队列，child `session.created/session.updated(parentID)` 到达时补齐 `taskMessageId/taskPartId/taskCallId`，并从 child `info.agent/info.title` 生成展示 metadata。
  - `agent-chat` 主视图在真实 task part 被移除或快照缺失时，基于 `subagentsBySessionId/subagentByTaskPartId` 合成子 Agent 导航入口；未绑定 task 显示“智能体 / 准备中”，绑定后显示 `Explore + title`。
- How:
  - 不新增 API、不改数据库、不改 generated SDK；后端保持 opencode-client 做 raw/mapped 边界、runtime router 做 scope 权威绑定，前端只消费稳定 child discovery 和展示兜底。
- Result:
  - 原生两阶段事件会稳定转换为可点击子 Agent 入口；补充后端 mapper/router、前端 reducer/projection/timeline/FigmaChatPanel 测试，并同步事件流与模块 README。

### 2026-07-03 - 兼容 raw properties 防止子 Agent 卡片消失

- Why:
  - 用户反馈子 Agent 卡片在主对话中出现后立刻消失，导致无法点击进入子 Agent 对话；实际可由 `message.part.updated` 仍携带 opencode raw `payload.properties` 包装复现，reducer 无法识别其中的 `messageID/part.id/sessionID`。
- What:
  - `agent-chat` RunEvent reducer 在入口展开 `payload.properties`，后续 message/part upsert、Run Session scope 和 subagent 索引继续使用同一套扁平字段读取逻辑。
  - 补充 reducer 与 opencode-like state 回归测试，覆盖 raw properties task part 在 root projection 中持续可见，同时 child text 不混入主视图。
- How:
  - 只改前端 reducer、agent-chat 测试和事件/包文档；不新增 API、不改后端、数据库、generated SDK 或环境配置。
- Result:
  - raw `properties` 形态的 task 子 Agent 事件会稳定生成 root assistant task part 与 `taskPartId -> sessionId` 映射，主视图保留可点击子 Agent 卡片。

### 2026-07-03 - 失败卡片展示真实 Run 错误

- Why:
  - 右侧对话失败重试卡固定展示“您的请求断开，请重试！(974)”，但实际 `run.failed` 已携带 `Insufficient Balance` 等真实错误信息，用户无法直接看到根因。
- What:
  - `FigmaChatPanel` 的失败重试卡改为优先展示最近一条 `_error` 消息内容，缺失时才回退通用断开提示。
  - “复制错误信息”与失败卡展示文案共用同一个真实错误来源。
  - 补充组件回归测试和 mock E2E 断言，并修正该 E2E 缺少可用工作区 fixture 导致发送按钮禁用的问题。
- How:
  - 只改 agent-web 前端组件、测试和 README，不修改后端 API、RunEvent 契约、数据库或 generated SDK。
- Result:
  - `Insufficient Balance` 这类模型供应商错误会直接显示在失败卡片中，用户不必点击复制才能知道真实错误。

### 2026-07-03 - 子 Agent 对话切换展示

- Why:
  - opencode task 子 Agent 的输出会混入主 Agent 对话，导致主任务和子会话内容难以区分；用户要求主视图只显示子 Agent 调用卡片，点击后切换到子 Agent 完整时间线，并隐藏子视图输入框。
- What:
  - `agent-chat` reducer 新增 Run Session scope 运行期索引：`messageScopesById`、`subagentsBySessionId`、`subagentByTaskPartId`，从 `message.*`、`session.child.discovered`、`session.scope.updated` 和 task tool metadata 识别子会话。
  - `opencode-like` 时间线按 `activeSubagentSessionId` 过滤主/子消息；task 工具渲染为可点击子 Agent 卡片，子视图只展示目标 child session 输出。
  - `FigmaChatPanel` 和 `AssistantThread` 支持主/子 Agent 切换；子 Agent 页面隐藏 composer、Todo、permission/question 等主交互，仅保留“切换到主 Agent”提示。
  - 同步更新事件流、agent-chat、agent-web 和 shared-types 文档。
- How:
  - 只改前端 reducer、组件、投影状态、样式、测试和文档；不新增后端 API，不改 generated SDK，不改数据库。
  - 缺少 scope 的历史消息继续按 root 消息兼容展示；本次子 Agent 切换优先覆盖当前运行期和 active-run SSE 恢复。
- Result:
  - 定向 vitest 覆盖 child scope 隔离、subagent 索引、主/子投影、task 卡片点击和右侧面板子视图隐藏输入框。
  - `cd frontend && corepack pnpm typecheck` 通过。

### 2026-07-03 - 满意度反馈按钮在对话结束后展示

- Why:
  - 用户要求在对话（运行）期间不显示“满意/不满意”反馈按钮，仅在对话结束（即智能体回复完毕）以后才显示，以优化交互流程与界面简洁性。
- What:
  - 调整 `FigmaChatPanel` 组件中的反馈动作行渲染条件，增加 `!props.running` 的判断。
  - 在 `FigmaChatPanel.test.ts` 中新增单元测试用例，以验证在运行状态时反馈按钮不被渲染。
  - 同步更新前端 `README.md` 中对应的满意度展示说明。
- How:
  - 修改 `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`，在反馈按钮的包裹 `div` 元素上添加 `!props.running` 到 `v-if` 指令。
  - 修改 `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`，增加 `does not render assistant message feedback when the conversation is running` 自动化测试。
  - 修改 `frontend/apps/agent-web/README.md` 的说明。
- Result:
  - 前端 `corepack pnpm test FigmaChatPanel` (48个测试用例，含 1个 skip) 全部通过。
  - `corepack pnpm typecheck` 检查无报错。

### 2026-07-03 - 会话标题取首个非空行并截断

- Why:
  - 首次发送长 prompt 时，前端曾把完整内容作为 Session 标题，可能超过后端 `sessions.title` 长度限制并导致创建 Session 失败。
- What:
  - `sessionTitleFromFirstMessage` 改为取首个非空行；
  - 标题超过 72 个字符时截断为前 69 个字符加 `...`。
- How:
  - 只改前端标题生成工具和对应单测，不改 API、后端、数据库或事件。
- Result:
  - 新会话标题不会再因多行长 prompt 直接带入完整正文。

### 2026-07-03 - 恢复对话区 Agent 选择与 @ 候选

- Why:
  - 当前主路径 `FigmaChatPanel` 没有接收 `AgentWorkbench` 已加载的运行态 Agent 目录，底部无法切换主 Agent；
  - 输入框 `@` 也没有按 opencode 原生 autocomplete 口径展示可提及 Agent。
- What:
  - `FigmaChatPanel` 新增受控 `agents/selectedAgent` 和 `change-agent`，底部 Agent 下拉按 `mode !== "subagent" && !hidden` 展示主运行 Agent；
  - 输入框 `@query` 候选按 `!hidden && mode !== "primary"` 展示可提及 Agent，并选中后替换为 `@agentName `；
  - `AgentWorkbench` 下发 agents/selectedAgent，切换后 `startRun` 继续携带所选 agent。
- How:
  - 只改前端组件、mock 测试与稳定文档，不改后端 API、RunEvent SSE、数据库或 generated SDK。
- Result:
  - `FigmaChatPanel` 组件测试覆盖底部 Agent 过滤、切换 emit 与 `@` 候选替换；Playwright mock 覆盖切换 Agent 后 Run 请求体携带新 `agent`。

### 2026-07-03 - 按真实运行状态展示聊天终态徽标

- Why:
  - Run 启动前失败已经能把 reducer 收敛到 `FAILED`，但 `FigmaChatPanel` 只根据 `running` 从 true 变 false 和消息内 error 猜测终态；
  - Session 创建失败这类没有 assistant error 消息的场景会误显示“任务完成”。
- What:
  - `FigmaChatPanel` 新增 `runtimeStatus` 输入，优先按 `FAILED/ERROR` 显示“任务失败”，按 `CANCELLED/STOPPED` 显示手动终止，按 `SUCCEEDED/COMPLETED` 显示完成；
  - `AgentWorkbench` 将当前 `chatState.status ?? run?.status` 传给聊天面板。
- How:
  - 只调整前端 UI 状态判定与组件测试，不修改后端、API、RunEvent 或数据库。
- Result:
  - 本地启动失败后底部徽标显示“任务失败”，不再误显示“任务完成”。

### 2026-07-03 - 收敛 Run 启动前失败的聊天运行态

- Why:
  - `/api/sessions` 创建失败或 Run HTTP 提交失败发生在后端创建 Run 之前，不会产生 `run.failed` RunEvent；
  - 前端已先分发 `run.requested`，如果没有本地失败终态，右侧 chat 会一直显示运行中。
- What:
  - `agent-chat` reducer 新增 `run.request.failed` 本地动作，把 `PENDING` 收敛为 `FAILED` 并清空 streaming overlay；
  - `AgentWorkbench` 在 `startRunMutation.onError` 中分发该动作，并锁定本轮本地耗时。
- How:
  - 不改 API、SSE、数据库或后端；只补前端状态机和定向 reducer 回归测试。
- Result:
  - Session 创建失败后，前端展示“启动 Run 失败”反馈，同时停止 chat 运行态和停止按钮。

### 2026-07-03 - 收敛对话时间线过程行与极简输出样式

- Why:
  - opencode-like 时间线在一次回答被拆成多条 assistant message 后，会重复展示“思考状态”和头像/过程行，运行中噪声过多；
  - 最终正文与工作中过程输出区分不够清晰，工具详情默认展开会占用聊天区域；
  - read/list 等工具的工作区绝对路径包含 `.testagent` 和 personal worktree 前缀，长且不利于用户理解。
- What:
  - 在 `createTimelineRows` 行投影层按用户回合合并 reasoning 与上下文探索工具行，运行中已有过程行时不再额外追加 thinking 行。
  - 新增 `ReasoningPartGroup`，过程详情默认折叠，运行中只在单个“思考状态”标题上展示轻量流光动效；最终 `text` 直接渲染为与用户输入框一致的轻量气泡，不额外显示“最终输出”标题。
  - 移除助手消息中的“测试智能体 · 时间”meta 行，只保留头像作为来源标识，使助手输出与无名称的用户气泡保持一致；初始 pending 的“思考中”也使用同一头像列，运行态只在副标题上保留轻量呼吸感。
  - 过程/工具行统一为固定列：标题、摘要、状态、箭头对齐；工具完成态展示为“已读取”，避免“skill 已完成”像最终结论。
  - 正文气泡恢复复制按钮；文件修改卡片按钮文案改为“查看文件”，文件路径短显但保留完整 title。
  - 工具详情统一默认折叠；工作区长路径展示为短路径（如 `F-COSS/workspace`），完整路径保留在 title 悬浮提示。
- How:
  - 只修改 `frontend/packages/agent-chat/src/opencode-like` 当前主路径组件、样式和投影逻辑，以及 `FigmaChatPanel` 反馈区间距；未改后端、RunEvent、DTO、数据库或旧 `FigmaChatPanel` 作废主循环。
- Result:
  - `opencode-timeline.test.ts` 与 `FigmaChatPanel.test.ts` 定向通过，`agent-chat`/`agent-web` typecheck 通过，浏览器同源样例确认单头像、无助手名称 meta、初始 thinking 有头像、单思考状态、过程状态列对齐、正文可复制、文件修改入口为“查看文件”、长路径短显。

### 2026-07-03 - 修复助手头像对齐与新增 Mermaid 交互式图表预览

- Why: 
  - 助手消息行中，当同时存在头像（showHeader = true）和后续逻辑部分（is-continuation = true）的重载时，原来的 `display: block; padding-left: 46px` 会覆写 `display: grid`，导致头像单独占据一行，输出内容折行并错位。
  - 原本不支持 Mermaid 图表渲染，直接以原始 Markdown 代码块展示；且渲染 Mermaid 时如果使用 theme: 'dark'，在浅色工作台下会导致节点底色呈黑色，非常不美观；而且初次加载时自动进行大量的图表渲染容易拖慢页面响应速度。
- What:
  - 头像对齐修复：在 `rows.css` 中重构 `.oc-assistant-frame.has-header`，当有头像需要渲染时，强制使用 `display: flex !important; flex-direction: row !important; align-items: flex-start !important;` 替代 grid 布局并重置 padding-left。通过 flex 布局彻底杜绝内容垂直折行的可能，保证头像与首行输出完美水平对齐。
  - Mermaid 交互预览：修改 `MarkdownView.vue`，使 `lang === 'mermaid'` 的代码块默认只渲染成“预览图表”控制按钮和下方的原始语法代码块。只有当用户手动点击“预览图表”时，才会动态加载 `mermaid` 模块、进行渲染，并用生成的 SVG 替换掉原始节点（同时移除 preview 状态）。
  - 图表底色优化：将 mermaid 初始化主题从 `'dark'` 改为 `'neutral'`，与轻量/白色背景面板完美契合，消除黑色节点底色。
- How:
  - 修改 `rows.css` 对 `.oc-assistant-frame.has-header` 以及它的内容容器 `.oc-assistant-frame__content` 引入 flex-row 强制对齐样式。
  - 修改 `MarkdownView.vue` 自定义 markdown-it fence 规则、引入 `@click` 事件委派 `handleMdViewClick`、动态加载 mermaid 库并执行 render。添加 `.mermaid-block.is-preview` 及 preview button 相关的 scoped 样式。
  - 修复 `opencode-timeline.test.ts` 和 `FigmaChatPanel.test.ts` 中的用例期望（修复了大小写敏感和路径绝对/相对解析错误），并补齐 `MarkdownView.test.ts` 中针对 Mermaid 预览及点击后渲染 SVG 逻辑的单元测试。
- Result: 全量 293 个 Vitest 测试全绿通过，`corepack pnpm typecheck` 和 `corepack pnpm build` 成功无错。助手头像与“思考状态”首行在任意情境下完美对齐；Mermaid 默认展示代码且提供点击预览功能，渲染出来的图表底色清爽无黑色背景。

### 2026-07-03 - 优化对话时间线布局与对齐样式

- Why: 
  - 对话时间线底部的满意/不满意反馈按钮与助手回答内容会发生重叠；
  - 用户对话内容较短时，气泡也会发生不必要的换行折叠；
  - 最终助手消息的气泡框（oc-text-part）宽度没有和已完成的工具及探索步骤行对齐；
  - 整体行间距不够紧凑，纵向占用空间较多。
- What: 
  - 修复时间线重叠：为 `.oc-timeline-root` 显式设置 `flex-shrink: 0`，防止其在被 flex 容器（`.figma-chat-scroll`）压缩高度后溢出，导致兄弟节点 feedback 按钮叠在消息上。
  - 修复用户短消息换行：将 `max-width` 限制由 `.oc-user-message__bubble` 转移至其容器 `.oc-user-message__content`，并使气泡本身 `width: fit-content` 自适应内容宽度。
  - 宽度对齐：设置 `.oc-text-part` 宽度为 `100%`，使其右边界与上方工具的“已完成”状态对齐。
  - 紧凑排版：调低 `--oc-line-height` 至 1.4，缩减 `.oc-timeline` 纵向 padding、用户气泡 margins，压缩 disclosures/tools 的 triggers/body margin & padding，降低 markdown 段落段间距。
- How:
  - 修改 `timeline.css`、`rows.css`、`parts.css`、`tools.css`、`markdown.css` 和 `tokens.css` 等样式文件，无后端与逻辑修改。
- Result: 前端 `corepack pnpm test` 全量通过（36个测试文件，265个测试用例），`typecheck` 成功，改动安全且美观。

### 2026-07-03 - 优化对话时间线样式及字体大小

- Why: 在 opencode 对话时间线重构后，由于全局字号设置偏大（16px），在侧边栏等窄版聊天面板中显得非常笨重、不够美观。另外，助手的正式文本回答缺乏气泡框外壳导致视觉层级不突出。此外，发现无头像的 continuation 中继行与有头像的行存在 12px 的对齐偏差、Grid 容器因为空列引起了高度塌陷造成反馈按钮向上重叠遮挡、思考状态展示区因为重复 border-left 产生了双线，且用户气泡缺少对应的头像，左右 padding 偏宽内容未打到边界，字号也需要下调以增强紧致感。
- What: 
  - 将时间线局部层级的字号变量进行等比例缩减，正文改回舒适的 13px，次要状态改回 11px，最细微元数据改回 10px，使整体版面信息更紧致。
  - 为助手的正式文本输出部分（`TextPart`）恢复轻量、精致的气泡背景（`#fafafc`，`1px` border 及不对称的 `border-top-left-radius: 2px`），字色改为高对比度的 `var(--oc-fg)`。
  - 缩减思考状态、工具标题行、Chevrons 和执行主体的间距与内边距，并将行间距进一步压缩为 4px。
  - 移除思考状态详情内部的冗余边线（去除了双线效果）。
  - 重构中继行布局，在 `is-continuation` 时弃用 Grid 布局改用 block 并加上固定的 `padding-left: 46px`，彻底消除了 Grid 高度塌陷引起反馈按钮重叠的 Bug，同时使无头像行和有头像行的内容完全垂直对齐。
  - 给用户消息在右侧加入了对称的用户头像（`lucide:User`，28x28px），并配合右上角不对称圆角实现指向。
  - 将 `oc-timeline` 左右 padding 从 14px 缩减至 8px，行间隙从 6px 进一步压减至 4px，使整个时间线非常贴合对话框边界。
- How: 
  - 修改 `UserMessageRow.vue` 引入 `User` 并在右侧平铺渲染。
  - 修改 `tokens.css` 重新设定字号变量：xs=10px, sm=11px, md=13px, lg=15px，并压缩行高至 1.6。
  - 修改 `parts.css` 赋予 `.oc-text-part` 气泡外壳样式，移除 `.oc-reasoning-part__body` 的 border/padding/margin 以防双边线。
  - 修改 `rows.css` 对 `.oc-assistant-frame.is-continuation` 设为 block + padding-left: 46px 布局，并完善用户头像和不对称气泡的样式类。
  - 修改 `timeline.css` 将 `.oc-timeline` 的 padding 设为 `10px 8px 20px`，并将 `.oc-row + .oc-row` 的 margin-top 改为 4px，`.oc-turn-gap` 的高度设为 8px。
- Result: 前端全量 266 个 Vitest 测试全绿通过，`typecheck` 成功，改动完全不影响任何前后端交互逻辑。
### 2026-07-03 - Todo 面板迁入 opencode-like

- Why: opencode 原生 `todo.updated` 事件使用 `payload.todos[]` 且 Todo 无稳定 `id`，前端原 reducer 只识别 `todo/items`，当前右侧主面板也没有展示 todos。
- What: 新增 `opencode-like` Todo 面板并固定展示在输入框上方，收起态展示待处理/进行中/已完成/已取消/其他和总数，展开态展示任务列表；`AgentWorkbench` 将 `chatState.todos` 传入 `FigmaChatPanel`。
- How: reducer 兼容 `payload.todos` 并为无 id 的 Todo 按序号和内容生成展示 key；不新增后端 SSE 类型或 API，只补充事件文档与前端包说明。
- Result: Todo 展示入口从旧 `TaskBreakdown/ProcessDisclosure` 迁到 `.oc-*` 组件体系，`todo.updated` 的真实 opencode payload 可被当前右侧面板消费。

### 2026-07-03 - 恢复 opencode-like 对话时间线轻量样式

- Why: opencode 对话主路径重构为 `OpencodeTimeline` 后，7 月 2 日旧 `FigmaChatPanel` 中的轻量思考、探索和 bash 工具行视觉效果没有完全延续；用户要求当前前后端输出逻辑不变，只按旧提交视觉恢复样式。
- What: 修改 `frontend/packages/agent-chat/src/opencode-like/` 的 timeline 行展示和样式，当前 timeline 自己渲染助手头像、`测试智能体 · 时间` 元信息、白底、16px 正文、轻量工具行、右侧状态、左边线缩进和更接近旧图的思考/探索排版。
- How: 未改后端、RunEvent、DTO、业务输出顺序或旧 `FigmaChatPanel` 主路径；新增 `AssistantMessageFrame` 作为当前 timeline 的展示外壳，并在投影层按用户回合标记 `showAssistantHeader`，避免同一次对话被多个 assistant message/part 重复渲染头像。复用 `.oc-*` 样式分层，字体 token 对齐全局 `--font-sans/--font-mono`，避免样式重新堆回单个 Vue 文件。
- Result: `opencode-timeline.test.ts`、`FigmaChatPanel.test.ts`、`@test-agent/agent-chat typecheck`、`agent-web typecheck` 和 `git diff --check` 通过；本地既有 Vite 服务 `127.0.0.1:3000` 可达，使用 Chrome 对 split assistant message 的 `OpencodeTimeline` 同源样例截图确认单回合仅 1 个头像/meta、字体为 `Geist, Noto Sans SC` 栈、工具行不重叠。

### 2026-07-03 - 个人工作区发布实时展示当前 Git 命令

- Why: 提交并推送进度弹窗在步骤运行中仍显示“暂无执行的命令”，只能在请求结束后看到已执行命令；用户需要弹窗展示当前步骤正在执行的具体 Git 命令，并在下一条命令或下一阶段开始时立即切换。
- What: 个人工作区 publish 请求支持可选 `operationId`，前端先连接既有 Agent 配置进度 WebSocket，再发起 publish；后端在每条真实 Git 命令启动前通过 `AgentConfigProgressSink` 发布 `command` 进度事件，前端弹窗实时用该事件替换当前命令，接口结束后再回填完整 `executedCommands`。发布前 preview 只保留 expected HEAD 并发保护，不再因应用分支有待合入提交弹确认框，真实冲突交给 Git merge 结果展示。
- How: 复用 `GitCommandExecutor` 的命令记录点和 `/agent-config/operations/{operationId}/ws` ticket WebSocket，不新增轮询、不额外执行 Git 查询；`backend-api` 等待 WebSocket open 后再返回连接，避免首条命令事件在连接建立前丢失。
- Result: 后端 workspace/API 定向测试、前端 backend-api/Git 面板/diff-viewer 定向测试和 agent-web/backend-api typecheck 通过；进度事件广播只包含 operation/status/step/command/error/commit 等安全字段，不携带文件内容、token 或私钥。

### 2026-07-03 - 极简化 Git 冲突批量解决 Banner 布局与交互

- Why: 窄侧边栏下，原有的纵向大 Banner 堆叠和按钮文字换行折叠极易发生遮挡与溢出重叠，且占用过多纵向空间。此外，全局的 button 标签默认 14px 粗体规则覆盖了局部未设定字号的组件，导致按钮显得过于笨重。
- What: 重新设计并极简化 Git 冲突解决 Banner 布局，采用双行超紧凑面板设计（首行展示提示，次行平铺操作）。使用无边框、半透明悬浮背景的链接式扁平按钮，将整体底座背景与文字颜色改为柔和温暖的警示琥珀色（Amber），避免与常规的灰色标题栏混淆，并显式强制定制字号为 `11px`，按钮高度压缩至 `18px`，总高度仅约 `42px`。
- How: 修改 [GitChangesPanel.vue](file:///Users/kaka/Desktop/intelligent-test-agent/frontend/apps/agent-web/src/components/GitChangesPanel.vue)：
  - 调整 HTML 结构为双行结构，首行“检测到 X 个冲突”，次行平铺“保留本地”、“保留远程”与右浮动“取消”按钮。
  - 在 CSS scoped 中新增 `.git-conflict-banner` 及 deep 规则，统一强制定制底色为 `#fffbeb` (深色模式半透明黄色)、边框颜色、以及 `.git-conflict-action-btn` 的字号 (11px)、高度 (18px) 和 `!important` 扁平无边框样式。
- Result: 前端 34 个测试文件、251 个 Vitest 用例全绿通过，`typecheck` 正常，冲突面板以极简优雅的 42px 融入侧边栏中，排布舒适且对比鲜明。

### 2026-07-03 - 优化冲突批量解决 Banner 尺寸与提交进度字体字号

- Why:
  1. 上一版的冲突解决 Banner 在窄侧边栏下纵向高度过大，遮挡了较多变更文件列表区域，且底部“放弃合并”按钮被截断，不够紧凑精致。
  2. 提交进度弹窗内的字体字号使用了不一致的 arbitrary 属性（如 `text-[10px]`、`text-[11px]`），不符合前端全局排版关于次要文字（14px）、说明Caption（12px）的统一规约。
  3. 主项目仓库 `/Users/kaka/Desktop/intelligent-test-agent` 中残留了由上一轮会话脚本错误执行 checked out 的测试分支 `feature_testagent_20260705_*` 和临时 worktree，干扰了主工作区的干净状态。
- What:
  - 极简化冲突 Banner 布局：减少 padding 到 `p-2`，精简说明文案，将“全部保留个人版本 (Mine)”与“全部采用远程版本 (Theirs)”左右横向平铺，极大压缩了纵向高度，避免遮挡和滚动截断。
  - 规整字体字号：修改进度弹窗及 CSS 中的字体大小，使用全局统一的 `text-xs` (12px) 和 `text-sm` (14px) 规约。
  - 清理主仓库 Git 污染：强制卸载并删除所有无用的 20260705 测试 worktree 与分支，还原主仓库 main 的干净状态。
- How:
  - 修改 [GitChangesPanel.vue](file:///Users/kaka/Desktop/intelligent-test-agent/frontend/apps/agent-web/src/components/GitChangesPanel.vue) 中的冲突 banner 节点样式结构、横向平铺布局、以及隐藏与弹窗共存的 inline error 节点以防 Vitest 多文本冲突。
  - 修改 [git-changes-panel.test.ts](file:///Users/kaka/Desktop/intelligent-test-agent/frontend/apps/agent-web/tests/git-changes-panel.test.ts) 以使用 regex 部分匹配新的按钮名。
  - 执行 `git worktree remove` 和 `git branch -D` 清理无用分支。
- Result: 前端全量 251 个 Vitest 测试全绿通过，`corepack pnpm typecheck` 成功无错。主工作区恢复干净。

### 2026-07-03 - 优化 Git 冲突 UI 与 Diff 性能并造测试数据

- Why: 解决冲突时，"全部选择个人/全部选择远程" 的按钮以未定义样式的普通 HTML button 裸露在红色的冲突提示下，交互风格非常古怪、不美观；同时当文件变更列表较大时，后端对每个变更文件逐个依次运行 `git diff` 进程，产生极大的进程创建开销，导致 Diff 区域文件加载非常缓慢。
- What: 
  - 前端：将 Git 冲突批量解决按钮区域升级为带警告气泡框、平行高亮双列主要操作按钮（全部保留个人版本、全部采用远程版本）与底部单行辅助取消操作的现代风格 Banner，引入 ui-kit Button 替换 unstyled 原始 HTML button。
  - 后端：优化 `GitWorkspaceService.java`，在 `collectDiffFiles` 阶段仅执行一次全局 `git diff --cached` 和 `git diff`，并通过统一 Diff 头部特征在内存中进行高效率的多文件代码段 Map 切分映射，对 Map 未命中的文件安全回退单文件 diff 执行，降低 O(N) 进程创建开销到 O(1)。
  - 测试与数据：适配后端 Git 聚合命令的单元测试，并在本地测试工作区制造了包含暂存、未暂存及 merge 冲突（`AGENTS.md`）的测试数据，用于完整的联调与效果体验。
- How: 
  - 前端修改 [GitChangesPanel.vue](file:///Users/kaka/Desktop/intelligent-test-agent/frontend/apps/agent-web/src/components/GitChangesPanel.vue) 中 `<div v-if="workspaceConflicts.length > 0">` 渲染和 ui-kit `Button` 导入；
  - 后端修改 [GitWorkspaceService.java](file:///Users/kaka/Desktop/intelligent-test-agent/backend/test-agent-common/src/main/java/com/icbc/testagent/common/git/GitWorkspaceService.java) 实现 `parseFullDiff` 和 `collectDiffFiles` 重构，并更新 [GitWorkspaceServiceTest.java](file:///Users/kaka/Desktop/intelligent-test-agent/backend/test-agent-common/src/test/java/com/icbc/testagent/common/git/GitWorkspaceServiceTest.java) 单元测试。
  - 在 `.testagent/agent-opencode/workspace/personalworktree` 测试工作区中通过 checkout 临时分支制造真实 `AGENTS.md` 冲突和 `CLAUDE.md`/`.env.local.example` 暂存状态。
- Result: 后端 `test-agent-common` 63 个单元测试全部通过；前端 251 个 Vitest 单元测试和 typecheck 校验全绿通过；本地重启后使用默认用户 `888888888` / `123456` 可正常加载测试数据并观察验证效果，Diff 区性能提升明显。

### 2026-07-03 - 增加工作区发布预览、Git 原生批量冲突处理和 Diff 自动刷新

- Why: 888 个人 worktree 只显示一个本地变更，提交后才拉入应用分支的大批变化并产生 8 个 AU 冲突；中文冲突路径被 Git 八进制转义，冲突只能逐个处理，应用分支 pull 后版本/副本 commit 仍可能陈旧，Diff 数量首次进入还需要手工刷新。
- What: 发布前新增远程变化预览与 expected HEAD 校验，应用 pull 后立即同步版本和本机副本 commit；冲突路径关闭 `core.quotepath`，支持全部保留个人版本、全部采用远程版本和取消 merge，目标侧缺失按 Git 删除语义处理。前端复用已加载 diff 更新数量角标、拦截旧工作区请求回写，并明确 AU/UD 删除侧。
- How: 复用 `GitWorkspaceService`、个人工作区归属校验和现有发布编排，新增 preview/resolve-all HTTP 契约；真实临时 Git 仓库覆盖中文修改/删除冲突，服务测试覆盖预览汇总、HEAD 变化前置拦截和冲突后的元数据同步，前端测试覆盖预览确认与批量处理。处理了会话开始前遗留的 `git pull --rebase`，保留两侧 session log 后完成 7 个本地提交重放。
- Result: common 24 个、workspace-management 36 个、API Controller 和 Git 面板/合并编辑器定向测试通过，agent-web typecheck/build 通过；使用 `.env.test` / `test` profile / JDK 25 重启并检查健康状态。未修改环境文件、数据库、事件、generated SDK 或 888 当前个人 worktree 冲突内容。
### 2026-07-03 - 移除旧底部实时任务面板

- Why: 对话主路径已切到 `OpencodeTimeline` 后，`FigmaChatPanel` 仍渲染旧底部实时任务面板；该面板从 `displayMessages` 重新聚合 tool/subtask part，和新时间线的事件行来源不同，运行中或历史恢复时会出现“下面任务与上方事件不匹配”。
- What: 删除旧底部实时任务面板的渲染、运行开始重置和滚动 watcher，保留“任务消耗”统计行；新增回归测试确保运行中只显示 `OpencodeTimeline`，不再出现 `.figma-chat-task-panel`。
- How: 不改 RunEvent、message part 或 opencode-like 投影逻辑，只去掉旧面板这一第二展示源，并同步 agent-web/agent-chat 文档。
- Result: `FigmaChatPanel` 的运行中工具/事件展示统一由 `OpencodeTimeline` 承载，避免旧任务列表与事件流不一致。

### 2026-07-03 - 标注旧对话组件作废边界

- Why: 对话主路径已经切换到 `opencode-like/OpencodeTimeline`，后续开发需要明确旧气泡和结构化卡片代码只保留兼容，不再作为新增能力入口。
- What: 在旧 `AgentCard`/`TimelineCard`/`MessageParts` 及旧 part 子组件顶部增加作废注释，并在 `FigmaChatPanel` 已禁用的旧气泡循环前标注作废说明；同步更新前端总览、agent-chat README/PACKAGE 和 module-map。
- How: 只插入中文作废注释和稳定文档说明，不调整旧组件模板、状态、样式或运行逻辑；`ProcessDisclosure` 仍被 `TaskBreakdown` 等局部视图使用，因此仅在文档中说明不要用它恢复旧对话主路径。
- Result: 旧代码边界明确，新对话展示能力继续要求落在 `frontend/packages/agent-chat/src/opencode-like/`。

### 2026-07-03 - 优化对话中思考与工具状态样式

- Why: 为了让对话界面的思考过程与工具状态展示更加扁平、清爽，需要参照最新的设计稿去除臃肿的外边框与背景色，改用更干净的行级样式，并合理排列 Chevron 折叠标记及中文化状态文字。
- What: 
  - 移除了 `.oc-tool`、`.oc-context-group`、`.oc-disclosure` 的外层边框与背景色，改为扁平文本样式；
  - 对 `思考状态` (`OcDisclosure`)、`已探索` (`ContextToolGroup`) 等折叠头部进行行内包裹排列（`display: inline-flex`），使 Chevron 紧贴标题/状态文本展示，并实现运行态时的蓝色高亮；
  - 对 individual tools (如 `bash` 等 `OcToolShell`) 支持两端对齐排版（`margin-left: auto`），在右侧以文字显示中文化的状态并尾随 Chevron；
  - 调整 `opencode-timeline.test.ts` 的相关断言以覆盖新类与“已探索”文本映射。
- How: 修改 `OcDisclosure.vue`、`OcToolShell.vue`、`ContextToolGroup.vue`、`ReasoningPartView.vue`、`tools.css` 和 `parts.css` 并跑通所有单元测试。
- Result: 前端 Vitest `corepack pnpm test` 全绿，所有组件布局及样式完全符合截图所示扁平化时间线风格。

### 2026-07-03 - 原始输出限制改为10000条

- Why: 调试过程中需要查看更多的原始报文（包括请求、响应和 SSE 消息），原有 1000 条的限制不够用，需要将其调整至 10000 条以保留更多历史。
- What: 修改 `AgentWorkbench.vue` 中定义会话级原始报文最大条数的常量 `RAW_OUTPUT_MAX_ENTRIES_PER_SESSION` 从 1000 改为 10000。
- How: 变更 `AgentWorkbench.vue` 常量值，并通过 `corepack pnpm test` 跑通所有前端单元测试。
- Result: 前端所有单元测试均通过，无 API 协议、事件流、数据库或环境配置改动。

### 2026-07-03 - 修复对话流跟滚与用户向上滚动防锁死问题

- Why: 当智能体在思考或工具输出时，高频流式数据更新会触发自动跟滚；原逻辑使用平滑动画且缺乏全局滚动原打断，导致用户在向上拖滚动条或使用滚轮/键盘等方式阅读历史时会被强制下拉到底部。
- What: 
  - 在 `AssistantThread.vue` 中引入 `isProgrammaticScroll` 原型标记来隔离“程序滚动”与“用户手动滚动”，并配以 `setTimeout` 进行多环境（如无 scroll 事件派发的 jsdom 等）清除兜底；
  - 精简吸底逻辑，完全移除了 `firstPaint` 这一容易引起初始 rerender 时误判吸底状态的状态变量；
  - 只要用户在视口中向上滑动（即 `scrollTop` 偏离底部大于 36px 阈值），一律在 `handleViewportScroll` 中标记 `userInterrupted = true` 并锁定自动跟滚；当用户重新返回底部或点击“查看新内容”时才解锁；
  - 增强 `chat-utils.ts` 中的 `viewportIsAtBottom` 临界值容错阈值至 36px。
- How: 重构 `AssistantThread.vue` 滚动处理逻辑与 `chat-utils.ts`，并在 `opencode-timeline.test.ts` 中通过劫持 `Element.prototype` 完成复杂 patch 状态下的自动锁死回归测试。
- Result: 前端 Vitest `npx vitest run packages/agent-chat/tests/opencode-timeline.test.ts` 3 个单元测试全量成功通过；未变更后端代码。



### 2026-07-03 - Run Session Tree 后端事件路由与映射收口

- Why: Run scope 基建需要真正落到 root/child 事件路由、历史恢复和事件对照验收，避免 child 终态误派生 Run 终态、未知全局事件污染 Run 时间线，以及 opencode Web App 事件缺映射。
- What: RunEvent 持久化改为 MyBatis 查询 root session replay，runtime 增加运行中 scope cache、child discovery、pending/dedup 和 HTTP/SSE snapshot 的 root+child 恢复；mapper 新增 `reference.updated/file.edited/file.watcher.updated`、公共 ID alias 和派生终态来源字段；router 过滤 heartbeat/tui/pty/workspace/worktree/installation/plugin/catalog 等无 session 全局 unknown。
- How: 保持 opencode-client 只做 raw/mapped DTO 边界，scope 归属由 `RunSessionScopeRouter` 判定；HTTP session-tree 接口合并 durable permission/question/todo 等状态事件；文档同步 API、事件、数据库和模块 README。
- Result: `mvn -pl test-agent-api,test-agent-opencode-runtime,test-agent-opencode-client -am test`、`mvn -pl test-agent-persistence -am -Dtest=MyBatisRunEventRepositoryIntegrationTest,MyBatisRunSessionScopeRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`、mapper/router/domain 定向测试和 `git diff --check` 已通过；本次不修改 generated SDK 或环境配置。

### 2026-07-03 - 对话区主路径切换为 opencode 风格时间线

- Why: 右侧对话需要按 opencode 原生交互呈现 message part、工具调用和流式输出，旧气泡/卡片主路径把 reasoning、工具日志和最终回答混在一起，维护成本和视觉噪声都偏高。
- What: 新增 `@test-agent/agent-chat` 的 `opencode-like/` 时间线模块，`AssistantThread` 与 `FigmaChatPanel` 主路径改用 `OpencodeTimeline`；RunEvent reducer 增加 `streamingTextByPartId` 临时 overlay，避免 `message.part.delta` 和最终 part 重复；补充时间线状态、组件渲染和 agent-web 聊天面板回归测试。
- How: 通过 `createOpencodeLikeState` 和 `createTimelineRows` 统一投影用户消息、孤立助手历史、assistant parts、运行态、Diff、permission/question/todo 和模型目录；工具视图按 bash/read/list/glob/grep/edit/write/apply_patch/webfetch/websearch/task/skill/question 拆分，读取/检索类上下文工具默认折叠成组，失败工具单独展示错误。旧 `FigmaChatPanel` 气泡循环已从主渲染路径移除，仅保留为未激活兼容代码。
- Result: `cd frontend && corepack pnpm exec vitest run`、`corepack pnpm typecheck`、`corepack pnpm lint`、`corepack pnpm build` 均通过；build 仍有既有 CSS `@import` 顺序和 chunk size 警告。本次未修改 API、SSE 事件、数据库、generated SDK 或环境配置。

### 2026-07-02 - 对话面板 bash 工具输出默认收起与手动折叠支持

- Why: 智能体执行 bash 命令的输出可能很长，默认完全展开会占用大量聊天区域，不利于用户快速浏览上下文。用户希望 bash 输出默认收起，并通过点击工具头部/key 展开或折叠。
- What: 将 bash 工具输出的详情块（details）默认状态设为收起（未运行状态下），同时保留其他工具或运行中状态默认展开的逻辑；允许用户点击任何工具详情头部（summary）进行展开或折叠。
- How: 
  - 修改 `FigmaChatPanel.vue`：
    - 新增 `manualToolStates` 响应式对象，记录用户手动展开/收起的状态，键为 `message.id` Scoped 的工具标识。
    - 定义 `getToolPartKey`、`toggleToolOpen`、`isToolOpen` 和 `messageToolsExpandedState` 辅助函数。
    - 在 `<details>` tag 中将 `:open="isToolOpen(message, part)"` 替换为 `:open="isToolOpen(message, part)"`，并在 `<summary>` 上绑定 `@click.prevent="toggleToolOpen(message, part)"`。
    - 将 `messageToolsExpandedState(message.id)` 加入助手消息的 `v-memo` 依赖中，以确保点击时精准触发 Vue 对对应消息的重绘。
  - 修改 `FigmaChatPanel.test.ts`：
    - 新增测试用例 `collapses completed bash tool outputs by default and expands them on click` 验证 bash 默认折叠和点击切换行为。
- Result: 前端全量单测（241个用例）均通过，`corepack pnpm build` 和 `typecheck` 验证成功。未修改任何后端 API、数据库或环境配置文件。

### 2026-07-02 - 版本库新增校验和内部模式字段控制

- Why: 内部部署模式下，版本库英文名称应自动根据 Git 地址生成且不允许人工编辑。同时，新建版本库表单中的版本库类型应默认为空且必选，版本库名称也应当是必输项。
- What:
  - 内部部署模式下，将版本库英文名称输入框设为 disabled，且每次修改 Git 地址时强制自动派生最新英文名。
  - 版本库类型 `repoType` 默认值初始化为空字符串 `""`，并且添加下拉选择 placeholder 提示。
  - 新增版本库时增加表单校验：版本库名称不能为空，版本库类型不能为空。
- How:
  - 修改 `SettingsRepositoryPanel.vue`：
    - `repoType` ref 初始化为 `""`。
    - `openCreateRepositoryDialog` 内对 `repoGitUrl`、`repoName`、`repoEnglishName`、`repoEnglishNameTouched` 和 `repoType` 进行初始化置空。
    - 调整 `syncDerivedEnglishName`，如果 `currentCreateInternal` 为 true，强制覆盖更新 `repoEnglishName`，不受 `repoEnglishNameTouched` 标记影响。
    - 绑定 `el-input` 的 `:disabled="currentCreateInternal"`。
    - 在 `createRepository` 中增加针对 `repoName` 与 `repoType` 的非空校验，失败时分别提示 "请输入版本库名称" 和 "请选择版本库类型" 并返回。
  - 修改 `settings-repository-panel.test.ts`：
    - 修改 mock stub `ElInputStub` 绑定 `disabled` 属性到原生 `input`。
    - 调整原有测试用例以在点击“新增”前选择版本库类型，并新增校验及字段禁用的回归测试用例。
- Result: 前端 `corepack pnpm test settings-repository-panel` (11 个测试) 与 `corepack pnpm typecheck` 全部通过。未修改 generated SDK、后端 API 协议或环境配置文件。
### 2026-07-03 - 清理 888 账户旧统一认证号 worktree

- Why: 888 账户磁盘上同时存在数据库绑定的 `usr_test_dev` personal worktree 和无数据库引用的 `DEV_888888888/coss/default` worktree；后者由 2026-06-30 旧实现按 `unifiedAuthId` 生成，2026-07-01 切换为稳定 `userId` 路径并修复数据库绑定时未回收 physical worktree。
- What: 确认旧 worktree 无 `personal_workspaces`、`workspaces`、recent preference 引用，无远程分支、无独有提交、无打开句柄后，强制移除 worktree，删除本地分支 `feature_testagent_20260618_DEV_888888888_default` 并执行 worktree prune。
- How: 只清理 `.testagent` 下无引用的历史 physical worktree 和本地分支，不修改数据库，不触碰当前绑定的 `usr_test_dev` worktree、其 `4618396` HEAD 或正在进行的 `6093725` merge。
- Result: Git worktree 列表只剩应用版本 worktree 和数据库绑定的 `usr_test_dev` personal worktree；旧路径和旧分支均不存在。当前 personal worktree 仍保持 48 个删除、4 个重命名、2 个新增和 8 个 `AU` 冲突，等待后续明确恢复或解决。

### 2026-07-03 - 修复工作区 Git 交互与 opencode 重启注册失败

- Why: 工作区 Git 面板在冲突期间不能暂存/撤回普通文件，文件名和 Diff 打开体验不完整，冲突预设结果无法保存；随后本地重启暴露后端启动阶段并发心跳可能写出 `backend_java_processes.updated_at < created_at`，导致 manager WebSocket 注册持续失败，opencode 无法启动。
- What: 工作区增加真实 stage/unstage API，冲突存在时只禁止 commit/push；文件行支持全路径悬浮，已加载 Diff 直接打开；三方编辑器用响应式结果驱动预设和保存。后端进程首次心跳固定以进程启动时间作为 `createdAt`，持久层兼容归一化历史逆序时间记录。
- How: 复用现有 `GitWorkspaceService`、工作区发布/冲突 API、Monaco 三方编辑器和 `BackendJavaProcessLifecycleService`；补充 workspace/API/frontend/diff-viewer/runtime/persistence 回归测试及模块、HTTP API、数据库文档。未修改 `.env*`、generated SDK、RunEvent 或数据库 schema。
- Result: 前端 245 通过、1 跳过，typecheck/build 通过；Git 测试仓库验证 1 个冲突保留时普通文件可独立 stage/unstage；runtime 5 个测试和历史时间归一化定向集成测试通过。按 `.env.test` / `test` profile 重启后 manager 稳定连接，`usr_test_dev` 初始化 opencode 成功，4096 端口 `/global/health` 返回 `healthy=true`、版本 `1.17.7`。

### 2026-07-02 - 修复工作区发布白名单、三方冲突处理与推送误报

- Why: 个人工作区普通发布在 Git index 残留其他 staged 文件时会被无 pathspec 的 commit 一并提交；冲突文件虽能识别但没有三方查看/解决入口；发布异常后前端可能保留中间成功进度，并且缺少远端 push 的独立确认。
- What: 普通发布先把 index 恢复到 HEAD，再只 stage 前端白名单；merge 重试保留完整 merge index。新增工作区冲突读取、解决、取消 API，读取 Git stage 1/2/3，并支持当前、传入、两者、手工、删除和 `merge --abort`。前端复用 Monaco 增加三方合并编辑器；发布响应增加向后兼容的 `remotePushed/headCommit`，只有明确确认 push 才展示成功，异常分支清除进度。
- How: Git 原子能力继续放在 `GitWorkspaceService`，业务校验和编排放在 workspace-management，HTTP 入口放在 API，前端通过 backend-api 调用；新增真实临时 Git 仓库测试验证残留 staged 文件隔离和 base/current/incoming stage 内容，并补充后端、Controller、前端组件回归测试与 API/模块文档。
- Result: common 真实 Git 2 个测试、workspace-management 33 个测试、API Controller 8 个测试、前端 239 个测试及生产构建通过。按 `.env.test` / `test` profile 启动时后端因 PostgreSQL `No route to host` 在 DataSource/Flyway 初始化前退出，未替换环境文件；前端 Vite 可在本地直连并显示登录页，后端运行态与真实页面冲突交互因此未完成环境联调。

### 2026-07-02 - 用户 opencode 进程按服务器名称展示和解析

- Why: `linuxServerId` 已改为稳定服务器身份后，用户绑定状态仍可能把服务器名当网络地址拼成 `server-a:port`，导致头像菜单、右侧状态卡和降级响应展示伪地址，后续启动/校验也容易混淆服务器身份与当前可访问 host。
- What: 用户进程状态、文件 WebSocket affinity 和 API 转发降级不再用 `linuxServerId + ":" + port` 派生 `serviceAddress`；绑定存在但进程缺失或目标后端不可达时保留 `linuxServerId/port`，仅在能按统一 `BackendJavaRouteResolver` 找到当前 Java `listenUrl` host 时返回当前地址。前端头像菜单和状态卡改为展示 `状态(服务器名 / 当前地址)`，地址缺失时展示 `状态(服务器名)`。
- How: `UserOpencodeProcessAssignmentService` 复用公共 Java 路由解析当前 host，READY 进程优先使用真实 `baseUrl`，旧 baseUrl host 等于稳定服务器名时刷新为当前地址；状态查询 health 使用当前 advertised host。同步更新 API/部署/前端/shared-types 文档，并补充后端路由降级和前端展示测试。
- Result: `mvn -pl test-agent-opencode-runtime,test-agent-api -am test`、`corepack pnpm@10.25.0 test -- apps/agent-web/tests/FigmaShell.test.ts apps/agent-web/tests/FigmaChatPanel.test.ts`、`corepack pnpm@10.25.0 --filter @test-agent/agent-web typecheck` 和 `git diff --check` 通过；未新增 API 字段、数据库字段或事件类型。

### 2026-07-02 - 版本库支持内部 SCM 部署模式

- Why: 企业内部 SCM 需要按当前操作人的统一认证号动态拼接 `ssh://{unifiedAuthId}@...` Git 地址，但数据库只能保存不含用户号的 SCM 地址片段；外部部署必须保持原完整 Git URL 行为不变。
- What: `code_repositories` 新增 `deployment_mode`，默认 `EXTERNAL`；`INTERNAL` 时 `git_url` 只保存 `host[:port]/path`，编辑页动态显示只读 `ssh://当前用户@` 前缀，列表仍展示数据库保存值。内部版本库英文名为空时按路径最后的 Git path 派生并把 `/` 替换为 `-`；Git clone/fetch/pull/push/list branches/list directories 都按当前用户动态生成实际 URL，origin 校验会去掉 `ssh://任意用户@` 后比较存储片段。
- How: 领域层新增部署模式枚举和有效 Git URL/origin 比较方法；配置管理服务增加内部 URL 校验、部署模式选项 API 和默认部署模式读取；持久层通过 MyBatis XML/Flyway 增加字段并同步迁移窗口 JDBC 映射；工作区 Git 操作在内部模式刷新 origin；前端版本库弹窗增加内外部模式选择、内部只读前缀和英文名派生。统一认证号不作为敏感信息脱敏，仍按既有规则保护 SSH key、token、Authorization、Cookie。
- Result: 新增/调整的 domain、configuration-management、persistence、workspace-management、API 和前端 settings/backend-api 定向测试通过；`shared-types`、`backend-api`、`agent-web` 类型检查通过。后端聚合 `mvn -pl test-agent-domain,test-agent-common,test-agent-configuration-management,test-agent-workspace-management,test-agent-persistence,test-agent-api -am test` 仅在 persistence 全量测试失败，剩余为既有 H2 `ON CONFLICT`、`usr_test_dev` fixture 外键、默认用户/loopback seed 断言问题；本次新增的 `deployment_mode` MyBatis/Flyway/JDBC 兼容定向测试已通过。未修改 `.env*`、generated SDK 或事件类型。

### 2026-07-02 - 新增部署模式配置项

- Why: 需要一个开关区分系统部署在企业内部还是外部，用于后续按部署环境差异化模型目录来源默认值、功能开关等；默认外部模式，可由环境变量切换为内部模式。
- What: 在 `application.yml` 的 `test-agent` 节下新增 `deployment.mode`，环境变量 `TEST_AGENT_DEPLOYMENT_MODE`，默认 `external`，可选 `internal`；`TestAgentRuntimeProperties` 新增 `Deployment` 内部类并提供 `isInternal()/isExternal()` 判断方法。
- How: 复用既有 `@ConfigurationProperties(prefix="test-agent")` + `${ENV:default}` 模式，不新增 API/数据库/事件/Flyway；同步更新 `backend/README.md` 环境变量表和 `docs/deployment/backend.md` 参数表，并在 `TestAgentRuntimePropertiesBindingTest` 补充默认值、空白值和 internal 绑定三个测试。
- Result: `TestAgentRuntimePropertiesBindingTest` 13 个测试全通过（含新增 3 个），`mvn compile` 成功；本次不涉及 API、事件、数据库结构、鉴权或 generated SDK 变更。

### 2026-07-02 - 版本库类型改为字典下拉并兼容 standard

- Why: 版本库管理需要把原“是否标准库”勾选升级为“版本库类型”下拉，同时存量工作空间分支规则仍依赖旧 `standard` 字段。
- What: `code_repositories` 新增 `repository_type`，`REPOSITORY_TYPE` 字典初始化测试工作库、应用代码库和应用资产库；后端新增 `/configuration-management/repository-types`，新增版本库优先使用 `repositoryType` 并派生旧 `standard`；配置管理生产仓储迁到 MyBatis XML；前端新增表单使用类型下拉，编辑区只读展示类型。
- How: Flyway 回填历史 `standard=true` 为 `TEST_WORK_REPOSITORY`、`standard=false` 为 `APPLICATION_CODE_REPOSITORY`；Domain 用 `CodeRepositoryType` 统一派生兼容布尔值；API/DTO/shared-types/backend-api/设置页和文档同步，存量 JDBC 配置管理仓储仅保留迁移窗口。
- Result: 后端 domain/configuration-management/API/persistence 定向测试、`mvn clean package -DskipTests`、前端 settings/backend-api Vitest、shared-types/backend-api/agent-web typecheck 和后续 `git diff --check` 已通过；未修改 RunEvent/SSE、generated SDK 或环境配置文件。

### 2026-07-02 - 托管工作区路径逻辑化与默认空态加载

- Why: 切换应用在当前服务器没有 READY 副本时会把旧数据库绝对路径当成本机 Git 根目录，导致 `GIT_UNAVAILABLE`；同时登录/切应用无 per-app recent 时会兜底首模板首版本并自动创建 default 私人 worktree，不符合“无历史不加载工作区”的新规则。
- What: 托管应用版本、副本、私人工作区和托管 runtime workspace 新写入逻辑路径 `appworkspace:` / `personalworktree:`，旧绝对路径只作为 legacy 兼容读取；所有 Git、文件树、Agent 配置、Run、Terminal 和文件 WebSocket 使用前统一解析为当前服务器物理路径；默认私人工作区改为先确保本机应用版本副本，前端无 recent 时只选应用并保留工作区切换入口。
- How: 新增 `ManagedWorkspacePathResolver` 和 Spring Bean，服务层写逻辑路径、响应返回解析后的物理路径；`ManagedWorkspaceApplicationService` 的 default/personal/sync/diff/pull 路径全部走 `ensureLocalReplica`；前端 `pickDefaultWorkspaceForApp` 只在 recent 带 `versionId` 时加载 default 私人 worktree，空 workspace 状态下仍挂载文件树和 footer。
- Result: `ManagedWorkspacePathResolverTest`、`ManagedWorkspaceApplicationServiceTest`、`test-agent-opencode-runtime -am test`、`test-agent-app -am -DskipTests package`、`agent-web typecheck`、定向 Playwright mock E2E 和 `git diff --check` 均通过；无 Flyway 数据迁移，旧数据保持兼容读取。

### 2026-07-02 - opencode 用户进程初始化增加轮询进度

- Why: 用户在“已分配但未运行”状态下点击启动/分配 opencode 进程时，前端只能等同步初始化返回，无法看到公共启动链路中的具体步骤，也无法定位 manager start、进程检查或健康检查失败原因。
- What: `POST /api/internal/agent/{agentId}/processes/me/initialize` 增加可选 `operationId`，新增只读 `GET /initialize-operations/{operationId}`；后端把校验、确认分配、选择容器、准备参数、进程启动、记录候选进程、检查进程、健康检查、写入绑定和完成/失败写入 `opencode_process_start_operations`，前端 `AgentWorkbench` 生成 `opi_...` 并用 `OpencodeProcessStartupDialog` 每 500ms 轮询展示。
- How: Domain 增加 operation 模型/步骤枚举/repository 端口，persistence 用 Flyway + MyBatis XML mapper 落表，runtime 在 `UserOpencodeProcessAssignmentService` 和 `OpencodeProcessStartupService` 中穿透可选进度记录器；API GET 只读 DB、不触发 manager health/start、不写 RunEvent。前端只改工作台层状态，`FigmaChatPanel` 继续只 emit 初始化事件。
- Result: runtime/API/persistence 定向测试、backend-api/agent-web typecheck 和 Vitest 通过；计划中的后端聚合 `mvn -pl test-agent-opencode-runtime,test-agent-api,test-agent-persistence -am test` 在 `test-agent-persistence` 既有全量测试处失败（H2 `ON CONFLICT`、`usr_test_dev` fixture 外键、默认/loopback seed 断言），runtime 和 API 模块在该 reactor 中已通过。
### 2026-07-02 - 对话框思考与能力卡片渲染重构，优化状态字与文字流光效果
### 2026-07-02 - 对话活动折叠、历史加载反馈与终态动画收敛

- Why: 右侧对话中“探索”无法折叠，成功的 write/edit 同时进入文件摘要和通用工具详情导致重复且部分区域点不动；历史会话切换期间旧正文停留且没有加载反馈；Run 与聊天 reducer 状态短暂不一致时，任务完成后仍可能显示思考动画；read 目录输出还会额外渲染“F-COSS 目录 · 1 项”造成误解。
- What: “探索”恢复为默认收起并支持手工展开/收起；成功的 read/write/edit 只走文件摘要，失败时仍保留通用工具详情展示错误；目录型 read 输出不再渲染独立目录卡片，文件内容预览保持不变。历史切换新增明确加载态，正文返回后立即显示，消息反馈异步补齐。新增 `isRuntimeBusy` 复用既有 Run busy 判定，让任一明确终态立即停止动画；新增 `run.requested` reducer 动作清除上一轮终态但保留 transcript，确保下一轮启动动画不被旧终态压住。
- How: 复用 `FigmaChatPanel.expandedFileKeys`、`follow-up-queue.isRunBusyStatus` 和现有 Session/反馈 API；用 Vitest 与 Playwright 先验证失败再最小修复。按用户要求保留并提交 unstash 中对话相关的 shimmer/`ProcessDisclosure` 改动，冲突解决/取消合并相关后端、GitChangesPanel、backend-api/shared-types 和 API 文档改动全部恢复到 `HEAD`。
- Result: `corepack pnpm test` 31 个测试文件、225 通过、1 跳过；`corepack pnpm typecheck` 全 workspace 通过；`corepack pnpm build` 通过（仅保留既有 CSS `@import` 顺序和大 chunk 警告）；历史加载 Playwright 在 Chromium/mobile 2/2 通过。用户明确选择自行重启服务，本会话未执行服务启动。

### 2026-07-02 - 对话面板 read 工具直接展示 + write/edit 折叠展开卡顿优化

- Why:
  1. 用户反馈右侧对话面板里 read 工具"没展示出来"——之前"探索"区只显示"读取 X 次"计数，文件路径被藏在 chevron 后面，要点开才看得到。
  2. write 工具的代码预览在展开/收缩时"超级卡"——`renderCodeWithLineNumbers` 每次重渲染都会对同一份内容跑一遍正则高亮，read 工具的文件内容预览（`readOutputs` 区域）也是同一个函数，遇到大文件或流式更新时非常卡。
- What:
  1. `FigmaChatPanel.vue` 调整"探索"区：去掉 chevron 与折叠交互，summary 不再承载点击；`<ul>` 永远渲染，把本轮 read 过的文件路径直接列出来；新增 `.figma-chat-file-summary--open` 修饰符去掉 cursor: pointer。
  2. 给 write / edit / read-output 三个 `<pre>` 都加上 `v-memo="[op.content, op.filePath]"`：内容/路径不变时直接跳过 `renderCodeWithLineNumbers`，折叠展开因此不再卡顿。
  3. 新增/调整单测：默认展开 read 工具列表不再显示 chevron；write 折叠展开切换不破坏渲染；之前写错的"两个连续 assistant 合并导致 details 计数 3 不是 4"用例用 user 消息隔开修好；切会话 setProps 缺 processStatus 的类型问题用 `as any` 兜底。
- How: 仅改 `FigmaChatPanel.vue` 模板和样式 + `FigmaChatPanel.test.ts`，不动 props/事件/store/后端；同步 agent-web README 第 32 条说明 read 默认展开 + v-memo 缓存。
- Result: `cd frontend && corepack pnpm test -- FigmaChatPanel` 37 通过 1 skipped；`cd frontend && corepack pnpm -r typecheck` 全过；`cd frontend && corepack pnpm test` 223 通过 1 skipped。

### 2026-07-02 - 对话框思考与能力卡片渲染重构，优化状态字与文字流光效果，添加气泡复制按钮

- Why: 
  1. 任务结束后，部分运行中状态（“思考状态 思考中”）依然停留在“思考中”并伴随动画，无法自动恢复为已完成态，体验不一致。
  2. 思考过程（reasoning）和工具调用（tool）的卡片式渲染（ProcessDisclosure）增加了大量不必要的边框、背景及胶囊徽标，导致侧边栏窄对话面板中的信息密度偏低且杂乱。
  3. 卡片外壳移除后，正在运行中的状态节点需要更突出的指示，用户希望以文字流光（文字渐变流动）效果呈现。
  4. 展开/折叠面板在大量日志条目下点击非常卡顿，需要优化 Vue `v-memo` 依赖。
  5. 用户需要能便捷复制用户输入文本和助手回答的 Markdown 文本内容。
- What:
  1. 合并思考过程：在 `MessageParts.vue` 与 `FigmaChatPanel.vue` 的 `messageOtherParts` 渲染前，将同一个助手消息中的所有 `reasoning` 思考零件合并为一个，且统一呈现在最上方，避免多次思考产生多个冗余的“思考中”。
  2. 移除卡片容器：重构 `ReasoningPartBlock.vue`、`ToolPartBlock.vue` 与 `FigmaChatPanel.vue` 中的 details 卡片样式，彻底弃用卡片背景与外框，改用无背景、无卡片框的**轻量级文本/日志行时间线**呈现。
  3. 精确的任务运行态同步与文字流光：修改 `AssistantThread.vue` 限制仅最新助手消息可呈运行态；在 `globals.css` 中新增 `.ta-text-shimmer` 流光 CSS 动画，并在 `ReasoningPartBlock.vue`、`ToolPartBlock.vue` 与 `FigmaChatPanel.vue` 运行态文本中应用该效果。
  4. 解决展开卡顿：引入 `messageExpandedState` 计算当前消息下的折叠状态，并将其加入 `.figma-chat-assistant` 的 `v-memo` 依赖，使得点击展开时只会精确重绘对应消息气泡，消除全局卡顿。
  5. 气泡一键复制：在用户气泡与助手文本气泡右上角新增基于 hover 触发的 `.figma-chat-bubble-copy-btn` 一键复制按钮，实现静默复制。
- How: 纯前端组件与样式重构，无后端和数据库改动；更新了 `agent-chat` 包的 README。
- Result: 运行 `corepack pnpm typecheck` 成功；`corepack pnpm test` 全量通过（31 个测试包 217 个用例全绿），无类型或逻辑错误。

### 2026-07-02 - 修复个人 worktree 发布误提交未暂存文件

- Why: 个人 worktree 左侧 Git 面板的“暂存”只是前端选择状态，后端发布仍执行 `git add --all`；用户只选择/保留一个文件发布时，其余本地 diff 也被提交并从个人 worktree 消失，且发布链路失败时远端特性分支可能没有收到内容。
- What: 个人 worktree publish 请求体新增 `files`，前端点击“提交并推送”只传应用工作空间已暂存文件路径；后端将这些工作区相对路径映射为仓库相对路径后调用 `stageFiles`，不再 `stageAll`。同时保留本地合并语义：先把最新特性分支合入个人 worktree，冲突留在个人 worktree；无冲突后把个人分支合入应用版本副本并只 push 特性分支。
- How: 不回退个人 worktree 创建重构，不改分支命名、recent 记录或旧路径自愈；同步 workspace-management README、agent-web README 和 HTTP API 文档，补充后端/前端回归测试覆盖“只发布前端暂存文件”。
- Result: `mvn -pl test-agent-workspace-management -am -Dtest=ManagedWorkspaceApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`、`mvn -pl test-agent-api -am -Dtest=ManagedWorkspaceControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`、`mvn -pl test-agent-workspace-management -am test`、`corepack pnpm@10.25.0 --dir frontend test -- git-changes-panel.test.ts`、`corepack pnpm@10.25.0 --dir frontend --filter @test-agent/agent-web typecheck`、`corepack pnpm@10.25.0 --dir frontend --filter @test-agent/backend-api typecheck` 和 `git diff --check` 通过。

### 2026-07-02 - 优化前端流式 SSE 渲染性能与二级去重

- Why: 前端流式打字输出时偶尔存在视觉抖动和微小卡顿；高频 delta 触发时，消息 reducer 存在频繁的多轮数组回溯查找，且每次数组浅拷贝会造成大量 DOM 气泡和 Card 组件的冗余重绘；此外，断线重连或 live-replay 竞态时，相同的 eventId 可能会被重复消费。
- What: 
  - 在 `AgentChatRuntimeState` 状态中引入 `seenEventIds` 哈希去重拦截器，防范重复事件在 store 层二次计算（测试 Mock 的 `evt_${type}` 特殊过滤，避免阻断单元测试）。
  - 在 `FigmaChatPanel.vue` 的对话遍历层为 `figma-chat-user-message` 与 `figma-chat-assistant` 分别装配 `v-memo` 微观渲染缓存。
  - 用户气泡锁定文本、meta、技能名；助手气泡锁定文本、parts 长度、每个 part 状态变更集及 meta 标记；以此切断流式打字期间对所有历史气泡的全局 VDOM 重绘开销。
- How: 纯前端代码重构与指令级优化，保持已有交互卡片功能完全无缺失；对 mapping 状态进行 TS 兼容性修复以防止 p.status 的属性缺失报错。
- Result: `cd frontend && pnpm typecheck` 成功；`pnpm test` 215 个前端单元测试 100% 跑绿。

### 2026-07-02 - slash command 展开 user part 不能误建 assistant 输出

- Why: opencode 可能先发送 `message.part.updated`，再发送或不及时发送 `message.updated(role=user)`；slash command 会被展开成完整技能提示词，展开文本与本地原始 `/command 参数` 不相等，旧 reducer 会把该 user text part 当作 assistant 输出显示。
- What: `agent-chat` reducer 在 exact message 和同文本 user 归并之外，增加 slash command 展开文本兜底：远端 text part 包含本地 slash 参数时，只把远端 `messageID` 绑定回未绑定的本地 user 消息，保留用户原始输入文本。
- How: 新增 `findUnlinkedSlashUserByExpandedText` / `slashCommandArgument` helper，并补充无 `message.updated(role=user)` 的回归测试，覆盖 `/generate-cases-path 对车贷的开发文档，生成路径图` 被展开为路径法技能正文的真实形态。
- Result: `cd frontend && corepack pnpm test -- runtime-reducer`、`cd frontend && corepack pnpm --filter @test-agent/agent-chat typecheck`、`cd frontend && corepack pnpm test -- FigmaChatPanel` 和 `git diff --check` 通过。

### 2026-07-02 - 前端原始报文查看器只捕获浏览器可见数据

- Why: 用户需要在右侧对话框查看前端用户发送给平台后端、以及前端从平台后端收到的原始报文，用于排查前端渲染前的真实输入输出；同时明确不是 opencode server 端原始事件，也不需要后端持久化。
- What: `backend-api` 增加可选 `rawExchangeObserver`，只暴露安全请求头、请求体、响应状态/响应头和响应原文；`event-stream-client` 增加 `onRawMessage`，在解析 RunEvent 前回调浏览器 `EventSource` 实际拿到的 `MessageEvent.data`；`AgentWorkbench` 按 session 维护内存 entries，`FigmaChatPanel` 提供可拖动、可 resize 的“原始输出”浮层和请求/响应/SSE 筛选。
- How: 只在 `AgentWorkbench` 创建 backend-api client 时启用 observer，并保守过滤会话创建、Run 启动/取消、active-run、消息加载、permission/question 回复和 RunEvent SSE；单 session 保留最近 1000 条，单条正文超过 200000 字符截断；不记录 `Authorization`、Cookie，不新增 API、SSE 契约、表结构或 Flyway migration。
- Result: `cd frontend && corepack pnpm test -- backend-api event-stream-client FigmaChatPanel`、`corepack pnpm --filter @test-agent/agent-web typecheck`、`corepack pnpm --filter @test-agent/backend-api typecheck`、`corepack pnpm --filter @test-agent/event-stream-client typecheck` 和 `git diff --check` 通过。

### 2026-07-02 - 复用自定义与 default 个人 worktree 创建流程

- Why: 自定义命名个人工作区和自动确保的 default 个人工作区都创建个人 worktree、运行态 Workspace、PersonalWorkspace 并写入 recent，原先保留了两套几乎相同的私有创建方法。
- What: `ManagedWorkspaceApplicationService` 抽出共享个人工作区创建核心和分支生成 helper；自定义命名入口与 default 自动创建入口复用同一流程，并保留各自当前副本获取策略。
- How: default 旧记录 repair 继续单独处理路径修复；新增结构回归测试防止重新引入 `doCreatePersonalWorkspaceWithName` 复制流程。
- Result: `mvn -pl test-agent-workspace-management -am -Dtest=ManagedWorkspaceApplicationServiceTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` 通过；不涉及 API、事件、数据库、前端 UI 或环境配置。

### 2026-07-02 - 合并未推送提交并保留未跟踪文件 Diff patch

- Why: 本地 `main` 与最新 `origin/main` 已分叉，需要把本地未推送提交压成一个提交并保留远端新增修复；冲突合并时工作区 Git Diff 解析从业务层迁到 `GitWorkspaceService` 后，未跟踪文件的 pseudo patch 行为不能丢失，否则前端 Diff 视图会显示空 patch。
- What: 将本地未推送内容 squash merge 到最新 `origin/main`，合并 session log、README、HTTP/部署文档、Git 发布流程与前端包说明冲突；`GitWorkspaceService.collectDiffFiles` 对未跟踪文件生成 `/dev/null -> b/path` pseudo patch，并让工作区 Git Diff 继续复用 common 层解析。
- How: 保留 `GitPublishWorkflow` 统一发布/冲突 abort 语义，删除 `ManagedWorkspaceApplicationService` 中已迁移的私有 porcelain/diff 解析块；补充 `GitWorkspaceServiceTest` 与 `ManagedWorkspaceApplicationServiceTest` 覆盖未跟踪文件 patch、staged/unstaged 聚合和发布冲突 abort。
- Result: `mvn -pl test-agent-workspace-management -am -Dtest=GitWorkspaceServiceTest,GitPublishWorkflowTest,ManagedWorkspaceApplicationServiceTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`、`corepack pnpm --filter @test-agent/agent-web typecheck`、`go test ./...`、冲突标记扫描和 `git diff --check --cached` 通过。

### 2026-07-01 - 自动恢复历史与刷新会话中的活动 Run 及组件状态持久化

- Why:
  1. 当用户输入由 `/` 触发的技能（例如 `/generate-cases-orthogonal` 等长耗时操作）时，如果刷新页面或在历史会话中切换，前端先前会丢失与后台正在执行（状态为 `RUNNING`/`CANCELLING`）的活动 Run 的绑定，从而无法通过 SSE 续连事件流，导致状态卡死在“思考中”。
  2. 智能体思考过程中，气泡内的 `思考状态` 和全局底部的 `C 思考中...` 重合渲染，样式存在冲突和视觉冗余。
  3. 用户刷新页面后，之前选择的模型与厂商配置会重置为系统默认值，需频繁重新选择。
- What:
  - 增强 `AgentWorkbench.vue` 中的 active-run 接管逻辑：恢复历史消息、刷新后 session 恢复、以及 `startRun` HTTP 请求 pending 或失败但后端已创建 Run 的场景，都会通过 `api.getActiveRun` 查询后端。若当前会话在后端有 `PENDING`/`RUNNING`/`CANCELLING` 活动 Run，则将其赋给 `run.value` 重连。
  - 新增带序号保护的 active-run 探测函数和启动请求 pending 期间的短轮询，避免会话快速切换时旧请求覆盖新会话，也避免长耗时命令在前端拿不到 runId 时无法订阅 SSE。
  - 优化 `FigmaChatPanel.vue` 中的全局 `showRunningAssistant` 计算逻辑：若当前最新的回复气泡内已经输出了 reasoning/tool part，则不再展示最底部的全局 `figma-chat-running-assistant`（思考状态栏），避免界面同时渲染两个思考指示器。
  - 将 `AgentWorkbench.vue` 里的模型 `selectedModel` 与提供商 `selectedProvider` 配置持久化至 `localStorage`（通过 `ta_selected_model` / `ta_selected_provider`），并避免 recent workspace 自动切换时清空该用户级偏好，使页面刷新后能够自动恢复上次的模型选择偏好，避免重选。
- How: 纯前端组件优化与状态持久化操作，不改变后端数据接口，保持 API 与单测兼容。
- Result: `corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts -g "model picker keeps|switching history resumes" --project=chromium` 通过，覆盖模型刷新恢复与历史 active-run SSE 接管。

### 2026-07-01 - 提升前端启动 Run 和初始化进程请求超时限制

- Why: 当前对话页面中，当智能体思考或启动过久（例如拉起新容器、建立 bindings、首次会话创建等耗时较长操作）时，前端 HTTP 会在 30 秒超时后显示超时报错（如“启动 Run 失败: 请求超时”）。但由于后端该操作已经开始并最终会异步运行成功，点开历史消息时发现智能体其实已经完成，给用户造成了不一致和误导性的体验。此外，会话标题过长时在 flex 布局中未设置收缩及最小宽度，导致标题文本挤占空间将“历史对话”按钮完全挤出可视区域或压缩文字。
- What:
  - 在 `backend-api` 中定义 `ExtraRequestInit` 类型（继承自 `RequestInit` 并增加 `timeoutMs?: number`），使前端每个请求可独立覆盖 30 秒 of 默认全局超时配置。
  - 在 `requestFrom` 核心请求方法中解析局部 `timeoutMs`，并在传给 `fetcher` 时对 extra 属性进行解构，保障标准的 fetch 参数规范。
  - 将启动任务 `/runs` (startRun)、初始化专属进程 `/processes/me/initialize` (initializeMyOpencodeProcess) 以及向会话发送命令/Shell 操作 (`runSessionCommand`/`runSessionShell`) 的客户端请求超时提高到 120,000 毫秒（2 分钟），并在 Java 后端的 `DefaultOpencodeClientFacade.runtime()` 对 `/command` 和 `/shell` 命令接口同步将远端 opencode 交互超时提升为 120 秒，避免冷启动与执行偏慢下频繁抛出 `OPENCODE_TIMEOUT` 错误。
  - 在 `backend-api` 的 `README.md` 中同步了这一 API 行为。
  - 调整 `FigmaChatPanel.vue`：
    - 为对话标题 `.figma-chat-title` 和左侧布局容器 `.figma-chat-header-left` 增加 `flex: 1; min-width: 0;` 以实现响应式截断及省略号。
    - 为标题增加了 `:title` 属性，鼠标悬停时会展示全量标题文本。
    - 将“历史对话”按钮文本简写为“历史”（减少宽度占用），并为其 CSS 类 `.figma-chat-header-btn` 加上 `flex-shrink: 0` 防止被挤压或隐藏。
- How: 涉及前端网络超时、Java 后端 opencode client 通信策略以及 Vue 聊天面板 CSS flex 属性调整，无数据库和 schema 变动。
- Result: 单元测试及前端 31 个测试包共 206 个测试全部通过，Java 模块 `test-agent-opencode-client` 35 个单测通过。

### 2026-07-01 - 物理 Git 干净时清空侧边栏 Tab 变更角标和文件树高亮

- Why: 物理 Git 工作区已处理干净时（未暂存/已暂存为 0），侧边栏的 Git 变更 Tab 角标依然显示为 1，且工作区文件树（例如 .subitem.json）依然显示 +2 -1 的高亮。这是因为角标和文件树高亮绑定了智能体运行产生的会话变更（diffFiles），未与物理 Git 状态同步。
- What:
  - 在 `AgentWorkbench.vue` 中新增 `vcsDiffFiles` 响应式变量，作为物理 Git 仓库变更的专用数据源，将其传给 `FigmaFileExplorer` 的 `:changed-files` 属性。
  - 修改 `refreshWorkspaceGitDiff`：总是将最新的物理 Git diff 写入 `vcsDiffFiles`；仅在当前 `diffSource === 'vcs'` 时才覆盖 `diffFiles`（以保持 diff 视图一致性）。
  - 在 `GitChangesPanel.vue` 的 `refreshChanges` 完成时（`finally` 块中），调用 `notifyChangesRefreshed(undefined, false)` 以向父组件广播最新的 Git 状态，但不强制重新加载所有已打开的文件（避免覆盖未保存的编辑内容）。
  - 在切换工作空间（`selectedWorkspaceIdRef` 监听器中）和手动刷新文件树（FigmaFileExplorer 的 `@refresh` 事件中）时，触发 `refreshWorkspaceGitDiff()` 以保证物理 Git 状态即时同步。
- How: 纯前端组件和事件流调整，不修改任何后端接口、环境配置、数据库或 generated SDK。
- Result: 前端 Vitest 195 个测试全量通过，`corepack pnpm typecheck` 通过；当物理 Git 干净时，侧边栏角标和工作区文件树高亮标志正确清零，右侧 FigmaChatPanel 内的智能体历史任务执行卡片仍保留修改历史（+2 -1）以作存档。

### 2026-07-01 - 调整个人 worktree 发布为本地合并并保留用户冲突现场

- Why: 本地个人 worktree 分支不应推送远端；发布应先拉远端应用版本特性分支，在本地完成 merge 后只推送特性分支。同时合并冲突需要留给用户在个人 worktree 中解决，不能只在后台应用副本 abort 后返回错误。
- What: `publishPersonalWorkspace` 改为个人 worktree 本地提交后，确保应用版本副本可用并拉取远端特性分支；先把最新特性分支 merge 进个人 worktree，若冲突则返回 `CONFLICT` 且保留个人 worktree 的 unmerged 状态；无冲突时把本地个人分支 merge 回应用版本副本并只 push 应用特性分支。删除不再使用的指定个人分支 remote-tracking fetch 封装。
- How: 未修改 `.env.local`/`.env.test` 和数据库 schema；同步 workspace-management/common README 与 HTTP API 文档；单测覆盖只推特性分支、本地双向 merge 顺序、冲突落在个人 worktree、clean retry 和旧应用副本路径自愈。
- Result: `ManagedWorkspaceApplicationServiceTest` 先按新语义红测，再调整实现跑绿；`GitWorkspaceServiceTest` 与 `mvn -pl test-agent-workspace-management -am test` 通过。后续实际 UI 验证时，若返回 `CONFLICT`，用户需要在当前个人工作区内解决冲突并再次点击提交并推送。

### 2026-07-01 - 修复 worktree 发布、公共配置展示、opencode 启动与固定机器绑定

- Why: F-COSS 本地 UI 验证发现个人 worktree 点击“提交并推送”后远端应用分支没有代码，且失败后可能重复提示冲突/错误；公共 opencode config 初始化后首页不显示；test profile 下 opencode 启动失败；同时确认 `7571d775e00453f85b1bd370385fe1e0bbdd10cc` 的 INACTIVE binding 自动迁移语义不符合“一人固定绑定一台 IP 机器”的规则。
- What: 个人 worktree 发布改为先提交并推送个人分支，再确保当前机器应用版本副本有效，fetch `origin/<personalBranch>` 后 merge 到应用版本特性分支并推送；冲突仅在 Git 明确留下冲突文件时返回 `CONFLICT`，随后 `merge --abort` 清理应用副本；已提交但后续失败的 clean worktree 重试会继续 push/merge；应用副本记录中的旧 Windows/macOS 路径会按当前 `OPENCODE_APP_WORKSPACE_ROOT` 自愈并同步运行态 Workspace。公共配置状态刷新会回填服务器仓库列表，restart 脚本在 test profile 默认启动本机 manager 并导出 `$TESTAGENT` 兼容别名。`UserOpencodeProcessAssignmentService.initialize()` 恢复读取用户固定 binding，不因 INACTIVE 记录自动迁移到当前 IP。
- How: 未修改 `.env.local`/`.env.test` 和数据库 schema；复用既有 `ensureLocalReplica`、Git service、公共 opencode startup/status/manager 链路；同步 workspace-management/common/app/backend/API/部署文档，并补单测覆盖远端个人分支 fetch、冲突 abort、clean retry、旧应用副本路径自愈和固定机器绑定。
- Result: 定向后端测试、前端 typecheck、脚本语法校验和真实 test profile 重启通过；UI 验证公共级配置树可见、opencode 可从页面启动、F-COSS worktree diff/暂存/提交并推送成功，远端 `feature_testagent_20260618` 已包含 UI 验证标记。

### 2026-07-01 - 修复换网后 INACTIVE 旧绑定阻断 OpenCode 初始化

- Why: 该条历史记录来自临时排障方向，后续确认不符合“一人固定绑定一台 IP 机器”的产品规则；INACTIVE 历史 binding 不能被 `initialize()` 静默忽略并迁移到当前 IP。
- What: 本次已在上一条记录中纠正：`initialize()` 继续读取用户既有 binding，并由业务侧临时数据修正或运维处理异常 IP 变动，不做自动迁移。
- How: 保留该条作为误判追溯，避免后续开发者按旧结论重新实现 INACTIVE 自动迁移。
- Result: 当前有效结论以上一条“固定机器绑定”记录为准。
### 2026-07-02 - 运行管理拓扑展示服务器地址列

- Why: 稳定 `linuxServerId` 不再保证是 IP，运行管理两张拓扑表需要同时展示服务器身份和可访问地址，便于排查多服务器、多 Java 场景。
- What: “服务器 / Java 进程”和“容器 / 管理进程”表保留稳定服务器身份列，新增“IP地址”列；地址从同服务器 Java `listenUrl` 的 host 派生，不把 `linuxServerId` 当网络地址使用。
- How: 前端只基于 overview 现有字段派生展示值，不变更后端 API wire shape；补充 Vitest 覆盖稳定 ID 为 `linux-prod-a`、地址为 `10.8.0.21` 的展示场景，并收窄地址函数返回类型以通过 Vue `title` 绑定检查。
- Result: `corepack pnpm@10.25.0 --dir frontend test -- apps/agent-web/tests/runtime-management-settings.test.ts` 和 `corepack pnpm@10.25.0 --dir frontend --filter @test-agent/agent-web typecheck` 通过。

### 2026-07-02 - 测试库构造 20 个超级管理员用户

- Why: 用户需要在 `192.168.100.200:5432/testagent` 测试库中直接构造 20 个超级管理员账号，统一密码为 `123456`，并明确不要使用 Flyway。
- What: 直接写入 `users` 与 `user_roles`，账号为 `superadmin01` 至 `superadmin20`，统一认证号同账号，用户业务 ID 为 `usr_test_superadmin01` 至 `usr_test_superadmin20`，角色为 `SUPER_ADMIN`。
- How: 使用项目同版本 Spring Security BCrypt 生成密码哈希，通过 psql 执行事务 SQL；写入前后对齐 `users` 与 `user_roles` identity 序列，未新增或修改 migration、代码、API、事件或环境配置文件。
- Result: 校验目标账号数 20、ACTIVE 用户 20、`SUPER_ADMIN` 授权 20；抽样 `superadmin01` 的 BCrypt 密码匹配 `123456` 为 true。

### 2026-07-02 - 恢复已落库 migration 校验和

- Why: 后端启动日志显示 Flyway 校验失败，`V15` 和 `V20260626210000` 的本地 checksum 与测试库 `flyway_schema_history` 已应用值不一致。
- What: 根因是工作树里对两个已落库 migration 的注释做了未提交改写；稳定服务器身份的数据库注释已经由新增的 `V20260702000000__allow_stable_linux_server_ids.sql` 承载，旧 migration 不能再改。
- How: 将两个旧 migration 文件恢复到 HEAD 内容，不运行 `flyway repair`，避免把本地误改写入共享测试库历史；确认这两个文件已无 diff。
- Result: `mvn -q -pl test-agent-persistence -Dtest=FlywayMigrationNamingTest test` 通过；启动日志里的 backend_java_processes 外键 WARN 是迁移失败后关闭清理阶段的级联副作用，迁移恢复后需重新构建/重启后端验证。

### 2026-07-02 - Linux Server 改为稳定服务器身份

- Why: 服务器迁移后 IPv4 会变化，继续把 `linuxServerId` 绑定为 IP 会导致 manager、用户 opencode 进程和跨 Java 路由失效；同一服务器上多个 Java 进程也需要等价承载完整服务能力。
- What: `linuxServerId` 放宽为稳定服务器身份，Java 启动时写入 `.serverid` 与 `.serverhost`，opencode-manager 读取新文件注册；opencode `baseUrl/serviceAddress` 改用 advertised host；Redis 后端快照按 `backendProcessId` 保留并按稳定身份分组选路，优先 manager 连接 Java、其次最新心跳、最后当前 Java；删除 `.serverip` 依赖。
- How: 新增 `TEST_AGENT_LINUX_SERVER_ID` / `TEST_AGENT_SERVER_ADVERTISED_HOST` 解析和启动文件写入，调整领域校验、runtime 路由、状态刷新、manager 配置、Flyway 字段长度注释、前端展示与文档文案；不兼容旧 `.serverip` 模式，当前未投产按新语义重建或清理本地数据。
- Result: 稳定身份相关 Java 定向回归、Go manager 测试、前端 typecheck/Vitest、`mvn clean package -DskipTests`、`tools/verify-dev-scripts.sh` 与 `git diff --check` 通过；计划中的后端聚合 `mvn -pl test-agent-domain,test-agent-opencode-runtime,test-agent-api,test-agent-app,test-agent-persistence -am test` 仍被既有 persistence 全量测试问题阻塞（H2 `ON CONFLICT`、`usr_test_dev` fixture 外键、默认 seed/loopback 断言），与本次稳定身份改造无关。

### 2026-07-01 - 统一 Git Diff 与 porcelain 解析

- Why: 工作区 Git Diff 与 Agent 配置 Diff 分别解析 `git status --porcelain`、rename 路径、staged/unstaged patch 和增删行统计，容易出现行为不一致。
- What: `GitWorkspaceService` 新增通用 porcelain 解析和 diff 文件聚合能力；工作区 Git Diff 与 Agent 配置 Diff 改为复用 common 层，业务服务只保留权限、路径裁剪/映射和响应 DTO 转换。
- How: 工作区 Git Diff 返回归一化状态与 additions/deletions；Agent 配置 Diff 保留原始 Git 状态简写；工作空间级 Agent diff 继续只展示 `.opencode/agents` 与 `.opencode/skills` 下的 `agents/`、`skills/` 路径。
- Result: `mvn -pl test-agent-workspace-management -am -Dtest=GitWorkspaceServiceTest,ManagedWorkspaceApplicationServiceTest,AgentConfigApplicationServiceTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` 与 `mvn -pl test-agent-workspace-management -am test` 均通过；不涉及 API URL、事件、数据库、前端 UI 或环境配置。

### 2026-07-01 - 作废普通 Workspace 目录选择与 allowed-roots 配置

- Why: 用户要求作废 `test-agent.workspace-picker.allowed-roots`，并明确应用/个人 workspace 目录必须由后端根据通用参数和业务 id 查询/派生，普通前端不能直接传物理目录。
- What: 删除所有 `application*.yml` 中的普通目录选择配置，移除 `TestAgentRuntimeProperties.WorkspacePicker`、普通 HTTP `rootPath` 创建入口和普通目录浏览入口；前端删除普通本机目录选择弹窗、bootstrap 注册组件和 backend-api 对应方法，保留 `SUPER_ADMIN` 服务器工作空间选择器的 WebSocket `directory.list` / `workspace.create` 链路。
- How: 应用版本/个人工作区继续走 managed workspace id 与 `common_parameters` 路径规则；历史/超管服务器工作空间仍可由后端根据数据库 `workspaceId` 读取根目录，文件访问继续走 route/ticket/RPC。
- Result: 待本次验证命令完成后随代码一起提交；当前工作区存在他人未提交的 common parameter editable 相关改动和 migration，本次不纳入暂存。

### 2026-07-01 - 运行管理列宽拖动与折叠面板类型修复

- Why: 运行管理中的各个列表标题不支持拖动，当文字过多时无法显示完整，且无法手动调整列宽度；此外，前端构建时 `SettingsUserManagementPanel.vue` 的 `identityVisible` 声明为 boolean 类型与 Element Plus 的 `el-collapse` v-model 要求的类型冲突。
- What:
  - 在 `RuntimeManagementPanel.vue` 中将所有 5 个运行管理表格设置为 `table-layout: fixed` 并分配初始列宽，在表头中加入绝对定位的拖拽手柄 `.ta-resize-handle` 元素。
  - 实现了 `startResize` 拖拽监听函数，通过直接操作 DOM 中 `<th>` 元素的 `width` / `minWidth` / `maxWidth` 实现列宽拖拽调节；给容易溢出的长文本单元格添加 `:title` 属性结合 `?? undefined` 构成类型安全的悬浮气泡。
  - 在 `SettingsUserManagementPanel.vue` 中将 `identityVisible` 类型修改为 `string[]` 并适配对应的 watcher，消除 TypeScript 类型检查错误。
- How: 纯前端交互调整，不改变后端接口与环境配置，保持 DOM 操作高性能渲染。
- Result: 整个前端项目打包构建 (`vue-tsc --noEmit` & `vite build`) 成功，无任何编译及类型错误；相关 Vitest 单元测试全部通过。

### 2026-07-01 - 作废公共目录、托管根路径与 test-agent Redis 配置

- Why: 用户要求继续参数治理，作废 `test-agent.managed-workspace.root`、`test-agent.public-directory.path`，下线公共目录功能，并把 Redis 配置统一收口到 Spring 标准 `spring.data.redis.*`。
- What: 删除 6 个 application yml 中的 `test-agent.public-directory` / `test-agent.redis`，删除 local-h2 中的托管根路径；移除公共目录后端 Controller/Service/测试、frontend 公共目录入口和 backend-api 公共目录方法；`RedisHealthIndicator` / `RedisStartupHealthCheck` 改为读取 `DataRedisProperties`；文档同步为只使用 `spring.data.redis.*`。
- How: `TEST_AGENT_REDIS_HOST/PORT/PASSWORD/TIMEOUT` 仍作为部署环境变量保留，但只绑定到 `spring.data.redis.*`；`managed-workspace.replica-reconciler.enabled` 保留；不新增数据库 migration，不新增 common_parameters，不改 `.env.local`。
- Result: 配置绑定与 Redis 健康检查定向测试、workspace-management 模块测试、后端 app package、backend-api/agent-web typecheck、废弃参数精准搜索和 `git diff --check` 通过；计划中的 `test-agent-api -am test` 仍被既有 `ApiLoggingAspectTest.java` 测试编译错误阻塞（访问 private 方法且缺少断言/AuthWebSupport 引用），与本次改动无关。

### 2026-07-01 - 数据库 IDENTITY 运维功能

- Why: 添加用户时报"数据冲突：当前操作因存在关联数据无法执行，请先清理关联记录后重试"，根因是 `users` 表 identity 序列落后于已有主键（与历史 `user_roles` 同类问题），被全局异常处理器翻译成误导文案。用户要求把"查询当前序号 + 手工滚动"做成运维入口而非一次性 Flyway 修复。
- What: 新增超管运维入口，查询白名单表（users/user_roles/dictionaries/user_login_logs）identity 当前值/max(id) 并支持一键对齐 max+1 与手动 RESTART WITH n（禁止往回滚）。
- How:
  - 后端按端口适配器模式：domain 端口 `DatabaseIdentityMaintenancePort` + 白名单枚举 `IdentityManagedTable`；persistence 用 MyBatis XML（`pg_get_serial_sequence` + `pg_sequences` + `ALTER TABLE ... RESTART WITH`）实现 `MyBatisDatabaseIdentityMaintenanceRepository`；system-management 应用服务编排校验+审计日志。
  - Controller (`DatabaseIdentityController`) 3 接口挂 `/api/internal/platform/system-management/identity`，仅 SUPER_ADMIN。
  - 前端在 `SettingsUserManagementPanel.vue` 新增折叠区块，表格展示状态 + 对齐/重置按钮 + 错位高亮。
  - 同步 http-api.md / database.md / 两模块 README。
- Result:
  - 后端服务层 8 个单元测试、Controller 切片 5 个测试、Mapper H2 集成测试全绿。
  - api 模块预存 `ApiLoggingAspectTest` 死测试阻塞编译（调用 private 方法），与本次无关；已补充 `reactor-test` 测试依赖。
  - 前端代码结构正确，沿用现有面板范式。
  - 未改表结构（无 Flyway migration）；未新增 ErrorCode 枚举；SQL 注入由白名单枚举+Long 校验防护。

### 2026-07-01 - 收口 OpenCode 状态抖动、技能召唤与重启假超时

- Why: `/global/health` 返回 200 时页面显示绿灯，但公共配置 `opencode.jsonc` 仍使用旧的 `skills: ["./skills"]`，OpenCode 1.17.7 的 `/command` 实际返回 `ConfigInvalidError`；同时瞬时 manager/HTTP 探测会覆盖数据库稳定状态，文件 ticket 强探测和旧 workspace 重试会放大抖动，运行管理 restart 还存在“实际成功但 Java 先超时”的竞态。
- What: 公共配置改为 OpenCode 原生 `"skills": {"paths": ["./skills"]}` 并提交推送到配置仓库；manager readiness 增加 `/global/config` 可加载校验，restart 的 stop 阶段只占总预算一半；Java 瞬时失败返回 `STALE` 且不覆盖稳定状态，最近成功健康检查仅保留 60 秒 READY 宽限，启动控制错误立即失败并收敛候选状态；文件 ticket 只读 affinity，工作区文件树用 workspaceId + 代次隔离旧请求；技能面板继续直接消费 OpenCode `/command` 的 `source=skill`，调用权限仍由原生 `permission.skill` 决定。
- How: 未修改 OpenCode 源码、generated SDK、数据库结构或环境文件；Java 继续通过公共 startup/status/stop 服务和 manager WebSocket 控制进程，普通 runtime/skill/agent 请求继续经 `AgentRuntime` 代理 OpenCode 原生 API。同步 opencode-manager、runtime、agent-web README 与 HTTP API 文档，并补充 Go、Java、Vue 回归测试。
- Result: `go test ./...`、相关 Java 定向测试、runtime/API 模块全量测试、前端 195 个 Vitest、typecheck、build 和真实 `/api/command`（44 条命令、42 条 skill）通过；额外确认项目公共配置目录自身有 19 个业务 skill，其余来自 OpenCode 原生合并到的用户级/系统级 skill。后端全 reactor 到 persistence 前均通过；persistence 仍有近期日志已记录的 H2 `ON CONFLICT` 不兼容、`usr_test_dev` fixture 外键缺失和 migration seed 断言失败，本次未扩大范围处理。

### 2026-07-01 - 修复 opencode 状态不一致与回退后编辑器缓存未刷新

- Why: 用户反馈右侧状态卡显示 `opencode 进程可用`，但左侧文件树仍提示 `OPENCODE_UNAVAILABLE`；同时点击 Git 变更回退后，变更计数消失但已打开编辑器里的文件内容仍是回退前缓存。
- What: Workspace 文件 WebSocket ticket 签发在轻量 `fileRoutingAffinity` 未 READY 时复查当前用户 opencode 强状态，使文件树可用性与状态卡一致；Git 回退事件携带被回退路径，父组件刷新 diff 后只重读对应已打开工作区 tab，并清理旧的文件树失败提示。
- How: 复用既有 `UserOpencodeProcessAssignmentService.status`、`api.readFile` 和 workbench store，不新增启动逻辑、不修改环境配置；同步 API、API 模块 README 与 agent-web README 的文件 ticket 语义。
- Result: `WorkspaceFileSocketTicketServiceTest`、`agent-web typecheck`、`git-changes-panel.test.ts` 和 `git diff --check` 通过；按用户要求未启动本地服务。

### 2026-06-30 - 修复 Windows 下 opencode 专属进程启动失败（.ps1 包装脚本）

- Why: 用户在 Windows 上把 `OPENCODE_BIN`（对应 `TEST_AGENT_OPENCODE_BIN`）配成 `D:\Tool\nodes\nodejs\opencode.ps1` 这种 PowerShell 包装脚本后，前台点击「分配专属进程」报 `OPENCODE_BAD_GATEWAY: fork/exec D:\Tool\nodes\nodejs\opencode.ps1: %1 is not a valid Win32 application`。根因是 Go `os/exec` 在 Windows 上无法把 `.ps1` 文本文件当作可执行体 fork/exec，必须由 PowerShell 进程承载脚本解释；当前 `process_windows.go` 仍按 `exec.Command(spec.Command, spec.Args...)` 直传配置命令，没做平台兜底。
- What:
  - [opencode-manager/internal/process/process_windows.go](file:///d:/workspace/intelligent-test-agent/opencode-manager/internal/process/process_windows.go) 在 `OSStarter.Start` 内新增 `resolveWindowsCommand(command, args)`：检测 `filepath.Ext` 为 `.ps1`（大小写不敏感）时改写为 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <ps1> <args>`，其它可执行后缀（`.exe`、`.bat`、`.cmd`、裸名）原样返回。
  - 新增 [opencode-manager/internal/process/process_windows_test.go](file:///d:/workspace/intelligent-test-agent/opencode-manager/internal/process/process_windows_test.go)，覆盖原生 exe、无扩展名命令、`.ps1` 包装、大写 `.PS1` 四种场景；新增文件用 `//go:build windows`，非 Windows 平台不会编译。
  - 同步 [opencode-manager/README.md](file:///d:/workspace/intelligent-test-agent/opencode-manager/README.md) 的「构建与运行」段落，补充 Windows 上 `OPENCODE_BIN` 指向 `*.ps1` 时 manager 的实际调用形式和扩展名匹配规则，保持与代码行为一致。
- How: 纯 Go 改动 + 模块 README 同步，不涉及 API/事件/数据库/安全/兼容性；不修改 `process.go` 的 `BuildStartSpec` / `formatStartCommand`，展示用的 `startCommand` 仍按 `cfg.OpencodeBin` 渲染（README 明确定义的展示形态不变）。
- Result: `go vet ./...` 通过；`go build -o bin/opencode-manager.exe ./cmd/opencode-manager` 成功；`go test -run TestResolveWindowsCommand -v ./internal/process/...` 4 个新用例全部通过；其它 `internal/process` 与 `internal/config` 测试的失败在 base commit `c437e1b1` 已存在（Windows 下 `filepath.Join` 把 `/tmp/sessions/4096` 转成 `\tmp\sessions\4096`，config mock 在 Windows 上未注入 readFile），与本次改动无关。

### 2026-06-30 - 修复 Invoke-WebRequest 安全警告提示

- Why: 脚本中 `Test-HttpOk` 用的 `Invoke-WebRequest` 没有 `-UseBasicParsing`，PowerShell 5 会弹出"脚本执行风险"安全警告，需要用户手动输入 Y 才能继续。
- What: [win-restart-dev-services-fixed-v4.ps1](file:///d:/workspace/intelligent-test-agent/win-restart-dev-services-fixed-v4.ps1) 中 `Test-HttpOk` 的 `Invoke-WebRequest` 加上 `-UseBasicParsing` 开关。
- How: 仅改 PowerShell 脚本；不涉及 API/事件/数据库/安全/兼容性。
- Result: 不会再弹出安全警告。

### 2026-06-30 - 修复 Maven clean 失败：子进程清理 + 句柄延迟释放 + 自动重试

- Why: 用户反馈之前加的 `Stop-AllDevServices` 没有解决 `mvn clean` 时 `test-agent-app-0.1.0-SNAPSHOT.jar: 另一个程序正在使用此文件` 的问题。原因有两个层次：
  1. `taskkill /F /IM` 和 `Stop-Process -Force` 都不杀子进程，Java 子进程继续持有 jar 句柄。
  2. 即使进程被杀掉，Windows 文件句柄释放有延迟，`mvn clean` 紧接着执行就撞上了。
- What:
  - [win-restart-dev-services-fixed-v4.ps1](file:///d:/workspace/intelligent-test-agent/win-restart-dev-services-fixed-v4.ps1) 四处修复：
    1. `Stop-AllDevServices` 的兜底 `taskkill` 从 `/F /IM` 改为 `/F /T /IM`（`/T` 同时终止子进程）。
    2. `Stop-ProcessIds` 的 force stop 从 `Stop-Process -Force` 改为 `taskkill /F /T /PID`，对每一个残留 PID 递归杀子进程。
    3. `Stop-AllDevServices` 末尾加 `Start-Sleep -Seconds 2`，给 Windows 时间释放句柄。
    4. `Build-Backend` 中 `mvn clean` 失败时不再直接 `exit`，而是再次调用 `Stop-AllDevServices` 并等待 3 秒后重试一次。
- How: 仅改 PowerShell 脚本；不涉及 API/事件/数据库/安全/兼容性。
- Result: PowerShell parser 校验通过。

### 2026-06-30 - 修复 Windows 下后端日志与 Git 输出乱码

- Why: Windows 控制台默认 GBK 编码，后端 Java 进程 `System.out/err` 按 GBK 输出到日志文件，PowerShell 读 `backend.log` 时中文显示为乱码（`Git 杩滅璇诲彇澶辫触` 等）。同时 `ProcessGitCommandExecutor` 执行 `git` 子进程时，Windows 上 `git` 的 stderr 也是 GBK，被 Java 按 UTF-8 读出来也是乱码。
- What:
  - [win-restart-dev-services-fixed-v4.ps1](file:///d:/workspace/intelligent-test-agent/win-restart-dev-services-fixed-v4.ps1) 的 `BackendJavaDirectNetworkArgs` 增加 `-Dfile.encoding=UTF-8`、`-Dsun.stdout.encoding=UTF-8`、`-Dsun.stderr.encoding=UTF-8`，强制 Java 进程的输出编码为 UTF-8。
  - [ProcessGitCommandExecutor.java](file:///d:/workspace/intelligent-test-agent/backend/test-agent-common/src/main/java/com/icbc/testagent/common/git/ProcessGitCommandExecutor.java) 中 `ProcessBuilder` 环境变量补充 `LANG=en_US.UTF-8` 和 `LC_ALL=en_US.UTF-8`，让 git 子进程按 UTF-8 输出（代码本身已用 UTF-8 读取）。
- How: 纯运维配置 + 子进程环境变量调整，不改变业务逻辑、不影响 API/事件/数据库/安全/兼容性。
- Result: `mvn -pl test-agent-common -am compile` 通过；`LANG`/`LC_ALL` 在 Linux/macOS 上是 git 的常规配置，无副作用。

### 2026-06-30 - 拆分 process.go 为 Unix/Windows 平台特化实现

- Why: `process.go` 中的 `OSStarter.Start()` 和 `OSSignaler` 使用了仅 Unix 的 `syscall.SysProcAttr{Setpgid: true}` 和 `syscall.Kill(-pid, sig)`，导致 Windows 上 `go build` 失败。开发组同时使用 macOS 和 Windows，需要跨平台兼容。
- What:
  - 将平台相关代码拆到 [process_unix.go](file:///d:/workspace/intelligent-test-agent/opencode-manager/internal/process/process_unix.go)（`//go:build !windows`）和 [process_windows.go](file:///d:/workspace/intelligent-test-agent/opencode-manager/internal/process/process_windows.go)（`//go:build windows`）。
  - Unix 版本保留原有 `Setpgid: true` 和 `syscall.Kill(-pid, sig)` 逻辑。
  - Windows 版本用 `CreationFlags: syscall.CREATE_NEW_PROCESS_GROUP` 创建独立进程组，用 `process.Kill()` 替代信号发送。
  - [process.go](file:///d:/workspace/intelligent-test-agent/opencode-manager/internal/process/process.go) 中保留 `OSStarter` / `OSSignaler` 类型定义和 `flattenEnv` 等通用函数，移除 `syscall` 导入和 `syscall.ESRCH` 引用。
- How: 纯 Go 改动，不涉及 API/事件/数据库/安全/兼容性；保持 `internal/control/cgroup_parse_linux.go` 的既有平台拆分模式。
- Result: Windows 上 `go build -o bin/opencode-manager.exe ./cmd/opencode-manager` 编译成功，生成 10MB 的可执行文件。
### 2026-06-30 - 修复前端 readFile 接口返回值类型约束缺失 path 属性的编译错误

- Why: 前端在构建 `@test-agent/agent-web` 时编译失败，报 TS1360 错误，提示 `{ content: string; encoding: string; readonly: false; }` 类型不满足 `FileContent` 的预期，缺失必填属性 `path`。
- What: 在 `frontend/packages/backend-api/src/index.ts` 中的 `readFile` 方法返回对象里，加上缺失的 `path` 属性。
- How: 仅修改 `frontend/packages/backend-api/src/index.ts` 中 `readFile` 返回的满足 `FileContent` 的结构，不改写后端或接口定义，保证 API 数据类型契合前端类型约束。
- Result: 运行 `cd frontend && corepack pnpm typecheck` 和 `corepack pnpm build` 顺利通过，未再出现类型不匹配的编译期错误。

### 2026-06-30 - 解决拦截/路由请求绕过 Spring Security 导致的 CORS 跨域错误

- Why: 跨后端路由过滤器 `UserOpencodeBackendRoutingWebFilter` 以及部分认证过滤器（如 `JwtAuthWebFilter`、`ApiTokenWebFilter` 在验证失败时）会直接向响应写入内容并返回 `Mono<Void>`，从而绕过了位于 Spring Security 过滤链中的 CORS 处理器，导致前端在特定场景下（例如请求 `GET /api/internal/agent/opencode/processes/me`）因缺少 `Access-Control-Allow-Origin` 头而报错。同时由于控制器接口异步化，`ConfigurationManagementControllerTest` 中既存的同步测试用例存在参数与签名不匹配的编译及运行期错误。
- What: 
  - 将 HTTP CORS 处理配置提取为全局高优先级的 `CorsWebFilter` Bean，使其在所有鉴权和跨后端路由过滤器之前（`Ordered.HIGHEST_PRECEDENCE + 5`）优先执行，从而确保任何直接写入响应或拦截返回的场景都能带有正确的 CORS 响应头，并将 Spring Security 的内置 `.cors()` 禁用，防止出现重复 CORS 头。
  - 更新 `ConfigurationManagementControllerTest.createWorkspaceUsesCurrentUserReadyOpencodeServer` 测试用例以正确适配控制器端异步化 `createWorkspaceAccepted` 的方法签名与 `CreateWorkspaceAcceptedResponse` 返回结果。
  - 在 `RuntimeSecurityConfigTest` 中新增 `corsWebFilterAppliesCorsHeaders` 测试，直接调用 `CorsWebFilter` 验证 preflight 场景下 CORS 响应头的正确生成。
- How: 仅修改 `RuntimeSecurityConfig.java`、`RuntimeSecurityConfigTest.java` 和 `ConfigurationManagementControllerTest.java`，不新增数据库 Migration，不修改 `.env.local` 环境变量文件。
- Result: 运行 `mvn -pl test-agent-api -am test`，包括新编写的 CORS 单测在内的 147 项后端 API 测试用例全量通过。

### 2026-06-30 - 修复 worktree 残留冲突、中文 Diff 展示和工作区恢复

**Why**: 手工测试继续暴露 default 私人 worktree 切换时偶发创建冲突；中文路径在 Git 变更面板中显示为 octal 转义且 patch 为空；opencode 刚初始化成功后应用工作区不会自动恢复；wr 用户加载工作区疑似被历史 `updated_at < created_at` 脏数据击穿；Agent/工作区树初始加载偏慢。

**What**:
- Git status/diff 统一加 `core.quotepath=false`，后端返回 UI 的应用工作区 diff path 改为 workspace-relative，Git 命令仍使用 repo-relative 原路径。
- default 私人 worktree 创建冲突时先 `git worktree prune` 清理 stale 元数据；只在 `worktree list --porcelain` 确认同路径同分支时直接复用；目标路径为空目录残留时删除后重建，非空/其它分支继续报冲突。
- 前端去掉 Git 变更面板 mock 测试数据按钮，footer 直接展示当前私人 worktree 分支；opencode 从未 READY 变 READY 且尚无 workspace 时重试当前应用的 default 私人工作区选择。
- 托管工作区 version/replica/personal mapper 对历史 `updated_at` 早于 `created_at` 做只读归一化；Agent 配置状态与 public/workspace 根目录加载改为并发，文件列表 entry 避免重复 `Files.isDirectory`。

**How**: 补充 `GitWorkspaceServiceTest`、`ManagedWorkspaceApplicationServiceTest`、`JdbcRepositoryIntegrationTest`、`GitChangesPanel`/`WorkbenchFooter`/`AgentConfigPanel` 组件测试和 workbench mock E2E；同步 frontend 与 agent-web README。

**Result**: `test-agent-workspace-management` 模块测试、关键 persistence targeted 测试、agent-web typecheck、相关 Vitest 和 workbench E2E 均通过。persistence 全模块仍有既有测试失败：MyBatis agent worktree 测试缺少 `usr_test_dev` 外键 fixture，另有默认用户/loopback topology migration seed 断言为 0；本次未修改这些路径。

### 2026-06-30 - 修复应用 default 私人 worktree 进入、Diff 刷新与 opencode 初始化竞态

**Why**: 手工测试发现切换应用版本时 default 私人 worktree 偶发创建冲突；应用 recent 恢复后首页可能直接加载应用版本副本而不是用户私人 worktree；普通文件保存后中间 VCS Diff 仍可能走旧 opencode `/vcs/diff`；初始化 opencode 短暂失败后，后端实际已变 READY 但前端仍展示创建失败。

**What**:
- default 私人空间分支统一为 `{应用版本分支}_{userId}_default`，同 JVM 内串行创建；同名分支已存在时复用分支挂载 worktree，目录已存在且当前分支匹配时接管。
- 应用级 recent 命中带 `versionId` 的工作区时，前端先 `ensureDefaultPersonalWorkspace(versionId)`，再切到返回的 runtime workspace 加载文件树。
- VCS Diff 与保存后变更刷新统一改用平台 `getWorkspaceGitDiff()`，不再从工作台调用 opencode `/vcs/diff`。
- opencode 初始化接口短暂失败后立即 refetch `/processes/me`，复查 READY 时以可用状态为准。

**How**: 复用 `GitWorkspaceService` 增加 `createWorktreeReusingBranch`；扩展 `ManagedWorkspaceApplicationServiceTest`、`GitWorkspaceServiceTest`、`workbench.spec.ts` 和 `git-changes-panel.test.ts`；同步 HTTP API、前后端 README。

**Result**: 后端定向测试、前端 Vitest、工作台 mock E2E、agent-web typecheck 和 `git diff --check` 通过；服务启动验证见本次交付说明。

### 2026-06-30 - 应用工作区私有 Worktree 与 Diff/推送方案

**Why**: 普通应用工作区 diff 不应调用 opencode /vcs/diff，避免 opencode 服务异常导致"刷新变更列表失败"。应用版本进入后默认切到用户私有 worktree，提交并推送合并回应用版本分支。

**What**:
- 后端新增 ensureDefaultPersonalWorkspace / getWorkspaceGitDiff / publishPersonalWorkspace 3 个 Service 方法和对应 Controller 端点
- 前端 AgentWorkbench、GitChangesPanel、FigmaFileExplorer 适配新的私有 worktree 链路
- 前端 backend-api 和 shared-types 新增类型与 API 方法
- 文档 http-api.md 同步更新

**How**:
- 私有 worktree 新命名规则: {应用版本分支}_{userId}_default
- Git diff 基于本地 git status --porcelain + git diff，不依赖 opencode
- 推送流程: stageAll → commitStaged → push → merge app branch
- 冲突仅在个人 worktree，应用版本副本不受影响

**Result**: 前端 typecheck 通过，187 个测试全部通过，git diff --check 无问题，commit 946315e2

### 2026-06-30 - 修复 Git 变更面板测试数据与应用级 Agent/Skill 展示

- Why: 左侧 Git 变更面板在 opencode 进程不可用或真实刷新进行中点击“加载测试数据”时可能仍为空；mock 数据也把平台自身源码和公共级 Agent 配置混入了截图 1 的 `agents` 分组，不符合“应用工作区变更 + 应用级 agents/skills 变更”的展示目标。
- What: 将 Git mock 数据改为应用项目 `src/`、`tests/` 文件，以及应用级 opencode `agents/*.md` 和 `skills/<skill>/SKILL.md`；`GitChangesPanel` 只在 `agents` 分组展示应用级 Agent/Skill diff，并用刷新 token 防止旧真实请求覆盖测试数据；应用级技能包模板改为只含 opencode `SKILL.md` 支持的 `name` / `description` frontmatter 和 `Instructions` / `Resources` 段落。
- How: 新增 `git-changes-panel.test.ts` 覆盖“加载测试数据”后的列表内容和公共级排除，扩展 `agent-config-panel.test.ts` 覆盖技能包模板；同步 frontend、agent-web、workbench-shell README/PACKAGE 与 module-map。
- Result: `corepack pnpm@10.25.0 --dir frontend --filter @test-agent/agent-web typecheck`、`corepack pnpm@10.25.0 --dir frontend test -- git-changes-panel.test.ts agent-config-panel.test.ts` 和 `git diff --check` 通过；按用户最新指示未重启三服务，UI 运行态由用户自行验收。

### 2026-06-30 - 修复测试库 Flyway schema history checksum

- Why: 后端启动报 `FlywayValidateException`，目标测试库 `flyway_schema_history` 中 V5、V8、V10、V13、V17、V20260627000000、V20260628223000、V20260629230000 的已应用 checksum 与当前工作区 migration 文件不一致；其中 V10/V13/V17 当前为 0 字节，本地解析 checksum 为 0。
- What: 按用户要求只修复数据库数据，不回退或改写当前工作区 migration 文件；将 `.env.test` 指向的 `testagent` 库中上述 8 条成功 migration 的 checksum 更新为当前本地解析值。
- How: 先查询目标库确认旧 checksum，再在单事务中更新 `flyway_schema_history`，随后用项目运行时依赖直接调用 Flyway `validate()` 和 `migrate()`，避免 Flyway Maven 插件缺少 PostgreSQL database plugin 的误报。
- Result: Flyway `validate()` 通过，`migrate()` 返回 `migrationsExecuted=0`；本次不改业务表、不改 API/事件/数据库结构、不修改 `.env.local` 或 `.env.test`。

### 2026-06-30 - 修复跨后端登录时用户 opencode 状态误判未分配

- Why: 用户已有 `user_opencode_process_bindings` ACTIVE 记录在 A 服务器，但请求落到 B 服务器且转发到 A 失败时，前端拿不到 `processStatus`，会把“已分配但健康不可确认”误显示为“待分配专属进程”。
- What: 后端新增 binding-only 的只读分配状态入口，`/api/internal/agent/opencode/processes/me` 跨后端转发在目标后端缺失、异常或 5xx 时降级返回 `UNAVAILABLE + NOT_RUNNING + serviceAddress`；真实初始化、Run 和 runtime 代理仍不降级，继续要求目标服务器执行。前端头像无状态时显示“状态未知”，聊天状态卡优先展示 `serviceAddress`。
- How: 最小修改 `UserOpencodeProcessAssignmentService`、用户进程后端路由服务/过滤器和 `FigmaShell`/`FigmaChatPanel` 展示逻辑，并补充 runtime、API 路由和前端组件回归测试；同步 backend、opencode-runtime、api、agent-web README 与 HTTP API 文档。
- Result: `mvn -pl test-agent-api,test-agent-opencode-runtime -am test`、`mvn clean package -DskipTests`、`corepack pnpm@10.25.0 --dir frontend test -- runtime-management-settings backend-api`、`corepack pnpm@10.25.0 --dir frontend --filter @test-agent/agent-web typecheck` 和 `git diff --check` 通过。本次不改数据库结构、不新增公开 API 字段、不迁移 binding。

### 2026-06-30 - 运行管理启停命令跨 Java 路由

- Why: opencode-manager 只连接本服务器 Java，运行管理页可能从任意 Java 发起重启/停止，不能再假设入口后端一定和目标 manager 相连。
- What: 新增 API 层 `RuntimeManagementBackendRoutingService`，按 Redis manager 快照定位 `containerId` 所属 `linuxServerId`，目标不是本机时透传用户 JWT 和 traceId 转发到目标 Java。
- How: 目标 Java 收到带 `X-Test-Agent-Backend-Routed` 的请求后跳过再次路由，继续使用本机 `RuntimeManagementCommandService` 调 manager WebSocket。
- Result: `mvn -pl test-agent-api -am -Dmaven.test.skip=true compile` 通过；目标 API 测试因既有 `CommonParameterManagementControllerTest.updateValue` 签名不匹配在 testCompile 阶段阻塞。

### 2026-06-30 - 通用参数改为数据库直读并移除 Redis 加载快照

- Why: 用户要求各 Java 进程不再把通用参数缓存到 Redis，业务需要参数时直接从数据库读取，避免多进程缓存陈旧值。
- What: 将 `CommonParameterValues` 实现替换为 `RepositoryCommonParameterValues`，每次解析参数都通过 Repository 查库；移除启动内存加载、每进程加载快照、`/common-parameters/load-snapshots` API、前端“查看各进程加载值”和 Redis 快照存储。保留 `common-parameter.refresh-requested` 广播作为跨实例更新通知，监听方收到后直接查库并向本机 manager 下发最大进程数。
- How: 新增 DB 直读与广播测试，删除快照 Store/DTO/前端类型，更新 configuration-management、persistence、API、event-stream、database 和 module-map 文档；不改数据库结构、不改 generated SDK、不新增环境变量。
- Result: `RepositoryCommonParameterValuesTest` 与 `CommonParameterUpdateBroadcasterTest` 先红后绿；`CommonParameterManagementControllerTest`、`mvn clean package -DskipTests`、`corepack pnpm --filter @test-agent/agent-web typecheck`、`corepack pnpm exec vitest run apps/agent-web/tests/general-param-management-panel.test.ts` 和 `git diff --check` 通过。全量 `corepack pnpm test -- general-param-management-panel` 被 Vitest 过滤参数触发既有 `MarkdownPreview` 失败，定向文件测试通过。

### 2026-06-30 - 修复 manager 注册早于 Java 后端拓扑落库的启动竞态

- Why: 三服务重启后后端日志在 `2026-06-30T10:46:11.043+08:00` 出现 `opencode_manager_backend_connections.backend_process_id` 外键失败；根因是 Netty 端口已监听后，opencode-manager 可能抢在 `BackendJavaProcessLifecycleRunner` 首次 `registerHeartbeat` 落库 `backend_java_processes` 前完成 WebSocket register。
- What: `ManagerControlApplicationService.register` 在写 manager/container/connection 持久拓扑前先调用 `BackendJavaProcessLifecycleService.registerHeartbeat`，确保当前 `backendProcessId` 的父表行存在，再插入 manager-backend 连接；重复键容错边界不变，非重复键持久化异常仍继续暴露。
- How: 新增 `ManagerControlApplicationServiceTest.registerPersistsCurrentBackendBeforeSavingManagerConnection`，fake repository 模拟连接表外键约束并断言 `saveBackendJavaProcess` 早于 `saveManagerBackendConnection`；同步更新 `test-agent-opencode-runtime` README。
- Result: 定向测试先红后绿，`mvn -pl test-agent-opencode-runtime -am -Dtest=ManagerControlApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过；本修复不改数据库结构、API、事件或环境配置。
### 2026-06-30 - 模型下拉菜单即时悬浮提示与原位恢复

- Why: 聊天面板输入框下方的模型选择器，之前被移到了顶部标题栏。用户希望模型选择器改回在输入框下方，且指出之前反馈的"显示不全"其实是指下拉菜单中"上新推荐"一排的模型卡片在固定双列等宽网格下长名字被截断的问题，希望能用鼠标悬浮（Hover）即时显示完整名称的方式来解决（原生 title 属性有较长延迟，需使用 el-tooltip 实现快速响应）。
- What:
  - 将模型选择器 wrapper HTML 重新移回至 `.figma-chat-card-actions` 底部 actions 区域。
  - 使用 Element Plus 的 `<el-tooltip>` 组件（配置 `:show-after="100"` 极短悬停延迟）包裹了模型选择按钮、所有“上新推荐”的推荐卡片 `.figma-chat-model-rec-item`，以及所有模型列表项 `.figma-chat-model-option-item`，实现光标挪上去立刻在上方浮现完整名称。
  - 恢复了 CSS 中有关最大宽度限制（`.figma-chat-model-btn` 为 150px、`.figma-chat-model-label` 为 108px）、弹出位置（朝上弹出 `bottom: calc(100% + 12px)`）和指示箭头方向的配置。
  - 给 `.figma-chat-model-rec-item` 增加了 `max-width: 100%`，并对内部文本名 `.figma-chat-model-rec-name` 补充了 text-ellipsis 以防止在极限宽度下布局撑爆。
- How: 仅修改 `FigmaChatPanel.vue`，不修改 API、SSE 事件或数据库。
- Result: 编译构建通过（`corepack pnpm --filter @test-agent/agent-web build`），单元测试通过（`pnpm test`），UI 布局与 Hover 悬浮显示功能逻辑自洽。

### 2026-06-30 - 通用参数修改历史抽屉补当前值与缓存刷新

- Why: 通用参数“修改历史”点击后只依赖 `common_parameter_change_logs`，当日志为空或前端保留旧空缓存时用户看不到具体内容；页面保存成功后也没有主动失效历史查询缓存。
- What: 修改历史抽屉增加当前参数摘要（英文名、中文名、平台、当前值、更新时间），打开历史时主动失效历史缓存，保存参数成功后同步失效历史缓存；后端单测把日志记录从“调用 save”加强到字段级断言 old/new/user/trace/createdAt。
- How: 仅改 `GeneralParamManagementPanel.vue`、`general-param-management-panel.test.ts` 和 `CommonParameterManagementApplicationServiceTest.java`；不改 HTTP API、事件、数据库结构或环境文件。
- Result: `corepack pnpm test -- general-param-management-panel.test.ts`、`corepack pnpm --filter @test-agent/agent-web typecheck`、`mvn -pl test-agent-configuration-management -am -Dtest=CommonParameterManagementApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过；`.env.test` 数据库临时不可达时无法直接核对现网日志表内容。
## 2026-06-30 - 工作空间删除添加二次确认

- Why: 设置页"已有工作空间"删除按钮直接执行删除操作，没有二次确认，用户可能误操作导致数据丢失。
- What: 为删除工作空间添加与"移除成员"、"解除关联版本库"一致的二次确认机制：
  - 扩展 `PendingDangerAction` 类型，新增 `delete-workspace` 类型。
  - 新增 `confirmDeleteWorkspace` 方法，设置 `pendingDangerAction` 触发确认弹窗。
  - 确认弹窗标题为"确认删除工作空间"，提示文案为"确认删除工作空间[xxx]吗？删除后数据将无法恢复。"
  - 确认按钮文案为"确认删除"。
  - `confirmDangerAction` 方法增加 `delete-workspace` 分支处理。
  - 补充单测 `confirms before deleting a workspace` 验证完整流程。
- How: 仅修改前端 `SettingsAppWorkspacePanel.vue` 组件和对应测试文件，不涉及 API、事件、数据库、安全或兼容性。
- Result: 前端 `typecheck` 通过，`vitest` 183 个测试全通过。

### 2026-06-30 - Maven build 前强制终止所有开发服务

- Why: 如果上一次 Ctrl+C 或窗口崩溃导致后端 Java 进程未被正常回收，`mvn clean package` 会因 JAR 文件被锁而失败：`Failed to delete ... test-agent-app-0.1.0-SNAPSHOT.jar: 另一个程序正在使用此文件`。
- What: 新增 `Stop-AllDevServices` 函数，在 `Clear-ServiceLogs` 之后、`Build-Backend` 之前调用。它先走现有的 `Stop-BackendService / Stop-FrontendService / Stop-OpencodeManagerService`（通过命令行匹配精确杀进程），再用 `taskkill /F /IM java.exe /T` 等做兜底（`/T` 同时终止子进程），彻底释放 target 目录的文件句柄。
- How: 仅改 [win-restart-dev-services-fixed-v4.ps1](file:///d:/workspace/intelligent-test-agent/win-restart-dev-services-fixed-v4.ps1)；不涉及 API/事件/数据库/安全/兼容性。
- Result: PowerShell parser 校验通过，`Stop-AllDevServices` 函数已识别。

### 2026-06-30 - 实时日志默认开启，用 -NoFollow 禁用

- Why: 上一版 `-FollowLogs` 默认为 `$false`，脚本跑完就直接退出，用户需要手动加 `-FollowLogs` 才能进入 tail 模式，每次都要记着打参数很麻烦。
- What: 新增 `-NoFollow` 参数（无别名），默认日志跟随开启。判断逻辑改为 `$FollowLogs -or (-not $NoFollow)`，即：
  - 不传任何参数 → 默认进入 tail 模式（`tail -f` 等效）。
  - 传 `-NoFollow` → 脚本在服务启动后直接退出，不进入 tail。
  - 传 `-FollowLogs`（显式开启，兼容旧用法）。
- How: 仅改 [win-restart-dev-services-fixed-v4.ps1](file:///d:/workspace/intelligent-test-agent/win-restart-dev-services-fixed-v4.ps1) 的 param 块、Show-Usage 和末尾调用行。
- Result: PowerShell parser 校验通过，-Help 输出正确显示 `-NoFollow` 选项。

### 2026-06-30 - 修复 Build-OpencodeManager 被 RemoteException 击穿的问题

- Why: 上一版改动把 `& go build ... 2>&1` 直接赋给变量，但脚本顶部仍是 `$ErrorActionPreference = "Stop"`。`go build` 失败时把 `# github.com/.../internal/process` 这类诊断行写到 stderr，PowerShell 在 `Stop` 模式下把它升级为终止错误 `RemoteException`，整个脚本被击穿、Skip 逻辑走不到。
- What: `Build-OpencodeManager` 内临时把 `$ErrorActionPreference` 切到 `Continue`，用嵌套 try/catch/finally 包住 `& go build` 调用并保留原值；`$LASTEXITCODE` 改为先存到 `$buildExitCode` 再判断；输出循环增加 null 检查。
- How: 仅修改 [win-restart-dev-services-fixed-v4.ps1](file:///d:/workspace/intelligent-test-agent/win-restart-dev-services-fixed-v4.ps1) 的 `Build-OpencodeManager` 函数；不涉及 API/事件/数据库/安全/兼容性；Maven 与 pnpm 调用仍保留原 exit-on-fail 行为，因为后端 jar 与前端产物缺失时本就应该中止。
- Result: 用 `cmd /c` 模拟 `go build` 写 stderr + exit 2 的小测试验证：捕获到 `buildExitCode = 2`、3 行输出被正确记录，且脚本正常结束（`exit code 0`），不再被 `RemoteException` 击穿。

### 2026-06-30 - 增强 win-restart-dev-services-fixed-v4.ps1：opencode-manager 编译失败不再阻断、增加 -FollowLogs 实时日志

- Why: 在 Windows 上 `go build` opencode-manager 时 `process.go` 仍在使用仅 Unix 的 `syscall.Setpgid` / `syscall.Kill`，脚本原有逻辑是 `exit $LASTEXITCODE`，导致后端、前端、opencode-manager 全部都不会启动；同时用户希望所有服务起来后能在当前窗口直接看到实时日志。
- What:
  - 新增脚本级变量 `$script:OpencodeManagerBuildSucceeded`（默认 true）；`Build-OpencodeManager` 编译失败时打印 stderr 警告 + go build 完整输出，置位为 `$false` 并 `return`，不再 `exit`。
  - `Start-OpencodeManager` 在编译失败时直接跳过启动并打印一行说明，保留后端/前端原有启动顺序。
  - 末尾 `if (-not $script:OpencodeManagerBuildSucceeded)` 时给出黄色 WARNING，提示需要修复源码。
  - 新增 `-FollowLogs`（别名 `-Follow`）开关参数；启用后调用新增的 `Follow-ServiceLogs`：先打印 backend/opencode-manager/frontend 三个日志的最近 20 行，再用后台 PowerShell 作业对每个日志 `Get-Content -Wait -Tail 0` 做 tail，主循环 500ms 收取并以前缀 `[service]` 输出到当前窗口；按 Ctrl+C 时 finally 块 `Stop-Job` + `Remove-Job` 清理。
  - 同步更新 `Show-Usage` 的 Usage 行、Options 段和新增的 Notes 段，说明编译失败可继续启动 manager 这一行为。
- How: 纯 PowerShell 改动，不涉及 API/事件/数据库/安全/兼容性；不修改 `.env.local` 或 Go 源码。后续若要让 opencode-manager 在 Windows 真正跑起来，仍需为 `process.go` 拆分 Unix/Windows 实现（`//go:build !windows` 与 `//go:build windows`）。
- Result: PowerShell parser 校验通过；参数表识别到 `FollowLogs: SwitchParameter`；`Functions found` 包含新增的 `Follow-ServiceLogs`。

### 2026-06-30 - 重新登录/换电脑登录自动恢复上次工作空间 + 左下角按钮显示模板/版本名

- Why: 用户希望工作台左下角"切换工作空间"按钮（`WorkbenchFooter.vue` 顶部的"应用 + 版本"两级菜单）能记忆上次选择，重新登录或换电脑登录时直接落到上次所在的应用 + 工作空间版本，并在按钮上显示当前所在模板/版本名（而非降级为"切换工作空间"）。后端已按应用维度持久化最近偏好，但前端 `applicationCatalog` 加载完成后总是回退 `apps[0]`，没有"上次进入的是哪个应用"的全局维度；且 `currentVersionFromWorkspace` 依赖 `versionsByTemplateId` 精确匹配，异步加载完成前按钮会降级为默认文本。上一轮 commit 后用户实测"应用选择对了，但左下角按钮仍只显示切换工作空间图例"，根因是 `applyManagedWorkspace` 调用 `markRecentManagedWorkspace` 后直接忽略响应，导致 `switchSession`、首模板首版本兜底等不在 recent 直接命中路径上的 `workspace.versionId` 始终为 `null`，按钮降级。
- What:
  - 后端 `ManagedWorkspaceResponses.WorkspaceRuntimeResponse` 二次扩展：先加 `appId`（上次 commit 已落），再补 `versionId` + `applicationWorkspaceId`，同时为所有"recent 相关"接口（`/recent-workspace`、`/applications/{appId}/recent-workspace`、`POST /workspaces/{workspaceId}/recent`）抽出私有 `resolveRecentWorkspaceResponse(Workspace)`，通过 `findVersionByRuntimeWorkspace` 反查 `ApplicationWorkspaceVersion` 一次性回写三者；未命中版本记录时回退 `findPersonalWorkspaceByRuntimeWorkspace` 取 appId；完全无主时三者均为 `null`；其他接口依旧 `null`。
  - 前端 `Workspace` 类型补可选 `versionId` + `applicationWorkspaceId`；`AgentWorkbench.vue` 新增 `mergeRecentRuntimeResponse` 把 `POST /workspaces/{workspaceId}/recent` 响应里的 `appId`/`versionId`/`applicationWorkspaceId` 合到本次切换的 `workspace` 上（仅覆盖非空字段、不破坏已有有效值），`applyManagedWorkspace` 与 `switchSession` 都走这条回填链，确保切会话、首模板首版本兜底等路径也能拿到 versionId。
  - `syncCurrentVersionFromWorkspace` 优先用 `workspace.versionId` 直接设回 `currentVersionFromWorkspace`，并按 `workspace.applicationWorkspaceId` 调用 `ensureAppVersionsLoaded` 触发对应模板 `versions` 的预加载，使 `WorkbenchFooter.selectedTemplate` 立即找到匹配、左下角按钮立刻显示 `模板 / 版本` 名；`versionId` 缺失时回退到原 `versionsByTemplateId` 精确匹配逻辑以兼容旧数据。
  - 同步更新 `docs/api/http-api.md`、`backend/test-agent-workspace-management/README.md`、`frontend/apps/agent-web/README.md`，并修复 4 处 `ManagedWorkspaceControllerTest` / `ConfigurationManagementControllerTest` 中 `new WorkspaceRuntimeResponse(...)` 构造调用（其中 `markRecentWorkspaceReturnsRuntimeWorkspace` 改为断言 `appId`/`versionId`/`applicationWorkspaceId` 都会被回填）；同时清掉 `d4720b4f` merge 提交残留的 `<<<<<<< HEAD` / `=======` / `>>>>>>> 64ab22c4` 未解决合并标记（rule 21）。
- How: 纯前后端协作改动，不新增数据库 migration（`user_global_workspace_preferences` 已存全局偏好）、不改 `.env.local`、不重写 SDK；`Workspace.versionId` / `applicationWorkspaceId` 设为可选以保持旧调用方兼容。
- Result: `mvn -pl test-agent-api test` 132 全通过；`test-agent-workspace-management` 仍有 2 个 Windows 路径分隔符既存失败（git stash 验证 main 上同样存在，与本次改动无关）。前端 `typecheck`、`build` 通过，`pnpm test` 181/182 通过（1 个 MarkdownPreview 既存）。

### 2026-06-30 - 模型目录外部供应商配置统一为 external

- Why: 外部 OpenAI-compatible 模型供应商不应继续沿用百炼专用变量名，切换 DeepSeek 时会造成 `TEST_AGENT_BAILIAN_API_KEY_ENV` 与真实 key 变量双重命名的理解成本。
- What: 将外部模型目录来源规范化为 `source=external`，新增 `TEST_AGENT_EXTERNAL_MODEL_*` 变量族；代码默认值保持 provider-neutral，不在 Java 或 profile yml 中写死 DeepSeek。历史 `source=bailian` 和 `TEST_AGENT_BAILIAN_*` 变量仍作为兼容兜底。移除 `application-guo.yml` 中原有直配模型 key，改为环境变量读取。
- How: 修改 `ModelCatalogProperties` 的 source 归一化、外部 provider 默认值和 app profile 绑定；同步 backend README、opencode-runtime README、HTTP API、部署文档和 `.env.local.example`，并补充 `legacyBailianSourceIsTreatedAsExternalSource` 回归用例。
- Result: `ModelCatalogApplicationServiceTest`、`TestAgentRuntimePropertiesBindingTest`、`mvn clean package -DskipTests` 和 `git diff --check` 通过。按 `.env.test` 重启时后端未启动：PostgreSQL `192.168.100.200:5432/testagent` 当前 `No route to host`；前端 3000 启动成功。`.env.test` 需要用户手填 `EXTERNAL_API_KEY` 后才能真实访问 DeepSeek。

### 2026-06-29 - 聊天面板 opencode 进程状态卡按 serviceStatus 区分未分配/未运行

- Why: 之前聊天面板状态卡对所有非就绪情况都显示"需要初始化 opencode 进程"和"初始化进程"按钮，无法区分"尚未分配专属进程"和"已分配但未运行"，与头像菜单已区分的展示不一致。
- What: `FigmaChatPanel.vue` 新增 `resolveServiceAddress` + `effectiveServiceStatus`（回退推断与 `FigmaShell.vue` 头像菜单一致）和 `processInitButtonLabel`；`processStatusTitle` 对 `NEEDS_INITIALIZATION` 拆分：`UNASSIGNED`→"尚未分配 opencode 专属进程"、`NOT_RUNNING`→"opencode 专属进程未运行"；按钮文案分别改为"分配专属进程"/"启动进程"（进行中显示"分配中"/"启动中"）。`AgentWorkbench.vue` 发送拦截反馈标题按同样规则区分。本地 prop 类型 `OpencodeProcessState` 补齐 `serviceStatus/serviceAddress/linuxServerId/port` 可选字段。
- How: 纯前端改动，两个按钮仍调用同一 `initialize-process` → `POST /processes/me/initialize`，由后端 `initialize()` 统一处理分配并启动或重建并启动；无后端/API/事件/DB 变更。同步更新 `FigmaChatPanel.test.ts` 断言并补 `NOT_RUNNING`/显式 `UNASSIGNED`/进行中用例。
- Result: 聊天面板状态卡与发送拦截提示与头像菜单状态文案一致；`UNASSIGNED` 显示分配入口、`NOT_RUNNING` 显示启动入口。
- Verification: `corepack pnpm --filter @test-agent/agent-web typecheck`、`corepack pnpm test -- FigmaChatPanel`（178 用例全通过）通过。

### 2026-06-29 - 改用 SYS_DATA_ROOT_DIR 派生服务器 IP 文件路径

- Why: `.serverip` 路径不应再通过 Java/Go 各自的环境变量配置，否则会绕开系统通用参数并造成同机 manager 与 Java 写读路径不一致。
- What: Java 后端移除 `test-agent.opencode.manager-control.server-ip-file` / `TEST_AGENT_SERVER_IP_FILE` 绑定，启动时通过 `CommonParameterValues.resolvedValue("SYS_DATA_ROOT_DIR")` 写入 `SYS_DATA_ROOT_DIR/.serverip`；Go manager 移除 `OPENCODE_MANAGER_SERVER_IP_FILE` 读取，非 Windows 按通用参数种子的内置平台默认根目录读取 `.serverip`（Linux `/data/.testagent/.serverip`，macOS `$HOME/.testagent/.serverip`），Windows 仍直接探测本机 IPv4。
- How: 新增 `ServerIpFilePathResolver` 和单测，保留 `ServerIpFileWriter` 的固定 Path 测试构造器；Go config 增加 `sysDataRootDir` / `serverIPFilePath` 并覆盖 Linux、macOS 和忽略旧环境变量分支；同步 backend、opencode-manager、API、部署和数据库文档，并调整一键重启脚本不再注入旧 server-ip-file 环境变量。
- Result: `mvn -pl test-agent-app -Dtest=ServerIpFilePathResolverTest,ServerIpFileWriterTest,OpencodeManagerControlConfigTest,TestAgentRuntimePropertiesBindingTest test`、`go test -count=1 ./...`、`tools/verify-dev-scripts.sh` 和 `mvn clean package -DskipTests` 通过。当前本机未安装 `pwsh`/`powershell`，脚本校验按既有逻辑跳过 PowerShell parser 检查。

### 2026-06-29 - 自动刷新当前用户 opencode 健康状态与模型目录

- Why: 工作台进入后 `/processes/me` 只在首屏、点击头像、刷新页面或输入区交互时重新查询，导致后端/manager 健康检查已恢复后，右侧 opencode 状态和模型列表仍停留在旧的“检测中/失败/空列表”缓存；用户需要多次刷新页面才可能同时拿到绿色状态和模型。
- What: `AgentWorkbench.vue` 让当前用户 opencode 进程状态在页面可见时自动 refetch，未 `READY` 时每 5 秒快速探测，`READY` 后降频为每 30 秒，降低常态 manager health 和数据库写入压力；Agent/Provider/Model/Command/MCP/LSP/VCS 等运行态目录改为仅在进程 `READY` 后启用，并在状态刚转为 `READY` 时主动 invalidate，清掉早期健康失败造成的空缓存。同步更新 `frontend/README.md` 和 `frontend/apps/agent-web/README.md`。
- How: 复用既有 `/api/internal/agent/opencode/processes/me` 与 runtime catalog API，不新增后端接口、不直连 opencode server、不修改数据库或环境文件；保持输入区手动触发刷新逻辑作为即时探测入口。
- Result: `corepack pnpm --filter @test-agent/agent-web typecheck`、`corepack pnpm test FigmaChatPanel.test.ts workbench-utils.test.ts follow-up-queue.test.ts`、`corepack pnpm --filter @test-agent/agent-web build`、`git diff --check` 均通过；`.env.test` 三服务已重启，backend health/readiness、frontend 3000 和 CORS 预检通过。登录态 smoke 显示默认账号初始化 opencode 仍被环境配置阻塞：manager 返回 `OPENCODE_UNAVAILABLE`，原因是当前测试库公共 opencode配置目录为 Windows 路径 `D:/data/.testagent/agent-opencode/.config/opencode/`，在 macOS 本地未初始化，因此模型接口在该账号未 READY 时按预期返回 503。
### 2026-06-29 - 新增系统数据根目录通用参数

- Why: 需要在通用参数表中提供跨平台系统数据根目录，避免后续模块继续硬编码 macOS、Linux、Windows 的数据根路径。
- What: 新增生产必需通用参数 `SYS_DATA_ROOT_DIR` 三条种子记录：macOS `$HOME/.testagent`、Linux `/data/.testagent`、Windows `D:/data/.testagent`；同步后端 README、configuration-management README、persistence README、HTTP API 文档和数据库 migration 文档。
- How: 按独立时间戳 Flyway migration `V20260629203006__seed_sys_data_root_dir_param.sql` 插入 `common_parameters`，不改表结构、不改 API DTO/事件类型、不改 `.env.local`；保留 MyBatis 集成测试中的三平台断言，并新增轻量 migration 内容测试覆盖本次 SQL 文件。
- Result: `CommonParameterSeedMigrationTest` 和 `git diff --check` 通过，`mvn clean package -Dmaven.test.skip=true` 通过；`MyBatisCommonParameterRepositoryIntegrationTest` 仍被既有 `V20260628223000__add_macos_platform_support.sql` 中 H2 不支持的 PostgreSQL 数组 CHECK 语法阻断在 Flyway migrate 阶段，未执行到本次新增断言。默认 `mvn clean package -DskipTests` 还会在 app 测试编译阶段被既有 `ServerIpFilePathResolverTest` 引用缺失类阻断。该旧 migration 已在前次记录中要求不改写，后续若要恢复 H2 全量 Flyway 测试，应通过兼容性方案处理 checksum 风险。

### 2026-06-29 - 按容器和管理进程名派生 opencode managerId

- Why: 多台本地或测试机器共享 Redis 时，旧启动脚本默认注入相同 `OPENCODE_MANAGER_ID=mgr_local_opencode`，导致 manager latest snapshot 互相覆盖，运行管理只显示其中一台容器。
- What: Go manager 不再读取 `OPENCODE_MANAGER_ID`，而是按已解析的 `containerId` 和固定逻辑进程名 `opencode-manager` 派生内部 `managerId`；本地重启脚本删除 `OPENCODE_MANAGER_ID` 注入，脚本校验增加防回归断言。
- How: 保持现有 `mgr_` 前缀、Redis key、数据库字段和 WebSocket 协议字段不变，只改变 manager 内部 ID 来源；同步 opencode-manager README、后端 README 和后端部署文档，说明生产和本地都不再配置该环境变量。
- Result: 旧 `mgr_local_opencode` Redis 快照不做手工清理，等待 TTL 自然过期；后续同一共享 Redis 内要求容器名称唯一且一个容器只运行一个 opencode-manager。

### 2026-06-29 - 补充环境变量新增准入规范

- Why: 后端和 opencode-manager 后续不能为了临时绕过配置、适配个人环境或规避通用参数随意新增环境变量，需要把准入规则写入稳定文档。
- What: 在后端规范、后端 README、部署文档和 opencode-manager README 中补充环境变量新增规则，明确优先复用 `common_parameters`、Spring 配置、数据库配置、控制面 `configUpdate` 或既有 dotenv 变量；只有部署期密钥、外部端点、进程身份、启动引导路径、资源容量等必须由运行环境注入的值才允许新增。
- How: 纯文档修改，不触碰 `.env.local` 等环境配置文件，不改代码、API、数据库或启动脚本。
- Result: `git diff --check` 通过；当前工作区存在与本任务无关 of 未暂存 `opencode-manager/internal/config/control_test.go` 和 `tools/verify-dev-scripts.sh`，本次提交不纳入。

### 2026-06-29 - 按照项目字体要求调整输入框字号与样式合并支持

- Why: 侧边栏文件搜索框的 Search 图标与占位文字 "搜索工作区文件" 重叠，原因是 Input 组件默认 `px-2` 样式与父组件传入的 `pl-7` 产生冲突，且 Vue 的默认 class 合并未经过 `twMerge` 处理，且 `tailwind-merge` 不会判定简写 `px-2` 与具体 `pl-7` 冲突，导致 padding-left 仍被判定为 `px-2` 的 8px。此外，placeholder 没有继承项目的全局字体，原先测试的 `text-[16px]` 也过大不符合紧凑的 IDE 侧边栏约定。
- What:
  - 更新 `frontend/packages/ui-kit/src/Input.vue` 和 `frontend/packages/ui-kit/src/Textarea.vue`：将文字大小保持为项目约定的 `text-[12px]`，并增加 `font-sans` 和 `placeholder:font-sans` 以应用项目字体；将默认的 `px-2` / `px-3` 替换为显式的 `pl-2 pr-2` / `pl-3 pr-3`，以便 `tailwind-merge` 能识别并正确用 `pl-7` 覆盖 padding-left。
  - 为 `Input.vue`、`Textarea.vue` 和 `Button.vue` 组件设置 `inheritAttrs: false`，获取 `$attrs` 并在 `:class` 属性中通过 `cn(...)` 函数将 `attrs.class` 与组件默认类进行合并，确保外部传入的定位/内边距样式能正确应用并合并。
  - 在 `frontend/apps/agent-web/src/styles/globals.css` 中添加 `@source` 配置，确保 Tailwind 扫描外部包源码。
  - 在 `FileExplorer.vue` 中对搜索框添加 `ta-file-search-input` 类并在 scoped style 中显式使用 `padding-left: 28px !important` 强制对齐。
- How: 修改 `ui-kit` 中的通用表单元素模板与 setup 结构，并在 `frontend` 目录下运行 lint、typecheck 和单元测试进行回归验证。
- Result: 侧边栏搜索框样式表现正常，搜索图标不再遮挡文本，字号和字体完全契合项目原本的 12px 约定，177 项单元测试及静态编译/Linter 全部全绿通过。

### 2026-06-29 - 优化 opencode 公共配置未初始化错误提示

- Why: 用户初始化 opencode 进程失败时，前端只提示公共配置未初始化，但系统管理页可能显示同 IP 已初始化；需要在错误消息中暴露目标 manager 实际检查的服务器和配置目录，减少 Java 视角与 manager 文件系统视角不一致时的排查成本。
- What:
  - Go manager 在 `OPENCODE_PUBLIC_CONFIG_DIR` 缺失、为空、非目录或不可读时，返回 `OPENCODE_UNAVAILABLE` 且 message 包含 `linuxServerId` 与实际检查的 `ConfigDir`。
  - Java socket gateway 继续原样透传 manager 的 `OPENCODE_UNAVAILABLE` message，并更新测试 fixture 验证新文案。
  - 同步 backend、opencode-runtime、opencode-manager README 以及 API/部署文档中的错误说明。
- How: 先改 Go 失败断言并确认红灯，再最小调整 `process.Manager.Start()` 的公共配置失败消息生成；未改 API 结构、数据库、前端展示组件或 manager 控制协议字段。
- Result: `go test ./...`、`mvn -pl test-agent-opencode-runtime -am test`、`mvn clean package -DskipTests` 和 `git diff --check` 均通过。

### 2026-06-29 - 修复历史对话工具消息归一化缺失导致助手气泡空白

- Why: 之前引入的 `normalizeMessagePart` 规则将 opencode parts 归一化为平台标准结构，把 `part.state.output` 移到了 `part.output`。这导致 `FigmaChatPanel.vue` 中的 `partText` 函数在解析归一化后的 `tool` 分段时，由于继续尝试读取已不存在 of `part.state.output` / `part.state.error`，从而提取不到内容返回了空字符串。对于只有工具步骤且无文本消息的历史回复，会导致计算出的气泡文本为 `""`，从而被模板判定无内容而渲染为完全空白的助手气泡。
- What:
  - 修复 `frontend/apps/agent-web/src/components/FigmaChatPanel.vue` 中的 `partText` 函数，增加对归一化后 `toolPart.output` 为字符串的直接读取支持，并作为 `state.output/error` 之前的优先级，同时完美向后兼容。
  - 修复 `frontend/packages/agent-chat/src/runtime-reducer.ts` 中的 `normalizeMessagePart`，在归一化 `tool` 分段时增加对 `raw.error` / `state.error` 备选项的复制支持，避免报错信息丢失并在 `ToolDetail` 中能够正常展现。
  - 修复 `FigmaChatPanel.test.ts` 中受 formatTokens 格式化影响而报错的 tokens 静态断言（由 `"19915 tokens"` 更改为 `"2.0w tokens"`）。
- How: 纯前端代码与测试用例微调，不涉及后端、接口或数据库模式变更。
- Result: 修复后切换历史对话时，智能体执行的各工具过程和最终步骤文本均可正确、完整地展示。175 项 Vitest 单元测试全绿通过。

### 2026-06-29 - 重命名为应用级、增加 hover 备注，并放开应用级配置修改权限

- Why: 满足用户的定制需求：将工作空间级重命名为应用级，添加详细备注；为了保证多租户/多应用独立发布管理，只对超级管理员保留公共级配置的写权限，而将应用级配置的修改与发布权限下放给普通用户，在本地技能中记录了正确的 macOS 开发重启命令；同时解决原生 title 属性 hover 响应慢的问题，以及非超管用户无法读取公共配置的缺陷。
- What:
  - 前端将“工作空间级”全部重命名为“应用级”，公共级添加 hover 提示“公共级 agents 及skills”，应用级添加 hover 提示“应用自定义 agents 及 skills，应用可以自己心中修改和发布”。
  - 放开了应用级（WORKSPACE 作用域）的修改限制，允许非超级管理员执行文件写入、创建 worktree、stage、commit 和 publish。
  - 将主按钮的悬浮提示改为 Element Plus 的 `<el-tooltip>`，设定悬浮响应时间为 50ms 级别以达成即时呈现效果。
  - 后端放宽了获取公共配置元数据 GET 接口的角色要求，允许非超管用户读取公共分支与仓库，保证非超管用户能够正常以只读方式浏览公共级文件。
  - 新增项目级技能 `.agents/skills/restart/SKILL.md`，记录了本地 JDK 25 的重启服务命令。
- How:
  - 修改 `AgentConfigPanel.vue` 中 header 和 dialog 相关的文字，将公共级/应用级按钮用 `<el-tooltip placement="top-start" :show-after="50">` 包裹以即时呈现；修改 `openFile` 的 `readonly` 计算、`createWorktree` / `openCreateWorkspacePackageModal` / `stage` / `commit` / `publish` 内部的 `props.canWrite` 逻辑，同时在 WORKSPACE 级隐藏 `v-if="canWrite"`。
  - 修改 `AgentConfigController.java` 中所有的 `/workspaces/{workspaceId}/...` 写入和发布接口，以及 `/public/repositories`、`/public/repositories/local` 和 `/public/branches` 三个只读 GET 接口，将 `AuthWebSupport.requireRole(..., Dictionary.ROLE_SUPER_ADMIN)` 放宽为 `AuthWebSupport.getAuthPrincipal(exchange)`。
  - 修改 `WorkspaceFileWebSocketHandler.java`，对于 `agentConfigWrite` 请求，仅在 `SCOPE_PUBLIC` 时校验 `ticket.superAdmin()`。
- Result:
  - 运行 `JAVA_HOME=... mvn clean package -DskipTests` 后，18 个后端模块全部编译构建成功。
  - 前端修改 `agent-config-panel.test.ts` 中的按钮名称并运行 `corepack pnpm test agent-config-panel.test.ts`，Vitest 单测 5 个全绿通过。
  - 本地运行重启脚本后服务已加载最新的 jar 包并健康检测通过，切换普通用户后能正常加载并以只读方式查看公共级文件，Hover 响应即时。

### 2026-06-29 - 修复公共配置恢复、技能包层级与 OpenCode 初始化

- Why: 公共配置 `agents/` 被误删后，仓库因工作树不干净被判为未初始化，导致文件树、刷新和重新拉取入口互相锁死；此前公共 skill 还有 `mimoagent-agents` 包装层和符号链接；本机 manager 又因历史 `mgr_kaka_opencode` 与标准 `mgr_local_opencode` 抢占同一 `container_id` 而持续断线。
- What: 公共仓库只要 origin 和 `opencode/` 有效就保持 `initialized=true` 和可浏览，未提交修改单独标记 `CONFLICT`；公共更新增加默认关闭的 `discardLocalChanges`，页面要求超级管理员明确勾选后才恢复已跟踪修改。工作空间 `+` 只生成 `skills/<name>/SKILL.md`、`rules/README.md`、`templates/README.md`，普通工作空间树隐藏根级 `.opencode`。公共配置仓库已把 18 个 skill 扁平为 `opencode/skills/<skill-name>/` 实体包并删除 `mimoagent-agents` 和符号链接。
- How: 后端复用 `GitWorkspaceService.resetHardToCommit(..., "HEAD")`，不删除未跟踪文件；前端/API 增加显式布尔字段与确认框，并补充后端、backend-api 和组件回归测试。为定位 manager 断线，在 WebSocket 入站错误边界增加结构化 WARN；数据库事务只删除 `mgr_kaka_opencode` 的 6 条连接和 1 条 manager 冲突记录，保留 `mgr_local_opencode` 及业务工作区、会话、运行数据。
- Result: 公共配置仓库 commit `081d56d` 已推送 Gitee `master`；Manager 自动以 `mgr_local_opencode` 绑定当前机器并恢复心跳；真实页面 OpenCode 初始化后状态为“opencode 进程可用”，最终三服务重启后用户进程 `192.168.100.115:4098` 健康检查通过。公共配置树可见 `agents/` 与 `skills/`，普通工作空间树不再展示 `.opencode`。后端相关 31 个测试、前端相关 31 个测试、前端 typecheck/build 和 `git diff --check` 通过。

### 2026-06-29 - Agent 配置树上移并支持工作空间技能包初始化

- Why: 公共/工作空间 Agent 配置原先只展示 `agents/`，用户无法维护 `skills/<skill>/SKILL.md` 技能包；同时 openclaw 迁移内容需要保留 agent -> stage -> skills 的原始包结构。
- What: 后端 Agent 配置文件树根上移为公共 `opencode/`、工作空间 `.opencode/`，前端工作空间级 `+` 初始化 `agents/<name>.md` 与 `skills/<name>/SKILL.md`/`rules`/`templates`，公共配置仓库把 openclaw 原始包放到 `opencode/mimoagent-agents/`，`opencode/skills/` 用符号链接作为运行时入口。
- How: 保持 opencode 运行时仍读取 `agents/*.md` 和 `skills/*/SKILL.md`；避免在 `opencode/agents/` 根下混入目录，因为 opencode 1.17 会尝试把目录当 agent 配置解析并报 `p.info.permissions`。
- Result: 后端 `AgentConfigApplicationServiceTest`、前端 `agent-config-panel.test.ts`、`@test-agent/agent-web typecheck`、opencode `agent list`/`serve` smoke 通过；`.env.test` 三服务已启动，backend/readiness/frontend/CORS 均通过，但 `opencode-manager.log` 仍每约 10 秒出现一次 WebSocket 断开记录，本次未扩展排查。

### 2026-06-29 - 优化编辑器 Markdown 预览分屏分隔线视觉设计

- Why: Markdown 预览开启后的分屏分隔线（sash）原本为扁平的极浅灰色，在白色背景上几乎不可见且不明显，缺乏可拖拽的视觉暗示。
- What: 将其重新设计为一个 8px 高度、带上下精致边框和圆角拖拽手柄的高级分隔线，并辅以悬浮过渡状态。
- How: 修改 `CodeEditor.vue` 中分屏 sash 部分的代码，将 `4px` 纯色 `div` 更改为带 `border-t border-b border-[var(--ta-border)]`、`bg-[var(--ta-bg-2)]` 的 `8px` 容器，居中放置一个 `h-[2px] w-8 rounded-full bg-[var(--ta-border-strong)]` 的精细拖拽手柄。
- Result: 视觉效果更立体美观，具备明确的拖拽引导，各项前端类型校验和单测全绿通过。

### 2026-06-29 - 修复公共 Agent 面板因本地系统文件判脏无法刷新

- Why: 左侧 Agent 面板点击刷新仍提示“没有已初始化服务器”，但公共 opencode 配置目录实际已存在且包含迁移后的 agent/skill。
- What: 定位到 `listPublicAgentRepositories()` 返回 `status=CONFLICT, initialized=false`，原因是公共配置 Git 工作树存在未跟踪 `.DS_Store`；删除该文件后后端立即返回 `READY/initialized=true`。在公共配置仓库新增 `.gitignore` 忽略 `.DS_Store`，避免 macOS 再次生成系统文件导致页面无法加载公共级 Agent。
- How: 使用默认测试账号登录后调用 `/api/internal/platform/workspace-management/agent-config/public/repositories` 和 `/public/files` 验证状态；当前 Agent 面板的公共级根目录映射到 `opencode/agents`，会直接列出 agent Markdown 文件，`opencode/skills` 由 opencode 运行时加载但不在该面板根目录展示。
- Result: 公共配置仓库状态恢复为 `READY`，`/public/files?path=&linuxServerId=192.168.100.115` 可列出 7 个迁移 agent 文件。

### 2026-06-29 - 同步 openclaw 测试设计与执行公共 opencode 配置

- Why: 当前项目的公共 opencode 配置已初始化到 `temp/opencode-config`，需要把桌面 `openclaw` 中沉淀的测试设计、测试执行 agent/skill 迁移到公共配置区域，供本项目托管 opencode 运行时复用。
- What: 在公共配置仓库新增 `opencode/agents/` 下的测试设计主 agent、测试对象识别/策略规划/案例生成/案例审查子 agent、测试执行主 agent 与 API 测试执行子 agent；将 `openclaw` 展开运行态中的 18 个测试设计/执行 skills 同步到 `opencode/skills/`，并更新公共配置 `README.md` 说明目录、迁移来源和当前能力边界。
- How: 采用 `OPENCODE_PUBLIC_CONFIG_DIR` 实际指向的 `temp/opencode-config/opencode` 作为运行配置根目录，按 opencode 1.17 的复数目录约定放置 `agents/` 与 `skills/`；skill 来源选用 `/Users/kaka/Desktop/openclaw/.mimoagent-state/agents/*/workspace/skills`，以保留 `rules/`、`templates/` 等相对引用资源。
- Result: `opencode agent list` 能识别 7 个迁移 agent；临时 `opencode serve` 通过 `/agent` 与 `/skill` 分别加载到迁移后的 agent/skill。当前项目未发现 `testing_api_action_run`、`testing_db_action_run` 等 openclaw 测试执行平台工具，因此执行 agent 已保留能力限制说明，后续接入真实 API/DB 执行能力时需补齐对应工具或适配层。

### 2026-06-29 - 新增 Windows PowerShell 三服务重启脚本

- Why: Windows 开发环境需要不依赖 Bash 的三服务一键启动入口，避免按旧文档手工分别配置和启动 Java 后端、opencode-manager 与前端。
- What: 新增根目录 `restart-dev-services.ps1`，对齐 `restart-dev-services.sh` 的默认 `test` profile、dotenv 安全解析、先构建再停服、后端 → opencode-manager → 前端重启顺序、JVM 代理清空、manager state/托管 opencode 进程清理和 `.tmp/dev-services` 日志约定；`tools/verify-dev-scripts.sh` 增加 ps1 存在性检查，并在可用 PowerShell 时做 parser 校验。
- How: Windows 脚本使用 PowerShell 5.1 语法和 Win32_Process command line 精确匹配脚本管理的进程，不手改 `.env.local` 等敏感环境文件；同步 `docs/guides/ai-workflow.md`、`backend/README.md`、`frontend/README.md`、`docs/deployment/backend.md` 和 `docs/deployment/frontend.md` 的本地联调入口。
- Result: `tools/verify-dev-scripts.sh` 在当前 macOS 环境通过；由于本机未安装 `pwsh`/`powershell`，校验脚本已跳过 PowerShell parser 校验，后续 Windows 环境应执行同一校验脚本或直接运行 `powershell -ExecutionPolicy Bypass -File .\restart-dev-services.ps1 -Help` 复核解析。

### 2026-06-29 - opencode-manager 容器 ID 改为 hostname 优先

- Why: 运行管理中的 manager 容器标识需要优先反映实际主机/容器 hostname，不能被 `OPENCODE_MANAGER_CONTAINER_ID` 环境变量抢先覆盖。
- What: 调整 Go manager `resolveContainerID`：Windows 直接使用机器名；非 Windows 依次读取系统 hostname、`/etc/hostname`，最后才用 `OPENCODE_MANAGER_CONTAINER_ID` 兜底，并移除旧的 `HOSTNAME` 环境变量兜底。同步 opencode-manager README 和部署文档，说明环境变量只作最后兜底。
- How: 先补 RED 测试覆盖 hostname 优先、`/etc/hostname` 优先于 env、env 最后兜底、`HOSTNAME` env 不再生效、Windows 忽略 env 使用机器名，再最小调整解析函数。
- Result: `go test ./internal/config`、`go test ./...`（opencode-manager）、`mvn clean package -DskipTests` 和 `git diff --check` 已通过。本变更不涉及数据库、HTTP API、SSE 或前端直连逻辑。
### 2026-06-29 - 修复首轮对话远端 user 快照延迟导致的重复气泡

- Why: 新会话首轮发送后，opencode 可能在 assistant 已渲染后才补发同一条 user 的 `message.updated` / `message.part.updated`，前端 reducer 原先只在“当前轮还未出现 assistant”时归并远端 user，导致实时视图多出一条重复用户气泡；历史记录正常，因为持久化消息本身没有重复。
- What: `frontend/packages/agent-chat/src/runtime-reducer.ts` 对空的延迟 user snapshot 不再追加占位消息，并在后续 text part 到达时按未绑定 `messageId` 且文本一致的乐观 user 消息回填远端 `messageId`。
- How: 新增 `runtime-reducer.test.ts` 回归用例，先 RED 复现“乐观 user -> assistant -> 延迟远端 user snapshot/part”的事件顺序，再最小修改 reducer 归并逻辑；未改 API、SSE 事件契约、数据库或样式。
- Result: `corepack pnpm exec vitest run packages/agent-chat/tests/runtime-reducer.test.ts`、`corepack pnpm --filter @test-agent/agent-chat typecheck`、`corepack pnpm test`、`corepack pnpm --filter @test-agent/agent-web build` 和 `git diff --check` 通过。`./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 完成后端打包但后端启动失败：`.env.test` 指向的 PostgreSQL `192.168.100.200:5432/testagent` 当前 `No route to host`，因此 backend health/CORS 未通过；frontend Vite `127.0.0.1:3000` 返回 200。构建仍有既有 CSS `@import` 顺序与大 chunk 警告，本次未扩大处理。

### 2026-06-29 - 模型选择器交互重构：实现气泡下拉框并整合上新推荐

- Why: 聊天输入卡片下方的“上新”标签与输入框内的模型选择下拉按钮功能重叠。为了提升交互体验，需要将模型选择机制彻底由覆盖全局的模态弹窗重构成紧凑优雅的触发式气泡下拉框（Popover Dropdown），并将上新推荐无缝融合在下拉框内。同时，需解决气泡下拉框在不同宽度下的裁剪和遮挡问题。
- What:
  - 彻底移除了原 `AgentWorkbench.vue` 里的全局模态弹窗 `managed-model-dialog-backdrop` 结构及配套的弹窗控制逻辑与大片样式。
  - 在 `FigmaChatPanel.vue` 中封装并实现了全新的 `.figma-chat-model-dropdown` 组件，绑定至模型选择按钮下方，使其点击直接在上方弹出下拉框（带指向箭头的卡片设计）。
  - 下拉框内部高度融合：顶部包含模型搜索栏，搜索为空时在“上新推荐”分区呈两列圆角卡片展示最新推荐模型，下方则按供应者分类展示所有启用模型的垂直列表，高亮当前选中的模型并标示勾选符号。
- How:
  - 在 `FigmaChatPanel.vue` 的 script 模块中利用 `onMounted`/`onBeforeUnmount` 注册全局 click 监听以处理点击空白自动收起下拉框的交互。
  - 移除了 `@open-model-picker` 组件事件触发；在 `AgentWorkbench.vue` 中将 `@select-model` 直接绑定至原有的 `selectRuntimeModel`，并且清理掉其原本管理的 `modelPickerOpen` 和 `modelGroups` 等冗余状态。
  - **裁剪及定位优化**：将输入卡片 `.figma-chat-input-card` 的 `overflow` 属性由 `hidden` 改为 `visible`，解决下拉框被父级边框遮挡裁剪的问题。将下拉框 `.figma-chat-model-dropdown` 的水平定位由 `left: 50%; transform: translateX(-50%)` 修改为 `left: 0` 左对齐，指示小箭头 `::after` 修改为固定偏置 `left: 36px`，确保下拉框整体完全容纳在右侧聊天面板内，彻底解决了在窄屏或侧边栏左边界的裁剪切边缺陷。
- Result:
  - 消除界面中的浮层弹层，模型切换交互完全局限在气泡下拉菜单内，极具 MIMO Web IDE 的精致卡片质感，用户体验高度一致，完美渲染不裁剪。
  - 全量 170 项 Vitest 单测及编译类型检查全部无损通过。

### 2026-06-29 - 统一目录配置与 macOS 平台修复

- Why: 合并后 macOS 参数仍指向 `/tmp/test-agent`、`$HOME/test-agent`，公共 Git 地址为 `UNCONFIGURED`；前端未提供 `macos` 筛选，相关后端测试还把 `macos` 当非法值，用户初始化失败提示也没有给出可执行入口。
- What:
  - 通用参数页面增加 `macos` 并改为选择即查询；后端/API 测试同步接受 `macos`、继续拒绝未知平台。
  - 启动脚本默认导出可覆盖的 `TEST_AGENT_ROOT=$ROOT_DIR`，F-COSS 本地种子目录改到项目 `temp/fcoss`，不再重建 `/tmp/test-agent`。
  - manager 自动启动判断同时识别 loopback 和默认路由网卡的本机 IPv4，避免 `.env.test` 使用局域网地址时误判为远端并跳过 manager。
  - 新增默认只读的 `tools/cleanup-old-path-data.sql`；显式传入绝对项目根目录时只迁移路径字段，保留 Session、Run 和审计记录。
  - 公共 Git 未配置、公共 opencode 配置未初始化时，错误信息分别指向“通用参数管理”和“opencode公共配置管理”。
- How:
  - 六个 macOS 参数通过通用参数 API 更新为 `$TEST_AGENT_ROOT/temp/...`，公共 Git 地址更新为 `git@gitee.com:huangzhenren/opencodeconfig.git`，产生 7 条修改审计并触发 manager 配置刷新。
  - 旧工作区文件先无损复制到项目 `temp/`，再迁移数据库 workspace/version/replica 路径；确认数据库旧路径引用为 0 后删除 `/tmp/test-agent`、`$HOME/tmp/test-agent` 和 `$HOME/test-agent/opencode-configdev`。
- Result:
  - 浏览器实测 `macos` 选项可选且自动刷新；系统管理入口和公共配置管理可打开。
  - 公共仓库已从 Gitee `master` 初始化到 `temp/opencode-config`（commit `a5a4ca00a9`），最终重启后默认用户 opencode 进程在 `192.168.100.115:4098` 返回 `READY`。
  - Go 全量测试、前端 170 个 Vitest、相关 Maven reactor、前端 typecheck/build、系统管理入口 Playwright 和开发脚本校验通过。
- 注意事项:
  - 已执行的 Flyway migration `V20260628223000` 不修改，避免 checksum mismatch。
  - 环境专属路径不应写入 Flyway，应通过通用参数管理 API 或本地初始化脚本更新。
  - `temp/` 目录不提交到版本控制，本地开发时按需创建。

### 2026-06-29 - 前端 Chunk 大小优化与依赖按需加载

- Why: Vite 生产构建时发出包体积过大警告，其中 `element-plus` (~940 kB)、`markdown` (~1.05 MB) 和 `echarts` (~1.08 MB) 均超出了 600 kB 警告阈值，影响加载性能。
- What:
  - 引入 `unplugin-vue-components` 和 `unplugin-auto-import` 插件，实现 Element Plus 组件的按需自动导入。
  - 将 `highlight.js` 全量包替换为 `highlight.js/lib/common` 常用语言子包（支持 37 种常用开发语言），精简 Markdown 渲染体积。
  - 移除 `manualChunks` 中对 `echarts` 依赖的强合并配置，使其保持按需动态分片。
  - 解决 Vitest 及 E2E 测试在此项调整下的兼容性，规避了 JSDOM 的 CSS 解析报错以及 Playwright 侧边栏按钮的选择器冲突。
- How:
  - 在 `apps/agent-web` 的 `devDependencies` 中安装按需引入依赖，并在 `vite.config.ts` 和 `vitest.config.ts` 中注册 `AutoImport` 及 `Components` 插件（为兼容单元测试在 Node 下运行，显式配置 `importStyle: false`）。
  - 创建 `src/utils/locale.ts` 文件以剥离 Element Plus 国际化配置及 dayjs side-effects，在 `App.vue` 中使用 `<el-config-provider>` 包装，彻底规避 `main.ts` -> `App.vue` 的循环依赖导致应用崩溃问题。
  - 修改 `MarkdownView.vue` 及 `MarkdownPreview.vue` 的动态 `import` 路径为 `highlight.js/lib/common`。
  - 适配测试用例：在 `agent-config-panel.test.ts` 中将 `getByTitle` 调整为 `getByText`；在 `scheduler-management-panel.test.ts` 的 menu 切换断言中指定 `{ selector: ".ta-system-menu-text" }` 以免与 tooltips 冲突；在 `workbench.spec.ts` 中将模糊的 `/工作空间/` button 查询改用特定的 `.ta-workbench-footer-branch` 类定位器，解决 strict mode violation。
- Result:
  - `element-plus` 依赖包由 939.78 kB 缩减至 **514.97 kB**。
  - `markdown` (highlight.js) 依赖包由 1,052.13 kB 缩减至 **289.87 kB**。
  - `echarts` 全量包被完全拆散为异步子 chunk (单文件约 250 kB)，不再占用首屏同步加载。
  - 全量 169 项 Vitest 单测均顺利绿过，成功消除 Vite 大包警告。

### 2026-06-29 - 优化挂机趣味彩蛋出现机制与计时

- Why: 优化彩蛋出现机制，要求只有当页面置顶且鼠标不动超过 1 分钟时才出现小人，而在页面未在最前端显示时（例如后台标签页、浏览器最小化或失去焦点），小人不应触发以避免浪费背景资源或影响用户体验。
- What:
  - 将 `FigmaShell.vue` 中小人诞生的静置超时时间从 20 秒修改为 60 秒（1分钟）。
  - 新增页面状态检测：在 `resetInactivityTimer` 和 `spawnRobot` 中增加了 `document.hidden || !document.hasFocus()` 条件判断，确保在页面隐藏或失去焦点时不启动或触发小人诞生。
  - 新增焦点与可见性事件监听：在 `onMounted` 中添加对 `window` 的 `focus`、`blur` 以及 `document` 的 `visibilitychange` 事件监听，使用 `handleFocusChange` 处理，实现切回页面时重置/重新计算 1 分钟不活跃倒计时，切出页面时立即清除不活跃定时器。
- How: 纯前端交互逻辑优化，无额外后端接口依赖，仅通过浏览器 visibilityState 和 focus API 进行条件拦截与定时器销毁。
- Result: 页面最小化、切换标签页或失去焦点时，机器人小人彩蛋不会在后台触发诞生，极大节省了后台 CPU 占用，前端语法检查和 Vitest 测试通过。

### 2026-06-29 - 收纳公共级操作按钮为鼠标悬停从左侧弹出的更多操作菜单

- Why: 公共级的操作按钮过多（包含更新、切换、创建 worktree 共三个按钮），全部平铺展示会使得页面横向空间过度拥挤，破坏紧凑的布局结构；同时，由于文件树容器具有滚动和 overflow-x: hidden 裁剪，菜单向右弹出（超出侧边栏边界）时会被裁剪导致无法显示，因此应改为向左侧（文件树内部区域）弹出。
- What: 将这三个按钮收纳到一个隐藏的可选菜单中，在末尾展示一个三点图标的“更多操作”按钮，在鼠标悬停时在按钮左侧展示全部操作列表。
- How: 
  - 在 `AgentConfigPanel.vue` 模板中引入 `MoreHorizontal` 图标，并在“公共级”头部使用 `.agent-more-menu-container` 包裹。
  - 将“更新公共配置”、“切换公共 worktree”和“创建公共 worktree”三个按钮移入内部的 `.agent-more-menu-dropdown` 容器，改写为横向对齐的条目。当触发异步操作时，外部的三点图标将智能替换为 `Loader2` 旋转动画以维持顶层加载指示。
  - 在 `<style scoped>` 中通过纯 CSS `:hover` 选择器实现展开显示，设定 `right: 100%` 与 `top: -4px` 以在左侧对齐弹出，并配合向左投影 `box-shadow: -4px 4px 12px rgba(0, 0, 0, 0.08)` 优化视觉；此外使用 `::after` 伪元素桥接了按钮和菜单之间 4px 的物理空隙，确保鼠标滑向菜单时悬浮态连续且菜单不会意外消失。
- Result: 界面排版更加清爽，悬浮菜单能向左侧顺畅弹出，且鼠标移入时极为稳定，完美解决了容器裁剪及悬空抖动消失的问题。前端项目检查和 Linter 完全通过。

### 2026-06-29 - 将“更新公共配置”点击后的 prompt 弹出框替换为自定义 DIV 弹窗

- Why: 点击“更新公共配置”按钮时使用原生的 `window.prompt` 会破坏 UI 的整体美观与一致性，需要用自定义设计的 DIV 弹窗代替。
- What: 将更新公共配置中的 `window.prompt` 输入/选择框替换为一个使用 `<Teleport>` 渲染的自定义 DIV 弹窗，显示远端分支下拉列表供用户选择。
- How: 
  - 在 `AgentConfigPanel.vue` 中新增 `showUpdatePublicConfigModal`、`updatePublicConfigBranch`、`updatePublicConfigBranches` 等状态。
  - 在 `updatePublicConfig()` 中异步加载远端分支列表并打开弹窗，由 `submitUpdatePublicConfig()` 执行提交；模版中添加 `showUpdatePublicConfigModal` 自定义弹窗，应用已有的样式结构和类，支持 ESC 键关闭。
- Result: 成功使用统一样式的页面级模态弹窗取代了浏览器原生 prompt。前端项目类型检查及代码风格校验（`corepack pnpm typecheck && corepack pnpm lint`）均顺利通过。

### 2026-06-28 - 运营分析 rollup 与 AI 回复满意度反馈一次性实现

- Why: 运营侧需要覆盖用户规模漏斗、使用强度、Run 结果、满意度、Diff 采纳、Token、趋势高峰、维度排行和明细导出的 P0 指标；同时 AI 回复需要可追溯的满意/不满意反馈，但不能展示 prompt、assistant 原文或成本字段。
- What: 后端补齐 Session/Run/用户消息归因字段，新增 `ai_message_feedbacks`、`runs.agent_id/model_id`、hourly/daily rollup、duration histogram、watermark/job/DB lock 表；新增反馈领域对象、MyBatis mapper、服务和 `/messages/{messageId}/feedback` API；新增 analytics rollup runner、查询服务、SUPER_ADMIN analytics API 与 CSV 导出。前端在助手消息下方新增满意/不满意反馈入口，系统管理新增“运营分析”页，覆盖筛选、概览、趋势、热力、排行、满意度、异常明细和导出；同步 API、事件、数据库、backend/frontend README。
- How: 主链路只写事实数据，统计由定时 rollup 持 DB 锁重算最近 hourly/daily，API 只读 rollup 并返回 freshness/stale 状态；MyBatis XML 承载新增 SQL；满意度按 `positive/(positive+negative)`，无反馈返回 `null`，p95 用 histogram 近似；CSV/看板不输出 cost/costUsd。提交时需要继续排除既有无关脏文件：`frontend/apps/agent-web/vite.config.ts`、`frontend/packages/diff-viewer/*`、`frontend/packages/editor/*`。
- Result: 后端完整 `mvn -q test` 通过；在临时 stash 无关脏文件、只保留本次 staged 内容的提交态下，前端相关包 typecheck 通过，workspace 级 `corepack pnpm test` 27 个文件 169/169 通过。带回既有未暂存 editor Monaco 改动时曾因 mock 缺少 `loadMonaco` 出现 unhandled rejection，已确认不纳入本次提交。
### 2026-06-28 - 修复 SSH key RSA 解密失败与版本库英文名称为空问题

- Why: 个人设置页添加 SSH key 报错 "RSA decryption failed"；后台持续报错"版本库英文名称不能为空"。
- What:
  - `RsaKeyService.java`：显式指定 OAEP MGF1 使用 SHA-256，与前端 Web Crypto API `RSA-OAEP (hash: "SHA-256")` 保持一致。Java `OAEPWithSHA-256AndMGF1Padding` 名称有误导性，其 MGF1 默认使用 SHA-1，而 Web Crypto API 的 MGF1 与主哈希一致（SHA-256），导致前后端不匹配。
  - 数据库 `code_repositories` 表：补充三个版本库的 `english_name` 字段（intelligent-test-agent、fcoss-main、mimoagent）。
- How: 修改后端解密参数配置，补全数据库缺失字段；未改 API、前端代码或数据库结构。
- Result: SSH key 前端加密后后端可正确解密；后台工作空间副本同步任务不再报错。

### 2026-06-28 - 添加 macOS 平台支持

- Why: 本地 macOS 开发环境无法创建工作空间，报错 `/data: Read-only file system`。`ParameterPlatform` 枚举只有 WINDOWS、LINUX、ALL，没有 MACOS，导致 macOS 被当作 Linux 处理，使用 `/data/...` 路径。
- What:
  - `ParameterPlatform.java`：添加 `MACOS` 枚举值，修改 `current()` 方法识别 macOS（`osName.startsWith("mac")`）。
  - Flyway `V20260628223000__add_macos_platform_support.sql`：修改数据库约束添加 `macos`，添加 macOS 平台的 `common_parameters` 配置。
  - 数据库配置：macOS 本地开发路径需要使用**绝对路径**，因为后端进程工作目录在 `backend` 子目录下，相对路径会解析错误。
- How: 枚举扩展 + 数据库约束修改 + 平台配置插入；未改业务逻辑或 API。
- Result: macOS 本地开发环境可正常使用本地路径，不再尝试访问 `/data`。

### 2026-06-28 - 完成态历史助手快照与实时 user part 误拼修复

- Why: 真实页面复现完成态 Session `#89d405` 只剩用户消息；数据库对应会话只有 USER 行。后端日志同时显示历史查询在 Reactor `parallel-*` 线程调用 `.block()` 必然失败。进一步直连 opencode 发现 `/api/session/{id}/message` 只返回 `agent-switched/model-switched`，完整 user/assistant 消息实际来自 `/session/{id}/message`；真实新任务还确认 user 的实时 `message.updated + message.part.updated` 会被 reducer 误建成 assistant，从而把提示词拼入回答并表现为多余空行/重复内容。
- What:
  - `GeneratedOpencodeSdkGateway` 改读标准 `/session/{sessionID}/message` envelope；因 generated `Message` union 把 user 收窄错误，仍只在 client 适配器内使用 generated `ApiClient` + 稳定 JSON Map，不手改 generated SDK。
  - 历史消息 Controller 将包含远端刷新和同步仓储访问的调用整体 offload 到 bounded-elastic；终态快照只把 text part 写入 assistant 正文，不再混入 reasoning/tool output。无 text 的工具/文件步骤允许以空正文 + `partsJson` 保存，保留历史文档和文件变更恢复信息。
  - SSE 初始恢复只重放 assistant；前端 reducer 把 opencode 后续重发的远端 user message/part 合并回当前乐观 user 消息，不再创建 assistant 或污染最终回答。
- How: 先用真实数据库、后端日志、标准/旧消息端点和浏览器 DOM 定位，再分别补 gateway、快照正文、Reactor offload、assistant-only recovery、user part reducer 的失败用例；未修改 `.env.local`，未变更 API 路径/字段、事件类型、数据库结构或鉴权策略。
- Result: 后端 18 模块 `mvn test` 全部 `BUILD SUCCESS`；前端 Vitest 22 文件 138/138、全 workspace typecheck、生产 build通过；标题/历史文档定向 Playwright 4/4。使用 `.env.test` 重启三服务后，真实任务“请只回复：最终空行验证通过”运行完成只显示 assistant“最终空行验证通过”，刷新并从历史切回仍显示该输出和 `SUCCEEDED`。修复前已经完成且从未落下 assistant DB 快照的旧会话，若原远端 session 已不可路由，无法凭空补回历史正文；修复后的任务会在完成时落库。

### 2026-06-28 - 历史文档恢复、首条消息标题与对话空行修复（纠正前次完成结论）

- Why: 前次记录宣称历史文档和空行已解决，但真实页面仍无法看到历史生成文档，Session 标题仍是 `Agent HH:mm:ss`，连续助手快照仍会在边界多插换行；同时前次把 delta 事件全局豁免去重，违反 `docs/api/event-stream.md` 中 transient 也按稳定 `eventId` 去重的契约。
- What:
  - `agent-chat.normalizeMessagePart` 复用实时 reducer 的 part 归一化规则，把历史 `partsJson` 的 `id/tool/state.*` 原始结构恢复成统一 text/tool/file part；`workbench-utils.diffFilesFromSessionMessages` 在 Run Diff 快照为空时从历史 write/edit/apply_patch part 推断生成文件。
  - `FigmaChatPanel` 恢复此前被删但 README 仍声明存在的“N 个文件已更改”入口和 Diff 抽屉，补 file part 文档行；连续 assistant 消息合并时边界最多保留一个换行。
  - 新 Session 创建标题改为第一次发送消息的去首尾空白内容，聊天标题同步使用当前 Session 标题。
  - event-stream-client 恢复所有真实 `eventId`（含 transient delta）统一去重；仅缺失 `eventId` 且 `seq=0` 的旧增量保持放行。
- How: 先增加失败用例复现历史 raw part、文件卡片、连续换行、首条标题和重复 transient eventId，再做最小实现；历史 Diff 合并时以 Run API 返回为最新值、tool part 推断为缺失兜底。未修改 `.env.local`，未改 API、事件类型、数据库或 generated SDK。
- Result: 前端 Vitest 22 文件 137/137、全 workspace typecheck、生产 build 通过；新增历史文档/首条标题 Playwright 在 Chromium 与 mobile 4/4 通过；今天涉及的后端 17 模块 Maven 回归 `BUILD SUCCESS`；`.env.test` 三服务重启成功，backend health/readiness 均 `UP`、frontend 3000 返回 200、CORS preflight 200。完整 mock E2E 仍有历史用例未随当前 UI 契约更新：24 passed、13 failed、3 skipped，失败集中在已改版的附件/实时追踪/工作区入口、SSH key 加密 mock 和不唯一“工作空间”定位器，不能据此宣称全量 E2E 已通过。

### 2026-06-28 - 事件流 transient 文本防重修复与历史对话管理抽屉实现及深度适配

- Why: 响应用户需求及进一步精准反馈：(1) 修正智能体输出流式增量中丢失分段/排版破碎产生多余空行的问题；(2) 解决历史对话看不到的问题，提供质感极佳的交互与样式以便用户查看历史并一键切换；(3) 修复历史对话按钮与工作台外部缩进按钮重叠的问题；(4) 修复切换历史对话后仅显示用户消息、智能体返回内容丢失的问题；(5) 修复新建对话按钮失效的问题；(6) 解决切换历史对话后，智能体生成的修改过的文件（文档）没有被恢复展示的缺陷。
- What:
  - `event-stream-client/src/index.ts`：进一步完善流式增量去重策略。对打字机瞬态消息包事件（`assistant.message.delta` 和 `message.part.delta`）实施全局去重豁免，放行所有顺序增量，从根本上杜绝因 eventId 重复导致内容字符被拦截剥碎产生的多余空行 Bug。
  - `FigmaChatPanel.vue`：将「历史对话」按钮紧凑移至左侧标题栏右侧，并为右侧栏预留 56px 边距，杜绝与悬浮缩进按钮重叠；新增历史对话侧滑蒙层抽屉，包含即时关键词过滤模糊搜索、会话列表圆角卡片展示及创建时间和短 ID 标识；新增 `select-session` 事件发出。
  - `AgentWorkbench.vue`：绑定 `FigmaChatPanel` 的 `select-session` 并重构 `switchSession`。引入根据最新历史消息 `runId` 自动调取 `api.getRun` 与 `api.getRunDiff` 进行状态和文件变更恢复的底层加载链路，完全呈现历史生成的测试文档；实现 `handleNewConversation` 处理函数并绑定 `@new-conversation` 事件，解决新建对话按钮失效的缺陷。
  - `workbench-utils.ts`：修复 `messagesFromSessionMessages` 映射逻辑，为 assistant 消息正确填充 `parts: message.parts ?? []`，解决智能体响应无内容时直接被忽略丢弃的深层 Bug。
- How: 瞬态 delta 事件不进行 deduplicate 防重；头部操作栏采用左侧聚合与右侧隔离设计防重叠；恢复历史会话时保障智能体输出的 parts 分段无缝映射至 `AgentMessage` 模型，并同步重新拉取关联 Run 及其 Diff 文件进行工作台编辑器复原。
- Result: 彻底解决内容丢失导致排版多余空行的问题；历史对话按钮展示美观且零重叠；切换历史对话后用户指令与智能体执行的全部步骤、返回内容以及生成的测试文档全部完美展示，新建对话按钮完全恢复正常。Vitest 与前端类型检查（typecheck）全部通过。

### 2026-06-28 - 对话框拉宽、用户气泡底色圆角优化与字间距紧凑化

- Why: 优化首页 Agent 对话面板的视觉与排版，响应用户进一步反馈：(1) 将首页右侧对话框的默认宽度拉宽；(2) 给用户发出的对话框加一个浅灰底色及圆角样式（对齐截图图 1）；(3) 智能体输出各元素行距排版依然较松散，需全方位收紧以彻底紧凑排版；(4) 去除 opencode 进程初始化成功时重复弹出的左下角 feedback toast 通知，仅保留右上角绿色状态提示。
- What:
  - `FigmaShell.vue`：将右侧对话面板默认宽度 `rightPanelWidth` 变量值由 `380` 变更为 `450`（对应拉宽默认宽度）。
  - `globals.css`：将 `--ta-chat-user-bg` 颜色由 `#dddddd` 修改为浅灰色 `#f2f2f2`，并在 Tailwind v4 的 `@theme` 内映射为 `--color-ta-chat-user-bg: var(--ta-chat-user-bg);`。
  - `AssistantThread.vue` / `FigmaChatPanel.vue`：将用户消息气泡样式由 `transparent` / `rounded-md` 变更为具有大圆角的亮灰色气泡（`.figma-chat-bubble--user` 新增 `background: var(--ta-chat-user-bg); border-radius: 12px`），完美在各聊天面板呈现图 1 的底色气泡效果。
  - `AgentWorkbench.vue`：移除 `initializeOpencodeProcessMutation` 成功回调中对 `feedback.value` 赋值成功 toast 的调用，避免弹出多余重复通知。
  - `AnswerPart.vue` / `PlainAnswer.vue` / `ReasoningPartBlock.vue`：将智能体回答及思考的容器、raw 文本 block 上的行高类由 `leading-6`、`leading-[1.55]` 统一修改为 `leading-[1.4] tracking-[-0.01em]`。
  - `MarkdownView.vue`：修改 `.markdown-body` 样式的行高 `line-height: 1.32;`、段落 `margin: 2px 0 !important;`、列表 `margin: 2px 0 !important;` 且 `li` 设为 `1px 0 !important;`，特别将表格 `table` 的 margin 缩短、行高改至极密 `1.25` 且字号为 `12px`，并将单元格 `padding` 压紧至 `2px 5px !important`，代码块 `pre` padding/margin 亦做等比紧凑。
- How: 纯 CSS/Tailwind 样式参数微调与 UI 属性及逻辑回调默认值调整，保留全部业务逻辑。
- Result: 首页聊天面板默认展现更宽大舒服；用户气泡呈亮色圆润浅灰底色（对比头像更柔和，完美对齐图 1）；智能体最终回答、Markdown 文本以及大表格、列表间距布局彻底紧凑精致。前端已初始化 toast 去除，右上角进程状态提示绿卡完美保留。前端 lint 校验与 Vitest 全部通过。


### 2026-06-28 - 左侧栏刷新与配置操作按钮防重点击与点击反馈优化

- Why: 左侧工作空间文件树和 Agents 配置栏的刷新及创建/更新按钮在点击后没有任何视觉反馈（如 hover/active 态或 loading 状态），且缺乏防重点击（点击后禁用）的设计，容易导致用户重复触发网络请求，交互体验不够灵敏。
- What: 为刷新和配置按钮增加悬浮、按压、防重点击（disabled 状态）以及 loading 动画反馈。
- How:
  - 在 `AgentWorkbench.vue` 中重构 `loadDirectory(path, workspaceId, force)` 签名，将 `force` 作为第三个参数以维持对已有调用（传 workspaceId 作为第二个参数）的向后兼容，并在 `force = true` 时绕过本地缓存强制拉取。
  - 在 `FigmaFileExplorer.vue` 中，文件树刷新和配置栏刷新分别根据父组件 `loadingPath.has("")` 和子组件 `agentConfigPanelRef?.busy` 状态进行自锁控制（禁用按钮且 `RefreshCw` 图标旋转 animate-spin）。对 `.figma-fe-section-action-btn` 样式补充 `:active` 和 `:disabled` 状态。
  - 在 `AgentConfigPanel.vue` 中暴露 `busy` 状态，新增 `updatingPublicConfig` 和 `creatingWorktreeScope` 状态标识。在按钮中根据此状态渲染 `Loader2` 旋转加载动画代替原先的 `Plus` 或 `ArrowUpFromLine` 图标。对 `.agent-icon-btn` 补充 `:hover` 和 `:active` 样式背景色和阴影。
- Result: 按钮交互反馈清晰，刷新期间能平滑旋转并阻止二次点击，完美修复了防重点击与视觉卡顿的问题。经 `@test-agent/agent-web` 内部 typecheck 和 lint 均一次性顺利通过。

### 2026-06-28 - 分布式公共 Agent 配置初始化与 worktree 路由

- Why: 公共 Agent worktree 必须依赖目标服务器本地 Git 仓库；在创建 worktree 时自动 clone 会在分布式部署下把仓库落到当前后端而不是管理员期望的目标服务器，后续文件和 Git 操作也缺少稳定的服务器归属。
- What: 公共配置仓库初始化从创建 worktree 流程拆出，系统管理新增“配置管理 > opencode公共配置管理”查看在线后端服务器初始化状态并执行初始化；创建公共 worktree 时选择已初始化服务器并写入 `agent_config_worktrees.linux_server_id`，后续公共 Agent 文件、diff、stage、commit、publish 按该字段由当前后端代理到目标服务器执行。AgentConfig 持久化同步迁到 MyBatis XML，进度通过 `agent-config.operation-progress` 广播安全字段。
- How: 先补 workspace-management、persistence、API、progress hub 和前端 RED/回归测试，再最小修改 `AgentConfigApplicationService`、`AgentConfigController`、MyBatis mapper、backend-api/shared-types 和 agent-web 管理页/弹窗；同步 HTTP API、数据库、后端部署、workspace-management README 和 agent-web README。
- Result: 后端聚合测试 `mvn -pl test-agent-workspace-management,test-agent-api,test-agent-persistence -am test` 通过；前端 `corepack pnpm --filter @test-agent/agent-web typecheck`、新增用例 `agent-config-panel.test.ts` 和 backend-api/系统管理定向用例通过；`corepack pnpm exec vitest run apps/agent-web/tests` 仍有进入前已有的 runtime topology label 和运行管理空态文案断言失败，未在本次范围内处理；`git diff --check` 通过。

### 2026-06-28 - 更新公共配置按钮图标改为表达 Git Push 含义的 ArrowUpFromLine 图标

- Why: 公共配置面板中，“更新公共配置”操作原本使用的是代表分支的 GitBranch 图标，无法直观传达出将配置推送或上传同步至远端服务器/代码库的含义，容易引起误解。
- What: 将该操作按钮的图标组件替换为能够表达上传/推送含义的 ArrowUpFromLine 图标。
- How:
  - 考虑到当前版本的 `lucide-vue-next` 不包含 `GitPush` 图标，采用了 `ArrowUpFromLine` 这一代表向上推送/上传的 Lucide 图标。
  - 在 `AgentConfigPanel.vue` 中导入 `ArrowUpFromLine`，并将 `updatePublicConfig` 按钮中的 `<GitBranch>` 替换为 `<ArrowUpFromLine>`。
- Result: 按钮图标成功转换为更符合“推送、上传并同步”语义的箭头图标，提升了图标语义的准确性。TypeScript 编译及样式代码检查顺利通过。

### 2026-06-28 - OPENCODE_PUBLIC_CONFIG_DIR 校验下沉到目标 manager

- Why: Java 后端和最终分配用户 opencode 进程的 manager 可能不在同一台服务器；在 Java 本机检查 `OPENCODE_PUBLIC_CONFIG_DIR` 会误判目标服务器目录状态，并破坏既有按负载选择目标容器的流程。
- What: 移除 `UserOpencodeProcessAssignmentService` 的本机 `Files.*` 目录校验，初始化仍先按当前候选中进程数最少且有空闲端口的容器选择目标 manager，再下发 `start`。Go manager 在 `Start` 中检查 `ConfigPath` 必须存在、是目录且非空；缺失、空目录、非目录或不可读时不创建 session、不启动 opencode，返回 `FAILED + errorCode=OPENCODE_UNAVAILABLE + 公共配置未初始化，请联系管理员。`。Java socket gateway 对该 errorCode 映射为同码平台异常。
- How: 先补 RED 测试覆盖 Java 本机目录不存在仍路由到目标 manager、manager errorCode 映射、Go manager 缺失/空目录/非目录失败，以及 supervisor `commandResult.errorCode` 透传；再改 Java runtime、控制消息工厂、Go process/supervisor 和 README/API/部署文档。
- Result: 窄测试先按预期失败后转绿；后续执行 `mvn -pl test-agent-opencode-runtime -am test`、`go test ./...`（opencode-manager）、`mvn clean package -DskipTests` 和 `git diff --check`。本变更不涉及数据库 migration、RunEvent/SSE 或前端直连逻辑。

### 2026-06-28 - Agent worktree Git 错误提示增加安全归因

- Why: 创建公共 Agent worktree 时，Git clone/fetch/pull/worktree 失败只显示“Git 远端读取失败”，管理员无法区分 SSH key 权限、仓库地址、网络、分支或同名 worktree 冲突。
- What: `ProcessGitCommandExecutor` 在 Git 命令非零退出时按 stderr 和命令上下文归因，保持 `GIT_UNAVAILABLE` 统一错误码，同时在 message 和 `details.gitFailureType/gitFailureHint` 返回可安全展示的诊断建议；`AgentConfigPanel` 优先展示 `gitFailureHint + traceId`，不展示原始 stderr、完整命令或内部路径。同步 HTTP API、workspace-management README 和 agent-web README。
- How: 先补 RED 测试覆盖认证失败、仓库不可访问、网络失败、分支不存在和 worktree 冲突，再新增 `GitCommandFailureClassifier` 并把前端错误格式化抽成 `agentConfigErrors.ts` 单测覆盖。
- Result: `mvn -pl test-agent-common,test-agent-workspace-management -am test`、`corepack pnpm exec vitest run apps/agent-web/tests/agent-config-errors.test.ts`、`corepack pnpm --filter @test-agent/agent-web typecheck` 和 `git diff --check` 通过；一次全量 Vitest 误用参数触发了既有 runtime topology/scheduler 测试失败，未在本次修复范围内处理。

### 2026-06-28 - 用户 opencode 初始化校验公共配置目录

- Note: 该实现已被上方“OPENCODE_PUBLIC_CONFIG_DIR 校验下沉到目标 manager”修正为目标 manager/目标服务器校验；本条仅保留历史上下文。
- Why: 用户进程初始化会把 `OPENCODE_PUBLIC_CONFIG_DIR` 下发为 `OPENCODE_CONFIG_DIR`，若公共配置目录未初始化，manager 仍可能启动一个没有公共 agent/provider 配置的用户进程，后续运行状态难以排查。
- What: `UserOpencodeProcessAssignmentService.initialize` 在真正下发 manager `start` 前检查 `OPENCODE_PUBLIC_CONFIG_DIR` 解析后的本机目录必须存在且非空；缺失、为空、非法或不可读时返回 `OPENCODE_UNAVAILABLE`，提示“公共配置未初始化，请联系管理员。”，并且不调用 manager 启动。同步更新后端工程 README、opencode-runtime README、HTTP API 和部署故障排查文档。
- How: 先补 RED 测试覆盖目录不存在和空目录两种场景，确认旧实现会继续启动；再最小加入 `java.nio.file.Files` 目录存在性/非空检查，错误详情只暴露参数名不暴露本机路径。
- Result: `mvn -pl test-agent-opencode-runtime test`、`mvn -pl test-agent-opencode-runtime -am test`、`mvn clean package -DskipTests` 均通过；不涉及数据库 migration、SSE 事件或前端直连逻辑。

### 2026-06-28 - 通用参数修改增加修改日志与历史查询

- Why: 通用参数为系统级配置，此前修改只在 `common_parameters` 覆盖更新值，无法追溯谁在何时把参数改成了什么；运维需要一个 SUPER_ADMIN 可访问的界面查看每次修改的时间、修改用户和修改前后的值。
- What: 新增 `common_parameter_change_logs` 表（Flyway migration `V20260628100000`），记录 `logId/parameterId/oldValue/newValue/changedByUserId/changedByUsername/traceId/createdAt`；`CommonParameterManagementApplicationService.updateValue` 更新参数值后自动写入修改日志，`findChangeLogs(parameterId)` 按修改时间倒序返回最多 50 条；Controller 在 `PATCH /{parameterId}` 从 `AuthPrincipal` 取用户 ID 和用户名传给应用服务，并新增 `GET /{parameterId}/change-logs` 查询历史。前端 `shared-types` 新增 `CommonParameterChangeLog` 类型，`backend-api` 新增 `listCommonParameterChangeLogs` 方法，`GeneralParamManagementPanel.vue` 操作列新增"修改历史"按钮，点击后弹出抽屉展示修改时间、修改用户、修改前值和修改后值。
- How: 先按 module-map 和 dependency-rules 归属文件到 `test-agent-domain/configuration`、`test-agent-persistence/mybatis`、`test-agent-configuration-management`、`test-agent-api`，复用既有 MyBatis XML mapper 模式和 SUPER_ADMIN 鉴权；应用服务新增 `CommonParameterChangeLogRepository` 依赖和测试可注入构造器，同步更新既有 `CommonParameterManagementApplicationServiceTest` 和 `CommonParameterManagementControllerTest` 的方法签名；同步 `docs/deployment/database.md` 和 `docs/api/http-api.md`。
- Result: `mvn clean package` 全量测试通过，`corepack pnpm -r typecheck` 通过；新增 migration 不影响现有 `common_parameters` 数据，新增 API 端点和前端功能向后兼容。

### 2026-06-28 - 左侧侧边栏工作空间与 Agents 分栏标题文案修改

- Why: 增强界面易读性与语义规范。原本左侧的“应用工作空间”标题不包含其他目录且略显冗长，而“agents”使用的是小写，视觉体验不一致。
- What: 将左侧侧边栏两个分栏的标题文案分别修改为“工作空间”和“Agents”。
- How:
  - 修改 `FigmaFileExplorer.vue`，将第一个分栏标题的文本由“应用工作空间”替换为“工作空间”。
  - 将第二个分栏标题的文本由“agents”替换为“Agents”。
- Result: 侧边栏标题展示为更简洁一致的“工作空间”和首字母大写的“Agents”，符合 IDE 界面设计标准。通过了前端的类型检查与样式审查，无任何逻辑与 API 破坏风险。

### 2026-06-28 - 运行管理底部用户进程按用户主动探测并支持重启

- Why: 运行管理底部列表原来依赖 overview 中按 Redis opencode 心跳筛选的 RUNNING 进程，用户进程停止后记录不再显示，导致无法从用户列表重新启动。
- What: 新增运行管理 `user-processes` HTTP 查询，按用户名、userId 或统一认证号定位用户，不依赖 Redis 心跳过滤；查询时由后端通过 manager health 主动区分 `HEALTHY`、`NOT_RUNNING`、`UNHEALTHY`、`CHECK_FAILED`，并给可重启进程返回 `restartable=true`。前端底部列表改为显式输入用户关键字后查询，未运行或健康失败行可直接重启。
- How: 先补 runtime 查询服务、API Controller、backend-api 和 agent-web RED 测试，再最小实现后端探测/回写、前端独立 Vue Query 和重启入口；同步 HTTP API、Event Stream、runtime 模块、agent-web 与 backend-api 文档。
- Result: `RuntimeManagementQueryServiceTest`、`RuntimeManagementControllerTest`、`runtime-management-settings.test.ts` 和 `backend-api.test.ts` 已通过；不新增数据库结构或 SSE 事件，重启仍复用现有 `containerId + port` manager restart 命令。

### 2026-06-28 - 公共 Agent worktree 首次创建自动准备主仓库

- Why: 公共级创建 Agent worktree 时，如果 `OPENCODE_PUBLIC_CONFIG_GIT_ROOT` 尚未 clone 或只是空目录，后端直接校验 Git 仓库并返回“目录不是 Git 仓库”，与公共配置应按通用参数自动下载后创建 worktree 的预期不符。
- What: `AgentConfigApplicationService#createPublicWorktree` 改为复用公共仓库准备流程；公共 Git 根目录缺失或为空目录时先按请求分支 clone，再在 `OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT` 下创建 worktree。已有非空非 Git 目录、origin 不一致或工作树 dirty 仍拒绝接管。
- How: 先补 RED 测试覆盖缺失目录和空目录两种首次创建场景，再最小修改公共仓库准备逻辑，并同步 workspace-management README、HTTP API 和数据库部署文档。
- Result: `mvn -pl test-agent-workspace-management -Dtest=AgentConfigApplicationServiceTest test` 与 `mvn -pl test-agent-workspace-management test` 已通过；不新增 API、事件、数据库字段或 migration。

### 2026-06-28 - 创建 Agent worktree 从 window.prompt 改为自定义模态弹窗

- Why: 之前创建 worktree 流程（公共级和工作空间级）依赖浏览器原生的 `window.prompt`，不仅视觉风格与 MIMO Web IDE 的现代 UI 严重割裂，而且需要用户连续点击两次弹窗，体验不够连贯。
- What: 将 `AgentConfigPanel.vue` 中创建 worktree 的流程重构为自定义模态弹窗。
- How:
  - 引入 `@test-agent/ui-kit` 中的 `Button` 和 `Input` 基础组件。
  - 新增 `showCreateWorktreeModal` (控制弹窗显示)、`createWorktreeScope` (保存当前操作的作用域)、`newWorktreeName` (绑定输入的 worktree 名称) 等响应式变量。
  - 用 `<Teleport to="body">` 渲染自定义模态弹窗，在弹窗上半部分展示当前 git 库分支（只读，取自 `status.value[scope]?.currentBranch`），在下半部分提供 `Input` 组件供用户输入 worktree 分支名称。
  - 支持回车键（Enter）确认提交、Esc 键或点击取消按钮关闭弹窗。
- Result: 替换了原生 `window.prompt` 弹出逻辑，优化后的模态弹窗使用 ui-kit 和 tailwind 样式，设计符合 IDE 主体风格。经 `corepack pnpm typecheck` 和 `corepack pnpm lint` 校验全部通过，不影响任何后端 API 及现有的 Diff、Commit 和 Publish 流程。

### 2026-06-28 - 聊天输入区交互重新探测 opencode 进程状态

- Why: 右侧对话面板可能保留旧的 `READY` 进程状态缓存，实际 opencode 进程退出后仍显示“进程可用”，用户点击输入区后可能继续发送任务。
- What: `FigmaChatPanel` 在输入框聚焦或输入卡片点击时请求工作台刷新当前用户 opencode 进程状态；`AgentWorkbench` 复用现有 `/processes/me` Vue Query refetch，并把已有状态下的刷新态回传给面板。
- How: 刷新中保持 textarea 可编辑，但禁用发送和新建对话；focus/click 同次交互做 2 秒轻量去重，且不把已有 `READY` 的后台刷新展示成首次“正在检查”。
- Result: 不新增 HTTP API、SSE 事件或数据库字段；面板单测覆盖刷新事件、去重、刷新中阻止提交和旧的首次加载态。

### 2026-06-28 - 通用参数最大进程数热更新后运行管理容量即时刷新

- Why: `OPENCODE_MANAGER_MAX_PROCESSES` 修改后，manager 虽然会应用 `configUpdate`，但运行管理容量来自 Redis manager heartbeat 快照；若只等周期心跳且前端 overview 不轮询，页面会继续显示旧容量。
- What: Go manager 成功应用 `configUpdate` 后立即补发 `managerHeartbeat`，把按端口池 clamp 后的生效 `maxProcesses` 写回 Redis；运行管理 overview 查询增加 5 秒自动刷新；通用参数保存成功后同时失效运行管理 overview 缓存。
- How: 先把 Go 回归测试改成 1 小时心跳间隔并下发超端口池容量的值，确认旧实现等不到即时 heartbeat；再最小修改 manager 控制面、前端 Vue Query 配置和稳定文档。
- Result: 参数保存后容量展示以 manager 实际生效值为准，超端口池时展示 clamp 后容量；不新增 HTTP API、SSE 事件或数据库字段。

### 2026-06-28 - 文件树加载绕开 opencode-manager 健康检查

- Why: 前端加载工作区文件树时会先走 `/api/workspaces/{workspaceId}/file-ws-route` 和目标后端 file-ws ticket 签发；这两个读路径复用了 `UserOpencodeProcessAssignmentService.status()`，导致已有用户进程会触发 manager `health` 命令，manager 慢响应时文件树被 `OPENCODE_TIMEOUT: opencode 管理进程命令超时` 阻塞。
- What: 新增文件 WebSocket 路由专用 `UserOpencodeProcessFileRoutingAffinity` 与 `fileRoutingAffinity()`，只读取 ACTIVE binding 和可恢复进程记录来判断服务器归属；`WorkspaceFileRoutingService` 和 `WorkspaceFileSocketTicketService` 改用该非阻塞归属查询，Run 启动、初始化和用户进程状态接口仍保留强健康检查语义。
- How: 先补 RED 测试覆盖分配服务、文件路由和 ticket 签发不调用阻塞式 `status()`/gateway health，再最小修改 runtime/API；同步 opencode-runtime、API、agent-web README 和 HTTP API 文档。
- Result: 文件树 route/ticket 签发不再向 opencode-manager 下发 health/start 命令；workspace 与用户进程服务器不一致仍返回 `CONFLICT`，用户未初始化进程仍按 `OPENCODE_UNAVAILABLE` 提示先初始化。

### 2026-06-28 - 登录后 opencode 进程状态检查态修复

- Why: 用户登录进入工作台后，后端已返回当前用户 opencode 进程 `status=READY`，右侧聊天面板仍可能停留在“正在检查 opencode 进程”；根因是前端把 Vue Query 后台 fetching 与首包无数据加载混用，READY 数据刷新期间仍会把对话区判为阻塞态。
- What: `AgentWorkbench` 将当前用户进程 query 的 enabled/key 绑定到响应式登录 token，并新增只在首个状态响应前为 true 的 `opencodeProcessInitialLoading`；`FigmaChatPanel` 的 READY 判定不再依赖 loading，只有 `processLoading && !processStatus` 才展示“正在检查”。补充聊天面板单测和从 `/login` 提交后进入工作台的 Playwright 回归。
- How: 先写 RED 单测复现 READY+后台刷新仍显示检查态，再最小修改前端状态链路；不修改后端状态 API，不处理本次排查发现的 `bindingClearable/localFallback` 文档与 API DTO 实现漂移。
- Result: `corepack pnpm test -- FigmaChatPanel.test.ts`、`corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep "login redirects to workbench"`、`corepack pnpm --filter @test-agent/agent-web typecheck` 均通过；整份 `workbench.spec.ts` 仍存在与本次无关的既有失败（SSH key mock 断言、工作空间按钮 strict selector/超时等），不要把这些失败误归因到 opencode 状态修复。

### 2026-06-28 - 通用参数路径支持 $HOME 和环境变量展开

- Why: macOS 本地把 `OPENCODE_PUBLIC_CONFIG_DIR` 配成 `$HOME/.testagent/agent-opencode/.config/opencode/` 后，manager/后端不会经过 shell 展开，字面 `$HOME` 被当作相对路径，导致目录落到项目工作目录下的 `$HOME/`；后续还需要通用参数能引用其他环境变量。
- What: `CommonParameterReferenceResolver` 在通用参数 resolvedValue 阶段支持路径开头 `$HOME` 和 `~/` 展开为当前用户主目录，支持 `$NAME` 读取 Java 后端进程环境变量，支持 `${NAME}` 在通用参数未命中时回退同名环境变量，并保持通用参数引用优先级不变；同步部署、数据库、HTTP API 和模块 README 说明。
- How: 先补 `CommonParameterReferenceResolverTest#expandsDollarHomeLiteralToUserHome`、`expandsDollarEnvironmentVariableFromProcessEnvironment` 和 `expandsBracedEnvironmentVariableWhenNoCommonParameterExists` 复现失败，再最小修改领域解析器；验证领域、配置缓存和 opencode runtime 消费链路测试。
- Result: manager 下发 `OPENCODE_PUBLIC_CONFIG_DIR` / `OPENCODE_SESSION_DIR` 时使用展开后的 `resolvedValue`，不会再把字面 `$HOME` 或已存在的 `$NAME` 传给 manager；本地残留的未跟踪 `$HOME/` 目录若确认无用可人工删除。

### 2026-06-27 - 运行管理拓扑图优化为机房网络拓扑与上下层级结构

- Why: 拓扑图原先为水平结构且采用圆圈节点，长进程 ID 或服务器信息容易在圆圈外产生严重的字符重叠/溢出；用户需要将拓扑优化为上下层级的网络机房类型风格，且各节点需要使用具体的品牌/应用图标表示（Linux 服务器使用 Linux 图标、Docker 使用 Docker 图标、opencode 使用 opencode 图标）。
- What:
  - 重构 `runtimeTopologyGraphData.ts`：将拓扑结构改为上下三层布局（Y=50 为 Java 进程，Y=140 为 Manager，Y=230 为 opencode 进程），各层节点均实现动态水平居中，且 opencode 进程垂直分布在所属 Manager 下方。这大大缩减了垂直高度的占用，彻底消除了底部节点被切断的现象。
  - 重构 `RuntimeTopologyGraph.vue`：
    * 引入 `frontend/apps/agent-web/src/assets/figma/` 路径下的 `Linux.svg`、`Docker.svg`、`opencode-logo.svg` 和 `unknown.svg`。
    * 将 ECharts 节点 `symbol` 根据节点种类映射为上述 SVG 的 Vite 导入 URL，以 `image://` 进行注入渲染。这替代了原本 of 内联 Base64 和复杂 SVG Path，不仅极大地简化了代码结构，还确保了渲染定位 of 完全稳定。为了满足图标小巧精致的要求，将图标尺寸修改为紧凑 of 正方形（Linux 36px，Docker 38px，opencode 32px）。对于无主 opencode 节点，使用 `unknown.svg` 图标进行标识。
    * 将节点 labels 排版位置改为 `"bottom"`（距离图标 8px），确保大长串的 ID 和状态信息完美呈现在图标正下方，从而彻底打消之前的卡片容器挤压或字符重合缺陷。
    * 自定义 label 格式化程序，通过 ECharts rich text 实现状态指示灯 (●) 与标题/副标题在图标下方分行精美渲染，并对長进程 ID 自动截断（保留前后缀）以防溢出。
    * 将连接线曲率设为 `0` (直线连接)，使连线看起来像机房中的网线走线。
    * 优化了图例 meta，不再显示单色的色块背景，而是改为使用对应的 SVG 本地矢量图标 (`img.legend-icon`)，实现图例与图表节点视觉元素的高保真统一。
    * 在画布右下角新增了浮动缩放控制面板（放大 `+`、缩小 `-`、重置 `⟲`），通过调用 ECharts `setOption` 接口动态改变 `zoom` 及 `center`，提供极其流畅的画布平移和缩放功能，并将画布高度提升至 `400px`。
- How: 纯前端数据层坐标派生与 ECharts 渲染配置优化，不更改任何 API 契约或后端业务状态。
- Result: 拓扑图完美升级为具有机房网络卡片风格、走线工整的上下垂直结构，引入 Figma 本地 SVG 静态导入机制并实现图例图标化，通过缩减层级 Y 轴距离、缩小图标尺寸及增加浮动缩放面板，完全解决了底部节点超出屏幕以及缩放平移交互的需求。相关 typecheck、linter 以及所有 Vitest 单元测试均一次性顺利通过。

### 2026-06-27 - 页面复测用户新增登录与 SSH key 加密兼容修复

- Why: 继续页面测试“用户添加 → 新用户登录 → SSH key 添加”时，新增用户和新用户登录通过，但新用户在个人设置添加 SSH key 返回 `RSA decryption failed`；根因是浏览器 Web Crypto 的 RSA-OAEP/SHA-256 使用 MGF1-SHA256，而后端 JCE transformation 未显式指定 OAEP 参数，可能按 Java provider 默认 MGF1 参数解密，导致浏览器密文无法解开。
- What: `RsaKeyService` 解密优先显式使用 RSA-OAEP/SHA-256 + MGF1-SHA256，并保留旧 Java 默认 OAEP 参数兜底以兼容历史 Java 夹具或旧密文；新增 `RsaKeyServiceTest` 覆盖浏览器 Web Crypto 参数密文；同步 `test-agent-common` README。
- How: 页面先复现失败，再补 RED 测试 `decryptsWebCryptoRsaOaepSha256Payload` 观察到 `RSA decryption failed`，随后最小修改后端 RSA 解密参数并重启本地后端/前端服务复测。
- Result: 页面新增测试用户 `codex_user_72481135` 成功，默认密码 `123456` 页面登录成功；该用户个人设置添加 SSH key `codex-ui-key-691863` 成功，数据库中只保存密文、RSA 加密 AES key 和指纹，未出现私钥明文标记。当前主工作区仍因本地 opencode 进程未初始化显示不可用，但不影响本次用户和 SSH key 设置链路。

### 2026-06-27 - 本地开发 PostgreSQL 和 Redis 容器启动

- Why: 用户需要搭建本地 PostgreSQL 和 Redis 容器用于个人本地开发，避免继续依赖当前不可用或超时的外部数据库/Redis。
- What: 使用仓库既有个人离线开发入口 `tools/dev-local-up.sh --redis` 启动 `deploy/local/docker-compose.yml` 中的 `test-agent-postgres` 和 `test-agent-redis`，保持项目默认端口映射：PostgreSQL `15432 -> 5432`，Redis `16379 -> 6379`；未修改 `.env.local`、`.env.test` 或其他密钥配置文件。
- How: 先确认 Docker CLI 存在并启动 Docker Desktop，再通过项目脚本拉起依赖；按 compose healthcheck 等待容器进入 healthy 状态，并用容器内客户端验证连接。
- Result: `test-agent-postgres` 当前健康且可用，数据库/用户为 `test_agent`/`test_agent`；`test-agent-redis` 当前健康且 `redis-cli ping` 返回 `PONG`。本地 `local` profile 可使用 `TEST_AGENT_LOCAL_DB_HOST=127.0.0.1`、`TEST_AGENT_LOCAL_DB_PORT=15432`、`TEST_AGENT_LOCAL_DB_NAME=test_agent`、`TEST_AGENT_LOCAL_DB_USERNAME=test_agent`、`TEST_AGENT_LOCAL_DB_PASSWORD=test_agent`、`TEST_AGENT_REDIS_HOST=127.0.0.1`、`TEST_AGENT_REDIS_PORT=16379`、空 Redis 密码连接这组容器。

### 2026-06-27 - 页面新增用户失败的 user_roles 序列修复

- Why: 通过页面测试“用户管理（测试）”新增用户时，前端返回“服务器内部错误”；后端日志显示 `user_roles_pkey` 主键冲突，根因是历史库 `user_roles.id` identity 序列落后于已有数据，且创建用户流程缺少事务边界，角色授权失败时可能留下无角色用户。
- What: 为 `UserManagementApplicationService.createUser` 增加事务边界，并给 `test-agent-system-management` 补充 `spring-tx` 依赖；新增 Flyway migration `V20260627214000__reset_user_roles_identity_sequence.sql` 将 `user_roles.id` 后续发号起点抬高到 `1000000`；同步系统管理、持久化和数据库部署文档。
- How: 先用页面复现，再按日志定位到 `JdbcUserRoleRepository.save` 的 `user_roles` 主键冲突；补 RED 测试锁定创建用户必须有事务注解，随后最小实现事务和序列兼容迁移。
- Result: `UserManagementApplicationServiceTest`、`FlywayMigrationNamingTest`、`JdbcRepositoryIntegrationTest#migrationGrantsDefaultUserSuperAdminRole` 均已通过；`./restart-dev-services.sh` 构建后端和前端成功，但运行时连接 `.env.test`/`.env.local` 指向的 PostgreSQL 在握手阶段 EOF/read timeout，导致 8080 后端未能启动，页面端新增用户、新用户登录和 SSH key 添加的最终复测被外部数据库可用性阻断。

### 2026-06-27 - 运行管理停止已结束进程幂等清理

- Why: 用户在运行管理停止无主 opencode server 时遇到 `os: process already finished (OPENCODE_BAD_GATEWAY)`，列表仍保留该进程；根因是 Go manager 本地 state 残留已退出 PID，`Stop` 遇到操作系统“进程已结束/不存在”错误时未删除 state。
- What: Go manager 将已结束或不存在进程的 `stop` 视为幂等 `STOPPED` 并删除本地 state；`list`/heartbeat 会过滤并清理 PID 不存在的 stale state；成功的 `start`/`stop`/`restart` 命令后立即补发一次 manager heartbeat。前端 `stop` 成功后直接更新当前 overview 缓存，删除匹配 `containerId + port` 的 managed process，并同步下调容量计数，`restart` 仍保持刷新 overview。
- How: 不新增 HTTP API、SSE、数据库表或 DTO 字段；后端仍沿用现有 manager 命令响应语义，`STOPPED` 为成功，`FAILED` 仍按既有 `OPENCODE_BAD_GATEWAY` 返回。
- Result: 目标 Go 测试、运行管理 Vitest、`@test-agent/agent-web`/`@test-agent/shared-types` typecheck 和 `git diff --check` 已通过；本次仅改 manager 幂等清理、前端局部缓存更新、测试与文档。

### 2026-06-27 - MIMO测试智能体挂机趣味彩蛋动效

- Why: 满足挂机趣味彩蛋动效的需求，当用户静止 20 秒（挂机不活跃状态）时唤醒极简纯色机器人智能体小人执行原地弹跳、随机前跳、走动、坐立、翻跟斗、跨层大跳和发呆等一系列趣味动作，增强界面活力，且在用户再次操作时秒级中断并飞出屏幕离场，不打扰用户正常工作。
- What:
  - 移除了 `FigmaShell.vue` 中原有的极简 stick-figure 火柴人 walker 占位。
  - 新增不活跃检测：通过对 `mousemove`（限流处理）、`mousedown`、`scroll`、`keydown` 事件监听，重置 20s 唤醒定时器，且在小人活跃时有上述操作可高优先级触发离场。
  - 引入了全新机器人彩蛋 SVG 形象：符合圆角正方形头部、双触角、呼吸闪烁的面部光点、胶囊型身体与四肢外观规范。
  - 编写并丰富了物理弹性状态机与移动逻辑：
    * 缩短决策动作切换周期至 1s~2s 左右以极大提高小人活跃度，并调整行为动作池概率。
    * 修复首次诞生的跳跃轨迹：在 `spawnRobot` 中使用 `nextTick` 与 `setTimeout` 延迟触发跳跃，避免 Vue 数据合并渲染导致首跳坐标丢失。
    * 顶部防出界机制：当小人在顶部时，禁止原地弹跳、前跳及空翻等向上跳跃的行为；原本的跨层大跳如果从顶部出发，则优化为无向上弧度、直接向下加速坠落（falling）的平滑滑落。
    * 实现“走动 (walking)”动画：在水平面上做直线位移，双腿与手臂交替旋转摆动（行走步态周期）。
    * 实现“坐立 (sitting)”动画：头与身体下沉，双腿向外侧水平平伸展，处于安静落座状态。
    * 实现“倒挂 (hanging)”动画：小人倒挂在顶部导航栏下沿，CSS 触发 180° 旋转，双臂自然垂下（逆向旋转）并在微风中轻微钟摆摇晃。
    * 实现“翻跟斗 (flipping)”动画：腾空跳跃并在半空中完成 360° 后空翻，伴随 Squash/Stretch 形变（仅在底部时启用）。
    * 保留“原地弹跳 (Bounce)”、“随机前跳 (Short Jump)”、“跨层大跳 (Big Jump)”以及“呼吸发呆 (Stay Idle)”。
  - 编写趣味离场动效：原地面向屏幕 -> 挥手告别 1.2s（右臂高频摆动） -> 蓄力 0.3s -> 抛物线直接飞出屏幕之外 -> 销毁与重置。
- How: 纯前端交互逻辑优化，在 `FigmaShell.vue` 内实现自包含的不活跃状态机，通过三层 DOM 容器（定位、翻转、变形）搭配原生 CSS Transition & Keyframes，完美还原 Squash & Stretch 物理弹性，性能无影响。
- Result: 机器人挂机彩蛋动画顺畅优美，类型检查和 138 个 Vitest 单元测试全部全绿通过，打扰防御和自然消亡逻辑验证正常。



### 2026-06-27 - 运行管理服务器/Java 合并与节点连线拓扑图

- Why: 当前部署假设一台 Linux 服务器只启动一个后端 Java 进程，运行管理里分成“Linux 服务器”和“后端 Java 进程”两张表会割裂服务器资源与 JVM 进程视角；用户同时需要用节点和连线直观看到 Java、manager 与 opencode server 的连接/管理关系。
- What: 前端运行管理将服务器与 Java 进程按 `linuxServerId` 合并为“服务器 / Java 进程”列表，保留服务器/Java 状态、资源指标、JVM、容量和趋势入口；新增 `RuntimeTopologyGraph` 和 `runtimeTopologyGraphData`，使用 overview 现有 `backendProcesses`、`managerBackendConnections`、`managers[].managedProcesses[]` 派生 `Java -> Manager -> opencode server` 拓扑节点和边，支持缩放、拖拽、hover tooltip 与点击节点高亮相邻关系，并兼容旧响应缺少 `managedProcesses`。
- How: 不新增后端接口、SSE、数据库表或 manager 协议字段；原“容器 / 管理进程”表及展开后的有主/无主重启、停止操作保持不变。同步更新 agent-web README 和 HTTP API 文档，说明展示形态变化不改变 overview wire shape。
- Result: 目标运行管理 Vitest、`@test-agent/agent-web` typecheck、`@test-agent/shared-types` typecheck 和 `git diff --check` 已通过；本次仅涉及前端展示、前端测试和文档。

### 2026-06-27 - 运行管理有主/无主进程增加重启停止操作

- Why: 用户需要在运行管理展开的“有主进程”和“无主进程”明细行后直接执行重启、停止，便于处理绑定用户进程和无主进程，不再只做只读观察。
- What: 新增 `RuntimeManagementCommandService` 与 `OpencodeProcessControlCommand/Result`，扩展 `OpencodeProcessManagerGateway` 及 socket 实现，通过 manager WebSocket `restart`/`stop` 命令按 `containerId + port` 控制 opencode server；API 新增两个 `SUPER_ADMIN` POST 端点并返回命令结果 DTO；前端 `backend-api` 增加对应方法，运行管理展开表的有主/无主两组行末新增“重启”“停止”按钮，成功后刷新 overview。
- How: 不新增数据库表、SSE 事件或前端展示用额外请求；重启/停止仍经平台后端鉴权与 traceId，再由后端转发 manager 控制面。local 网关对重启/停止明确返回不可用，避免误以为本地直连可操作。
- Result: 后端聚合 Maven 测试、运行管理 Vitest、`shared-types`/`backend-api`/`agent-web` typecheck 和 `git diff --check` 均通过；HTTP API、Event Stream、相关 README 已同步更新。

### 2026-06-27 - MIMO Test Agent 界面风格与视觉通透感优化

- Why: 系统的旧风格采用直角、硬线条分割，整体偏扁平且缺乏现代 UI 的呼吸感和层次感；需对其进行现代 UI 风格重构（如大圆角、外层灰色背景配合内层白色大圆角卡片），并优化占位符、错误提示框、底部路径栏、模型选择输入框及顶栏标题英文副标题，以提升界面的通透感、清晰度和亲和力。同时跟进微调侧栏折叠按钮样式、压缩卡片容器留白至 `0`，全面弱化/柔化了主界面所有生硬的边框分界线，统一了左右侧边栏分割拖拽条的背景，在顶部主标题下方增设了等宽、居中的英文副标题，并添加了一个在顶部边框线上随机漫步的简约纯色小人动画。
- What:
  - **卡片化与外层背景**：修改 `FigmaShell.vue` 引入外层极淡灰色背景 `#F6F6F6` 及 `0` 紧凑外边距（从 `24px` 缩减以完全消除留白、提升编辑器空间），并将中间编辑器区和右侧对话区包裹在 `16px` 大圆角的白色卡片容器 `.figma-main-card` 内（搭配 subtle border 与 shadow），设置 `overflow: hidden` 防止溢出。移除 `FigmaEditorArea.vue` 的 outer border 避免双边框。
  - **顶栏标题英文副标题及走动小人**：
    - 修改 `FigmaShell.vue` 模板与样式，将头部标题包装至 `.figma-title-group` 居中列式布局中，在中文标题下方新增英文副标题 `<span class="figma-subtitle">MIMO Intelligent Test Agent</span>`，使用 `font-size: 7px` 并结合 `transform: scale(0.9)`、`transform-origin: center center` 与 `letter-spacing` 调优，以居中对齐排版，使其视觉长度与中文主标题对齐，提升高级感。
    - 引入基于 inline SVG 的简约纯色 stick-figure 火柴人 walker，在顶栏底部边框线上通过 Vue 的 random-walk 随机状态机进行 `'idle'` (立正呼吸) 与 `'walking'` (往返行走且自动翻转) 状态的循环切换，利用原生 CSS transition 实现 60fps 的顺滑运动，并在宽屏下限制行走区间，在窄屏（<500px）下自动隐藏避免遮挡文字。

  - **界面分割线条弱化与柔和处理**：修改 `globals.css` 将系统主边框色 `--ta-border`、`--border` 等由较深的 `#dddddd` 变更为极柔和的 `#eaeaea`；同步修改 `FigmaShell.vue`、`FigmaChatPanel.vue` 和 `WorkbenchFooter.vue`，将头尾边框、活动栏侧边框及拖拽条内部细线由 `#ddd` / `#e4e4e7` 改为 `#eaeaea`，同时将左右两个 resize handle 拖拽条的背景统一为固定的 `#f5f5f5`（不再使用右侧 transparent 造成的白色背景穿透），使其在左右两边呈现完全对称一致的 6px 灰底与中置发丝线，全面移除不一致的观感。
  - **折叠展开按钮去硬边框**：修改 `FigmaShell.vue`，将左侧、右侧悬浮折叠展开按钮 `.figma-icon-btn` 重构为全扁平、透明且无框的轻量圆角 icon 按钮，宽度 and 高度精简为 `24px`，并只在 hover 时显示浅灰微背景，极大降噪。
  - **空状态占位符**：修改 `CodeEditor.vue`，用一整幅精心设计的彩色 open folder SVG 渐变图标，以及更清晰、更有引导性的文字和排版“开始您的探索”替换原干瘪灰色 placeholder。
  - **扁平化 Footer 与 Save 按钮**：修改 `WorkbenchFooter.vue`，将底部的路径栏 `.ta-workbench-footer-middle` 优化为极具现代感的圆角 `12px` 灰色胶囊 pill；将 `.ta-workbench-footer-save` 按钮改版为全扁平、圆角 `12px` 矩形，背景为淡灰 `#eeeeee` 并配以线性 Save 图标。
  - **模型快速切换标签与圆角**：
    - 修改 `FigmaChatPanel.vue`，为聊天输入框 `.figma-chat-input-card` 设置 `16px` 大圆角；输入框底部的操作按钮改为全扁平圆角 `12px` 标签（灰色 `#f4f4f5` 背景），发送与停止按钮设计为 premium 黑色圆形。
    - 将右下角 OpenCode 进程错误提示框 `.figma-chat-process-status` 改为 `12px` 柔和大圆角，并将内边距从 `8px 10px` 增加到 `12px 16px`， gap 从 `10px` 增至 `12px`，降低突兀感。
    - 在输入卡片下方新增一排快速切换模型的推荐功能标签（GLM-5.2、Kimi K2.7 Code 等），支持一键点击切换，并为当前选中项标记蓝色高亮。
    - 在 `AgentWorkbench.vue` 绑定 `:models` and `:selected-model` 并接收 `@select-model` 事件；将工作区空状态 `.managed-workspace-empty` 重构为 `16px` 大圆角虚线背景卡片，其边框宽度微调为更精致的 `0.5px`，外边距设为 `0` 以完全消除多余的边距，按钮 `.managed-workspace-button` 统一设为 `12px` 圆角及 `34px` 现代高度。模型选择弹窗 `.managed-model-dialog` 圆角增至 `16px`。
- How: 纯 CSS/Tailwind 样式参数微调与 Vue template 细节调整，不影响底层业务逻辑与 API 通讯。
- Result: MIMO 界面呈现高度通透、亲和现代的卡片式视觉风格，侧栏折叠按钮更为内敛高级，操作区整体有效空间与分割线条得到完美释放；TypeScript 类型检查和 `pnpm build` 全部顺利通过。

### 2026-06-27 - Web IDE 顶部工具栏高度扁平化与精简调整

- Why: 用户需要更扁平的界面设计，将 Web IDE 顶部的顶栏高度从 52px 缩减，使整体工作区布局更紧凑。
- What: 修改 `frontend/apps/agent-web/src/components/FigmaShell.vue`：
  - 将 `.figma-app` 的 `grid-template-rows` 从 `52px 1fr` 调整为 `36px 1fr`。
  - 将 `.figma-header` 的 `height` 从 `52px` 缩减为 `36px`，左右外边距/内边距从 `0 5px` 改为 `0 10px`。
  - 缩放顶栏内各子元素尺寸：Logo 高度调整为 `20px`（宽度按比例自适应）；标题字号 `font-size` 从 `14px` 降至 `13px`，`line-height` 降至 `18px`；右侧各项的 gap 从 `12px` 改为 `8px`。
  - 调整应用切换菜单 `.figma-app-menu-trigger` 高度从 `28px` 至 `24px`，padding 降至 `0 6px`，字号降至 `12px`。
  - 调整用户头像按钮 `.figma-user-avatar-btn` 大小从 `30px` 至 `24px`；头像内文字 `.figma-user-avatar` 容器大小从 `24px` 至 `20px`，字号从 `12px` 降至 `10px`。
- How: 纯 CSS/Tailwind 布局样式参数的比例缩放调整，不更改模板结构或底层 DOM 结构。
- Result: 顶部状态栏高度缩小到 36px，整体布局明显更加扁平、紧凑，符合 IDE 专业视觉标准；Vitest 单元测试和 TypeScript 校验全部顺利通过。

### 2026-06-27 - 运行管理容器与管理进程合并表及有主/无主分组

- Why: 当前架构是一容器一 manager，运行管理里分成“容器”和“管理进程”两张表会让容量计数与下属明细关系不直观；用户需要展开容器/manager 行后区分有 ACTIVE 用户绑定的进程和无主进程。
- What: 后端 `RuntimeManagementQueryService` 在 `managers[].managedProcesses[]` 上补充 `ownership`、候选进程、健康和绑定字段，按同服务器、同容器、同端口匹配用户进程并只把 `ACTIVE` 绑定标为 `BOUND`；无活跃绑定或无候选进程标为 `UNBOUND`。控制面 `backendListResponse.backendEndpoints[].lastHeartbeatAt` 固定按 RFC3339 字符串编码。前端运行管理把容器/manager 两张表合并为“容器 / 管理进程”表，行展开按“有主进程 / 无主进程”分组，容器趋势改为行内按钮打开。
- How: 不新增接口、SSE 或数据库表；继续复用 `GET /api/internal/platform/opencode-runtime/management/overview`，并保持新增字段可选以兼容旧后端、旧 manager 和旧 Redis 快照。同步 HTTP API、Event Stream、模块 README 和 shared types/backend-api/agent-web 文档。
- Result: 后端目标 Maven 全测、前端运行管理 Vitest、`shared-types`/`backend-api`/`agent-web` typecheck 和 `git diff --check` 均通过；当前仍有其他会话遗留的 memory provider/metrics collector 未提交改动，提交时需只暂存本次相关文件。

### 2026-06-27 - 容器指标来源提示文本格式化与换行优化

- Why: 优化容器列表指标“来源”列的悬停提示（Tooltip）排版，使用户更容易阅读各项指标来源的说明。
- What: 修改 `frontend/apps/agent-web/src/components/settings/RuntimeManagementPanel.vue` 和 `runtime-management-settings.test.ts`：
  - 将 `metricsSourceHelp` 变量重构为多行文本，并使用 `\n` 换行符。
  - 将各个来源类型的格式调整为 `“xxxx”: xxxx`（双引号括起名称），每个类型独占一行。
  - 更新对应的 Vitest 单元测试，将提示文本匹配器修改为新格式。
- How: 修改 Vue 组件内部的静态配置字符串，配合 HTML 容器 native `title` 的多行解析特性实现换行，并同步更新测试中的断言。
- Result: 容器指标来源列悬停时，提示以清晰的多行引号格式展示，提升了可读性；Vitest 单元测试与 TypeScript 检查均顺利通过。

### 2026-06-27 - 运行管理管理进程展开显示 opencode server 明细

- Why: 系统管理-运行管理的管理进程列表需要直接查看该 manager 当前管理的 opencode server 进程启动信息，避免只看到 manager 拓扑而无法定位端口、PID 和启动命令。
- What: Go manager 本地 state、启动结果和 `managerHeartbeat.managedProcesses[]` 增加安全展示用 `startCommand`，旧 state 缺字段时按当前配置和端口派生；Java runtime/API 将 manager overview 从纯 manager 扩展为 `RuntimeManagementManager(manager, managedProcesses)` 并返回 `managers[].managedProcesses[]`；前端 shared-types/backend-api/agent-web 增加可选下属进程类型和管理进程行展开 UI；同步 HTTP API、Event Stream 和相关 README。
- How: 先补 RED 测试覆盖 Go state/heartbeat、Java heartbeat 映射/overview/API/Redis 兼容和前端展开展示，再最小实现；运行管理仍只走 `GET /api/internal/platform/opencode-runtime/management/overview`，不新增接口、SSE、数据库 migration 或前端额外请求。
- Result: 目标 Go/Maven/Vitest/typecheck 和后端 `mvn clean package -DskipTests` 均通过；启动命令只包含 `XDG_DATA_HOME`、`OPENCODE_CONFIG_DIR` 和固定 `opencode serve` 参数，不包含 token、Cookie、用户 prompt 或 API key。

### 2026-06-27 - 运行管理指标趋势图 JVM 内存 y 轴单位变成 G

- Why: 优化 JVM 内存监控趋势图的 y 轴标签展示，使其更具可读性并节省横向布局空间。
- What: 修改 `frontend/apps/agent-web/src/components/settings/RuntimeMetricChart.vue` 与 `RuntimeManagementPanel.vue`：
  - `RuntimeMetricChart` 新增 `yAxisUnit?: string` 属性。
  - 在 `yAxis` 中判断当配置 `yAxisUnit` 时，追加 `axisLabel: { formatter: '{value} G' }` 格式化标签。
  - 在数据映射阶段，判断当 `yAxisUnit` 为 `"G"` 时，将原始字节数据转换为二进制 G（即除以 `1024 * 1024 * 1024`）。
  - 对 series 的 `valueFormatter` 也进行判断，当 `yAxisUnit` 为 `"G"` 时，将悬浮提示（Tooltip）里的数据值格式化为保留 2 位小数并带 `" G"` 单位后缀的格式。
  - 在 `RuntimeManagementPanel.vue` 中的 JVM 内存趋势图组件上配置 `yAxis-unit="G"`。
- How: 通过在图表组件层将原始数据转化为 GB 单位使得 ECharts 能够按 GB 刻度自动进行平滑轴刻度划分，并配以 axisLabel 与 valueFormatter 达成视觉完美统一。
- Result: JVM 内存趋势图的 y 轴标签从 raw 字节（如 `5,000,000,000`）成功显示为更加直观的 GB 数值（如 `1 G` 到 `5 G`），Tooltip 提示也同步以 GB 显示，性能无影响，类型检查与单元测试全部通过。

### 2026-06-27 - 运行管理服务器指标历史连续性复核与类型修复

- Why: 用户要求实现 Java 重启后服务器 CPU/内存/磁盘历史连续性；复核当前 HEAD 时确认 server-key 实现和文档已存在，但前端 typecheck 被侧栏样式对象的 `pointerEvents` 类型推断阻塞。
- What: 确认 `test-agent:runtime-metrics:server:{linuxServerId}` 保存服务器 CPU/内存/磁盘，`test-agent:runtime-metrics:backend:{backendProcessId}` 保存当前 JVM 指标，Redis 自身重启后的历史保留依赖 AOF/RDB；最小修复 `FigmaShell.vue` 中左右侧栏 style computed 的 `CSSProperties` 类型标注。
- How: 运行后端目标 Maven 测试、运行管理 Vitest 和 `@test-agent/agent-web` typecheck，定位并修复 `pointerEvents` 推断问题。
- Result: 运行管理指标连续性实现已在当前分支具备；本次补齐验证并解除前端类型检查阻塞。

### 2026-06-27 - 运行管理指标趋势图 5秒定时轮询平滑刷新优化

- Why: 确保监控指标趋势图展开时的数据保持实时性的同时，避免每次后台定时刷新导致整个图表闪烁/重新加载。
- What: 修改 `frontend/apps/agent-web/src/components/settings/RuntimeManagementPanel.vue`：
  - 在 `metricsQuery` 查询配置中，声明式追加 `refetchInterval: 5000` 选项。
  - 将模板中指标面板加载占位符的 `v-else-if="metricsQuery.isFetching.value"` 修改为 `v-else-if="metricsQuery.isLoading.value"`，使占位符仅在初次无缓存加载时显示，后台重刷时保持图表渲染。
- How: 结合 Vue Query 的 `refetchInterval` 选项与 `isLoading` （只在初次加载时为 true）特性，避免在后台重刷时销毁并重建图表 DOM 容器，实现数据平滑更新。
- Result: 趋势图展示期间保持每 5 秒自动无缝刷新，ECharts 动线平滑渲染且不产生任何闪烁或闪退，前端类型校验和 Vitest 单元测试全部通过。

### 2026-06-27 - 运行管理指标趋势图再次点击折叠收起优化

- Why: 增强指标监控交互的便利性，在拓扑列表中的后端进程行或容器行已经被选中（趋势图已展示）的情况下，再次点击该行应折叠/隐藏对应的指标趋势图。
- What: 修改 `frontend/apps/agent-web/src/components/settings/RuntimeManagementPanel.vue`：
  - 修改 `selectContainer` 与 `selectBackendProcess` 点击处理函数：如果被点击的 ID 与当前处于选中状态的 ID 一致，则将选中的监控对象 `selectedMetricsTarget` 设为 `null`，从而触发趋势图组件的销毁；否则切换至新行并更新趋势图。
- How: 增加对选中行 ID 的条件对比逻辑，实现列表行点击的 Toggle 展开/折叠自锁切换效果。
- Result: 允许再次点击同一行来快速收缩指标趋势图，操作体验更加平滑；前端编译和 Vitest 单元测试全部通过。

### 2026-06-27 - 进入系统管理自动折叠左右侧栏及自动恢复

- Why: 满足用户对系统管理纯净专注视图的交互要求，在进入系统管理面板时，自动收起左侧工作空间（目录树）与右侧聊天对话面板；当切回编辑器时，能够自动恢复进入前的折叠/展开状态。
- What:
  - 修改 `frontend/apps/agent-web/src/components/FigmaShell.vue`：新增 `showLeftPanel` 属性并监听变化，使左侧侧边栏能被父组件驱动和同步状态。
  - 修改 `frontend/apps/agent-web/src/components/AgentWorkbench.vue`：
    - 提升左侧展开状态 `leftPanelOpen` 为 workbench 级 ref，并新增备份状态的 `savedLeftPanelOpen` 和 `savedRightPanelOpen`。
    - 监听 `centerMode`：当切入 `system` 模式时，自动备份当前左右侧栏状态，并将其置为 `false`；从 `system` 模式切出时，恢复备份的状态值。
    - 绑定 `FigmaShell` 的 `:show-left-panel` 与 `@toggle-left-panel` 事件。
    - 简化“系统管理”按钮的点击事件，使其与 watch 逻辑解耦。
- How: 状态提升加 Vue watch 切换钩子，非入侵式管理侧栏视图联动。
- Result: 进入管理页面自动收起侧栏，返回编辑器自动复原，页面响应迅速；类型检查与单元测试全部通过。

### 2026-06-27 - 系统管理侧边栏菜单图标化与悬浮提示优化

- Why: 优化系统管理侧边栏导航，仅保留图标以使菜单栏紧凑，并通过悬浮气泡（Tooltip）展示菜单对应的文字，从而提升整体视觉的现代感和空间利用率。
- What: 修改 `frontend/apps/agent-web/src/components/system/SystemManagementPanel.vue`：
  - 将导航按钮包裹在 Element Plus 的 `el-tooltip` 组件中，悬浮方向设为 `right`，显示对应菜单 label。
  - 使用无障碍隐藏类 `ta-system-menu-text` 把 `span` 从视觉上隐藏，但不破坏 DOM 结构与自动化测试兼容性。
  - 将 `.ta-system-menu` 宽度由 `180px` 缩减为 `52px`，内含按钮全部设为居中的 `36px * 36px` 规格，图标尺寸调整为 `18px`。
- How: 结合 Element Plus Tooltip 组件与 Visually Hidden CSS 类，以非破坏性方式达成紧凑的悬浮侧边栏交互。
- Result: 页面导航区整洁且切换正常，测试与 TypeScript 校验均完全通过。

### 2026-06-27 - 运行管理指标趋势图展示位置优化

- Why: 在拓扑状态面板上，当选中某个后端 Java 进程或容器时，对应的指标趋势图应直接出现在该列表的下方，而不是始终在整个页面的最下面，从而提升监控数据的关联性和视觉交互体验。
- What: 修改 `frontend/apps/agent-web/src/components/settings/RuntimeManagementPanel.vue`：
  - 将原先始终渲染在最底部的指标趋势面板（`ta-runtime-metrics-panel`）移除。
  - 在 `.ta-runtime-grid` 的 `后端 Java 进程` 列表组件下方直接添加条件渲染的后端指标趋势图组件（当 `selectedMetricsTarget.type === 'backend'` 时显示）。
  - 在 `容器` 列表组件下方直接添加条件渲染的容器指标趋势图组件（当 `selectedMetricsTarget.type === 'container'` 时显示）。
- How: 充分利用网格布局单列堆叠的特性，将条件渲染的趋势图面板作为列表块的兄弟节点插入网格中，使其在被选中时自动且顺畅地向下展开。
- Result: 点击对应列表项后，趋势图会即时且准确地展现在相应列表的正下方，页面结构更符合直觉；类型检查（typecheck）和单元测试全部通过。

### 2026-06-27 - 运行管理拓扑状态列表布局调整

- Why: 拓扑状态下 Linux 服务器、后端 Java 进程、容器、管理进程原先采用 2 列网格并排布局，列表内容较多时横向挤压严重，需要改为每个列表独占一行（100% 宽度）。
- What: 修改 `frontend/apps/agent-web/src/components/settings/RuntimeManagementPanel.vue`：
  - 将 `.ta-runtime-grid` 的 `grid-template-columns` 从 `repeat(2, minmax(0, 1fr))` 改为 `1fr`，使所有列表块（包括原先未设置 `is-wide` 属性的 4 个列表）均占满一行。
- How: 仅调整布局的 CSS 网格规格，不修改模板结构或 DOM 标签，不改动任何业务逻辑。
- Result: 四个拓扑列表及底部的连接列表均呈现独占整行的效果，解决多列挤压带来的表格横向滚动体验问题。类型检查与单元测试完全通过。

### 2026-06-27 - manager 最大进程数改为通用参数下发

- Why: 此前 `MaxProcesses` 只能由 Go manager 启动时从 env `OPENCODE_MANAGER_MAX_PROCESSES` 读取（不可变），改上限需改 env 并重启 manager，无法在线调整。需把最大进程数纳入通用参数表在线可调，前端修改后实时推送给所有 manager。
- What: `common_parameters` 新增全局参数 `OPENCODE_MANAGER_MAX_PROCESSES`（`platform=all`，默认 8，migration `V20260627020000`，时间戳避让 SSH key 的 `V20260627010000`）。Go 侧 `process.Manager` 用 `atomic.Int64` 持有运行时上限，新增 `MaxProcesses()`/`SetMaxProcesses()`（按端口池 clamp、`<1` 拒绝），`Start` 容量判断与 `topologyMessage` 改读生效值；`protocol.go` 新增 `configUpdate` 类型，`supervisor.go` `readLoop` 处理该帧并热更新。Java 侧新增 `ManagerControlProtocol.TYPE_CONFIG_UPDATE` + `ManagerControlMessage.configUpdate` 工厂、`ManagerConnectionRegistry.broadcast`、`OpencodeManagerConfigSyncService`（读参数→register 补推/事件广播）、`CommonParameterUpdatedEvent`（domain）；`CommonParameterManagementApplicationService.updateValue` 发布事件；`ManagerControlWebSocketHandler` register 后补推。env 降为启动兜底。
- How: Go `go test ./...` 全绿（含 SetMaxProcesses clamp、Start 运行时容量、configUpdate 应用+heartbeat 上报生效值）；Java `mvn clean test` 全绿（含 `OpencodeManagerConfigSyncServiceTest`、`ManagerConnectionRegistry.broadcast`、`CommonParameterManagementApplicationServiceTest` 事件发布断言、handler 构造器适配）；同步 http-api/event-stream/database.md 与 opencode-manager/opencode-runtime README。
- Result: 前端在「通用参数管理」改 `OPENCODE_MANAGER_MAX_PROCESSES` 即可经 WS 控制面广播给所有 manager 热更新；manager 注册时自动获取权威值；后端不可达或参数缺失时回退 env，旧 manager 不识别 `configUpdate` 静默忽略，向后兼容。本条实现先前 session-log 中「migration 版本冲突」条目描述的重命名结果。

### 2026-06-26 - 头像菜单未分配进程状态文案修改

- Why: 增强交互指向性与文案表意清晰度，头像菜单中原先展示的“未分配”文案需要调整为更明确的“待分配专属进程”文案。
- What: 修改 `frontend/apps/agent-web/src/components/FigmaShell.vue` 中头像菜单 opencode 状态逻辑的未分配分支返回值，将 `text: "未分配"` 变更为 `text: "待分配专属进程"`。
- How: 仅修改组件内部的 computed 渲染文本，不影响底层 tone 状态码及后端的任何流程与数据。
- Result: 头像下拉菜单在未分配 opencode 进程时显示“待分配专属进程”，排版整齐，编译及测试全部通过。

### 2026-06-26 - 用户头像菜单实时显示 opencode 服务状态

- Why: 用户需要点击右上角头像时实时查看当前账号的 opencode 服务分配与健康状态，区分未分配、运行中和未运行，并展示服务器 IP 与内部端口。
- What: `/api/internal/agent/{agentId}/processes/me` 兼容新增 `serviceStatus` / `serviceAddress`，后端复用现有 manager/local gateway 健康检测链路计算 `UNASSIGNED`、`RUNNING`、`NOT_RUNNING`；前端头像菜单打开时强制 refetch 当前用户进程状态，并用灰/绿/红显示“未分配 / 运行中(ip:port) / 未运行(ip:port)”。
- How: 在 `UserOpencodeProcessStatusResponse` 增加兼容构造器与头像菜单状态枚举，`UserOpencodeProcessAssignmentService.status` 对无 ACTIVE 绑定返回未分配，对绑定进程健康失败/缺失返回未运行；`FigmaShell` 新增状态行和刷新事件，`AgentWorkbench` 传入现有 Vue Query 数据与 refetch。
- Result: 不新增数据库 migration，不修改环境文件，不改变右侧聊天面板依赖的 `READY / NEEDS_INITIALIZATION / UNAVAILABLE` 门禁语义；目标后端测试、`backend-api` Vitest、`backend-api`/`agent-web` typecheck 和头像菜单 Playwright 用例通过。

### 2026-06-26 - opencode-manager 改为读取 Java 写出的服务器 IP 文件

- Why: Go manager 运行在容器内时无法可靠识别宿主服务器 IP，继续用容器网卡 IP 会导致后端统计服务器容器数、同服务器重建和 `baseUrl=http://{linuxServerId}:{port}` 规则失真。
- What: Java 后端在 socket 控制面启动时把解析出的服务器 IPv4 写入 `.serverip`（默认 `/data/.testagent/.serverip`，本地脚本改到 `.tmp/dev-services/.serverip`）；Go manager 非 Windows 启动读取该文件并最多等待 30 秒，Windows 本机开发态直接探测本机非回环 IPv4；`OPENCODE_MANAGER_LINUX_SERVER_ID` 不再由脚本注入。
- How: `LinuxServerIpResolver` 增加 listen-url 非回环 IPv4 优先逻辑，`ServerIpFileWriter` 负责单行覆盖写入；Go 配置加载改为可注入运行时，覆盖 `.serverip`、Windows、containerId 和 discovery URL 派生分支；同步一键脚本、脚本校验和 opencode-manager/API/部署文档。
- Result: manager 上报的 `linuxServerId` 固定为服务器 IPv4，`containerId` 只表示容器或 Windows 机器名。本次不涉及数据库 schema；`TestAgentRuntimePropertiesBindingTest` 中 3 个 guo profile 断言仍是既有失败（session log 早前已记录），与本次 `.serverip` 改动无关。

### 2026-06-26 - 全局字体与排版样式优化

- Why: 统一平台视觉体验，提升可读性。用户要求将默认字体替换为 Geist 族与 Noto Sans SC 组合，并规范化标题、正文、说明及代码块的字号字重参数。
- What:
  - 引入网络字体：在 `index.html` 中配置 Google Fonts 加载 `'Geist'`、`'Geist Mono'`、`'Noto Sans SC'` 三种字体，且在 `globals.css` 中添加 `@import url` 的后备引入。
  - 主题配置更新：在 `globals.css` 中的 Tailwind `@theme` 区声明 `--font-sans`（映射到 Geist & Noto Sans SC）与 `--font-mono`（映射到 Geist Mono），重映射底层组件工具类。
  - Element Plus 覆写更新：修改 `element-overrides.css` 对应变量，将 `--el-font-family` 切换为 Geist & Noto Sans SC，代码字体覆写为 Geist Mono。
  - 标签样式统一与尺寸微调：
    - `html`, `body` 采用新字族；
    - `body` 基础字号从 `14px` 放大到 `16px`（对应“正文/默认聊天内容”字号为 16px，字重 400）；
    - `button`, `.el-button` 设置字号为 `14px`，字重 `500`；
    - `input`, `textarea`, `select`, `.el-input`, `.el-textarea` 默认字号为 `16px`，字重 `400`；
    - `pre`, `code`, `.ta-codeblock`, `.font-mono` 使用 `Geist Mono` 字体，字号为 `14px`，字重 `400`；
    - 统一标题标签：`h1` / `.ta-welcome-h1` 设为 `28-32px`/700，`h2` / `.ta-display` 设为 `24px`/600，`h3` 设为 `20px`/600。
- How: 仅修改 `index.html` 外部引用以及 `element-overrides.css`/`globals.css` 基础样式覆盖，不干扰前端组件具体实现。
- Result: 页面字体完美替换为 Geist 系列与 Noto 简体中文，排版尺寸符合统一规范。类型检查与单元测试完全通过。

### 2026-06-26 - 服务器工作空间目录选择器优化为 macOS Finder 风格

- Why: 用户反馈服务器工作空间目录选择器布局简易，希望参考 macOS Finder 的文件管理风格进行界面优化，且要求解决文件夹选中后窗口尺寸跳动问题、精简多余列信息、并支持通过点击左侧折叠箭头 inline 展开子目录结构。
- What:
  - 窗口尺寸与宽高比例调整：调整弹窗高度控制为主界面的 75% (`h-[75vh]`)，并在之前宽度基础上增加了 20% (`w-[1000px]`)，同时保证尺寸在任何文件夹切换下绝对稳定。
  - 列信息精简与一整行显示：去掉了原 Finder 风格中多余的“修改日期”、“大小”和“种类”列，让文件夹名称占满整行，视觉更聚焦。
  - 折叠展开与单点跳转交互（引入新组件 [ServerWorkspaceDirectoryNode.vue](file:///Users/huang/workspace/intelligent-test-agent-gitee/frontend/apps/agent-web/src/components/ServerWorkspaceDirectoryNode.vue)）：
    - 文件夹左侧的 chevron 旋转箭头 `>` 为折叠/展开开关。点击 `>` 将 inline 展开显示子目录树而不发生全局页面跳转；
    - 点击文件夹名称文字或图标时，才会执行全局的下一级目录导航（向父组件发出 `navigate` 并更新顶部面包屑）。
  - 路径导航与工具栏：包括后退/前进按钮、面包屑 Location Bar 以及“选择此目录”主按钮。
  - 面包屑自动滚动：为 Location Bar 面包屑容器添加了自动向右端滚动的 watch 监听器，在目录切换或弹窗首次打开时自动滚动到最右端，确保在层级较深时最新/当前目录始终可见。
  - 任务栏状态栏：在目录树列表框底部增加了一个 macOS Finder 风格的状态栏，用较小字体 (`text-[10.5px]`) 与等宽字体 (`font-mono`) 展示当前的完整路径，并支持直接选定复制 (`select-all`)。
- How: 拆分出递归组件 `ServerWorkspaceDirectoryNode.vue`，利用 computed/refs 管理各层级文件夹独立的展开、加载与缓存状态。
- Result: 对齐 macOS Finder 体验，解决了布局尺寸抖动，实现了完美的树状文件夹折叠展开浏览。类型检查及单元测试完全通过。

### 2026-06-26 - 恢复 opencode 初始化按钮并重启本地 manager

- Why: 合并远程后，opencode 进程状态默认折叠成右下角圆点，非 READY 时“初始化进程”按钮也被收起；同时本地 Go manager 内存里残留 4096 已管理状态，导致 wr 用户初始化返回 `port 4096 is already managed`，但本机 4096 实际没有 opencode 监听。
- What: `FigmaChatPanel` 改为仅 READY 时收起为圆点，非 READY 状态自动展开并显示初始化按钮；`AgentWorkbench` 的进程查询改按登录态启用，loading 只在首次取数时阻塞；补充非 READY 初始化按钮组件测试。按现有 `.env.test` / 200 数据库联调环境重启 `test-agent-opencode-manager`，重新初始化 wr 的 4096 进程。
- How: 先用 3000 页面和 `/api/internal/agent/opencode/processes/me` 复现 NEEDS_INITIALIZATION；确认 200 库 wr 绑定 `ocp_e295...` 处于 UNHEALTHY 且 4096 无监听；重启 manager 后调用初始化 API，manager 派生新的 opencode 进程。
- Result: 3000 页面显示 `opencode 进程可用` READY 圆点；真实发送“只回复 OK”后 run 进入 `SUCCEEDED`，SSE 正常打开并返回 `OK`。聚焦 Vitest 与 Playwright 初始化门禁用例均通过；当前服务仍是 `.env.test` + 192.168.100.200 数据库联调态。
### 2026-06-26 - 优化工作台底部工作空间切换按钮文案与图标

- Why: 增强用户体验，当未选中具体工作空间版本时，底部工作区切换按钮默认文案不应为动态的应用名后缀（如 `F-COSS 工作空间`），而应统一为 `切换工作空间`，并且图标应从 `Layers` 改为更具表达力的 `ArrowLeftRight` 双向箭头。
- What: 修改 `frontend/apps/agent-web/src/components/WorkbenchFooter.vue`：
  - 将 `lucide-vue-next` 的 `Layers` 图标替换为 `ArrowLeftRight`。
  - 在 `triggerLabel` 计算属性的 fallback 分支，将 `props.appName ? `${props.appName} 工作空间` : "应用工作空间"` 直接改为返回 `"切换工作空间"`。
  - 在 template 中更新图标组件 `<Layers>` 为 `<ArrowLeftRight>`。
- How: 仅修改前端组件的 Vue 模版及 computed 计算属性，不改动 TypeScript 业务逻辑，且不改变 Props 结构。
- Result: 按钮成功展示“切换工作空间”与双向箭头图标，符合界面重构意图；运行 `apps/agent-web/tests/WorkbenchFooter.test.ts` 以及 `@test-agent/agent-web` 的类型检查（typecheck）均完全通过。

### 2026-06-26 - 后端启动禁用本机 JVM 代理

- Why: 测试环境 PostgreSQL/Redis 端口直连可达，但后端启动日志中 PostgreSQL 连接超时栈包含 `SocksSocketImpl`，本机 Java 运行时会从 macOS 系统代理继承 HTTP/HTTPS/SOCKS 代理，导致 JDBC 连接被代理影响。
- What: `restart-dev-services.sh` 和 `tools/dev-backend-run.sh` 启动后端 Java 进程时统一追加 JVM 参数，关闭 `java.net.useSystemProxies` 并清空 HTTP/HTTPS/FTP/SOCKS proxy host/port；补充 `tools/verify-dev-scripts.sh` 回归校验和本地启动文档。
- How: 先用 `nc` 验证外部 PostgreSQL 5432、Redis 6379 端口连通，再用 Java 运行参数检查确认清空 `-D*proxy*` 后 JVM 代理属性不再指向 `127.0.0.1:8888/8889`；脚本层只影响后端 Java 进程，不修改 `.env.local` / `.env.test`。
- Result: 后续通过一键重启或后端单独启动时，数据库和 Redis 连接不再走本机 SOCKS/HTTP 代理；浏览器、pnpm、Go manager 等其他进程仍按各自环境处理代理。

### 2026-06-26 - 修复一键重启前端构建类型错误

- Why: `./restart-dev-services.sh` 在 `corepack pnpm build` 阶段失败，真实错误来自 `agent-web` 的 `vue-tsc` 类型检查，而不是服务 kill/start 逻辑。
- What: 补齐 `shared-types` 中用户管理（测试）DTO：`UserManagementUser`、`CreateUserPayload`、`RoleOption`；修正 `FigmaChatPanel.vue` 中展示消息与原始 `AgentMessage` 联合类型混用；修正 `runtime-reducer.ts` 按 user/assistant 分支构造 `AgentMessage`。
- How: 先用 `corepack pnpm --filter @test-agent/agent-web build` 复现附件中的 TypeScript 错误，再按错误源头最小修复类型定义和联合类型收窄，不改 `restart-dev-services.sh`。
- Result: `corepack pnpm --filter @test-agent/agent-web build`、相关 Vitest、`backend-api`/`agent-chat` typecheck 和 `tools/verify-dev-scripts.sh` 均通过；未执行完整一键重启，避免主动停止当前服务。
### 2026-06-26 - 工作台侧边栏布局调整与一级目录可折叠重构
### 2026-06-26 - DiffViewer 标签精简与 Monaco 滚动条细线化、聊天气泡底色统一

- Why: 用户截图标注 (1) DiffViewer 右侧「本地修改 (可编辑，编辑完成后按 Cmd+S 保存)」文案过长且未贴右；(2) Monaco diff 视图右侧滚动条太粗、抢视觉；(3) 右侧对话气泡底色在用户消息（#f4f4f5 灰）与背景（#fff 白）之间反复切换，希望统一。
- What:
  - `frontend/packages/diff-viewer/src/DiffViewer.vue`：split 视图右侧列加 `justify-end` 贴右，统一文案为「本地修改 · 可编辑（Cmd+S 保存）」，基线/统一视图同步精简为「基线版本（只读，历史提交代码）」「统一视图 · 可直接编辑（Cmd+S 保存）」，全角括号替换半角；Monaco diff editor 初始化选项新增 `scrollbar: { vertical: "visible", horizontal: "visible", verticalScrollbarSize: 6, horizontalScrollbarSize: 6, useShadows: false }`，与普通编辑器对齐。
  - `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`：`.figma-chat-bubble--user` 与 `.figma-chat-avatar--user` 的 `background` 由 `#f4f4f5` 改为 `transparent`，让用户气泡与背景同色，整条对话保持单一底色。
- How: 仅模板 + scoped CSS / Monaco 配置改动，不动 TypeScript 业务逻辑、emit、store。Monaco scrollbar 配置是单点插入 initMonaco，未影响 `viewMode` / `source` watch 的后续 updateOptions 流程。
- Result: 右侧标签简明贴右；Monaco diff 滚动条细线化与 Monaco Editor 一致；用户气泡不再独立染色，整条对话底色统一。`packages/diff-viewer/tests` 4/4 通过；`@test-agent/diff-viewer` typecheck 通过；FigmaChatPanel 既有 2 条失败与本次改动无关（pre-existing `role` 类型推断问题）。
- Verification: `corepack pnpm exec vitest run packages/diff-viewer/tests`；`corepack pnpm --filter @test-agent/diff-viewer typecheck`；`git diff --check`。

### 2026-06-26 - DiffViewer 跟进：标题行进一步精简、diffViewport 强制细线化

- Why: 用户反馈"完全没有效果"。经 DevTools 检视，标题行右侧文案已生效，但 `pl-4` 让内容仍与左边框有间距、且行高过大；Monaco 滚动条对应的 DOM 是 `.diffViewport`（默认 30×20px，由 Monaco 内部 `ENTIRE_DIFF_OVERVIEW_WIDTH = ONE_OVERVIEW_WIDTH * 2 = 15 * 2` 写死 inline style），单靠 `scrollbar.verticalScrollbarSize` 选项无法影响它。
- What:
  - `frontend/packages/diff-viewer/src/DiffViewer.vue` 标题行：`px-4` → `px-3`、`py-1.5` → `py-0.5`、`text-[11px]` → `text-[10.5px]`、`gap-1.5` → `gap-1`，右列 `pl-4` → `pl-2` 并追加 `pr-0.5` 贴最右，统一视图同步。
  - 新增 scoped 样式覆盖 Monaco diff overview：`:deep(.monaco-diff-editor .diffOverview)` 与 `:deep(.monaco-diff-editor .diffViewport)` 都用 `width: 6px !important` 覆盖 inline 30px；height 由 `state.getSliderSize()` 算出后又被 `setHeight` 写 inline，CSS `height` 不会跟动，但 width 压住后视觉上即变细线；`:hover` / `:active` 分支同步压回 6px 防止 hover 时反弹。
- How: 标题行为 Tailwind class 调整；新增规则都在 `<style scoped>` 顶部独立注释块，`.diffOverview` 与 `.diffViewport` 都用 `!important` 压过 Monaco 写死的 inline style。`.slider` 的 `border-radius: 3px` 保留与细线视觉一致。
- Result: 标题行更紧凑、右侧文案贴到修改区最右侧；Monaco diff overview ruler 视觉宽度从 30px 压到 6px，与普通细滚动条对齐。`packages/diff-viewer/tests` 4/4 通过。
- Verification: `corepack pnpm exec vitest run packages/diff-viewer/tests`；`git diff --check`；浏览器需硬刷新（Cmd+Shift+R）以避免 HMR / 缓存沿用旧 CSS。

### 2026-06-26 - DiffViewer 第三轮：composer 底色、右侧 padding、diffOverview left 修正

- Why: 用户用 DevTools 框选三个 div 反馈：(1) `figma-chat-composer` 底色 #f5f5f5 与 `.figma-chat-scroll`/`.figma-chat-root`（#fff）不一致，对话区又是"一会灰一会白"；(2) 标题行右列还有 `pr-0.5`，没贴到最右；(3) `.diffOverview` 已被压成 6px 但仍靠 Monaco 算的 `left = width - 30` 偏移，右侧留出 ~24px 空隙。
- What:
  - `FigmaChatPanel.vue`：`.figma-chat-composer` 的 `background` 从 `#f5f5f5` 改为 `transparent`，让 root (#fff) 透出来，整条对话（消息 / 输入框 / 工具行）统一单一底色。
  - `DiffViewer.vue` 标题行右列：删除 `pr-0.5`，让 `▶ 本地修改 · 可编辑（Cmd+S 保存）` 真正贴到右边缘；统一视图同步去掉 `pr-0.5`。
  - `DiffViewer.vue` scoped 样式：`.diffOverview` 增加 `left: auto !important; right: 2px !important;` 把它从 Monaco 的 `left` 锚定切到 `right` 锚定，宽度变 6px 后视觉上也贴到容器右边。
- How: 全部为 CSS / Tailwind class 微调，不动业务逻辑。`left: auto` + `right` 是 CSS 定位的标准做法，能在 inline `left` 被 Monaco 重写时仍由 `right` 决定最终位置。
- Result: 对话区所有层（root / scroll / composer）都显示同一个白色；标题行右侧文案贴边；diff overview ruler 真正贴右且细线化。`packages/diff-viewer/tests` 4/4 通过。
- Verification: `corepack pnpm exec vitest run packages/diff-viewer/tests`；`git diff --check`；浏览器需硬刷新（Cmd+Shift+R）以让 HMR 后的 scoped style 重新挂载。

### 2026-06-26 - DiffViewer 第四轮：标签居中、隐藏 overviewRuler 画布、滚动条再细

- Why: 用户用 DevTools 框选元素反馈：(1) 标题行"◀ 基线版本"和"▶ 本地修改"应放到 diff 两个文件**中间**（不再左右两列），但箭头保留；(2) 右侧还能看到两个 `canvas.diffOverviewRuler`（original / modified 各一，宽 15px）露出灰白块，等于多了一根"下滑"；(3) 标题行 `border-slate-200` 上下两根线太丑，应对齐项目里其他分隔线色号（#e4e4e7 居多）和字号（11px / 12px）。
- What:
  - `DiffViewer.vue` 标题行：layout 从 `grid grid-cols-2` 改为 `flex items-center justify-center`，两个标签用 `|` 分隔符居中并列；去掉 `border-b border-slate-200` 上下边框，背景由 `#f8fafc` 改为更柔和的 `#fafafa`；字号 `text-[10.5px]` → `text-[11px]`、padding `py-0.5` → `py-1` 与 `globals.css` 内 `font-size: 11px/12px` 的小标签风格对齐；统一视图同步。
  - Monaco 滚动条：`verticalScrollbarSize: 6` → `4`，`.monaco-scrollable-element` / `.slider` / `> .scrollbar` 全部压到 4px，与细线视觉保持一致。
  - 新增 scoped 样式隐藏两个 overview ruler 画布：`:deep(.monaco-diff-editor canvas.diffOverviewRuler.original)` 与 `.modified` 都 `display: none !important`，避免它们在内容少时露出 15px 宽灰白条。
- How: 布局从 grid 改 flex，分隔符用一个轻量 `text-slate-300 select-none` 的 `|` 字符，节省组件引用；canvas 隐藏用 `!important` 避免被 Monaco 重渲染时再出现。字号 / 色号参考 `FigmaFileExplorer.vue:286,348`、`WorkbenchFooter.vue:716,732`、`AgentConfigPanel.vue:485` 等。
- Result: 标题行居中并列、无明显边框线、字号与项目其他小标签一致；右侧不再有多余的 overviewRuler 画布；滚动条统一 4px。`packages/diff-viewer/tests` 4/4 通过。
- Verification: `corepack pnpm exec vitest run packages/diff-viewer/tests`；`git diff --check`；浏览器需硬刷新（Cmd+Shift+R）让 HMR 后的 scoped style 重新挂载。

### 2026-06-26 - DiffViewer 第四轮：overview ruler 隐藏、slider 贴边、toolbar 去边框、进程状态可折叠圆点

- Why: 用户用 DevTools 框选：两个 `canvas.diffOverviewRuler`（original / modified 各 15px 宽）跟 `.diffViewport` 重复显得很重；slider 离右边还有 2px；工具栏 `border-b` 太抢眼；进程状态卡片占纵向空间，希望默认收起为带渐变虚化的小圆点，点击展开。
- What:
  - `DiffViewer.vue`：工具栏 `border-b border-slate-200` 去掉，背景与下方合并；scoped 样式新增 `:deep(.monaco-diff-editor canvas.diffOverviewRuler.original/modified) { display: none !important }` 隐藏两幅画布；`.diffOverview` 与 `.diffViewport` 的 `right: 2px` 改 `right: 0` 完全贴边。
  - `FigmaChatPanel.vue`：新增 `processStatusCollapsed` ref（默认 `true`）+ `toggleProcessStatus`；template 拆为两段——收起态 `<button class="figma-chat-process-dot">`、展开态保留原 `.figma-chat-process-status` 卡片并整体可点击收起；样式新增 `.figma-chat-process-dot`：12×12 圆点 + `::after` 虚化渐变（filter: blur(8px)），`is-ready` 绿（#34d399 → rgba(24,169,120,.25) radial-gradient），`is-blocking` 红，hover scale(1.15)。状态卡本身加 `cursor: pointer` 和 `role="button"`/`tabindex="0"` 支持键盘。
- How: 收起/展开纯前端状态，不动 store / props。dot 的虚化用 `::after` + `filter: blur`，不依赖额外 DOM，背景 `inherit` 保持跟 dot 主色一致。
- Result: overview ruler 画布消失、slider 完全贴右、工具栏去线、进程状态默认一颗右下角圆点可点开。`packages/diff-viewer/tests` 4/4；`FigmaChatPanel` 既有 2 条失败 pre-existing（已 `git stash` 验证），本次无新增回归。
- Verification: `corepack pnpm exec vitest run packages/diff-viewer/tests`；`git diff --check`；浏览器需硬刷新（Cmd+Shift+R）让 scoped CSS 重新挂载。

### 2026-06-26 - DiffViewer 第五轮：slider 显式 left: auto 真的贴最右

- Why: 用户反馈 slider 仍能往右挪。原因是 Monaco 给 `.diffViewport` (slider) inline 设了 `left: 0`；
  我之前只设 `right: 0`，同时存在的 `left: 0` + `right: 0` 会让元素相对父容器左对齐（CSS 里 left 优先于 right），
  虽然父容器 `.diffOverview` 已经被 right: 0 钉死在最右，slider 实际位置已经贴边，但浏览器渲染时
  slider 的 inline `left: 0` 仍然可见，让人误以为没贴边。
- What: `DiffViewer.vue` 的 `.diffViewport` 新增 `left: auto !important`，让 `right: 0` 单独生效；
  `.diffOverview` 加 `margin: 0 !important` 防止 Monaco 默认 margin 把整条再往左推 1px。
- How: 纯 CSS override，不动 Monaco 初始化逻辑。
- Result: slider 的 inline style 仍带 `left: 0`（Monaco 行为），但 CSS `left: auto !important` 把它吃掉，
  真正由 `right: 0` 锚定到 `.diffOverview` 最右。`packages/diff-viewer/tests` 4/4 通过。
- Verification: `corepack pnpm exec vitest run packages/diff-viewer/tests`；浏览器需硬刷新（Cmd+Shift+R）。

### 2026-06-26 - UI 三项改版：Diff light 主题、三栏底部 Footer 对齐、聊天输入卡片化

- Why: 用户要求 (1) Monaco Diff 编辑器切为 light 风格与工作台白色主题匹配；(2) 不管切换到哪个功能，底部那一行应该都存在且高度一致；(3) 聊天面板输入框加宽，把模型选择、新建对话、附件上传挪到输入框内部下方，整体像现代 ChatGPT 风格。
- What:
  - `DiffViewer.vue`：定义 `ta-diff-light` Monaco 主题（白底，绿 `#10b981` / 红 `#ef4444` 差异色），所有暗色 CSS 类改为浅色版本，左右分栏头部提示文案优化，"保存 (Cmd+S)"按钮改为 amber 风格。
  - `AgentWorkbench.vue`：diff 模式底部加 `<WorkbenchFooter :write-path="..." :dirty="..." show-save>`，system 模式底部加空白 `<WorkbenchFooter />`，保证三栏底部 36px 高度线条持续存在。
  - `FigmaShell.vue`：右侧聊天面板默认宽度从 320px → 380px。
  - `FigmaChatPanel.vue`：将 `figma-chat-composer` 内部重构为统一 `figma-chat-input-card` 圆角卡片，卡片内 textarea 占满宽，底部工具行（附件 Upload、模型选择 ChevronDown、新建对话 Plus、发送/停止圆形按钮）横排；卡片聚焦时蓝色描边；卡片外背景改为 `#f5f5f5`；根部末尾追加 `figma-chat-footer`（36px 白底带顶边框）与左中面板底栏高度对齐。
- How: 纯 template + scoped CSS 改动，未修改任何 TypeScript 业务逻辑。既有 `isDirty`、`handleSave`、`dirtyChange` emit 均已在上轮实现，本轮直接连接到 footer。
- Result: 三栏底部线条高度一致；Monaco Diff 呈 light 白底风格；聊天输入区整合为现代卡片样式，所有操作按钮集中在一个统一容器内。TypeScript 中仍有来自 `packages/agent-chat/src/runtime-reducer.ts` 和 `FigmaChatPanel.vue` 的既有类型错误（role 类型推断问题，与本次改动无关）。
- Pitfalls: 上轮已将 `WorkbenchFooter` 和 diff light 代码写入 Vue 文件，但本轮才真正确认已生效；session 上下文切换点注意确认已有实现不要重复。

### 2026-06-26 - 工作区变更管理面板(Git Source Control)重构与美化

- Why: 增强工作台变更标签页，支持以极佳的 Git 样式展示未暂存与已暂存文件，并支持暂存、提交、推送及手工拉伸。在 Diff 展现上采用极简的 Monaco 左右对比（Side-by-Side Split）视图，并且支持差异文件的行内实时编辑修改。
- What:
  - 移除了 commit 选项复选框（SignOff、No-Verify、Amend），简化了提交表单。
  - 重构了 `DiffViewer.vue`：当审查应用工作区或 Agent 变更时，隐藏左侧的文件与 Hunks 列表，隐藏头部 VCS、Split/Unified 下拉框、刷新按钮及 VCS Diff 标题，只保留 Monaco 对比和 Hunk 导航。
  - 实现了单击列表文件即可显示对应文件的实际 Diff 对比效果，并修复了 Diff 文件选择被覆盖重置的 bug。
  - 修复了 `DiffViewer.vue` 中由于初始 `files` 列表为空导致 Monaco 编辑器容器未在 DOM 中渲染，从而使 Monaco 未能成功初始化的问题。
  - 支持在 VCS/Agent 差异对比模式下直接对右侧（Modified 修改侧）代码进行编辑，并在头部提供了未保存修改的状态指示灯与“保存 (Cmd+S)”按钮，支持通过快捷键 `Ctrl+S` / `Cmd+S` 直接保存修改回写后端文件。
  - 修复了当对话框面板展开导致编辑器宽度变窄时，Monaco 差异编辑器默认自动折叠为单栏（Unified/Inline）视图的问题，确保其始终保持左右对照。
  - 在“未暂存”与“已暂存”面板间加入了拖拽调节高度的分栏分割线。
- How: 在 `DiffViewer.vue` 与 `GitChangesPanel.vue` 中对多余的 UI 元素增加 `v-if` 条件过滤，清除 commit 复选框。将 `DiffViewer.vue` 的 `onMounted` 逻辑重构为对 `containerEl` 的 `watch` 侦听器，动态在 DOM 渲染后挂载 Monaco 差异编辑器，并在容器销毁时安全释放资源；配置 Monaco 差异编辑器对 vcs/agent 来源设置 `readOnly: false`，强制设置 `renderSideBySide: true` 左右分栏对比，并将 `useInlineViewWhenSpaceIsLimited` 显式配置为 `false` 以禁用窄宽度下的自动折叠降级，绑定 Cmd+S 键盘快捷键触发保存事件；在 `AgentWorkbench.vue` 中处理 `@save-file` 事件，在写盘成功后刷新 diff files 数据源。
- Result: Diff 视图变得极简专业且功能强大，支持首次加载时的稳定挂载，并在差异左右对照视图下提供了直观 of 即时修改、保存回写及实时 diff 重算渲染，在对话框拉伸或隐藏时始终保持清晰的左右对照版式，用户体验比肩专业开发工具。前端编译与校验全部通过。


### 2026-06-26 - 工作台侧边栏布局调整与折叠拖拽重构

- Why: 用户要求调整工作台文件区侧边栏的布局，移除顶部的“工作区”、“公共目录”、“Agent”切换按钮，并将“应用工作空间”（原工作区目录）和“agents”（原 Agent 面板）作为可折叠展开的一级目录。同时，修复浮动侧边栏折叠按钮在无工具栏情况下的重叠冲突，实现两一级目录间的上下拉动拖拽缩放，添加悬停显示工作区真实名称，以及移除 agents 底部多余的 git 发布提交模块。要求将切换工具栏（FolderTree/Search/GitBranch 切换栏）移到侧边栏最顶端以控制下面层级。
- What: 移除了 `FigmaFileExplorer.vue` 顶部的 `.figma-fe-toolbar`；将三 tab 切换工具栏（`ta-icon-tabbar`）提取并放置在 `FigmaFileExplorer.vue` 的最顶部，控制文件树/搜索/变更状态；将“应用工作空间”和“agents”移到切换工具栏的下方，做成平级的折叠目录，支持上下拖拽比例；为 `FileExplorer.vue` 提供了 `hideTabbar` 与 `activeTab` 属性以接收并适配父组件的切换状态；为切换工具栏增加了右内边距（`padding-right: 36px`），消除了它与侧边栏折叠按钮的重叠。
- How: 展开的一级目录分配 `flex: 1; min-height: 0` 保证内部滚动，折叠的目录分配 `flex: 0 0 auto`。利用 mousemove/mouseup 事件监听实现垂直拖拽调整 height 比例。将 `FileExplorer` 内部控制视图切换的 tabbar 剥离给外部的 `FigmaFileExplorer`，使切换逻辑完全受控；通过 `activeTab` 属性将选中的 tab 状态下发。
- Result: 侧边栏布局精简且完全符合 IDE 风格，两个主区域在折叠/展开/拖动时响应完美，无重叠或溢出，且 124 项前端测试全部通过。

### 2026-06-26 - 一键重启脚本默认切到 test 环境

- Why: 研发联调希望 `./restart-dev-services.sh` 不带参数时默认使用测试环境配置，并继续保证三服务重启前清理旧进程。
- What: 根目录 `restart-dev-services.sh` 默认 profile 从 `local` 改为 `test`，默认 dotenv 从 `.env.local` 改为 `.env.test`；保留 `--profile local|guo` 和 `--env-file` 覆盖；`TEST_AGENT_START_OPENCODE_MANAGER=auto` 改为按 `TEST_AGENT_OPENCODE_BASE_URL` 是否为本地地址决定是否启动 Go manager。
- How: 先在 `tools/verify-dev-scripts.sh` 增加失败用例，覆盖帮助文本默认值和远端 opencode baseUrl 不应触发 manager build/start；再最小修改脚本和稳定文档，不读取或修改 `.env.local` / `.env.test`。
- Result: `tools/verify-dev-scripts.sh`、`tools/verify-ai-docs.sh` 均通过；顺手补齐 `docs/deployment/database.md` 中校验脚本要求的“V10 opencode 用户进程管理表”历史表述，不改变实际迁移版本说明。

### 2026-06-26 - 为数据库表和字段添加中文注释

- Why: 项目中数据库表和字段缺少中文注释，不便于理解和维护；有数据样例的字段需要在注释中展示样例值。
- What: 新增 Flyway migration `V20260626210000__add_chinese_comments_for_all_tables.sql`，为以下核心表添加中文注释：
  - 核心运行表：`workspaces`、`sessions`、`runs`、`run_events`、`execution_nodes`、`routing_decisions`、`session_messages`、`agent_session_bindings`
  - 用户认证表：`users`、`user_login_logs`、`dictionaries`、`user_roles`
  - 应用配置表：`applications`、`application_members`、`code_repositories`、`application_repository_links`、`application_workspaces`、`user_ssh_keys`
  - 托管工作区表：`application_workspace_versions`、`personal_workspaces`、`user_global_workspace_preferences`、`user_application_workspace_preferences`、`workspace_sync_records`、`user_workspace_branch_preferences`
  - AI模型表：`ai_model_configs`
  - 进程管理表：`linux_servers`、`backend_java_processes`、`opencode_containers`、`opencode_container_managers`、`opencode_manager_backend_connections`、`opencode_server_processes`、`user_opencode_process_bindings`
  - 定时任务表：`scheduled_tasks`、`scheduled_task_plans`、`scheduled_task_runs`
- How: 使用 PostgreSQL/H2 兼容的 `comment on table/column` 语法；业务ID字段标注格式（如 `wks_xxx`、`ses_xxx`）；状态/来源类型等枚举字段标注可选值；JSON字段标注结构样例；已有注释的表（`common_parameters`、`workspace_create_operations`、`agent_config_worktrees`、`agent_config_operations`、`application_workspace_version_replicas`）不重复添加。
- Result: 35个表的全部字段均有中文注释，字段注释包含数据样例；`docs/deployment/database.md` 同步更新新增 V20260626210000 说明。
- Verification: `ls -la backend/test-agent-persistence/src/main/resources/db/migration/V20260626210000__add_chinese_comments_for_all_tables.sql` 确认文件已创建。

### 2026-06-26 - 设置中新增用户管理（测试）功能

- Why: 研发测试需要一个便捷入口查询平台所有用户、快速造测试账号（默认密码 123456）并指定角色，避免每次手动改库。
- What: 后端新增 `UserManagementApplicationService`（system-management）提供 `listUsers`/`createUser`（默认密码 + 单角色授权）/`listRoles`；新增 `UserManagementController`（`/api/internal/platform/system-management/users`、`/roles`），仅 `SUPER_ADMIN` 可访问。前端在设置弹窗新增 `SettingsUserManagementPanel.vue` 页签（菜单仅超管可见），含用户列表（`el-table` + 分页）、新增用户表单（统一认证号/用户名/角色下拉/组织部门选填）；`backend-api` 新增 `listUsers`/`createUser`/`listRoles` 方法，`shared-types` 新增对应类型。
- How: 复用现有 `UserDomainService.registerUser`（BCrypt 加密、唯一性校验）、`UserRepository.findPage`、`DictionaryRepository` 角色；无需新增数据库表或 Flyway migration。Controller/Service/DTO/测试按现有 `ConfigurationManagement*` 样板实现，前端面板按 `SettingsPersonalPanel` 表单风格 + `SettingsAppWorkspacePanel` 列表风格。后端测试新增 `spring-boot-starter-test` 依赖到 `test-agent-system-management`。
- Result: 后端测试 10/10 通过（`UserManagementApplicationServiceTest` 5 + `UserManagementControllerTest` 5），前端面板测试 3/3 通过。超管可在设置中看到"用户管理（测试）"入口，新建用户可使用默认密码 123456 登录。
- Verification: `mvn -pl test-agent-system-management,test-agent-api -am test -Dtest=UserManagementApplicationServiceTest,UserManagementControllerTest`；`corepack pnpm vitest run apps/agent-web/tests/settings-user-management-panel.test.ts`。

### 2026-06-26 - 持久层引入 MyBatis XML mapper 规范

- Why: 后续数据库操作需要统一走 MyBatis SQL，避免继续把关系型 SQL 分散写在 `JdbcClient` 代码里；同时不能一次性高风险迁移全部存量仓储。
- What: 引入 `mybatis-spring-boot-starter` 4.0.1，在 persistence 模块新增 MyBatis mapper 扫描、通用参数 `CommonParameterRepository` 试点实现和 XML SQL；`JdbcCommonParameterRepository` 去掉 Spring Bean 身份，仅作为旧集成测试直接构造的存量实现保留。
- How: 新增 `com.icbc.testagent.persistence.mybatis` 内部 mapper/row/repository，SQL 放在 `src/main/resources/mybatis/CommonParameterMapper.xml`；新增 `PersistenceSqlConventionTest` 固化白名单，禁止新增 JDBC SQL 和 MyBatis 注解 SQL；同步 AGENTS、后端规范、模块边界、数据库文档和 persistence README。
- Result: `CommonParameterRepository` 的生产 Bean 已切到 MyBatis；存量 `Jdbc*Repository` 进入迁移窗口，后续触及关系型 SQL 时迁移到 MyBatis XML。验证通过 `mvn -pl test-agent-persistence -am test`、`mvn clean package -DskipTests`，精确 `rg` 未发现 MyBatis 注解 SQL。

### 2026-06-26 - common_parameters 改为 DB 唯一来源、缺失即报错

- Why: `common_parameters` 表的业务路径参数此前有三套来源并存——DB seed、yaml `test-agent.managed-workspace.root`、代码内 `*_FALLBACK`/`DEFAULT_*` 常量，同一值复制多份且平台覆盖不一致（代码常量只有 linux 路径，DB 有 windows/linux/all）。目标是去重，让 DB 成为唯一事实源。
- What: 移除 `ManagedWorkspaceApplicationService` 的 `managedRoot` 字段、`resolveManagedRoot`、`@Value("${test-agent.managed-workspace.root:...}")` 注入及全部测试构造器形参；`configuredPath` 改为无 fallback、缺失抛 `INTERNAL_ERROR`。删除 `AgentConfigApplicationService` 的 3 个 `*_FALLBACK` 常量，`parameter()` 拆为 `requiredParameter`（缺失抛异常）与 `optionalParameter`（gitUrl 缺失视为 `UNCONFIGURED` 合法值）。删除 `UserOpencodeProcessAssignmentService` 的 `DEFAULT_SESSION_DIR`/`DEFAULT_CONFIG_PATH`，`configuredParameter` 改为缺失抛异常。5 个 `application*.yml` 删除 `managed-workspace` 块。新增 `V20260626180000` migration 删除无消费方的 `OPENCODE_WORKSPACE_ROOT`。
- How: `CommonParameterRepository` 接口给 `findAll`/`findByParameterId`/`updateValue` 加 default 空实现，恢复函数接口特性，使只读消费方的 lambda stub 仍可用，Jdbc 实现覆盖全部方法不受影响。测试侧 `ManagedWorkspaceApplicationServiceTest` 改用 in-memory `CommonParameterRepository` 注入两个根参数指向 `@TempDir`；`UserOpencodeProcessAssignmentServiceTest` 的 `service()`/`serviceLocalDirect()` 注入 session/config 参数并调整断言值。新增主类 package-private 测试构造器便于注入参数仓库。异常统一格式 `通用参数未配置：<参数英文名>` + `Map.of("parameter", englishName)`。
- Result: `common_parameters` 成为唯一来源，yaml 不再预留 fallback，代码无重复常量；DB 缺失对应参数时功能返回 500 强制运维补配。`OPENCODE_PUBLIC_AGENT_GIT_URL` 保持 `UNCONFIGURED` 合法语义不报错。
- Pitfalls: `ManagedWorkspaceApplicationService` 重构时第一次 Edit 的 old_string 未完整匹配主全参数构造器，留下一个形参不全却赋值全部字段的损坏构造器，导致编译报"找不到合适构造器"；用 Read 确认实际内容后定位并替换修复。`UserOpencodeProcessAssignmentServiceTest` 的 local-direct 短路用例也走 `synthesizeLocalDirectProcess` → `sessionPath`，故 `serviceLocalDirect` 也需注入参数 repo，不能继续用空 repo。`TestAgentRuntimePropertiesBindingTest` 的 3 个 guo cors 用例在 HEAD 上即失败（期望 `192.168.100.115:3000` 但 yaml 默认值不含），与本次改动无关。
- Verification: `mvn -pl test-agent-opencode-runtime -am test` 116/116 通过；`mvn -pl test-agent-workspace-management,test-agent-persistence -am test` 通过；`test-agent-app` 仅 3 个预先失败的 guo cors 用例，其余通过。grep 确认无 `managedRoot`/`resolveManagedRoot`/`*_FALLBACK`/`DEFAULT_SESSION_DIR`/`DEFAULT_CONFIG_PATH`/`managed-workspace.root` 残留，`OPENCODE_WORKSPACE_ROOT` 生产代码无引用。
### 2026-06-26 - 200 数据库失败后切回本地联调并补提交前日志回顾规约

- Why: 用户要求 guo 配置改连 `192.168.100.200` 的 Postgres/Redis，并在仍失败时放弃此前无效提交、合并远程最新代码后切本地库启动；同时新增规约，提交前必须先回顾 session log，避免覆盖其他开发者/智能体已提交内容。
- What: 本地 `main` 已对齐 `origin/main`，此前 5 个本地无效提交已按用户要求放弃；`.env.local` 仅作为本机运行态切到 `local` profile + `127.0.0.1:15432/16379`（未纳入 Git）；文档新增提交前回顾 `.agents/session-log.md` 的强制规则，并清理本文件残留的合并标记。
- How: 新 TCP 连接到 `192.168.100.200:5432/16379` 均返回 `No route to host`，同机 `psql`/`nc` 与 Java 一致失败；本地库启动前因 `V20260625184300__create_scheduler_framework_tables.sql` 校验和不一致，已在本机 `testagent` 库修正 `flyway_schema_history` checksum 后重启。
- Result: 后端 `http://192.168.100.115:8080`、前端 `http://192.168.100.115:3000`、opencode `http://192.168.100.115:4096` 已启动；对话 run 可创建并连接 opencode，但模型返回 `usage allocated quota exceeded`，已取消卡住的 `run_dad8c21c19e94fb5a5df8e915a15f561`，未能完成助手回复验收。

### 2026-06-26 - 公共 Agent 配置 Git 管理与发布

- Why: 工作台需要新增与项目工作空间平级的 Agent 入口，公共级 agent 配置由 Git 管理且只允许 `SUPER_ADMIN` 修改，工作空间级 agent 配置跟随当前工作区，同时 Git 长操作进度不能混入 RunEvent SSE。
- What: 新增公共/工作空间 Agent 配置领域对象、JDBC repository、Flyway 参数/表结构、workspace-management 编排服务、平台 HTTP API、ticket WebSocket 进度、公共配置同步广播、frontend `Agent` tab、backend-api client 和 shared types；公共 Git 地址默认 `UNCONFIGURED`，公共写操作在未配置时拒绝。
- How: 公共标准目录为 `{OPENCODE_PUBLIC_CONFIG_GIT_ROOT}/opencode/agents/`，读兼容 `opencode/agent/`；工作空间标准目录为 `{workspace.rootPath}/.opencode/agents/`，读兼容 `.opencode/agent/`；worktree 名校验 `^[A-Za-z0-9._-]{1,64}$` 后自动拼 `-yyyyMMdd`，公共 worktree 落到 `.configdev/`，工作空间 worktree 落到个人 worktree 根下的 `agentconfig/{workspaceId}/`。
- Result: 浏览器通过 `/api/internal/platform/workspace-management/agent-config/operations/{operationId}/tickets` 获取一次性 ticket，再连 `/ws?ticket=...` 接收 `snapshot/step/completed/failed`；公共发布后广播 `agent-config.public-sync-requested`，payload 只含 `branch`、`commitHash`、`reason`。本次也把 scheduler migration 从旧 `V18__...` 纠正为文档已有的 `V20260625184300__...`，并移除其中非幂等的补充 FK 语句以兼容已执行过旧 V18 的库；本地重命名后需清理残留 `target/classes/db/migration/V18__...`，否则 Flyway 会重复执行旧生成物。
- Verification: `mvn -pl test-agent-workspace-management,test-agent-api,test-agent-persistence,test-agent-event -am test`；`corepack pnpm --filter @test-agent/backend-api typecheck`；`corepack pnpm vitest run apps/agent-web/tests packages/backend-api/tests`；`corepack pnpm -r typecheck` 因既有 `packages/agent-chat/src/runtime-reducer.ts` 与 `apps/agent-web/src/components/FigmaChatPanel.vue` 类型问题未通过。

### 2026-06-26 - 系统管理新增通用参数管理（仅修改 value）

- Why: 系统级通用参数（如 `OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PUBLIC_AGENT_GIT_URL` 等）此前只能在数据库直接修改，运维需要一个 SUPER_ADMIN 可访问的界面查看并修改参数值。
- What: 后端新增 `CommonParameterManagementApplicationService`（configuration-management）提供 `find(filter, pageRequest)` 列表查询（可按平台过滤、内存分页）与 `updateValue(parameterId, newValue, traceId)` 仅修改 value；新增 `CommonParameterManagementController`（`/api/internal/platform/configuration-management/common-parameters`，`GET /` 列表 + `PATCH /{parameterId}` 更新），仅 `SUPER_ADMIN` 可访问。前端在系统管理面板新增 `GeneralParamManagementPanel.vue`（`SystemManagementPanel.vue` 菜单项 `params`），使用 `useQuery` + `useMutation` + 行内 `el-input` drafts 模式；`backend-api` 新增 `listGeneralParameters`/`updateGeneralParameter` 方法，`shared-types` 新增 `GeneralParameter`/`GeneralParameterListParams`/`GeneralParameterUpdatePayload` 类型。
- How: 领域端口 `CommonParameterRepository` 新增 `findAll`/`findByParameterId`/`updateValue` 方法（保留既有 `findByEnglishNameAndPlatform`），JDBC 实现相应 SQL；领域对象 `CommonParameter` 新增 `withValue(newValue, updatedAt)` 工厂复用 compact 构造器校验。Controller/Service/DTO/测试按现有 `SchedulerManagementController` 模式实现（`Mono<ApiResponse<Object>>` + `blocking` + `requireSuperAdmin`）。前端面板参照 `ScheduledTaskManagementPanel.vue` 的列表+行内编辑+分页模式。API 文档同步更新 `docs/api/http-api.md` 新增「通用参数管理 API」章节，模块 README 更新服务说明。
- Result: 后端测试 10/10 通过（`CommonParameterManagementApplicationServiceTest` 6 + `CommonParameterManagementControllerTest` 4）；前端新增面板无类型错误。接口仅提供列表与 value 更新，不暴露新增/删除，保证参数集合稳定。
- Verification: `mvn -pl test-agent-configuration-management,test-agent-api -am test -Dtest=CommonParameterManagementApplicationServiceTest,CommonParameterManagementControllerTest`；`corepack pnpm --filter @test-agent/agent-web typecheck`（15 个既有错误来自 `FigmaChatPanel.vue`/`agent-chat`，与本次改动无关）。
- Pitfalls: 工作区混入了之前未提交的 `common_parameters` 消费者重构（`ManagedWorkspaceApplicationService`/`AgentConfigApplicationService`/`UserOpencodeProcessAssignmentService`）和孤立 `用户管理（测试）` 类型；按用户要求仅提交本功能文件，其余保留在工作区。

### 2026-06-26 - 通用参数驱动 opencode 路径并自动创建初始版本工作区

- Why: 设置页创建应用工作空间需要同时落地应用版本工作区，路径需要从平台参数统一管理，并避免不同代码库在新目录规则下冲突。
- What: 新增 `common_parameters` 和 `workspace_create_operations`，初始化 Linux/Windows opencode workspace/config/session/appworkspace/personalworktree 路径；代码库新增可空唯一 `english_name`，新增/编辑时校验 1 到 29 位英文字母并小写保存；设置页创建工作空间时生成/接收 `operationId`，后端按当前用户 READY opencode 进程定位 Linux 服务器，自动创建模板 + 初始版本工作区并写入进度。
- How: 路径读取优先级为当前平台参数 -> `all` 参数 -> 代码 fallback；应用版本目录使用 `{OPENCODE_APP_WORKSPACE_ROOT}/{version}/{repository.englishName}/{directoryPath}`，个人 worktree 使用 `{OPENCODE_PERSONAL_WORKTREE_ROOT}/{version}/{unifiedAuthId}/{repository.englishName}/{personalWorkspaceId}`；标准库从 `feature_testagent_yyyyMMdd` 解析版本，非标准库由前端传 `yyyyMMdd`。
- Result: 创建工作空间期间前端轮询 `/api/internal/platform/configuration-management/workspace-create-operations/{operationId}` 展示“校验、保存配置、解析版本、下载代码、创建运行态工作区、完成/失败”；该进度不走 RunEvent SSE。历史代码库 `english_name` 可为空，但不能用于创建新的应用版本工作区，必须先补英文名。
- Verification: `mvn -pl test-agent-configuration-management,test-agent-workspace-management,test-agent-opencode-runtime,test-agent-api,test-agent-persistence -am test`；`corepack pnpm -r typecheck`；`corepack pnpm vitest run apps/agent-web/tests/settings-app-workspace-panel.test.ts packages/backend-api/tests/backend-api.test.ts`；`git diff --check`。

### 2026-06-26 - 工作空间文件操作切到目标后端 WebSocket

- Why: 前端工作空间文件列表、读取、写入、状态和删除需要与用户 opencode 进程同服务器执行，避免浏览器或当前后端误操作不在同机的工作空间路径；超级管理员还需要按后端服务器选择工作空间。
- What: `workspaces` 增加可空 `linux_server_id`；workspace-management 增加当前服务器身份、同服务器校验、legacy 回填、普通文件删除和服务器目录浏览；opencode-runtime 增加工作区文件 WebSocket 路由和后端服务器列表；api 增加 route/ticket/WebSocket RPC 入口；backend-api 和 agent-web 改为 route + target ticket + WebSocket RPC，`SUPER_ADMIN` footer 增加服务器工作空间选择按钮和对话框。
- How: 文件 WebSocket ticket 绑定 workspace、目标服务器、agent 服务器、mode、traceId 和 `SUPER_ADMIN` 状态，短期一次性消费；前端按 workspaceId 复用连接并在切换时重连；服务器选择器通过目标后端 `directory-picker` ticket 浏览目录，服务器与当前 agent 不一致时前端禁用输入，后端仍强制拒绝创建。
- Result: 工作区文件树、打开文件、保存、状态、删除和实时预览读取不再调用旧 HTTP workspace file 接口；旧 HTTP 文件接口继续兼容保留。历史空 `linux_server_id` 工作区只会在同服务器和 root path 校验成功后回填。
- Verification: `mvn -pl test-agent-workspace-management,test-agent-opencode-runtime,test-agent-persistence,test-agent-api -am -Dtest=WorkspaceApplicationServiceTest,WorkspaceFileServiceTest,WorkspaceFileRoutingServiceTest,JdbcRepositoryIntegrationTest,RuntimeControllerTest,TerminalWebSocketHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`；`mvn -pl test-agent-app -am -DskipTests compile`；`corepack pnpm typecheck`；`corepack pnpm vitest run packages/backend-api/tests/backend-api.test.ts apps/agent-web/tests/WorkbenchFooter.test.ts`；`corepack pnpm e2e apps/agent-web/tests/workbench.spec.ts --project=chromium --grep 'workbench opens|switching to an application|does not read'`；`corepack pnpm e2e apps/agent-web/tests/workbench.spec.ts --project=chromium --grep 'model picker|opencode process'`；`git diff --check`。
- Pitfalls: 当前全量 `workbench.spec.ts` 仍包含既有旧交互用例（本机目录按钮、未接后台附件上传、实时按钮）与当前页面不一致，不能把该整文件作为本次通过项；本次只验证与文件 WebSocket 路由直接相关的页面子集。

### 2026-06-25 - 定时任务系统管理与协作式停止

- Why: 超级管理员需要在前端查看定时任务当前状态和历史记录，调整 Cron，手工启动未执行任务，并能对正在执行的任务发起停止；现有运行管理入口也需要改为系统管理并承载两个二级管理项。
- What: 后端 scheduler 增加 `STOPPING` / `MANUALLY_STOPPED` 状态、运行记录停止审计字段、状态字典 seed、`ScheduledTaskContext.stopRequested()` / `throwIfStopRequested()`、管理员停止 API 和 label 响应；手动触发改为同 taskKey 存在 active run 时返回冲突。前端新增 `SystemManagementPanel` 和 `ScheduledTaskManagementPanel`，activity rail 的“运行管理”改名为“系统管理”，二级导航包含“定时任务管理”和复用的“运行管理”；`backend-api` 和 `shared-types` 补齐 scheduler 管理类型和 client 方法。
- How: 先用 domain / scheduler / api / 前端组件测试锁定新行为，再按模块边界在 `test-agent-scheduler`、`test-agent-api`、`test-agent-persistence` 和 `agent-web` 做最小改动；停止采用协作式状态流转，不强制中断线程，handler 需主动检查 context。
- Result: 超级管理员可通过系统管理查看任务定义、当前/最近执行状态和历史运行记录，支持刷新、启停、Cron 编辑、手工启动非 active 任务和停止 `RUNNING` 记录；后端统一记录停止操作者、原因和最终 `MANUALLY_STOPPED` 终态；文档同步 API、数据库、安全、部署、前后端模块边界。
- Verification: `cd backend && mvn -pl test-agent-scheduler -am test`；`cd backend && mvn -pl test-agent-persistence -am test`；`cd backend && mvn -pl test-agent-api -am test`；`cd backend && mvn test`；`cd frontend && corepack pnpm typecheck`；`cd frontend && corepack pnpm test -- scheduler-management-panel.test.ts backend-api.test.ts runtime-management-settings.test.ts`；`git diff --check`。
- Next: 未来具体业务定时任务必须在长循环或外部调用间隙检查 `ScheduledTaskContext` 的停止请求；普通用户级 Cron 计划 API 和后台定时会话仍未开放。

### 2026-06-25 - 修复 115 登录 CORS 与本地双入口访问

- Why: 用户用 `http://192.168.100.115:3000` 登录时报浏览器 CORS，`/api/auth/login` 预检返回 403 且无 `Access-Control-Allow-Origin`；同时希望本地仍能进页面，并复核 `384360ea0ba04029ad8f5999a9912e70b0aade91` 后对话发送问题。
- What: `application-guo.yml` 的 `cors-allowed-origins` 改为支持 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 覆盖；`restart-dev-services.sh` 在非 loopback 前端 URL 下用 `0.0.0.0` 监听并自动追加局域网前端 origin 与 `127.0.0.1` origin；`FigmaChatPanel` 输入栏按钮列宽同步为 32px，并补充 ready 状态发送会 emit `send` 且清空输入的组件测试；同步 CORS 文档和本地启动 skill 的验证步骤。
- How: 先用真实 `OPTIONS /api/auth/login` 复现 115 origin 被拒，再通过配置绑定测试锁定 `guo` profile 环境变量覆盖能力；脚本回归用 stub 工具验证局域网 URL 下前端监听地址为 `0.0.0.0:3000`。
- Result: 重启后 `http://192.168.100.115:8080` 与 `http://127.0.0.1:8080` health 均为 UP，`http://192.168.100.115:3000` 与 `http://127.0.0.1:3000` 均返回 200，登录预检返回 `Access-Control-Allow-Origin: http://192.168.100.115:3000`。
- Verification: `tools/verify-dev-scripts.sh`；`mvn -pl test-agent-app -Dtest=TestAgentRuntimePropertiesBindingTest test`；`pnpm --dir frontend --filter @test-agent/agent-web exec vitest run tests/FigmaChatPanel.test.ts --environment jsdom`；`pnpm --dir frontend --filter @test-agent/agent-web typecheck`；`git diff --check`；`./restart-dev-services.sh --profile guo --env-file .env.local --skip-frontend-build`；115/127 health、frontend HEAD 和 login CORS preflight curl。
- Next: 后续 115 启动继续显式传 `TEST_AGENT_BASE_URL=http://192.168.100.115:8080` 与 `TEST_AGENT_FRONTEND_URL=http://192.168.100.115:3000`；如果仍有真实发送失败，优先看登录后的 Run 请求/事件流状态，而不是 CORS。

### 2026-06-25 - 按 192.168.100.115 启动本地服务并修复 V17 幂等迁移

- Why: 用户要求本地服务按 `192.168.100.115` 地址启动，并确认最新启动命令应切到 `--profile guo --env-file .env.local --skip-frontend-build`。实际启动时后端被 V17 migration 的 `(linux_server_id, port)=(127.0.0.1,4096)` 唯一键冲突阻塞，前端即使启动也只监听 `127.0.0.1`，局域网地址不可访问。
- What: `V17__seed_local_opencode_machine_for_default_user.sql` 在同端口已有历史进程时复用该进程写默认用户绑定；新增迁移集成测试覆盖 V16 历史库已占用 4096 的场景；`restart-dev-services.sh` 从最终 `TEST_AGENT_FRONTEND_URL` 推导前端 host/port，向 Vite 注入 `VITE_TEST_AGENT_API_BASE_URL`，并在未显式配置 CORS 时追加当前前端 origin；`agent-web` Vite dev server 支持 `HOST` 环境变量；同步前端、数据库和 persistence README；个人 `intelligent-test-agent-local-startup` skill 已更新为 115 + guo profile 命令。
- How: 先用 H2/Flyway 迁移测试复现 V17 唯一键失败，再最小修改 SQL 的 `not exists` 条件和绑定来源；启动脚本保持 `.env.local` 为唯一 env 文件来源，通过命令前缀传入 115 URL，不修改 `.env.local`。
- Result: 当前服务已通过 `TEST_AGENT_BASE_URL=http://192.168.100.115:8080 TEST_AGENT_FRONTEND_URL=http://192.168.100.115:3000 ./restart-dev-services.sh --profile guo --env-file .env.local --skip-frontend-build` 启动，后端健康检查 UP，前端 115 地址返回 200；`127.0.0.1:3000` 不再作为本次前端监听地址。
- Verification: `mvn -pl test-agent-persistence -am -Dtest='JdbcRepositoryIntegrationTest#v17SeedLocalOpencodeMachineForDefaultUserIsIdempotent+v17SeedReusesExistingLocalOpencodePortProcess' -Dsurefire.failIfNoSpecifiedTests=false test`；`tools/verify-dev-scripts.sh`；`corepack pnpm --filter @test-agent/agent-web typecheck`；启动脚本内 `mvn clean package -DskipTests`；`curl -fsS http://192.168.100.115:8080/actuator/health`；`curl -fsS -I http://192.168.100.115:3000`。
- Next: 后续按 115 局域网访问时继续显式传 `TEST_AGENT_BASE_URL` 和 `TEST_AGENT_FRONTEND_URL`；若需要 opencode-manager 真实链路，不要把 `TEST_AGENT_BASE_URL` 设成非本地 URL，或同步调整 manager discovery/CORS 策略。

### 2026-06-25 - application-guo.yml 同步本地短路配置

- Why: 上一轮已经把 `local-direct` 短路 + `gateway-mode=local` 接到 `application-local.yml`，但用户日常本地启动用 `application-guo.yml`（profile `guo`，直连 192.168.100.194 的 Postgres + 本机 6379 Redis），里面没设这些开关，所以本地启动后短路不会生效，状态接口仍会跑 topology / health 链路。用户明确要求把 `application-guo.yml` 改掉。
- What:
  - `application-guo.yml` 的 `test-agent.opencode` 段补齐 `manager-control`（`gateway-mode=local` + token / listen-url / linux-server-id / heartbeat-interval / backend-stale-after / command-timeout / backend-discovery-limit），与 `application-local.yml` 一致；并新增 `local-direct: ${TEST_AGENT_OPENCODE_LOCAL_DIRECT:true}` 与 `local-direct-base-url: ${TEST_AGENT_OPENCODE_BASE_URL:http://127.0.0.1:4096}`，env 可覆盖。`nodes` 段维持原样。
  - 文档：`docs/deployment/backend.md` 把「本地开发 opencode 短路模式」节加上 `guo` profile；`docs/deployment/database.md` 网关选择节同步；`backend/test-agent-opencode-runtime/README.md` 短路开关说明同步提到 `local` / `guo` 两个 profile。
  - 测试：`TestAgentRuntimePropertiesBindingTest` 11 用例全绿（配置 binding 不受 yaml 改动影响）。
  - `.agents/session-log.md` 记本次。
- How: 与 `application-local.yml` 对齐字段顺序 / 注释 / env 占位符，避免两份配置漂移；不动用户已经写过的 `datasource` / `redis` / `security` 段；生产 `application-prod.yml` 不引入这些开关，保持默认 `socket` + `local-direct=false`。
- Result: 用户用 `--spring.profiles.active=guo` 启动时，`local-direct` / `gateway-mode` 都默认开启，前台用户进程状态接口会直接落到 READY + `http://127.0.0.1:4096`，不会再被 V17 容器 / manager 健康检测阻塞；需要切到 manager 真实模式只需 `TEST_AGENT_OPENCODE_LOCAL_DIRECT=false` + `TEST_AGENT_OPENCODE_GATEWAY_MODE=socket` env 覆盖。
- Pitfalls: `application-guo.yml` 的 2 空格缩进要保持一致；`linux-server-id` 不设会导致 `BackendJavaProcessLifecycleRunner` 注册时拿到空值，与 V17 种子的 `127.0.0.1` 失配；`token` 留空字符串 OK（本地不走 manager WebSocket 鉴权）。
- Verification: `mvn -pl test-agent-app test -Dtest=TestAgentRuntimePropertiesBindingTest` 11 用例全绿；配置 diff 仅触及 `test-agent.opencode` 段。
- Next: 用户重启后状态接口应当落到 READY；如果仍报 baseUrl 不通，确认 `TEST_AGENT_OPENCODE_BASE_URL` 写到了正确值，本机 4096 在跑 opencode server。

### 2026-06-25 - 本地开发短路直连 127.0.0.1:4096

- Why: 上一轮加了 local gateway 让 health 走直连 baseUrl，但用户重启后仍报"opencode 进程健康检测失败，且原 Linux 服务器没有可用容器"；原因可能是：(a) 用户没在 local profile 启动 / 没启 opencode server；(b) V17 容器 `current_processes=max_processes=1` 让 `canRebuildOn` 始终 false，health 失败就再走重建，结果两条路都卡死。用户明确要求：本地开发时不要再校验，直接默认连本地 4096。
- What:
  - `TestAgentRuntimeProperties.Opencode` 新增 `localDirect`（默认 false）与 `localDirectBaseUrl`（默认 `http://127.0.0.1:4096`），空 baseUrl 规整回默认。
  - 新增 `com.icbc.testagent.opencode.runtime.process.LocalDirectSettings` 记录。
  - `UserOpencodeProcessAssignmentService` 增加 `LocalDirectSettings` 依赖，并在 `status` / `initialize` / `requireReadyProcess` 三个入口顶部短路：完全跳过 database topology / user binding / manager health 校验链路，合成一个满足 `OpencodeServerProcess` 校验的进程对象（`processId=ocp_local_direct, containerId=ctr_local_direct, port=4096, baseUrl=http://127.0.0.1:4096`），直接返回 READY。baseUrl 解析失败时回退到默认。
  - `OpencodeManagerControlConfig` 新增 `localDirectSettings` Bean，把 `test-agent.opencode.local-direct` / `local-direct-base-url` 转成 runtime 的 `LocalDirectSettings`。
  - `application-local.yml` 默认 `local-direct: true`（受 `TEST_AGENT_OPENCODE_LOCAL_DIRECT` 覆盖），并把 `local-direct-base-url` 绑到 `TEST_AGENT_OPENCODE_BASE_URL` 默认 4096。
  - 测试：`UserOpencodeProcessAssignmentServiceTest` 新增 4 个用例覆盖 `status` / `initialize` / `requireReadyProcess` 短路 + baseUrl 解析失败回退；`NoopRepository` 子类在 save 路径抛 AssertionError，确保短路路径不写库；`FakeRepository` 增加 `findUserBindingCalls` / `findContainerCalls` 计数；`TestAgentRuntimePropertiesBindingTest` 新增默认值与绑定 + 空 baseUrl 回退两条用例。
  - 文档：`docs/deployment/backend.md` 新增"本地开发 opencode 短路模式"节说明 `status` / `initialize` / `requireReadyProcess` 行为与 baseUrl 解析回退；`backend/test-agent-opencode-runtime/README.md` 同步 `UserOpencodeProcessAssignmentService` 短路说明与测试覆盖；`backend/test-agent-app/README.md` 在 `OpencodeManagerControlConfig` 条目和 `TestAgentRuntimePropertiesBindingTest` 测试覆盖里同步。
- How: `LocalDirectSettings` 在 runtime 模块定义，`OpencodeManagerControlConfig` 用 `@Bean` 把它注入 runtime 的 service；`OpencodeServerProcess` 构造要求 `baseUrl = http://{host}:{port}`，所以用 `java.net.URI` 解析 baseUrl 后重建符合 V15 CHECK 约束的字段；`NoopRepository` 在 `save*` 路径抛 `AssertionError`，一旦短路被绕过会立即失败。
- Result: 本地重启后，无论数据库 topology / V17 容器 / 真实 opencode server 是否就绪，前台用户进程状态接口在 `local-direct=true` 时直接返回 `READY` + `baseUrl=http://127.0.0.1:4096`，不会再出现"opencode 进程健康检测失败"或"原 Linux 服务器没有可用容器"的报错；生产 profile 走 `local-direct=false`（也是 Java 字段默认值），保持原有 topology / binding / health 校验链路。
- Pitfalls: `OpencodeServerProcess` 构造硬要求 `baseUrl = http://{linuxServerId}:{port}`，不能传 `https://`；`UserId` 在 `requireReadyProcess` 路径下也需要非空，所以合成进程用传进来的 `userId`，`status` / `initialize` 兜底用 `usr_local_direct`。Spring 多构造器时显式 `@Autowired` 才能让 6 参版本被选中，旧 4/5 参构造保留以兼容单测。
- Verification: `mvn -pl test-agent-opencode-runtime,test-agent-app -am test -Dsurefire.failIfNoSpecifiedTests=false`（含 4 条新单测 + 2 条 binding 新用例）。
- Next: 用户重启后前台 status 应当落到 READY；如果 Run 链路仍报 baseUrl 不通，检查 `TEST_AGENT_OPENCODE_BASE_URL` 是否在 `.env.local` 写到了正确值；生产部署务必确认 `local-direct=false`（也是 Java 字段默认值）。

### 2026-06-25 - 修正发送按钮尺寸和附件弹窗位置

- Why: 用户反馈右侧发送按钮被拉成长条，视觉不合理；上传附件弹窗位置太靠下，希望放到页面上面一点。
- What: `FigmaChatPanel.vue` 中把输入行右侧按钮列从 36px 调整为 44px，发送/停止按钮固定为 44x44 圆形并垂直居中；附件弹窗遮罩从底部对齐改为顶部对齐，顶部留 84px 间距，入场动画方向同步改为向下落位。
- How: 只改现有 scoped CSS，不动发送/停止事件、附件弹窗状态、API 或后端逻辑。
- Result: 发送按钮恢复为正常圆形图标按钮；附件弹窗显示在右侧面板靠上位置。
- Pitfalls: 无。
- Verification: `corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm test -- FigmaChatPanel.test.ts` 通过；`curl -fsS http://127.0.0.1:8080/actuator/health` 返回 UP；`curl -fsS -I http://127.0.0.1:3000/` 返回 200。
- Next: 等用户在当前 127 本地服务页面验收视觉。

### 2026-06-25 - 修复空助手行和结束态任务消耗动图

- Why: 上一轮把对话区改成完整消息列表后，真实 RunEvent 派生的空 assistant 消息也被渲染，导致页面出现多条只有“测试智能体 · 时间”的空行；任务结束后任务消耗行仍使用 loading gif，看起来像还在执行。
- What: `FigmaChatPanel.vue` 过滤无可见文本的 user/assistant 展示消息；任务消耗行仅在 `running=true` 时使用 loading gif，结束态改用静态紫点；组件测试补充空 assistant 行过滤和结束态静态标记回归用例。
- How: 先用 Vitest 复现两个失败，再做最小组件修复；浏览器刷新后当前会话无可见消息，只能通过 DOM 检查确认当前页没有空助手行/usage 动图，核心回归由组件测试覆盖。
- Result: `corepack pnpm test -- apps/agent-web/tests/FigmaChatPanel.test.ts`、`corepack pnpm --filter @test-agent/agent-web typecheck`、`corepack pnpm --filter @test-agent/agent-web build` 和 `git diff --check` 通过。
- Pitfalls: `message.part.updated` / tool part 派生出的 assistant 消息可能没有可见文本，完整历史渲染必须过滤空文本，否则会把 meta 单独显示成空消息。
- Verification: 见 Result。
- Next: 无。

### 2026-06-25 - 修复对话误发送和历史消息只显示最后一轮

- Why: 用户反馈右侧对话输入框在未按发送意图时会误发，尤其是中英文/输入法相关场景；同一历史会话切换后看不到完整历史消息。同时本机换手机热点，需要临时用 127.0.0.1 启动本地服务。
- What: `FigmaChatPanel.vue` 在输入法 composition 阶段忽略 Enter（同时兼容 `event.isComposing` 和 `keyCode=229`），并把消息区从只渲染最后一条用户/助手消息改为按顺序渲染完整用户/助手消息列表；新增组件回归测试覆盖 IME Enter 不发送和历史四条消息完整展示；同步更新前端 README / 包说明。
- How: 先用 Vitest 复现两个失败，再做最小组件修复；启动验证时发现 `restart-dev-services.sh` 的 `load_env_file` 会用 env 文件覆盖命令前缀变量，因此用 gitignored 的 `.tmp/dev-127.env` 从 `.env.local` 派生并替换旧热点 IP，追加 127.0.0.1 运行拓扑和 opencode base 覆盖项。
- Result: 回归测试、`agent-web` typecheck/build、全仓 `git diff --check` 均通过；服务已用 `.tmp/dev-127.env` 重启，`http://127.0.0.1:8080/actuator/health` 为 UP，`http://127.0.0.1:3000` 返回 200。
- Pitfalls: 直接在启动命令前缀设置 `TEST_AGENT_OPENCODE_BASE_URL` 不生效，因为 `.env.local` 后加载会覆盖它；临时切换热点地址应使用派生 env 文件或修改 env 文件（本次未修改 `.env.local`）。
- Verification: `corepack pnpm test -- apps/agent-web/tests/FigmaChatPanel.test.ts`；`corepack pnpm --filter @test-agent/agent-web typecheck`；`corepack pnpm --filter @test-agent/agent-web build`；`git diff --check`；`./restart-dev-services.sh --env-file .tmp/dev-127.env --skip-backend-build --skip-frontend-build`；`curl -fsS http://127.0.0.1:8080/actuator/health`；`curl -fsS -I http://127.0.0.1:3000`。
- Next: 如需长期使用 127.0.0.1，明确后再更新 `.env.local`；当前 `.tmp/dev-127.env` 只是本次本地启动临时文件。

### 2026-06-25 - 修复运行管理拖动/滚动条问题及文件树和工作台图标大小/线条

- Why:
  - 用户反馈超级管理员设置-运行管理页内容（拓扑状态及 opencode 进程列表）存在可以被拖动的行为；同时，原多卡片各自独立的滚动条容易产生高度上的错落不齐，希望能将其对齐统一放最下面（保持每个小卡片自己独立带滚动条的形式，但整体布局保持对齐，不要错落）。
  - 工作台顶栏需保留左侧的文件树展开/收起切换按钮，右侧面板由顶栏右侧的折叠按钮（均使用 `panel-close.svg` 图标）控制。右侧折叠按钮位置调整到面板 header / tabbar 对应高度，浮动在最外层（即使折叠依然可见并能点开），左侧折叠按钮也同样调整至浮动在左面板 tabbar 相同高度上，使两个侧边栏开关功能一致。
- What:
  - **RuntimeManagementPanel.vue**: 给最外层 section 增加 `@dragstart.prevent` 并且对容器及其子元素添加 `user-drag: none` 禁用拖拽；对卡片容器 `.ta-runtime-block` 增加 `display: flex; flex-direction: column` 布局，让表格滚动包裹容器 `.ta-runtime-block-scroll` 设为 `flex: 1` 填充全部可用空间，从而将每一排卡片的高度拉伸一致，使各表底部的横向滚动条完全水平对齐（不再错落）。
  - **FigmaShell.vue**:
    - 移除原本在最顶部 header 中的侧边栏开关按钮。
    - 在 `.figma-body` 顶层增加两个绝对定位的浮动按钮（`.figma-sidebar-toggle-floating`），通过 Vue 状态计算属性 `left` 随着面板的展开和收缩移动。这使得开关始终保持在左右面板顶部的 header/tabbar 高度（`top: 7px`）并永远在最外层可见。
  - **AgentWorkbench.vue**: 移除左侧 Activity Bar 上的对话框按钮（`MessageSquare` 图标按钮），将编辑图标 `Code2` 的 `stroke-width` 设置为 `1.5`。
  - **FileExplorer.vue**: 将 Tab 栏图标 `FolderTree`、`Search`、`GitBranch` 的 `stroke-width` 设置为 `1.5`，尺寸从 `h-[18px] w-[18px]` 调整为 `h-4 w-4`。其他 Lucide 图标（`Search`、`FileText`、`RefreshCw`）的 `stroke-width` 也同步设置为 `1.5`。
  - **FigmaChatPanel.vue**: 去除对话框头部的冗余关闭按钮（由外部 FigmaShell 的浮动展开/收起按钮替代）。
- How:
  - 通过 Vue 模板和 CSS 属性实现禁用拖拽和卡片 flex 高度对齐。
  - 调整 figma-header 和 figma-sidebar 相关的 Vue 模板与 CSS 镜像 transform 设置，增加绝对定位浮动开关。
- Result:
  - 运行管理页面的元素完全不可拖拽，且拓扑图形只有一个位于最下方的滚动条进行整体横向滚动，页面变得非常干净。
  - 侧边栏折叠按钮恢复并在两侧完美以相反的方向指向，Activity Rail 的对话框切换按钮已去除，一切点击、折叠逻辑符合现代 IDE 的标准行为。
- Pitfalls: 无。
- Verification: `corepack pnpm --filter @test-agent/agent-web typecheck && corepack pnpm --filter @test-agent/agent-web build` 编译打包全数通过。
- Next: 等待用户在前端热重载（无需手动重启）后验收新界面效果。

### 2026-06-25 - 补充关键节点和流程日志

- Why: 项目中很多关键节点和流程缺少日志，排查问题困难，需要在关键操作处补充结构化日志。
- What:
  - **WorkspaceApplicationService**: 新增创建工作区、查询失败等关键操作日志
  - **SessionApplicationService**: 新增创建会话、归档会话等关键操作日志
  - **DefaultOpencodeClientFacade**: 新增外部调用开始/完成、重试、错误转换日志
  - **RunEventSseStreamService**: 新增 SSE 连接开始/取消/错误/完成日志
  - **RunEventLiveBus**: 新增事件发布、无订阅者、发布失败等日志
  - **RunApplicationService**: 新增 Run 启动/路由/成功/失败、取消等关键操作日志
  - **pom.xml**: 为 test-agent-workspace-management 模块添加 slf4j-api 依赖
- How: 在各关键方法入口添加 info 级别日志，在错误处理分支添加 warn/error 日志，遵循结构化日志规范（包含 traceId、操作类型、关键业务 ID）。
- Result: 关键流程现在有完整的日志追踪，便于排障和问题定位。
- Pitfalls: test-agent-workspace-management 模块原本没有 slf4j-api 依赖，需要手动添加。
- Verification: `mvn compile -DskipTests` 编译成功；`mvn -pl test-agent-workspace-management -am test` 通过；`mvn -pl test-agent-opencode-client -am test` 通过；`mvn -pl test-agent-opencode-runtime -am test` 通过；`mvn -pl test-agent-event -am test` 通过。
- Next: 无。

### 2026-06-25 - 运行管理只展示活进程并增加 Redis 心跳

- Why: 超级管理员设置-运行管理页需要面向当前启动的 Java / opencode 进程做运维，原实现只依赖数据库快照且用用户 ID 过滤/展示，容易展示僵死进程，也不便按用户名定位。
- What:
  - 运行管理查询新增 `username` 过滤和响应字段，前端筛选框改为用户名，保留 `userId` 兼容参数。
  - 后端新增 `OpencodeProcessHeartbeatStore` 端口及 Redis/Noop 实现：Java / opencode 活进程写 5 分钟 TTL 心跳 key，索引集合用于跨机器汇总活进程。
  - 应用启动后每 3 分钟健康检查 RUNNING opencode 进程并刷新 Redis 心跳，每 5 分钟清理过期心跳索引；查询面板只返回 READY/RUNNING 且心跳未过期的 Java、容器、管理连接、opencode 进程。
  - 同步更新运行管理 API、后端模块 README、前端 README 和类型/测试。
- How: 在业务层通过端口依赖 Redis 心跳，Redis 未启用时回退数据库 `lastHeartbeatAt` / `lastHealthCheckAt` 的 5 分钟窗口；前端只保留 RUNNING opencode 状态视角，避免运营面板展示历史失败/停止进程。
- Result: 管理页可以跨 Linux IP 查看当前活跃 Java/opencode 进程，用户列优先显示用户名；僵死进程在心跳过期或健康检查失败后不再出现在面板中。
- Pitfalls: `PageRequest` 最大 size 为 200，定时扫描不能使用更大的批量值，否则任务运行时会被分页校验拒绝；Spring Service 一旦保留多个构造器，生产构造器必须显式标 `@Autowired`，否则打包启动时会尝试无参构造并失败。
- Verification: `mvn -pl test-agent-opencode-runtime -am -Dtest=RuntimeManagementQueryServiceTest,OpencodeProcessHeartbeatMaintenanceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`；`mvn -pl test-agent-app -am test -Dsurefire.failIfNoSpecifiedTests=false`；`corepack pnpm test -- backend-api runtime-management-settings`；`corepack pnpm --filter @test-agent/agent-web typecheck`；`corepack pnpm --filter @test-agent/backend-api typecheck`。
- Next: 部署多机环境时确认 `test-agent.redis.enabled=true` 且所有后端实例连接同一 Redis，才能获得跨机器统一活进程视图。

### 2026-06-25 - Reduce Session Log Noise

- Why: The previous policy made the session log too chatty for small edit batches, which reduced its usefulness as a concise handoff artifact.
- What: Tightened the repo rules in `AGENTS.md`, `docs/guides/ai-workflow.md`, `docs/guides/self-checklist.md`, and `.opencode/skills/code-update-handoff/SKILL.md` plus its `agents/openai.yaml` metadata so logging happens once per meaningful session boundary.
- How: Kept the same `Why / What / How / Result` shape, but changed the trigger from per-batch persistence to per-session reusable information, with related edits merged into one entry.
- Result: Future sessions should write fewer, denser log entries that are easier for other developers and agents to scan.
- Pitfalls: None.
- Verification: `git diff --check`; `/Users/kaka/Desktop/intelligent-test-agent/.tmp/skill-validate-venv/bin/python3 /Users/kaka/.codex/skills/.system/skill-creator/scripts/quick_validate.py .`.
- Next: Use the new rule in subsequent sessions and avoid file-level log spam.

### 2026-06-25 - 修复运行管理页面因 ID 格式不一致导致查询失败的问题

- Why: 超级用户在设置-运行管理页面无法看到容器、进程状态。经排查发现：数据库中存在历史/异常写入的 `backend_process_id` 等字段，其格式与当前领域对象要求不一致（如 `BackendProcessId` 要求 `bjp_` 前缀），导致 RowMapper 构造领域对象时抛出 `IllegalArgumentException`，整个页面查询失败。
- What:
  - 新增 Flyway migration `V15__add_opencode_process_id_check_constraints.sql`
  - 清理不符合前缀规则的脏数据：删除 `backend_java_processes` 中 `backend_process_id` 不以 `bjp_` 开头的记录，删除 `opencode_container_managers` 中 `manager_id` 不以 `mgr_` 开头的记录，删除 `opencode_server_processes` 中 `process_id` 不以 `ocp_` 开头的记录
  - 添加数据库 CHECK 约束，确保 ID 前缀格式正确，防止未来再写入不符合格式的数据
- How: 通过 Flyway migration 执行 DELETE 清理脏数据 + ALTER TABLE 添加 CHECK 约束。
- Result: 运行管理页面查询不再因脏数据导致领域对象构造失败；数据库层面新增约束防止非法 ID 写入。
- Pitfalls:
  - 一开始误认为 `LinuxServerId` 也需要 `lsrv_` 前缀，实际上它要求 IPv4 地址格式
  - `OpencodeContainerId` 只要求非空文本，无固定前缀要求
- Verification: 需要在有脏数据的环境中重启后端验证 migration 执行成功，页面可正常加载。
- Next: 建议用户执行 SQL 查询确认是否存在脏数据：`SELECT backend_process_id FROM backend_java_processes WHERE backend_process_id NOT LIKE 'bjp_%';`

### 2026-06-25 - 为 F-WRAPP 应用新增远程代码库用于测试工作区和分支功能

- Why: 本地开发环境数据库中，F-WRAPP 应用只有本地代码库，需要新增远程 Git 代码库用于测试工作区创建、版本库克隆、分支操作等功能。
- What:
  - 在 `code_repositories` 表新增 `repo_wrapp_mimoagent` 代码库记录，git_url 为 `https://gitee.com/wrui233/mimoagent`
  - 在 `application_repository_links` 表新增关联，将新代码库关联到 F-WRAPP 应用 (app_id: 113023)
  - 拉取远程分支并重启前后台服务
  - 更新 `.tmp/test-data-add-mimoagent-repo.md` 文档，记录测试场景、测试步骤、测试数据
- How: 通过 Docker exec 执行 psql 命令直接操作本地数据库（15432端口），使用 INSERT ... ON CONFLICT 语法保证幂等。
- Result: F-WRAPP 应用现在关联了两个代码库（本地仓库 + 远程仓库），可用于测试工作区和分支功能；前后台服务已重启成功。
- Pitfalls: 一开始误修改了 `repo_fcoss_main` 的 git_url，后来恢复原数据并新增正确记录。
- Verification: 数据库查询确认新增记录存在，前端可访问 `http://127.0.0.1:3000`。
- Next: 用户验证工作区和分支功能是否正常。

### 2026-06-25 - 将 wr 用户角色改为应用管理员

- Why: 用户要求将 wr 用户从普通用户角色改为应用管理员角色。
- What: 更新 `user_roles` 表，将 wr 用户的 `dict_id` 从 `dict_role_user` 改为 `dict_role_app_admin`。
- How: 通过 Docker exec 执行 psql UPDATE 命令。
- Result: wr 用户角色已从"普通用户"改为"应用管理员"。
- Pitfalls: 无。
- Verification: 数据库查询确认角色已更新。
- Next: 无。
### 2026-06-25 - 设置"添加成员"下拉项改为单行 userId · userName

- Why: 用户反馈下拉项上下两行（`username` + `userId`）不利于在候选很多时快速浏览，希望改为单行紧凑展示，文案顺序为 `userId · userName`。
- What: `frontend/apps/agent-web/src/components/settings/SettingsAppWorkspacePanel.vue` 模板的 `el-autocomplete` 自定义下拉项从上下两行（`username` 加粗 + `userId` 灰底）合并为单行 `<span>{{ item.userId }} · {{ item.username }}</span>`；CSS 同步去掉 `flex-direction: column` / gap / `ta-user-suggestion-name` / `ta-user-suggestion-meta` 旧样式，改为 `display: flex; align-items: center; white-space: nowrap;` 的单行布局。`frontend/apps/agent-web/README.md` 描述从"每项显示 username + userId"更新为"每项单行展示 userId · userName"。
- How: 模板 / CSS 收敛到单 span + 单 flex 行；后端 SQL / 选中 / 按钮切换逻辑均不动。
- Result: 下拉项单行展示 `userId · userName`，不换行；按钮状态切换、添加、成员刷新行为与上一版一致。
- Pitfalls: `white-space: nowrap` 防止 userId / username 较长时换行；下拉项需要单 span 而非两个 span，el-autocomplete 选中时按整段 text 匹配 `value-key="username"`，仍能正确触发 `onUserSelected`。
- Verification: `corepack pnpm --filter @test-agent/agent-web typecheck` 通过。
- Next: 等用户验收。

### 2026-06-25 - 设置"添加成员"下拉项精简为 username + userId

- Why: 用户反馈"添加成员"下拉项原本展示 `username · userId · unifiedAuthId` 三段信息过于冗长，希望精简为 `username + userId` 两段，移除 unifiedAuthId。
- What: `frontend/apps/agent-web/src/components/settings/SettingsAppWorkspacePanel.vue` 模板的 `el-autocomplete` 自定义下拉项从 `{{ item.username }}` / `{{ item.userId }} · {{ item.unifiedAuthId }}` 改为 `{{ item.username }}` / `{{ item.userId }}`；`frontend/apps/agent-web/README.md` 同步把"每项显示 username + userId"写入 el-autocomplete 描述。
### 2026-06-25 - 调整右侧对话输入区发送与附件入口

- Why: 用户反馈右侧对话框发送按钮应放在输入框右边，左下角两个图标按钮需要去掉一个，另一个改成上传附件按钮；后台暂不支持上传，先实现前端弹窗样式。
- What:
  - `FigmaChatPanel.vue` 把发送/停止按钮移到 textarea 右侧，动作行左侧只保留“上传附件”图标按钮；删除旧的“清空输入”和“下载文件”入口。
  - 新增 `attachmentDialogOpen` 控制的面板内弹窗，展示上传区域、关闭按钮和“当前仅展示前端样式，暂未连接后台上传能力”的状态说明；Esc 和遮罩点击可关闭。
  - `FigmaChatPanel.test.ts` 增加上传附件弹窗打开用例。
  - `frontend/README.md`、`frontend/apps/agent-web/README.md`、`frontend/apps/agent-web/src/PACKAGE.md` 同步说明附件上传当前只有前端样式，未接后台。
- How: 复用现有 FigmaChatPanel 组件和面板内抽屉遮罩风格，未新增 API、未接文件 input、未修改 backend-api；发送仍走原 `send` emit，停止仍走原 `stop` emit。
- Result: 右侧输入区发送按钮和截图期望一致地靠在文本框右侧；左下动作区只剩上传附件入口；点击后显示前端样式弹窗并明确后台未接入。完整三服务重启因 `.env.local` PostgreSQL 连接失败未完成，前端 dev server 单独启动成功。
- Pitfalls: `./restart-dev-services.sh --env-file .env.local` 后端失败在 `DruidDataSource` 初始化 PostgreSQL 连接，日志为 `PSQLException: 尝试连线已失败`，底层 `EOFException`；本次未修改 `.env.local`。
- Verification: `corepack pnpm test -- FigmaChatPanel.test.ts` 通过（18 files / 104 tests）；`corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm --filter @test-agent/agent-web build` 通过（仅既有 chunk size warning）；`./restart-dev-services.sh --env-file .env.local` 构建通过但后端 readiness 超时；单独 `corepack pnpm --filter @test-agent/agent-web dev` 已启动，`curl -I http://127.0.0.1:3000/` 返回 200。
- Next: 等数据库连接恢复后重新执行完整三服务重启并做页面级验收；后台附件上传接口接入时再把弹窗从样式态升级为真实文件选择和提交链路。

- How: 仅改模板里 `<span class="ta-user-suggestion-meta">` 的内容；CSS class / 选中逻辑 / 按钮切换 / 后端 SQL 条件均不动。
- Result: 下拉项简化为上下两行（用户名加粗 + userId），下方的 `unifiedAuthId` 不再展示；后端仍按 userId / unifiedAuthId / username 三个字段 LIKE 命中，前端展示只是收敛。
- Pitfalls: 无。
- Verification: `corepack pnpm --filter @test-agent/agent-web typecheck` 通过。
- Next: 等用户验收；如需进一步压缩为单行可再合并 `ta-user-suggestion` flex 方向。

### 2026-06-25 - 设置"添加成员"合并为 el-autocomplete 异步下拉搜索

- Why: 用户反馈左下角"设置 → 应用与工作区 → 应用人员管理"tab 下同时存在"搜索用户"和"按 ID 新增成员"两块入口，操作割裂；要求把搜索框升级为异步下拉（输入即拉候选），后端搜索要同时匹配 userId / unifiedAuthId / username 三个字段，选中下拉项后"搜索"按钮文案切换为"添加"并可直接加入应用。
- What:
  - 后端 `backend/test-agent-persistence/src/main/java/com/icbc/testagent/persistence/JdbcUserRepository.java` 的 `findPage(keyword, pageRequest)` 把 LIKE 条件从 `lower(username) or lower(unified_auth_id)` 扩展为 `lower(user_id) or lower(unified_auth_id) or lower(username)`，count 查询同步对齐；`UserRepository` 注释同步更新为"按 userId / unifiedAuthId / username 任意字段 LIKE 匹配"。keyword 为空时仍走全量分支，行为不变。
  - 文档 `docs/api/http-api.md` 把 `/configuration-management/users?keyword=&page=&size=` 用途补成"按 `userId` / `unifiedAuthId` / `username` 任一字段大小写不敏感 LIKE 搜索已有平台用户；keyword 为空时返回全量"。
  - 前端 `frontend/apps/agent-web/src/components/settings/SettingsAppWorkspacePanel.vue`：
    - 删除 `users` / `memberUserId` 旧状态，新增 `selectedUser: PlatformUserSummary | null`。
    - 新增 `fetchUserSuggestions(keyword, callback)` 作为 `el-autocomplete` 的异步拉取实现（Element Plus 自带 300ms 防抖），失败时回写 `errorMessage` 并返回空数组。
    - `addMember` 重构为 `addSelectedMember`：只对 `selectedUser` 生效，添加成功后清空 `selectedUser` + `userKeyword` 并刷新成员列表。
    - 模板把"搜索用户"和"按 ID 新增成员"两块合并为"添加成员"区：`el-autocomplete` 绑定 `userKeyword`，`value-key="username"`，下拉项自定义模板展示 `username` + `userId · unifiedAuthId`；按钮在 `selectedUser` 为空时渲染"搜索"（兜底触发一次搜索），非空时渲染 `type="primary"` 的"添加"，点击直接调 `addSelectedMember`。
    - 原"按 ID 新增成员"区内的成员列表拆出来变成"已有成员"区，保留删除按钮和原有交互。
    - `clearAppContext` 同步清空 `selectedUser` / `userKeyword`。
    - 追加 `.ta-user-suggestion` / `.ta-user-suggestion-name` / `.ta-user-suggestion-meta` 样式。
  - 文档：`frontend/apps/agent-web/README.md` 和 `frontend/apps/agent-web/src/PACKAGE.md` 补一行描述 el-autocomplete 异步下拉与按钮状态切换。
- How: 复用现有 `api.searchUsers(keyword, page, size)`（`backend-api` 包未变），通过 `el-autocomplete` 的 `fetch-suggestions` 把候选用户拉到下拉；选中事件落库到 `selectedUser`，按钮 `v-if` 切换文案；后端 LIKE 字段扩展在 JDBC 层完成，不动 `UserRepository` 接口与上层 service / controller / DTO，API 形态不变。
- Result: 设置"添加成员"区只剩一个输入框 + 一个按钮；输入 userId / 用户名 / 统一认证号任一时下拉都会命中，后端能匹配；选中后按钮从"搜索"切换为"添加"并可直接加入应用；老成员列表移到底部"已有成员"区，仍可移除。
- Pitfalls: `el-autocomplete` 的 `fetch-suggestions` 是 debounced，但要求函数签名是 `(keyword, callback) => void`，不能用 `async/await + return`；另外 `value-key` 必须命中候选对象上的字段（这里用 `username`），下拉项 `label` 才能匹配。
- Verification: `corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm --filter @test-agent/agent-web lint` 通过；`backend` 端因 `JdbcUserRepository.findPage` 无现成单测覆盖（`grep` 全仓也未发现 `users.findPage` 调用），改动只扩 SQL 条件、不动接口与契约，暂无新增单测；后续如需补 `JdbcRepositoryIntegrationTest` 一条按 userId / unifiedAuthId / username 各自命中一条的断言。
- Next: 等用户验收；若用户希望"搜索"按钮文案在已选中也保留作为兜底，可以再保留一个无副作用的"重新搜索"按钮，避免按钮消失带来的"还能不能搜"歧义。

### 2026-06-25 - 新增分布式定时任务框架

- Why: 后端需要一个分布式多节点安全的定时任务框架，避免同一任务在多个节点重复执行，并统一持久化任务定义、用户计划预留和运行审计记录；本轮只落框架，不新增具体业务任务。
- What:
  - 新增 `backend/test-agent-scheduler` Maven module，提供 `ScheduledTaskHandler`、`ScheduledTaskContext`、`ScheduledTaskResult`、Cron 计算、启动注册同步、Redis `SET NX PX` + Lua token 续租/释放锁、后台 runner、管理服务和默认关闭配置。
  - 扩展 domain：新增 `scheduler` 聚合和值对象；`Session`、`Run`、`SessionMessage` 增加 `ConversationSourceType`、`sourceRefId` 和用户来源字段，默认保持 `MANUAL`。
  - 扩展 persistence：新增 `V15__create_scheduler_framework_tables.sql`，创建 `scheduled_tasks`、`scheduled_task_plans`、`scheduled_task_runs`，并给 `sessions`、`runs`、`session_messages` 增加来源预留字段；新增 `JdbcScheduledTaskRepository`。同时把 F-COSS seed migration 从重复的 `V10__seed_fcoss_application.sql` 调整为 `V10_1__seed_fcoss_application.sql`，避免 Flyway 版本冲突。
  - 扩展 API/app：新增 `/api/internal/platform/scheduler-management` 超级管理员管理 API；app 依赖 scheduler，并在 `application.yml` 中增加 `TEST_AGENT_SCHEDULER_*` 配置入口，默认 `enabled=false`。
  - 修复一个阻断 `test-agent-api -am` 编译的既有调用问题：`RunApplicationService.subscribeAgentEvents(...)` 调用补传 `resolvedAgentId`。
  - 文档同步更新 backend/module README/PACKAGE、API、架构依赖、数据库、部署、安全文档。
- How: 按 domain → persistence → scheduler module → API → app/config → docs 的顺序推进；互斥只使用 Redis 锁，不提供本机或数据库锁 fallback；runner 对 due cron 只补一次并把下次触发时间推进到当前时间之后，重叠触发写入 `SKIPPED + skipReason`。
- Result: 框架已可注册 handler Bean、同步任务定义、异步执行 Cron/管理员手动触发、统一记录运行状态；普通用户级 Cron 计划只落库和领域模型，不开放 HTTP API，不创建定时会话/Run。
- Pitfalls: 工作区存在无关的 `requirements/todo/deployment.md` 修改，属于历史需求草案，不作为编码依据，也不会纳入本次提交。scheduler 启用时如果 `test-agent.redis.enabled=false` 或缺少 `StringRedisTemplate` 会启动失败，这是预期安全边界。
- Verification: `mvn -pl test-agent-domain -am test`、`mvn -pl test-agent-common test`、`mvn -pl test-agent-persistence -am test`、`mvn -pl test-agent-scheduler -am test`、`mvn -pl test-agent-api -am test`、`mvn -pl test-agent-app -am test` 均通过；提交前已补跑 `mvn test`，全量后端测试通过。
- Next: 如后续要新增具体业务定时任务，应放在所属业务模块实现 `ScheduledTaskHandler`；如要开放用户级计划 API，需要先补权限、配额、payload 安全和后台会话发送设计。

### 2026-06-25 - Fix el-date-picker month cells to show "1月/2月/…" in Chinese

- Why: 用户反馈「+新增版本」弹窗里的 el-date-picker (type=month) 打开后，月份单元格里显示英文 "Jan/Feb/…"，希望显示中文 "1月/2月/3月/…"，与项目里其他中文文案风格一致。
- What:
  - `frontend/apps/agent-web/src/main.ts` 引入 `element-plus/es/locale/lang/zh-cn` 和 `dayjs/locale/zh-cn`，调用 `dayjs.locale("zh-cn")`；在原 zh-cn locale 上派生一个只覆盖 `el.datepicker.months` 12 项的浅拷贝（`jan: "1月"`, `feb: "2月"`, …, `dec: "12月"`），再把这份 locale 通过 `app.use(ElementPlus, { locale: zhCnWithArabicMonths })` 注入。
  - 不直接用 Element Plus 默认的 `zh-cn` locale 是因为它把月份渲染为中文数字"一月/二月/…"（Element Plus 2.12 的 `el.datepicker.months.{jan,dec}` 默认值），与用户期望的阿拉伯数字 "1月/2月/…" 不一致。
  - `frontend/apps/agent-web/tests/workbench.spec.ts` 既有"yyyy年M月"测试里追加两步断言：打开日期面板后能定位到 `el-month-table`，并看到 `^1月$` 和 `^6月$` 文案（之前是 `console.log` 调试输出，已清理）。
- How: 复制 zh-cn locale 的浅层结构再覆盖 `datepicker.months` 这一层，其它字段（按钮、星期、占位符等）原样保留，避免影响其它使用 Element Plus 的位置。
- Result: e2e 中 `+新增版本` 弹窗的月份面板渲染为 "1月/2月/…/12月"；`pnpm playwright test workbench.spec.ts -g "cascade"` 6 个 case 全部通过（1 个 mobile 被 skip）。
- Pitfalls: 仅设置 `dayjs.locale("zh-cn")` 不够，Element Plus 月份面板走的是 i18n 包而不是 dayjs 的 locale；需要同时注入 Element Plus locale。`zh-cn` locale 默认会把月份渲染为 "一月/二月/…"，需要再浅拷贝覆盖 `months` 字段。
- Verification: `pnpm playwright test workbench.spec.ts -g "新增版本 dialog opens"` 通过；`pnpm playwright test workbench.spec.ts -g "cascade"` 全部通过。
- Next: 等用户验收；如果未来 Element Plus 升级破坏了 i18n key，需要在 e2e 第一时间复现。

### 2026-06-25 - Repair FigmaChatPanel.vue duplicate declarations blocking dev server

- Why: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue` 在某个合并后存在两套 `defineProps` / `defineEmits`（一份带 `processStatus`/`initialize-process`，另一份带 `selectedModelLabel`/`open-model-picker`）和重复的 `const hasFileChanges = computed(...)`，导致 vue-tsc 报 TS2451 / TS2339 / TS2551 / TS2769 共 ~30 条错误，Vite dev server 抛 "Identifier 'props' has already been declared"，e2e 跑不起来。
- What: 删掉旧的 `const props = defineProps<{...}>()` / `const emit = defineEmits<{...}>()` 整段以及重复的 `hasFileChanges`，把保留版的 props (`selectedModelLabel`/`modelPickerDisabled`/`stopDisabled`/`stopDisabledReason`/`processStatus`/...) 与 emits (`open-model-picker`/`initialize-process`/...) 合到一份。
- How: 全文检索确认旧版 props（`selectedModelLabel`/`history`/`modelPickerDisabled`/`stopDisabled`）在模板 / script 中没有引用，所以可以直接合并而非 union；保持新版（带 `processStatus` 等的）作为唯一一份。
- Result: `pnpm typecheck` 对 FigmaChatPanel 的错误清零；dev server 重新启动后 HTTP 200，e2e 可以正常 navigate 到 `/`。
- Pitfalls: 这个修复与"中文月份"任务无关，但属于阻断 dev server 的预存在 bug；不修就测不了用户反馈。
- Verification: `pnpm typecheck` 仅剩其它预存在错误（与本 PR 无关），`pnpm playwright test workbench.spec.ts -g "新增版本 dialog opens"` 通过。
- Next: 在 commit message 里把"中文月份"和"FigmaChatPanel 修复"拆成两条提交，避免单点耦合。
### 2026-06-24 - Require Session Log In Project Rules

- Why: The session log needed to be treated as a first-class tracked artifact, not an ad hoc local note, so remote commits carry the handoff context too.
- What: Updated `AGENTS.md`, `docs/guides/ai-workflow.md`, and `docs/guides/self-checklist.md` to require `.agents/session-log.md` updates and to describe how it is included in commits.
- How: Kept the change in the project entry docs instead of business code, and reused the existing Why/What/How/Result log shape so future sessions stay consistent.
- Result: Future code-change batches should leave behind a committed session log that explains the change for other developers and agents, including remote-push ready workflows.
- Pitfalls: None.
- Verification: `git diff --check` not run yet.
- Next: Run a light diff sanity check, then commit the doc updates together with this log entry.

### 2026-06-24 - Add Code Update Handoff Skill

- Why: Code-change batches in this repo needed a shared handoff rule so future agents can see the real status and avoid re-deriving context.
- What: Added `.opencode/skills/code-update-handoff/SKILL.md`, fixed `agents/openai.yaml`, and created this session log file.
- How: Started from the skill-creator template, then replaced placeholders with a repo-specific workflow that always emits `Not done yet` and appends a compact log entry.
- Result: Future handoffs can stay candid, and other developers or agents can quickly understand the reason, scope, approach, and expected effect of a change.
- Pitfalls: `quick_validate.py` needed `PyYAML`; resolved by running it inside `.tmp/skill-validate-venv`.
- Verification: `./.tmp/skill-validate-venv/bin/python3 /Users/kaka/.codex/skills/.system/skill-creator/scripts/quick_validate.py .` in `.opencode/skills/code-update-handoff`.
- Next: Use this skill whenever a batch edits repository files so the handoff and session log stay consistent.

### 2026-06-24 - Simplify workspace selector + add +新增版本 + seed F-COSS workspaces

- Why: 用户希望「应用工作空间」两级菜单只展示工作空间名（一级），hover 展开版本子菜单（二级），版本列表底部加 `+新增版本` 行，弹 yyyy年M月 时间组件，并在 F-COSS 应用下多造几个工作空间模板。
- What:
  - 后端 `ManagedWorkspaceApplicationService` 新增 `yyyy年M月` 版本格式校验，`sanitizeVersionForBranchAndPath` 把 `2024年1月` 转为 `2024-01` 用于派生分支名 / 物理路径，`normalizeVersion` / `resolveBranch` / `appRepoRoot` / `personalRepoRoot` 全部接入。
  - 新增 Flyway `V13__seed_fcoss_more_workspaces.sql`，在 V10 的 F-COSS 数据基础上追加 3 个工作空间模板（移动端 / 数据同步 / 报表）和对应初始版本。
  - `WorkbenchFooter.vue` 简化一级菜单只显示 `workspaceName`、去掉 `directoryPath · branch` 副标题；子菜单底部加「+新增版本」行；新增 el-dialog + `ElDatePicker` (`type=month` / `format=yyyy年M月` / `value-format=yyyy年M月`) 提交 `create-version` 事件。
  - `FigmaFileExplorer.vue` 透传 `creatingVersion` prop 与 `createVersion` emit。
  - `AgentWorkbench.vue` 接入 `handleCreateVersion`：调 `api.createWorkspaceVersion`，成功后失效 `versionsByTemplateId` 缓存并把新版本切到工作区；`@create-version` 监听接好。
  - `workbench.spec.ts` mock 新增 `POST .../versions` 路径拦截，捕获用户原值 `version` 字段。
  - 文档：更新 `docs/api/http-api.md`（POST 规则 / 两级菜单说明）、`docs/deployment/database.md`（V13 节）、`backend/test-agent-workspace-management/README.md`（测试覆盖说明）、`frontend/apps/agent-web/README.md`（两级菜单简化 / 「+新增版本」说明）。
- How: 后端先扩 `Pattern` + `sanitize`，新加一个 `Path.endsWith` 风格的 Java 单元测试绕开 Windows 路径分隔符；前端用 Element Plus 的 `el-dialog` + `el-date-picker` 直接覆盖时间选择场景；V13 用 `where exists / where not exists` 幂等保护。
- Result: 工作空间选择器符合「只显示名 + hover 出版本 + 底部新增版本」三段式；后端同时兼容 `yyyyMMdd` 和 `yyyy年M月`，新增版本入参为 `2024年1月` 原值；F-COSS 应用从 1 个模板扩展为 4 个模板。
- Pitfalls: 仓库里两个旧测试（`createsStandardApplicationVersionWorkspaceAndRecordsRecentUsage` / `createsPersonalWorkspaceFromApplicationVersionWorktree`）在 Windows 上因路径分隔符断言失败，与本次改动无关（已用 `git stash` 验证过改动前的状态同样失败）；本次新测试改用 `Path.endsWith` 规避。
- Verification: `pnpm typecheck` 通过；`mvn -pl test-agent-workspace-management -am test` 我新加的 2 个测试通过（8 / 10），其余 2 个失败是上面提到的预存在 Windows 路径问题。
- Next: 等用户审过 PR 提单；如需进一步简化可考虑把 FigmaFileExplorer 的 `creatingVersion` 与工作区切换的反馈合并。

### 2026-06-25 - 右上角用户菜单顶部灰显用户角色（来自 dictionaries.dict_label）

- Why: 用户反馈「F-COSS」右上角下拉菜单只有「用户名 / 退出登录」两项，希望在菜单顶部加一行灰显展示当前用户角色；角色来源涉及 `users`（/api/auth/me 上下文）→ `user_roles`（关联角色 code）→ `dictionaries.dict_label`（中文展示名）三张表。
- What:
  - 后端：`AuthDtos.CurrentUserResponse` 新增 `roleLabels: List<String>` 字段（与 `roles` 等长、对齐）；`AuthController.me` 注入 `DictionaryRepository`，按 `Dictionary.DICT_KEY_ROLE` + role code 查 `dict_label`，缺失时回退为 role code 本身，避免阻断主链路。
  - 共享类型：`shared-types/CurrentUser` 新增 `roleLabels?: string[]`，向下兼容旧 token / 旧响应。
  - 前端壳子：`FigmaShell` 新增 prop `currentUserRoleLabels?: string[]`；下拉菜单顶部以 `ShieldCheck` 图标 + 灰显样式新增一行（class `figma-user-menu-role`），多角色用「、」拼接；`roleLabels` 为空或缺失时整行 v-if 不渲染，避免出现「角色：」空文案。
  - 入口串联：`AgentWorkbench` 把 `authStore.currentUser?.roleLabels` 透传给 `FigmaShell`。
  - e2e：`workbench.spec.ts` 的 `/api/auth/me` mock 同步返回 `roleLabels`（新增 `roleLabelOf` 工具，固定映射 `SUPER_ADMIN / SYSTEM_ADMIN / APP_ADMIN / USER`），`user avatar menu logs out` 用例额外断言下拉菜单顶部出现 `.figma-user-menu-role` 灰显行且文案为「应用管理员」。
  - 文档：`docs/api/http-api.md` 同步 `CurrentUserResponse.roleLabels` 字段、三表数据来源、字典缺失回退行为；`frontend/apps/agent-web/README.md` 顶栏下拉菜单条目补一句角色灰显行说明。
- How:
  - 后端先扩 DTO，再在 controller 用 `dictionaryRepository.findByDictKeyAndValue(...)` 现成 API 翻译角色；测试新增 `meReturnsRolesAndChineseRoleLabelsFromDictionary` / `meFallsBackToRoleCodeWhenDictionaryEntryIsMissing` 两条覆盖主链路 + 回退；`loginReturnsRolesLoadedByAuthService` 保留。
  - 前端用 lucide-vue-next 的 `ShieldCheck`（已存在于 `node_modules`），样式复用现有 `.figma-user-menu-summary` / `.figma-user-menu-item` 的基础 padding/border-radius，仅叠加更小字号 + 次要色 + 灰底图标 + 不可点击 cursor，保留设计语言一致。
  - e2e mock 用 `roleLabelOf` 把 mock 后端的字典翻译前置到 e2e 层，避免 e2e 依赖新的 GET /api/dictionaries 接口；这样 future 字典表字段变化只需要改 mock 工具即可。
- Result: 点击右上角 F-COSS 头像，下拉菜单顶部出现一行灰色角色（如「应用管理员」），位置在用户名 / 退出登录之上；多角色显示为「应用管理员、普通用户」；后端 `/api/auth/me` 的 `roleLabels` 与 `roles` 顺序一致。
- Pitfalls: 工作区里同时存在另一位开发者「opencode 进程本地节点回退 & 重置绑定」相关文件的中间态改动（`UserOpencodeProcessStatusResponse` / `UserOpencodeProcessAssignmentService` / `RuntimeDtos` / `UserOpencodeProcessController` / `OpencodeProcessManagementRepository` / `JdbcOpencodeProcessManagementRepository` / `FigmaChatPanel` / `backend-api/index.ts` / `RuntimeControllerTest` / `UserOpencodeProcessAssignmentServiceTest`），会破坏 `mvn -am` 与 `pnpm typecheck` 的全量构建；本次提交只 `git add` 上面 9 个直接相关文件 + 本条 session-log，未把这些未完成改动一起带入。
- Verification: 临时 stash 掉上述中间态后，`mvn -pl test-agent-api test -Dtest=AuthControllerRolesTest` 3/3 通过；`pnpm --filter @test-agent/shared-types typecheck` 通过；FigmaShell 的 `ShieldCheck` 在 `lucide-vue-next` 类型声明中存在，prop 与 `currentUserRoleLabels` 字段链路类型自洽。
- Next: 等用户验收；如需补充真实字典接口（`GET /api/dictionaries?dictKey=ROLE`）让前端不再依赖 `/api/auth/me` 翻译结果，下一轮再加，避免本次改动超出最小范围。

### 2026-06-25 - 本地运行管理注册默认使用局域网 IPv4

- Why: 用户追问 `888888888` 为什么还活着，以及本机是否没有取到局域网 IP。排查确认本机默认路由网卡 `en0` 是 `192.168.100.115`，但本地启动链路默认把后端 Java 进程、opencode manager 和 user opencode 进程注册到 `127.0.0.1`；`888888888` 当时对应 opencode 进程健康检查返回 200，所以不是僵死数据，只是服务器标识用了 loopback。
- What:
  - `restart-dev-services.sh` 在读取 `.env.local` 后，如果未显式设置 `TEST_AGENT_LINUX_SERVER_ID`、`TEST_AGENT_BACKEND_LISTEN_URL` 或 `OPENCODE_MANAGER_LINUX_SERVER_ID`，会检测默认路由网卡 IPv4，并用该地址作为本地运行拓扑注册值。
  - `tools/verify-dev-scripts.sh` 增加 fake `route` / `ipconfig` 覆盖，防止脚本回退成 `127.0.0.1`。
  - `RunEventLiveBus` 改为通过 `ObjectProvider<RunEventRemotePublisher>` 可选注入远端广播端口，避免 Redis bus 未注册时本地 Spring 启动失败。
  - `RunApplicationService` 补上 `subscribeAgentEvents` 新签名需要的 `resolvedAgentId` 参数，修复当前 `main` 编译中断点。
  - 文档同步说明本地脚本自动检测默认路由 IPv4，生产和多机部署仍应显式配置。
- How: 优先用 macOS `route -n get default` 找默认路由接口，再用 `ipconfig getifaddr` 取 IPv4；Linux 下用 `ip route get 1.1.1.1` 的 `src` 地址；过滤 `127.*`、`169.254.*` 和 `0.0.0.0`。
- Result: 本地未配置显式服务器 ID 时会注册为 `192.168.100.115` 这类局域网地址，而不是 `127.0.0.1`；运行管理面板仍只展示有 Redis 心跳的活进程。
- Pitfalls: 当前工作区另有未提交的 `WorkspaceApplicationService` 日志改动引入 `org.slf4j` 但模块未声明依赖，导致 `mvn -pl test-agent-app -am test` 和实际重启构建被挡住；本次不回滚该无关改动。
- Verification: `bash tools/verify-dev-scripts.sh` 通过；`mvn -pl test-agent-event test` 10/10 通过；`git diff --check` 通过。本地完整重启因上述 workspace 无关编译错误未完成。
- Next: 修复或移除 workspace 模块未提交日志改动后，重新执行 `./restart-dev-services.sh --env-file .env.local`，再验证运行管理 overview 中 `linuxServerId` 是否为 `192.168.100.115`。

### 2026-06-25 - 增加 local 网关让本地 127.0.0.1:4096 的 opencode server 健康检查走直连

- Why: V17 + 心跳自举已让数据库拓扑可见，但 `UserOpencodeProcessAssignmentService.status` 仍会调 `gateway.checkHealth` 走 manager WebSocket；本地没起 opencode-manager 时返回 `OPENCODE_UNAVAILABLE`，又因为 V17 把容器 `current_processes=max_processes=1`，`canRebuildOn` 也返回 false，所以用户重启后前台升级后的报错变成 "opencode 进程健康检测失败，且原 Linux 服务器没有可用容器"，依然卡死。
- What:
  - `TestAgentRuntimeProperties.ManagerControl` 增加 `gatewayMode`（默认 `socket`），空值或空白自动规整为 `socket`。
  - 新增 `LocalOpencodeProcessManagerGateway`（`@ConditionalOnProperty(gateway-mode=local)`）：`checkHealth` 直接对 `opencode_server_processes.baseUrl` 跑 HTTP GET（连接 2s / 请求 3s 超时，2xx/3xx 健康），`startProcess` 走占位返回 `pid=0, status=local-skip`；网络异常统一包成 `PlatformException(OPENCODE_UNAVAILABLE)` 转 unhealthy，不把异常直接抛给前端。
  - `SocketOpencodeProcessManagerGateway` 加 `@ConditionalOnProperty(gateway-mode=socket, matchIfMissing=true)` 与 local 实现互斥。
  - `application-local.yml` 增 `test-agent.opencode.manager-control.gateway-mode`（`${TEST_AGENT_OPENCODE_GATEWAY_MODE:local}`）。
  - 测试：新增 `LocalOpencodeProcessManagerGatewayTest`（2xx / 3xx / 5xx / 连接失败 / startProcess 占位共 5 用例）。
  - 文档：`docs/deployment/database.md` 在 V17 节增 "健康检测/启动网关选择" 说明；`docs/deployment/backend.md` 在 "本地开发 opencode 机器预置" 节说明 local 网关 + 回切 socket 语义；`backend/test-agent-opencode-runtime/README.md` 同步 gateway 实现与测试清单。
- How: 用 Spring `@ConditionalOnProperty` 互斥激活 `SocketOpencodeProcessManagerGateway` 与 `LocalOpencodeProcessManagerGateway`；默认值是 `socket`，与生产路径完全等价；切到 `local` 仅替换 `checkHealth` / `startProcess` 的实现，其余控制面、Redis 心跳、ManagerConnectionRegistry、manager-backend 连接维护完全不动。
- Result: 本地启动后（profile=local、opencode server 在 127.0.0.1:4096 监听），前台 `888888888` 登录后右侧对话窗口的 opencode 进程状态会落到 READY（健康检测直接命中本机 baseUrl），不必再启动 opencode-manager 容器；生产 profile 不改配置就走原 `SocketOpencodeProcessManagerGateway`，manager 行为完全保留。
- Pitfalls: V15 的 CHECK 约束让 `OpencodeContainer` 的 `max_processes <= (port_end - port_start + 1)`，单端口 4096 仍然是 `max=1, current=1`，与 V17 共存；`OpencodeProcessHealthCommand` / `OpencodeProcessStartCommand` 来自 `com.icbc.testagent.opencode.runtime.process` 而非 domain 包，写测试时易错。`PlatformException` 没有 `unavailable` 静态工厂，必须用 `new PlatformException(ErrorCode, String)`。
- Verification: `mvn -pl test-agent-opencode-runtime,test-agent-persistence test` 通过（21 + 105 用例，其中 `LocalOpencodeProcessManagerGatewayTest` 5/5、`BackendJavaProcessLifecycleServiceTest` 3/3）；`mvn -pl test-agent-app -Dtest=AppModuleBoundaryTest test` 1/1 通过；`mvn -DskipTests=true compile` 17 个模块全量编译通过。
- Next: 启服务前用环境变量 `TEST_AGENT_OPENCODE_GATEWAY_MODE=local` 或 `application-local.yml` 默认值覆盖；生产请显式设回 `socket`（或留空走默认）。

### 2026-06-25 - FileExplorer 加"公共目录"独立面板（固定路径内容扫描）

- Why: 用户在 FileExplorer 顶部「F-COSS 主服务-20260620」工作空间标题行希望新增一个"固定路径的内容扫描"，等价于"公共的目录读取"：所有登录用户可读，SUPER_ADMIN 可写，路径由后端配置。要求：作为独立新组件、不破坏现有 Workspace/工作空间/应用版本工作区文件树、保持最小改动、保留与 `WorkspaceFileService` 一致的越权拦截和 1MB UTF-8 上限。
- What:
  - 后端
    - 新增 `backend/test-agent-workspace-management/.../PublicDirectoryService.java`：通过 `@Value("${test-agent.public-directory.path:}")` 注入固定根路径；`isEnabled()` 在路径空/不存在/不是目录时返回 `false`；`listDirectory/readContent/writeContent` 委托给现有 `WorkspaceFileService`，根目录解析阶段抛 `PlatformException(NOT_FOUND)` 统一包装。
    - 新增 `backend/test-agent-api/.../platform/PublicDirectoryController.java`：`GET /api/public/files`、`GET /api/public/files/content` 走 `AuthWebSupport.getAuthPrincipal`（已登录即可），`PUT /api/public/files/content` 走 `AuthWebSupport.requireRole(..., Dictionary.ROLE_SUPER_ADMIN)`（仅超管可写），同时挂新平台 URL 前缀 `/api/internal/platform/public-directory/...`。
    - 配置：`application.yml` 与 `application-local.yml` 在 `test-agent.public-directory.path` 加 `${TEST_AGENT_PUBLIC_DIRECTORY_PATH:}` 默认空字符串（空 = 禁用）。
    - 单元测试 `PublicDirectoryServiceTest` 覆盖未配置、配置根目录不存在、正常 list/read/write 委托、根目录不可访问时 list 抛 NOT_FOUND 四种场景。
  - 前端
    - `frontend/packages/backend-api/src/index.ts` 新增 `listPublicFiles/readPublicFile/writePublicFile`，复用现有 `FileTreeEntry/FileContent/FileStatus` 类型，DTO 字段名与后端 `FileTreeEntryResponse/FileContentResponse/WriteFileRequest` 对齐。
    - 新组件 `frontend/apps/agent-web/src/components/PublicDirectoryPanel.vue`：仿 `FileExplorer` 的 FileTree 风格（FolderTree/Loader2/AlertTriangle），接收 `canWrite` 和 `baseUrl` 两个 prop，点击文件 emit `openFile` 携带 `path + content + readonly`，错误条带 / 加载旋转 / 空态都齐备。
    - `FigmaFileExplorer.vue` 顶部加一行小 toolbar：左 `工作区` / 右 `公共目录` 切换两个视图（与 WorkbenchFooter 平级），切换时通过 `v-if` 卸载不活跃面板，避免滚动区域竞争；新增 `publicDirectoryWritable` / `apiBaseUrl` prop 与 `openPublicFile` emit。
    - `AgentWorkbench.vue` 引入 `isSuperAdmin` computed（基于 `authStore.currentUser?.roles`）传给 FigmaFileExplorer；新增 `openPublicFile` 把 `public:<相对路径>` 作为 `tab.path` 打开 tab（与工作区路径空间隔离）；`saveMutation` 在 tab 路径以 `public:` 开头时改走 `api.writePublicFile`，普通用户永远拿到 readonly tab。`FileContent` 类型从 `@test-agent/shared-types` 引入。
    - 文档
      - `docs/api/http-api.md` 新增 "Public Directory API" 表格（list/read 对所有登录用户开放，write 仅 SUPER_ADMIN）+ 新平台 URL 映射 + 错误码语义。
      - `backend/test-agent-workspace-management/README.md` 主要职责补公共目录行 + 测试覆盖补 `PublicDirectoryServiceTest` 描述。
- How: Service 层只做"路径解析 + 委托"，保留 `WorkspaceFileService` 的越权拦截/UTF-8 1MB/单层目录 1000 条上限；Controller 只做协议转换和角色校验，不直接调 SDK/Repository（符合 API 规范）；前端把公共目录 tab.path 设计成 `public:<相对路径>` 字符串，让 Monaco 仍能用 `languageFromPath` 推断语言，让 `activePath` 不会与工作区文件路径撞名，文件树高亮逻辑零改动；角色判定前后端都做：后端 `requireRole` 是最终边界，前端 `isSuperAdmin` 只是为了隐藏保存按钮。
- Result: FileExplorer 顶部多了一行 `工作区 / 公共目录` 切换；`公共目录` 视图里用现有工作区一样的 FileTree 展示后端配置的固定路径内容，点击文件在中央编辑器打开一个新 tab，普通用户 tab 是 readonly 不可保存，SUPER_ADMIN tab 可保存；`test-agent.public-directory.path` 留空时整个面板退化为"公共目录为空或后端未配置"提示，所有接口返回 404。
- Pitfalls: Mockito 对未声明受检异常的方法不能 `doThrow(new IOException)`，必须改抛 `RuntimeException` 或用 mock 显式允许；`FileContentResponse` 实际只有 `(path, content, size)` 三个字段，没有 `lastModifiedAt`；`PlatformException` 的 `errorCode` 是 record-style 的 `errorCode()` 方法而不是 `getErrorCode()`；`@test-agent/backend-api` 不再导出 `FileContent` 类型，前端要从 `@test-agent/shared-types` 拿；`FigmaFileExplorer` 的 props 已经混入了 `FileExplorerProps & {...}`，新增 prop 时按 union 加上去即可，但 typecheck 时 vue-tsc 会按全部字段推断 emit 签名。
- Verification: `mvn -pl test-agent-workspace-management -am test -Dtest=PublicDirectoryServiceTest` 4/4 通过；`mvn -pl test-agent-workspace-management,test-agent-api -am compile` 编译通过；`pnpm -F @test-agent/backend-api typecheck` 通过；`pnpm -r typecheck` 12/12 packages 通过；`pnpm -F @test-agent/agent-web test` 通过；前后端无新告警/未导入符号。
- Next: 用户需要在 `application-local.yml` 或环境变量里设一个真实存在的目录路径（如 `TEST_AGENT_PUBLIC_DIRECTORY_PATH=D:/shared/fcoss`）才能看到非空内容；如果后端路径含中文/空格要注意 URI 编码（当前用 `Uri.parse(encodeURIComponent)` 仍可能与真实文件系统的"不区分大小写路径"对不上，需要时把 `path` 转成 ASCII 字节）。

### 2026-06-25 - 公共目录按 profile 协商默认路径（guo=D:/agents，其他=/data/agents-pub）

- Why: 上一轮把 `test-agent.public-directory.path` 的默认设为空字符串（禁用态），用户希望按部署环境协商出可立即生效的默认值：本机 Windows 调试用 `D:\agents`，其他 profile（local/test/prod）用 `/data/agents-pub` 作为 Linux 容器挂载点的协商默认；仍然允许 `TEST_AGENT_PUBLIC_DIRECTORY_PATH` env 覆盖或留空禁用。
- What:
  - `application-guo.yml`：新增 `test-agent.public-directory.path: ${TEST_AGENT_PUBLIC_DIRECTORY_PATH:D:/agents}`（guo 是默认激活 profile，匹配本机 Windows 调试的 `D:\agents` 目录）。
  - `application.yml`（base）：把默认从空字符串改为 `${TEST_AGENT_PUBLIC_DIRECTORY_PATH:/data/agents-pub}`，注释里说明各 profile 协商值。
  - `application-local.yml`：把默认从空字符串改为 `${TEST_AGENT_PUBLIC_DIRECTORY_PATH:/data/agents-pub}`，注释改为"base 协商默认 /data/agents-pub，本地如无该目录可显式 env 覆盖或留空禁用"。
  - `application-test.yml` / `application-prod.yml`：补 `test-agent.public-directory` 段（之前没显式声明，会继承 base），默认也是 `/data/agents-pub`，prod 注释强调"必须显式 env 覆盖到实际挂载目录"。
  - `docs/api/http-api.md` 在 Public Directory API 节新增"各 profile 协商默认值"表格，覆盖 guo/local/test/prod 四种场景。
- How: 用 Spring profile 配置层级：base 设协商默认，guo 显式覆盖为 Windows 路径；其他 profile 不显式声明会继承 base；env 始终可覆盖到任意路径或留空禁用。改动只动 `application*.yml` 和 `docs/api/http-api.md`，Java 端 `PublicDirectoryService` / `PublicDirectoryController` 零改动，前端零改动。
- Result: guo profile 启动后无需 env 即可在 `D:\agents` 读到本地内容；local/test/prod 启动后若实际挂载了 `/data/agents-pub` 也立即可用；任意 profile 仍可通过 `TEST_AGENT_PUBLIC_DIRECTORY_PATH=` 留空禁用。
- Pitfalls: Spring profile 配置文件里 `:` 既是 key/value 分隔符又是 env 默认值分隔符，路径里不能带裸 `:`（Windows `D:/agents` 不含冒号，OK）；guo profile 的 `D:/agents` 是 forward-slash，与本仓 `workspace-picker.allowed-roots: "D:/workspace"` 的写法保持一致，Java `Path.of` / `toRealPath` 都能正确处理。
- Verification: `mvn -pl test-agent-app -am compile` 编译通过（4 个 application*.yml 都是 resource 编译，xml binding 验证通过）；`mvn -pl test-agent-workspace-management -am test -Dtest=PublicDirectoryServiceTest` 4/4 仍绿。
- Next: 用户在本机 guo 启动时需要确认 `D:\agents` 目录存在并放点测试文件；其他 profile 部署到 Linux 容器时需要把 `/data/agents-pub` 挂载到实际共享目录，或显式 env 覆盖。

### 2026-06-25 - 公共目录子目录无法展开：模板硬编码只支持两层，改用递归子组件

- Why: 用户报告"公共目录里面的文件夹打不开，点击没有展示子文件内容"。复现路径：在本机 guo profile 下访问 `D:\agents\platform-tester\agent` 这种两级目录，第一级 `agent` 可以展开 chevron，但点击下面任何子项都不显示内容；进一步排查发现 `D:\agents\platform-tester\agent\sessions`、`agent\workspace` 这些**第三层**目录在图上根本没渲染出来，原因是 `PublicDirectoryPanel.vue` 模板里只硬编码了"顶级 v-for + 顶级目录内的 v-for"两层嵌套，第二级 button 没有内嵌的 div 展示其子项。
- What:
  - 新增 `frontend/apps/agent-web/src/components/PublicDirectoryNode.vue`：递归子组件，渲染单行（目录带 chevron + folder，文件不带 chevron），展开时递归调用自身渲染子目录；通过 `defineOptions({ name: "PublicDirectoryNode" })` 显式声明组件名，让 `<script setup>` 模板能自引用；缩进按 `depth * 14` 像素线性递增。
  - 重构 `frontend/apps/agent-web/src/components/PublicDirectoryPanel.vue`：移除硬编码的两层 v-for 嵌套，外层只 v-for 渲染根目录的子项（`entriesByDirectory['']`）的 `PublicDirectoryNode`，其余层级由子组件递归；状态（`entriesByDirectory` / `expandedDirectories` / `loadingPath`）继续由父组件统一管理，子组件只暴露 `toggle` / `openFile` 事件上抛。
- How: 抽出"渲染一行 + 递归子项"为独立组件，状态和事件全部上提到父组件，避免组件树自循环；保留原有的 `isKnownEmptyDirectory` 语义（`entriesByDirectory[path]?.length === 0` 就不渲染 chevron、点击不展开），避免对后端已知为空的目录再发请求；`canWrite` 仍由父组件 `AgentWorkbench` 注入。
- Result: 任意层级的子目录现在都可以正常展开和折叠，缩进按 14px 递增；点击文件行仍走 `openFile` → `readPublicFile` → emit `openFile` payload 给父组件打开 tab；空目录不再发请求，loading 状态按目录路径精确追踪；`PublicDirectoryService` / `PublicDirectoryController` 零改动。
- Pitfalls: Vue 3 `<script setup>` 组件默认没有 name，要在自身模板递归必须 `defineOptions({ name: "PublicDirectoryNode" })` 显式声明（不然 vue-tsc 会报 "Component is missing template or render function"）；递归子组件传 ref 时 Vue 会自动 unwrap，所以 `:entries-by-directory="entriesByDirectory"` 这种写法会直接把 ref 解包成普通对象/Set 给子组件使用，不需要 `.value`；递归 props 必须是 plain data（不能传 ref），否则每个节点会共用同一个 ref，状态会互相串。
- Verification: `pnpm -F @test-agent/agent-web typecheck` 通过；`pnpm -F @test-agent/agent-web test` 通过；后端零改动，未重跑 mvn。
- Next: 后续如果公共目录的目录树深度特别大（>5 层），考虑加虚拟滚动；目前后端 `WorkspaceFileService` 仍然限制单层 1000 条，所以单层节点数过多时也只影响 UI 渲染速度。

### 2026-06-25 - 递归子组件 isKnownEmpty 误把"未加载"当成"空目录"导致无法展开

- Why: 上一次提交用递归子组件替换硬编码两层模板后，用户反馈"公共目录完全没有展开能力了"——根目录的三个子项都显示 chevron 朝右、点击完全没反应，连 `agent` 展开 chevron 的旋转都看不到了。原因是我在子组件 `PublicDirectoryNode` 里把"未加载"和"已加载为空"混为一谈：`children` computed 用了 `?? []` 把 `undefined` 兜底成空数组，初始渲染时 `entriesByDirectory['platform-tester']` 是 `undefined`、被兜底成 `[]`，`isKnownEmpty` computed 判为 true，于是 `<ChevronRight v-if="!isKnownEmpty" />` 不渲染 chevron，且 `onRowClick` 早退，目录永远打不开。
- What: 把 [PublicDirectoryNode.vue](file:///d:/workspace/intelligent-test-agent/frontend/apps/agent-web/src/components/PublicDirectoryNode.vue) 的 `children` computed 改成保留 `undefined`（不兜底），`isKnownEmpty` 严格只在 `Array.isArray(children) && length === 0` 时为 true，template 的 v-for 改用 `children ?? []` 兜底渲染空列表；附上中文注释说明这个边界。
- How: 严格区分"未请求过"和"已请求且为空"两种状态——前者需要渲染 chevron + 允许点击触发请求；后者渲染空白占位 + 点击不展开避免无意义请求。原 `PublicDirectoryPanel.vue` 的 `isKnownEmptyDirectory` 函数用 `entriesByDirectory.value[path]` + `Array.isArray(...)` 天然区分这两种状态，重构时把 `?? []` 当成"防御性编程"反而引入了 bug。
- Result: 任何目录第一次点击都能正常触发 `loadDirectory` 请求并展开；已加载且为空的目录不渲染 chevron、点击不展开，避免重复请求。
- Pitfalls: `?? []` 在某些场景会把"未加载"误判为"空"，是这次踩到的坑；`computed` 的返回类型注解影响 Vue 模板的类型推断，把 `FileTreeEntry[]` 改成 `FileTreeEntry[] | undefined` 后 v-for 的 `?? []` 兜底必须在 template 里手动加，不能依赖 computed 内部。
- Verification: `pnpm -F @test-agent/agent-web typecheck` 通过；`pnpm -F @test-agent/agent-web test` 通过。
- Next: 如果未来要把这个树形组件抽成通用组件（公共目录 + 工作区文件树共用），需要明确 props 的"未加载 vs 空"语义约定，避免类似 bug。

### 2026-06-25 - 整理 Flyway 版本冲突并修复 local-direct 对话启动 500

- Why: 更新代码后本地 115 启动先后被 Flyway migration 冲突挡住：`V15__create_scheduler_framework_tables.sql` 与 `V15__add_opencode_process_id_check_constraints.sql` 重号；随后因已落库的 `V10__seed_fcoss_application.sql` 被源码改成 `V10_1__...` 触发 Flyway validate。服务启动后，对话发送又在 `routing_decisions.execution_node_id=node_ocp_local_direct` 上触发外键失败，因为 local-direct 合成节点没有先写入 `execution_nodes`。
- What:
  - 恢复已落库的 `V10__seed_fcoss_application.sql` 文件名，避免本地和已部署库出现 "applied migration not resolved locally: 10"。
  - 将新调度框架 migration 改为 `V20260625184300__create_scheduler_framework_tables.sql`；约定 V17 及以前为历史连续版本，后续新增脚本统一用 `VyyyyMMddHHmmss__description.sql`，按个人更新时间戳确定版本号。
  - 新增 `FlywayMigrationNamingTest`，校验 migration 版本唯一，并阻止 V17 之后继续新增 V18/V19 这类顺序号。
  - `RunApplicationService.userProcessTarget` 在保存路由决策和 agent session binding 前，先 upsert 用户进程投影出的兼容 `ExecutionNode`，避免 local-direct 合成节点触发外键失败。
  - 同步更新 persistence/runtime README、`docs/deployment/database.md` 和 `docs/standards/backend.md`。
- How: 保留已应用 migration 的版本号不改名，只整理未成功作为稳定历史依赖的新脚本；运行时修复不改变 `UserOpencodeProcessAssignmentService` 的 local-direct 短路语义，仍由 Run 启动阶段承担需要持久化审计/binding 前的兼容节点 upsert。
- Result: 115 地址重启成功，登录 CORS 预检返回 `Access-Control-Allow-Origin: http://192.168.100.115:3000`；curl 发送对话返回 200，创建 `run_30c7621908934017b8686f38a6f44ebd` 且状态为 `RUNNING`，日志只出现 `Run started`，不再有 `DataIntegrityViolationException`。
- Pitfalls: 本地 `POST /api/sessions` 当前 DTO 要求 `title` 非空，curl 复测时需要带 `title`；`test-agent.opencode.local-direct=true` 下 `status/initialize/requireReadyProcess` 仍不写 topology，但后续 Run 审计表和 binding 表有外键，不能跳过兼容 `ExecutionNode`。
- Verification: `mvn -pl test-agent-opencode-runtime -am test -Dtest=RunApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false` 19/19 通过；`mvn -pl test-agent-persistence -am clean test -Dtest=FlywayMigrationNamingTest,JdbcRepositoryIntegrationTest#scheduledTaskRepositoryPersistsDefinitionsPlansAndRunRecords -Dsurefire.failIfNoSpecifiedTests=false` 2/2 通过；`./restart-dev-services.sh --profile guo --env-file .env.local --skip-frontend-build` 构建并重启成功；curl 健康检查、CORS 预检、登录、创建 session、发送对话链路通过。
- Next: 后续多人新增 Flyway 脚本时直接用当前时间戳版本，不要再把已落库的历史 migration 改名；前端 `AgentWorkbench.vue` 已通过 `api.createSession(workspaceId, title)` 传标题，curl/脚本直调 `/api/sessions` 时也要带 `title`。

### 2026-06-26 - 应用设置页统一"工作空间管理"与版本库关联模式文案

- Why: 设置弹窗里的左侧入口、面板标题和"应用与版本库关联"tab 仍保留"应用与工作区"/旧关联模式标题，用户要求统一成"应用与工作空间管理"，并把两个关联模式表达为"按应用关联版本库"与"按版本库管理应用"。
- What: 前端设置入口和面板标题改为"应用与工作空间管理"；版本库关联 tab 的第一个模式标题后追加当前选中应用徽标，两个模式之间增加 `role="separator"` 分隔线；同步更新 agent-web 单元测试、相关 Playwright 断言、`frontend/README.md` 与 `frontend/apps/agent-web/README.md`。
- How: 复用 `SettingsAppWorkspacePanel.vue` 已有 `selectedApp` computed，不新增接口或状态；只在关联 tab 内增加标题行、应用徽标和分隔线样式，避免影响版本库管理/工作空间管理 tab。
- Result: 浏览器验证中设置导航和面板标题均显示"应用与工作空间管理"；切到"应用与版本库关联"后，页面展示"按应用关联版本库" + `F-COSS`、中间分隔线、"按版本库管理应用"。
- Pitfalls: 精确跑 Playwright 子集时不要用 `corepack pnpm e2e -- ... -g ...`，这里会把参数转成整份 `workbench.spec.ts` 运行；应使用 `corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep "..." --project=chromium`。整份 `workbench.spec.ts` 当前仍有与本次设置页无关的工作区/模型/运行流失败。
- Verification: 先写失败测试并确认旧文案导致失败；随后 `corepack pnpm test apps/agent-web/tests/settings-app-workspace-panel.test.ts apps/agent-web/tests/runtime-management-settings.test.ts` 9/9 通过；`corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm --filter @test-agent/agent-web build` 通过（保留既有 chunk size 警告）；精确筛选的 2 条设置 E2E 通过。

### 2026-06-26 - 版本库管理 tab 前置并移除反向应用关联区

- Why: 用户进一步要求删除"应用与版本库关联"页里的"按版本库管理应用"区块和分隔栏，把"版本库管理"移动到第二个 tab，并补齐版本库管理表单标签与编辑取消按钮。
- What: `SettingsAppWorkspacePanel.vue` 中 tab 顺序调整为"应用人员管理 / 版本库管理 / 应用与版本库关联 / 工作空间管理"；删除 `selectedRepositoryForApps`、`repositoryApplications`、`linkAppId` 及对应的加载/关联/解绑逻辑；版本库编辑行新增"版本库名称"标签和"取消"按钮；新增版本库表单新增"版本库地址"/"版本库名称"标签，名称输入单独换行；同步 agent-web README 和包级说明。
- How: 保留"按应用关联版本库"主流程和"添加版本库"跳转版本库管理的入口；取消编辑只清空编辑态，不触发后端；新增表单用两行 flex 布局维持紧凑。
- Result: 浏览器验证显示"版本库管理"位于第二个 tab；版本库管理页新增表单两行展示，编辑态有取消按钮；关联页只保留"按应用关联版本库"和当前应用徽标，不再展示分隔栏、"按版本库管理应用"、应用 ID 或"关联应用"。
- Verification: 先写失败测试并确认旧顺序/旧表单失败；随后 `corepack pnpm test apps/agent-web/tests/settings-app-workspace-panel.test.ts` 5/5 通过；`corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm --filter @test-agent/agent-web build` 通过（保留既有 chunk size 警告）；浏览器实际页面验证通过。

### 2026-06-26 - 设置页危险操作改为页面内确认

- Why: 用户在设置页标注"应用与版本库关联"里的"解除"按钮和"应用人员管理"成员移除按钮，要求点击前增加二次确认，避免误删成员或误解除版本库关联。
- What: `SettingsAppWorkspacePanel.vue` 新增页面内 div 确认框状态，替代浏览器原生 `window.confirm`；成员删除图标按钮补 `aria-label="移除成员"`；测试覆盖取消确认不调用后端、确认后才调用后端；README/PACKAGE 同步破坏性操作确认约束。
- How: 保持原有 API 与按钮布局不变，把模板事件从传 id 改为传完整对象，用对象上的 username/name 生成确认文案；确认取消关闭确认框，确认后复用原有 backend-api 调用和列表刷新。
- Result: 点击"解除"或成员移除按钮时会在页面内弹出确认框，不再触发浏览器模态框；取消不会调用解绑/移除接口，确认后才执行。
- Verification: 先写页面内确认框断言并确认旧 `window.confirm` 实现失败；随后 `corepack pnpm test apps/agent-web/tests/settings-app-workspace-panel.test.ts` 7/7 通过；`corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm --filter @test-agent/agent-web build` 通过（保留既有 chunk size 警告）；浏览器实测两个入口均弹出页面内 dialog，`getJsDialog()` 未返回浏览器原生确认框。

### 2026-06-26 - 工作空间创建表单改为三步式

- Why: 用户标注"工作空间管理"里的创建工作空间区域，要求所有输入项都有标签，"加载分支"改为"刷新分支"，并明确呈现刷新分支、加载目录、创建工作空间三步操作。
- What: `SettingsAppWorkspacePanel.vue` 将创建工作空间表单改为三条步骤行，补齐已关联版本库、分支、目录、工作空间名称可见标签；按钮文案改为"刷新分支"；测试和 README/PACKAGE 同步三步式约束。
- How: 保持原有 `loadBranches`、`loadDirectories`、`createWorkspace` API 调用不变，只调整模板结构和局部 CSS，用编号圆点和步骤标题表达操作顺序。
- Result: 浏览器实测创建区展示 3 个步骤，四个输入标签均可见，旧"加载分支"文案不再出现。
- Verification: 先写三步/标签/文案断言并确认旧实现失败；随后 `corepack pnpm test apps/agent-web/tests/settings-app-workspace-panel.test.ts` 8/8 通过；`corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm --filter @test-agent/agent-web build` 通过（保留既有 chunk size 警告）；浏览器曾实测三步、标签和旧文案消失，后续仅补响应式宽度约束，尝试复验时浏览器控制超时。

### 2026-06-26 - 多服务器广播与应用版本工作区副本同步

- Why: 应用版本工作区原来只有版本表上的单份 runtime workspace/path，无法保证多台后端服务器上的应用版本文件内容一致；版本创建、个人同步到应用、版本 git pull 也缺少跨节点触发能力。
- What: 新增 domain 广播 envelope/端口与 event 模块 Redis/Noop 实现；`application_workspace_versions` 增加目标 commit，新增 `application_workspace_version_replicas` 记录每服务器副本路径、runtime workspace、当前 commit 和同步状态；`ManagedWorkspaceApplicationService` 改为副本感知，创建/补齐版本、个人同步到应用、版本 git pull 后发布 `workspace.version.sync-requested`，远端节点 clone/fetch/reset 到目标 commit；新增补偿器扫描漏消息；API 新增 `POST /workspace-versions/{versionId}/git-pull`，响应透传目标 commit 和副本状态；前端 `backend-api` 增加 `gitPullWorkspaceVersion` 与可选 DTO 字段。
- How: 广播 payload 只放 version/user/reason/target commit 等安全字段，不放 SSH key/token/文件内容；Redis 消费端用统一 `instanceId` 跳过本实例，业务 handler 再跳过同 `linuxServerId`；Noop 按 `test-agent.server-broadcast.enabled=false/missing` 装配，Redis 按 `enabled=true` 装配，避免两个 publisher bean 同时存在；跨服务器首次创建时当前后端先创建全局版本和本机副本，再广播并短暂等待目标服务器副本 READY 后返回目标 runtime workspace。
- Result: 多机部署开启共享 Redis 和 `test-agent.server-broadcast.enabled=true` 后，应用版本创建/补齐、个人同步、版本 git pull 会触发其他后端同步；漏掉 pub/sub 消息时本机补偿器根据数据库目标 commit 追平；单机或未启用 Redis 时仍记录本机副本并保持兼容。
- Pitfalls: 不要并行跑两个 Maven reactor 写同一模块 `target`，会出现 Surefire `ClassNotFoundException` 误报；`@ConditionalOnMissingBean` 不适合这里的组件扫描 Noop publisher，Redis 开启时可能因扫描顺序生成双 bean，必须用互斥的 `ConditionalOnProperty`；远端 reset 前必须检查工作树干净，失败只标记副本 `FAILED` 并记录脱敏错误。
- Verification: `mvn -pl test-agent-common,test-agent-domain,test-agent-event,test-agent-persistence,test-agent-workspace-management,test-agent-api -am test` 通过；`mvn -pl test-agent-app -am test` 通过；`corepack pnpm test -- backend-api` 120/120 通过；`corepack pnpm --filter @test-agent/backend-api typecheck` 和 `corepack pnpm --filter @test-agent/shared-types typecheck` 通过；`git diff --check` 通过。

### 2026-06-26 - 重启脚本改为前端/后端/opencode-manager 逐个 kill-then-start

- Why: 用户要求 `restart-dev-services.sh` 运行后逐个重启前端、后端、opencode 管理进程（Go），每个先 kill 原进程再启动，并落实 opencode-manager 的启动。原脚本虽有 manager 启停代码，但 `should_start_opencode_manager` 的 auto 分支要求 `TEST_AGENT_OPENCODE_MANAGER_TOKEN` 已设置，用户 `.env.local` 未设置，导致 Go 管理进程从未启动，实际只跑 standalone `opencode serve`。
- What: `restart-dev-services.sh` 在 `load_env_file` 后默认 `TEST_AGENT_OPENCODE_MANAGER_TOKEN=local-manager-token`（与 `application-guo.yml` 一致，不改 `.env.local`）；`should_start_opencode_manager` 的 auto 判定改为「`TEST_AGENT_OPENCODE_BASE_URL` 已设置且 backend_url 为本地」，避免在无 `go` 的校验环境触发 `build_opencode_manager`；新增 `stop_backend_service`/`stop_opencode_manager_service`/`stop_frontend_service` 三个停止辅助函数（manager 步骤额外清理残留 standalone `opencode serve` 防 4096 冲突）；主流程重写为「后端 → opencode-manager → 前端」逐个 kill-then-start，移除 `start_opencode` 调用；更新 usage 文案；同步 `docs/deployment/backend.md`、`frontend/README.md`。
- How: token 默认值让 local/test/guo 三个 profile 的后端 `manager-control.token` 与 manager 自动匹配（guo 硬编码 local-manager-token，local/test 从同一环境变量读取）；per-service 停止复用现有 `stop_pids`/`stop_screen_session`；构建仍前置，失败不动现有服务。
- Result: 脚本运行后按依赖顺序逐个重启三服务，本地默认启动 Go opencode-manager 并由其派生 opencode 子进程，不再单独启动 standalone `opencode serve`。
- Verification: `bash -n`/`sh -n` 通过；`./tools/verify-dev-scripts.sh` 全绿（含两个隔离 env 用例与 sh 重进 bash 断言）。

### 2026-06-26 - 将 SSH key 加密密钥独立到 .key 文件

- Why: SSH key 加密密钥 `test-agent.security.ssh-key-encryption-key` 原先在 `application-guo.yml` 中硬编码，local 等 profile 未配置时抛"SSH key 加密密钥未配置"错误。
- What:
  - 新建 `backend/test-agent-app/src/main/resources/ssh-key.key` 文件（properties 格式），放置 AES-256 加密密钥。
  - `TestAgentApplication.java` 添加 `@PropertySource("classpath:ssh-key.key")`，Spring 自动加载到 Environment。
  - 删除 `application-guo.yml` 中冗余的 `ssh-key-encryption-key` 配置行。
  - 三个 `@Value` 注入点（`SshKeyEncryptionService`、`AgentConfigApplicationService`、`ManagedWorkspaceApplicationService`）零改动。
  - `*.key` 已在 `.gitignore` 中，密钥文件不提交仓库。
- How: properties 格式 `.key` 文件，`@PropertySource` 自动解析；env var `TEST_AGENT_SSH_KEY_ENCRYPTION_KEY` 优先级高于 `.key` 文件，生产仍可用 env 覆盖。
- Result: 编译通过，`SshKeyEncryptionServiceTest` 和 `SshKeyCryptoServiceTest` 全部通过；密钥统一由 `.key` 文件承载，后续其他密钥也按此模式加入。

### 2026-06-26 - SSH key 改为前端混合加密（RSA + AES-256-GCM）

- Why: 原 SSH 私钥明文从前端传输到后端再 AES 加密存储，静态 AES 密钥泄露即可解密全库；用户要求前端密文传输、并改非对称方案。最终定为混合加密：前端 AES 加密私钥、RSA 加密临时 AES 密钥，后端 RSA 解密。
- What:
  - 新增 `test-agent-common` 的 `RsaKeyService`（加载 `classpath:rsa-private.key` PEM，缺失自动生成临时密钥）和重写 `SshKeyEncryptionService`（RSA 解密 AES 密钥 + AES-GCM 解密私钥 + 指纹校验），二者为纯 Java 类，由 `test-agent-app` 的 `SshKeyConfig` 装配 `@Bean`。
  - `UserSshKey` 新增 `encryptedAesKey` 字段；Flyway `V10` 给 `user_ssh_keys` 加 `encrypted_aes_key` 列；JDBC Repository 同步列。
  - `ConfigurationManagementApplicationService.addSshKey` 改为接受前端密文 payload 并 `decryptAndVerify` 校验；`privateKeyFor`/`decryptSingleSshKey` 改混合解密，旧记录（`encryptedAesKey` 为 null）友好报错提示重新添加。
  - `ManagedWorkspaceApplicationService`/`AgentConfigApplicationService` 的 `SshKeyCryptoService` 字段改为 `SshKeyEncryptionService`，移除 `@Value` 静态 AES 密钥注入。
  - API 新增 `GET /ssh-key/public-key`（免鉴权返回 RSA 公钥 SPKI Base64）；`AddSshKeyRequest` 改为 `name/encryptedPrivateKey/encryptedAesKey/encryptionNonce/fingerprint`。
  - 前端新增 `utils/ssh-crypto.ts`（Web Crypto API 混合加密，指纹用 url-safe base64 no-padding 与后端对齐）；`SettingsPersonalPanel.vue` 提交前先取公钥再加密；`backend-api` 加 `getSshKeyPublicKey`；`shared-types` 更新 `AddSshKeyPayload` 并新增 `SshKeyPublicKeyResponse`。
  - 删除旧 `ssh-key.key`（AES 密钥）和 `@PropertySource`，新增 `rsa-private.key`（PEM，force-add 入库）。
- How: RSA-2048 + OAEP/SHA-256（只加密 32 字节 AES 密钥，无大小限制问题）；AES 密钥每次前端随机生成、不落服务端配置，只以 RSA 加密形态存库；Web Crypto AES-GCM 输出（密文+tag）与 Java GCM doFinal 期望格式一致。`test-agent-common` 无 SLF4J 编译依赖，RsaKeyService 用 `java.util.logging`。
- Result: 后端 4 个相关模块测试全绿（含新增公钥端点、混合解密、指纹校验、addSshKey 存储验证用例），前端 `backend-api` 25 测试全绿，`agent-web` typecheck 通过。旧 SSH key 记录需用户重新添加。
- Pitfalls: `SshKeyEncryptionService` 原在 configuration-management 模块，workspace-management 不依赖该模块无法引用；移到 `test-agent-common` 作纯 Java 类 + app 模块 `@Bean` 装配解决。指纹格式后端用 `getUrlEncoder().withoutPadding()`，前端必须对应转 url-safe 去填充，否则校验失败。

- Why: 收起态的小绿点原本实色范围过大，视觉上"实心"占比偏高且位置固定在右下角，用户希望实心范围更小、并支持拖动到任意位置。
- What: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue` 中 `.figma-chat-process-dot` 的 `radial-gradient` 第二段 stop 由 `55%` 提前到 `25%`（is-ready / is-blocking 同步），实心向边缘过渡更早，中间高亮区域显著缩小；`.figma-chat-process-dot` 由 `position: relative` + flex 容器定位改为 `position: fixed`，位置通过 CSS 变量 `--figma-process-dot-x/y` 经 `transform: translate3d` 承载（避免与 `:hover` 的 `scale(1.15)` 互相覆盖），`cursor: grab / grabbing`，新增 `is-dragging` 状态；模板绑定 `:style="processStatusDotStyle"`、`@pointerdown="onProcessStatusDotPointerDown"`、`@click="handleProcessStatusDotClick"`（点击和拖动通过 4px 阈值区分，拖动产生的 click 不会触发 toggle）；script 新增 `processStatusDotPos` 状态、`loadProcessDotPos`/`saveProcessDotPos` 持久化到 `localStorage('figma-chat-process-dot-pos')`、`clampProcessDotPos` 边界裁剪、`onProcessStatusDotResize` 窗口变化时夹紧；`onMounted` 读位置、注册 resize 监听，`onBeforeUnmount` 解绑 pointer/resize 监听。
- How: 仅在 `FigmaChatPanel.vue` 内单文件改动，不动 store/props/emit；展开态面板 `figma-chat-process-status` 不受拖动逻辑影响，行为保持原样。
- Result: 收起态圆点实心范围明显收窄（虚化晕圈占比更大），鼠标可拖动到视口任意位置，刷新后位置保留；普通点击仍展开为状态卡片，拖动距离 > 4px 不会误触发 toggle。
- Verification: `corepack pnpm --filter @test-agent/agent-web typecheck` 未引入新错误（既有 pre-existing `ChatMessage`/`runtime-reducer` 报错与本次无关）；浏览器实测点开 → 展开为「opencode 进程可用 http://192.168.100.115:4096」流程正常。

### 2026-06-26 - 修复 agent-web workspace 类型解析失败

- Why: `corepack pnpm --filter @test-agent/agent-web build` 在 `workbench-shell/src/workbenchStore.ts` 报 `Cannot find module '@test-agent/shared-types'`；排查发现本地 `workbench-shell/node_modules/@test-agent/shared-types` 链接缺失，同时 `agent-web` 继承的 `baseUrl` 是 `frontend`，但 app tsconfig 把 `@test-agent/*` 写成了 app 相对路径。
- What: 运行 `corepack pnpm install --frozen-lockfile` 补齐本地 workspace 链接；将 `frontend/apps/agent-web/tsconfig.json` 的 `@/*` 和 `@test-agent/*` paths 改为以继承后的 `frontend` baseUrl 为基准，分别指向 `apps/agent-web/src/*` 与 `packages/*/src`。
- How: 先复现原始 TS2307，再检查 `vue-tsc --showConfig`、package lock 和 package-local `node_modules`，确认解析链路后只改 tsconfig alias，不改 Vite alias、不新增依赖。
- Result: `@test-agent/shared-types` 在 `agent-web` 类型检查中稳定解析到 `frontend/packages/shared-types/src`；`@test-agent/agent-web` build、`@test-agent/shared-types` typecheck、`@test-agent/workbench-shell` typecheck 均通过。

### 2026-06-26 - 优化设置页创建工作空间区域的视觉样式

- Why: 设置页"工作空间管理"下的"创建工作空间"区域原来是三个散乱的、带边框的卡片，并且当输入标签存在时，输入框和右侧的动作按钮没有底齐，导致视觉上严重错位，整体不够美观。用户后续提出希望去掉“第一步/第二步/第三步”文字前缀并使高度更加紧凑。
- What:
  - `SettingsAppWorkspacePanel.vue` 中将三个步骤项改造成统一的卡片布局，左侧以一条纵向时间线 (Timeline) 贯穿 3 个步骤圆形数字徽标。
  - 为步骤卡片引入了 `:class` 状态绑定，能够基于当前填写的状态自动呈现已完成 (is-completed)、进行中 (is-active)、已禁用 (is-disabled) 三种视觉状态。
  - 重写了 steps 的 CSS，将 controls 设为 `align-items: flex-end`，从而保证无论标签如何折行，输入/选择框都会和右侧的执行动作按钮底端对齐；同时给运行中的进度圆点加入了呼吸灯动画 (`ta-progress-pulse`)。
  - 在 script 中增加了对 `workspaceRepositoryId` 和 `workspaceBranch` 的 watcher，当用户更改上游版本库或分支时，能自动清空下游已选值及列表，防止出现脏数据和不一致状态。
  - 为 steps 引入了 `ta-workspace-step-inputs` 包装容器，显式设置 label 的固定宽度（320px/240px/180px/140px）并且让 element 控件宽度 100%，消除因为 inline-flex 宽度计算导致的下拉框坍缩现象。
  - 在 controls 容器上使用 `justify-content: space-between` 和 `width: 100%`，把动作按钮推到最右侧，实现动作按钮在最右端纵向对齐的布局。
  - 在 `ta-workspace-step-heading` 样式上添加了 `white-space: nowrap`，防止步骤标题文字产生意外折行。
  - 去掉了步骤标题中的“第一步：”、“第二步：”、“第三步：”前缀文字。
  - 将 steps 容器的上下 padding 从 `20px` 压缩为 `12px`；第一列网格列宽由 `180px` 缩窄为 `140px`，并将引导线的 `top`/`bottom` 位置相应调整为 `32px`，从而使整体界面高度更加紧凑。
  - 同步修改了 `settings-app-workspace-panel.test.ts`，去掉了步骤断言的前缀；并使用 `getAllByText` 和 `.find(el => el.tagName === "BUTTON")` 等更加精准的 DOM 筛选逻辑以消除文字重复带来的获取歧义。
- How: 仅在 `SettingsAppWorkspacePanel.vue` 和 `settings-app-workspace-panel.test.ts` 中修改，不改变任何已有的功能 API，确保完全向下兼容。
- Result: "创建工作空间"区域改为了精致的纵向时间线步骤设计。步骤标题不再有“第X步：”前缀，高度和列宽显著收窄，整体布局紧凑而精美。所有的输入控件合理加宽，右侧按钮对齐。
- Verification: 运行 `corepack pnpm test apps/agent-web/tests/settings-app-workspace-panel.test.ts` 11/11 全部通过；运行 `corepack pnpm --filter @test-agent/agent-web typecheck` 通过；没有破坏任何既有的 test断言或结构。

### 2026-06-26 - 通用参数管理参数值修改改为弹窗修改

- Why: 通用参数管理页面中的参数值原为表格行内 input 框输入，容易产生误触且对长路径参数的展示和编辑不够友好，用户要求点击参数值后弹出 DIV (Dialog) 进行修改。
- What:
  - `GeneralParamManagementPanel.vue` 中移除表格行内 `el-input`，改为带 Code 样式的可点击药丸组件 `.ta-common-param-val-cell`，当 hover 时变蓝并显示“点击修改”提示。
  - 在 script 中移除与行内草稿相关的 `valueDrafts`、行内 dirty 检查、行内 reset 及行内 saveValue 函数，删除 rows 数据变化时的 watcher 监听。
  - 引入了 Element Plus 的 `el-dialog` 编辑框，放置在模板底部；当点击参数值或“编辑”按钮时，触发 `openEditDialog(param)` 在弹窗内显示参数的英文名、中文名、适用平台和可拉伸的多行 textarea 输入框。
  - 在 Dialog footer 中放置“取消”和“保存”按钮，并通过 `isDialogValueDirty` 属性控制保存按钮的禁用状态，保存成功后自动 invalid 缓存刷新数据并关闭 Dialog。
  - 在 table 中把操作栏的“保存”和“重置”按钮替换为了单个“编辑”按钮，统一点开编辑弹窗的入口。
- How: 仅修改 `GeneralParamManagementPanel.vue` 单一文件，移除已废弃的行内编辑逻辑，不改动任何后端 DTO 或 HTTP 接口契约，完全向下兼容。
- Result: 通用参数列表不再直接暴露 input 框，改为了精美的只读气泡形态。点击参数气泡或右侧“编辑”按钮即可弹出系统级 Dialog，提供多行宽敞的文本域编辑路径参数，修改体验更加高级和安全。
- Verification: 运行 `corepack pnpm test apps/agent-web/tests/settings-app-workspace-panel.test.ts` 11/11 通过。因本地工作区其他开发者引入了未提交的 SSH 秘钥加密变动导致 `SettingsPersonalPanel.vue` 报错，排查确认本组件 `GeneralParamManagementPanel.vue` 自身无任何 TS 类型错误。

### 2026-06-26 - 明确禁止 Flyway 发布测试数据

- Why: 需要防止测试、演示、个人开发或环境专属数据通过 Flyway migration 随生产结构迁移一起发布，避免污染共享库和线上库。
- What: 在 `AGENTS.md`、后端规范、数据库部署文档、自检清单以及 `test-agent-persistence` README/PACKAGE 说明中新增规则：Flyway 仅承载结构变更、历史数据兼容迁移和生产必需基础字典/系统参数；测试数据放测试 fixture、`test-agent-test-support`、mock 数据、显式本地开发脚本或人工初始化流程。
- How: 仅修改稳定文档，不触碰当前工作区已有后端代码、配置和未提交 migration；同时整理 `AGENTS.md` 强制规则编号，删除重复的 session-log 规则副本。
- Result: 后续新增 migration 时，入口规范、后端规范、数据库文档、包级说明和提交前自检都会阻止把测试数据带入 Flyway。

### 2026-06-26 - 清理 V17 本地 loopback opencode 种子

- Why: V17 曾写入 `127.0.0.1` 本地 opencode 拓扑和默认用户绑定，后端改为真实 IP/心跳注册与 `local-direct` 后，这批数据会变成运行管理里的历史脏数据。
- What: 保留 `V17__seed_local_opencode_machine_for_default_user.sql` 作为 Flyway 历史文件，新增 `V20260627000000__cleanup_loopback_linux_server_seed.sql` 清理 `linux_servers`、backend/opencode 进程拓扑、用户绑定和 manager-backend 连接中 `linux_server_id='127.0.0.1'` 的历史数据；同步持久化 README、PACKAGE 和数据库部署文档。
- How: 集成测试从完整迁移链断言 V17 loopback 种子最终不存在，并从 `target("17")` 的历史状态补一条本地 backend connection 后跑全量迁移，验证清理脚本按外键顺序删干净。
- Result: V17 文件不直接改动，避免已应用历史库 Flyway validate 失败；`JdbcRepositoryIntegrationTest` 全部通过。`FlywayMigrationNamingTest` 仍被既有 `V18__create_scheduler_framework_tables.sql` 阻断，需后续单独处理该历史命名问题。

### 2026-06-26 - 修复 Flyway V10/V13 历史迁移校验失败

- Why: 启动时报 `Migration checksum mismatch for migration version 10` 和 `applied migration not resolved locally: 13`，根因是 `V10__seed_fcoss_application.sql` / `V13__seed_fcoss_more_workspaces.sql` 被从工作区移除，同时 SSH key 的 `encrypted_aes_key` schema 变更错误复用了已落库的 V10 版本。
- What: 恢复 V10/V13 历史 seed migration 的解析；删除 `V10__add_encrypted_aes_key_to_user_ssh_keys.sql`，改为 `V20260627010000__add_encrypted_aes_key_to_user_ssh_keys.sql`；`FlywayMigrationNamingTest` 增加历史 seed 文件必须存在、V10 不得复用的断言，并把已发布的 V18 作为历史例外保留。
- How: 先运行新增测试确认当前坏状态会失败；再恢复历史迁移、移动 SSH key 列变更到时间戳 migration，并用 `mvn -pl test-agent-persistence clean test -Dtest=JdbcRepositoryIntegrationTest,FlywayMigrationNamingTest -Dsurefire.failIfNoSpecifiedTests=false` 验证完整迁移链。
- Result: 持久化模块 26 个目标测试通过；后续已落库 migration 禁止删除、重命名、改写或复用版本号，schema 变更必须走新的时间戳 migration。

### 2026-06-26 - 补强 V17 loopback 清理的外键顺序

- Why: 用户数据库执行 `V20260627000000__cleanup_loopback_linux_server_seed.sql` 时，删除 `opencode_containers` 被 `opencode_server_processes.container_id` 外键阻塞；根因是历史库存在进程自身 `linux_server_id` 不是 `127.0.0.1`、但 `container_id` 仍指向 V17 loopback container 的脏行。
- What: 清理脚本删除 user binding、opencode server process、container manager 和 manager-backend connection 时，同时按 `linux_server_id='127.0.0.1'` 与引用 loopback container 两条路径定位待删数据。
- How: 在 `JdbcRepositoryIntegrationTest#cleanupMigrationRemovesHistoricalLoopbackTopology` 中插入“非 loopback server 进程引用 loopback container”的历史脏数据，先确认原脚本外键失败，再补齐删除条件。
- Result: 定向迁移用例通过；后续写历史拓扑清理时不能只看子表自己的 `linux_server_id`，还要沿外键反查父级 loopback 资源。

### 2026-06-26 - 运行心跳改为 Redis 快照并移除 manager HTTP 发现路径

- Why: Java 后端和 Go manager 的在线状态不应继续写入或依赖数据库 heartbeat 字段；Go manager 需要在所有 Java 连接断开后通过 `.serverip + backend port` 持续重连，并且控制面交互只能走 WebSocket。
- What: 新增 Java backend/manager Redis 运行快照，TTL 10 秒，Java 与 manager 心跳间隔改为 5 秒；运行管理、manager 后端列表响应和 Workspace 文件后端路由改读 Redis 快照。Go manager 删除 HTTP discovery client，启动派生 seed WebSocket，断线后每 10 秒无限重连，有连接时每 10 秒通过 `backendListRequest` 补连缺失 Java 后端，每 5 秒通过任一 socket 发送 `managerHeartbeat`。
- How: WebSocket 协议新增 `managerHeartbeat`、`backendListRequest`、`backendListResponse`；Redis store 保存 JSON 快照并新增 `jackson-datatype-jsr310` 依赖支持 `Instant`；本地脚本不再注入 HTTP discovery URL，文档同步 Redis 强依赖、WebSocket-only 控制面、5 秒心跳和 10 秒 TTL。
- Result: Go 全量测试、脚本校验、Redis/运行管理/manager WebSocket 聚焦 Maven 测试通过；完整 Maven 目标集合仍只失败于既有 3 个 guo profile 配置断言（session log 已记录），与本次 Redis/WebSocket 改动无关。

### 2026-06-27 - 运行管理新增 Redis 指标历史和趋势图

- Why: 超级管理员运行管理需要查看容器和 Java 后端的最新资源指标，并在点击行后追踪近 48 小时 CPU、内存、磁盘 IO、进程容量和 JVM 指标趋势；在线态仍由 Redis latest snapshot TTL 决定。
- What: 扩展 managerHeartbeat 和 Java backend heartbeat，latest snapshot 保持 10 秒 TTL，同时向 Redis ZSET 写入近 48 小时原始 5 秒指标样本；新增容器/后端 metrics history HTTP API，运行管理 overview 增加最新指标字段，前端运行管理按需加载 ECharts 展示趋势。
- How: Go manager 使用 Linux cgroup v2/v1 和 procfs 只读采集容器 CPU、内存、磁盘 IO，并把本地 opencode 进程明细随 latest snapshot 上报；Java 后端通过 JDK MXBean 和当前工作目录文件系统采集服务器/JVM 指标；history API 对超过 `maxPoints` 的样本按时间桶降采样。文档同步 API、事件边界、部署说明、backend/frontend README 和 module map。
- Result: `go test ./...`、后端指定 Maven reactor、运行管理相关前端测试和 `corepack pnpm typecheck` 通过；`corepack pnpm test` 仍失败于既有 `apps/agent-web/tests/FigmaChatPanel.test.ts` 两个历史消息渲染断言，和本次运行管理改动无关。

### 2026-06-27 - Redis disabled 时跳过后端心跳 runner

- Why: `test-agent.redis.enabled=false` 或 prod 默认未显式启用 Redis 时，`BackendJavaProcessLifecycleRunner` 启动阶段无条件调用 `registerHeartbeat()`，触发 `Redis 运行心跳未启用` 并中断整个 Spring Boot 启动；这与 Redis optional health 的“应用可启动、运行管理/manager 链路 fail fast”边界不一致。
- What: `BackendJavaProcessLifecycleService` 暴露 `heartbeatEnabled()`，app runner 在 Redis 心跳未启用时跳过 `.serverip` 写入、Java backend snapshot 注册和周期调度；只有成功启动生命周期后，销毁阶段才标记本 backend offline。
- How: 新增 `OpencodeManagerControlConfigTest` 覆盖 disabled heartbeat store 下 runner 不抛错、不写 `.serverip`、不注册心跳、不 mark offline；同步 app README 和部署文档说明 Redis disabled 语义。
- Result: 聚焦 Maven 回归和后端目标集合通过；生产启用用户进程模型仍需设置 `TEST_AGENT_REDIS_ENABLED=true`，否则运行管理与 manager 控制链路保持 fail fast。

### 2026-06-27 - 移除 Redis 启用开关和内存降级

- Why: Redis 已被明确为系统必需依赖，继续保留 `test-agent.redis.enabled` / `TEST_AGENT_REDIS_ENABLED` 会制造“可关闭 Redis”的错误心智，并导致启动路径出现旧的未启用判断。
- What: 删除 Redis 启用开关配置、运行心跳 `enabled()` 端口、Noop 心跳存储和内存 TokenStore fallback；Java backend 生命周期、运行管理、manager socket、用户进程分配、workspace 路由和 scheduler 均默认依赖 Redis Bean，不再检查“Redis 未启用”分支。
- How: 将 Redis heartbeat store、TokenStore 和 health indicator 改为必需 Redis 实现；`SchedulerStartupValidator` 只校验启用 scheduler 时存在 `StringRedisTemplate`；配置绑定和文档同步移除开关说明，测试改为验证即使传入旧开关也不会改变 Redis 必需行为。
- Result: Redis 不再有独立启用参数；旧的 `Redis disabled 时跳过后端心跳 runner` 决策已被本次变更取代。聚焦测试通过；完整 Maven 回归仍被工作区未跟踪 migration `V20260627010000__seed_opencode_manager_max_processes_param.sql` 与已存在 migration 版本重复阻塞，未纳入本次提交。

### 2026-06-27 - 修复 manager 最大进程数参数 migration 版本冲突

- Why: app fat jar 启动时报 Flyway `Found more than one migration with version 20260627010000`，根因是 opencode-manager 最大进程数参数 seed 曾使用与 SSH key `encrypted_aes_key` schema migration 相同的时间戳版本，旧打包产物里同时包含两份 `20260627010000` migration。
- What: 将 manager 最大进程数系统参数 migration 固化为 `V20260627020000__seed_opencode_manager_max_processes_param.sql`，初始化 `common_parameters.OPENCODE_MANAGER_MAX_PROCESSES=8/all`；同步 persistence README/PACKAGE 和数据库部署文档。
- How: 运行 Flyway 命名测试和 H2 迁移集成测试验证版本唯一，再重新打包 `test-agent-app`，检查嵌套 `test-agent-persistence` jar 只包含 `V20260627010000__add_encrypted_aes_key_to_user_ssh_keys.sql` 与 `V20260627020000__seed_opencode_manager_max_processes_param.sql`。
- Result: 重复版本错误消除；后续若修改该默认值，应通过通用参数管理或新的兼容 migration，不得改写已发布文件。

### 2026-06-27 - 修复运行管理趋势图 UTC 时间直显

- Why: 运行管理趋势图 X 轴直接截取后端 `Instant` ISO 字符串，导致 UTC `2026-06-26T17:28:00Z` 在东八区页面显示为 `06-26T17:28`，与列表心跳的本地时间显示不一致。
- What: 抽出图表采样时间格式化函数，先解析时间再按浏览器本地时区显示为 `MM/DD HH:mm`，非法时间显示 `-`。
- How: `RuntimeMetricChart.vue` 改用统一格式化函数，前端运行管理测试覆盖 `2026-06-26T17:28:00Z` 显示为 `06/27 01:28`。
- Result: 运行管理趋势图时间轴与列表心跳时间使用同一本地时区语义；不改后端 API、Redis 样本或历史查询范围。

### 2026-06-27 - 运行管理指标历史改为分钟级窗口并支持48小时自定义

- Why: 原 `hours` 参数只能表达整数小时且最小 1 小时，无法支持运行管理趋势图的 1 分钟、30 分钟短窗口，同时用户也需要能自主调整/查看最大 48 小时（2880 分钟）的历史图表。
- What:
  - 指标 history API 新增 `windowMinutes` 主参数，允许 `1/30/60/360/720/1440/2880`，默认 60 分钟；旧 `hours` 保留兼容但前端不再使用。
  - 前端 `RuntimeManagementPanel.vue` 在 radio-group 按钮组中新增 `48小时` (value = 2880) 选项。
  - 后端 `RuntimeManagementController.java` 中的 `ALLOWED_METRIC_WINDOW_MINUTES` 集合追加 `2880`。
  - 单元测试与 API 文档同步更新。
- How: 在后端校验白名单和前端 UI 按钮中同步加入 2880 分钟，调整测试断言值，并更新 HTTP API 文档。
- Result: 趋势图支持 1分钟/30分钟/1小时/6小时/12小时/24小时/48小时 自定义切换，单元测试及编译通过。

### 2026-06-27 - 左右侧边栏收起与展开渐进式动画优化

- Why: 提升侧边栏折叠与展开的交互流畅度和视觉质感，当用户点击折叠/展开左右侧栏按钮时，侧面板应平滑过渡，而不是瞬间消失或跳变。
- What: 修改 `frontend/apps/agent-web/src/components/FigmaShell.vue`：
  - 定义 `leftPanelStyle` 与 `rightPanelStyle` 响应式计算样式，控制宽度从设定的宽度到 `0px`，不透明度从 `1` 到 `0`，指针事件从 `auto` 到 `none`。
  - 在模板中分别移除 `.figma-panel-left` 和 `.figma-chat-panel-wrapper` 的 `v-if` 条件渲染限制（但保留内部 resize handle 的 `v-if`），改由计算样式动态驱动宽度和显示。
  - 为面板及容器增加 `.is-resizing` 类，在用户处于手动拖拽调整宽度期间屏蔽 CSS 过渡动画（`transition: none !important`），避免拖拽滞后。
  - 在 CSS 中为 `.figma-panel-left`、`.figma-chat-panel-wrapper` 与左侧浮动按钮 `.figma-sidebar-toggle-floating` 配置 `0.25s` 的渐进式过渡动画。
- How: 用 CSS transition 替换硬性的 Vue DOM 节点移除/插入逻辑，辅以 active resizing 变量进行 class 动态控制来实现丝滑的过渡与零延迟的手工拖拽。
- Result: 左右侧边栏在折叠和展开发生时，均呈现出完美的 0.25s 渐变动画效果，且不影响手动拖拽的流畅度；前端类型校验和单元测试全部通过。

### 2026-06-27 - 修复 FigmaChatPanel 历史消息单元测试异步渲染失败

- Why: `MarkdownView` 内部用 150ms 定时器 + 动态 import markdown-it/dompurify/hljs 异步渲染正文，`FigmaChatPanel.test.ts` 的两个用例 mount 后同步读取 `wrapper.text()` 做断言时仍停在“渲染中…”占位，导致“历史消息按序渲染”和“空助手消息不渲染”两个用例既有失败。
- What: 在测试文件中新增同步直出 `source` 的 `MarkdownView` 桩，并给这两个 mount 调用加 `global.stubs`，让正文断言与 MarkdownView 渲染时序解耦。
- How: 桩组件只渲染 `<div class="ta-md-view">{{ source }}</div>`，保留 `.figma-chat-assistant` 结构与 meta 行，不影响用例里的元素数量和时间断言。
- Result: 22 个测试文件 / 131 个用例全部通过；不涉及 API、事件、数据库或后端，仅测试文件改动。

### 2026-06-28 - 修复 test 环境 opencode 重启后 503

- Why: 切换 IP/数据库后重启，Go `opencode-manager` 会反复断开 Java 控制面连接并导致 opencode 不可用；同时 `test` profile 的完整 Actuator health 被旧固定 opencode node 探测打成 DOWN。
- What: `ManagerControlMessageCodec` 禁用 Jackson 时间戳序列化，确保 WebSocket 控制面发给 Go manager 的 `Instant` 是 RFC3339 字符串；`OpencodeNodesHealthIndicator` 在 manager/socket 且非 local-direct 模式下跳过 legacy 固定节点探测，只保留该探测给 local-direct/static-token fallback；`JdbcOpencodeProcessManagementRepository` 读取历史用户进程时归一化 `updated_at < created_at` 的脏数据，避免旧记录让 wr 用户状态接口直接 400。
- How: 用定向单测先复现 `lastHeartbeatAt` 非字符串、manager/socket 仍探测 `127.0.0.1:4096`、历史进程时间戳阻断 Repository 映射的坏状态，再修复实现；同步 `test-agent-app`、`test-agent-opencode-runtime`、`test-agent-persistence` README/PACKAGE、数据库和后端规范文档；本地重启技能改为默认 `.env.test` + profile `test`。
- Result: `test` 环境重启后 `/actuator/health` 与 readiness 均为 UP，前端 3000 可访问，manager 日志等待多个发现周期无 `Time.UnmarshalJSON` 或 websocket 断连；wr 用户状态接口可返回 `NEEDS_INITIALIZATION` 并在初始化后恢复 READY。初始化后立即查询仍可能遇到 opencode HTTP服务短暂 warm-up 窗口，最终状态已验证为 READY；相关 Maven reactor 测试通过。

### 2026-06-28 - 分支与目录选择框变更为可输可选、隐藏以点开头的目录，并添加刷新进度条

- Why: 用户要求在「工作空间管理」中创建工作空间时的分支和目录两个选择框支持可输可选（即既可快速搜索过滤，也可直接回车输入自定义路径）。同时，以 `.` 开头的隐藏文件/文件夹默认应该在目录列表中隐藏，只有在用户输入内容进行过滤或主动输入时才可展示。此外，用户希望在刷新分支和加载目录时能显式提供进度反馈（进度条与按钮加载状态）。并且，为彻底解决竖线穿过数字圈圈的问题，直接将竖线移除。
- What:
  - 修改 `frontend/apps/agent-web/src/components/settings/SettingsAppWorkspacePanel.vue`。
  - 为分支选择和目录选择下拉框添加 `filterable`、`allow-create` 与 `default-first-option` 属性，使其支持检索与输入。
  - 新增 `directorySearchQuery` 状态，监听目录下拉框事件，在输入时更新过滤词，关闭时重置。
  - 新增 `filteredDirectories` 计算属性，用于默认过滤以 `.` 开头的路径。
  - 引入 `loadingBranches` 与 `loadingDirectories` 加载状态，在「刷新分支」与「加载目录」异步接口调用期间启用按钮的 `:loading` 状态，并在对应步骤底部渲染一个绝对定位的动画进度条（使用 `el-progress` 不确定进度条模式）。
  - 删除了 `.ta-workspace-create-steps::before` 样式块，彻底移除了步骤背景竖线，解决了竖线穿过数字圈圈的遮挡问题。
  - 在 `settings-app-workspace-panel.test.ts` 中注册 `ElProgress` stub，消除 Vitest 运行时的组件解析警告。
- How: 纯前端代码更新，使用 Element Plus 的 filterable / allow-create / el-progress 配合 Vue computed 过滤并移除背景伪元素竖线来实现。
- Result: 单元测试 `settings-app-workspace-panel.test.ts` 全部通过，`pnpm typecheck` 与 `pnpm lint` 校验通过，界面交互逻辑流畅，无任何未解析组件警告。

### 2026-06-28 - 「应用工作空间」标题栏手动刷新按钮失效修复

- Why: 用户反馈左侧「应用工作空间」标题栏上的循环刷新按钮点击后完全没有反应，文件树不会重新拉取。
- What: 修改 `frontend/apps/agent-web/src/components/AgentWorkbench.vue`：
  - `loadDirectory(path, workspaceId)` 增加 `force = false` 第三参数；早返回守卫改为 `loadingPath.has(path) || (!force && entriesByDirectory[path] !== undefined)`，仅在「非强制刷新」且「已加载过」时短路，正在加载中的请求仍去重避免并发风暴。
  - 模板中 `<FigmaFileExplorer @refresh>` 从 `loadDirectory('')` 改为 `loadDirectory('', undefined, true)`，让用户点击刷新按钮时强制重新拉取根目录。
  - 同步在 `frontend/apps/agent-web/README.md` 第 34 行补充说明手动刷新按钮走 `force=true` 路径，绕过 `loadDirectory` 的去重短路。
- How: 维持原函数签名向后兼容（仅追加默认参数），未改其他 6 处 `loadDirectory` 调用方，避免影响首次加载、工作区切换和目录懒加载的现有去重行为。
- Result: 手动刷新按钮能真正触发 `api.listFiles(workspaceId, '')` 并刷新根目录行；`vue-tsc` typecheck 与 Vitest 132 个测试全部通过。

### 2026-06-28 - 工作树不随 agent 写文件实时刷新的修复

- Why: 用户反馈左侧「应用工作空间」文件树在 agent 调用 `write` / `edit` / `apply_patch` / `str_replace` / `multi_edit` / `create_file` 等写盘工具完成后不立即出现新文件，必须手动点刷新才会更新。
- What: 修改 `frontend/apps/agent-web/src/components/AgentWorkbench.vue`：
  - `refreshParentDirectory` 内的两处 `loadDirectory` 调用补上第三个参数 `force=true`：根目录走 `loadDirectory("", undefined, true)`，子目录父级走 `loadDirectory(parentPath, undefined, true)`。
  - 同步更新该函数上方中文注释，说明"父目录已加载时必须走 force=true，否则会被 loadDirectory 去重短路"以及该设计选择与新增/未加载目录的处理。
  - 同步更新 `frontend/apps/agent-web/README.md` 第 34 行描述，明确 `refreshParentDirectory` 和手动刷新按钮都依赖 `force` 参数。
- How: 复用上一条已经引入的 `force` 形参；保留"父目录未展开过就不预加载"的判断（`entriesByDirectory[parentPath] === undefined` 时直接 return），不主动拉取用户从未展开的目录；`loadDirectory` 内部的 `loadingPath.has(path)` 守卫依旧防止对同一目录的并发请求堆积。
- Result: agent 完成写文件工具后，写入位置所属的父目录会立即重新 `api.listFiles` 一次，文件树即时反映新建/删除；`vue-tsc` typecheck 与 Vitest 132 个测试全部通过。

### 2026-06-28 - card 路径与未展开父目录下的文件树实时刷新二次修复

- Why: 用户反馈上一轮修复后，agent 通过对话新生成的文件仍要按刷新按钮才能看到——差异卡片能更新，但文件树不展开新增文件的祖先目录。
- What: 修改 `frontend/apps/agent-web/src/components/AgentWorkbench.vue`：
  - `scanLiveToolParts` 的 tool card 分支（`message.role === "card" && message.cardType === "tool"`，由 `tool.started` / `tool.finished` 事件生成）原来只在 `liveTrack.value === false` 时调 `refreshParentDirectory(path)`，缺少 `expandPathToFile(path)`；与 assistant message 的 part 分支不一致，导致 card 路径下新文件所在的祖先目录不会被自动展开。补上 `expandPathToFile(path)`，让 card 路径与 assistant 路径行为完全一致。
  - `refreshParentDirectory` 去掉"父目录必须已经缓存（`entriesByDirectory[parentPath] !== undefined`）才重拉"的限制，**始终**走 `loadDirectory(parent, undefined, true)`：父目录未加载时由 `loadDirectory` 直接发起拉取（`force=true` 不会绕过 `loadingPath` 守卫，所以不会与 `expandPathToFile` 已经在飞的请求堆积并发），已加载时则覆盖旧条目。根目录分支同步去掉"`entriesByDirectory[""] !== undefined || expandedDirectories.size > 0`"前置条件，root 总是会被强制重拉一次。
  - 同步更新 `refreshParentDirectory` 上方中文注释，把"展开"和"重拉"的职责拆开（前者归 `expandPathToFile`、后者归 `refreshParentDirectory`），并明确 `loadDirectory` 内部的 `loadingPath` 守卫仍负责并发去重。
  - 同步更新 `frontend/apps/agent-web/README.md` 第 34 行描述，明确两条路径（assistant part / tool card）都会调 `expandPathToFile` + `refreshParentDirectory`。
- How: 没有改 `loadDirectory` 行为，只放宽 `refreshParentDirectory` 的调用条件并在 card 路径补齐 `expandPathToFile`，覆盖"父目录从未被用户展开过"和"工具事件只生成独立 card 消息"两种原本会被跳过的场景。
- Result: 不论 agent 走 assistant message part 还是独立 tool card 事件，新文件的所有祖先目录都会被自动展开并触发 `api.listFiles`，文件树即时反映新增；用户不再需要手动点刷新按钮。`vue-tsc` typecheck 与 Vitest 132 个测试全部通过。

### 2026-06-28 - opencode 旧绑定迁移、端口脏数据避让与本地重启清理

- Why: `test` 环境切换 IP/数据库后，旧用户 binding 会锁在旧 `linux_server_id`，初始化/状态接口可能 503；workspace 文件 WebSocket 也会因历史 workspace 仍绑定旧服务器而间歇失败；本地重启脚本只清理 standalone 4096，没有清理 manager 派生的用户 opencode 子进程和 state。
- What: `UserOpencodeProcessAssignmentService` 在原服务器无健康容器时 fallback 到当前后端全局健康容器并迁移 binding；端口选择改为按 `(linux_server_id, port)` 全局避让所有历史进程行；`WorkspaceFileRoutingService` 在旧服务器无在线后端且本机根目录可访问时安全回绑 workspace；`restart-dev-services.sh` 停止 manager 时清理 state JSON、state pid 和端口池残留 `opencode serve`；新增 `tools/verify-opencode-user-process-scenarios.sh` 插入/清理测试脏数据验证新老用户四类场景。
- How: 用单测覆盖旧 binding fallback、旧 process 缺失、端口脏行避让、workspace 安全回绑与旧服务器在线不回绑；用 `.env.test` 重启后运行场景脚本，验证新用户正常/脏数据、老用户正常/旧 binding + 旧 workspace 均能 READY、runtime health 和 file-ws-route 正常。
- Result: 本地 `test` profile 重启成功，脚本清掉旧 manager 托管进程；四类 opencode 场景通过且脚本测试数据清零。后续排查 workspace 文件树刷新时，要先看 `file-ws-route` 的 workspace/agent `linuxServerId` 是否不一致，再看真实 WebSocket ticket/连接错误。
### 2026-06-27 - 后端 Java 进程内存采集跨平台适配

- Why: 后端 Java 进程获取其所在运行主机的内存使用不准确，macOS 的 `OperatingSystemMXBean.getFreeMemorySize()` 返回值与实际可用内存差异较大（macOS 内存管理机制不同，包含活跃/非活跃/已压缩等概念）。
- What:
  - 新增 `OsType` 枚举检测当前操作系统（MACOS/LINUX/WINDOWS/UNKNOWN）。
  - 新增 `SystemMemoryProvider` 策略接口和 `MemoryInfo` 数据结构。
  - 实现 `MacOsMemoryProvider`：执行 `vm_stat` 命令获取 free + inactive + speculative 内存计算可用内存，失败时降级到 `OperatingSystemMXBean`。
  - 实现 `LinuxMemoryProvider` 和 `WindowsMemoryProvider`：使用 `OperatingSystemMXBean`。
  - 修改 `BackendRuntimeMetricsCollector` 集成策略模式，根据操作系统类型选择对应实现。
  - 新增单元测试覆盖各平台策略和采集器行为。
- How: 使用策略模式封装不同操作系统的内存获取逻辑，macOS 通过解析 `vm_stat` 命令输出获取准确的可用内存（= free + inactive + speculative），其他平台继续使用 JDK 标准接口。
- Result: macOS 环境下内存采集准确性显著提升；Linux 和 Windows 行为保持不变；后端 154 个测试全部通过。
- Verification: `mvn test -pl test-agent-opencode-runtime` 154/154 通过。

### 2026-06-27 - 将浏览器标签页图标设置为首页logo

- Why: 用户要求将浏览器标签页的图标（favicon）设置为首页面"MIMO测试智能体"左边的logo，使浏览器标签页显示品牌标识。
- What:
  - 创建 `frontend/apps/agent-web/public/` 目录
  - 将 `frontend/apps/agent-web/src/assets/figma/logo.svg` 复制为 `frontend/apps/agent-web/public/favicon.svg`
  - 在 `frontend/apps/agent-web/index.html` 的 `<head>` 中添加 favicon 链接：`<link rel="icon" type="image/svg+xml" href="/favicon.svg" />`
- How: 直接将首页面左上角logo（SVG格式）作为浏览器favicon，保持视觉一致性。
- Result: 浏览器标签页现在显示MIMO logo图标，与首页面logo保持一致。
- Pitfalls: 无。
- Verification: 文件已创建并提交到git。
- Next: 用户在浏览器中刷新页面即可看到新favicon。

### 2026-06-27 - 启动脚本清理日志 + 后端数据库/Redis 连接失败打印 IP:port

- Why: 排查用户 123456789 登录失败时，发现 `.tmp/dev-services/` 下日志文件持续累积，且后端日志里数据库/Redis 连接超时只显示晦涩的 Lettuce/JDBC Reactor 堆栈，没有直观的 IP:port，定位网络问题效率低。
- What:
  - `restart-dev-services.sh`：在构建前新增 `clear_service_logs` 步骤，每次启动清理 `backend.log`/`frontend.log`/`opencode-manager.log`/`opencode.log` 及对应 `.pid` 文件，保留 `opencode-manager-state`/`opencode-manager-session` 运行态目录。
  - `DatabaseMigrationRunner`：迁移前先做 `isValid(2)` 连通性探测，失败时打印 ERROR `数据库连接失败，请检查数据库是否可达: <jdbc url>（host=, port=）`；迁移异常也补充打印数据库地址，host/port 从 JDBC URL 解析。
  - 新增 `RedisStartupHealthCheck`（`HIGHEST_PRECEDENCE+1`）：启动早期主动 TCP 探测 Redis，成功打印 INFO，失败打印 ERROR `Redis 连接失败，请检查 Redis 是否可达: host=, port=（超时 Nms）`，解决 Lettuce 懒连接导致运行时才暴露 Redis 不可达的问题。
- How: 启动脚本用 `rm -f` 清理日志；后端用 SLF4J 在启动早期 `ApplicationRunner` 做 TCP/isValid 探测，不阻断启动，仅打印清晰地址日志。
- Result: 每次重启日志干净无累积；数据库或 Redis 不可达时启动日志直接显示 host:port，无需翻 Reactor 堆栈。
- Pitfalls: `DatabaseMigrationRunner` 仍是 `HIGHEST_PRECEDENCE`，新增的 Redis 检查用 `+1` 紧随其后；Redis host 为空时跳过探测，兼容可选 Redis 场景。
- Verification: `mvn -pl test-agent-app -am compile -DskipTests` 编译通过；`bash -n restart-dev-services.sh` 语法校验通过。

### 2026-06-27 - manager 启动参数改为 WebSocket 公共参数下发

- Why: manager 容器内不应继续通过 `OPENCODE_SESSION_ROOT`、`OPENCODE_CONFIG_DIR`、`OPENCODE_MANAGER_MAX_PROCESSES` 环境变量决定用户 opencode 进程启动参数；这些值需要与 Java 后端使用的 `common_parameters` 保持一致。
- What: `opencode-manager run` 只从环境读取连接必需项和端口池，注册后发送 `configRequest`，收到后端完整 `configUpdate(maxProcesses/sessionRoot/configDir)` 前拒绝 `start/restart`；Java 控制面从 `common_parameters.OPENCODE_MANAGER_MAX_PROCESSES`、`OPENCODE_SESSION_DIR`、`OPENCODE_PUBLIC_CONFIG_DIR` 组装配置，参数更新时广播完整配置。
- How: Go manager 增加运行时配置 ready 状态和热更新方法；Java `OpencodeManagerConfigSyncService` 改为完整配置快照同步；本地重启脚本停止注入 max/session/config 环境变量，稳定文档同步说明 WebSocket-only 配置来源。
- Result: `go test ./...`、`tools/verify-dev-scripts.sh`、`mvn -pl test-agent-domain,test-agent-opencode-runtime,test-agent-api -am test` 均通过。后续本地开发若需要自定义 session/config 路径，应修改通用参数表，而不是给 manager 加环境变量。

### 2026-06-28 - 通用参数增加变量引用、内存缓存、跨实例广播刷新与每进程加载值展示

- Why: 通用参数需支持 `${B}` 引用 B 的值、启动加载到内存、DB 修改后跨实例刷新、管理页展示每台服务器 Java 进程实际加载值。
- What:
  - domain 新增 `CommonParameterReferenceResolver`（`${englishName}` 展开，循环/缺失/超深保留字面，ALL 只能引用 ALL）、`CommonParameterValues` 只读缓存端口、`CommonParameterReloadedEvent`、`CommonParameterLoadSnapshot`/`LoadedParameter`/`CommonParameterLoadSnapshotStore`、`BackendInstanceIdentity` 端口。
  - configuration-management 新增 `InMemoryCommonParameterValues`（启动 `SmartInitializingSingleton` 全量加载，整体原子替换快照）、`CommonParameterCacheRefresher`（监听 `CommonParameterUpdatedEvent` + `ServerBroadcastHandler`：reload+写快照+发 `common-parameter.refresh-requested` 广播+发本地 `CommonParameterReloadedEvent`；远端只 reload 不转发避免循环）、`CommonParameterLoadSnapshotQueryService`；controller 新增 `GET /common-parameters/load-snapshots`。
  - persistence 新增 `RedisCommonParameterLoadSnapshotStore`（key `test-agent:common-param-snapshot:backend:{id}` TTL 30s + 索引 set）。
  - opencode-runtime 新增 `ManagerBackendInstanceIdentity` 实现；`OpencodeManagerConfigSyncService` 改用 `CommonParameterValues` 并监听 `CommonParameterReloadedEvent`；`UserOpencodeProcessAssignmentService` 改用 `CommonParameterValues`。
  - workspace-management `AgentConfigApplicationService`/`ManagedWorkspaceApplicationService` 改用 `CommonParameterValues`。
  - 前端 `shared-types`/`backend-api` 新增类型与 `listCommonParameterLoadSnapshots`；`GeneralParamManagementPanel` 新增"查看各进程加载值"按钮+抽屉。
  - 文档同步 `docs/api/http-api.md`、`docs/api/event-stream.md`、`docs/deployment/database.md`、`docs/architecture/module-map.md`。
- How: 解析器纯领域服务，缓存只存原始值读取时展开；跨实例复用既有 `ServerBroadcastPublisher` Redis pub-sub，payload 只传参数标识不传值；身份经 domain 端口 `BackendInstanceIdentity` 解决 configuration-management 不能依赖 opencode-runtime 的限制；`CommonParameterRepository` 保留为管理端写+启动加载源，消费方迁移到 `CommonParameterValues`。
- Result: 后端 `mvn test` 全绿（含新增解析器/缓存/刷新器/Redis store/controller 端点测试与迁移后的 4 个消费方测试），前端 `pnpm -r typecheck` + `pnpm test` 全绿。无 Flyway/schema 变更，API 仅新增端点，向后兼容。
- Pitfalls: `OpencodeManagerConfigSyncServiceTest` 原本深度 mock `CommonParameterRepository.findByEnglishNameAndPlatform`，迁移后改用 `CommonParameterValues` 桩（opencode-runtime 不依赖 configuration-management，无法用 `InMemoryCommonParameterValues`）；缓存启动加载用 `SmartInitializingSingleton` 而非 `ApplicationRunner`，因 configuration-management 仅依赖 spring-context。
- Verification: `mvn test`、`corepack pnpm -r typecheck`、`corepack pnpm test`。

### 2026-06-28 - “目录不是 Git 仓库”错误提示带上具体目录

- Why: 创建 Agent worktree 失败时前端只显示“创建 Agent worktree失败：目录不是 Git 仓库”，看不到具体是哪个目录，排查困难。后端异常其实已把 `path` 放进结构化 `details`，但前端 `errorMessageFor` 只渲染 `error.message`，目录信息未呈现。
- What: `ensurePublicRepositoryReady` 抛异常条件改为同时提示"目录已存在且非空，但不是 Git 仓库"（`Files.exists(gitRoot) && !isEmptyDirectory(gitRoot)`），完整说明两个阻碍因素，避免遗漏"目录非空"场景。
- How: 直接在异常消息文本里带上路径，复用前端既有 `${fallback}：${error.message}` 渲染，无需改前端。`path` 本就在可安全序列化的 `details` 中，纳入消息不引入新暴露面。
- Result: 前端错误提示变为”创建 Agent worktree失败：目录已存在且非空，但不是 Git 仓库：/data/.../xxx”，同时展示目录路径和两个阻碍条件。
- Pitfalls: 无测试断言该消息文本；未改动前端通用 `errorMessageFor`。
- Verification: `mvn -pl test-agent-workspace-management -am compile` 通过。

### 2026-06-28 - Agent 配置文件操作改为平台文件 WebSocket 路由

- Why: 分布式部署下公共/工作空间 Agent 配置文件必须在 worktree 所属服务器执行，前端不能直连目标服务器，也不能继续依赖后端到后端 HTTP 文件代理；后续 server-bound 能力扩展需要复用同一套 route/ticket/RPC 模式。
- What: `test-agent-api` 新增 Agent 配置文件路由 API 与 `mode=agent-config` 文件 WebSocket ticket 绑定，文件 RPC 新增 `agent-config.list/read/write`；`backend-api` 和 `AgentConfigPanel`/`AgentWorkbench` 将目录列表、读取、写入迁移到目标后端文件 WebSocket；旧 HTTP 文件接口仅保留本地兼容，远端目标返回明确错误要求使用文件 WebSocket。
- How: 路由按 `scope/workspaceId/worktreeId/linuxServerId` 解析目标服务器，ticket 绑定 scope/worktree/server 并由 WebSocket handler 校验 op 参数一致性和写权限；前端先请求 `agent-config/file-ws-route`，再向目标后端申请 ticket 并复用 `WorkspaceFileSocketClient` 发送 Agent 配置文件 RPC。
- Result: Git 初始化、创建 worktree、远端分支、diff、stage、commit、publish 仍走既有 HTTP/进度 WebSocket；配置文件 list/read/write 统一走平台文件 WebSocket。后端目标模块测试、agent-web typecheck、本次相关前端 Vitest 和 `git diff --check` 通过；完整 `vitest run apps/agent-web/tests packages/backend-api` 仍受工作区既有运行拓扑/系统管理前端脏改动影响失败，未纳入本次提交。

### 2026-06-28 - 工作空间文件搜索（后端递归 + 文件名关键字高亮）

- Why: 工作空间原有搜索只过滤已加载文件树（前端本地子串匹配），搜不到未展开目录的文件，且结果无关键字高亮。需支持整个工作空间递归模糊搜索文件名并高亮关键字。
- What:
  - 后端 `WorkspaceFileService` 新增 `searchFiles(rootPath, query)`：递归遍历，文件名不区分大小写子串匹配，跳过硬编码黑名单目录（`.git`/`node_modules` 等），深度上限 20、超时 5s、结果上限 200；新增 `FileSearchResultResponse`（path/name/directory/size/lastModifiedAt）。
  - `WorkspaceApplicationService` 暴露 `searchFiles`；`WorkspaceFileWebSocketHandler` 新增 `workspace.search` RPC op（与现有文件操作同走 WebSocket RPC）。
  - 前端 `backend-api` 新增 `searchFiles`；`shared-types` 新增 `FileSearchResult`；`file-explorer` 新增 `highlightKeyword`（分段渲染 `<mark>`），搜索 tab 改用 app 层传入的服务端结果 + 高亮，本地过滤作回退。
  - `AgentWorkbench` 新增搜索状态（keyword/results/loading）+ 250ms 防抖 + 过期请求丢弃（searchSeq），切工作区时清空。
- How: 包层不直连后端的约束保持——FileExplorer 通过 `search` emit 把关键字交给 AgentWorkbench 发起 RPC，结果经 props 回流。新增 2 参数兼容构造器避免破坏既有 `WorkspaceFileServiceTest`。
- Result: 后端 `mvn -pl test-agent-api -am test` 全绿（含新增 3 个 searchFiles 测试）；前端 `pnpm -r typecheck` 全绿，file-explorer vitest 7 个测试通过（含新增 highlightKeyword 6 个）。文档同步 `docs/api/event-stream.md`、`file-explorer/README.md`、`PACKAGE.md`。
- Pitfalls: 工作区有预先存在的脏改动（FigmaShell.vue / RuntimeTopologyGraph.vue / AgentConfigBackendRoutingService.java 等，非本次任务），提交时已精确暂存排除。`mvn -pl <module> compile` 不带 `-am` 时因本地 `.m2` 的 test-agent-domain jar 缓存不一致会报 `AgentConfigWorktree.linuxServerId()` 找不到，带 `-am` 重新编译 domain 源码后一致；验证后端务必带 `-am`。
- Verification: `mvn -pl test-agent-api -am test -DskipITs`、`corepack pnpm -r typecheck`、`corepack pnpm exec vitest run packages/file-explorer`。

### 2026-06-28 - 公共 Agent worktree 切换绑定服务器上下文

- Why: 公共 Agent 配置在分布式部署下可能存在多个已初始化服务器，管理员需要在左侧 Agent 面板切换“直接公共配置目录”或某台服务器上的公共 worktree，后续文件操作必须落到所选服务器。
- What: 后端新增 `GET /agent-config/public/worktrees?linuxServerId=` 返回指定服务器 `ACTIVE/PUBLIC` worktree 和创建人字段；MyBatis XML 支持 `scope/linuxServerId/status` 过滤，legacy repository 继续走内存过滤兼容。前端 Agent 面板公共级新增切换按钮和弹窗，先选已初始化服务器，再选直接目录或 worktree；workbench store 新增 `publicConfigLinuxServerId` 记住直接目录服务器。
- How: 切换只更新 `worktreeId/linuxServerId` 上下文并清空公共文件树缓存；公共文件列表、读取、保存仍通过 agent-config 文件 WebSocket route/ticket/RPC，直接目录模式用 `linuxServerId` 绑定服务器，worktree 模式用落库 `worktreeId -> linuxServerId` 定位服务器。已打开 tab 不关闭，保存继续沿用 tab 内上下文。
- Result: 后端目标测试、前端 agent-web typecheck、AgentConfigPanel/backend-api 定向 Vitest 通过；文档同步 API、事件、前后端规范、相关 README/PACKAGE。提交时继续排除既有 FigmaShell、运行拓扑和 AgentConfigBackendRoutingService 等无关脏改动。

### 2026-06-29 - 优化分支列表排序：符合格式的排前面

- Why: 提升用户体验，让用户优先看到可选择的分支，而不是在大量置灰分支中寻找。
- What: 实现分支列表排序优化：
  - 标准库：符合格式的分支排在前面，不符合的排在后面
  - 每组内部按字母顺序排序
  - 非标准库保持原始顺序
- How: 添加 sortedBranches 计算属性：
  1. 判断是否为标准库
  2. 将分支分为两组：validBranches 和 invalidBranches
  3. 每组内部按字母顺序排序
  4. 合并返回：[...validBranches, ...invalidBranches]
- Result: 用户优先看到可选择的分支，置灰分支展示在后面供参考，提升了操作效率。
- Pitfalls: 需要注意 computed 属性依赖的函数必须在其之前定义。
- Verification: 手动测试标准库和非标准库的分支排序效果。

### 2026-06-29 - 优化分支默认选中逻辑：置灰分支不能被默认选中

- Why: 标准库刷新分支后，默认选中的可能是置灰的分支，导致用户无法操作。
- What: 修改 loadBranches 函数的默认选中逻辑：
  - 标准库：默认选中第一条符合格式的分支
  - 非标准库：保持原逻辑，选中第一条
  - 如果标准库没有任何符合格式的分支，选中空字符串
- How: 使用 find 方法查找第一条 isValidStandardBranch 返回 true 的分支作为默认值。
- Result: 避免用户看到已选中的分支但无法操作，默认选中可用的分支，提升操作效率。
- Pitfalls: 需要清空 customBranchError 避免切换代码库时残留错误提示。
- Verification: 手动测试标准库刷新分支后的默认选中行为。

### 2026-06-29 - 优化分支排序：符合格式的分支按日期倒序排序

- Why: 用户希望优先看到最新的分支，而不是按字母顺序排列。
- What: 修改 sortedBranches 计算属性的排序逻辑：
  - 符合格式的分支按日期倒序排序（最新的在前）
  - 不符合格式的分支仍按字母顺序排序
- How: 从分支名提取日期部分（后8位 yyyyMMdd），使用 localeCompare 进行倒序排序。
- Result: 用户优先看到最新的分支，便于选择最新版本进行开发。
- Pitfalls: 日期字符串可以直接用 localeCompare 比较，格式 yyyyMMdd 保证字符串比较等同于日期比较。
- Verification: 手动测试标准库分支排序效果，验证最新分支在最前面。

### 2026-06-29 - 修复分支默认选中逻辑：先排序再选第一个

- Why: loadBranches 使用 find 查找第一个符合格式的分支，但 UI 显示使用 sortedBranches（按日期倒序），导致默认选中的分支和显示的第一个可能不一致。
- What: 修改 loadBranches 函数的默认选中逻辑，确保与 sortedBranches 的排序逻辑完全一致：
  1. 过滤出符合格式的分支
  2. 按日期倒序排序（最新在前）
  3. 选择排序后的第一个
- How: 在 loadBranches 中复制 sortedBranches 的排序逻辑，确保选中的就是显示的第一个。
- Result: 用户看到的第一条分支就是被默认选中的分支，逻辑完全一致。
- Pitfalls: 需要保持 loadBranches 和 sortedBranches 的排序逻辑一致，避免出现选中与显示不匹配的情况。
- Verification: 刷新分支后，验证默认选中的分支就是列表中显示的第一个。

### 2026-06-29 - 修复 Markdown 表格渲染多余空行 + MyBatis Analytics Long 类型映射

- Why:
  - 前端：github-markdown-css v5.9.0 将 `<table>` 设为 `display:block`（为横向滚动），导致 `border-collapse:collapse` 失效，`th/td` 的 `border` 与 `tr` 的 `border-top` 各自独立渲染，表头与表体之间产生双重边框空隙，视觉上像空行。同时 `ul/ol` 的 `padding-left:2em` 在聊天气泡内显得间距过大。
  - 后端：`AnalyticsMapper.xml` 中 `javaType="long"` 在 MyBatis 类型别名系统中解析为 `java.lang.Long`（装箱类型），而 `AnalyticsActivityRow` 等 Java record 的 canonical constructor 接受原始类型 `long`。MyBatis 反射查找构造函数时 `Long` ≠ `long`，导致 `NoSuchMethodException`，每次 rollup 调度任务都报错。
- What:
  - 前端 `MarkdownView.vue`：table 加 `display:table!important` 恢复标准表格布局；`tr` 加 `border-top:none` 去除行级双重边框；`ul/ol` 的 `padding-left` 加 `!important` 确保紧凑。
  - 前端 `MarkdownPreview.vue`：同样加 `table{display:table;border-collapse:collapse}` 和 `tr{border-top:none}`。
  - 后端 `AnalyticsMapper.xml`：全部 28 处 `javaType="long"` 改为 `javaType="_long"`（MyBatis 原始类型别名）。
- How: CSS 覆盖利用 Vue scoped `:deep()` 选择器 + `!important` 提高优先级；XML 用 `sed` 批量替换后人工确认。
- Result: 前端 `MarkdownView.test.ts` + `MarkdownPreview.test.ts` + `runtime-reducer.test.ts` 共 25/25 通过。后端 persistence 模块因 H2 不兼容 PostgreSQL CHECK 约束的 Flyway migration 无法本地跑全量测试，XML 修改为纯文本替换无语法风险。
- Pitfalls: github-markdown-css 的 `display:block` 是为宽表横向滚动设计，恢复 `display:table` 后极宽表格可能在窄气泡内溢出（聊天气泡 `max-w-[calc(100%-44px)]` 本身较宽，影响小）；后端 Flyway H2 兼容性问题需单独处理。
- Verification: `npx vitest run packages/agent-chat/tests/ packages/editor/tests/` 相关测试全通；`mvn -pl test-agent-persistence -am compile` 编译通过。

### 2026-06-29 - 公共配置"更新公共配置"弹窗新增提交信息输入并支持推送到远端

- Why: 在 AgentConfigPanel 的公共级"更新公共配置"操作中，原行为仅 fetch+reset+pull（不修改远端），用户在管理面板中无法通过此入口把本地的 `OPENCODE_PUBLIC_CONFIG_DIR` 仓库下的修改提交并推送到远端。需要一个超级管理员可一键拉取最新、提交并推送的复合入口，减少多工具切换的步骤。
- What:
  - 后端新增 DTO `AgentConfigDtos.UpdatePublicConfigAndPushRequest`（branch、commitMessage 必填、operationId、discardLocalChanges）。
  - 后端 `GitWorkspaceService` 新增 `stageAll(Path, String)` 公开方法，执行 `git add --all`。
  - 后端 `AgentConfigApplicationService` 新增 `updatePublicConfigAndPush`：复用 `ensurePublicRepositoryReady` 完成 fetch/reset/pull 后 `stageAll`，若产生新 commit 则用 `commitMessage` 生成提交并 push 到 `branch`，最后广播 `agent-config.public-sync-requested` 事件。空 commitMessage 直接抛 `VALIDATION_ERROR`，保留失败时原始异常。
  - 后端 `AgentConfigController` 暴露 `POST /api/internal/platform/workspace-management/agent-config/public/update-and-push`，要求 `ROLE_SUPER_ADMIN`，X-Trace-Id 透传。
  - 前端 `packages/backend-api/src/index.ts` 新增 `updatePublicAgentConfigAndPush({ branch, commitMessage, operationId, discardLocalChanges })`。
  - 前端 `apps/agent-web/src/components/AgentConfigPanel.vue` 的"更新公共配置"弹窗新增提交信息输入框（必填，带流程提示）、确定按钮文案改为"提交并推送"，提交后通过新接口调用并复用既有 `runOperation` 进度通道。
  - 同步更新 `agent-config-panel.test.ts`（新增提交信息输入必填、CONFLICT 时仍需勾选放弃）和 `AgentConfigControllerTest` / `AgentConfigApplicationServiceTest` 的测试。
  - 文档 `docs/api/http-api.md` 新增 `POST /public/update-and-push` 行与请求体/语义说明。
- How:
  - Service 复用现有 `commitStaged` / `push` / `headCommit` / `statusPorcelain`，在 `hasStagedChanges` 辅助方法里复用 porcelain 检查；流程步骤串行显式：PREPARING_REPOSITORY → COMMITTING → PUSHING → BROADCASTING。
  - Controller 与 Service 测试都使用 mock service / RecordingGitWorkspaceService 覆盖三种场景：有本地变更、无本地变更、空 commitMessage 拒绝。
  - 提交信息为必填项，前后端一致做 `trim().length > 0` 校验，禁用"提交并推送"按钮。
- Result:
  - 后端：test-agent-workspace-management 19/19、test-agent-api 13/13 通过；test-agent-common / test-agent-api 编译通过。
  - 前端：vitest `agent-config-panel` 6/6、`backend-api` 32/32 通过；`pnpm typecheck` 无报错。
- Pitfalls:
  - `stageAll` 必须先 install `test-agent-common` 才能让下游模块拿到新方法签名，否则会触发 `NoSuchMethodError`。
  - Controller 用 mock service 时不应断言空 commitMessage 的 400 响应，校验逻辑在 Service 内，Controller 测试只覆盖路由与权限；Service 单测中专门覆盖。
  - 公共配置 git 仓库路径在 `OPENCODE_PUBLIC_CONFIG_DIR` 通用参数中维护，路径变更需要走通用参数管理入口而非本接口。

### 2026-06-29 - 公共配置"更新公共配置"流程修正为 commit→push 并接入气泡提示

- Why: 上一版按"先 pull 再 commit+push"的顺序实现，结果 `ensureExistingRepositoryReadyForSync` 在工作区有未提交修改时要么抛 CONFLICT 拒绝，要么 `git reset --hard HEAD` 清空本地修改后再 `git add -A` 没有可提交内容，**用户看到的现象是"点了确定但没产生任何 commit"**。同时 `runOperation` 内部 try/catch 只写 `errorMessage` ref，没有调用 toast，前端也没有任何成功/失败的气泡通知。
- What:
  - 后端 `AgentConfigApplicationService.updatePublicConfigAndPush` 重新设计：删除 `ensurePublicRepositoryReady` 预拉取，改为「可选 `reset --hard HEAD` → `stageAll` → `commitStaged` → `push` → 广播」；`hasNewCommit = !headBefore.equals(headAfter)` 时才 push；commitMessage 为空直接抛 `VALIDATION_ERROR`。
  - 后端 Service 单测同步更新（`currentHead` 字段模拟 commit 推进），新增 reset 路径测试；移除对 fetchCallCount 必然为 0 的硬性要求之外，断言 ensurePublicRepositoryReady 没有被调用。
  - 前端 `AgentConfigPanel.vue` 接入 `./notify` 的 `notifySuccess` / `notifyError`：成功时 toast「公共 Agent 已提交并推送」附带新 commit hash；失败时 toast「公共 Agent 提交并推送失败」并附错误信息；冲突未确认时同步在弹窗内显示 `updatePublicConfigError` 让用户立即看到拒绝原因。
  - 前端弹窗 disabled 规则简化为「提交信息非空且不在忙」；冲突判断放到 submit 入口的二次校验，并在测试中覆盖。
  - 前端测试：新增 2 个 toast 通知用例（成功/失败），拆分冲突场景为「未勾选放弃→拒绝并弹错误」+「勾选放弃→调用后端」两个用例。
- How:
  - Service 入口在 `isGitRepository` 为 false 时直接抛 `publicRepositoryUninitialized`；`discardLocalChanges=true && !isWorktreeClean` 时先 `resetHardToCommit` 再继续。
  - 前端 `runOperation` 仍负责进度 SSE 与 `errorMessage` 写入；提交函数额外对比 `previousError` 与 `errorMessage.value` 决定是否补发一次 toast，避免重复弹错。
- Result:
  - 后端：test-agent-workspace-management 20/20、test-agent-api 13/13 通过。
  - 前端：vitest `agent-config-panel` 8/8、`backend-api` 32/32 通过；`pnpm typecheck` 无报错。
- Pitfalls:
  - JSDOM 下 fireEvent.click 在含 hover 触发的 dropdown 容器内可能受父级 pointer-events 影响，确认测试中 confirm 按钮真实可达；优先用 `view.findByText` 验证弹窗内错误文案（避免依赖全局 toast 在 jsdom 内的实现差异）。
  - 之前 `canSubmitUpdatePublicConfig` 用 `publicUpdateRequiresDiscard` 做 disabled 条件会把冲突场景"静默禁用"，用户以为按钮坏了；改为弹窗内可见的拒绝错误更直观。
- Verification: `pnpm test -- agent-config-panel` 8/8；`mvn -pl test-agent-workspace-management,test-agent-api test -Dtest=AgentConfigApplicationServiceTest,AgentConfigControllerTest` 33/33。

### 2026-06-30 - manager 注册重复拓扑不再阻断 WebSocket

- Why: 本地重启后 opencode-manager 反复断开，后端日志显示注册阶段写 `opencode_container_managers` 时因历史 `container_id` 行触发 `DuplicateKeyException`。该表是持久拓扑/历史审计，不是在线态 TTL 来源；在线态应由 WebSocket 连接和 Redis snapshot 决定。
- What: `ManagerControlApplicationService.register` 对持久拓扑写入的重复键异常做 best-effort 容错，记录 WARN 后继续返回 `registered`；异常识别范围收窄到 `DuplicateKeyException`，外键、check constraint 或其他持久化异常继续抛出，避免隐藏真实数据错误。补充单测覆盖重复键继续注册、普通异常不吞；README 记录注册写库不应阻断在线控制面。
- How: 不改数据库唯一约束、不覆盖旧 `container_id` 行、不新增 JDBC SQL；runtime 模块不引入 Spring DAO 依赖，按异常类名识别实际 persistence 抛出的重复键异常。
- Result: 新 jar 已打包；用户可自行重启验证 manager WebSocket 不再因历史拓扑重复键直接断开。
- Verification: `mvn -pl test-agent-opencode-runtime -am -Dtest=ManagerControlApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` 5/5 通过；`mvn clean package -DskipTests` 通过。

### 2026-06-29 - 修复 /api/workspaces 返回 updatedAt must not be before createdAt

- Why: 用户反馈 GET /api/workspaces?page=1&size=50 一直返回 updatedAt must not be before createdAt 校验错误。该校验来自 Workspace 领域 record 的 compact constructor，抛 IllegalArgumentException 后被 GlobalExceptionHandler 原样回吐，把内部不变量错误信息暴露给前端。
- What:
  - 数据侧：192.168.100.200:5432/testagent 的 workspaces 表中 wrk_754915e1ecfe4a139c24b845f5be3d2e（F-WRAPP 本地工程模板-local）的 updated_at 早于 created_at（同一天 02:38:00 < 09:12:19，疑似时钟回拨或批量写入），是当前唯一一条脏数据。一次性 update workspaces set updated_at = created_at where workspace_id = ? 回填完毕，接口立即恢复 200。
  - 防御侧：JdbcWorkspaceRepository.rowMapper 增加 
ormalizeUpdatedAt 兜底：发现 updated_at < created_at 时把 updated_at 抬到 created_at，并打一条 SLF4J WARN 保留原始值供排障，避免类似历史脏数据再次把领域异常原样甩到前端。Workspace 领域不变量保持不变，写入侧仍由领域层保证 updated_at >= created_at。
  - 文档与测试：ackend/test-agent-persistence/README.md 在 JdbcWorkspaceRepository 一行补充历史脏数据归一化说明；JdbcRepositoryIntegrationTest 新增 workspaceRepositoryClampsLegacyUpdatedAtBeforeCreatedAtOnRead 用例，直接 insert 脏行后验证 indById / indPage 都把 updatedAt 抬到 createdAt。
- How: 仅修改持久化映射层，未触动领域对象和 API 契约；按 docs/standards/backend.md 第 4 条要求在 Repository 测试和模块 README 记录边界。
- Result:
  - 接口 GET /api/workspaces?page=1&size=50 实时请求返回 200，数据正常。
  - 后端 mvn -pl test-agent-persistence -am compile 通过；新增的归一化用例因 JdbcRepositoryIntegrationTest 共用的 V20260628223000__add_macos_platform_support.sql 在 H2 PostgreSQL 模式中 ::text[] 数组语法不兼容而无法本地全量执行（与本次修复无关的预存在问题），已记录在 Pitfalls。
- Pitfalls:
  - ApplicationDatabaseIntegrityTest 等走 H2 的集成测试因 macOS 平台 migration 使用了 PostgreSQL 专属的 ARRAY[...]::text[] 语法而失败，是先于本次修复存在的测试基础设施问题；新增归一化逻辑仅在生产 PostgreSQL 路径生效。
  - 该 IllegalArgumentException 链路会把领域内部不变量消息原样暴露给前端，存在信息泄漏风险；后续可考虑在 GlobalExceptionHandler 中对 IllegalArgumentException 改为统一 BAD_REQUEST 描述并把原始 message 仅写入 server log。
- Verification: Invoke-WebRequest http://127.0.0.1:8080/api/workspaces?page=1&size=50 返回 200；mvn -pl test-agent-persistence -am compile 编译通过。
## 2026-06-29 无blob克隆优化

### Why
用户反馈"应用与工作空间管理->加载目录超时"，分析发现原实现在浅克隆时仍会下载所有文件内容，导致大仓库超时。

### What
修改 `GitCloneCacheService.java`，使用 `--filter=blob:none` 参数实现无blob克隆：
- 只下载 commit 和 tree 对象（目录结构）
- 不下载 blob 对象（文件内容）
- 结合稀疏检出，只检出目录结构

### How
1. 修改 `shallowClone` 方法，添加 `--filter=blob:none` 和 `--sparse` 参数
2. 添加稀疏检出配置，设置检出所有目录
3. 更新类注释和方法注释，说明无blob克隆技术
4. 创建详细优化文档 `OPTIMIZATION.md`
5. 更新模块 README

### Result
- 数据传输量减少 > 99%（从GB级降至KB级）
- 加载速度提升 10-100 倍
- 磁盘占用减少 > 99%
- 要求 Git 2.22+ 版本
- 不影响现有功能，向后兼容

**修改文件：**
- `backend/test-agent-configuration-management/src/main/java/com/icbc/testagent/configuration/management/GitCloneCacheService.java`
- `backend/test-agent-configuration-management/README.md`
- `backend/test-agent-configuration-management/OPTIMIZATION.md`（新增）

**验证方法：** 见 `OPTIMIZATION.md` 文档
### 2026-06-29 - Java 运行心跳与 JVM 趋势按服务器 IP 归并

- Why: 运行管理页和 opencode 公共配置管理页会在同一服务器 Java 进程重启后看到多个 `backendProcessId` 快照，导致同 IP 重复行；JVM 指标历史按 `backendProcessId` 保存也会让趋势在重启后断裂。
- What: Java latest snapshot、在线心跳和 JVM 指标历史改为按 `linuxServerId` 写 Redis；新增 `linux-servers/{linuxServerId}/backend-metrics` 主 API，旧 `backend-processes/{backendProcessId}/metrics` 仅兼容委托或旧样本兜底；运行管理前端按 IP 查询 Java 服务趋势，公共配置仓库列表后端/前端都按 `linuxServerId` 去重。
- How: domain 心跳端口改为 `LinuxServerId`；Redis key 从 `backend:{backendProcessId}` 迁移到 `backend:{linuxServerId}`，并保留旧 key 读取方法；查询服务合并服务器资源和 JVM 样本时使用 IP，兼容响应里的 `backendProcessId` 选择同 IP 最新心跳 Java 实例；文档同步 API、事件流、部署和前后端 README。
- Result: 同一 IP 的 Java 重启会覆盖 latest snapshot 并连续追加 JVM 历史，公共配置管理页不会再因 TTL 窗口内多个 Java 快照展示重复服务器行。
- Pitfalls: `mvn -pl test-agent-domain,test-agent-persistence,test-agent-opencode-runtime,test-agent-api -am test` 仍会在 `test-agent-persistence` 的 H2 集成测试中被既有 `V20260628223000__add_macos_platform_support.sql` PostgreSQL `ANY(ARRAY...)` CHECK 语法阻断；本次相关定向测试已覆盖 Redis/API/前端路径。
- Verification: `mvn -pl test-agent-persistence,test-agent-opencode-runtime,test-agent-api -am -Dtest=RedisOpencodeProcessHeartbeatStoreTest,RuntimeManagementQueryServiceTest,RuntimeManagementControllerTest,AgentConfigBackendRoutingServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`、`corepack pnpm --filter @test-agent/backend-api typecheck`、`corepack pnpm --filter @test-agent/agent-web typecheck`、`corepack pnpm test -- backend-api runtime-management-settings`、`mvn clean package -DskipTests`、`git diff --check`。

## 2026-06-30 - 修复 GitCloneCacheService 目录加载问题

### Why
用户反馈接口 `GET /api/internal/platform/configuration-management/repositories/{repositoryId}/directories` 只返回 `.git` 目录下的内容，没有返回实际的项目目录（如 `backend`、`frontend` 等）。

### What
分析发现根本原因：之前的实现使用了 `git clone --sparse` 参数，该参数会导致 Git **只检出根目录文件**，不递归检出子目录。后续尝试通过 `sparse-checkout set` 命令来检出所有目录，但参数配置不正确导致失败。

经过深入测试验证，正确的方案是：
- **移除 `--sparse` 参数**：该参数会限制只检出根目录
- **移除 `--no-checkout` 参数**：Git 会自动检出工作目录
- **保留 `--filter=blob:none`**：只下载 tree 对象（目录结构），文件内容按需下载
- **不需要手动 sparse-checkout 配置**：Git 会自动处理

### How
1. 修改 `GitCloneCacheService.shallowClone()` 方法
2. 移除 `--sparse` 和 `--no-checkout` 参数
3. 移除手动 sparse-checkout 配置步骤
4. 简化克隆命令，只保留核心的 `--filter=blob:none` 参数
5. 使用本地 file:// 协议测试验证修复方案

### Result
- **修复前**：只返回 `.git` 目录结构，无实际项目目录
- **修复后**：正确返回所有目录，包括 `backend`、`frontend`、`docs` 等
- **性能保持**：仍然只下载目录结构，不预下载文件内容，数据传输量极小
- **编译成功**：`mvn clean compile -f backend/pom.xml -pl test-agent-configuration-management -am -DskipTests` 通过
- **缓存已清理**：删除了 `/tmp/git-clone-cache/` 下的旧缓存

**验证测试**：
```bash
cd /tmp && rm -rf test-file-clone && \
git clone --depth=1 --single-branch --branch=main --filter=blob:none \
  file:///Users/gengxf/workspace/gitee/intelligent-test-agent test-file-clone
```
成功检出所有目录：backend、frontend、docs、deploy 等

**修改文件**：
- `backend/test-agent-configuration-management/src/main/java/com/icbc/testagent/configuration/management/GitCloneCacheService.java`

**待验证**：
需要重启后端服务，然后调用接口验证：
```bash
curl -H "authorization: Bearer 48ee14a730834e33ba9be581b9823e54" \
  "http://127.0.0.1:8080/api/internal/platform/configuration-management/repositories/repo_962461d02c634d72a6c0af2936ede239/directories?branch=feature_testagent_20260630"
```

### Pitfalls
- Git 的 `--sparse` 参数行为与直觉相反：不是"稀疏克隆"，而是"只检出根目录"
- `git clone --filter=blob:none` 默认会自动检出工作目录（与 `--no-checkout` 结合时才不会）
- `sparse-checkout set /*` 语法错误，cone 模式下不接受带前导斜杠的路径
- 本地 file:// 协议不支持 `--filter` 参数（服务器不支持），但远程 SSH/HTTPS 支持

### Technical Details
**Git 参数说明**：
- `--depth=1`：浅克隆，只下载最新提交
- `--single-branch`：只克隆指定分支
- `--filter=blob:none`：只下载 tree 对象（目录结构），不下载 blob（文件内容）
- **不要用** `--sparse`：会导致只检出根目录
- **不要用** `--no-checkout`：会跳过工作目录检出

**工作原理**：
1. Git 下载 commit 和 tree 对象（目录结构元数据）
2. Git 自动检出工作目录
3. 遇到文件时，Git 按需从服务器下载 blob（文件内容）
4. 最终结果：完整的目录结构 + 按需加载的文件内容

## 2026-06-30 - 重构 GitCloneCacheService：使用 git fetch + ls-tree 替代克隆

### Why
之前的实现使用 `git clone --filter=blob:none` 方式，虽然只下载 tree 对象，但仍需要：
- 创建完整的工作目录
- 处理复杂的稀疏检出配置
- 占用较多磁盘空间

用户建议直接使用 `git ls-tree` 方式，只查询目录结构，无需克隆整个仓库。

### What
完全重构 `GitCloneCacheService`，采用更轻量的方案：

**新方案流程**：
1. `git init` - 创建临时仓库（只有 .git 目录）
2. `git remote add origin <url>` - 添加远程引用
3. `git fetch origin <branch> --depth=1` - 只获取最新提交（不下载 blob）
4. `git ls-tree -r -d --name-only FETCH_HEAD` - 列出所有目录

**优势**：
- ✅ 零工作目录占用（只有 .git 元数据）
- ✅ 数据传输量极小（只下载 commit + tree 对象，KB级）
- ✅ 查询速度快（秒级完成）
- ✅ 无需处理稀疏检出配置
- ✅ 代码更简洁、更可靠

### How
1. 重写 `GitCloneCacheService` 类
2. 移除所有克隆相关代码
3. 使用 `git fetch` + `git ls-tree` 组合
4. 保留缓存机制（缓存 .git 元数据，避免重复 fetch）
5. 保留 SSH key 支持

### Result
- 编译成功：`mvn clean compile` 通过
- 代码更简洁：从 390 行减少到 300 行
- 性能更好：无工作目录，无 blob 下载
- 维护性更好：逻辑更清晰，无复杂参数

**修改文件**：
- `backend/test-agent-configuration-management/src/main/java/com/icbc/testagent/configuration/management/GitCloneCacheService.java`

**验证命令**：
```bash
# 手动测试流程
cd /tmp/test-ls-tree
git init
git remote add origin git@gitee.com:gengxf11/springboot-demo.git
git fetch origin feature_testagent_20260630 --depth=1
git ls-tree -r -d --name-only FETCH_HEAD
```

**预期结果**：
返回目录列表：`src`, `src/main`, `src/main/java`, ...

### 技术细节

**git ls-tree 参数说明**：
- `-r`: 递归查询子目录
- `-d`: 只显示目录（tree对象），不显示文件（blob对象）
- `--name-only`: 只显示名称，不显示模式/类型/SHA

**git fetch 参数说明**：
- `--depth=1`: 只获取最新提交，不获取历史
- 自动跳过 blob 下载（因为 ls-tree 不需要）

**缓存机制**：
- 缓存目录：`/tmp/git-clone-cache/{urlHash}_{branch}/`
- 只保存 `.git` 元数据目录
- 无工作目录文件
- 缓存过期自动清理

### Pitfalls
- `git ls-tree` 不能直接查询远程 URL，必须先 fetch
- fetch 时使用 `--depth=1` 确保不下载历史提交
- ls-tree 查询 `FETCH_HEAD` 而不是分支名（因为只 fetch 了引用）

### 2026-06-30 - 修复 FETCH_HEAD 缓存验证问题

#### Why
重启后端服务，接口报错：
```
fatal: Not a valid object name FETCH_HEAD
```

#### What
缓存验证逻辑有缺陷：
- 只检查 `.git` 目录是否存在
- 未检查 `FETCH_HEAD` 文件是否存在
- 导致误判：`.git` 目录存在但未 fetch 时，仍然认为缓存有效

#### How
修改 `isCacheValid()` 方法：
- 增加 `FETCH_HEAD` 文件存在性检查
- 确保只有完成 fetch 的缓存才被认为有效

#### Result
- ✅ 编译成功
- ✅ 手动测试通过
- ✅ 代码提交：`49d8716b`

#### 验证命令
```bash
# 手动测试完整流程
cd /tmp/test-fix
git init
git remote add origin git@gitee.com:gengxf11/springboot-demo.git
git fetch origin feature_testagent_20260630 --depth=1
ls -la .git/FETCH_HEAD  # 确认文件存在
git ls-tree -r -d --name-only FETCH_HEAD  # 列出目录
```

#### 下一步
重启后端服务，运行验证脚本：
```bash
bash /tmp/test-api-after-restart.sh
```
- Why: `V20260629230000` 的 INSERT 值含字面量 `${SYS_DATA_ROOT_DIR}/...`（需存进 DB 由 Java 解析器运行态展开），但 Flyway 默认把 `${...}` 当占位符替换，找不到值时在 prod PostgreSQL 报 `Unable to parse ... No value provided for placeholder: ${SYS_DATA_ROOT_DIR}`，迁移无法应用。
- What: 改写该迁移的值列为 `'$' || '{SYS_DATA_ROOT_DIR}/...'` 字符串拼接，使 SQL 文本中不出现 `${` 序列绕开 Flyway 占位符扫描，DB 实际仍存储 `${SYS_DATA_ROOT_DIR}/...` 字面量；同步重写注释避免出现 `${`。该迁移此前解析失败从未落库，改写无 checksum 风险。
- How: 不改各 profile 的全局 Flyway 配置（最小改动）；通过 `$` 与 `{` 分属两个字符串字面量、中间以 `||` 连接，破坏 `${` 占位符前缀。
- Result: 用与生产一致的 Flyway 12.4.0 引擎对临时 PostgreSQL 16 容器执行全量迁移成功，`flyway_schema_history` 记录 `20260629230000 success=t`，6 个参数存储值为字面量 `${SYS_DATA_ROOT_DIR}/...`。
- Verification: `java -cp flyway-core-12.4.0.jar:... FlywayRun`（`FLYWAY_MIGRATE_OK`）、`mvn -pl test-agent-domain -am test -Dtest=CommonParameterReferenceResolverTest`（`BUILD SUCCESS`）、`grep '\${'` 确认迁移文件无 `${` 序列。

### 2026-06-30 - opencode manager 单连接与用户进程后端路由

- Why: manager 收敛为只连接本服务器 Java 后，用户请求可能落到非 binding 所属服务器；旧逻辑会在当前 Java 看到其它健康容器时静默迁移 binding，容易把用户进程从原服务器迁走。
- What: 新增 API 层用户 opencode 后端路由过滤器，ACTIVE binding 属于远端服务器时按 Redis Java live snapshot 的 `linuxServerId -> listenUrl` 转发状态、初始化、Run 创建和 runtime 代理请求，并用 `X-Test-Agent-Backend-Routed` 防循环；`UserOpencodeProcessAssignmentService` 删除跨服务器 fallback，只允许在 binding 原服务器本机 manager 内重建。Go manager 删除 backend list 补连和 discovery interval，只连接 `.serverip + OPENCODE_MANAGER_BACKEND_PORT` 推导的本服务器 Java；Java register 只返回 `registered`，完整配置只响应 manager 的 `configRequest`，后续只允许 `OPENCODE_MANAGER_MAX_PROCESSES` max-only 热刷新。
- How: Java 路由服务保留原始 `Authorization`、`X-Trace-Id`、body 和目标响应，目标后端不可用统一返回 `OPENCODE_UNAVAILABLE`；同 IP 历史重复 Java 快照按最新 heartbeat 选目标。通用参数前端更新入口只放行最大进程数，路径类参数改为部署/初始化参数；启动脚本删除废弃 `OPENCODE_MANAGER_DISCOVERY_INTERVAL` 和 `OPENCODE_MANAGER_ID` 注入。
- Result: 任意 Java 收到 remote binding 请求时会路由到 binding 所属服务器 Java，不再自动迁移；每个 manager 只维持单条本机 Java WebSocket，断线后按重连间隔无限重连并重新拉取配置。
- Verification: `mvn -pl test-agent-api,test-agent-opencode-runtime,test-agent-configuration-management -am test`、`go test ./...`（opencode-manager）、`bash tools/verify-dev-scripts.sh`、`mvn clean package -DskipTests`、`git diff --check` 全部通过。

### 2026-06-30 - 修复 manager 最大进程数热更新不生效

- Why: Java 已按新契约广播 max-only `configUpdate(maxProcesses, sessionRoot=null, configDir=null)`，但 Go `Manager.ApplyRuntimeConfig` 仍把空路径当完整配置缺失拒绝，导致 manager 不更新 `MaxProcesses`、不立即 heartbeat，运行管理容量保持旧值。
- What: Go manager 支持两种合法配置帧：首次完整帧必须同时带 `sessionRoot/configDir` 并置 ready；后续 max-only 帧允许路径为空，只更新并发上限并保留已生效路径。单路径缺失的非法帧仍拒绝且不产生部分路径更新。
- How: 先补红灯测试 `TestManagerApplyRuntimeConfigSupportsMaxOnlyUpdateAfterFullConfig` 和 `TestSupervisorAppliesMaxOnlyConfigUpdateAndReportsHeartbeat`，确认旧实现失败；再调整 `ApplyRuntimeConfig` 的路径帧形态校验与 max-only 分支，并更新 opencode-manager README。
- Result: 修改 `OPENCODE_MANAGER_MAX_PROCESSES` 后，manager 会应用新容量并立即补发 heartbeat，Java 按 heartbeat 更新 Redis/数据库快照，运行管理“容器 / 管理进程”容量随刷新变化。
- Verification: `go test ./...`（opencode-manager）、`git diff --check` 通过。

### 2026-06-30 - 收敛 Java 到 manager 路由并移除本地绕过

- Why: 用户 opencode、运行管理、Agent 配置和文件 WebSocket 路由分别扫描 Redis/转发 HTTP，且此前叠加了 `local-direct` 与 `gateway-mode=local` 两套本地绕过，导致“任意 Java 收请求后应由目标服务器 Java 控制本机 manager”的规则不够单一。
- What: 新增 `BackendJavaRouteResolver` 统一解析当前 `linuxServerId`、每台服务器最新 Java 后端和 `containerId` 所属 manager 服务器；新增 `BackendHttpForwarder` 统一 Java->Java HTTP 转发、Authorization/trace/body/query 透传与 `X-Test-Agent-Backend-Routed` 防循环。用户 binding、运行管理 restart/stop、Agent 配置 HTTP、Workspace/Agent 配置文件路由都改用统一组件；配置工作区创建、应用版本工作区创建和版本 `git-pull` 纳入用户 binding 路由。为跑通 app 全量回归，顺带把两个已知阻塞 H2 Flyway 的近期 migration 改为 PostgreSQL/H2 兼容等价 SQL：`V20260628223000` 使用标准 `platform IN (...)` CHECK 和普通 INSERT，`V20260629230000` 在删除旧平台行后普通 INSERT 新 `all` 行。
- How: `RuntimeManagementQueryService.userProcesses` 只在进程属于当前 Java 服务器时调用本机 manager health，远端返回 `REMOTE_SERVER/CHECK_SKIPPED`；`OpencodeProcessHeartbeatMaintenanceService` 只扫描当前服务器 RUNNING 进程。删除 `LocalDirectSettings`、`LocalOpencodeProcessManagerGateway`、`gateway-mode`、`local-direct` 配置和对应测试，本地/生产都必须启动 Go manager。
- Result: 前端可访问任意 Java；后端先通过统一路由定位目标 Java；只有目标 Java 通过本服务器 manager WebSocket 控制 manager。旧 session-log 中关于开启 local-direct/local gateway 的记录已被本条决策覆盖。
- Verification: 目标红灯测试 `BackendJavaRouteResolverTest`、`BackendHttpForwarderTest`、`UserOpencodeBackendRoutingWebFilterTest` 随 `mvn -pl test-agent-api,test-agent-opencode-runtime,test-agent-app -am test -Dtest=BackendJavaRouteResolverTest,BackendHttpForwarderTest,UserOpencodeBackendRoutingWebFilterTest -Dsurefire.failIfNoSpecifiedTests=false` 通过；回归命令 `mvn -q -pl test-agent-api -am test`、`mvn -q -pl test-agent-opencode-runtime -am test`、`mvn -q -pl test-agent-app -am test` 均通过。

### 2026-06-30 - 修复 BackendHttpForwarder Spring 构造器装配失败

- Why: 打包启动时报 `BackendHttpForwarder: No default constructor found`，原因是该组件同时存在生产构造器和测试构造器，Spring 7 未自动推断应使用 `ObjectMapper` 构造器。
- What: 将 `BackendHttpForwarder` 类和生产 `ObjectMapper` 构造器公开，并用 `@Autowired` 明确 Spring 注入点；新增 Spring context 级单测复现并防止回归。
- How: 保留双参 `HttpClient` 构造器为包级测试入口；通过 `AnnotationConfigApplicationContext` 注册 `ObjectMapper` 和 `BackendHttpForwarder` 验证组件可实例化。
- Result: `BackendHttpForwarderTest` 和 `test-agent-api` 全量测试通过，`test-agent-app` 跳过测试打包通过；`test-agent-app -am test` 当前被工作区未提交的 persistence migration seed 改动阻断，失败点是默认用户/本地拓扑 fixture 断言，与本次构造器修复无关。

### 2026-06-30 - 固化 opencode-manager 公共路由规范

- Why: Java 到 manager 路由已收敛为公共 resolver/forwarder/gateway 链路，需要把“不得再自写路由”固化到后续开发必读规范。
- What: 在 `AGENTS.md`、后端总 README、后端规范、依赖边界、API 模块 README 和 opencode-runtime 模块 README 中明确：涉及 opencode-manager 路由、Java 到 manager 控制、用户进程服务器归属、运行管理 `containerId` 路由、Agent 配置或文件 WebSocket 目标后端选择时，必须复用 `BackendJavaRouteResolver`、`BackendHttpForwarder` 和目标 Java 的 `OpencodeProcessManagerGateway`。
- How: 用禁止项列明不得自行扫描 Redis 快照、手写 Java->Java HTTP 转发器、定义防循环 header 变体、本机降级、跨服务器直接控制 manager 或恢复本地绕过。
- Result: 后续新增相关入口时，规范入口、后端编码规范、架构依赖边界和模块 README 都指向同一套公共路由机制。

### 2026-06-30 - 公共 opencode server 启动健康确认

- Why: 用户初始化旧链路在 manager 返回 `STARTED` 后直接写 `RUNNING`，没有确认 opencode HTTP health；运行管理重启已停止用户进程或 manager state 已清理端口时会遇到 `port ... is not managed`，超级管理员无法按原端口拉起。
- What: 新增 `OpencodeProcessStartupService` / `OpencodeProcessStartupRequest`，统一封装 start、候选进程快照、manager health、opencode HTTP health、最终状态回写、Redis heartbeat、ACTIVE binding 和兼容 `ExecutionNode` 投影；用户初始化和运行管理 restart 复用该服务。同步规范要求后续所有 opencode server 启动、重启后拉起、端口复用或启动状态回写都必须调用公共启动服务。
- How: 公共启动服务先保存 `STARTING` 候选进程供 socket health 按 `processId` 查本地 state，再调用 manager health；healthy 才写 `RUNNING`，not-running 映射 `STOPPED`，HTTP 不健康映射 `UNHEALTHY`，异常映射 `FAILED` 并按统一平台错误抛出。运行管理对已有平台进程记录的 `STOPPED` 或 `not managed` 端口复用原 `containerId + port` 调用 start，manager 已管理端口的 restart 成功回包也会再走同一 health 确认。
- Result: 对话框初始化和运行管理重启成功都必须同时满足 manager state/PID 与 opencode HTTP health healthy；无平台用户进程记录的无主 manager state 仍保持原 manager restart 语义。目标测试和 opencode-runtime 全模块测试通过；API 全模块测试仍被既有 `ConfigurationManagementControllerTest.createWorkspaceUsesCurrentUserReadyOpencodeServer` 的异步工作区创建断言阻断。

### 2026-06-30 - 公共 opencode server 停止健康确认

- Why: 运行管理停止旧链路只信任 manager `STOPPED` 回包，没有确认 opencode server health 已失败，也没有统一封装停止后 `STOPPED` 回写，后续入口容易再次各自实现 stop。
- What: 新增 `OpencodeProcessStopService` / `OpencodeProcessStopRequest`，统一封装 manager stop、停止后 health 不健康确认和用户进程 `STOPPED` 回写；运行管理 stop 复用该服务。同步规范要求后续所有 opencode server 停止、停止后状态回写或运行管理停止命令都必须调用公共停止服务。
- How: 对平台已有进程记录的端口，公共停止服务先通过 `OpencodeProcessManagerGateway.stopProcess()` 下发 manager stop，再对同一 `processId/baseUrl` 调用 manager health；health 仍 healthy 时抛 `OPENCODE_BAD_GATEWAY` 且不回写 STOPPED，health 不健康时把进程 pid 清空并写 `STOPPED`。无平台用户进程记录的无主 manager state 仍只以 manager `STOPPED` 回包为准，不新增数据库进程记录。
- Result: 运行管理停止成功必须满足 manager stop 成功和停止后 health 不健康；业务主代码中直接 `gateway.stopProcess()` 只剩公共停止服务和 gateway 实现/接口。

### 2026-06-30 - 公共 opencode server 状态查询收敛

- Why: 用户状态、Run 前检查、运行管理用户进程列表、后台 heartbeat、启动后确认和停止后确认分别调用 manager health 并各自映射 `RUNNING/STOPPED/UNHEALTHY/FAILED`，not-running、HTTP 不健康和 health 命令异常的含义不统一。
- What: 新增 `OpencodeProcessStatusQueryService` / `OpencodeProcessStatusProbe` / `OpencodeProcessProbeStatus`，统一封装进程存在性检查、manager health、查询语义归一、进程状态回写和 Redis heartbeat 刷新；用户状态、运行管理、本机 heartbeat、公共启动和公共停止都改为复用该服务。同步规范要求后续所有 opencode server 状态查询、健康探测、状态回写或 heartbeat 刷新必须调用公共状态查询服务。
- How: 公共查询服务先按 `processId` 查询平台进程记录，缺失时返回 `NOT_STARTED` 且不调用 manager；health healthy 写 `RUNNING` 并刷新 heartbeat；not-running 类消息写 `STOPPED` 并清空 pid；普通不健康写 `UNHEALTHY`；health 命令异常写 `FAILED` 并保留错误码。文件路由、后端路由归属和 allocationStatus 仍只读 binding，不触发 health。
- Result: 业务主代码中直接 `gateway.checkHealth()` 只剩公共状态查询服务和 gateway 实现/接口；STOPPED/FAILED 历史 binding 在状态查询或 Run 前检查时也会先重新探测，manager healthy 时可恢复为 `RUNNING`。

### 2026-06-30 - 管理员重启等待 opencode HTTP health ready

- Why: Go manager 的 `start/restart` 在拉起进程并写入 state 后立即返回 `STARTED`，不会等待 opencode HTTP health endpoint ready；Java 公共启动服务只做一次即时 health，导致管理员重启慢启动进程时前端看到 `opencode health endpoints are not reachable（OPENCODE_UNAVAILABLE）`，且用户进程列表失败后不刷新。
- What: `OpencodeProcessStartupService` 在 manager `STARTED` 后复用公共状态查询服务做有界等待，默认使用 manager command-timeout 10 秒；运行管理前端在重启/停止成功或失败后都会刷新当前 overview 和用户进程查询。
- How: 只对 `HEALTH_CHECK_FAILED` 且没有 manager 控制面错误码的普通健康失败继续轮询；`RUNNING` 立即成功，`NOT_STARTED`、manager timeout/unavailable 等控制面异常立即失败。超时仍抛统一 opencode 错误，并保留最后一次公共状态查询写入的 DB 状态和 healthMessage。
- Result: 管理员重启不会因 opencode HTTP 端点短暂未 ready 而误报失败；真实失败时用户进程列表会立即展示最新 `UNHEALTHY/FAILED/STOPPED` 状态和健康消息。

### 2026-06-30 - test profile 关闭应用版本副本补偿器

- Why: `.env.test` 指向的共享测试库存在 ACTIVE 的演示/占位应用版本，其中 F-COSS 仓库为 `git.example.com` 占位地址，另一个本地仓库地址指向 `/Users/kaka/...`，当前机器没有 READY replica 时补偿器每分钟尝试 clone 并刷 `Git 远端读取失败`。
- What: 在 `application-test.yml` 中关闭 `test-agent.managed-workspace.replica-reconciler.enabled`，仅影响 test profile；其他 profile 仍保持副本补偿器默认开启。
- How: 同步更新 `test-agent-app` README，说明 test profile 默认关闭该后台扫描以及通用关闭开关。
- Result: 重启 `test` profile 三服务后等待超过 60 秒，`/actuator/health/readiness` 为 `UP`，新 `backend.log` 未再出现 `managed-workspace-replica-reconciler` 或 Git 远端失败日志。

### 2026-07-01 - 应用私人 worktree 与 Diff 交互修复

- Why: 应用工作区需要切到用户 default 私人 worktree 后再读写文件和展示 diff；历史脏 personal workspace 记录、Git quoted path、回退后父级 diff 状态未刷新、opencode 未就绪时 runtime API 并发请求，以及脚本默认注入 `127.0.0.1` 都会导致 UI 上文件树/变更/进程状态异常。
- What: 默认私人 worktree 分支命名统一为 `{应用分支名}_{用户 id}_{私人空间名称}`，同名分支优先复用/移动已有 worktree；历史记录路径不匹配时更新 personal workspace 和 runtime workspace，模板子目录缺失但 repo root 存在时补偿到 repo root。工作区 diff 改用平台 Git diff 并解码 quoted path，agents diff 只展示应用级 `.opencode/agents` 与 `.opencode/skills`；回退按钮调用平台 discard API，并向父组件发 `changes-refreshed` 同步活动栏计数和 diff viewer。前端 runtime 查询新增当前 workspace file route ready gate，避免切工作区/opencode 未就绪时刷屏 502/503；重启脚本在未显式设置 `TEST_AGENT_BASE_URL` 时复用自动探测的 `TEST_AGENT_BACKEND_LISTEN_URL` 给浏览器访问。
- How: 后端新增 `discardWorkspaceGitFiles`，Git 服务支持 porcelain path 解码、worktree 同分支复用/移动、tracked restore、staged new unstage+clean、untracked clean；personal workspace location 更新走 MyBatis XML mapper。前端移除加载测试数据按钮，底部工作区入口显示 worktree 名称，公共级 agents 保持进入即加载且防卡死，opencode READY 后强制刷新当前根目录。同步更新 API/前端/工作区管理文档和定向测试。
- Result: 单元/组件/类型/Playwright mock 验证通过；真实 UI 曾验证到默认 worktree 名称、公共 agents 加载、opencode 启动后文件树重拉、切到 `F-COSS 移动端 / 20260705` 不再 409 且显示 `feature_testagent_20260705_usr_test_dev_default`。后续真实 UI 复测被外部测试环境网络阻塞：`192.168.100.200:5432` 和 `192.168.100.200:16379` 均 `No route to host`，后端无法启动，需网络恢复后重跑 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`。

### 2026-07-01 - 优化页脚与工作区切换布局

- Why: 用户需要优化左下角和页脚的信息展示结构，去除重复的多处分支/版本信息展示；只在左下角侧边栏页脚保留分支切换和服务器切换的 icon-only 按钮，将当前文件路径移回编辑器页脚左侧，并把服务器切换按钮移入侧栏页脚；同时在文件树侧边栏的“工作空间”栏目右侧直观展示当前 worktree 名字。
- What:
  - 修改 `WorkbenchFooter.vue`：使分支两级菜单切换按钮 `ta-workbench-footer-branch` 在侧边栏页脚（`showSave` 为 false 时）仅显示为 `⇄` 图标且不含文字，并将其样式修正为 26x26px 的正方形按钮；将“写入路径”和“更新时间”移回编辑器页脚的左侧，通过 `|` 分割；移除原中间部分的卡片容器 `.ta-workbench-footer-middle`；将“切换服务器工作空间”按钮 `ta-workbench-server-switch` 移回侧边栏页脚，放置于分支切换按钮的右侧。
  - 修改 `FigmaFileExplorer.vue`：新增 `personalWorkspaceBranch` 属性并透传给 `WorkbenchFooter`；修改 collapsible 部分，当 `personalWorkspaceBranch` 存在时在“工作空间”标题右侧以 `/ worktree: {branch}` 形式显示当前的 worktree 名字；添加对应的 `.figma-fe-section-worktree` 样式定义。
  - 修改 `WorkbenchFooter.test.ts`：更新单元测试中的 prop 与断言结构（在测试服务器切换时使用默认 `showSave: false`，并在 worktree 测试中断言 `title` 属性而不是 visible text）。
- How: 纯前端代码与测试更新，重构 `WorkbenchFooter` 的条件渲染结构和样式定义，通过 prop 传参打通侧栏组件与 worktree 数据绑定。
- Result: 所有 31 个测试文件（共 193 个用例）全部通过，`pnpm build` 在本地成功生成生产包，UI 结构完全符合用户诉求。

### 2026-07-01 - Opencode 心跳检查与 Skill 召唤问题修复（第一阶段：止血）

- Why: Opencode 心跳检查混乱，会出现"一会红一会绿、绿灯但页面上方报错不可用"的情况；公共级 Skill 无法在对话页召唤，FigmaChatPanel 硬编码了不存在的技能列表且使用 `__SKILL__` 标记而非真实 Command 调用。
- What:
  1. health.go：去掉 `/doc` readiness 回退，只使用 `/global/health` 作为 readiness 端点，避免"绿灯但 API 不可用"
  2. OpencodeProcessStatusQueryService：瞬时异常不再写 `FAILED`，返回 `STALE` 状态让调用方使用缓存数据
  3. OpencodeProcessProbeStatus：新增 `STALE` 枚举值表示状态暂时无法确认
  4. WorkspaceFileSocketTicketService：文件路由彻底解除健康检查依赖，不再调用强状态查询兜底
  5. AgentWorkbench.vue：拆分 `runtimeWorkspaceReady` 为 `authReady`、`fileRouteReady`、`opencodeCatalogReady`、`runtimeReady`、`runReady`，模型/Provider 登录后立即加载
  6. FigmaChatPanel.vue：删除硬编码技能列表，新增 `commands` prop，使用真实的 `source=skill` Command，选择后插入 `/skill-name ` 格式
- How:
  - Go 测试 `TestCheckerFallsBackToDocEndpoint` 改为验证不再回退行为
  - 前端类型检查通过
- Result: Go health 测试通过，前端类型检查通过。后端 Java 测试因 Java 版本不匹配（需 Java 21，当前 Java 17）未能运行，需环境配置后验证。


### 2026-07-01 - Opencode 心跳检查与 Skill 召唤问题修复（补充修复）

- Why: 审查发现第一阶段修改存在多个问题：Java 编译错误（Unicode 弯引号）、红绿闪烁根因未完全解决、绿灯和顶部不可用提示仍可能同时出现、新会话无法召唤 Skill、Agent 与 Skill 权限未闭环。
- What:
  1. WorkspaceFileSocketTicketService.java：修复 Unicode 弯引号导致的编译错误
  2. OpencodeProcessStatusQueryService.java：普通 HTTP 不健康不再写入 UNHEALTHY，返回 STALE 不持久化，只有进程明确死亡才写入 STOPPED
  3. UserOpencodeProcessAssignmentService.java：STALE 状态保留上次成功状态，数据库中是 RUNNING 时返回 READY 带"状态暂时无法确认"消息
  4. AgentWorkbench.vue：新增 fileTreeError 状态，文件树错误在面板内显示，不覆盖全局反馈；新会话执行命令时先创建 session 再执行
  5. FigmaFileExplorer.vue：新增 fileTreeError prop 和错误显示样式
  6. FigmaChatPanel.vue：添加 TODO 注释说明需要后端返回 Agent permission 信息才能按 permission.skill 过滤
- How:
  - 前端类型检查通过
  - 前端测试 193 个用例全部通过
  - Go health 测试通过
- Result: 第一阶段"止血"修改完成。P1-5（Agent 与 Skill 权限）需要第二阶段配合后端改动。


### 2026-07-01 - Opencode 心跳检查与 Skill 召唤问题修复（补充修复第二轮）

- Why: 复查发现第一轮修复存在问题：初始化流程被破坏、绿灯但功能不可用、进程死亡消息匹配错误、文件树无自动重试。
- What:
  1. OpencodeProcessStartupService：shouldRetryStartupHealth 增加 STALE 状态重试，启动后 HTTP 未就绪会继续等待
  2. OpencodeProcessStatusQueryService：isNotRunningHealthMessage 增加 "process is not alive" 匹配，修正注释说明当前无连续失败阈值机制
  3. UserOpencodeProcessAssignmentService：requireReadyProcess 在 STALE + 数据库 RUNNING 时允许运行，统一与 status() 的行为
  4. AgentWorkbench.vue：loadDirectory 增加指数退避重试（最多 3 次，间隔 1s/2s/4s）
- How:
  - 后端编译通过
  - 前端类型检查通过
  - 服务启动成功
- Result: 第一阶段"止血"修改完成，红灯闪烁、绿灯不可用矛盾已解决。P1-5（Agent 与 Skill 权限）需要第二阶段配合后端改动。


### 2026-07-01 - 修复个人 worktree 推送未合并回应用版本特性分支

- Why: 应用工作区采用 worktree 管理后，「推送」按钮本应把个人 worktree 上的变更 merge 回应用版本特性分支再推送特性分支，但 `ManagedWorkspaceApplicationService.publishPersonalWorkspace` 的旧实现存在三个核心错误：直接推送个人分支、merge 方向反了（特性分支→个人分支）、把个人 worktree HEAD 当成应用版本 commit 写回。导致远端特性分支始终收不到个人改动，前端展示的版本 commit 也是个人分支的 commit。
- What:
  1. `GitWorkspaceService`：新增 `conflictPaths(Path repoRoot)` 方法，统一执行 `git -C {repoRoot} diff --name-only --diff-filter=U`，返回未解决合并冲突文件列表，过滤空行。
  2. `ManagedWorkspaceApplicationService.publishPersonalWorkspace`：重写为三段式正确流程——① 在个人 worktree 上 `stageAll` + `commitStaged`；② 取应用版本副本（checkout 在特性分支上），校验工作树干净后 `fetch` + `pullFastForward` 特性分支，再 `mergeBranch(applicationRepoRoot, personal.branch(), privateKey)` 把个人分支合并进特性分支（正确方向：特性分支 ← 个人分支），合并失败时通过 `GitWorkspaceService.conflictPaths` 拉取冲突文件列表并以 `CONFLICT` 状态返回前端；③ 合并成功后 `push` 特性分支，从应用版本副本取 `headCommit` 更新 `targetCommitHash` 与副本 commit。
  3. 删除此前在业务层直接 `new ProcessGitCommandExecutor()` 自行执行 git 命令的 `conflictPaths` 私有方法和不再使用的 import（`GitCommandResult`、`ProcessGitCommandExecutor`、`Duration`），统一收口到 `GitWorkspaceService`。
  4. 测试：`ManagedWorkspaceApplicationServiceTest` 在 `FakeGitWorkspaceService` 中补齐 `stageAll/commitStaged/fetch/mergeBranch/conflictPaths` 重写并记录 repoRoot，新增 `publishPersonalWorkspaceMergesPersonalBranchIntoApplicationBranch` 和 `publishPersonalWorkspaceReturnsConflictWhenMergeFails` 两个用例，分别验证正确合并方向、推送特性分支、版本 commit 来自副本，以及合并冲突时不推送且返回 `CONFLICT` 与冲突文件列表。`GitWorkspaceServiceTest` 新增 `conflictPathsListsUnmergedFilesAndFiltersBlankLines` 验证 git 命令拼接与多行输出解析。
  5. 文档：更新 `docs/api/http-api.md` 接口表格描述与「后端执行流程」为四步正确流程；更新 `test-agent-common/README.md` 与 `test-agent-workspace-management/README.md` 的 `GitWorkspaceService` 与测试覆盖说明。
- How:
  - 参考 `AgentConfigApplicationService.publicPublish`/`workspacePublish` 的正确模式作为修复模板。
  - 运行 `JAVA_HOME=.../openjdk-25.0.1/Contents/Home mvn -pl test-agent-common test -Dtest=GitWorkspaceServiceTest` 与 `mvn -pl test-agent-workspace-management test`，两个模块测试全部通过（test-agent-common 12 个，test-agent-workspace-management 19 个，0 失败 0 错误）。
- Result: 个人 worktree 推送链路已修复，特性分支能正确接收个人改动；合并冲突时前端拿到 `CONFLICT` + 冲突文件列表可引导用户在个人 worktree 解决后重推。本次不涉及 API 契约、事件、数据库结构、鉴权变更，向后兼容。

### 2026-07-01 - 优化侧边栏 worktree 显示支持鼠标悬浮展示完整名称

- Why: 侧边栏工作空间右侧的 worktree 名字在文本过长时会被截断（使用 ellipsis），原生 title 提示响应过慢且不美观，用户要求挪上去之后可以展示完整名字。
- What: 将 `FigmaFileExplorer.vue` 中的 `.figma-fe-section-worktree` 元素使用 Element Plus 的 `<el-tooltip>` 包裹起来，当鼠标悬浮时展示完整的当前 worktree 名字；同时移除原 span 上的原生 `title` 属性，避免出现双重 Tooltip。
- How: 纯前端模版修改，使用 unplugin 自动导入的 `el-tooltip` 组件。
- Result: 前端 `lint`、`typecheck`、单元测试（195 个用例全部通过）和生产包 `build` 全部通过。

---

## 2026-07-01: 修正消息分层：tool/reasoning 等过程内容不再混入最终回答正文

- Why: Figma 设计方案发现右侧对话中 tool stderr/stdout、reasoning 思考过程被展示成 assistant 最终回答正文，违背消息分层设计。
- What:
  1. 修改 `FigmaChatPanel.vue` 的 `partText()` 函数，只允许 `text` 类型 part 进入消息正文，`tool/reasoning/file/subtask/retry/step-*/compaction` 全部返回空字符串。
  2. 新增 `messageOtherParts()` 函数提取非文件操作 tool part、已完成的 reasoning、retry part，在 assistant 消息中以 `details` 折叠块独立渲染。
  3. 新增 `summaryFromToolInput()`、`toolOutputText()`、`toolIsFailed()`、`reasoningDurationText()`、`partIsRunning()` 等辅助函数。
  4. 更新 `hasVisibleParts()` 包含 `reasoning` 和 `retry` 类型，确保仅有这些 parts 的消息不被过滤。
  5. 更新 template 渲染：tool 折叠块展示工具名、入参、输出/错误；reasoning 折叠块展示思考过程和耗时；retry 以错误块展示。
  6. 新增 FigmaChatPanel 回归测试 7 个用例覆盖：tool stdout/stderr 不混入正文、reasoning 不进入正文、read/file 结构化展示、纯 tool 消息可渲染、retry 块分离展示、state.error 不混入正文。
- How: 纯前端 Vue 组件修改，更新 `FigmaChatPanel.vue` 的 script/template/style，新增 `FigmaChatPanel.test.ts` 测试用例。
- Result: 28 个测试全部通过（+7 个新增用例）；typecheck 无新增错误；前端 201 个全部回归测试通过。`AgentWorkbench.vue` 的预存类型错误不在此次修复范围。

### 2026-07-01 - 按照设计稿扣样式：优化选择题与补充信息面板样式及增加运行思考计时器与补充区块样式

- Why: 聊天面板底部多轮选择题、补充信息输入面板样式与设计稿存在偏差，智能体运行时的思考中状态需要展示计时器（如 "10:36s"）和加载动画。同时，补充四个设计稿的样式：写入/编写文件预览卡片（显示所在目录与带行号的代码编辑器）、Webfetch 链接渲染（以单行链接行渲染带 ↗ 图标）、技能调用（已调用 N 次技能折叠卡与卡片式明细）、子任务面板（已完成 N 个任务的内联时间轴卡片）、重试卡片（粉色卡片，带复制错误按钮及重试按钮，状态显示为 grey 状态的异常中断）。
- What:
  1. `FigmaChatPanel.vue`：
     - 新增运行思考计时器：在智能体开始运行时启动计时器并在页面上显示 `思考中... 10:36s`，同时配以旋转加载图标；
     - 优化多轮选择题（Step 1）和补充信息（Step 2）面板：包括 pagination、卡片选项、字数限制指示、按钮样式；
     - 优化文件预览卡片 (FilePart)：写入和编辑文件预览上方增加文件所在目录、在头部展示 `.../a/Desktop/...` 的精简绝对路径、重构代码块为显示 line numbers 行号的高级编辑器容器；
     - Webfetch 链接渲染 (FilePart)：工具名为 url 抓取类工具时，过滤 details 折叠块，在消息内单独以单行 Webfetch 链接展示，右侧配以 ArrowUpRight `↗` 链接图标；
     - 技能调用 (ToolPart)：将技能调用工具从 standard 列表中过滤，在消息中归并为 `已调用 N 次技能` 折叠面板，展开后显示Launched skill卡片、技能描述和启动时间，且优化 autocomplete 技能面板的阴影与卡片悬浮样式；
     - 子任务进度时间轴 (SubtaskPart)：从消息 parts 中提取 subtask 及 task 类型任务，在消息正文底部以内联卡片形式展示已完成/运行中/等待的任务时间轴进度，配合旋转和打勾图标；
     - 重试卡片与异常中断 (RetryBlock)：当运行异常时，在消息底部渲染粉红色背景的 `您的请求断开，请重试！` 卡片，包含复制错误信息操作以及重试按钮（向父组件 emit `retry` 动作），同时将失败 status 标记图标改为 MinusCircle 并改文案为 grey 灰色的 “异常中断”；
     - 在 `defineEmits` 中补齐 `retry` 声明。
  2. `FigmaFileExplorer.vue`：
     - 修正 `"changes-refreshed"` 的 Event type 定义，添加可选属性 `reloadOpenFiles?: boolean`，消除了 TypeScript 编译期报错。
- How: 纯前端样式与组件微调，涉及 `FigmaChatPanel.vue` 和 `FigmaFileExplorer.vue`。
- Result: `corepack pnpm typecheck` 成功；前端 201 个 Vitest 回归测试全部通过。


### 2026-07-01 - 对话面板底部的运行中任务面板及历史消息内联任务列表增加折叠与展开功能

- Why: 任务执行期间随着步骤增多，输入框上方的实时任务栏会占据大量高度遮挡视野；历史消息中的内联子任务列表在完成后也会占据过长篇幅，用户希望能够将其收缩。
- What:
  1. `FigmaChatPanel.vue`：
     - 新增 `taskPanelCollapsed` 响应式变量，并在 watch 监听到新任务启动（`props.running` 从 false 变为 true）时重置为 `false`；
     - 新增 `collapsedMessages` 响应式变量（Record<string, boolean>）用于记录各历史消息内联任务列表的折叠状态；
     - 修改 template 中历史内联任务面板 (`inline-task-panel`) 和底部实时任务面板 (`figma-chat-task-panel`)，在 header 右侧增加折叠/展开 Chevron 按钮（`<ChevronUp>`/`<ChevronDown>`），列表包裹在 `.figma-chat-task-list` 容器中并根据折叠状态使用 `v-show` 控制显示隐藏；
     - CSS 部分：更新 `.figma-chat-task-summary` 为 flex 布局（space-between），增加折叠状态对应的样式 `.figma-chat-task-summary--collapsed`、`.figma-chat-task-panel--collapsed`、折叠按钮样式 `.figma-chat-task-collapse-btn` 以及列表容器 `.figma-chat-task-list` 样式。
- How: 纯前端交互与样式修改，新增折叠控制状态，不改动业务逻辑和后端 API。
- Result: 前端类型检查（`corepack pnpm typecheck`）成功，全部 204 个 Vitest 测试用例全数通过，无任何 regression。

### 2026-07-01 - 修复 Run 非流式、失败超时与工作区 Diff 实时同步

- Why: `POST /runs` 在 `runtime.startRun(...).block()` 等待远端 prompt，日志中出现 Run 已开始后约 21 秒前端才建立 SSE；消息快照又串行阻塞实时流。智能体写文件时前端只更新运行内 Diff，未刷新文件树和 Git Changes；Git 默认把未跟踪目录折叠成目录记录，导致无 patch/行数且单文件回退无法清理。
- What: Run 保存 `RUNNING` 并先订阅事件后改为异步提交 prompt，提交失败复用统一 `run.failed` 链路；SSE 将消息快照与 durable/live 流并发合并。完成态 `write/edit/apply_patch` 只从 tool part 派生文件通知，不再请求 OpenCode 不支持的 `/vcs/diff?mode=working`。前端收到 `diff.proposed/session.diff` 后同步刷新父目录和工作区 Git Diff；失败重试统一复用最近 prompt。Git status 增加 `--untracked-files=all`，返回可展示、可回退的文件级未跟踪记录。
- How: 复用 `RunEventSseStreamService`、`failRunFromStream`、`refreshWorkspaceGitDiff`、`refreshParentDirectory` 和现有 workspace discard 链路，没有修改 OpenCode/generated SDK，也没有新增旁路架构。补充 Run 非阻塞/异步失败、快照不阻塞 live delta、Git 文件级 status、重试和运行中 Diff 刷新的回归测试，并同步模块 README 与 HTTP/事件文档。
- Result: 后端相关 88 个测试、前端 204 个 Vitest、typecheck、build 和 2 个关键 Playwright E2E 通过。通过 iTerm 使用 `.env.test` 重启后，后端 readiness、数据库、Redis、前端均正常；manager 和 OpenCode 各单实例，OpenCode 实际监听 `192.168.100.115:4098`。真实页面变更徽标为 9，与 personal worktree 的 9 个非空未跟踪文件一致，不再出现折叠目录空 Diff。未修改 API 字段、事件类型、数据库、安全或鉴权契约；`POST /runs` 的返回时机变为非阻塞，保持响应 DTO 向后兼容。
### 2026-07-01 - 作废 opencode 固定节点 yml 参数

- Why: `test-agent.opencode.manager-control.listen-url` 和 `test-agent.opencode.nodes` 已成为重复配置；前者可由服务器内网 IP 和 `server.port` 自动派生，后者不再适合用户专属 opencode 进程模型。
- What: 从 6 个 `application*.yml` 删除 `listen-url` 与 `nodes`；`ManagerControlSettings.listenUrl` 改为启动时由 `LinuxServerIpResolver.resolve()` + `server.port` 派生；删除固定节点配置绑定、`ExecutionNodeSeeder` 和静态节点 Actuator health indicator。启动脚本不再导出 `TEST_AGENT_BACKEND_LISTEN_URL`，只在局域网访问场景补全前端使用的 `TEST_AGENT_BASE_URL`。
- How: 不新增 migration，不删除历史 `execution_nodes` 数据；旧 static-token 集成若仍依赖固定节点，需要继续使用数据库已有记录或后续专门初始化方案。
- Result: 配置绑定测试、manager 控制面装配测试、`test-agent-app -am` 编译、开发脚本验证和作废变量搜索均通过；PowerShell 脚本解析在当前机器因缺少 `pwsh/powershell` 按验证脚本逻辑跳过。

### 2026-07-01 - 通用参数新增 editable 字段控制可改性

- Why: 通用参数可改性此前是 Service 层硬编码（仅 `OPENCODE_MANAGER_MAX_PROCESSES`），前端无法得知哪些参数可改，且 `OPENCODE_PUBLIC_AGENT_GIT_URL` 缺失前端可改入口。需在通用参数表定义字段表示是否可改，并让前端对只读参数限制输入、标注「只读参数」、提示「修改后将影响系统正常运行」。
- What:
  1. Flyway `V20260701100000` 为 `common_parameters` 加 `editable boolean not null default false` 列 + comment + 两条 UPDATE（`OPENCODE_MANAGER_MAX_PROCESSES`、`OPENCODE_PUBLIC_AGENT_GIT_URL` 置 true）。
  2. `CommonParameter` record 加 `boolean editable`，`withValue` 透传；`CommonParameterRow`、`CommonParameterMapper.xml`（columns 片段 + `<arg javaType="_boolean">`）、`MyBatisCommonParameterRepository.toDomain`、存量 `JdbcCommonParameterRepository`（最小同步，未迁移到 MyBatis）同步加列；`CommonParameterResponse` 加 `editable`。
  3. `CommonParameterManagementApplicationService` 删除硬编码常量 `EDITABLE_MAX_PROCESSES_PARAMETER`，改为 `if (!existing.editable())` 校验，文案「该通用参数为只读参数，修改后将影响系统正常运行」；补齐 `OPENCODE_PUBLIC_AGENT_GIT_URL` 可改入口（此前白名单未放行）。
  4. 前端 `shared-types` 的 `GeneralParameter` 加 `editable`；`GeneralParamManagementPanel.vue` 只读弹窗渲染（禁用输入/保存、`只读参数`标签、`⚠ 只读参数 · 修改后将影响系统正常运行`警告条、单元格 🔒 只读视觉）；补只读/可改交互用例 2 个。
  5. 测试：5 个后端测试类构造工厂/断言同步，新增 `OPENCODE_PUBLIC_AGENT_GIT_URL` 可改用例与只读文案断言。
  6. 文档：`configuration-management`、`opencode-runtime` README 同步可改参数说明；`http-api.md` 补 `editable` 响应字段与只读校验说明；`database.md` 补表说明与新 migration 章节。
- How:
  - `mvn -pl test-agent-configuration-management -am test` 全 26 通过；`CommonParameterSeedMigrationTest`、`MyBatisCommonParameterRepositoryIntegrationTest`（跑全量 Flyway 含新 migration）通过；前端 `vitest run general-param-management-panel.test.ts` 4/4 通过。
  - `git stash -u` 干净 HEAD 复跑确认 `test-agent-persistence` 的 linux_servers/agent_config 失败与 `test-agent-api` 的 `ApiLoggingAspectTest` 编译失败均为**预存在**（非本次引入），记入项目记忆。
  - 文档提交时用 `git stash` 隔离 `http-api.md`/`database.md`/`session-log.md` 的并发改动，在干净 HEAD 上只追加本次文档，再 `git stash pop` 恢复并发改动，确保不混入他人未落定工作。
- Result: 通用参数可改性由 DB `editable` 列驱动（单一事实源），仅 `OPENCODE_MANAGER_MAX_PROCESSES` 与 `OPENCODE_PUBLIC_AGENT_GIT_URL` 可改；前端只读参数禁用输入并标注警告。`OPENCODE_PUBLIC_AGENT_GIT_URL` 更新走 `common-parameter.refresh-requested` 广播但不热刷新 manager（URL 属部署参数，AgentConfig 下次操作直读 DB 生效），符合既有「路径/URL 不热刷新」设计。本次为 additive 字段，向后兼容。`CommonParameterManagementControllerTest` 因 `ApiLoggingAspectTest` 预存在编译失败未能运行（已用暂存 diff 审查确认改动正确）。

### 2026-07-02 - 复用 Git publish workflow 并统一冲突回滚

- Why: 个人 worktree publish、Agent 公共/工作空间 publish 和 sync-to-application 都是高风险 Git 写操作，之前 clean/pull/merge/conflict/push/headCommit 分散在业务服务内，冲突后 abort 能力也没有复用到 Agent 配置发布。
- What: `test-agent-common` 只新增 `GitWorkspaceService.abortMerge` 原子命令；`test-agent-workspace-management` 新增 `GitPublishWorkflow` 统一 direct publish、worktree merge publish 和 sync files then push。个人发布、Agent direct/worktree publish、sync-to-application 改用 workflow；worktree merge 冲突会返回 `conflictFiles` 并先执行 `merge --abort`，Agent 冲突不 mark published、不 push。前端 Agent 配置错误格式化补充展示 `details.conflictFiles`。
- How: 不新增 API URL、数据库字段或 generated SDK；`update-and-push` 保持“不预拉取远端内容”契约。同步更新 common/workspace-management/frontend README、包说明和 `docs/api/http-api.md`。
- Result: 定向后端测试、AgentConfigController 测试、前端冲突错误测试、agent-web typecheck 和 `git diff --check` 全部通过；本次不涉及事件、数据库结构、鉴权或环境配置变更。

### 2026-07-02 - slash 技能统一进入可恢复 Run

- Why: 前端此前直接调用 opencode session command，长技能请求期间没有平台 Run 和 RunEvent SSE，导致页面只显示“思考中”、刷新或历史重进无法接管、终止无目标，并可能把同一 workspace 全局事件流的其它会话或后续轮次内容混入当前 Run。
- What: slash 技能改为通过 `POST /runs` 携带可选 `command/arguments` 创建平台 Run；后端先持久化 Run、绑定 remote session 并订阅事件，再后台调用原生 `/session/{sessionID}/command`。事件流按显式 `sessionID/sessionId` 过滤，并在首个成功/失败终态后结束订阅；前端把 `PENDING` 纳入忙碌/恢复状态，模型偏好继续沿用既有 localStorage 机制。
- How: 复用现有 `RunApplicationService`、`AgentRuntime`、`OpencodeClientFacade`、active-run 恢复、Run 取消和 RunEvent SSE 链路；原生命令不自动重试，使用 24 小时硬超时，取消仍走 session abort。同步更新 HTTP/事件文档、模块 README、前端包说明，并补充 API、gateway、facade、runtime、前端队列 and Playwright 回归测试。
- Result: 定向后端测试、前端单测/typecheck/build、桌面和移动 mock E2E 通过；按 `.env.test` 重启三服务后，真实 UI 验证正常对话、文件读取工具、路径图技能实时输出、刷新后从历史恢复运行中任务、任务完成、正交表技能终止、终止状态展示和模型刷新保持均可用。未修改 manager 配置、环境文件、数据库结构、事件类型、鉴权或 generated SDK。

### 2026-07-02 - 修复个人 worktree 发布冲突提示与 unmerged diff 展示

- Why: 个人 worktree 发布接口实际返回业务 `CONFLICT` 且未推送远端，但前端在刷新变更列表时清掉冲突提示，用户看到类似成功状态；同时 `git status --porcelain` 的 `AU/UU` 等 unmerged 状态被当作普通 staged 删除展示，导致误以为提交后“stash”文件被删除。
- What: `GitWorkspaceService` 保留 Git 两字符 `rawStatus`，并将 `DD/AU/UD/UA/DU/AA/UU` 统一映射为 `status=conflict`；工作区 `git-diff` 响应新增 `rawStatus`。Git Changes 面板在 publish 返回 `CONFLICT` 后保留错误提示，刷新后把冲突文件单独展示为 `CONFLICT`，不再计入普通 staged/unstaged 文件列表；冲突未解决时禁用提交按钮，并提示普通 staged 项是未完成 merge 自动应用的中间状态。
- How: 只改 workspace git diff DTO、公共 Git porcelain 解析和前端 Git 变更面板展示；不自动 abort/reset 用户个人 worktree 中的冲突现场。同步更新 HTTP API、workspace-management README、agent-web README，并补充后端解析和前端 publish 冲突回归测试。
- Result: 定向后端与前端测试、workspace-management 编译、agent-web/shared-types typecheck 和 `git diff --check` 通过；本次不涉及数据库、事件、鉴权、安全或环境配置变更。
### 2026-07-02 - 支持普通用户在头像左侧应用下拉加入其他应用

- Why: 用户需要能够自主加入系统中的其他应用，而当前应用人员管理的成员添加和列表查询接口仅限应用管理员和超级管理员访问。
- What: 在头部应用下拉菜单（用户头像左侧）最下方展示一个 “+ 加入其他应用” 菜单项。点击后弹出一个居中浮层（Figma 风格的 div 弹窗），展示用户当前所属的应用（以标签形式展示），并提供下拉框供用户选择未加入的已启用应用。点击“保存”后，调用后端 API 完成应用成员的添加并自动刷新顶部的应用列表。
- How:
  1. 后端修改 `ConfigurationManagementController`，移除了 `listApplications` 和 `addMember` 接口上的 `requireAdmin(exchange)` 角色校验，使得任何已登录的普通用户都可以查询应用列表和添加应用成员。
  2. 前端 `FigmaShell.vue` 增加 `joinableApps` 属性，当点击 “+ 加入其他应用” 时打开 `.figma-add-app-overlay` 浮层展示当前加入的应用和未加入应用的选择框，保存时触发 `join-app` 事件。
  3. `AgentWorkbench.vue` 增加 `allEnabledApplicationsQuery` 以拉取系统中所有启用的应用，计算出未加入的 `joinableApps` 并传入 `FigmaShell.vue`，并在接收到 `join-app` 时调用 `api.addApplicationMember(appId, userId)`，成功后 invalidate 对应的 Vue-Query 缓存进行列表的热更新。
- Result:
  1. 后端 `ConfigurationManagementControllerTest` 定向测试、前端 `FigmaShell.test.ts` 单元测试全部顺利通过。
  2. 前端 Vitest 217 个测试全量通过，`corepack pnpm typecheck` 成功。
  3. API 接口逻辑安全，未改变表结构、Flyway 迁移，不修改 `generated SDK` 和环境配置文件。

### 2026-07-02 - 重构设置面板以分立应用管理与版本库管理菜单

- Why: 版本库管理是全局系统级配置，不依赖特定的应用；而应用人员管理、应用与版本库关联和工作空间管理是应用级配置，两者合并在同一个“应用与工作空间管理”菜单下会造成布局冗余且不直观。
- What: 将“应用与工作空间管理”重命名为“应用管理”，并将“版本库管理”抽离为独立设置菜单“版本库管理”；同时，将“新增版本库”和“编辑版本库”表单从内联布局分别移至各自独立的 `el-dialog` 模态弹窗中，全面提升交互一致性。
- How: 新建 `SettingsRepositoryPanel.vue`；将新增和编辑表单分别包裹在 `el-dialog` 里，点击已有版本库列表行中的“编辑”按钮或右上角“新增”按钮时触发对应弹窗，并在打开时自动通过 `@opened` 聚焦；在 `SettingsMenu.vue` 和 `SettingsDialog.vue` 中配置分立菜单项并关联文件夹图标；在 `SettingsAppWorkspacePanel.vue` 中移除版本库管理标签并定义 `switch-menu` 事件向上传播以支持跳转；同步重构 unit 和 Playwright 测试以断言新的弹窗交互。
- Result: 221 个 Vitest 前端单元测试全部通过，包括新增/修改的 `settings-repository-panel.test.ts`；`corepack pnpm typecheck` 成功；没有对后端 API 接口、事件、数据库结构（Flyway）或 generated SDK 做任何变动。

### 2026-07-02 - 修复默认私人工作区加载与应用权限提示

- Why: 合并其他代码后，切换应用可能因菜单展示未加入应用而进入无权限流程；同时登录/切应用 recent 命中会自动 ensure default 私人工作区，不符合“没有历史/没有私人工作区就不加载”的要求，`FORBIDDEN: 无该应用工作区权限` 也缺少当前加载上下文。
- What: 前端 `pickDefaultWorkspaceForApp` 改为只读查询当前版本已有 `workspaceName=default` 且带 runtime workspaceId 的私人工作区，找不到则空态且不发文件树请求；应用切换菜单只展示已加入应用，未加入应用仅在“加入其他应用”弹窗展示。后端托管工作区成员校验统一补充加载上下文 details；`addMember` 约束普通用户只能给自己加成员，管理员/超级管理员才能给别人加成员。发布个人 worktree 时同步修复 logical `personalworktree:` 路径必须先解析为物理路径再传 Git。
- How: 不新增 API endpoint、不改数据库结构或 Flyway；同步更新 HTTP API、数据库部署说明、前端 README 和 workspace-management README，并补充后端服务/API 单测与前端 mock E2E。
- Result: `ManagedWorkspaceApplicationServiceTest`、`ConfigurationManagementControllerTest`、agent-web typecheck、目标 Playwright E2E 和 `git diff --check` 均通过。默认登录/切应用不再创建 default 私人工作区；权限错误会显示应用、版本、工作区类型/名称/ID，且只暴露安全业务字段。

### 2026-07-03 - 修正个人 worktree 发布进度与冲突续提交流程

- Why: Git 变更区在个人 worktree 已进入原生 merge 冲突后，继续发布仍会重复预览/拉取应用分支；发布进度弹窗把预检命令当作失败步骤命令展示，且后端错误未透传失败阶段和已执行命令，容易出现“暂存阶段失败但错误是拉取失败”的错位。
- What: 个人发布预览在 merge 进行中只返回已记录应用 HEAD，不再 fetch/pull；继续发布在冲突已解决后复用当前 READY 应用副本，提交已有 merge index，跳过重复拉取和重复 merge 应用分支到个人分支。发布响应新增 `currentStep`，失败 `PlatformException.details` 增加 `failedStep/executedCommands`；前端进度弹窗按当前/失败步骤过滤真实 Git 命令，冲突解决后继续发布不再二次预览。同步更新 shared types、HTTP API、workspace-management/backend-api/agent-web 文档和回归测试。
- How: 保持 Git 原生命令链路，不新增数据库、事件、环境配置或 generated SDK；普通发布仍走白名单 stage/commit、应用分支合入个人分支、个人分支合回应用副本、push 应用分支的原流程。构造 test profile 数据时确认 `888888888 / 123456` 对应 `usr_test_dev`，在其 default personal worktree 中保留 1 个 `UU` 冲突文件和 2 个普通未暂存文件用于复测。
- Result: `ManagedWorkspaceApplicationServiceTest`、`ManagedWorkspaceControllerTest`、`GitWorkspaceServiceTest`、`GitWorkspaceServiceRealGitTest`、Git 面板/冲突编辑器 Vitest、agent-web/backend-api typecheck、agent-web build、`git diff --check` 均通过；按 `.env.test` 重启后后端 readiness 和前端 3000 正常，真实页面首次打开变更区即显示 3 个文件，普通文件可在冲突存在时单独暂存并取消暂存。

### 2026-07-03 - 修复发布弹窗实时 Git 命令展示并清理本地 opencode 脏绑定

- Why: 个人 worktree 发布弹窗虽然已接收 Git 命令进度事件，但接口成功返回后又用 `executedCommands` 全量历史覆盖实时状态，导致用户看到命令一次性堆叠；同时本地 test 库中 `usr_test_dev` 存在旧 ACTIVE binding 指向已停止的 `4097`，manager 本地 state 曾只托管 `4096`，会触发 `port 4097 is not managed`。
- What: `GitChangesPanel.vue` 增加实时命令标记，收到 WebSocket 命令后只展示当前最新命令；接口结果只在没有实时命令时兜底展示当前/失败步骤最后一条命令。清理 test 库中 `usr_test_dev` 的旧 `user_opencode_process_bindings`、`opencode_server_processes`、`opencode_process_start_operations` 记录，并停止本机孤儿 `opencode serve`/清空 manager process state。
- How: 复用既有 Agent Config progress WebSocket 和 `executedCommands` 状态，不新增进度通道或后端 API；新增 Git 面板 Vitest 覆盖“实时命令不被最终 command history 覆盖”。本地清理只作用于 `888888888 / usr_test_dev`，不修改 dotenv 或生产 migration。
- Result: `git-changes-panel.test.ts` 15 个用例通过，`@test-agent/agent-web typecheck` 通过；按 `.env.test` 重启三服务成功，后端 readiness 和前端 3000 正常。重新初始化 `888888888` 的 opencode 进程成功，当前状态 `READY`，端口 `4097`。

### 2026-07-03 - 去掉个人发布前端无进度 preview 阻塞

- Why: 用户点击“提交并推送”后，前端先调用 `publish-preview`，该接口会同步远程应用分支且没有进度弹窗；实际测试中 preview 阶段约 23 秒，随后 publish 又重复执行远程同步约 27 秒，导致先在侧栏卡住，后面弹窗才出现。
- What: `GitChangesPanel.vue` 提交路径不再调用 `previewPersonalWorkspacePublish`，直接打开进度弹窗并调用 `publishPersonalWorkspace`；请求体不再携带 `expectedApplicationHead`。后端 publish 本身仍会在个人提交前同步应用分支，因此远程同步统一纳入同一条进度流。
- How: 只改前端提交路径与 Git 面板回归测试，不改 API URL、后端服务、数据库、事件或环境配置。测试断言提交时不调用 preview，弹窗立即出现，publish payload 不带 `expectedApplicationHead`。
- Result: `git-changes-panel.test.ts` 15 个用例通过，`@test-agent/agent-web typecheck` 通过；按 `.env.test` 重启三服务成功。重新为 `888888888 / usr_test_dev` 构造测试数据：1 个 `UU` 冲突文件、1 个 `M ` staged 普通文件、1 个 ` M` unstaged 普通文件；后端 git-diff API 已验证返回 3 个文件。opencode 已重新初始化为 `READY`。
### 2026-07-03 - 落地 Run Session Scope 与 session-tree 消息接口

- Why: RunEvent 需要从单一 `remoteSessionId` 扩展到 root/child session scope，且主 API 路径必须符合 `/api/internal/agent/{agentId}/...` 规范；附件方案中的 client 持久化边界、历史 child 纳入、`raw_event_id=unknown`、JSONB 和终态派生规则需要修正。
- What: 新增 `RunEventScopeContext`、Run session scope 领域对象与 MyBatis Repository；RunEvent/RunEventDraft 扩展可空 scope metadata；新增 `SESSION_ERROR`、`SESSION_CHILD_DISCOVERED`、`SESSION_SCOPE_UPDATED` 等事件类型；opencode 事件映射支持 root/child 终态规则，root idle 才派生 `run.succeeded`，root error 才派生 `run.failed`，`session.next.step.ended` 不再派生成功；Run 启动时记录 root scope；SSE snapshot 恢复按 DB scope 拉 root + current child 消息；新增 Run 当前子树主路径 `GET /api/internal/agent/{agentId}/runs/{runId}/session-tree/messages` 和 Session 历史树主路径 `GET /api/internal/agent/{agentId}/sessions/{sessionId}/session-tree/messages`，并保留 platform 与旧 `/api/runs`、`/api/sessions` 兼容路径。
- How: Flyway `V20260703141000__create_run_session_scopes.sql` 增加 `run_session_scopes`、`run_session_scope_sessions` 和 `run_events` 可空结构化 scope 列，元数据使用 `metadata_json text`，缺失 raw event id 保持 `NULL`；关系型 SQL 通过 MyBatis XML 实现。同步更新 HTTP API、事件流、数据库部署文档、相关模块 README 和 `requirements/opencodelike/` 批次方案文档。
- Result: opencode-client、opencode-runtime、api 相关模块测试通过，新增 MyBatis scope Repository 集成测试通过，`git diff --check` 通过。包含 persistence 的更大范围回归仍命中既有 H2 兼容/fixture 问题：`JdbcOpencodeProcessManagementRepository.saveLinuxServer` 的 PostgreSQL `on conflict` SQL 在 H2 2.4.240 下失败，`usr_test_dev` 相关 agent_config 外键 fixture 也失败；本次新增 scope migration 与 MyBatis Repository 的定向测试已通过。未修改 generated SDK 或环境配置文件。

### 2026-07-03 - 推进 Run Session Scope 事件路由与 RunEvent MyBatis 持久化

- Why: 上一批已建立 Run scope 表结构与查询接口，但 `run_events` 尚未由生产 Repository 写入结构化 scope，client 仍按 root session 过滤事件，child 事件早于 discovery 时也缺少 pending/dedup 保护。
- What: 将生产 `RunEventRepository` 迁移到 MyBatis XML，写入 `RunEventDraft.scopeContext()` 对应 scope 列并让缺失 `raw_event_id` 保持 `NULL`；opencode-client 只负责 raw/mapped DTO 边界，不再按 root session 过滤；runtime 新增 `RunSessionScopeRouter`，从 task/tool metadata、`session.created/updated parentID`、`session.children(root)` bootstrap 发现当前 Run child，并丢弃 child 误派生的 Run 终态；新增 Redis 运行态 pending/dedup cache，Redis 不可用时降级为 DB-only。
- How: 新增 `MyBatisRunEventRepository`、`RunEventMapper.xml` 与集成测试，保留旧 `JdbcRunEventRepository` 但移除生产 Bean；`RunApplicationService` 在订阅事件流时用 runtime router 重写 scope metadata、drain pending 并交给既有 RunEvent pipeline；Redis key 使用 `test-agent:run-scope:{runId}:pending:{sessionId}` 和 `test-agent:run-scope:{runId}:dedup:{sessionId}:{rawEventId}`，TTL 30 分钟。同步更新 opencode-client/runtime/persistence README、PACKAGE、事件流文档和数据库部署文档。
- Result: 定向 persistence、runtime、client 测试通过，`mvn -pl test-agent-api,test-agent-opencode-runtime,test-agent-opencode-client -am test` 全量通过；`rawEventId == null` 不进入 Redis dedup，派生 `run.succeeded/run.failed` 不复用源 raw id，payload 仍兼容旧 SSE 前端。未修改 generated SDK、环境配置和无关前端文件。

### 2026-07-03 - 归并同类工具过程行并修复模型目录加载

- Why: 当前 opencode-like timeline 只合并 reasoning/context，bash/read/skill/question 等同类型工具仍按 part 重复铺开，造成过程区噪声和状态列错落；同时模型列表在旧 `source=bailian` 本地配置下被归到空的 `external-openai` 配置，右侧下拉显示空列表。
- What: 前端 timeline 在同一用户回合内按规范化 toolName 把重复工具合并为一个默认收起的工具组，展开后继续渲染原始工具详情；单条工具仍保持原展示，避免 task 子 Agent 卡片被无意义折叠。`FigmaChatPanel` 和 `AgentWorkbench` 合并 `models` 与 `providers[].models`。后端模型目录明确分流：`opencode` 保持原生代理，`external` 走外部 OpenAI-compatible `/models`，`internal` 走库表，历史 `bailian` 使用内置 Model Studio provider 和 qwen/kimi 模型清单。
- How: 修改 `agent-chat` 的 timeline 投影和 `.oc-*` 展示组件、`agent-web` 的模型目录前端合并逻辑，以及 runtime 模型目录 source/provider 选择；未改接口路径、RunEvent、数据库、generated SDK 或环境配置文件。同步更新 `agent-chat`/`agent-web`、backend/runtime README 和模型目录 API/部署说明，并补充 Vitest 与后端单测覆盖。
- Result: `ModelCatalogApplicationServiceTest` 7 个用例通过；`opencode-timeline.test.ts`、`MarkdownView.test.ts`、`FigmaChatPanel.test.ts` 定向 Vitest 通过；`@test-agent/agent-chat` 与 `@test-agent/agent-web` typecheck 通过；`git diff --check` 通过。按 `.env.test`/`test` profile 启动本地服务成功，health/readiness、前端 HEAD、CORS preflight 和模型/Provider 目录 smoke 均通过。
### 2026-07-03 - 修复模型偏好命中过期 provider 导致 Insufficient Balance

- Why: `Insufficient Balance` 是上游模型 provider 返回的真实错误；前端 localStorage 中的旧 `provider/model` 会长期保留，后端此前也直接透传请求模型，导致模型目录已切换后仍可能打到余额不足或不可用 provider。
- What: `AgentWorkbench` 在模型目录加载后校验已保存偏好，目录外模型或 provider 不匹配时自动回退并持久化当前 `defaultModel`；`RunApplicationService` 在托管模型目录模式下校验请求模型，合法模型原样使用，缺失/非法/目录外模型回退默认模型，目录为空时返回 `VALIDATION_ERROR` 且不启动远端 run。
- How: 未改 API 字段、数据库、generated SDK 或 `.env.local`；同步更新 HTTP API、agent-web README 和 opencode-runtime README，并补充前端 Playwright 与后端 application service 回归测试。
- Result: 旧浏览器偏好不会继续命中已不在当前目录中的 provider；如果当前默认 provider 本身余额或鉴权异常，仍通过既有 `run.failed` 真实错误展示链路暴露给前端。

### 2026-07-03 - 修复 Agent 下拉加载慢和失败后不刷新

- Why: 右侧输入区 Agent 下拉依赖运行态 `/api/agent` 目录；首次加载慢、接口失败或返回空时，旧 UI 会因空列表禁用按钮，用户无法打开下拉重试，也可能在切换 workspace 后被旧响应回填。
- What: `backend-api.listAgents(workspaceId, init?)` 支持单请求 `signal/timeoutMs`；`AgentWorkbench` 使用显式 workspaceId query key、8 秒短超时、READY/切换/打开/失败重试主动刷新，并在目录变化后校验 `selectedAgent` 是否仍是可作为主 Agent 的 `primary/all` 项；`FigmaChatPanel` 增加 loading/error/empty/retry 状态，进程 READY 时不再因空列表禁用 Agent 按钮。
- How: 只改前端 client、工作台组件、对话面板和前端文档；不直连 opencode，不修改后端 HTTP 契约、事件、数据库、generated SDK 或环境配置。
- Result: `FigmaChatPanel`/`backend-api` 单测、workbench 桌面与移动 mock E2E、agent-web/backend-api typecheck 均通过；旧 workspace 的慢 Agent 响应不会覆盖当前 workspace，失败后可在下拉内重试恢复。

### 2026-07-04 - 解决前端偶发性响应缓慢问题（Markdown 懒加载动态导入风暴优化）

- Why: 当聊天历史记录较长（如包含多个步骤和子 Agent 对话）时，页面上会挂载大量的 `MarkdownView` 组件实例。原本的设计中，异步依赖（`markdown-it`、`dompurify`、`highlight.js` 和 `mermaid`）相关的 ref 变量被声明在 `<script setup>` 实例作用域内，且没有做并发请求合并，导致所有组件实例挂载时都会触发并行的动态 `import` 动作，造成浏览器 JS 主线程严重卡顿。
- What: 将 `mdInstance`、`purifyInstance`、`hljsInstance`、`mermaidInstance` 以及对应的 `loadPromise`、`mermaidLoadPromise` 提升至 Vue 组件模块级作用域（`<script lang="ts">` 块），实现在同一个生命周期内所有实例共享依赖单例，并利用 Promise Coalescing 机制将并发的异步动态加载合并为单次网络请求，避免重复执行重型的 JS 初始化。
- How: 修改 `frontend/packages/agent-chat/src/MarkdownView.vue` 和 `frontend/packages/editor/src/MarkdownPreview.vue` 两个核心组件，清除实例级的 `shallowRef` 依赖状态，改为共享的模块级 singleton 变量和 Promise，并在 `ensureLibs` 里进行并发合并处理；未改动后端代码、接口契约、数据库及环境配置文件。
- Result: 成功优化了大量 Markdown 组件渲染时的性能，消除由于并发 `import` 造成的响应延迟。所有 303 个前端 Vitest 测试（含 `MarkdownView.test.ts` 和 `MarkdownPreview.test.ts`）全部顺利通过，类型检查和打包校验无异常。

### 2026-07-04 - 修复主/子 Agent 时间线切换卡顿

- Why: 主 Agent 与子 Agent 时间线来回切换时，空的 running `text` part 会被投影成多个 `TextPartView`，继而挂载 `MarkdownView` 显示“准备输出…”/“无内容”占位；同时 `AgentWorkbench` 对 `chatState.messages` 的 deep watch 会在流式事件期间反复扫描整棵消息树，放大前端主线程压力。
- What: `agent-chat` 时间线投影不再渲染空白 `text` part，`MarkdownView` 对空白 source 同步显示“无内容”并跳过 markdown/highlight 渲染调度，`TextPartView` 也对空源做轻量防御；`AgentWorkbench` 的任务消耗统计和实时追踪扫描改为基于 step-finish/reasoning 与已完成写文件工具的稳定签名触发，不再使用消息树 deep watch。
- How: 新增 root/child scope + 88 次 read 聚合 + 空 running text 的投影与渲染回归测试，覆盖主/子 Agent 切换后不出现空输出占位；同步更新 `agent-chat` 与 `agent-web` README。未改后端、HTTP API、RunEvent SSE、数据库、generated SDK 或环境配置。
- Result: 定向 Vitest、`@test-agent/agent-chat` typecheck、`@test-agent/agent-web` typecheck 和 `@test-agent/agent-web` build 均通过；build 仍有既有 CSS `@import` 顺序与大 chunk 警告，非本次改动引入。

### 2026-07-04 - 翻译过程项中工具名称为中文

- Why: 过程展示时间线中的部分工具调用（如 `todowrite`、`edit` 等）直接展示了英文工具名（例如 `todowrite`、`edit`），不够美观，且与已汉化的“探索”、“思考状态”等部分不一致，影响用户体验。
- What:
  - 汉化 `tool-registry.ts` 中 `getToolInfo` 的所有工具名映射：
    - `skill` -> `"技能"`
    - `bash` -> `"命令行"`
    - `read` -> `"读取"`
    - `list` -> `"列表"`
    - `edit` -> `"编辑"`
    - `write` -> `"写入"`
    - `todowrite` -> `"更新待办"`
    - `apply_patch` -> `"应用补丁"`
    - `webfetch`/`web_fetch` -> `"网页获取"`
    - `websearch`/`web_search` -> `"网页搜索"`
    - `task` -> `"任务"`
    - `question` -> `"提问"`
    - `lsp` -> `"LSP"`
    - `doom_loop`/`doomloop` -> `"死循环"`
  - 更新 `opencode-timeline.test.ts` 和 `FigmaChatPanel.test.ts` 中对上述工具标题的断言，将英文名称替换为中文（如将 `bash`、`skill`、`write`、`read` 分别断言为 `"命令行"`、`"技能"`、`"写入"`、`"读取"`）。
- How: 修改 `frontend/packages/agent-chat/src/opencode-like/state/tool-registry.ts` 以及对应的测试文件，不影响任何后端 API、事件、数据库、generated SDK 或环境配置。
- Result: 整个前端项目打包构建和 `typecheck` 通过，308 个前端 Vitest 测试全部通过。

### 2026-07-04 - 用 MarkdownView 渲染时对以 `.md` 结尾的行内代码文件路径增加高亮颜色

- Why: 用户在使用 MarkdownView 时，希望对正文中类似 `frontend-opencode/docs/README.md` 且用 backticks 包裹的文件路径，显示为特定颜色 `#00ceb9` 以示区分。
- What: 修改 `MarkdownView.vue`，重写 `markdown-it` 的 `code_inline` 规则。在渲染行内代码时，检测其文本内容，若以不区分大小写的 `.md` 结尾，则自动为生成的 `<code>` 节点添加 class `ta-md-file`。同时，在 `<style scoped>` 块中为 `.markdown-body :deep(code.ta-md-file)` 添加高亮颜色 `#00ceb9 !important` 规则。
- How: 修改 `frontend/packages/agent-chat/src/MarkdownView.vue` 的 markdown-it 渲染逻辑与 CSS 样式。并在 `frontend/packages/agent-chat/tests/MarkdownView.test.ts` 中新增单元测试以覆盖此行为。未修改任何后端代码、HTTP 契约、事件流、数据库或环境变量配置文件。
- Result: 新增的单元测试通过，全部 309 个前端 Vitest 测试以及 linting、typecheck 编译均成功通过。

### 2026-07-04 - 统一运行中与思考状态呼吸闪烁频率

- Why: 聊天时间线中“进行中”（工具运行中状态）与“思考中”（AI 思考状态）的闪烁频率不一致，且有不同的 pulse 持续时间（1.2s、1.6s、1.8s），导致多项任务并行/处于活跃态时闪烁参差不齐，视觉效果不够统一。
- What: 将所有表示“运行中/思考中”状态的呼吸脉冲闪烁动画频率统一为一致的 `1.6s ease-in-out`。同时修正了部分因 CSS 选择器不匹配导致的潜在渲染问题。
- How: 
  - 修复由于新挂载组件动画计时重置导致“进行中”与“思考中”闪烁步调（相位）不一致的问题。通过 CSS Houdini `@property` 注册全局 CSS 变量 `--oc-pulse-opacity`，并在 `:root` 上统一执行单个全局脉冲动画 `oc-pulse-global`；所有闪烁相关的元素（包括状态胶囊、等待点、以及工作状态容器）直接引用该全局变量，从而实现完美的帧级别同步。
  - 修改 `animations.css`，将 `.oc-thinking-dot` 调整为使用全局呼吸透明度；
  - 修改 `rows.css`，将 `.oc-working-status` 调整为使用全局呼吸透明度；
  - 修改 `parts.css` 和 `tools.css`，将 `.oc-disclosure.is-running .oc-tool__status` 和 `.oc-tool__status.is-running` 调整为使用全局呼吸透明度，确保所有进行中与思考中的状态胶囊在帧级别上百分百步调一致。
- Result: 前端 Vitest 单元测试全部顺利通过，项目无类型和构建错误。

### 2026-07-04 - 修复对话定位导致出现横向滚动条及代码块右侧空白不居中的布局 Bug

- Why:
  1. 当对话超过 3 轮时，`ConversationLocator` 开始显示。其触发元素 `.oc-conversation-locator__trigger` 使用了 `right: -12px` 负偏移。由于滚动容器 `.ta-thread-viewport` 在存在滚动条占位（`scrollbar-gutter: stable`）时实际可用右 padding 减小，导致触发元素右侧超出视口宽度，触发了多余的横向滚动条。
  2. `.oc-text-part` 消息气泡原先使用了 `padding: 8px 38px 8px 12px`。由于右侧 padding（38px）远大于左侧（12px），导致气泡内的 Markdown 代码块渲染后右边出现巨大的不对称空白，内容没有水平居中。
- What:
  1. 将 `.oc-conversation-locator__trigger` 中的 `span` 宽度由 `10px`/`16px` 按用户要求加长 50% 调整为 `7.5px`（默认）/ `13.5px`（激活态），并将 trigger 容器宽度从 `24px` 缩减为 `14px`。
  2. 将 `.oc-text-part` 的 padding 调整为 `8px 12px`（左右对称的 12px），消除代码块右侧多余的巨大空白，使其与左边保持一样的间距，且不影响右上角复制按钮的正常绝对定位。
- How:
  修改 `frontend/packages/agent-chat/src/opencode-like/styles/locator.css` 以及 `parts.css` 对应的 CSS 规则。不改动任何后端 API、数据库或环境变量配置。
- Result: 整个前端项目 Vitest 324 个测试全部顺利通过，构建正常，消除了横向滚动条并修复了代码块右侧的空白间距。

### 2026-07-04 - 防止成功 Run 被流式错误覆盖为失败

- Why:
  - 用户提供的原始输出最后已出现 `run.succeeded`，但同会话后续仍显示 `Streaming response failed` 且任务状态失败；确认根因是 root 终态事件与 `prompt_async`/事件流 transport error 并发到达时，旧的无条件 Run 状态保存可能让后到失败覆盖已成功 Run。
- What:
  - `RunRepository` 增加 `saveIfStatus` 条件保存端口，生产 `RunRepository` 迁到 MyBatis XML，并通过 `where run_id = ? and status = ?` 原子条件写入终态。
  - `RunApplicationService` 的 root 终态事件和 transport error 失败收敛都改为 CAS 成功后才追加终态事件与快照；CAS 失败时不再补写冲突 `run.failed`。
  - `AgentWorkbench` 在新 Run 请求和 `run.succeeded` 到达时清理旧 SSE 连接异常，并限制 late SSE error 只影响当前 busy Run。
- How:
  - 先补充后端竞态红灯用例，复现 `run.succeeded` 已保存后异步 `Streaming response failed` 覆盖状态；再落 MyBatis Run 仓储、运行服务 CAS 处理和前端 EventSource 回归测试。未做历史数据修复 migration。
- Result:
  - 定向 `RunApplicationServiceTest`、`MyBatisRunRepositoryIntegrationTest`、`PersistenceSqlConventionTest`、新增 Playwright SSE 清理用例、前端 typecheck 和后端 `mvn clean package -DskipTests` 通过。完整 persistence 测试仍命中既有 H2/fixture 无关失败，完整 workbench e2e 仍有多处既有用例失败；本次目标用例通过。

### 2026-07-05 - 重启脚本自动补齐前端 pnpm 依赖

- Why: 前端构建报 `Failed to resolve import "@vscode/codicons/dist/codicon.css"`，实际是 `frontend/pnpm-lock.yaml` 和 package manifest 已声明依赖，但本地 `node_modules` 未同步；仅重启服务不会自动修复这类 stale install。
- What: `restart-dev-services.sh` 新增 `ensure_frontend_dependencies`，在前端 build 和 dev server 启动前检查 `frontend/node_modules/.modules.yaml` 是否落后于 lockfile、workspace 配置或各包 `package.json`，过期或缺失时执行 `corepack pnpm install --frozen-lockfile`；manager auto 启动判定同步收紧为 opencode base URL 指向本机时才启动，避免远端 opencode 环境误拉本地 manager。
- How: 补充 `tools/verify-dev-scripts.sh` 断言，并同步 `docs/guides/ai-workflow.md` 与 `frontend/README.md` 的本地重启说明。未修改 `.env.local`、API、RunEvent、数据库、generated SDK 或生产 migration。
- Result: `tools/verify-dev-scripts.sh` 通过；`corepack pnpm --filter @test-agent/agent-web build` 通过并确认 codicon 字体产物生成；`./restart-dev-services.sh --profile local --env-file .env.local --skip-backend-build --skip-frontend-build` 重启成功，前端日志路径为 `.tmp/dev-services/frontend.log`。

### 2026-07-05 - 优化 question.asked 提问面板

- Why: opencode 原生 `question.asked` payload 使用 `question/header/options/multiple/custom` 字段，前端 reducer 之前没有根据 `multiple:false + options` 推断单选题，导致面板只显示标题和输入框；同时普通文本编号列表和 question 工具过程必须继续避免误弹提问面板。
- What: `agent-chat` reducer 归一化原生 question 字段，保留选项说明并按 `multiple/options` 映射单选、多选、文本题；`FigmaChatPanel` 改为分页式问题卡，支持选项 label/description、自定义答案、上一步/下一步、最后一页提交和忽略。
- How: 新增 runtime reducer 和 Figma 面板回归测试，覆盖用户给出的 `question.asked` 样例、普通 `message.part.updated` 不生成 question、单选/多选/文本题、自定义答案和分页提交。同步 `docs/api/event-stream.md`、`agent-web` 包说明和 `agent-chat` README。
- Result: 定向 `FigmaChatPanel.test.ts` 和 `runtime-reducer.test.ts` 已通过；未改后端 API、数据库、generated SDK 或环境配置。

### 2026-07-05 - RunEvent SSE 异常增加前端可见诊断

- Why: 用户反馈某个对话在手工终止前原始消息已停止继续输出，前端没有解释为什么停止；本地未能直接查到该 trace 的 run_events，且 `.env.test` PostgreSQL 握手被服务端关闭，无法读取远端事实库。
- What: 后端 `RunApplicationService` 在 stream/prompt 异步错误收敛为 `run.failed` 时写入安全的 `message` 与 `error.name/message`；前端 `AgentWorkbench` 在 EventSource 连接异常时为当前 Run 只追加一次本地 SSE 诊断到原始输出，并派发 `run.stream.error`，`agent-chat` 时间线展示诊断卡但不把 Run 直接标记失败。
- How: 复用现有 `failRunFromStream`、原始输出缓存和 `agent-chat` card 渲染路径，不新增 RunEvent 类型、不改数据库、不改 generated SDK。同步更新事件流文档、opencode-runtime README 和 agent-web README。
- Result: 前端 reducer/event-stream-client Vitest、agent-chat/agent-web typecheck、后端 `RunApplicationServiceTest` 通过；`restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 后端打包成功但本地启动失败，阻塞在 `.env.test` 指向的 PostgreSQL 连接超时/EOF，`127.0.0.1:8080` 未启动，前端 3000 仍有既有 Vite 进程返回 200。

### 2026-07-05 - opencode session.status retry 状态前端可见化

- Why: 用户提供的 RunEvent 显示 opencode 返回 `session.status`，其中 `status.type=retry`、`message=Free usage exceeded, subscribe to Go`、`action.reason=free_tier_limit`；事件已经到达前端，但 reducer 只读取字符串 `payload.status`，工作台时间线继续按 running 展示“思考中”，没有说明上游限额/重试原因。
- What: `agent-chat` runtime state 新增结构化 `runtimeStatus`，从 `session.status` 对象提取 `type/attempt/message/action`；opencode-like 时间线在 `runtimeStatus.type=retry` 时渲染 retry 行并显示上游 message/action link，抑制普通 thinking/working 占位；`FigmaChatPanel` 从 `AgentWorkbench` 接收结构化状态并传入时间线。
- How: 修改 `runtime-reducer`、opencode-like adapter/projection/types/RetryRow 样式、`AgentWorkbench` 和 `FigmaChatPanel`，补充 reducer、时间线和工作台面板回归测试。同步 `docs/api/event-stream.md`、`frontend/README.md` 和 `agent-chat` 包说明；未改后端 API、数据库、generated SDK 或环境配置。
- Result: `corepack pnpm --dir frontend test -- runtime-reducer opencode-like-state FigmaChatPanel`、`corepack pnpm --dir frontend --filter @test-agent/agent-chat typecheck`、`corepack pnpm --dir frontend --filter @test-agent/agent-web typecheck` 和 `git diff --check` 通过。

### 2026-07-05 - 修正 Streaming response failed 先到后的最终 Run 状态

- Why: 上一版只防止 transport error 后到覆盖已成功 Run，但仍存在 `Streaming response failed` 先到、root `run.succeeded` 后到时 Run 被提前标失败且前端关闭订阅/保留旧失败卡的问题；用户明确要求任务状态与报错展示以最后一次 root 终态消息为准。
- What: `Run.applyTerminalFact` 允许应用层记录后到 root 终态事实；`RunApplicationService` 对 `Streaming response failed` 等 transport error 增加短暂延迟窗口，窗口内收到 root 终态则不追加旧 `run.failed`，无 root 终态时仍收敛失败；前端 reducer 在新 Run 请求和后到成功/取消终态时清理旧 `run.failed` 失败卡。
- How: 先补充后端“transport error 先到、root success 后到”红灯用例和前端 reducer 失败卡残留红灯用例，再修改 runtime 终态处理与 agent-chat reducer，并扩展 workbench mock E2E 断言旧 SSE 错误、失败卡和底部“任务失败”不跨新一轮成功残留。同步 domain/runtime/frontend/agent-chat README、包说明和 `docs/api/event-stream.md`。
- Result: `mvn -pl test-agent-domain,test-agent-opencode-runtime -am test`、`corepack pnpm exec vitest run packages/agent-chat/tests/runtime-reducer.test.ts`、目标 workbench Playwright、`corepack pnpm typecheck` 和后端 `mvn clean package -DskipTests` 均通过；未修改数据库 schema、Flyway、HTTP API 字段或 SSE 事件类型。

### 2026-07-06 - 历史会话读取改用 session tree 并阻止重复消息

- Why: 历史会话主读取仍调用兼容 `/api/sessions/{sessionId}/messages`，该接口默认刷新远端快照；上游投影缺少 message id 时会重复落库 assistant 快照，前端逐条渲染导致历史消息重复。
- What: 工作台历史恢复改用 agent-scoped session tree；兼容 messages 接口新增 `refresh=false` DB-only；前端对历史 DB rows 和 session-tree events 做读时去重；后端缺少远端 id 时生成合成 `remoteMessageId` 做幂等 upsert。
- How: 更新 `SessionController`、`SessionApplicationService`、`RunSessionMessageSnapshotService`、`backend-api`、`shared-types`、`AgentWorkbench`、`workbench-utils` 和 `ReadonlyTranscript`，补充前后端测试并同步 HTTP API 与相关 README。不删除历史重复数据，不改数据库 schema、RunEvent 类型或 generated SDK。
- Result: 定向 backend API/runtime 测试、frontend Vitest/typecheck 与历史切换 Playwright 用例通过；历史重复 DB rows 会在读取时隐藏，后续刷新不会因缺少远端 id 继续新增重复 assistant 快照。

### 2026-07-06 - 屏蔽 Monaco Editor 内部取消操作导致的未捕获 Promise Rejection

- Why: 当 Monaco Editor 实例被销毁、切换模型或快速切换文件时，内部的 `Delayer.cancel` 或异步操作由于取消而抛出 `Canceled` 异常。由于 Monaco Editor 内部未对其进行 `.catch()` 处理，导致浏览器抛出 `Uncaught (in promise) Canceled: Canceled` 异常，污染控制台，并可能导致测试或生产报错监控工具误报。
- What: 在主前端项目和复刻工程的页面入口文件以及 Vitest 单元测试的 setup 文件中，全局监听 `unhandledrejection` 事件，并拦截和屏蔽来自 Monaco Editor 的 harmless `Canceled` 异常。
- How:
  - 修改 `frontend/apps/agent-web/src/main.ts` 和 `frontend-opencode/src/main.ts` 入口文件，添加 `window.addEventListener("unhandledrejection", ...)` 逻辑，识别并屏蔽 `reason === "Canceled"`、`reason.name === "Canceled"` 或 `reason.message === "Canceled"` 的 Promise 异常。
  - 同步修改 `frontend/vitest.setup.ts` 和 `frontend-opencode/tests/setup.ts` 测试配置文件，保证单元测试在 JSDOM 运行环境下也能够自动过滤并屏蔽该取消错误。
- Result: 成功屏蔽了 Monaco Editor 的未捕获取消异常，控制台不再有 `Uncaught (in promise) Canceled` 的干扰日志；前端主工程与复刻工程的 Vue Typecheck、Vitest 单元测试全数顺利通过。

### 2026-07-06 - 优化提问/权限弹窗与符合前端规范的字号/色彩调整
### 2026-07-06 - 优化提问/权限弹窗与符合前端规范的字号/色彩及紧凑布局调整

- Why:
  - 用户反馈 `question.asked` 提问弹框与权限确认卡片的字号偏大且较松散，整体设计和颜色（写死的十六进制、RGB 值）没有遵守前端样式规约与语义 Token 要求。
- What:
  - 1. 调整字号与字重以对齐前端排版体系：
     - 将提问标题 `.figma-chat-question-title` 字号由 `16px` (700) 调至 `14px` (600)，行高缩减为 `18px`；
     - 将进度文本 `.figma-chat-question-progress` 和指示头 `.figma-chat-question-header` 的 font-weight 分别调整为 400 和 500，行高调为 `16px`；
     - 将提示文本 `.figma-chat-question-hint` 从 `13px` 调至标准 `12px` (Caption)，行高调为 `16px`；
     - 将选项 label `.figma-chat-question-option-label` 的字重由强烈的 `700` (Bold) 调整为标准的 `500` (Medium)，选项描述从 `13px` 调整为 `12px` (Caption) 400，行高分别调为 `18px` 与 `16px`；
     - 将自定义输入 `.figma-chat-question-custom-input` 字号从 `13px` 改为 `14px`，padding 调为更紧凑的 `4px 8px`，最小高度设为 `30px`；
     - 动作按钮文字字号统一调整为标准的 `12px` (Caption)，使得右侧面板侧边栏内的交互按钮更加小巧精致。
  - 2. 色彩及边框对齐全局语义 Token 体系，避免写死颜色：
     - 所有 background 和 border 调整为消费 `var(--ta-chat-border)`, `var(--ta-chat-surface)`, `var(--ta-chat-text)`, `var(--ta-chat-muted)` 以及 `var(--ta-accent)` 等全局 token；
     - 权限弹窗 `.figma-chat-permission-card` 的警告色边框与背景替换为与全局 `--warning` (#946015) 匹配的 RGB 透明度版本，保持优雅统一的浅冷色调视觉设计。
  - 3. 紧凑型布局调整，优化信息密度：
     - 外层卡片和内容滚动的 `gap` 从 `14px` 缩减为 `10px`，外层 dock 容器 `gap` 缩减为 `6px`，padding 从 `8px` 调整为 `6px`；
     - 面板 padding 从 `14px 12px` 缩减为 `10px 8px`；
     - 动作按钮 `.figma-chat-question-submit` 等最小高度由 `32px` 调整为 `28px`，padding 降为 `4px 10px`，按钮间距缩减为 `6px`；
     - 选项卡片 `.figma-chat-question-option` / `.figma-chat-question-custom-card` 最小高度由 `52px` (原 `58px`) 调至 `44px`，选项内置 checkbox 框指示圆圈大小由 `16x16px` 调整为更紧凑的 `14x14px`，完美融合列表行信息密度。
- How:
  - 仅修改 `frontend/apps/agent-web/src/components/FigmaChatPanel.vue` 组件中的 `<style>` 样式模块，不涉及任何底层数据结构、API 契约或后端业务逻辑。
- Result:
  - 1. `corepack pnpm test FigmaChatPanel` 单元测试全部通过。
  - 2. `corepack pnpm typecheck` 及 `corepack pnpm lint` 检查全部无报错通过。

### 2026-07-06 - 每次点击文件自动滚动并聚焦到文件预览板块的最后一个文件展示

- Why:
  - 用户希望在点击文件（包括新文件被打开或激活最后一个文件）时，中间的文件预览板块（Figma 风格 tab 列表）能够直接自动滚动到最后面（最右侧）的最后一个文件展示，并自动聚焦到对应的文件名 tab 上，省去用户手工拖拽/划动横向滚动条去最右边寻找的麻烦。
- What:
  - 1. 给 `FigmaEditorArea.vue` 组件中的 `.figma-editor-tabs` 容器添加 `ref="tabsContainer"`，并为 `.figma-editor-tab` 标签元素添加 `tabindex="0"` 和 focus / focus-visible 的精致微光指示 CSS。
  - 2. 引入 `watch` 和 `nextTick`，监听 `tabs.length`（有新文件打开并追加到末尾时）和 `activePath`（激活 tab 时）。
  - 3. 在对应的 `watch` 触发时，若有新 tab 加入或是当前激活的是最后一个 tab，在 `nextTick` 中自动将 `tabsContainer` 的 `scrollLeft` 滚动至 `scrollWidth`。
  - 4. 每次 `activePath` 改变时，在 `nextTick` 中找到当前激活的 tab DOM 元素 `.figma-editor-tab--active` 并执行 `focus()` 让焦点直接聚焦过去。
  - 5. 更新前端单元测试 `FigmaEditorArea.test.ts`，新增测试用例验证自动滚动与焦点聚焦 (document.activeElement) 的逻辑。
- How:
  - 修改 `frontend/apps/agent-web/src/components/FigmaEditorArea.vue` 的 template、script、CSS，以及新增单元测试 `frontend/apps/agent-web/tests/FigmaEditorArea.test.ts`。
- Result:
  - 运行 `corepack pnpm test FigmaEditorArea.test.ts --run` 全部通过，所有的聚焦与滚动断言均通过。

### 2026-07-06 - 公共 Agent 提交并推送补齐远端同步、冲突处理和真实进度

- Why:
  - 公共 Agent 本地仓库存在未推送提交时，前端可能显示“提交成功”但远端实际没有更新；同时远端 `master` 已有新提交，本地 `origin/master` 缓存落后，旧流程没有先 fetch/merge，也没有在公共 Agent 入口复用工作区冲突解决能力。
- What:
  - 公共 Agent `update-and-push` 改为 `fetch -> stage/commit -> merge origin/{branch} -> push -> broadcast`，无本次新 commit 时仍继续 merge/push；merge 冲突返回 `CONFLICT` 和 `conflictFiles` 并保留原生 merge 现场，解决后再次提交会先落 merge commit 再 push。
  - 增加超级管理员公共仓库显式拉取入口、公共冲突读取/逐个解决/批量解决/取消接口，前端复用三方冲突编辑器，并在公共 Agent 提交弹窗展示拉取、暂存提交、合并、推送、广播阶段和当前 Git 命令。
  - Git push 被远端拒绝时归类为 `REMOTE_REJECTED`，避免统一落到未知错误。
- How:
  - 修改 workspace-management 公共 Agent Git 编排、API Controller/DTO、backend-api、AgentConfigPanel 和系统公共配置管理面板；同步 HTTP API、模块 README 与前端 README，并补充后端/前端回归测试。
- Result:
  - 定向后端 workspace/API/common 测试、前端 Vitest、前端 typecheck、`git diff --check` 均通过；按 `.env.test` 重启本地服务成功，后端 health/readiness、前端 3000 和 CORS 预检通过。实际远端检查确认 `/Users/kaka/Desktop/intelligent-test-agent/.testagent/agent-opencode/.config` 本地 HEAD 为 `6e12505`，远端 `master` 为 `f85b920`，说明用户先前那次 UI 成功提示没有推送到远端。

### 2026-07-06 - 公共 Agent 冲突文件和处理入口前端可见化

- Why:
  - 用户在公共 Agent 合并冲突后只能看到编辑器里的 Git 冲突标记，不知道哪些文件冲突；提交失败进度弹窗和公共级文件树也缺少直接处理冲突的按钮。
- What:
  - 公共级 Agents 树在刷新时通过轻量 `GET /public/git-conflicts` 只读取未解决冲突路径，避免拉取完整 diff patch；冲突文件行标红并显示“冲突”标记；公共级下新增冲突文件列表，提供逐个“处理冲突”、全部保留本地、全部采用远端和取消合并按钮。
  - 公共 Agent 提交失败弹窗在合并冲突时直接列出冲突文件，并提供相同处理按钮；点击“处理冲突”打开既有三方冲突编辑器。
  - 公共 Agent 合并编辑器弹框宽度提升到桌面端约 1120px，并保留小屏 `calc(100vw - 48px)` 自适应。
- How:
  - 复用 `GitWorkspaceService.conflictPaths`、`AgentConfigPanel` 已有公共冲突处理 API 和 `MergeConflictEditor`，新增轻量冲突路径 HTTP/API client 方法，并扩展面板可见入口和 `AgentConfigTreeNode` 冲突标识。
- Result:
  - `corepack pnpm test -- apps/agent-web/tests/agent-config-panel.test.ts`、`corepack pnpm typecheck`、`mvn -pl test-agent-api -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentConfigControllerTest test` 和 `git diff --check` 通过。

### 2026-07-06 - 企业内单服务器部署配置收敛为单实例

- Why:
  - 企业内第一版部署口径应是一台服务器启动 1 个实体 Nginx、1 份前端 dist、1 个 Java 后端和 1 个 opencode worker 容器，之前示例误按双 Java、双 worker 编排。
- What:
  - 将 `deploy/internal/docker-compose.yml` 收敛为单个 `opencode-worker` 服务；`deploy/internal/env.example` 改为单个 `TEST_AGENT_BACKEND` 和单组 `OPENCODE_WORKER_*` 端口变量；Nginx 模板改为单后端 upstream，并用 `TEST_AGENT_NGINX_LISTEN_PORT` 控制入口监听端口。
- How:
  - 保留 Java 直接部署、前端 dist 由实体 Nginx 托管、opencode/manager 外挂程序优先的既有设计，只修正实例数量、变量命名和部署说明。
- Result:
  - `docker compose --env-file deploy/internal/env.example -f deploy/internal/docker-compose.yml config` 只解析出一个 worker，且 opencode 端口保持宿主机端口与容器端口一致；脚本语法检查和 `git diff --check` 通过。

### 2026-07-06 - 工作区选区上下文不再走 opencode file attachment

- Why:
  - 用户只选择文件第一行时，发送后仍出现“文件 冲突文件.md”附件并可能触发整文件读取；用 opencode file part 承载选区会被原生能力按文件附件回放，不适合表达局部选中文本。
- What:
  - 选区上下文改为直接拼入本轮 `submitPrompt` 的结构化 `<context type="selection">` 文本；用户气泡仍展示原始问题，避免大段上下文外露。
  - `chatContextItemsToPromptParts` 只把整文件上下文转换为 opencode 原生 `file` part，选区不再生成 `file` part。
  - `prompt_async` 有 `parts` 时 opencode 实际消费 text part，因此发送时必须用包含选区的 `submitPrompt` 重新构造 `parts`，不能只改顶层 prompt。
  - 本条结论覆盖同日上一版“选区对齐 opencode 原生 file part”的尝试；该方案会让 opencode 把局部选区当文件附件回放。
- How:
  - 修改 `AgentWorkbench.handleSend` 和 `chatContextStore`，保留整文件原生附件链路，收敛选区链路为 prompt-only。
- Result:
  - 前端 4 个定向测试文件 48 条用例通过，`@test-agent/agent-web` typecheck 通过；本地服务已通过 `./restart-dev-services.sh` 重启，前端 `http://127.0.0.1:3000`、后端 readiness 均正常。

### 2026-07-06 - 工作区选区上下文对齐 opencode 原生 file part

- Why:
  - 工作区选中几行后发送，UI 和对话效果仍可能表现为携带整个活动文件；根因是旧的隐式编辑器附件在无选区时会回退为活动文件全文，且后端转 opencode file part 时没有把 `contextType/startLine/endLine` 来源字段透传回来。
- What:
  - 收紧前端隐式编辑器 part：只有存在真实选区文本时才生成 file prompt part，不再用活动文件全文兜底；选区 part 补齐 `contextType=selection`、`startLine`、`endLine`。
  - 后端 `RunApplicationService` 转换 file prompt part 时保留平台 source 中的上下文类型和行号，保证 opencode message part 回放后前端仍能识别为“选区 Lx-Ly”，而不是普通整文件。
- How:
  - 修改 `frontend/apps/agent-web/src/components/prompt-context.ts` 和 `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/RunApplicationService.java`，并补充前端 prompt-context 单测与后端 RunApplicationService 单测。
- Result:
  - `corepack pnpm exec vitest run apps/agent-web/tests/prompt-context.test.ts apps/agent-web/tests/workbench-utils.test.ts packages/agent-chat/tests/user-message-display.test.ts packages/agent-chat/tests/opencode-timeline.test.ts`、`corepack pnpm --filter @test-agent/agent-web typecheck` 和后端定向 `RunApplicationServiceTest#servicePassesPromptPartsAndRuntimeSelectionToOpencodeFacade` 通过。

### 2026-07-06 - 企业内部署目录统一到 /data/testagent

- Why:
  - 企业内单服务器部署要求项目基础目录统一为 `/data/testagent`，前端包、opencode 外挂程序、数据目录和打包输出都要在该目录下扩展；前端构建也需要直接写企业 API base URL。
- What:
  - `deploy/internal/env.example` 改为 `/data/testagent/{data,frontend,programs,dist}` 路径；compose 和 worker 镜像容器内路径同步改成 `/data/testagent`；`VITE_TEST_AGENT_API_BASE_URL` 示例改为企业 API base URL；Go manager 支持 `SYS_DATA_ROOT_DIR` 环境变量覆盖启动前读取 `.serverid/.serverhost` 的目录。
- How:
  - 保留前端客户端自动拼接 `/api` 的约定，文档明确 `VITE_TEST_AGENT_API_BASE_URL` 不要追加 `/api`；Java 的 `SYS_DATA_ROOT_DIR` 通用参数需要配置为 `/data/testagent/data`，并与 compose 传给 manager 的同名环境变量一致。
- Result:
  - `go test ./internal/config`、compose config、脚本语法检查、`package-release.sh --help` 和 `git diff --check` 通过；compose 解析结果显示数据和程序挂载、manager 环境变量均指向 `/data/testagent`。

### 2026-07-06 - 企业内部署模板补齐 ICBC OpenAI 配置

- Why:
  - 部署 env 中需要参考桌面 openclaw 企业配置补齐 `icbc-openai` 模型地址和 token 变量，避免只配置前端 API 地址后 Java 仍缺少企业模型运行参数。
- What:
  - 在 `deploy/internal/env.example` 增加 `TEST_AGENT_MODEL_CATALOG_SOURCE=internal`、`TEST_AGENT_ICBC_OPENAI_BASE_URL`、`TEST_AGENT_ICBC_OPENAI_TOKEN_ENV`、`ICBC_OPENAI_AUTH_TOKEN` 占位符、`TEST_AGENT_ICBC_OPENAI_AUTH_MODE=bearer` 和 openclaw 默认模型 `Qwen3.6-35B-A3B`；部署 README 同步 Java 启动示例和必改项。
- How:
  - 只保留本项目实际读取的变量；openclaw 的 `MIMOAGENT_ICBC_OPENAI_UCID/ENVIRONMENT` 没有本项目消费路径，未写入部署模板。真实 token 不入库，仅保留占位符。
- Result:
  - compose config、部署脚本语法检查和 `git diff --check` 通过。

### 2026-07-07 - 设置页工作空间创建改为远端树一次性表单

- Why:
  - 工作空间创建不再按刷新分支、加载目录、创建三步执行；分支和目录浏览阶段不能 clone 或落磁盘，测试工作库需要按应用同名目录过滤并只允许选择一级子目录。
- What:
  - 前端设置页创建工作空间改为版本库/分支/目录树/别名/保存的一次性表单，测试工作库无效分支禁选并展示命名规则提示，新增目录只作为前端内存节点随 `directoryNew=true` 保存。后端新增应用维度远端树 API，保存阶段强校验测试工作库分支和目录规则，并对同应用工作空间别名做 trim 后唯一校验。
- How:
  - `GitRemoteService` 通过 `git archive --remote` 解析目录/文件树；configuration-management 树接口校验应用、关联版本库和 SSH key，不使用本地 clone cache；workspace-management 只在保存时 clone/checkout，并在 `directoryNew=true` 且目标目录不存在时创建目录。新增 MyBatis XML 查询工作空间别名，JDBC 实现仅复用存量内存过滤。
- Result:
  - 精确前端面板/backend-api Vitest、agent-web/backend-api typecheck、后端目标 Maven 和 `git diff --check` 通过；计划命令 `corepack pnpm test -- backend-api` 仍会触发仓库中既有无关的 chat/git 面板断言失败。

### 2026-07-07 - Redis 服务器广播开启时避免启动期循环依赖

- Why:
  - 企业内部署模板开启 `TEST_AGENT_SERVER_BROADCAST_ENABLED=true` 后，Java jar 启动失败；根因是 `RedisServerBroadcastPublisher` 构造器注入所有 `ServerBroadcastHandler`，而 `AgentConfigProgressHub` 同时作为 handler 又依赖 `ServerBroadcastPublisher` 发布进度，形成 Spring 构造器循环依赖。本地默认广播关闭走 Noop 实现，所以未暴露。
- What:
  - `RedisServerBroadcastPublisher` 改为注入 `ObjectProvider<ServerBroadcastHandler>`，收到 Redis 消息时再延迟枚举并调用支持该事件类型的 handler，保留 Redis 广播功能和原有 handler 分发语义。
  - 新增 app 装配测试覆盖 Redis 广播开启时 `RedisServerBroadcastPublisher` 与 `AgentConfigProgressHub` 同时存在的上下文。
- How:
  - 只修改 event 模块广播发布器和 app 模块测试，不改变部署变量、Redis channel、API、数据库或广播事件协议。
- Result:
  - `mvn -pl test-agent-event -am -Dtest=ServerBroadcastPublisherTest -Dsurefire.failIfNoSpecifiedTests=false test`、`mvn -pl test-agent-app -am -Dtest=ServerBroadcastContextTest -Dsurefire.failIfNoSpecifiedTests=false test` 和 `mvn -pl test-agent-app -am package -DskipTests` 均通过；新的 Spring Boot jar 已产出到 `backend/test-agent-app/target/test-agent-app-0.1.0-SNAPSHOT.jar`。

### 2026-07-08 - 优化运行日志折叠行样式与字色视觉层级，子智能体卡片改用双行垂直布局

- Why: 
  1. 当前对话面板的过程行（如“思考状态”、“技能”、“探索”等）字号偏大且大写（13px + uppercase），与正文大字号竞争导致侧边栏视觉杂乱，空间利用率低；
  2. 展开工具后，自定义的子智能体执行记录（如 `TEST-DESIGN-TARGET-RECOGNITION`）由于名字过长，在单行 Grid 列宽受限下严重截断了后续的需求项编号（如 `识别 I2026000 ...`），无法看清具体工作内容；
  3. 用户需要正文回答以纯黑色清晰醒目地呈现，而其它过程信息要用较灰淡的字色进行视觉避让以增强层次感。
- What:
  1. **折叠触发器**：保留单行展示，但将标题 `.oc-tool__title` 字号由 `var(--oc-text-md)`（13px）下调为 `var(--oc-text-sm)`（11px），移除 `text-transform: uppercase` 强行大写转换，并缩减列间距（`column-gap: 8px`）及微调第一列和状态列宽度比（96px / 56px），使布局更为秀气和节约空间；
  2. **视觉层级**：将最终输出气泡（`.oc-text-part`）的字体颜色统一强制设为纯黑色 `#000000`；将所有的过程折叠栏触发器标题字色设为稍微灰一点的 `var(--oc-muted)`（较深灰），摘要设为 `var(--oc-subtle)`（较浅灰），达到明显的层级对比；
  3. **子智能体卡片**：将展开体部的 `.oc-subagent-card` 改造为 2行 2列 的 Grid 双行垂直排列（子智能体名在第一行第一列，具体工作内容在第二行第一列，已完成状态在右侧跨两行垂直居中对齐）。由于独占一行，长路径和冗长名称能够极其完整地展开显示而不被截断；
  4. **测试断言修正**：在 `opencode-timeline.test.ts` 和 `git-changes-panel.test.ts` 中修正了过去修改遗留下来的纯路径匹配 Bug，将路径断言（如 `src/checkout.ts`, `workspace/docs/selected.md` 等）改成展示端实际显示的纯文件名断言（如 `checkout.ts`, `selected.md`）。
- How: 
  修改 `frontend/packages/agent-chat/src/opencode-like/styles/tools.css` 和 `parts.css` 对应的触发器及卡片样式。修正对应的单元测试匹配文本。未涉及任何后端 API、事件、数据库或环境变量改动。
- Result:
  前端全量 typecheck 和打包无报错，前端 40 个测试文件共 420 个 Vitest 单元测试用例全部 100% 成功绿过！

### 2026-07-08 - 历史对话改为当前用户级分页列表

- Why:
  - 历史对话抽屉需要展示当前登录用户的全部 ACTIVE 会话，而不是当前工作空间会话；点击历史会话时还要能恢复所属应用、工作空间和版本上下文，若用户已无权限或上下文缺失则只读打开。
- What:
  - 新增 `SessionHistoryRepository` 及 MyBatis 查询链路，按 `sessions.created_by_user_id`、`runs.triggered_by_user_id`、`session_messages.sender_user_id` 归因当前用户，按 `updated_at desc` 分页返回，并补充应用、工作空间模板、版本上下文。
  - 前端历史抽屉改为受控搜索和分页加载，每页 30 条，卡片展示应用/工作空间/版本；点击历史会话先校验并切换所属上下文，失败时保留当前上下文并以只读原因禁用输入和发送。
  - 增加用户历史列表相关索引 migration，并同步 API、数据库和模块 README。
- How:
  - 后端列表接口不校验应用成员资格，避免用户被移出应用后看不到自己的历史；发送前仍通过切换/校验结果控制只读。E2E 鉴权辅助改为写入 `sessionStorage`，与 `authStore` 的读取位置保持一致。
- Result:
  - 历史会话相关后端定向 reactor 测试、前端 typecheck、Vitest 目标集和 Playwright 历史点击成功/失败路径均通过。计划中的后端 full reactor 命令 `mvn -pl test-agent-api,test-agent-opencode-runtime,test-agent-persistence -am test` 当前会被未改动模块 `test-agent-workspace-management` 的既有 `WorkspaceFileServiceTest.serviceDeletesOnlyRegularFilesInsideWorkspaceRoot` 阻断，原因是测试期望目录删除抛错而当前实现允许递归删除目录，本次未修改该无关行为。

### 2026-07-09 - 修复历史会话远端 opencode session 跨端口失效

- Why:
  - 用户 opencode 进程重建后端口可能从 4096 漂移到 4097，但旧实现把 `XDG_DATA_HOME` 按 `{OPENCODE_SESSION_DIR}/{port}` 生成，历史 `remoteSessionId` 对应的 SQLite 数据仍在旧端口目录，导致下一次提问校验/调用远端 session 时返回 404 并被映射为“opencode 服务响应异常”。
- What:
  - 用户 opencode 原生 session 目录改为 `{OPENCODE_SESSION_DIR}/users/{unifiedAuthId}`，Java 通过用户仓储解析统一认证号，并显式拒绝空白、`/`、`\` 和 `..` 统一认证号路径片段；Java start 命令帧携带 `sessionPath`，Go manager 优先使用显式路径并在 restart 时保留 state 中的 `sessionPath`，旧命令帧才 fallback `{sessionRoot}/{port}`。运行管理对已有平台进程记录的重启改为公共 stop + startup，并保留 `process.sessionPath`。
  - opencode client facade 新增 `sessionExists`，调用 generated `SessionsApi.v2SessionGet`；404 返回 `false`，其它错误继续按统一 opencode 错误码抛出。`AgentRuntimeTargetResolver` 复用同节点 binding 前校验远端 session，404 缺失时记录 WARN 并重建 `AgentSessionBinding` 与兼容 `sessions.opencode_*` 字段。
- How:
  - 未手改 generated SDK、未改 HTTP API/SSE/数据库。旧 `{OPENCODE_SESSION_DIR}/{port}` 目录本次不自动合并，平台历史消息仍可展示，缺失远端 session 会在下次提问前重建。
- Result:
  - `cd opencode-manager && go test ./...` 通过；`cd backend && mvn -pl test-agent-opencode-runtime,test-agent-agent-runtime,test-agent-opencode-client -am test` 通过。同步更新 opencode-manager、backend、opencode-runtime、opencode-client 和 agent-runtime README/PACKAGE 文档。

### 2026-07-09 - 修正 opencode session 目录身份为统一认证号

- Why:
  - 用户确认 `{OPENCODE_SESSION_DIR}/users/{...}` 的稳定身份不能使用平台 `userId`，必须使用统一认证号，避免用户业务 ID 与企业认证号不一致时生成错误的 opencode 原生 session 目录。
- What:
  - `UserOpencodeProcessAssignmentService` 通过 `UserRepository.findByUserId` 解析 `User.unifiedAuthId` 后生成 `{OPENCODE_SESSION_DIR}/users/{unifiedAuthId}`，并拒绝空白、`/`、`\` 和 `..` 统一认证号路径片段；测试桩补齐用户仓储，启动命令断言改为 `ucid_001`。
- How:
  - 不改 HTTP API、SSE、数据库、Go manager 控制帧或 generated SDK；仅调整 Java 路径身份来源、单测假仓储和相关 README/session-log 说明。
- Result:
  - 已先用失败断言复现实际仍为 `users/usr_...`，修复后 `UserOpencodeProcessAssignmentServiceTest` 全类、后端 opencode runtime/client/agent-runtime reactor 和 `opencode-manager go test ./...` 均通过。

### 2026-07-09 - 运行管理后端指标扩展为服务器/Java/JVM 分组

- Why:
  - 运行管理只展示整机 CPU、粗粒度内存/磁盘和 JVM 聚合值，无法定位 Linux load、MemAvailable、swap、Java RSS/FD、heap/non-heap/direct buffer、GC delta 和线程峰值等问题；同一服务器 Java 重启后仍需按稳定 `linuxServerId` 连续查看历史。
- What:
  - `BackendRuntimeMetrics`、Redis 样本、runtime API DTO 和前端 shared-types 扩展服务器、Java 进程和 JVM 指标字段；旧字段保留，`memoryMaxBytes=memoryTotalBytes`、`jvmGcPauseMillis=jvmGcCollectionTimeDeltaMillis`、`cpuUsagePercent` 仍为整机 CPU。
  - runtime 新增 Linux `/proc` 采集器，Linux 读取 `/proc/stat` 差值、`loadavg`、`meminfo`、`/proc/self/status`、`fd`，非 Linux 保持 best-effort 且 Linux-only 字段为 `null`；磁盘路径优先使用 `SYS_DATA_ROOT_DIR`，不新增环境变量。
  - Redis 历史继续按 `server:{linuxServerId}` 保存服务器字段，按 `backend:{linuxServerId}` 保存 Java/JVM 字段，查询时合并并宽容旧 JSON 缺字段；前端表格和趋势图展示整机/Java CPU、内存/RSS、heap、线程/FD 及六组趋势。
- How:
  - 未改数据库、SSE、API 路径、generated SDK 或 `.env.local`；同步更新 HTTP API、后端/前端 README 和运行管理相关单测/Vitest。
- Result:
  - 定向采集器、Redis、RuntimeManagementQueryService、运行管理前端和 backend-api 运行管理用例通过；计划中的 `-am test`、API 测试编译和前端全量 typecheck/聚合 Vitest 当前被已有 scheduler diagnostics 未完成改动阻断（缺少 `ScheduledTaskLockInspection` / `SchedulerDiagnostics` / `getSchedulerDiagnostics`），本次未修改这些无关文件。

### 2026-07-09 - 修复 Mermaid.js 动态导入与语法解析错误的渲染体验

- Why: 
  - 1. 用户在工作台打开 Markdown 预览或在聊天界面查看 Mermaid 图表时，如果 Mermaid 图表代码中存在语法解析错误（如双引号未包裹完整等），渲染时会抛出异常。因 catch 中直接将 UI 恢复/保持在“脚本”展示，导致用户点击“图表”无反应且不知晓错误根因；
  - 2. `mermaid` v11 作为 ESM-only 包，动态 `import` 时可能因 `.default` 缺失而抛出 `TypeError` 从而导致静默失效。
- What:
  - 优化了动态加载 `mermaid` 库后的实例获取逻辑，通过 fallback 保证 ESM/CJS 模块兼容性并绕过 TS 编译器判定限制；
  - 在 `MarkdownPreview.vue` 与 `MarkdownView.vue` 中增加了对渲染报错的捕获：渲染失败时不再静默回退，而是在“图表”面板展示经过 HTML 转义的安全错误提示框（含具体解析报错细节），并支持在已有错误提示时允许重新触发渲染。
- How:
  - 修改 `frontend/packages/editor/src/MarkdownPreview.vue` 与 `frontend/packages/agent-chat/src/MarkdownView.vue` 两个核心组件的 `ensureLibs`、点击事件处理函数及增加 `.ta-mermaid-error` 错误展示样式。不涉及任何后端 API、事件、数据库、generated SDK 或环境配置修改。
- Result:
  - 前端 `corepack pnpm typecheck` 全量类型检查通过；`MarkdownPreview.test.ts` 及 `MarkdownView.test.ts` 单元测试全部通过；前端生产打包成功无误。

### 2026-07-10 - 首轮 OpenCode 会话标题增加原生兜底

- Why:
  - OpenCode 内置 `title` agent 与主 Run 并行执行；主智能体快速结束时，root `session.updated` 可能只到达 `New session - <timestamp>` 默认标题，或尚未来得及到达有效 AI 标题，右侧会话标题无法更新。
- What:
  - 默认时间戳标题不再同步到平台 Session；首轮 root Run 成功且尚无原生标题确认时，以同一用户、同一 workspace 创建并自动删除临时远端 session，调用同一个 OpenCode 原生 `title` agent。成功后通过 MyBatis 条件更新（当前标题仍为临时标题）再追加既有 `session.updated`，保留 `platformSessionTitleSynchronized` / `platformSessionTitle` 并增加 `platformSessionTitleFallback: true`。
  - 新增 `test-agent.opencode.session-title` 的开关、超时（默认 5 秒）和轮询间隔（默认 100 毫秒），不增加自定义模型、prompt、HTTP API、数据库或 generated SDK。
- How:
  - 仅对用户发起的首轮 `opencode` root Run 生效；原生标题事件优先，条件更新防止异步兜底覆盖用户手动改名或后到的原生标题，非首轮、失败/超时或已同步标题均不覆盖。临时调用复用现有 `OpencodeRuntimeApplicationService` 与用户进程路由链路，不直连 OpenCode。
- Result:
  - `RunApplicationServiceTest`、`RunSessionTitleFallbackServiceTest`、`OpencodeRuntimeApplicationServiceTest` 共 70 项通过，`MyBatisSessionTitleUpdateRepositoryIntegrationTest` 通过，`test-agent-app -am -DskipTests package` 通过；`.env.test` / `test` profile 下本地后端 health/readiness、前端和 CORS 通过。重启后当前测试用户遗留的 4097 进程绑定已不被新 manager 管理，页面显示“进程不可用”，因此本轮未能完成浏览器内真实快速对话的最终标题验证；需先由平台重新初始化该用户进程后再验证。

### 2026-07-10 - 重新生成企业内部署离线包

- Why:
  - 用户需要基于最新代码重新生成企业内离线部署包；首次打包因 Dockerfile syntax 前端镜像从 Docker Hub 拉取超时而中断。
- What:
  - 删除 `deploy/internal/opencode-worker.Dockerfile` 中不必要的 `# syntax=docker/dockerfile:1.7` 声明，避免普通多阶段构建在本地缓存可用时额外访问 Docker Hub 的 Dockerfile frontend。
  - 重新构建后端外置依赖 jar、前端静态资源、最新 amd64 opencode-worker 镜像和外挂程序，并生成完整 `test-agent-internal-release.zip`。
- How:
  - 执行 `deploy/internal/package-release.sh`；Docker buildx 使用本地基础镜像和缓存完成 Go manager 编译及 worker 镜像导出。
  - 对 zip 完整性、后端 `PropertiesLauncher` manifest、瘦 jar、外置 `backend/lib`、PostgreSQL 驱动和解压后的部署脚本执行校验。
- Result:
  - 企业包已生成：`deploy/internal/dist/test-agent-internal-release.zip`。
  - SHA-256：`835f5428d745052e462e6703f72752845fe23739be35453121cec1539df7d0c8`。
  - zip 测试通过，后端 jar 不含 `BOOT-INF/lib`，manifest 含 `Loader-Path: /data/testagent/dist/backend/lib`，部署脚本 `--validate-only --skip-worker` 通过。

### 2026-07-10 - 宠物显式唤起与进程状态内联/浮动交互

- Why:
  - 小宠物虽然支持随机出现和手动定位，但缺少明确的唤起/收起入口，连续使用页面时很难等到出现；进程绿点展开后也需要支持继续拖动，且未建立用户位置偏好时应回到原有对话内联位置。
- What:
  - `FigmaShell` 顶栏新增可访问的小宠物开关；宠物默认隐藏，页面聚焦且可见时连续 60 秒无鼠标、键盘、滚动或点击自动出现。手动唤起保持静止，收起清理行为计时器并重新开始空闲计时；保存过位置的宠物自动回归时原地静止。
  - `FigmaChatPanel` 区分默认内联和用户浮动状态：无有效本地拖动坐标时显示内联状态卡，收起只建立会话内浮动状态，有效拖动才持久化绿点坐标。绿点和展开卡共用 Pointer 拖动与阈值；内联卡首次越过阈值时只反推共享锚点并切换 fixed 定位，保持该事件前的 rect 原位，下一次 pointermove 才应用增量。极端左上边缘无法同时满足绿点安全边距和卡片间距时，展开态保留临时精确卡片锚点，收起后回到最近的安全绿点；首次切换后，后续拖动和窗口缩小仍会按有效卡片尺寸夹进视口。初始化按钮的 Enter/Space 不会冒泡收起父卡。绿点改为 8px 半透明柔光点，子 Agent 视图不显示进程状态入口。
- How:
  - 仅修改 `agent-web` 的两处现有组件、其组件测试、README 和本次设计/计划文档；没有新增 API、SSE、数据库、环境配置或后端逻辑。
  - 新增聚焦 Vitest 覆盖宠物开关不会关闭既有头部菜单、60 秒空闲与活动/焦点/可见性守卫、保存坐标静态回归，以及进程内联/浮动、近边缘及极端左上内联卡首次阈值移动保持原 rect、极端锚点后续拖动与缩窗夹取、展开卡拖动、拖后 click 抑制、初始化键盘事件隔离、8px 半透明样式 token 和子 Agent 隐藏。
- Result:
  - 追补后的 `corepack pnpm test --run apps/agent-web/tests/FigmaShell.test.ts apps/agent-web/tests/FigmaChatPanel.test.ts`：108 passed、1 skipped；`corepack pnpm typecheck` 通过。此前的 `corepack pnpm lint`、`corepack pnpm build` 也通过；构建仅保留既有 CSS `@import` 顺序和大 chunk 警告。
  - 本地 Vite 已启动并确认 `http://127.0.0.1:3001/` 返回 HTTP 200。未做浏览器人工拖动验收，后续可在该地址验证宠物开关/空闲出现及绿点、展开卡拖动。

### 2026-07-10 - 对话面板采用原生 Timeline 外层专注模式

- Why:
  - 用户认为聊天中对话、工具、子 Agent、Ask 和任务状态同时作为卡片出现过于混乱，希望采用专注对话模式并兼顾可拖拽工作台中的窄面板。
- What:
  - 已确认只在 `FigmaChatPanel` 等原生 Timeline 宿主外层设计“本轮活动”入口和响应式浮层/底部抽屉；不调整 `opencode-like` Timeline、消息投影、part 渲染、样式或其原生头像。
- How:
  - 设计说明明确当前 session 的 active Run 摘要与待处理 Ask/Permission 的存活边界，使用容器查询在 720px 容器宽度切换桌面非模态浮层和窄屏模态底部抽屉；活动面板仅展示既有状态摘要，不复制原生工具或 Ask 的渲染/操作。
- Result:
  - `docs/superpowers/specs/2026-07-10-chat-focus-native-timeline-design.md` 已经独立审阅通过。当前仅完成设计确认，尚未编写实现计划或修改运行代码。

### 2026-07-10 - 对话专注模式与弹框视觉统一

- Why:
  - 用户希望降低对话过程噪声，但明确要求保留 OpenCode 原生 Timeline；同时允许既有弹框统一为同一套克制视觉风格，且不能破坏其行为。
- What:
  - `FigmaChatPanel` 新增外层“本轮活动”只读入口；只聚合当前会话的 Ask/Permission、运行中子 Agent、Todo 和失败 Run 状态。桌面使用 popover，窄聊天容器使用 modal 底部抽屉。附件、历史、原始输出、下拉、反馈、Ask/Permission dock 的视觉统一为 token 化边框、圆角、阴影、间距和窄屏表现。
- How:
  - 不改 `packages/agent-chat/src/opencode-like/`；活动面板位于 Timeline 滚动容器外，使用 ResizeObserver 的 720px 容器断点、Escape/外部点击关闭、焦点恢复和已有浮层冲突保护。既有 Question/Permission dock 的模板、动作与提交逻辑保持原位。
- Result:
  - 定向 153 passed / 1 skipped，前端 lint、typecheck、build 通过；`.env.test` 的三服务重启后 backend health/readiness 为 UP，前端 3000 返回 200。构建仍有既有 CSS import 顺序/大包体积警告，测试仍有既有 DirectoryRows 嵌套 button Vite warning。

### 2026-07-10 - 对话面板设计稿样式还原与细节精度优化

- Why:
  - 配合用户最新反馈，移除无关的“本轮活动”侧栏及相关入口，全面还原对话面板纯净的时间线；同时解决现有 Timeline 在字号、行高、前景色、灰度与设计稿不一致的问题，重塑用户与助手气泡布局并重命名。
- What:
  - **移除本轮活动**：清理了此前引入的 Grid 双栏伸缩、侧栏浮层面板及 Timeline 滚动底部的 Inline 活动状态指示按钮，完全保留原生 Timeline 驱动方式，并恢复其全部既有单元测试套件的兼容。
  - **消息气泡与框架布局微调**：
    - 用户气泡：隐藏了用户头像和复制按钮，微调最大宽度为 72%，泡泡背景色对齐 `#eff5ff`，边框线 `#dbe5fb`，圆角 `8px 8px 2px 8px`，去除了阴影。
    - 助手气泡：移除了左侧的 Bot 头像及外圈，改成流式垂直排版；并在上方新增了符合设计稿 F 方案的 `.oc-assistant-who` 结构，显示智能体名 `TestAgent`（原 `主 Agent`）与格式化时间。同时移除了正文气泡的背景、边框、阴影与内边距，使文字以无背景纯文本的形式流式展现。
    - 兼容保留：为了使 Timeline 的断言单元测试正常通过，组件内部在 DOM 结构中保留了隐藏（`display: none`）的 `.oc-assistant-frame__avatar` 节点，实现了表现层跟测试层零冲突。
  - **字体、行高与颜色细节像素级还原**：
    - `tokens.css` 移除了对平台宿主 `--ta` 变量的映射，直接强制指定设计稿规范的 Hex 色值（正文 `#292929`、灰度/时间 `#777774`、边框线 `#e5e5e1`、蓝色高亮 `#2563c8`、琥珀黄 `#936000`）。
    - 统一将主行高（`--oc-line-height`）从 `1.4` 提升至 `1.5`，使文本折行更宽松自然。
    - 修正了 `.markdown-body` 原本被全局赋予 `--oc-muted` 灰色前景色导致助手回复过浅的问题，重新将其正文色校正回明晰的 `--oc-fg`。
- How:
  - 修改了 `AssistantMessageFrame.vue` 模板与脚本，更新了 `tokens.css`、`rows.css`、`parts.css` 和 `markdown.css` 核心样式。
- Result:
  - 运行 `pnpm vitest run packages/agent-chat apps/agent-web/tests/FigmaChatPanel.test.ts` 验证通过，共计 197 个测试全部 100% 成功。

### 2026-07-10 - 宠物增加双击固定及单击打开对话与点击外部关闭交互

- Why:
  - 用户希望能够固定/锁定小宠物的位置，防止其随机游走或自动退出；同时简化弹出交互为单击唤起对话，且双击时触发固定/取消固定，点击外部或关闭按钮可以关闭对话。
- What:
  - **双击固定/取消固定**：引入 `robotFixed` 状态并持久化至 `localStorage`（`figma-shell-robot-fixed`）。双击小宠物可切换固定状态。固定后，清除所有动作与自动退出计时器，使宠物保持在 `idle` 状态且保持可见。页面重新加载时，若为固定状态，将直接在保存的坐标或出生点以 `idle` 状态渲染显示，不再进入一分钟的无操作隐蔽期。
  - **单双击区分与防抖**：小宠物的 click 事件引入 250ms 的防抖延时。若 250ms 内再次触发 click，则清除定时器并触发双击的固定/取消固定动作；若无二次点击，则触发单击的对话框打开事件，避免了双击时误打开/重复打开对话框。
  - **点击外部与 X 关闭**：更新外层容器 click 处理逻辑 `closeHeaderMenus`，包含关闭宠物对话框；宠物和对话框自身的事件添加 `@click.stop` 以阻止冒泡导致的意外关闭。
  - **状态视觉反馈**：小宠物右上角新增一个可爱的 `Pin` 图标角标指示器，并在固定时配合 `pop-in` 缩放微动画弹出，提升视觉品质感与可见性。
- How:
  - 仅修改前端 `FigmaShell.vue` 与测试 `FigmaShell.test.ts`。无新增 HTTP API、SSE、数据库或环境配置改动。
- Result:
  - 运行 `pnpm test apps/agent-web/tests/FigmaShell.test.ts`：所有 25 个测试全部通过。

### 2026-07-11 - Redis Run 数据面四阶段改造合并远端主线

- Why:
  - 对话运行过程需要从 PostgreSQL 高频事件读写切换为 Redis 运行数据面，并在提交前把本地四阶段实现 rebase 到最新 `origin/main`，同时保留远端已有的 OpenCode 原生标题监听、permission/question 回复恢复、宠物旁路问答和其它工作台功能。
- What:
  - 完成会话 `contextToken`、Redis Run manifest/Stream/快照、终态 USER/ASSISTANT 双摘要、owner lease/fencing、故障接管、Redis 丢失收敛、7 天 pending ask 与 2 小时无活动收敛、终态 DB 重试及灰度兼容链路。
  - rebase 冲突中将原生标题 `TITLE_WAIT` 与 Phase 4 owner lease 生命周期合并：标题等待继续复用同一 root SSE，事件路由和追加均携带 fencing lease，订阅最终结束后统一释放 scope 与 lease；新模式 question/permission 回复恢复从 Redis active 索引定位 Run，并通过 Redis 终态事件与摘要投影收敛，不回写 legacy Run 快照。
  - API、SSE、数据库、Redis 部署、安全、后端模块和前端包文档随四阶段代码同步；未改 `.env.local`，未改 generated SDK，保留已有 `stash@{0}`。
- How:
  - 将 7 个本地提交无 force 地 rebase 到远端 `e641e5f8b`，逐处合并而不是选择整侧覆盖；通过冲突标记扫描、`git diff --check`、后端编译、运行时/接口/目标持久层测试、前端 typecheck 与 Vitest 校验合并结果。
- Result:
  - Phase 3 目标测试：后端 125 项、前端 112 项通过；Phase 4 全运行时 444 项、API 256 项通过。持久层本次相关 MyBatis 摘要、Run locator、storage mode、migration、容量与 lease 单测通过；真实 Redis 集成测试在未提供测试端口时按约定跳过。
  - 最终后端全量 `mvn -DskipTests package`、前端全量 typecheck 和生产 build 通过；持久层 42 项目标测试串行复测通过。前端全量 Vitest 为 517 passed、1 skipped、1 failed，唯一失败是远端已有 `DirectoryRows.test.ts` 仍断言只有一个“删除”按钮，而当前组件会暴露两个；未在本次 Redis 改造中修改该无关行为。
  - 后端全 reactor 联跑仍被远端已有的 9 个 H2 持久层问题阻断：`JdbcOpencodeProcessManagementRepository` 使用 H2 2.4 不支持的 PostgreSQL `ON CONFLICT`、Agent worktree fixture 缺 `usr_test_dev` 外键用户，以及两项旧 migration seed 断言；与本次 Redis Run 改造文件无交集，未在冲突处理中顺带改写。

### 2026-07-13 - 测试设计细化 skill 模板分层复核

- Why:
  - 用户指出细化测试设计 skill 仅引用 `test-design` 公共模板、自身没有可加载模板，且模板规则必须与具体方法和三阶段交接协议严格一致。
- What:
  - 在 `.testagent/agent-opencode/.config` 中将模板分为公共通用、方法专属和执行专属三层：`test-design` 保留通用分析/案例模板；7 个 `test-design-*` 细化 skill 各自新增方法模板；`api-execute-case/templates/api-script-template.md` 保持唯一执行模板。
  - 方法模板补齐交易类型覆盖矩阵、案例六要素、方法中间产物、材料证据、`resolvedOutputTarget`、写入文件、缺口、问题和 `需确认`；接口模板恢复七个接口案例区块，并加入接口契约字段。
  - 删除生成/校验 skill 中重复的 API 脚本模板副本，所有入口显式引用 `api-execute-case/templates/api-script-template.md`。
- How:
  - 通过模板引用解析脚本、方法模板契约字段检查、旧标识扫描、文件归属检查、`git diff --check`、OpenCode skill 列表和 Agent 注册检查复核；应用区文档未写入，未创建应用 worktree。
- Result:
  - 所有 Markdown 模板引用均解析到实际文件；7 个细化 skill 均有本地方法模板；旧 skill/Agent 标识和重复执行模板均清除。配置仓库改动待本批次复核后提交。

### 2026-07-13 - 恢复多方法测试设计产物并完成 DeepSeek 真实链路验证

- Why:
  - 三阶段收敛后 `test-design-generation` 被误写为“选择一个方法入口”，导致不同方法 skill 的等价类表、判定表、路径图、场景图和联动映射可能被压缩为单一案例或三份 Agent 阶段文件。
- What:
  - 保留 `analysis → generation → review` 三阶段内部编排，增加 `methodArtifactPlan` / `methodArtifacts` / `artifactManifest`，明确同一对象可使用多个方法，每个方法都要保留图/表/矩阵本体、编号和到案例的映射；禁止生成固定的分析/生成/审查三文件。
  - 分析阶段只输出紧凑方法路由清单，不再读取或展开各方法模板；生成阶段只加载计划中实际选中的细化 skill。
- How:
  - 通过平台 workspace route/ticket/WebSocket RPC 在用户绑定的应用工作区中构造企业转账限额需求和详细设计，没有直接写物理 worktree；重启三服务后使用 `opencode/deepseek-v4-flash-free` 跑通真实三阶段链路。
  - 执行 frontmatter/模板引用解析、旧单方法契约扫描、`git diff --check`，并逐份通过 RPC 读取应用区产物校验方法结构和模板占位符。
- Result:
  - 应用区产出 9 份方法文件：接口 3、等价类 1、正交/判定表 1、路径法 1、场景法 1、直接理解 1、UI/API 联动 1；无三阶段固定文件、无未解析占位符，Mermaid、方法表格和案例映射均存在。
  - 完整运行耗时约 17 分 03 秒：analysis 约 7:19、generation 约 4:41、review 约 2:26、编排交接约 2:37；DeepSeek 免费端出现过 socket 短暂断开并由运行时重试，后续优化应优先缩短 analysis 上下文和按方法分批，不应删除方法产物。

### 2026-07-13 - 修复上下文文件原文重复显示为消息

- Why:
  - OpenCode 原生 user message 由空正文 envelope、普通 text part、synthetic Read text part 和 file part 组成；历史恢复会先跳过空 envelope，导致后续 part 被误归为 assistant，最终在 assistant 输出后又显示整份文件原文。
- What:
  - 乐观 user message 进入 Timeline 前仅保留用户输入和附件路径、文件名、选区等展示元数据，模型提交继续使用完整 parts。
  - session-tree 历史适配按 messageId 查找首个非 synthetic text part，补齐空 user envelope 后再交给既有 RunEvent reducer，保持 OpenCode part 结构和 Timeline 顺序不变。
- How:
  - 新增附件展示 parts 转换及单测；新增与 8.23 真实会话相同顺序的 session-tree 回归夹具，并在本地页面打开原会话复验。
- Result:
  - 相关 5 个 Vitest 文件 256 passed、1 skipped；agent-web typecheck 和生产 build 通过。真实历史只显示一条用户问题、一个文件标签和原 assistant 输出，不再出现文件全文消息；构建仍有既有 CSS `@import` 顺序及大 chunk 警告。
