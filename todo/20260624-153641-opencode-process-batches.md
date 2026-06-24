# opencode 用户进程管理分批实施总账

## 背景

- 用户问题：后端部署在多台 Linux 服务器上，每台服务器运行 1 个后端 Java 进程和多个 Docker 容器；每个容器内通过管理进程维护多个用户专属 opencode server 进程，需要实现用户进程分配、健康检测、跨后端 Java 进程连接、状态持久化和超级管理员可视化管理。
- 当前现象：现有后端以 `execution_nodes` 表表达 opencode 执行节点，`RunApplicationService` 通过 `ExecutionNodeRouter` 选择可路由节点，并用 `agent_session_bindings` 把平台 Session 粘滞到远端 opencode session；该模型仍是“固定 opencode server 节点”，尚未表达 Linux 服务器、Docker 容器、管理进程和用户专属 opencode 端口。
- 目标：先固化原始需求编号与分批交付顺序，后续每个批次都引用需求编号、保持最小范围修改，并能独立验收，避免实现过程中遗漏原始需求。

## 范围

- 包含：原始需求编号、批次划分、每批覆盖范围、主要涉及文件、验收标准、验证方式和跨批次约束。
- 不包含：本批不修改后端 Java、前端 Vue、数据库 migration、管理进程源码或部署脚本；不启动真实 opencode server；不定义最终 socket 协议字段。

## 现状分析

- 相关文件：
  - `docs/README.md`：项目文档入口与任务规范。
  - `docs/guides/ai-workflow.md`：修改前定位、文档同步、测试与提交流程。
  - `docs/guides/self-checklist.md`：完成前自检要求。
  - `backend/README.md`：后端模块职责，唯一可运行服务为 `test-agent-app`。
  - `frontend/README.md`：前端访问边界，前端只能通过 `packages/backend-api` 调用后端。
  - `docs/architecture/module-map.md`：后端 `opencode-runtime`、`persistence`、`api` 和前端包职责。
  - `docs/architecture/dependency-rules.md`：Controller 不得直接访问 Repository 或 generated SDK。
  - `docs/api/http-api.md`：新增管理 API 必须记录路径、鉴权、DTO、错误码和测试。
  - `docs/api/event-stream.md`：本需求不新增 RunEvent，除非后续明确要把管理状态转成 SSE。
  - `docs/deployment/database.md`：新增表和字段必须同步 Flyway migration 说明。
  - `docs/deployment/backend.md`：现有生产部署只描述 Java 后端与固定 opencode 节点配置。
  - `docs/standards/security.md`：管理 API、socket 鉴权、日志脱敏和密钥管理必须遵守。
  - `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/RunApplicationService.java`：当前 Run 启动、节点路由和 agent session 粘滞入口。
  - `backend/test-agent-domain/src/main/java/com/icbc/testagent/domain/node/ExecutionNode.java`：当前执行节点领域模型。
  - `backend/test-agent-domain/src/main/java/com/icbc/testagent/domain/agent/AgentSessionBinding.java`：当前平台 Session 到远端 agent session 的绑定模型。
  - `backend/test-agent-persistence/src/main/java/com/icbc/testagent/persistence/JdbcExecutionNodeRepository.java`：当前执行节点持久化实现。
  - `backend/test-agent-persistence/src/main/java/com/icbc/testagent/persistence/JdbcAgentSessionBindingRepository.java`：当前 agent 绑定持久化实现。
  - `frontend/apps/agent-web/src/components/settings/SettingsDialog.vue`：已有设置入口，可作为新增超管菜单的相邻 UI 参考。
  - `frontend/packages/backend-api/src/index.ts`：前端新增运行管理 API client 的唯一位置。
- 当前实现：
  - `RunApplicationService.resolveAgentTarget(...)` 已有“已有绑定则粘滞到绑定节点，否则从 `execution_nodes` 选最低负载节点”的基础逻辑。
  - `ExecutionNodeSeeder` 从 `test-agent.opencode.nodes` seed 固定 opencode server 节点，适合本地和单节点配置，不适合动态用户专属进程。
  - `OpencodeNodesHealthIndicator` 对配置化 opencode node 做健康检查，但不管理进程生命周期。
  - 前端已有 `roles`，且设置页根据 `APP_ADMIN` / `SUPER_ADMIN` 控制展示；新运行管理必须由后端强制校验 `SUPER_ADMIN`，前端只做可见性优化。
- 问题原因：
  - 当前数据模型缺少 Linux 服务器、后端 Java 进程、容器、管理进程、opencode server 进程和用户绑定关系。
  - 当前运行时只知道固定 `baseUrl`，不知道端口由哪个容器管理，也不知道异常后如何在原 Linux 服务器内重建进程。
  - 当前没有后端与容器管理进程的长连接、心跳、命令应答、重连和扩容发现协议。
  - 当前没有面向超级管理员的运行状态查询 API 和前端页面。

## 修改方案

### 1. 批次 0：固化总需求与批次总账

- 修改文件：
  - `todo/20260624-153641-opencode-process-batches.md`
- 修改位置：
  - 新增完整计划文档。
- 具体改动：
  - 固化需求编号：
    - `R1` 用户进程分配和检测：未分配时选进程数最少的容器启动进程，绑定用户、Linux 服务器和端口；已分配时做健康检测，异常时只在原 Linux 服务器内重建进程。
    - `R2` 容器管理进程：每个容器一个管理进程，负责 opencode server 启动、重启、停止、健康检测；状态持久化到数据库；后端通过 socket 发指令。
    - `R3` 超级管理员运行管理页：前端新增仅超级管理员可见菜单，展示后端进程、管理进程、opencode server 进程运行情况。
    - `R4` 管理进程独立工程：在与 `backend/` 平级的新目录 `opencode-manager/` 中实现，独立技术栈与部署边界。
    - `R5` opencode 路径规则：session 使用 `/data/opencode/session/{port}`，启动参数 `XDG_DATA_HOME=/data/opencode/session/{port}`；公共配置使用 `/data/opencode/.config/opencode/`，启动参数 `OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/`。
  - 固化分批顺序和每批验收标准。
- 原因：
  - 先建立需求总账，后续每批实现时能明确覆盖哪些需求，避免跨批次遗漏。

### 2. 批次 1：后端拓扑与运行态数据模型

- 修改文件：
  - `backend/test-agent-domain/src/main/java/com/icbc/testagent/domain/node/*`
  - `backend/test-agent-domain/src/main/java/com/icbc/testagent/domain/agent/*`
  - `backend/test-agent-persistence/src/main/resources/db/migration/V10__create_opencode_process_management_tables.sql`
  - `backend/test-agent-persistence/src/main/java/com/icbc/testagent/persistence/*`
  - `backend/test-agent-persistence/src/test/java/com/icbc/testagent/persistence/JdbcRepositoryIntegrationTest.java`
  - `backend/test-agent-domain/README.md`
  - `backend/test-agent-persistence/README.md`
  - `docs/deployment/database.md`
- 修改位置：
  - 在 domain 中新增或扩展运行管理领域对象和 Repository 端口。
  - 在 persistence 中新增 migration 与 JDBC Repository。
- 具体改动：
  - 新增 Linux 服务器表，记录服务器业务 ID、名称、主机地址、状态、最后心跳、容量摘要、traceId。
  - 新增后端 Java 进程表，记录实例 ID、所属 Linux 服务器、监听地址、启动时间、状态、最后心跳。
  - 新增容器表，记录容器 ID、所属 Linux 服务器、容器名称、状态、管理进程连接状态、最大 opencode 进程数、当前进程数。
  - 新增管理进程表，记录管理进程 ID、所属容器、协议版本、socket 连接状态、最后心跳、可用能力。
  - 新增 opencode server 进程表，记录进程 ID、所属用户、Linux 服务器、容器、端口、PID、状态、启动参数、健康结果、最后检测时间。
  - 新增用户进程绑定表，记录用户 ID、Linux 服务器 ID、opencode 进程 ID、端口、绑定状态、创建和更新时间。
  - 保留现有 `execution_nodes` 兼容读取，后续批次再决定是否由 opencode 进程表派生或替代。
- 原因：
  - 调度、socket 指令和管理页面都依赖同一份可查询、可审计的数据模型。

### 3. 批次 2：后端调度服务骨架

- 修改文件：
  - `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/process/*`
  - `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/process/*`
  - `backend/test-agent-opencode-runtime/README.md`
  - `docs/architecture/module-map.md`
- 修改位置：
  - 在 `test-agent-opencode-runtime` 新增用户 opencode 进程分配服务和管理进程 gateway 抽象。
- 具体改动：
  - 实现 `UserOpencodeProcessAssignmentService`：输入当前用户、traceId，输出绑定的 opencode 进程和 baseUrl。
  - 未绑定用户：查询健康容器，按当前 opencode 进程数升序选择容器，通过 gateway 启动新进程并写入绑定。
  - 已绑定用户：先对绑定进程做健康检测；健康则复用；异常则锁定原 Linux 服务器，在该服务器内按进程数最少选择容器重建进程。
  - 使用 fake gateway 覆盖启动成功、启动失败、健康失败、无可用容器、并发绑定冲突等场景。
- 原因：
  - 先把核心调度规则在后端内可测试化，不被真实 socket 和容器进程细节阻塞。

### 4. 批次 3：容器管理进程 MVP

- 修改文件：
  - `opencode-manager/README.md`
  - `opencode-manager/PACKAGE.md`
  - `opencode-manager/go.mod`
  - `opencode-manager/cmd/opencode-manager/*`
  - `opencode-manager/internal/*`
  - `docs/deployment/backend.md`
- 修改位置：
  - 在仓库根目录新增与 `backend/` 平级的 `opencode-manager/` 独立工程。
- 具体改动：
  - 管理进程启动后读取容器 ID、Linux 服务器 ID、端口池、最大进程数和本地路径配置。
  - 实现本地进程操作：启动、停止、重启、健康检测 opencode server。
  - 启动 opencode server 时设置：
    - `XDG_DATA_HOME=/data/opencode/session/{port}`
    - `OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/`
  - 管理进程维护端口到 PID 的本地 state 文件索引，并通过 CLI 输出稳定 JSON 结果。
- 原因：
  - 容器内进程生命周期应由容器本地管理进程处理，后端 Java 不直接进入容器执行系统命令。
- 已锁定实施决策：
  - 管理进程使用 Go 单二进制工程。
  - 本批只实现 CLI + 本地 Go library，不实现后端 socket。
  - opencode server 默认监听 `0.0.0.0:{port}`，不启用 Basic Auth；生产依赖容器网络和主机防火墙限制访问面。

### 5. 批次 4：后端与管理进程 socket 通信

- 修改文件：
  - `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/process/socket/*`
  - `backend/test-agent-app/src/main/java/com/icbc/testagent/app/config/*`
  - `backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/*`
  - `backend/test-agent-api/src/test/java/com/icbc/testagent/api/web/platform/*`
  - `opencode-manager/src/*`
  - `docs/standards/security.md`
  - `docs/deployment/backend.md`
- 修改位置：
  - 后端新增 socket registry 和命令分发实现；管理进程新增多后端连接、心跳和重连。
- 具体改动：
  - 定义管理进程到后端 Java 的注册、心跳、命令请求、命令响应和错误响应消息。
  - 每个管理进程连接所有已发现的后端 Java 实例；后端实例扩容后，管理进程通过配置中心、数据库或固定服务发现入口获得新实例并建立连接。
  - 命令使用 commandId 幂等，包含 traceId、containerId、target port、timeout 和操作类型。
  - 后端记录连接状态和最后心跳；断连时把对应管理进程标记为不可调度。
- 原因：
  - 真实进程启动和健康检测必须通过可靠的长连接控制面完成，且要支持多后端 Java 进程负载均衡。

### 6. 批次 5：opencode runtime 接入用户专属进程模型

- 修改文件：
  - `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/RunApplicationService.java`
  - `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/runtime/OpencodeRuntimeApplicationService.java`
  - `backend/test-agent-agent-runtime/src/main/java/com/icbc/testagent/agent/runtime/*`
  - `backend/test-agent-domain/src/main/java/com/icbc/testagent/domain/node/*`
  - `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/run/RunApplicationServiceTest.java`
  - `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/runtime/OpencodeRuntimeApplicationServiceTest.java`
  - `docs/api/http-api.md`
  - `docs/deployment/backend.md`
- 修改位置：
  - 在 Run 和 runtime 代理入口调用前解析当前用户绑定的 opencode 进程 baseUrl。
- 具体改动：
  - `RunApplicationService` 启动 Run 前调用用户进程分配服务，确保当前用户存在健康 opencode 进程。
  - `AgentRuntimeCommand` 使用用户进程派生出的 `ExecutionNode` 或等价运行目标，保持现有 opencode facade 调用边界。
  - 兼容旧 `execution_nodes` 配置：本地开发未启用进程管理时继续使用固定节点；启用后优先使用用户绑定进程。
  - 现有 `agent_session_bindings` 保持平台 Session 到远端 session 的粘滞；新绑定必须保证 remote session 位于用户绑定 Linux 服务器上的 opencode 进程。
- 原因：
  - 运行链路最终必须从固定节点路由切换到用户专属进程，同时保留旧开发路径和旧 session 的兼容窗口。

### 7. 批次 6：超级管理员运行管理页

- 修改文件：
  - `backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/RuntimeManagementController.java`
  - `backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/RuntimeManagementDtos.java`
  - `backend/test-agent-api/src/test/java/com/icbc/testagent/api/web/platform/RuntimeManagementControllerTest.java`
  - `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/process/RuntimeManagementQueryService.java`
  - `frontend/packages/shared-types/src/index.ts`
  - `frontend/packages/backend-api/src/index.ts`
  - `frontend/apps/agent-web/src/components/runtime-management/*`
  - `frontend/apps/agent-web/src/router/*`
  - `frontend/apps/agent-web/src/PACKAGE.md`
  - `frontend/packages/backend-api/README.md`
  - `docs/api/http-api.md`
- 修改位置：
  - 后端新增 `/api/internal/platform/opencode-runtime/management/**` 只读 API。
  - 前端新增仅 `SUPER_ADMIN` 可见的运行管理菜单和页面。
- 具体改动：
  - 后端 API 强制校验 `SUPER_ADMIN`；非超级管理员返回 `FORBIDDEN`。
  - API 返回后端 Java 进程、Linux 服务器、容器、管理进程、opencode server 进程和用户绑定状态。
  - 前端通过 `backend-api` 调用；页面展示筛选、状态徽标、最近心跳、进程数、异常原因和 traceId。
  - 前端只做展示，不直连管理进程或 opencode server。
- 原因：
  - 运行管理是平台内部高权限能力，必须由后端权限控制兜底，前端只负责可见性和展示。

### 8. 批次 7：运维收口与真实环境验证

- 修改文件：
  - `docs/deployment/backend.md`
  - `docs/deployment/database.md`
  - `docs/standards/security.md`
  - `backend/README.md`
  - `frontend/README.md`
  - `opencode-manager/README.md`
  - `tools/*`
- 修改位置：
  - 补齐多 Linux 服务器、多后端 Java 进程、多容器的部署配置、验收脚本和故障处理说明。
- 具体改动：
  - 明确后端实例发现方式、管理进程配置、端口池规划、目录挂载、日志目录、健康检查频率、超时和容量参数。
  - 明确数据库字段兼容策略和旧固定节点模式回滚方式。
  - 增加真实环境手工验收清单：首次登录分配、复用绑定、进程异常重建、后端 Java 扩容连接、管理页展示、权限拒绝。
- 原因：
  - 该功能依赖部署拓扑，最终必须通过运维文档和真实环境验收收口。

## 影响范围

- UI/交互：批次 6 新增超级管理员运行管理页面；普通用户和应用管理员不可见，后端仍强制拒绝非 `SUPER_ADMIN`。
- 数据/协议：批次 1 新增运行管理表；批次 4 新增后端与管理进程 socket 控制协议；批次 6 新增只读 HTTP API。
- 兼容性：批次 5 保留固定 `execution_nodes` 开发路径，启用用户进程管理后优先使用用户绑定进程；旧 session 继续通过 `agent_session_bindings` 兼容。
- 风险：
  - 多后端 Java 进程与管理进程全连接会带来连接数增长，需要在批次 4 明确连接上限、心跳间隔和断线清理策略。
  - 用户绑定与进程启动存在并发竞争，批次 2 必须通过数据库唯一约束或业务锁保证同一用户不会被分配多个活跃进程。
  - opencode 进程健康检测和 session 持久化强依赖 Linux 服务器本地磁盘，批次 2 和批次 5 必须保证异常重建只发生在原 Linux 服务器内。
  - 管理进程 socket 鉴权和日志脱敏属于安全关键路径，批次 4 必须同步安全文档并覆盖测试。

## 验收标准

- [x] 批次 0：`todo/20260624-153641-opencode-process-batches.md` 存在，并包含 `R1` 到 `R5` 原始需求编号、批次 1 到批次 7 的覆盖关系和验收方式。
- [x] 批次 1：数据库 migration 能从空库执行到最新版本，Repository 集成测试能保存和读取 Linux 服务器、容器、管理进程、opencode 进程和用户绑定关系。
- [x] 批次 2：调度服务测试覆盖未绑定分配、健康复用、异常后原 Linux 服务器内重建、无可用容器、并发绑定冲突。
- [x] 批次 3：管理进程能在容器内启动 opencode server，并正确设置 `XDG_DATA_HOME` 和 `OPENCODE_CONFIG_DIR`。
- [x] 批次 4：管理进程能连接多个后端 Java 实例，后端能下发启动、停止、重启、健康检测命令并收到带 commandId 和 traceId 的响应。
- [ ] 批次 5：Run 启动和 opencode runtime 代理都使用当前用户绑定的健康 opencode 进程；进程异常时按原 Linux 服务器重建。
- [ ] 批次 6：只有 `SUPER_ADMIN` 可以访问运行管理 API 和前端菜单；页面能展示后端进程、管理进程、opencode server 进程状态。
- [ ] 批次 7：部署文档包含多 Linux 服务器、多后端 Java 进程、多容器、目录挂载、端口池、健康检查、扩容和回滚说明。

## 验证方式

- 命令/操作：
  - 批次 0：执行 `git status --short`，确认仅新增计划文档。
  - 后续后端批次：执行 `cd backend && mvn -pl test-agent-domain,test-agent-persistence,test-agent-opencode-runtime,test-agent-api -am test`，并按批次运行更窄的目标测试。
  - 后续前端批次：执行 `cd frontend && corepack pnpm --filter @test-agent/backend-api typecheck && corepack pnpm --filter @test-agent/agent-web typecheck && corepack pnpm test -- backend-api`。
  - 后续真实环境批次：按 `docs/deployment/backend.md` 的多服务器验收清单，验证首次登录分配、健康复用、异常重建、后端扩容连接和超管页面展示。
- 预期结果：
  - 每个批次完成时都能指出覆盖的 `R*` 需求编号。
  - 每个批次只修改当前批次声明的代码和文档范围。
  - 每个批次完成后同步稳定文档、运行必要测试，并使用中文 commit message 自动提交。
