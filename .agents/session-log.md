# Session Log

## Entries

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
### 2026-06-26 - 工作区变更管理面板(Git Source Control)重构与美化

- Why: 增强工作台变更(Changes)标签页的源代码控制功能，支持以极佳的 Git 样式分别展示“应用工作空间”与“agents”的未暂存与已暂存文件，双击查看 Monaco 侧边 diff，并支持暂存/取消暂存、提交与推送动作。
- What:
  - 新增 `GitChangesPanel.vue` 前端 Git 控制面板，替代了原有的变更列表逻辑，提供 `UNSTAGED`/`STAGED` 分组折叠菜单、悬停暂存按钮和底部提交表单（支持 SignOff/No-Verify/Amend 选项，及提交/推送操作）。
  - 对接 agents 的 stage/unstage/commit/publish 真实后端 API；对应用工作空间则进行前端数组模拟以填充后端未完成的缺失；梳理并提供了应用工作区所需的后台 VCS 提交/推送缺失接口。
  - 在 `workbenchStore.ts` 引入全局 worktree 状态，使 `AgentConfigPanel` 与 `GitChangesPanel` 共享 active 临时工作区；Monaco `DiffViewer.vue` 新增 `agent` 源支持，并在审查非 AI 代码（VCS/Agent）的 Diff 时自适应隐藏反馈及接受/拒绝按钮。
  - 顺手修复了 `shared-types` 中遗漏的 `CreateUserPayload`、`RoleOption` 与 `UserManagementUser` 导出声明，解决了前端因其缺少编译不通过的问题。
- How: 借助 Vue 组合式状态实现前端暂存集存储，Monaco 编辑器联动使用 `setSelectedDiffPath` 及 `loadDiffSource("agent")` 加载映射后的 RunDiffFile，保持样式设计与 Figma 布局的一致性。
- Result: 变更页面体验大幅提升，与 IDE 的 VCS 面板非常类似；除了实际不兼容的 2 项历史聊天测试外，其余前端类型校验与单元测试全部通过。

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
