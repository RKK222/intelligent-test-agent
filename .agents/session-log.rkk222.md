# Session Log - rkk222

> 按提交者 `git config user.name` 分文件维护，新增条目置于 `## Entries` 顶部。
> 提交前需回顾所有 `.agents/session-log*.md`（含已冻结旧档 `.agents/session-log.md`）的近期条目。

## Entries

### 2026-07-24 - 升级本机 OpenCode 并统一公共 Agent/Skill 规约

- Why:
  - 公共 Skill 的名称、用途、实际调用 Agent 和串联时机不够直观；OpenCode 1.18.4 的 Task 子 Session 权限派生还会对 `task`、`todowrite` 做同名显式规则检查，只有 `permission."*": allow` 时可能被追加 deny。
  - 用户要求把本机 OpenCode 升级到 1.18.4 后测试，并复核 `api-execute-case` 是否为空壳、测试分析/设计/执行 Agent 逻辑和全部名称整改结果。
- What:
  - 在公共配置 README 中逐项说明 12 个 Skill 的加载 Agent、调用时机、产出和设计/执行两条调用链；用户可见名称调整为“生成接口测试案例”“文本理解生成”“生成接口自动化脚本”“生成接口自动化报文”，并具体说明 `api-execute-case` 负责请求/数据准备、平台 API/DB 调用、响应/数据库断言和执行证据。
  - 7 个 `opencode/agents/*.md` 统一显式声明 `permission."*": allow`、`permission.task: allow`、`permission.todowrite: allow`；技术 ID 与既有案例文件方法后缀保持兼容。执行报文文件名统一为 `<案例名称>-接口自动化报文.md`，脚本格式校验规则与当前输出命名一致。
  - `api-execute-case` 保留并明确为不独立执行的“接口执行公共规约”：其 Skill、输出路径和脚本模板共 137 行，另有 8 个 Agent/Skill/文档消费者；删除会使脚本、报文和格式校验重复维护同一契约。
  - 测试设计去除未被全部方法支持的隐式 `phase=full`，固定显式 Phase A → 确认/冻结 → Phase B；manual 恢复必须携带上一轮 `previousPhaseAResult`。测试执行新增 `GENERATE_SCRIPT`、`GENERATE_MESSAGE`、`EXECUTE_API`、`VERIFY_DB` 四类 `requestedActions`，产物生成与真实执行状态分离。
  - 从官方 1.18.4 release 下载并校验 macOS arm64 资产后，把 `/Users/kaka/.opencode/bin/opencode` 升级到 1.18.4；保留原 1.17.7 二进制和项目 1.17.8 layered shim 备份，项目 `.tmp/dev-bin/opencode` 改为指向当前官方二进制。
- How:
  - 核对 OpenCode 官方 Permissions/Agents 文档与官方 v1.18.4 标签的 `subagent-permissions.ts`、`task.ts`：官方文档仍将 `*` 定义为通用规则，v1.18.4 子 Session 派生实现只额外按同名显式规则检查 `task`、`todowrite`，未发现第三个需要同类显式补齐的权限；父 Session deny 和 `external_directory` 规则继续按运行时继承。
  - YAML/frontmatter、12 个 Skill 的名称/归属、7 个方法 Phase A/B、73 处规约/模板引用、旧术语、冲突标记、eval JSON 和 `git diff --check` 通过。官方 1.18.4 CLI 实际加载 7/7 个公共 Agent，并将每个 Agent 的 `*`、`task`、`todowrite` 都解析为 allow；`debug skill` 实际发现全部 12/12 个公共 Skill。
  - 使用 JDK 25、`.env.test`、`test` profile 完整构建并重启 backend、opencode-manager、frontend；backend health/readiness 为 `UP`、前端 HTTP 200、manager WebSocket 已连接且启动环境的 `OPENCODE_BIN` 指向 1.18.4。本轮重启后当前用户 4104 进程未被 manager 管理，因此未把该端口写成已通过的端到端聊天验证；配置加载与 Agent/Skill 目录验证由 1.18.4 CLI 完成。
- Result:
  - 公共配置仓库提交为 `622769c`（`明确公共技能职责并补齐子智能体权限`）和 `4832776`（`统一测试设计与执行智能体规约`）；均未推送远端。未修改 OpenCode 源码、应用 API、RunEvent、数据库/Flyway、SQL、generated SDK、环境配置、性能或安全边界。
- Next:
  - 下一次用户真实进入工作台并触发 OpenCode 进程初始化时，可再观察 manager 是否以 1.18.4 拉起对应端口；当前静态目录、CLI 运行时加载和平台三服务已验证。

### 2026-07-24 - 固化 OpenCode 源码只读边界

- Why: 用户明确要求本项目严格禁止修改 OpenCode 源码，并将约束补入项目相关文档。
- What: 新增 `docs/standards/opencode.md`，同步更新入口规范、文档索引、研发工作流、自检清单、架构地图、OpenCode 升级说明、后端/前端 README 以及 AI 文档校验器。
- How: 统一约束 `opencode-source/opencode-1.18.4/` 仅作只读审计与行为参考；平台适配必须落在本项目自身边界，OpenCode 升级只能整体替换干净上游快照。
- Result: `tools/verify-ai-docs.sh`、文档差异检查与关键约束断言通过；未修改 OpenCode 快照、API、事件、数据库、generated SDK 或环境配置。
- Next: None。

### 2026-07-24 - 重封企业模型白名单修复完整包

- Why:
  - 用户要求在企业模型白名单读取修复提交 `199b27431` 后再次生成 `.4/.114/.2` 固定名企业完整包。
- What:
  - 复用该提交前已构建并验证的最新后端薄 JAR 与 160 个外置依赖，重封内层发布 ZIP 和 `test-agent-two-backend-complete.zip`；前端运行代码、programs、worker 和部署脚本自上次完整包后未变，因此复用原制品与既有三节点受控配置包。
- How:
  - 后端 JAR SHA256 为 `a1248d44f8ae69b1a1d49587c3490cd7a1bba9b665c6a9772f961dd979e2d965`，两个变更模块 JAR 均为 10:17-10:18 新产物且字节码包含 `/config` 与兼容 `/global/config`；前端、programs、worker SHA 与上次完整包一致。
  - 固定名完整包、双后台节点、内置 RSA、敏感信息脱敏、manager 日志兼容、MySQL 分离和 AI 文档校验通过；worker 未变，本轮不重复触发已知不稳定的 Mac Docker Desktop amd64/qemu smoke。
- Result:
  - 最新后端模型白名单修复、当前部署脚本、文档和全部 `.agents/session-log*.md` 已进入重新封装的固定名外层包；端口池仍为每台 `14096-15095` 共 1000 个。
  - 未修改业务源码、API、RunEvent、数据库/Flyway、SQL、generated SDK 或现场环境配置；企业仍需按 `.4` → `.114` → `.2` 完成真实 x86_64/Docker 18.09 和业务验收。

### 2026-07-24 - 修复企业模型白名单读取有效配置

- Why:
  - 企业 `opencode.jsonc` 已正确配置 `enabled_providers`，但部署 OpenCode 1.18.4 后仍展示 OpenCode Zen；上一版前端虽然已经按 Provider 白名单过滤，却从 `/global/config` 取得白名单，而该接口不会合并 `OPENCODE_CONFIG_DIR` 下的企业配置。
- What:
  - 平台模型目录使用的配置 GET 改为代理 OpenCode `/config`，读取当前 workspace 合并后的有效配置；Agent 标准兼容路由仍保留 `/global/config`，配置写入语义不变。
  - 保持按 Provider ID 动态过滤，不固定模型数量：`enabled_providers` 中每个企业 Provider 下的全部模型均可展示，未列入白名单的 `opencode`/Zen Provider 及其模型全部隐藏。
  - 同步 runtime、API、前端工程和 `backend-api` 文档，并用 OpenCode 1.18.4 的实际 `{data, location}` 目录响应补充回归。
- How:
  - 使用 `test-agent-opencode-worker:internal` 和企业示例配置实测：`/global/config` 无企业白名单，`/config` 正确返回两个企业 Provider；OpenCode 原生目录仍返回 24 个模型（其中 22 个 Zen）和 3 个 Provider，应用白名单后 Zen 数为 0。
  - Java 25 下相关后端测试 52/52 通过；前端全量 Vitest 1561 passed / 1 skipped、全仓 lint 和生产 build 通过。按 `.env.test` 重启后 backend health/readiness 为 `UP`、前端 HTTP 200、登录 CORS 正常、manager WebSocket 已连接。
  - 全量后端测试仍有既有 `OpencodeProcessConfigLinkServiceTest.rejectsOrdinaryDirectoryAtManagedPathWithoutDeletingUserData` 失败；失败代码来自提交 `3ab793a8b` 的 Windows 目录复制降级，与本次修改文件和模型配置链路无交集，本次未扩大范围修复。
- Result:
  - 企业环境继续只需维护 `enabled_providers` Provider 白名单，不需要固定两个模型或额外模型白名单；部署本次新后端后，前端已提交的目录过滤会只展示企业 Provider 模型。
  - 已生成后端替换产物 `deploy/internal/dist/backend/test-agent-app.jar`，SHA-256 为 `a1248d44f8ae69b1a1d49587c3490cd7a1bba9b665c6a9772f961dd979e2d965`；未重封完整企业 ZIP，也未部署 `.4/.114` 企业节点。
  - 本次仅调整平台配置 GET 的响应语义并同步 HTTP API 文档；未变更 endpoint、配置写入、RunEvent、数据库/Flyway、SQL、generated SDK、环境配置、性能或安全边界。

### 2026-07-24 - 基于当前代码重打双后台企业完整包

- Why:
  - 用户要求基于当前代码重新生成 `.4/.114` 双后台与 `.2` 前端共用的企业离线完整包，并继续复用现有三台节点受控配置包。
- What:
  - 基于当前主线 `a9144d200` 全量重建后端薄 JAR 与外置依赖、前端、programs、`linux/amd64` worker 镜像 tar 和内层发布 ZIP；外层继续固定为 `test-agent-two-backend-complete.zip`，端口池保持每台 `14096-15095` 共 1000 个端口。
  - 外层封装复用 `.4/.114/.2` 既有节点包，只刷新当前平台发布物、稳定部署脚本、手册和会话日志，不读取或输出节点包中的密码、token 等敏感值。
- How:
  - `package-release.sh` 全量构建通过；运行目录 Git 忽略、双后台节点/RSA/敏感信息脱敏/manager 日志兼容和 AI 文档校验通过。组件 SHA256：JAR `5f62dd3e3645c233786538b14446c1e735088a5d1f57cb8c6c7a963431aba575`，前端 `160bb969bace141ffa140e58bd740224e9b3f67639facf346cdfddf3f4f8fcbd`，programs `e5bdba13d71505b3ebea99929e69f00f7a93325b10bb02ca7032341065a454bb`，worker tar `432fb291af953d881acae74685ee5bb75499bf074fd471f79417b0770b6eb55b`。
  - 本机 Docker Desktop 的 `linux/amd64` qemu 探针先后出现 Node `fetch` 卡住和 `signal 6`；当前镜像曾返回 OpenCode 1.18.4 健康 200，上一版镜像在同环境完整通过优雅停止，但当前镜像的整套运行态 smoke 未能在本机稳定复跑，因此该项按部分验证记录，不能表述为全通过。
- Result:
  - 当前代码、部署脚本和会话日志已进入固定名外层完整包；外层 ZIP CRC、内外 SHA、固定目录、三节点包结构、节点规范化、敏感信息脱敏和 MySQL 分离校验均通过。包自身 SHA 只写入配套 `.sha256` 和交付回复，避免递归写入包内日志。
  - 未修改 Java/前端/manager 业务源码、HTTP API、RunEvent、数据库/Flyway、SQL、generated SDK 或现场环境配置；企业目标机仍需按 `.4` → `.114` → `.2` 顺序完成真实 Docker 18.09、1000 端口映射和业务验收。

### 2026-07-24 - 重打最新公共 Agent/Skill 独立替换包

- Why:
  - 企业内已经部署上一版本公共配置，用户要求重新提供包含本地最新公共 Agent 与 Skill 的独立 ZIP，不重打整套应用。
- What:
  - 以公共主线提交 `d002a60` 为底座，在临时 worktree 合入已提交的公共 Skill 创建/优化变更 `07993d3`，保留六个测试 Agent 的全量权限和取消固定步数上限，同时加入 `skill-creator`、`skill-optimizer` 1.1.0 及 14 个 Skill 的双语来源信息。
  - 覆盖生成 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及 `.sha256`；包内只含公共 README、`opencode/agents/**`、`opencode/skills/**`，未包含 provider 配置、`opencode.json(c)`、Git 元数据、依赖目录或四个未跟踪个人验收样例。
- How:
  - 复用历史固定名 `git archive` 出包方式；校验六个测试 Agent 均为 `permission == {"*": "allow"}` 且无 `steps`，14 个 Skill 全部通过公共 `skill-creator` 1.1.0 校验器。
  - ZIP CRC、允许路径、逐文件 tree 对比均通过；OpenCode 1.17.7 从独立解压目录发现 14/14 个 Skill，并逐个加载 7/7 个 Agent。
- Result:
  - ZIP 包含 7 个 Agent、14 个 Skill、69 个 Skill 文件，SHA-256 为 `40fb1613f9bee79b2b95f55147ae20e8f9967b289e43a6c6a574496a4bf4c8ae`。
  - 本次仅刷新公共配置替换包，不修改应用代码、稳定工程文档、HTTP API、RunEvent、数据库/Flyway、SQL、generated SDK、环境配置、性能或安全边界；企业现场只需校验并导入/发布新公共配置，无需重打应用包。
- Next:
  - 企业现场导入与发布待执行；发布后按平台既有公共配置排空/热加载流程验证 7 个 Agent 与 14 个 Skill。

### 2026-07-23 - 补齐 Agent/Skill 双语存量与创建后热加载

- Why:
  - 公共级和应用级 Agent/Skill 已能展示中文，但存量配置、创建/优化 Skill 的公共技能以及配置树新建后的运行态刷新口径不完整；用户要求英文目录和 OpenCode 原生识别保持不变，并让重新输入 `@`/`/` 能看到最新目录。
- What:
  - Agent 配置树的创建、上传、复制、移动、改名和删除事件新增当前个人 workspace/worktree/server 路由；命中 `opencode.jsonc`、`agents/*.md` 或 `skills/*/SKILL.md` 时复用编辑器保存已有的当前用户 dispose、busy 排队和 Agent/Command 重拉程序。默认新建 Agent 仍为 `primary`，不改变 `@` 只列 `subagent/all` 的原生规则。
  - 更新前端工程文档和用户手册，明确中文主展示、英文辅助展示、英文技术 ID、完整拼音回退、`source` 来源语义和重新输入候选行为。
  - 在四个已登记配置工作树补齐双语说明与 Skill `source`；公共 `skill-creator`/`skill-optimizer` 升级到 1.1.0，创建时生成英文目录/顶层 name 与双语 metadata，优化时保留已有真实 source。对应配置提交为 `375bb35`、`07993d3`、`b128f31`、`0fa45bb`；`public-usr_test_dev` 原有四个未跟踪用户样例保持未修改。
- How:
  - 复用 `refreshRuntimeCatalogAfterAgentConfigSave`，没有新增 OpenCode 代码、扫描器或第二套热加载路径；多文件 Skill 模板只选择 `SKILL.md` 触发一次刷新。存量本地 Git 配置继续兼容双语 description、Skill metadata 和旧的目录/名称回退。
- Result:
  - agent-web typecheck 通过；相关 Vitest 为 176 passed / 1 skipped；`AgentConfigApplicationServiceTest` 49/49 通过；两个公共技能自身校验通过，OpenCode 1.17.7 能发现当前 14 个相关 Skill、逐个发现历史 18 个 Skill，并加载当前/历史/应用 Agent。
  - Codex 通用 Skill validator 因不接受 OpenCode 合法的 `compatibility` 字段而不适用，本轮以项目校验器和 OpenCode 原生 debug 结果为准。
  - 按 `.env.test`、JDK 25 重启三服务，backend health/readiness 为 `UP`、前端 3000 为 HTTP 200、登录 CORS 正常、manager WebSocket 已连接。本次不涉及 HTTP API、RunEvent、数据库/Flyway、generated SDK、环境配置、鉴权或安全边界。
- Next:
  - None。

### 2026-07-23 - 优化服务器工作空间选择器窗口交互

- Why:
  - 超级管理员的服务器工作空间选择器尺寸固定，目录较多时处理空间不足；用户还要求像用户手册一样在普通 Chrome 标签页继续处理，并明确不能使用 `about:blank` 或在 URL 暴露服务器、本机目录。
- What:
  - 选择器新增视口内右下角拖拽/方向键缩放、页面内全屏与还原；新标签页改为真实工作台 URL `/?serverWorkspacePicker=1`，不传 popup 尺寸参数。
  - 当前服务器和目录通过同源 `sessionStorage` 交接，新标签页首次加载和刷新均自动恢复；浏览器策略阻止新标签页时保留原页面并明确提示。
  - 同步前端工程 README、app README、包级说明和模块地图；未使用晚于 Chromium 108 基线的 CSS 能力。
- How:
  - 定向 Vitest 5/5、`vue-tsc --noEmit` 和包含 VitePress 的生产 build 通过；生产构建继续使用项目既有 Chromium 108 target。
  - 按 `.env.test`、JDK 25 启动本地三服务，health/readiness 与前端 HTTP 正常；Playwright 真实 Chromium 验证缩放、全屏、真实 URL 新标签页、目录交接和刷新恢复。
- Result:
  - 地址栏只保留入口标记，不再出现 `about:blank`、服务器 ID 或本机目录；本次不涉及 HTTP API、RunEvent、数据库/Flyway、generated SDK、权限边界或环境配置。

### 2026-07-23 - 企业模型目录按运行配置隐藏 OpenCode Zen

- Why:
  - 企业包已经在 `opencode.jsonc` 配置 `enabled_providers`，但 OpenCode 1.18.4 原生 Model/Provider 目录仍可能返回 Zen 内置供应商，导致模型选择器同时展示公网 Zen 与企业内部模型。
- What:
  - 复用 `backend-api` 既有 runtime config、models 和 providers 请求；当运行配置存在非空 `enabled_providers` 时，按 Provider ID 同时过滤模型与供应商目录。Model/Provider 并发加载共用同一轮 config 请求，请求结束即清理，以兼顾配置热加载；白名单未配置或 config 暂时不可读时保留原生目录兼容。
  - 新增 Zen/企业模型混合目录过滤、未配置白名单兼容回归，并同步前端工程及 `backend-api` 包级文档。
- How:
  - `backend-api` 定向 Vitest 84/84、全量前端 Vitest 1556 passed / 1 skipped、全仓 lint、生产 build 均通过；按 `.env.test`、JDK 25 重启本地三服务后 health/readiness、前端 HTTP、登录 CORS 和 manager WebSocket 均正常。
  - 执行企业 `package-release.sh --frontend-only --no-zip`，确认编译资源包含 `enabled_providers` 过滤逻辑并生成前端离线归档。
- Result:
  - 企业配置继续沿用现有 `enabled_providers` 即可只展示企业白名单模型；未改 HTTP API、RunEvent、数据库/Flyway、generated SDK、环境配置、鉴权或安全边界。
  - 前端离线归档为 `deploy/internal/dist/test-agent-frontend-dist.tar.gz`，SHA-256 `313be3a8a466f41353e91ee1d1c6d9dcf367ec69a0517425c1eab17df53028ca`；本次只生成前端产物，未重封完整企业 ZIP，也未部署企业节点。

### 2026-07-23 - 收敛双后台为千端口并防护旧 Docker 代理耗尽

- Why:
  - `.4` 的 Docker 18.09.7 在默认 `userland-proxy` 下发布 `14096-16095` 时，到第 536 个端口报 `iptables ... fork/exec ... resource temporarily unavailable`；实测 Docker service/parent cgroup、内核 PID 和内存均未到上限，问题是旧 daemon 为每个端口创建代理时的瞬时进程风暴。
- What:
  - 双后台端口池改为 `14096-15095` 正好 1000 个端口，与 `OPENCODE_MANAGER_MAX_PROCESSES=1000` 一致；同步节点校验、封包、XXL 诊断、全量/多后台手册和 enterprise skill。
  - `opencode-worker-docker.sh` 在 Docker 18.09 启动千端口池前必须确认 daemon 已配置 `"userland-proxy": false`；不满足时在删除现有 worker 容器前失败，不再运行到一半才耗尽资源。
- How:
  - 为 worker 脚本扩展临时 fake Docker 回归，覆盖默认代理的失败停止点和禁用代理后的 1000 端口同号映射；执行开发脚本、双后台节点、完整包、XXL 诊断、AI 文档和 Shell 语法校验。
- Result:
  - 相关回归全部通过；Mac 固定名完整包已重封并从实际 `.4` 节点包确认 env、部署脚本和内层 worker 前置校验。现场仍需按维护窗口先优雅停止 `.4` PostgreSQL、禁用 Docker userland proxy、重启并恢复 PostgreSQL，再验证 worker 1000 端口；未清理任何数据或 manager state。

### 2026-07-23 - 迁移双后台 OpenCode 端口池到 14096-16095

- Why:
  - 企业 `.4` 后台启动 worker 时，旧端口池内的 `4118` 已被宿主机其它进程占用，Docker 因此报 `bind: address already in use`；用户明确要求两台后台不再使用旧端口，统一迁移到 `14096-16095`。
- What:
  - 复用现有双后台封包、节点部署和 worker 启动链路，把 `.4/.114` 节点包的 `OPENCODE_WORKER_PORT_START/END` 固定为 `14096/16095`，宿主机与容器仍保持同号映射。
  - 同步双后台部署校验、完整包校验、XXL 诊断提示、全量升级手册、多后台手册、部署入口和 enterprise skill；封包回归额外确认复用旧敏感节点包时，包内部署脚本和手册也会替换为当前版本。
  - 每台 worker 提供 2000 个端口坐标，但页面通用参数 `OPENCODE_MANAGER_MAX_PROCESSES` 仍固定为 `1000`，不未经压测直接翻倍单机进程容量。
- How:
  - 运行变更 Shell 语法和 `git diff --check`，执行多后台节点、固定名完整包、XXL 诊断及 AI 文档验证；从重封完整包中实际解出 `.4` 节点包，核对 env、脚本和 Docker 首尾端口验收逻辑。
- Result:
  - 双后台节点、完整包、XXL 诊断和 AI 文档回归全部通过；固定名完整包已在 Mac 重封并通过 SHA-256、ZIP CRC 和节点内容校验，企业现场仍需先在 `.4`、再在 `.114` 逐台原地改配置和重建 worker。
  - 不清理 `/data/testagent/data`、manager state 或用户数据；不修改 Java、HTTP API、RunEvent、数据库/Flyway、generated SDK、鉴权或 `.env.local`。

### 2026-07-23 - 固化 Redis Docker IPv4 转发前置检查

- Why:
  - 企业 `.20` 的 Redis 7 容器、本机 TCP 和 `0.0.0.0:6379` 均正常，但 `.4` 持续连接超时；抓包确认 SYN 已到 `.20` 且无 SYN-ACK，最终定位为 `net.ipv4.ip_forward=0` 阻断 Docker DNAT 转发，这类 Docker 网络问题现场已重复发生。
- What:
  - 复用现有 `deploy-redis.sh deploy/verify` 入口，在接触 Docker 前读取 `/proc/sys/net/ipv4/ip_forward` 并拒绝值不为 `1` 的宿主机，给出临时恢复和企业 sysctl 持久化提示，不自动修改系统网络策略。
  - 同步 Redis 离线手册、全量升级手册、企业部署入口、后端部署文档和 enterprise skill，固定“`.20` 本机验证后仍从 `.4/.114` 跨机验证”的停止点，并区分 `ip_forward`、`FORWARD/DOCKER-USER` 与上游 VLAN/ACL。
- How:
  - 扩展 Redis 部署 verifier，动态覆盖 `deploy/verify` 在 `ip_forward=0` 下都提前失败；离线包 verifier 和 AI 文档校验强制检查新脚本及手册内容。
- Result:
  - Shell 语法、Redis 部署脚本、Redis 离线包、AI 文档和 `git diff --check` 全部通过；未改 API、事件、数据库、环境配置或 Redis 数据，当前现场仍需由系统管理员把 `.20` 的转发值持久化并完成 `.4/.114` 跨机验证。

### 2026-07-23 - 修复 Redis 配置 bind mount 的 Linux UID 权限错误

- Why:
  - 现场绕过旧 Docker `--platform` 报错后，Redis 容器又因宿主机敏感配置保持 `0600`、容器内 `redis` 用户无法读取 bind mount，报 `can't open config file ... permission denied`。
- What:
  - 复用现有 `run_redis_container`，保留宿主机配置 `0600`，容器启动时先复制到容器临时文件、改为 `redis:redis` 所有权，再用镜像内 `/usr/bin/setpriv` 切换到 redis 用户；同步更新 Redis/全量手册、企业部署 skill 和包/脚本回归。
  - 现场可先把原配置复制为 `/data/testagent/redis/config/redis.conf`，设为 `0400`、`999:1000` 后用该 `--config-file` 原地恢复，不改原包文件权限。
- How:
  - Shell、部署/封包、AI 文档校验通过；真实 `0600` 配置 bind mount + Redis 5 双 DB RDB→Redis 7 AOF 转换、重启、key 核对、GETDEL 和停止均通过。
- Result:
  - 权限问题已在脚本层解决，不需要开放配置到 `0644`；本次按现场要求暂不重封或重新导入包，后续生成新 Redis/平台包时必须携带本次脚本版本。

### 2026-07-23 - 修复 Redis 离线部署对旧 Docker experimental platform 的依赖

- Why:
  - 企业 `.20` 在 Redis 5 RDB 转换阶段再次出现 `"--platform" is only supported on a Docker daemon with experimental features enabled`；现场为 `x86_64`，镜像已校验为 `linux/amd64`，运行期 `--platform` 属于重复约束并阻断旧 daemon。
- What:
  - 复用现有 `run_redis_container`，移除目标机 `docker run` 的 `--platform`，保留部署前镜像 OS/架构强校验；封包脚本在 Mac 拉取和构建阶段仍固定 `linux/amd64`。
  - 部署脚本、Redis 手册、全量手册、企业部署 skill 和回归测试同步固定旧 Docker 兼容规则；一并修正无密码 Redis 仍导出空 `REDISCLI_AUTH`、盘点遗漏 `/etc/redis/6379.conf` 的现场问题。
- How:
  - Shell 语法、Redis 部署/封包和 AI 文档校验通过；真实使用 Redis 5 双 DB RDB 运行修正后的脚本，完成 Redis 7 AOF 转换、重启、2 个 key 核对、GETDEL 和停止清理，全程未传运行期 `--platform`。
- Result:
  - 旧 Docker daemon 不再需要开启 experimental features；现场失败发生在容器创建前，已确认无同名容器、数据目录只有原 `dump.rdb`，可在替换脚本后原地重试。Redis 敏感包通过 `--zip-only` 保留原密码和镜像重封，最终哈希以配套 `.sha256` 为准。

### 2026-07-23 - 修复 XXL 平台 SSO 通用错误页二次异常

- Why:
  - 企业浏览器在 `/xxl-job-admin/platform-sso/login` 报 `window.parent.$.adminTab` 未定义；确认该脚本来自上游通用错误页，是平台 SSO 服务端异常后继续产生的二次前端异常，会掩盖真正首因。
- What:
  - `PlatformXxlSsoController` 统一接管运行时异常，记录不含票据、用户或令牌的结构化完整异常，并返回平台自有 `503 + unavailable` 状态页；补充 MockMvc 回归，覆盖票据消费等登录步骤异常时不再落入上游错误页。
  - 同步 XXL 集成 README、测试说明和企业排查手册；在前端编码规范固定 Chromium 108 最低基线，并要求跨窗口全局对象逐级判空、iframe SSO 统一使用平台状态页和 `postMessage`。
- How:
  - JDK 25 下运行 `mvn -pl test-agent-xxl-job-integration -am -DskipITs test`，37 个 XXL 集成测试通过，包含真实 Tomcat 子上下文和 MySQL 8.4 Testcontainers；`bash tools/verify-internal-xxl-job-diagnostics.sh` 与 `git diff --check` 通过。
  - 按 `.env.test` 和 `test` profile 重启本地三服务，health/readiness、前端 3000、登录 CORS 与 manager WebSocket 均正常。
- Result:
  - 平台 SSO 再发生服务端运行时异常时不再复现 `adminTab` 二次报错，并可用稳定日志标记回查首因；本次不修改上游源码，不涉及 API、RunEvent、数据库结构/SQL、generated SDK、性能或权限边界，安全上仅新增脱敏错误日志。

### 2026-07-23 - 修正 XXL 排查手册的外部 MySQL 拓扑

- Why:
  - 现网 XXL MySQL 已切换到外部 `122.210.106.43:3306/xxl_job`，但随后新增的排查手册和诊断脚本仍硬编码旧 `.148`，现场探测因此产生无效 TIMEOUT 结论。
- What:
  - 统一修正排查手册、入口/后台诊断脚本、专项 verifier、测试说明及排查设计/计划；明确外部 MySQL 不在平台节点部署容器，DBA 只从获准客户端执行只读 SQL。删除企业部署入口中“当前 XXL MySQL 由容器脚本管理”的冲突表述。
- How:
  - 复用既有三套只读诊断脚本和 `verify-internal-xxl-job-diagnostics.sh`，同步正常/错误地址夹具、证据文件名和安全负向契约；全仓稳定部署文档及工具扫描不再出现旧 `.148`。
- Result:
  - `bash tools/verify-internal-xxl-job-diagnostics.sh`、相关 Shell `bash -n`、`git diff --check` 均通过；不涉及 API、RunEvent、数据库结构/SQL、generated SDK、性能或凭据变更。

### 2026-07-22 - 原始输出倒序跟随与主思考面板滚动

- Why:
  - 用户要求原始输出按时间倒序且新记录默认可见，并修复只有主智能体思考面板无法向下查看的问题，同时保留子智能体既有聊天区滚动。
- What:
  - `FigmaChatPanel` 复用会话级有界缓存，按 `occurredAt` 倒序派生展示、筛选和下载；打开浮层或新增记录时回到顶部。`agent-chat` 只给主智能体使用的 `.oc-work-status-dock` 增加 `min(360px, 50vh)` 限高与纵向滚动，子智能体 inline 时间线不变。
  - 同步 frontend、agent-web、agent-chat、PACKAGE、前端规范和模块地图，并补充排序不变性、展示/下载顺序、实时置顶及 dock 样式回归。
- How:
  - 定向 Vitest 3 文件 181 passed / 1 skipped、全量 lint、两个目标包 typecheck、前端生产构建和 JDK 25 后端 20 模块跳过测试构建通过；按 `.env.test` 重启三服务，health/readiness、前端 3000、CORS、manager WebSocket 与 Vite 实际服务源码正常。
- Result:
  - 两项交互已实现且运行服务已加载最新代码；全量 Vitest 仍有任务外存量 `DirectoryRows` 用例把实际 `radio`“上传”按 `button` 查询而失败。Browser 插件因本机 `Cannot redefine property: process` 未完成自动化点击复验；不涉及 API、RunEvent、数据库、SQL、generated SDK、安全或兼容性契约。

### 2026-07-22 - 基于公共 Agent 发布修复重打四节点完整离线包

- Why:
  - 用户尚未部署上一包，要求按当前最新 `main` 重新打固定名企业完整包，并继续提供 `.147/.4/.114/.2` 的逐台执行和验证清单。
- What:
  - 从干净 worktree 的 `52b732848a6f89d3216e0deb074b6b2f94350b6c` 全量重建后端 JAR、同源前端、programs、Linux/amd64 worker 和 MySQL 8.4 镜像；复用上一未部署包中已校验的四节点敏感配置，避免数据库、Redis、MySQL 密码和 XXL token 漂移。
  - 固定名外层 ZIP 覆盖到 `deploy/internal/dist/` 与 `/Users/kaka/Desktop/qr-decode/out/`；包内继续保留 JAR 内置 RSA、顺序 Flyway `V20260722130000`、`.4` 首节点延后 peer 校验和四份小于 1 MiB 的节点包。
- How:
  - 运行 MySQL、多后台节点、完整包和 AI 文档回归；逐层校验外层/内层 SHA 与 CRC、四节点 SHA/大小、MySQL 大 ZIP 校验、RSA、Flyway、最新前端诊断文案和两个 Docker tar 结构。
- Result:
  - 外层 ZIP SHA256 `5c8770f43bd22c0a619b7dd1e8c4e2557b7505338adf786ba3f60275b6998af5`，内层 release `107f5a4a87187966ddbcd5476342859172504aa659aa170ec01ae719fc359c32`，JAR `fb4b56f8a6733bb7675d7a5536752eed1a65f14a7efec3bafe5959a924d332e4`，前端归档 `c442d60502c9e86e3192a80f2a3e85aa79fc9fe2041b06e0613534feb81affd4`。
  - Mac 构建和封装验证完成；企业现场尚未部署，必须按 `.147 MySQL -> 停两台旧 Java -> .4 -> .114 -> .2` 顺序执行并完成真实 systemd、Docker、Nginx 与浏览器验收。

### 2026-07-22 - 重打公共 Agent 排空优化双后台固定名包

- Why:
  - 用户要求基于当前最新代码重新生成企业双后台完整离线包；上一包基线为消息门禁 SQL 修复提交，尚未包含后续文件分片上传和公共 Agent 发布排空优化。
- What:
  - 从干净 worktree 的 `98866da441379aed145c4d05460739214726861b` 重建后端 JAR、用户手册和空 API base 同源前端，复用上一包已验证的 `.4/.114/.2` 受控配置并覆盖固定名完整包。
  - `86423b4e2..98866da44` 未修改 worker、programs 或部署脚本，因此复用已验证的 Linux/amd64 worker/programs；JAR 继续内置 RSA，节点 env 不配置外置 RSA 路径。
- How:
  - 运行 Nginx、单机配置、自动节点、多后台节点、固定名封装和 AI 文档回归；最终校验内外层 SHA/压缩完整性、构建产物逐字节一致、三节点 `--validate-only`、systemd 首装/升级和配置归档小于 1 MiB。
- Result:
  - 外层 ZIP SHA256 为 `58525ea01f83a4ac4b8aeed209b442cddd3d08f2372539ee2403051e1446b4cb`，内层发布 ZIP 为 `e1c222fe6412d16f4340534fef3a8a9a5ec6826d7b65ea06e034d200d042be56`，JAR 为 `601914150ba8e5fc5b3a03a0fee77849a75acd7bcdce95d9cc5a4e655a12939f`，前端归档为 `27743b611903974d03003e3117c1f282e59e86ad6bb8b109ca5b2952c5a8dd87`。
  - `.4/.114/.2` 节点配置包分别为 `22705/22703/20536` 字节；Mac 构建与封装验证完成，企业现场仍需按 `.4 -> .114 -> .2` 执行真实 systemd、Docker、Nginx 部署和业务验收。
  - 本次只更新交付记录；工作区原有 `.agents/skills/restart/SKILL.md` 修改保持未暂存、未覆盖。

### 2026-07-22 - 直连 ICBC personal OpenAI-compatible 行内模型配置

- Why:
  - 用户要求不修改业务代码，仅通过 OpenCode JSONC 和运行环境配置切换到 ICBC `personal/v1` OpenAI-compatible 接口；上游模型由令牌绑定。
- What:
  - 将本机当前及两个开发配置快照中的 `icbc-openai` provider 切换到 `/icbc/jdt/model/api/openai/personal/v1`，请求头改为原始 `Authorization`，模型配置仅作为 OpenCode 的 provider/model 路由标识。
  - 同步本机实际被 OpenCode 1.17.8 读取的 `~/.config/opencode/opencode.jsonc`；该版本临时实例验证表明其实际加载全局配置，而不是 manager 传入的 `OPENCODE_CONFIG_DIR` 配置目录。
  - 令牌未写入仓库、JSONC 或日志；JSONC 引用受管启动链路已有的 `TEST_AGENT_INTERNAL_PROXY_API_KEY` 环境变量。
- How:
  - 使用 OpenCode `debug config` 解析校验，并启动临时 4196 端口实例检查 `/api/provider` 和 `/api/model`；provider、路由模型和新基地址均已出现。
  - 按项目启动脚本尝试重启三服务，使用非敏感占位值验证 manager 启动链路；后端因 PostgreSQL 连接 `EOFException` 启动失败，未完成真实上游请求验证。
- Result:
  - 配置层已部分验证；真实 ICBC 调用仍需在数据库恢复后，将新令牌安全配置到 `TEST_AGENT_INTERNAL_PROXY_API_KEY` 并重启受管 OpenCode。用户提供的令牌已在会话中暴露，后续应先申请/轮换新令牌。

### 2026-07-22 - 取消六个测试 Agent 的固定迭代步数上限

- Why:
  - 测试设计和测试执行子智能体可能在业务工作未完成时达到 `steps` 上限，被 OpenCode 强制转为文本总结；Task 返回后主智能体会继续接手。
- What:
  - 删除测试设计四个 Agent、测试执行两个 Agent 的 `steps`，保留 `permission."*": allow` 和主智能体现有接手能力；按用户决定暂不增加程序级完成门禁。`test-design` 版本提升到 `3.8.4`，公共配置 README 同步。
  - 从公共配置提交 `d002a60` 重新生成 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及 SHA，覆盖上一版替换包。
- How:
  - 只修改现有 Agent frontmatter，不新增代码或编排包装层；使用 OpenCode 1.17.7 检查六个解析结果均为 `steps=None`，并复验外部规约读取和 Skill 加载。
- Result:
  - 六个 Agent 不再因固定迭代步数被强制结束；模型仍可自行结束，用户仍可中断，平台请求和 Run 基础设施超时不变。
  - ZIP 与提交逐文件一致，包含 7 个 Agent、12 个 Skill（Skill 目录共 59 个文件），完整性、禁止路径和凭据特征扫描通过；SHA256 为 `e9adbd2f3b2f8ff0a6dd10a251c6fd4802b0544501708baa984745e942947121`。
  - 未修改 API、RunEvent、数据库、SQL、generated SDK、Java/前端代码或环境配置；企业现场替换和 worker 重启仍待执行。

### 2026-07-22 - 基于消息门禁 SQL 修复重打双后台同源完整包

- Why:
  - 企业 `.4`、`.114` 是同源克隆节点，用户要求同一缺陷两台一起处理，禁止分别使用不同版本 JAR 或依赖库。
- What:
  - 从修复提交 `86423b4e2` 全量重建后端、同源前端、programs 和 Linux/amd64 worker，复用两台后台及前端既有受控节点配置，覆盖固定名双后台完整包并同步到 `deploy/internal/dist/` 与 `/Users/kaka/Desktop/qr-decode/out/`。
- How:
  - JDK 25 下运行 `package-release.sh` 和 `package-two-backend-complete.sh`；校验外层/内层 ZIP、节点配置保留、两后台共享配置与 manager token 一致，并直接解包确认 mapper 只含 `pw.app_workspace_version_id`、不含 `pw.version_id`。
- Result:
  - 外层 SHA256 `f0fe2cc58f638122ac7c333511f65ab996ba4a0f239364ffbc127a50e5af4b37`，内层 release `4ce670d99d0a243a8dd72b19e571b2bf5c65d044bb47ae62bb28bc2e4c7574ae`，app JAR `159480cd0051eab9b6f1b57d29add3d246f5a51cd5f3fd760b222d309baf1f55`；Mac 构建验证完成，企业两节点滚动部署与 Docker IPv4 forwarding 修复仍待现场执行。

### 2026-07-22 - 落实初始化进程按钮缺失的 SQL 修复

- Why:
  - 本地三服务均在运行，但前端没有显示 TestAgent 进程初始化入口；后端日志显示 `/processes/me/message-gate` 因 `personal_workspaces` 不存在 `version_id` 列而失败。
- What:
  - 将 `PublicAgentConfigRolloutMapper.xml` 的个人工作空间关联改为 `pw.app_workspace_version_id = v.version_id`；新增 XML 绑定 SQL 回归断言，并同步 persistence README 的字段关系说明。
- How:
  - 复用既有 MyBatis mapper、用户状态查询和宠物初始化入口，不改 API、事件、数据库结构或环境配置；提交前回顾全部 session log，保留工作区原有的 `.agents/skills/restart/SKILL.md` 修改。
- Result:
  - 定向 MyBatis 测试 6/6、前端 FigmaShell/FigmaChatPanel 182 通过/1 跳过；JDK 25 下 18 模块生产构建和 `.env.test` 三服务重启成功，health/readiness、前端 3000、CORS、manager WebSocket 均正常；重启后无新增该 SQL 错误。
- Next:
  - None

### 2026-07-22 - 诊断企业后台升级日志中的 SQL 与停机心跳异常

- Why:
  - 企业节点执行离线部署后同时出现消息门禁 PostgreSQL 错误、停机阶段 `LettuceConnectionFactory has been STOPPED` 和 Docker IPv4 forwarding 告警，需要区分业务缺陷与部署收尾噪声。
- What:
  - 消息门禁 `findBlockingRolloutId` 误用不存在的 `personal_workspaces.version_id`，真实外键是 `app_workspace_version_id`；部署脚本主动停止旧服务后，Redis lifecycle 已停止但 5 秒心跳尚未销毁，晚到心跳才产生 Lettuce 错误。
- How:
  - 对照 V9 表结构、既有 `SessionHistoryMapper`、`PublicAgentConfigRolloutMapper.xml`、部署脚本 stop/start/health 顺序和心跳销毁时机；本地已有的字段修正及防回归测试并非本次创建，定向 6 项测试通过。
- Result:
  - 新 Java health/readiness、worker health 和 manager 配置均正常，但当前企业坏包的消息门禁仍可能阻断新消息；必须交付包含正确 SQL 的新 JAR。Lettuce 堆栈不是启动失败，Docker `ip_forward=0` 且无持久出站 unit 仍需网络侧处理。

### 2026-07-21 - 隔离公共与应用配置发布并异步收敛个人 worktree

- Why:
  - 企业双后台中一次应用 Agent 发布因个人 worktree 存在本地修改或合并冲突长期停在 `RETRY_WAIT`，旧的全局唯一活动 rollout 同时阻止无关公共配置拉取；直接初始化不经过该门禁，因此形成“初始化可更新、拉取持续报正在排空”的设计缺陷。
- What:
  - rollout 活动锁改为公共范围独立、应用范围按版本 ID 隔离；应用个人 worktree 未收敛时持久化为独立补偿任务，服务器共享副本仍可标记 `SYNCED` 并完成主 rollout，不再占用公共发布或其他应用版本。
  - 新增 worktree 认领、租约、重试、等待用户、同步和放弃状态；后台在用户提交/丢弃本地修改或解决冲突后自动合入固定目标提交，再只登记该用户旧进程的延迟 dispose。已完成应用 rollout 仍可处理后到 target，门禁只影响对应应用成员和对应用户。
  - 新增 PostgreSQL Flyway `V20260721213000`，SQL 全部落在 MyBatis XML；同步 runtime/persistence README、HTTP/事件语义、数据库/后端部署文档和应用 worktree 测试案例。
- How:
  - 复用既有 rollout coordinator、个人 worktree 原生 Git 合并、进程快照与 dispose 状态机；不扫描 Redis 私有快照、不新增跨服务器文件代理、不覆盖或重置用户本地修改，也不手工改存量 rollout 数据。
- Result:
  - 定向回归 `ManagedWorkspaceApplicationServiceTest` 57 项、`PublicAgentConfigRolloutServiceTest` 24 项、MyBatis rollout 仓储 5 项全部通过；相关 reactor 中 workspace、runtime 等业务模块全量通过，persistence 全套仍被旧迁移 `V20260717173000` 的 `timestamptz` 与 H2 不兼容基线阻断 76 项，与本次迁移无关。
  - JDK 25 下 18 模块生产打包、前端生产构建通过；使用 `.env.test` / `test` profile 启动 backend、opencode-manager、frontend，Flyway 已把真实 PostgreSQL 更新到 `20260721213000`，readiness 为 `UP`、前端返回 200。
  - 涉及数据库结构与发布/门禁兼容语义；未新增 HTTP API 或 RunEvent 类型，未修改 generated SDK、鉴权、安全边界或环境配置。真实企业双节点现场待两台 Java 同版部署后由定时任务自动接管原 `acr_8c2caacc30954278812ca915a4062b5b`。

### 2026-07-21 - 修正测试 Agent 为无 deny 的全量权限并重打替换包

- Why:
  - 上一版只放行 `external_directory` 和 `skill`，仍保留测试设计顶层 `"*": deny`、敏感文件读取限制及 Task 白名单，测试执行也保留 `ask`，不符合企业内部测试“无需限制任何 deny、直接全量放行”的明确要求。
- What:
  - 测试设计四个 Agent 与测试执行两个 Agent 的 permission 统一精简为唯一的 `"*": allow`，移除全部 `deny`、`ask` 和按工具/子智能体白名单；`test-design` 版本提升到 `3.8.3`，公共配置 README 同步真实权限口径。
  - 从公共配置提交 `f8a2ff6` 重新生成 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及 SHA，覆盖上一版替换包。
- How:
  - 复用 OpenCode 原生通配权限，不新增运行时代码；使用 OpenCode 1.17.7 在临时业务目录分别验证六个 Agent 的外部规约读取与公共 Skill 加载，并检查解析结果包含通配 allow。
- Result:
  - 六份 YAML frontmatter 均精确解析为 `permission == {"*": "allow"}`；12 个 Skill 元数据和相对规约引用有效。
  - ZIP 包含 7 个 Agent、12 个 Skill（Skill 目录共 59 个文件），与提交逐文件一致，压缩完整性、禁止路径和凭据特征扫描通过；SHA256 为 `fb68054de0a73854bb0a3337ea6c7ca26667f02fdd54a56619a17117f8cb8dc5`。
  - 未修改 API、RunEvent、数据库、SQL、generated SDK、Java/前端代码或环境配置；全量权限会允许六个 Agent 使用所有工具、读取外部目录及原先被保护的环境/OpenCode 配置文件，仅适用于用户指定的企业内部测试环境。

### 2026-07-21 - 全量放行测试 Agent 外部目录与 Skill 并生成替换包

- Why:
  - 企业内除测试设计外，测试执行 Agent 也需要稳定读取公共 Skill 的 rules/templates；用户明确要求测试 Agent 的外部目录与 Skill 调用全量放行，并提供可批量替换的 ZIP。
- What:
  - 测试设计与测试执行共六个 Agent 统一为 `external_directory: allow`、`skill: allow`，Task 编排白名单保持不变；`test-design` 版本提升到 `3.8.2`，公共配置 README 同步。
  - 生成 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及 SHA，只包含公共 README、`opencode/agents/**`、`opencode/skills/**`，不包含 `opencode.json(c)`、tools、node_modules、Git 元数据或 provider 配置。
- How:
  - 复用 OpenCode 原生 Agent permission 与公共配置 Git，不改运行时代码；从公共配置提交 `fd9fad5` 直接 `git archive`，避免把未提交文件或密钥带入包。
- Result:
  - 六个 Agent 真实读取外部 Skill 资源及加载任意公共 Skill 均 6/6 通过；12 个 Skill frontmatter 和相对 rules/templates 引用通过。
  - ZIP 包含 7 个 Agent 文件、59 个 Skill 文件，逐目录 `diff`、压缩完整性、禁入路径和凭据模式扫描通过；SHA256 为 `b116d03f01bd2f8d1d61748962e2b81b24dd225e825dfd65cafe85ee06db295e`。
  - 未修改 API、RunEvent、数据库、SQL、generated SDK、Java/前端代码或环境配置；全量放行扩大了六个 Agent 的外部文件读取和 Skill 调用范围。

### 2026-07-21 - 修复企业测试设计 Agent 读取公共规约权限

- Why:
  - 企业内案例设计时，测试设计主 Agent 及三个阶段 Agent 能加载 `test-design` Skill，但读取其 `rules/`、`templates/` 时被提示外部目录权限受限。
- What:
  - 公共配置仓库的四个测试设计 Agent 显式设置 `external_directory: allow`，保留 `.env` 与 `opencode.json(c)` 的 read deny；`test-design` 版本提升到 `3.8.1`，README 同步权限约束。
- How:
  - OpenCode 1.17.x 权限按最后匹配规则生效，Agent 自身的 `permission."*": deny` 会覆盖运行时为公共 Skill 目录生成的外部目录 allow；按用户确认对四个 Agent 全量放行 external directory。
- Result:
  - 四份 YAML frontmatter 解析通过；本机 OpenCode 1.17.7 在临时业务工作区中分别以四个 Agent 真实读取公共 `rules/workspace-layout.md`，4/4 通过。
  - 完整后端构建被工作区中并行未提交的 `WorkspaceFileService` 上传重构缺失符号阻断；未修改该任务外代码，改用既有 JAR 重启 backend、manager、frontend 成功。本次未改 API、RunEvent、数据库、SQL、generated SDK 或环境配置；全量 external directory 放行扩大了四个 Agent 的文件读取范围。

### 2026-07-21 - SCM 跳转改为 HTTPS 并补个人 worktree 回收指引

- Why:
  - SCM GMP 权限申请需要改用 HTTPS；同时用户确认移除应用成员后服务器个人 worktree 仍保留，需要明确安全回收方式。
- What:
  - 权限申请弹框及桌面/移动端回归统一改为 `https://scm-gmp.sdc.cs.icbc/icbc/gmp/index.jsp#@`。
  - 明确成员删除只撤销 `application_members`，保留个人工作区、运行态 Workspace、历史 Session 和物理 worktree；在后端部署文档增加按用户/应用只读定位、停止用户进程、检查 dirty 状态和使用无 `--force` 的 `git worktree remove` 回收磁盘步骤。
- How:
  - 复用现有 HTTPS 新窗口跳转及 default worktree 缺失修复能力；不在成员删除入口自动清理，因为该入口无法安全处理未提交内容、多服务器归属、活动进程和历史归属，也不建议现场删除数据库记录或直接 `rm -rf`。
- Result:
  - Git 权限 Playwright 桌面/移动端 2 项、agent-web typecheck 和生产构建通过；`.env.test` / `test` / JDK 25 重启三服务后 health/readiness、前端 3000、CORS 和 manager 日志正常。
  - 未新增或修改 HTTP/RunEvent/数据库/SQL/generated SDK/环境配置；只澄清成员删除既有语义及磁盘回收运维步骤。

### 2026-07-21 - 基于拉取后最新代码重打双后台固定名包

- Why:
  - 用户在拉取主分支最新代码后要求重新打包；打包期间又提交了版本库权限申请直达 SCM 的前端改动，因此需以最终最新提交重新生成企业离线交付物。
- What:
  - 后端从拉取后的 `0fb851e157ee2758662cd73b7fe964a724da0ae1` 隔离构建；确认后续 `80b250e6c03cf2605b86feca93ed497cea43b435` 只修改前端和文档后，从该最终提交重新构建用户手册及空 API base 的同源前端，覆盖固定名 `test-agent-two-backend-complete.zip` 及 SHA。
  - 复用 `.4/.114/.2` 三份受控节点配置；worker/programs 源码相对上一交付基线未变化，因此复用已验证的 Linux/amd64 产物。JAR 继续内置 RSA，节点 env 不配置外置 RSA 路径。
- How:
  - 运行 Nginx、单机配置、自动节点、多后台节点、固定名封装和 AI 文档回归；最终执行内外层 SHA/压缩完整性、构建产物逐字节比对、当前部署脚本同源、三节点 `--validate-only`、systemd 首装/升级及节点配置小于 1 MiB 校验。
- Result:
  - 外层 ZIP SHA256 为 `ecb5c84b6a77dfee89e1a2ff07100dacf69cdee84f4cb21409f938c0d455e59e`，内层发布 ZIP 为 `d4f7adac8ccf77dbf4411c7ab4df8d500ac4b8cd68f86fdd8b25824a2035b4ea`，JAR 为 `bb236df73f4116b3ff5d11aa9025616b7ffbee3ff59f0cb448b5d303124f6fb4`，前端归档为 `8cd225d94374e4c6c70b3c09843896cf280dfcec54a2f3fcf2c431b600fb722a`。
  - `.4/.114/.2` 节点配置包分别为 `22411/22411/20387` 字节；本地构建与封装验证完成，企业现场仍需按 `.4 -> .114 -> .2` 执行真实 systemd、Docker、Nginx 部署和验收。
  - 本次只更新交付记录，不修改 API、RunEvent、数据库/Flyway、generated SDK、环境配置或业务代码。

### 2026-07-21 - 版本库权限弹框直达 SCM GMP

- Why:
  - 版本库权限预检弹框原先只写“前往开发者门户”，用户无法从弹框直接进入企业 SCM 权限申请页面。
- What:
  - 无版本库读取权限时展示 SCM GMP 地址 `http://scm-gmp.sdc.cs.icbc/icbc/gmp/index.jsp#@`，将确认按钮改为“前往申请”并复用现有 `window.open(..., "_blank", "noopener,noreferrer")` 外链方式；取消后仍停留在当前工作区且不创建 worktree。
  - 同步 agent-web README/PACKAGE，并扩展桌面/移动端回归验证地址、按钮和安全新窗口参数。
- How:
  - 仅扩展既有 `ElMessageBox` 权限分支，不新增 API、路由或导航封装；同时复核现有应用成员可在设置页按人逻辑删除，且该平台成员权限与 SCM 仓库权限相互独立。
- Result:
  - Git 权限 Playwright 2 项、agent-web typecheck/生产构建、设置页成员管理 Vitest 15 项、成员服务 23 项及跨模块撤权 1 项通过。
  - 使用 `.env.test`、`test` profile 和 JDK 25 重启三服务；health/readiness 为 UP、前端 3000、CORS 和 manager 日志正常。不涉及 HTTP/RunEvent/数据库/SQL/generated SDK/环境配置变更。

### 2026-07-21 - 应用版本选择前增加 Git 权限预检

- Why:
  - 用户选择应用版本时，原流程会直接创建或切换个人 worktree；若当前用户没有关联版本库权限，只能在后续 Git 操作失败后获知，且提示不够明确。
- What:
  - 新增版本 Git 访问预检接口，按当前用户的仓库地址和 SSH key 只读探测远端；认证失败或仓库不可访问返回申请版本库权限结果，缺少 SSH key 返回独立配置提示，网络及超时仍按统一 Git 异常处理。
  - 前端在版本选择产生任何 worktree 副作用前调用预检；无权限时弹框展示具体版本库名称并引导前往开发者门户申请，缺少 key 时引导至个人设置，校验通过后才沿用既有默认个人工作区流程。
  - 同步 workspace-management、API、backend-api、agent-web、HTTP API 与模块地图文档，并补齐后端服务、Controller、API 客户端及桌面/移动端交互回归。
- How:
  - 复用 `GitRemoteService.listBranches()`、当前用户唯一 SSH key、内部仓库有效 URL 和既有 Java 路由；不创建第二套 Git 命令、不返回仓库地址或密钥，也不改 generated SDK、事件或数据库。
- Result:
  - 后端聚焦 71 项、backend-api 78 项和 Playwright 桌面/移动端 2 项通过；backend-api/agent-web typecheck、agent-web 生产构建、后端完整跳过测试打包及 `git diff --check` 通过。
  - 使用 `.env.test`、`test` profile 和 JDK 25 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 返回 200、CORS 正常、manager WebSocket 已连接且当前日志无新错误。
  - 新增一个兼容性的只读内部 HTTP API；不涉及 RunEvent、数据库、SQL、环境配置或权限模型变更。每次显式选择版本会增加一次 Git 远端只读探测。

### 2026-07-21 - 强制企业 Git 提交显式传入身份

- Why:
  - 企业 SCM 会校验提交者邮箱；提交服务仍保留不传身份的兼容入口时，后续调用可能退回服务器 Git 默认配置并再次生成无法推送的提交。
- What:
  - 删除 `GitWorkspaceService` 和 `GitPublishWorkflow` 中所有缺省提交身份的兼容重载及空值回退，所有可能生成 commit 的提交、合并和发布入口统一要求非空 `GitCommitIdentity`。
  - 补充空身份失败关闭、身份透传及真实 Git 作者/提交者邮箱回归测试，并同步 common、workspace-management 模块稳定文档。
- How:
  - 继续复用现有 `GitCommitIdentity.forPlatformUser`，只对单次 Git 命令注入当前操作人身份，不修改仓库或全局 Git 配置；未新增 API、事件、数据库字段或迁移。
  - 定向 145 项、common/domain/workspace 全量 395 项测试及后端 18 模块跳过测试打包通过。
- Result:
  - 新代码无法再通过缺省入口创建使用服务器默认邮箱的提交；应用 Workspace/应用 Agent 旧失败提交不需要数据库迁移，升级后可由发布流程重新投影并生成正确身份的 feature 提交。
  - 企业存量公共 Agent 个人 worktree 若含尚未推送的 `@testagent.local` 提交，仍需逐仓库备份并重建提交后再发布；远端已拒绝的提交不在远端历史中，不需要强推或迁移远端数据。

### 2026-07-21 - 修复高行数文件 WebSocket 帧误关闭

- Why:
  - 文件 WebSocket 单帧上限只按上传 Base64 的 4/3 膨胀估算；文本保存经过 JSON 序列化后，换行或控制字符会进一步转义，导致仍在 1 MiB 业务上限内的高行数文件先被传输层关闭，前端只能看到 WebSocket 关闭。
- What:
  - 共享 WebSocket adapter 改为按文本 JSON 控制字符最坏 6 倍转义量加 64 KiB RPC envelope 配置单帧上限，同时覆盖 Base64 上传；UTF-8 或解码后文件的 1 MiB 默认业务限制保持不变。
  - 新增基于实际 Jackson 序列化结果和实际 Reactor Netty server spec 的控制字符、高行数文本回归测试，并同步 API 与模块稳定文档。
- How:
  - 继续复用现有 route/ticket/RPC、`WebSocketHandlerAdapter` 与 `WorkspaceFileService` 大小校验，没有新增文件 HTTP 代理、分片协议或前端旁路。
  - 定向 `TerminalWebSocketConfigTest,WorkspaceFileWebSocketHandlerTest` 19 项及 `test-agent-api -am` 全量 340 项测试通过，后端 18 模块跳过测试打包成功，`git diff --check` 通过。
- Result:
  - 使用 `.env.test`、`test` profile 和 JDK 25 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 返回 200、CORS 正常、manager WebSocket 已连接。
  - 未修改 API 字段、事件类型、数据库、generated SDK、环境配置或文件权限边界；默认全局 WebSocket 单帧上限由约 1.40 MiB 调整为约 6.06 MiB，文件业务上限仍为 1 MiB。

### 2026-07-21 - 应用与公共 Agent 支持全部暂存

- Why:
  - Git Changes 仅应用 workspace 有“全部暂存”，应用 Agent 与公共 Agent 仍需逐文件操作。
- What:
  - 两类 Agent 未暂存分组新增“全部暂存”，一次提交当前作用域全部未暂存路径；无权限、冲突或 index 更新中禁用，并补齐进行中防重复状态、组件回归和稳定文档。
- How:
  - 抽取并复用单文件暂存程序，继续调用既有 `stageWorkspaceAgentFiles` / `stagePublicAgentFiles` 批量 API，不新增后端接口、配置分支或跨作用域状态。
- Result:
  - GitChangesPanel 38 项、agent-web typecheck、用户手册与生产构建、后端 18 模块跳过测试打包通过；`.env.test` / `test` 重启后三服务 health/readiness、前端 3000、CORS 和 manager WebSocket 正常。
  - 前端全量 Vitest 为 1448 passed / 1 skipped / 1 failed；唯一失败是既有 `DirectoryRows` 用 `button` 查询实际 `radio` 角色的“上传”，单独复跑稳定复现，与本次改动无关，未扩大范围修复。

### 2026-07-21 - 重打包含用户安全删除的双后台固定名包

- Why:
  - 用户要求再次重打企业双后台完整包；最新业务提交新增用户安全删除与 TCDS 信息同步，需要替换上一版 `106f8b3dc` 构建物。
- What:
  - 从干净 worktree 的 `680a2a298` 重新构建后端 JAR、用户手册和前端，复用上一包已校验的 `.4/.114/.2` 受控配置，覆盖固定名 ZIP 与 SHA。
  - Nginx 配置继续使用 `TEST_AGENT_NGINX_SERVER_ROUTES` 精确路由，JAR 继续使用内置 RSA；worker/programs 源码未变化，复用已验证的 linux/amd64 交付物。
- How:
  - 运行 Nginx、单机配置、自动节点、多后台节点、固定名封装和 AI 文档回归；最终执行内外层 SHA/压缩完整性、三节点 `--validate-only`、systemd 首装/升级、脚本同源、JAR 内置 RSA 和配置大小校验。
- Result:
  - 外层 ZIP SHA256 为 `6aa61be641b734640ec518b4bfa1bcb20c6551356da0c72ee7c62076da91bb6c`，内层发布 ZIP 为 `f43369d4ef28bb81f3e649ec51cb6095c3f63dd38c9faa799ad7bf7847cb1b42`，JAR 为 `f50666006b268116b7e08ab029bbd869da1d0f94436ccc0c1242982cdabda435`。
  - `.4/.114/.2` 配置包分别为 `22406/22407/20380` 字节；本地验证完成，企业现场仍需按 `.4 -> .114 -> .2` 部署和验收。

### 2026-07-21 - 交付 Nginx 精确路由双后台固定名离线包

- Why:
  - 用户完成 Nginx 配置改造后，需要沿用既有 `.2/.4/.114` 现场参数，重新生成固定名完整离线包，并明确新配置生成、校验和逐机部署顺序。
- What:
  - 基于 `106f8b3dc` 隔离构建最新后端 JAR 与前端，完整包中的前端配置使用 `TEST_AGENT_NGINX_SERVER_ROUTES`，为 `.4/.114` 两个 `linuxServerId` 配置精确首跳路由。
  - 复用上一轮已校验的两台后台共享凭据和逐机身份，删除外置 RSA 配置；`.2` 不再携带旧 `TEST_AGENT_NGINX_TERMINAL_ROUTES`。
  - 固定名产物为 `/Users/kaka/Desktop/qr-decode/out/test-agent-two-backend-complete.zip` 及同名 `.sha256`；三份逐机配置包继续控制在 1 MiB 以内。
- How:
  - 后端和前端从当前提交实际执行 Maven/Vite 生产构建；worker/programs 对比 `3724ae37a..106f8b3dc` 无源码变化，因此复用已验证的 `linux/amd64` worker/programs 交付物。
  - 运行 Nginx、单机配置、自动节点初始化、多后台逐机和完整包封装回归；再对最终包执行内外层 SHA/压缩完整性、三节点 `--validate-only`、JAR 内置 RSA、当前部署脚本一致性和 Nginx 路由键检查。
- Result:
  - 最终包 SHA256 为 `9a7c3080d70c931f3204cd5644454c25b69e64c90334fc3e0fcf826f38e95ca2`；内层发布 ZIP SHA256 为 `cbe63aa1e0dfc1d17279fc7c52cd3125e4e89d68eb59ad70cd7c4b2bb567680d`。
  - `.4/.114/.2` 配置包分别为 `22407/22403/20375` 字节，均通过配置与发布物校验；尚未在企业现场执行真实 systemd、Docker 和 Nginx reload，必须按 `.4 -> .114 -> .2` 顺序部署并现场验收。

### 2026-07-21 - 重建包含 Agent 配置按钮对齐的固定名双后台包

- Why:
  - 用户要求再次重打企业双后台完整包；打包期间主分支新增 Agent 配置按钮对齐提交，需要以最新已提交代码重新构建，避免交付包遗漏该前端变更。
- What:
  - 从提交 `c15d288a89a7dc9a3dbf326ec7ce46a664d87193` 的临时干净 worktree 全量重建后端 JAR、同源前端、programs 和 Linux/amd64 worker，并复用三台既有受控配置覆盖固定名 `test-agent-two-backend-complete.zip` 及 SHA。
  - 外层结构和企业操作方式保持不变：一个 ZIP 内包含内层标准发布 ZIP 及 `.4/.114/.2` 三台节点包，节点包继续只含配置、逐机脚本和手册且均小于 `1 MiB`。
- How:
  - 实跑外层 ZIP 完整性与 SHA、内层发布 SHA、三节点 SHA、JAR 内置 RSA、worker `linux/amd64`、三节点 `--validate-only`、systemd 首装/升级和固定名重复覆盖回归。
- Result:
  - 新包位于 `/Users/kaka/Desktop/qr-decode/out/test-agent-two-backend-complete.zip`，约 `237 MiB`，SHA256 `a1515f0d389bed97d73bdb614080e5114f9d77be877ef674b4fb37069c06f348`；内层发布 SHA256 `f3b75328ab4667b30784024ff851e1a32f241a96ef70c9d6717e121297594e4f`，JAR SHA256 `66397b506b5239a5b91081ca68722d24a31167dbc1b7e2694a9db8e95bf274c5`。
  - 本机交付物与部署脚本已验证；企业三台服务器仍需按 `.4 -> .114 -> .2` 正式部署并完成浏览器验收。

### 2026-07-21 - 对齐 Agent 配置树与工作区操作按钮

- Why:
  - 用户指出统一新建/上传面板后，Agent 公共级和应用级的触发按钮仍未与应用工作区保持一致。
- What:
  - 公共级、应用级根入口由 `FilePlus2` 改为工作区同款 `Plus`，并对齐 20px 尺寸、4px 圆角、hover、focus 和过渡效果。
  - Agent 目录行新增/删除按钮对齐工作区 18px 规格、间距和交互反馈，删除按钮恢复一致的红色 hover 语义；组件测试锁定根入口使用 `Plus`。
- How:
  - 直接参照并复用 `FileExplorer.vue`、`DirectoryRows.vue` 现有按钮规格，只调整 Agent 配置组件，不新增按钮组件或全局样式。
- Result:
  - AgentConfigPanel 27 项、agent-web typecheck、用户手册与前端生产构建通过；`.env.test` / `test` profile 重启三服务后 health/readiness 为 UP、前端 3000 与 CORS 正常、manager WebSocket 已连接。
  - 应用内浏览器自动视觉检查仍受既有 `Cannot redefine property: process` 限制；图标由组件测试验证，CSS 值已与工作区源码逐项对齐。未修改 API、事件、数据库、安全边界、环境配置或依赖。

### 2026-07-21 - 统一公共与应用 Agent 新建上传能力

- Why:
  - 用户希望合并“新建文件”和“初始化 Agent/Skill”两个根入口，让公共 Agent 与应用 Agent 能力对齐；弹出面板和按钮需与应用工作区新建/上传一致，并说明普通条目与 Agent/Skill 模板的区别。
- What:
  - 公共级、应用级根统一为“新建或上传配置”，直接复用共享 `FileEntryCreateDialog` 样式，提供文件、文件夹、上传、Agent、Skill 五种操作；可写目录行提供文件、文件夹和上传。
  - 面板按当前选项说明：普通文件/文件夹只创建空白条目或整理素材，Agent 生成 `agents/<name>.md`，Skill 生成标准 `skills/<name>/` 配置包；公共/应用模板文案分别保留 public/application scope，英文名称不再逐字母加短横线。
  - 新增 `agent-config.upload` 文件 WebSocket RPC，复用既有 Base64、大小、不覆盖、重名和越界校验；应用上传在服务层限制为 `opencode.jsonc`、`agents/**`、`skills/**`。公共上传/改名要求 `SUPER_ADMIN`，应用上传/改名要求 `APP_ADMIN`（`SUPER_ADMIN` 继承）；普通用户界面隐藏入口且后端拒绝绕过调用。
  - 同步前后端 README/PACKAGE、HTTP 与文件 WebSocket 协议、安全规范和内置用户手册。
- How:
  - 复用共享文件面板、`WorkspaceFileService.uploadFile/renameFile`、Agent 配置 route/ticket/RPC 与既有 Git revision 刷新链路，没有新增 HTTP 文件代理或第二套模板/文件服务。
  - 前端定向 37 项、agent-web typecheck、用户手册和生产构建通过；`AgentConfigApplicationServiceTest` 47 项、`WorkspaceFileWebSocketHandlerTest` 17 项通过，后端 18 模块跳过测试打包成功，`git diff --check` 通过。
- Result:
  - 使用 `.env.test` / `test` profile / JDK 25 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 返回 200、CORS 和 manager WebSocket 正常。
  - 应用内浏览器自动视觉检查仍受既有运行时 `Cannot redefine property: process` 限制；共享面板实际复用、五种按钮、说明文案、上传事件与权限由组件/协议测试和真实服务启动验证。未修改数据库、migration、RunEvent、generated SDK、环境配置或依赖。

### 2026-07-21 - 基于最新提交重建固定名双后台完整包

- Why:
  - 用户要求重新打包，并继续只向企业内部导入一个固定名完整 ZIP 及其 SHA，随后按企业内部中转机、`.4`、`.114`、`.2` 的顺序逐步部署。
- What:
  - 从提交 `588097fc144b1770f8d1adcaa843fb090e6e6bfd` 的临时干净 worktree 全量重建后端 JAR、同源前端、外置 programs 和 Linux/amd64 worker；复用三台服务器既有受控配置重新封装固定名 `test-agent-two-backend-complete.zip`。
  - 包内固定根目录为 `test-agent-two-backend-complete/`，包含内层完整发布 ZIP、三台节点包及各自 SHA；节点包继续只包含配置、逐机脚本和手册，均小于 `1 MiB`，JAR 使用内置 RSA。
- How:
  - 外层 ZIP 完整性与 SHA、内层发布 SHA、三节点 SHA、JAR `BOOT-INF/classes/rsa-private.key`、worker `linux/amd64`、三节点 `--validate-only`、systemd 首装/升级和固定名封装回归全部实跑通过。
  - 构建与校验均在临时 worktree 完成，没有把工作区未提交内容带入发布包；企业现场只从中转机传输固定名 ZIP 和 SHA，不需要分别传内层发布包和节点包。
- Result:
  - 新包路径 `/Users/kaka/Desktop/qr-decode/out/test-agent-two-backend-complete.zip`，大小约 `237 MiB`，SHA256 为 `af926d32748c833ee2e641e38d8bb06cc2365afd51bbead8045a2bee4f545422`；内层发布 SHA256 为 `4b4710ab3714fced7115088226f88f9a1af02e9f9487d11c8cb60c546ba0deb6`，JAR SHA256 为 `d28495f87a4f0ec7759e5a0b80444bc5a05269ba703104eada14c2f0875ac624`。
  - 本机已验证发布物与脚本；企业三台服务器的正式部署和浏览器双后台业务验收仍需现场执行。

### 2026-07-21 - 应用 Agent 文件双击改名与只读权限复核

- Why:
  - 应用级 Agent/Skill 文件缺少与普通工作区一致的双击改名能力，同时需要确认公共级和应用级对无写权限用户确实保持只读。
- What:
  - 应用管理员与超级管理员可双击 `agents/**`、`skills/**` 文件名行内改名；成功后刷新父目录、同步已打开 Agent tab，并触发 Git Changes 重新统计。
  - 新增 `agent-config.rename` 文件 WebSocket RPC，后端仅允许 `APP_ADMIN`（`SUPER_ADMIN` 继承）操作应用级文件；公共级不开放改名。
  - 复核并补测普通用户：公共级和应用级文件均以只读 tab 打开，树中不进入改名输入，后端绕过界面调用仍返回 `FORBIDDEN`。
- How:
  - 复用普通文件树的双击/Enter/失焦/Esc 行内交互、`WorkspaceFileService.renameFile` 的同目录改名和路径安全校验，以及既有 Agent 配置 route/ticket/RPC，没有新增 HTTP 文件代理或平行文件服务。
- Result:
  - AgentConfigPanel 24 项、backend-api 定向契约、WorkspaceFileWebSocketHandler 14 项、AgentConfigApplicationService 46 项通过；agent-web typecheck、前端生产构建和 `git diff --check` 通过。
  - 使用 `.env.test` / `test` profile 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 返回 200、CORS 正常。
  - 仅新增文件 WebSocket RPC 操作，不涉及 RunEvent、数据库、性能、generated SDK、环境配置或新依赖；权限边界保持公共写 `SUPER_ADMIN`、应用写 `APP_ADMIN`。

### 2026-07-21 - 修复公共配置未知用户目标永久排空

- Why:
  - 企业双后台公共 Agent 发布中，两台服务器 Git 已 `SYNCED`，但 manager 进程与平台进程表因历史 PID 为空或启动时间微差无法精确映射用户；`user_id=null` target 在恢复公共链接时构造空 `UserId`，持续以 `userId must not be null` 重试并阻断后续发布。
- What:
  - 公共 rollout 在 dispose 前优先使用同一服务器、容器、端口、PID、启动时间完全匹配的 manager 实时快照 `sessionPath/configPath` 恢复共享配置链接；未知用户 target 不再依赖数据库用户绑定。
  - 旧 manager 缺路径时仅对已映射用户保留数据库精确身份兼容路径；路径缺失、越界或进程身份变化仍失败关闭。同步 runtime README 与企业后端部署说明。
- How:
  - 复用既有 manager heartbeat 快照和 `OpencodeProcessConfigLinkService`，不放宽 PID/启动时间比较、不按端口猜测用户、不新增 API、数据库字段、migration 或 manager 协议；新增未知用户成功排空与缺路径失败关闭回归。
- Result:
  - 定向 23 项和 runtime 模块全量 626 项测试通过；18 模块生产代码以 `-Dmaven.test.skip=true` 打包成功。标准 `-DskipTests` 仍被既有 `UserDomainService` 测试缺少 `ThirdPartyUserApiClient` 构造参数阻断，与本次改动无关。
  - 使用 `.env.test` / `test` profile 启动 backend、manager、frontend；health/readiness 为 UP、前端 3000 和 CORS 为 200、manager WebSocket 已连接并应用配置。

### 2026-07-21 - Agents 新建删除联动 Git Changes

- Why:
  - 公共级和应用级 Agents 配置树只能编辑既有文件，缺少新建文件、文件夹和删除入口；通过树操作落盘后还需要让既有 Git Changes 立即感知。
- What:
  - 抽取工作空间已有的新建与删除确认面板供 Agents 复用；公共级、应用级根与可写目录支持新建空文件、以 `.gitkeep` 表示空文件夹，并支持文件和目录树递归删除。
  - 新增平台文件 WebSocket `agent-config.delete`，公共级继续要求 `SUPER_ADMIN`，应用级要求 `APP_ADMIN`（`SUPER_ADMIN` 继承）；删除复用工作空间文件服务的根目录、`.git`、越界路径和符号链接保护。
  - 创建/删除成功后刷新对应目录并递增既有 Agent 配置修订号，触发 Git Changes 重新查询；删除同时关闭对应文件或目录下的已打开标签。应用级入口限制在 `opencode.jsonc`、`agents/**`、`skills/**` Diff 白名单内。
  - 同步前后端模块 README/PACKAGE、HTTP/事件流协议、安全/前端规范和内置用户手册。
- How:
  - 新建继续复用 `agent-config.write`，删除新增同一 route/ticket/RPC 通道内的操作，不增加 HTTP 文件代理、RunEvent 或第二套 Diff 状态；业务层直接复用 `WorkspaceFileService.deleteFile` 的安全递归语义。
- Result:
  - 前端 lint、typecheck、生产 build 和全量 Vitest 通过（86 files，1439 passed / 1 skipped）；首次全量中 1 个无关 `agent-chat` 时间敏感用例偶发失败，单独复跑及第二次全量均通过。
  - `AgentConfigApplicationServiceTest` 46 项、`WorkspaceFileWebSocketHandlerTest` 14 项通过；后端全量测试执行到既有 `test-agent-system-management` 测试编译错误后停止，其 `UserDomainService` 测试仍缺少新增的 `ThirdPartyUserApiClient` 构造参数，与本次改动无关。
  - JDK 25 下后端 18 模块跳过测试打包成功；使用 `.env.test` / `test` profile 重启 backend、opencode-manager、frontend，health/readiness 为 UP、前端 3000 和 CORS GET 为 200、manager WebSocket 已连接。
  - 增加兼容性的 WebSocket RPC；未修改 RunEvent、数据库、migration、generated SDK、环境配置或依赖。删除沿用既有权限和路径安全边界，不引入新的跨服务器文件通道。

### 2026-07-21 - 应用配置初始化区分 Agent 与 Skill

- Why:
  - 应用级初始化此前总是同时生成 Agent 和 Skill，且名称转换对英文逐字符插入短横线，例如 `Payment Agent` 会得到 `p-a-y-m-e-n-t-a-g-e-n-t`。
- What:
  - 初始化弹窗新增 Agent/Skill 类型选择；Agent 只生成 OpenCode Markdown Agent 文件，Skill 单独生成 `SKILL.md`、rules 与 templates 资源模板。
  - 名称转换保留中文拼音分段，同时让连续英文和数字保持连续。
  - 同步 agent-web README 和内置手册，新增 Agent/Skill 分流、模板内容和英文名称回归。
- How:
  - 复用 `writeWorkspaceAgentFile` 和既有目录刷新，没有新增后端 API、模板服务或命名工具；Agent 模板按 OpenCode 规则由文件名决定名称，Skill 名称继续符合 `^[a-z0-9]+(-[a-z0-9]+)*$`。
- Result:
  - 前端全量 Vitest 86 个文件通过（1435 passed / 1 skipped），agent-web typecheck 和生产构建通过；JDK 25 下后端 18 模块打包成功。
  - 使用 `.env.test` / `test` profile 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 返回 200、CORS 正常、manager WebSocket 已连接。
  - 应用内浏览器自动视觉检查因运行时 `Cannot redefine property: process` 未执行；组件交互测试、构建和真实服务启动已覆盖本次交付。未修改 API、RunEvent、数据库、安全、性能、generated SDK 或环境配置。

### 2026-07-21 - 双后台完整包改为固定名称

- Why:
  - 用户希望后续每次只操作同一个完整包，并固定文件名，避免日期、`v2/v3` 导致中转机和三台服务器命令反复变化。
- What:
  - 新增 `package-two-backend-complete.sh`，把标准发布 ZIP 与三台节点包封成固定的 `test-agent-two-backend-complete.zip` 和配套 SHA，包内顶层也固定。
  - 同步企业部署 README、多后台手册和离线部署 Skill；新增隔离回归覆盖固定结构、SHA、敏感输出、重复无交互覆盖和禁止版本后缀。
- How:
  - 复用现有 `package-release.sh` 产出的内层 ZIP 及现有节点归档，不复制 Java、前端或 worker 构建逻辑；源交付物只读校验，节点包换入当前逐机脚本和手册后重新计算 SHA。
- Result:
  - 固定名封装回归、外层/内层/三节点 SHA、ZIP 完整性、固定目录、重复覆盖和 JAR 内 RSA 校验通过；当前固定包 SHA256 为 `5f0e544330dd0d749116bc81e4b9f076239d45162228b0d71d97e29c136053b5`。
  - 后续企业内部中转机只需接收固定名 ZIP 和 SHA，两者视为一套交付；节点配置与 JAR 内 RSA 仍在 ZIP 内按敏感交付物管理。

### 2026-07-21 - 新增公共技能创建与优化基础能力

- Why:
  - 用户需要在本机公共 Agent 配置区增加通用 `skill-creator` 和独立技能优化能力，打包后由用户导入企业内部环境。
- What:
  - 在公共个人 worktree 的 `opencode/skills/` 新增 `skill-creator`、`skill-optimizer`，包含 OpenCode 入口、按需参考、模板、无第三方依赖的离线校验脚本和 eval 样例；同步公共仓库 README 与 `opencode/AGENTS.md` 技能清单。
- How:
  - 复用既有 `skills/<name>/SKILL.md`、渐进加载和 `.skill` 打包约定，创建与优化职责分离；打包产物写入 `.tmp/enterprise-skill-packages/`，未修改或暂存公共 worktree 中既有热加载测试文件。
- Result:
  - 两项技能均通过自带校验、系统 `quick_validate.py`、eval JSON、敏感路径扫描、OpenCode `debug skill` 发现和归档解压复验；两个 `.skill` 包可供企业内部导入。
- Pitfalls:
  - 系统 Python 缺少 PyYAML，复用已有 `.tmp/skill-validate-venv` 完成系统校验；`package_skill.py` 需要从 skill-creator 根目录以 `python -m scripts.package_skill` 运行。
- Verification:
  - `python3 scripts/validate_skill.py <skill-dir>`；`quick_validate.py`；`OPENCODE_CONFIG_DIR=... opencode debug skill`；`unzip -t`、解压后二次校验；`git diff --check`。
- Next:
  - 用户将 `.skill` 包导入企业公共技能区后，可按企业模型与真实任务样本补充触发率和行为基准测试。

### 2026-07-21 - 小宠物入口单击重启已终止进程

- Why:
  - 已分配的 opencode 进程终止后，左侧活动栏宠物入口只会先唤出宠物，用户还要再次点击状态入口才能启动；用户希望一次点击完成启动，并在成功后显示宠物。
- What:
  - `FigmaShell` 在 `NEEDS_INITIALIZATION + NOT_RUNNING + initializable` 时把活动栏入口切换为直接启动，继续复用工作台已有初始化 mutation；READY 后只唤出浮动宠物，不自动打开状态卡或问答，失败后清理延迟唤出意图。
  - 增加组件成功/失败回归和桌面、移动端工作台 E2E；同步 frontend、agent-web、PACKAGE、模块图和快速开始手册。
- How:
  - 未新增 API、启动服务或旁路；前端仍调用既有 `/processes/me/initialize`，后端继续由公共 `OpencodeProcessStartupService` 完成 manager 与健康检查。
- Result:
  - agent-web typecheck、全量 Vitest（1431 passed / 1 skipped）、手册/生产构建通过；定向 Playwright Chromium/mobile 2 项通过。
  - 按 JDK 25、`.env.test`、test profile 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 为 200、CORS 正常、manager WebSocket 已连接。不涉及 API/RunEvent、数据库、性能、安全、兼容性、generated SDK、依赖或环境配置。

### 2026-07-20 - 企业部署人工复制改为无交互覆盖

- Why:
  - 企业服务器的 `cp` 被配置为交互式覆盖，逐个复制内层发布 ZIP、SHA 和节点包时反复询问，影响逐步部署操作。
- What:
  - 企业离线部署 Skill 约定现场人工复制明确交付文件时统一使用 `/bin/cp -f`，绕过 `alias cp='cp -i'`。
- How:
  - 只允许对逐条列明的文件执行无交互覆盖，禁止扩大为 `cp -rf` 目录覆盖；部署脚本原有配置备份行为不变。
- Result:
  - 后续逐台部署命令不再因已有同名发布文件反复询问，仍保留目标范围和回滚边界。

### 2026-07-20 - 记录企业中转机与逐步部署说明偏好

- Why:
  - 用户指出完整包已通过 U 盘进入企业内部中转机的 `Desktop/mimoagent/0709`，现场 `scp` 并非从 Mac 发起；同时明确以后每次部署都需要逐台、逐步的操作说明。
- What:
  - 更新企业离线部署 Skill，区分外部联网 Mac 与企业内部中转机，并把“中转机校验和传输 → `.4` → `.114` → `.2` → 业务验收”记录为现场说明顺序。
- How:
  - 要求每一步标明操作机器、绝对目录、完整命令、预期结果和失败停止条件；禁止用循环、“同上”或前后不一致的 `BASE/WORK` 假定目录压缩步骤。
- Result:
  - 后续用户已经说明交付物位于企业内部中转机时，从中转机 SHA256 校验和 `scp` 开始，不再误称“从 Mac scp”；仍保留 Mac 作为外部构建机的事实。

### 2026-07-20 - 修复企业同源部署 RunEvent SSE 地址构造

- Why:
  - 企业单/双入口前端按约定以显式空 `VITE_TEST_AGENT_API_BASE_URL` 构建，但 RunEvent client 仍调用 `new URL("/api/...")`；浏览器缺少绝对 origin 时会在发起请求前抛出 `Invalid URL`，表现为消息可发送、实时思考和回答缺失、历史记录最终完整。
- What:
  - RunEvent URL 改为先保留绝对或同源相对 path，仅在存在续传游标时用 `URLSearchParams` 追加 query；显式空 `baseUrl` 现在生成 `/api/internal/agent/.../events`，非空 base URL 和 `lastEventId` 语义不变。
  - 新增空 base URL 回归测试，并同步 event-stream-client README/PACKAGE、事件流 API 和模块图；未修改双后台生产 Java 解析、SSE 转发或 Nginx 路由。
- How:
  - 修复前回归用例稳定复现 `TypeError: Invalid URL`；修复后 event-stream-client 15 项测试、包级 typecheck 和前端全量 86 个测试文件通过（1429 passed / 1 skipped）。
  - 以空 `VITE_TEST_AGENT_API_BASE_URL` 完成 agent-web 生产构建，构建产物在 `http://127.0.0.1:4189/` 启动并返回 HTTP 200；保留既有 canvas 提示和大 chunk warning。
- Result:
  - 企业同源部署会真正向当前 Nginx origin 发起 RunEvent fetch SSE，不再在浏览器本地地址构造阶段反复报连接异常；双后台仍复用现有 producer Java 路由与 Java-to-Java SSE 转发。
  - 不涉及 RunEvent 类型、HTTP 路径、数据库、SQL/migration、generated SDK、依赖、鉴权或环境配置文件；企业现场仍需重新构建并部署包含本修复的前端产物后做真实双后台对话验收。

### 2026-07-20 - 修复双后台 manager 成功日志误判

- Why:
  - `.4` 现场容器 healthy、manager WebSocket 已连接且配置已经下发，但逐机 `--verify-only` 仍退出 1；实际日志为结构化 `event=manager_config_update status=applied`，脚本仍只匹配旧文本。
- What:
  - 标准发布与双后台逐机验证脚本改为同时识别当前结构化事件和旧版 `manager config update applied`，并同步企业部署 Skill、README、单/多后台手册。
  - 扩展双后台隔离回归，用假的 systemctl/curl/docker 真实执行 `--verify-only`，覆盖新旧两种成功日志。
- How:
  - 只调整日志成功判定，不修改 manager 协议、JAR、RSA、worker 镜像、配置或业务代码；重新封装完整发布 ZIP 和三份逐机配置包并更新各层 SHA256。
- Result:
  - Shell 语法、双后台逐机回归、最终三节点 `--validate-only`、ZIP/tar/SHA、JAR 内置 RSA 和 systemd 首装/升级验证通过；最终 JAR SHA256 保持 `08e4459c0c825682d2d2193d4fdd0c448602d6e816de8e64999503e0725c4ba2`。
  - `.4` 当前部署状态可判定成功；尚未在企业现场用修复脚本重新执行 `.4/.114 --verify-only`，前端 `.2` 仍应在两台后台验证通过后部署。

### 2026-07-20 - 基于现场配置交付双后台完整部署包

- Why:
  - 用户回传 `.2/.4/.114` 的轻量现场配置，要求直接生成使用 JAR 内置 RSA 的完整双后台包、逐机操作脚本和验收命令；`1 MiB` 只约束现场配置导出，不约束最终发布包。
- What:
  - 新增 `deploy-multi-backend-node.sh`，逐机校验并安装真实配置，复用标准后台/前端发布脚本，提供 `--validate-only`、正式部署和 `--verify-only`。
  - 现场修正包括：前端切换 `.4 + .114` multi upstream、`.4` 端口池补齐到 `4096-4115`、删除外置 RSA 路径和旧 `TEST_AGENT_BACKEND`；两台保留一致的共享凭据但使用不同稳定身份。
  - 修复标准部署脚本在 `pipefail + grep -q` 下可能把 worker 已成功下发配置误判为超时的问题；同步多后台手册和隔离回归。
- How:
  - 校验三份采集包 SHA，比较共享凭据但不打印值；完整发布 ZIP 与三份逐机配置包分层交付，逐机包不重复包含 JAR/镜像。
- Result:
  - 生成 `test-agent-two-backend-complete-20260720.zip`（约 `237 MiB`），外层 SHA、内层发布 SHA、三节点 SHA、JAR 内 RSA、systemd、Nginx 和逐机 validate-only 全部通过；尚未在三台企业服务器执行正式部署。

### 2026-07-20 - 增加双后台现场轻量配置采集包

- Why:
  - 用户最终要求只交回 `.2/.4/.114` 的现场配置，保留真实密码/token 以便生成无占位符部署脚本，但排除 JAR、RSA、日志等大文件，并把每台导出包控制在 `1 MiB` 内。
- What:
  - `deploy/internal/collect-multi-backend-context.sh` 以 `frontend/backend` 角色只读采集：后台为原始 `backend.env/docker.env`、身份文件和 systemd 有效 unit，前端为原始 `nginx.env`、主配置和 `test-agent.conf`。
  - 输出 `0600` 的 `SENSITIVE` tar.gz 与可搬移 SHA 文件，强制压缩包不超过 `1 MiB`；明确排除 JAR/lib、RSA、日志、Docker、programs、worker 镜像、业务数据和已部署前端。
- How:
  - dotenv 仅按文本读取、不 source；采集命令不调用 start/stop/restart。隔离回归验证原始密码/token 入包、禁止项不入包、无显式开关时拒绝、两种角色结构、SHA 和 `1 MiB` 超限删除行为。
- Result:
  - Shell 语法、配置采集回归、AI 文档和 diff 校验通过；独立脚本可直接复制到三台服务器，不要求重新传完整企业 ZIP。
  - 未修改 API、RunEvent、数据库、环境配置或 generated SDK；尚未在企业三台服务器执行采集或部署，配置包仍包含真实凭据，需按受控交付物处理。

### 2026-07-20 - 确认企业单后台回退实际生效时间

- Why:
  - 现场 Run 在历史记录中最终成功，但浏览器提示 RunEvent SSE 连接异常；此前曾短暂部署 `.4 + .114` 双 Java，随后关停 `.4`。
- What:
  - `.2` 的 `nginx.env` 与实体 `/data/apps/nginx` 的活动 `nginx -T` 均确认只包含 `.114:8080`；`.4:8080` 已拒绝连接，`.114` readiness 为 `UP`，因此当前静态 upstream 残留双后台已排除。
- How:
  - 使用 `/data/apps/nginx/sbin/nginx -p /data/apps/nginx/ -c /data/apps/nginx/conf/nginx.conf -T` 核对活动配置；PATH 中裸 `nginx -T` 会错误读取 `/root/conf/nginx.conf`，不能用于该现场。单后台前端/Nginx 部署实际完成于 16:06，而已采集故障 Run 在 15:40 发起。
- Result:
  - 15:40 的旧 Run 不能验证 16:06 后的单后台链路；需要浏览器硬刷新后用新 runId 复测。既有 Nginx access/error 默认路径未查到旧 runId，后续应先从实体 `nginx -T` 确认实际 `access_log/error_log`，再结合浏览器 SSE 的 Request URL、状态、Remote Address、耗时与 `.114` `api_stream_start/end` 定位；当前根因仍未确认。

### 2026-07-20 - 恢复 JAR 内置 RSA 并交付 20 端口单后台包

- Why:
  - 用户再次确认企业部署只能使用 JAR 内置 RSA；删除个人 SSH 配置后公共 Agent 已能拉取，但双后台下 RunEvent SSE 与 reference 仍必现异常，因此先回退到 `.114` 单后台，并把 OpenCode 端口池在原 10 个基础上再增加 10 个。
- What:
  - `RsaKeyService` 和 Spring 配置移除外置私钥路径构造与 `TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH`，固定读取 JAR `classpath:rsa-private.key`；打包脚本强制校验该资源存在，企业配置模板、Skill、安全和部署文档同步改回内置模式。
  - 单后台生成脚本只配置 `.114:8080`，实体 Nginx 同一 server 同时监听 `80`、`9996`；前端以空 `VITE_TEST_AGENT_API_BASE_URL` 使用同源 API，Java/OpenCode CORS 同时允许域名与 IP 的 `:9996` origin。
  - worker 端口池扩为 `4096-4115`，Docker 宿主机/容器同号映射 20 个端口；文档要求超级管理员同步把数据库通用参数 `OPENCODE_MANAGER_MAX_PROCESSES` 调为 `20`。补充 `.4` 停 worker、禁用 Java但保留数据的回退步骤。
- How:
  - 先抓取 `origin`、`github` 并将共同最新 `c539d018a` 合入本地 `main`；未新建分支。保留远程会话列表样式改动，再最小修改 RSA、单后台配置、worker 端口和相关稳定文档。
  - 定向 RSA WebCrypto OAEP-SHA256 测试通过；JDK 25 下 18 模块跳过测试打包并按 `.env.test`/`test` profile 真实重启 backend、manager、frontend，readiness 为 `UP`，日志确认从 `classpath:rsa-private.key` 加载。
  - 单后台配置、Nginx、systemd 首装/升级模拟、Shell 语法、ZIP/SHA、包内 JAR/脚本、Linux/amd64 worker、同源前端和离线部署 `--validate-only` 均通过；首次 Docker 构建从 `proxy.golang.org` 下载模块瞬时 EOF，切回 `goproxy.cn` 重试成功。
- Result:
  - 最终包 `deploy/internal/dist/test-agent-internal-release.zip` SHA256 为 `c9e41d912a37c486aa5d11ccb13f4a492bb552031fe21e93db015b0b16ca785e`；ZIP 与同名 `.sha256` 上传 `.2`、`.114` 的 `/data/0709/`，`.4` 不部署新包，只停服务并保留数据。
  - 公共 Agent 拉取已由现场确认恢复；RunEvent SSE 与 reference 的双后台根因仍未定位，不能称为已修复。单后台切换预计可排除跨后台路由/副本因素，但仍需现场按同一 runId 和 reference 同步重新验收；若仍失败再采集实际 SSE HTTP 状态、traceId 及 `.114` 日志。
  - 未变更 HTTP API、RunEvent 类型、数据库结构/SQL/migration、generated SDK 或依赖。内置私钥使交付 JAR/ZIP 成为敏感物；替换该资源会使数据库中既有 SSH 密文不可解密。

### 2026-07-20 - 收窄企业双后台 RSA、引用副本与 SSE 排障范围

- Why:
  - 企业双后台现场同时出现公共 Git 凭据 `RSA decryption failed`、新增 `.4` 引用资产指针核验失败和 RunEvent SSE 必现断流，需要区分部署数据、节点网络与代码问题。
- What:
  - 两台磁盘外置 RSA 私钥和当前 Java 进程公钥已由现场确认一致；仓库历史同时确认用户在 7 月 16 日明确要求企业包继续使用 JAR 内置 `rsa-private.key`，当前双后台 `backend.env` 的外置路径覆盖了这一既定模式。当前交付 JAR 仍包含自创建以来未变更的内置 RSA，两个节点部署的 JAR SHA 也一致。
  - `.4` 的引用根目录只有平台创建的 `.reference-repository-locks`，目标仓库目录尚不存在，不是残留坏仓库；修复凭据后应从前端仓库卡片触发同步，让后端临时 clone 后原子落位，不能用只读“刷新 Git 指针”代替同步。
- How:
  - 现场已验证 `.4/.114` 身份文件和 advertised host 正确、Java 均监听 `*:8080`、两台 Java 双向 readiness 为 `UP`、`.2` 可访问两台 readiness；Nginx 已加载两个 upstream，`/api/` 为 `proxy_buffering off` 且 `proxy_read_timeout=3600s`。
- Result:
  - `RSA decryption failed` 的直接原因是现场从约定的 JAR 内置 RSA 切换到了另一把外置 RSA；应从两台 `backend.env` 移除 `TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH` 并在维护窗口重启 Java，恢复相同 JAR 内置公钥。基础短连接网络和 Nginx SSE 参数已排除；RunEvent 必现断流尚未定位，仍需取得 `/runs/{runId}/events` 的实际 HTTP 状态、traceId、三入口流式 curl 结果及两台 Java 同一 runId 日志。企业模板/脚本当前仍会重新写入外置路径，后续需按用户既定模式修正并重打包，未授权前不修改业务代码或部署模板。

### 2026-07-20 - 生成域名/IP同端口双后台企业包

- Why:
  - 当前企业现场要求浏览器同时使用 `http://mimo.sdc.cs.icbc:9996` 与 `http://122.233.30.2:9996`，并把 Java/worker 从 `.114` 扩为 `.4 + .114`；既有前端固定域名后无法兼容 IP，Nginx 渲染也只能声明一个监听端口。
- What:
  - 前端 API 环境读取区分“显式空值”和“未配置”，空的 `VITE_TEST_AGENT_API_BASE_URL` 现在稳定表示当前页面同源 `/api`，不会让登录页回退到 `127.0.0.1:8080`；补充 backend-api 单测和包文档。
  - `configure-nginx.sh` 新增可选 `TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS`，同一个 server 块保留实体 `listen 80` 并增加 `listen 9996`，校验端口范围和重复值；多后台 upstream 固定 `.4:8080 + .114:8080`，实体配置继续复用已加载的 `/data/apps/nginx/conf/test-agent.conf`。
  - 多后台文档改为当前 HTTP 双入口完整执行单：两个浏览器 URL 都使用 9996，域名的既有企业入口内部仍可转发到实体 80；两台 Java/worker 同时放行两个 Origin、返回各自直接 `ws://...:8080`，后台就绪后再更新前端 Nginx。
- How:
  - 抓取 `origin` 和 `github` 后，两者仍在 `cc89296e0`，本地已包含全部远程代码，无新增提交需要合并。运行前端全量 Vitest（86 files，1428 passed / 1 skipped）、backend-api typecheck、Nginx 单/多后台渲染、单后台配置生成和最终 ZIP systemd 首装/升级模拟。
  - 第一次 worker 构建在 `goproxy.cn` 下载 Go 模块时瞬时 EOF；切换 `GOPROXY=https://proxy.golang.org,direct` 后完整打包成功。校验 ZIP/SHA、包内脚本文档、未夹带现场 env/私钥、前端未固化域名/IP、Linux/amd64 镜像和离线 Tool 依赖。
- Result:
  - 新包 `deploy/internal/dist/test-agent-internal-release.zip` SHA256 为 `7b6438cfadd7a4ef9073a518f979e06e0fdf73d9a036cf0f082f7bc379593d88`；同名 `.sha256` 需上传 `.2`、`.4`、`.114` 的 `/data/0709/`。
  - HTTP/WS 会明文传输登录信息和终端内容，且浏览器网段必须直达 `.4:8080`、`.114:8080`；本次没有替用户修改或重启内网服务器。未变更 HTTP API、RunEvent/SSE、数据库、SQL/migration、generated SDK 或依赖；新增部署环境字段为空时向后兼容。

### 2026-07-20 - 收口单后台现场问题与部署文档

- Why:
  - 当前单后台现场同时遇到 Docker 容器调用动态 Tool 地址超时、HTTP 域名解析/CORS、服务器终端不走 WSS、实体 Nginx 显式 include、服务重启后仍运行旧 JAR 等问题；既有文档仍混有旧 IP、WSS 和系统 Nginx 路径示例。
- What:
  - 单后台文档统一为浏览器 `http://mimo.sdc.cs.icbc:9996`、企业入口转发到 `.2:80`、Java/worker `.114`；补充 HTTP/WS 风险、精确 CORS/编译期地址、Docker bridge 源网段 `FORWARD + MASQUERADE` 持久规则、变更重启矩阵和 PID/JAR SHA 验证链。
  - 明确实体 Nginx 只显式加载 `/data/apps/nginx/conf/test-agent.conf`，当前应检查备份后复用该专用文件、监听保持 80；自动生成的 frontend/deploy/Nginx `.bak` 只是回滚备份。通用模板继续保持 WSS 安全默认，当前 HTTP 现场通过真实 env 显式覆盖。
- How:
  - 对照部署脚本、Nginx `-T` 现场输出、公共配置/模型和 worker 管理实现；抓取两个远程后均无待合入提交。运行 AI 文档、Shell 语法、单后台配置、Nginx 渲染、systemd 升级模拟、前后端交付脚本校验和完整 Mac 企业打包。
- Result:
  - 新 ZIP `deploy/internal/dist/test-agent-internal-release.zip` 包含修正文档与 `http://mimo.sdc.cs.icbc:9996` 前端，SHA256 为 `d9b93b614af2ba942dc9dcea8709bfb23a9d61c7eb4fe493634af8a5256b2842`；ZIP/SHA、包内路径、Linux/amd64 镜像和 Tool 基线依赖均通过。
  - 首次 Tool 探针从 worker 工作目录直接 import 因未经过运行时模块链接而失败；改从镜像实际 `/usr/local/lib/opencode-node` 复跑成功，确认不是依赖缺包。未变更 API、事件、数据库、依赖或安全默认，未在本机替用户操作内网服务器。

### 2026-07-20 - 修复企业 Nginx 显式 include 目录误判

- Why:
  - `.2` 前端部署时 Nginx 两次语法校验成功，但脚本随后提示未 include `/data/apps/nginx/conf/test-agent-gateway.conf`；现场主配置只显式加载同目录某个现有文件，旧探测逻辑却误以为同目录新建文件也会自动加载。
- What:
  - `configure-single-deployment.sh frontend` 复用实体 Nginx `-T`，在每个候选目录短暂创建仅含注释的探测 `.conf`，只有新文件确实出现在加载清单中才选择该目录；生效配置与主配置中的 `*.conf` include 均走相同验证。
  - 没有通配 include 时改为明确失败并要求增加专用目录，不再生成语法正确但永不生效的网关文件；同步单后台配置执行单和企业部署入口说明。
- How:
  - 扩展 `verify-internal-single-config.sh`：覆盖通配目录成功生成/安装网关，以及显式 include 单文件时拒绝同级目录的回归；运行 Shell 语法、配置生成回归、完整 HTTP 域名企业打包、ZIP/SHA 和前后端 `--validate-only`。
- Result:
  - 修复后的 HTTP 域名版 `deploy/internal/dist/test-agent-internal-release.zip` 构建成功，SHA256 为 `2a7e602eda32055679f5dfe616da4d3e02a7f3e1a07fb6a46ce2db3eaf8b77e1`；包内前端仍固定为 `http://mimo.sdc.cs.icbc:9996`。
  - 现场旧包无需重启 Java/worker即可修复：在现有通配 include 目录或新建的专用通配目录设置 `TEST_AGENT_NGINX_CONF_PATH`，重新执行前端部署。未变更 API、事件、数据库、依赖、终端权限或标准安全默认。

### 2026-07-20 - 生成 HTTP 企业域名版最终离线包

- Why:
  - 用户确认企业前端域名已由现有环境解析，但现场不采用 HTTPS，也不能由应用部署方直接调整企业 DNS；此前按 HTTPS/WSS 构建的前端包不符合最终入口。
- What:
  - 重新抓取并比较 `origin/main`、`github/main`，本地仍包含两个远程的全部代码；以 `http://mimo.sdc.cs.icbc:9996` 作为前端 API 基址重新构建完整企业离线 ZIP。
  - 标准仓库模板继续保持生产 WSS 安全默认；现场若必须使用服务器终端，需要在真实 `backend.env` 中清空公开 WSS 基址并显式设置 `TEST_AGENT_SERVER_TERMINAL_ALLOW_INSECURE_WEBSOCKET=true`，由浏览器直连签票 Java 的 `ws://122.233.30.114:8080`。
- How:
  - 运行单后台配置生成回归、Shell 语法检查、完整 `package-release.sh`、前后端交付脚本 `--validate-only`、ZIP/SHA 校验和不安全 WebSocket 显式开关单测。
  - 校验前端编译产物只包含 `http://mimo.sdc.cs.icbc:9996`，不含此前 HTTPS 域名或旧 IP API 基址；OpenCode 1.17.8 与 Tool 运行时依赖加载成功。
- Result:
  - 最终 HTTP 域名版 `deploy/internal/dist/test-agent-internal-release.zip` 构建成功，SHA256 为 `d3897116183e96828b2036c391bfac8db6b60238e9ddab0e9fa99dfca9438109`；ZIP 与同名 `.sha256` 需上传两台服务器的 `/data/0709/`。
  - HTTP/WS 会使登录凭证和终端内容在网络中明文传输；服务器终端还要求浏览器网段直达 `.114:8080`。未修改 API、事件、数据库、依赖或标准安全默认，仅生成站点专属前端产物并记录现场配置边界。

### 2026-07-20 - 生成企业域名终端版最终离线包

- Why:
  - 用户尚未实施 Nginx 域名/TLS 和 Docker 出网规则，需要基于最新代码与已提交的服务器终端默认参数重新生成最终企业包，并给出可从零执行的部署顺序和预期结果。
- What:
  - 重新抓取并比较 `origin/main`、`github/main`；两端均停留在 `cc89296e0`，本地 `main` 已包含全部远程提交并额外包含 `57a651251` 终端默认启用提交，无远程代码需要合并。
  - 以 `https://mimo.sdc.cs.icbc:9996` 作为生产前端 API 基址重新构建 Java、前端、OpenCode 1.17.8、Linux/amd64 worker、外置 programs 和完整离线 ZIP；未修改真实 `.env.local` 或服务器配置。
- How:
  - 运行企业单后台配置生成回归、开发脚本校验、Shell 语法检查和完整 `package-release.sh`；校验 ZIP SHA256、压缩结构、包内终端默认参数、前端编译域名、worker 镜像架构及 Tool 运行时依赖。
  - 前端产物确认含 `https://mimo.sdc.cs.icbc:9996` 且不含旧的 `http://122.233.30.2` API 基址；worker 内 `@opencode-ai/plugin`、SDK、Effect、Zod、node-pty 均可加载。
- Result:
  - 最终包 `deploy/internal/dist/test-agent-internal-release.zip` 构建成功，SHA256 为 `bf8b5174ee637eca2be29a96130fc4e0060a0ffb7065c2e1e857ac90569113cb`；同名 `.sha256` 需一并上传内网 `/data/0709/`。
  - 企业服务器仍需现场配置域名 DNS、Nginx 9996 TLS/WSS、后端精确 CORS 和 Docker FORWARD/MASQUERADE 后再启动验收；这些是环境操作，不写入仓库或交付包。未新增或变更 API、事件、数据库、SQL/migration、依赖或权限模型。

### 2026-07-20 - 企业交付模板默认启用服务器终端

- Why:
  - 企业内部署后签票接口返回“服务器终端未启用”；用户确认企业模板应直接启用，无需每次部署再手工把 `TEST_AGENT_SERVER_TERMINAL_ENABLED` 从 `false` 改成 `true`。
- What:
  - `deploy/internal/backend.env.example` 默认显式设置 `TEST_AGENT_SERVER_TERMINAL_ENABLED=true`，保留 `/data/testagent` 工作目录和强制 `wss://122.233.30.2` 公开地址；Spring 应用在未配置变量时的安全兜底仍为关闭。
  - 单机配置生成脚本新增终端启用值和 WSS 地址断言，避免后续模板回退；同步单/多后端完整配置、部署、安全和 HTTP API 文档。
- How:
  - 运行 Shell 语法检查，并在隔离临时目录实际执行 backend 配置生成，确认输出为 `true`、`/data/testagent` 和 WSS 地址；未改真实 `.env.local` 或企业服务器现有配置。
  - 完整运行 `deploy/internal/package-release.sh --output-dir deploy/internal/dist`，构建 Java、前端、Linux/amd64 worker 和最终离线 ZIP；对 ZIP 执行完整性、包内模板和 SHA256 校验。
- Result:
  - 新企业包 `deploy/internal/dist/test-agent-internal-release.zip` 构建成功，SHA256 为 `1ccb10ebf0781f3d3627e61d289968a68d40a1d5fd9726867de197e2362b20a2`，包内服务器终端配置已确认默认启用。
  - 现有企业服务器仍需用新包重新生成 `/data/testagent/config/backend.env`（或等价地改为 `true`）并重启 Java；Nginx TLS 与按 `linuxServerId` 的 WSS 精确路由仍是启用前提。未新增或变更 API 路径、RunEvent/SSE、数据库、SQL/migration、generated SDK、依赖或权限。

### 2026-07-20 - 合并最新远程并生成企业离线部署包

- Why:
  - 用户要求在保留公共/应用个人配置热加载与宠物入口改动的前提下合入最新远程代码，并重新生成可导入内网的企业全量部署包。
- What:
  - 将本地四个提交重放到 `origin/main` / `github/main` 共同基线 `9f8cb2b1b`，冲突处理同时保留远程夜间任务会话锁、会话列表等能力与本地用户级 dispose 闸门、七种宠物和 Agent 配置更新入口。
  - 合并后消除 `backend/README.md` 中自动产生的 `test-agent-opencode-runtime` 重复模块说明；未修改真实环境文件，部署包不包含 `ssh-rsa-private.key`、`backend.env`、`docker.env` 或 `.env.local`。
  - 重新构建 Linux/amd64 企业离线包 `deploy/internal/dist/test-agent-internal-release.zip`，SHA256 为 `a5b59c6b91b96a8d3e9153102909712de40e9830c6e91d2d4adb43a5165d13ef`。
- How:
  - 后端聚焦运行态/API/Redis 回归共 76 项通过；前端全量 Vitest 86 个文件 1427 passed / 1 skipped，工作区全量 typecheck 通过。首次前端测试与 Maven/typecheck 并发时有 3 项超时/异步等待抖动，单独复跑及随后全量独占复跑均通过。
  - 企业打包脚本完成 Java、前端、OpenCode 1.17.8、manager、Linux/amd64 Worker 和自定义 Tool 依赖构建；SHA256、`unzip -t`、Worker 镜像运行时检查及前后端 `--validate-only` 均通过。
- Result:
  - 最新企业离线包可上传到内网 `/data/0709/`，ZIP 与同名 `.sha256` 必须成对上传。包内示例配置不覆盖服务器 `/data/testagent/config/` 下的真实配置和持久私钥。
  - 本次收口不新增 API、RunEvent/SSE、数据库 migration、依赖、环境变量或鉴权语义；远程基线自带的既有 migration 仍由后端启动时按原流程执行。未在本机替用户重启内网服务。

### 2026-07-19 - 修复个人运行态重载的跨会话竞态

- Why:
  - 个人 Agent 配置热加载原先只看当前页面 Run，手动与自动入口使用不同锁；后端 `/global/dispose` 会释放当前用户全部 Workspace Instance，却没有覆盖宠物/手册旁路问答和 legacy 新消息入口，也缺少覆盖 OpenCode 超时重试的续租。
  - Redis Run 初始化在闸门拒绝后可能残留 `runtime-user` marker，误导运行态摘要跳过 legacy 活跃 Run；初始化脚本参数新增后也使既有 persistence 测试失配。
- What:
  - 新增 `UserRuntimeDisposeCoordinator`：在 `{userId}` slot 原子清理过期 active、确认空闲并申请 token 闸门，再复核用户全部 Session；两分钟租约每 30 秒按 token 续租，应用与公共个人重载共用该协调器。
  - 主 Run、宠物/手册旁路问答及 legacy sideQuestion/command/shell（含非默认 Agent）统一检查 dispose 闸门。新 Redis Run 在用户 slot Lua 内先检查闸门，再登记 `active:user` 并以随机 owner 建立 marker；拒绝发生在 Session、服务器、历史索引及 marker 写入前。单 Run `{runId}` 初始化 Lua 保持原 13 参数，不跨 Redis Cluster slot。
  - 前端以 `sessionRuntimeState.runningCount` 补齐用户级 busy，手动/自动重载共用响应式串行锁；公共重载不再依赖应用工作区选择。自动保存收到后端 `CONFLICT` 时保留 revision 和公共 worktree 目标，在用户空闲后或短延迟复核时重试。
  - 同步 runtime/persistence/agent-web README、persistence PACKAGE、HTTP API 和后端 Redis Lua 规范；没有新增 HTTP 接口，继续使用既有应用 `global/dispose` 与公共个人 `public/runtime-reload`。
- How:
  - Redis 用户闸门、active 索引和 marker 的脚本全部使用同一 `{userId}` hash tag；单 Run详情继续使用 `{runId}`，避免 Redis Cluster `CROSSSLOT`。闸门申请、续租和释放均以随机 token fencing，旧 owner 不能释放新租约。
  - 测试覆盖 marker 写入前拒绝、13 参数初始化契约、过期 active 清理、租约续期/丢失、全部新消息入口、非默认 Agent、公共工作区独立、用户级按钮 busy 和前端全量回归。
- Result:
  - persistence 定向 5 项通过；runtime 核心 125 项通过，非默认 Agent 加固后相关 49 项再次通过；后端 17/18 模块 app 打包与启动脚本 clean package 均成功。
  - 前端 typecheck、全量 Vitest 79 个文件（1323 passed / 1 skipped）和生产 build 通过；仅保留既有 canvas 提示与大 chunk warning。
  - 按 JDK 25、`.env.test`、test profile 重启 backend、opencode-manager、frontend；health/readiness 为 UP，前端 3000 返回 200，manager WebSocket 已连接并应用配置。
  - 不涉及新 API 路径、RunEvent/SSE、数据库、SQL/migration、generated SDK、依赖或环境配置；兼容未接入用户闸门的旧 `RunRuntimeStore` 实现。无未完成事项。

### 2026-07-19 - 收紧宠物配置更新入口并统一左侧 Agent 操作布局

- Why:
  - 用户要求 Agent 配置更新只出现在小宠物对话页，宠物选择页不展示；左侧 Agent 区域仍保留入口，并希望公共/应用两行的刷新图标位置统一。
- What:
  - `FigmaShell.vue` 进入宠物选择页时清理运行态确认，配置更新操作与确认块仅在对话页渲染；新增选择页隐藏入口回归。
  - `AgentConfigPanel.vue` 将公共/应用根节点动作收进统一动作容器，公共“更多操作”与应用初始化按钮均置于刷新按钮之前，刷新图标固定为动作组最右侧；不改变权限门禁和事件 payload。
  - README、PACKAGE、模块图和 Agent 配置手册改为“Agent 配置更新”口径，明确选择页不展示且复用既有接口。
- How:
  - 继续复用 `AgentWorkbench` 已有的 `disposeGlobal()` 和 `reloadPublicPersonalAgentRuntime()`，没有新增接口、事件、后端代码或 API 文档契约。
  - 先回顾全部 `.agents/session-log*.md`，再执行组件测试、类型检查、用户手册和生产构建；初次 workspace filter typecheck 被 `temp/workspace` 缺少 node_modules 的重复包阻断，改在 agent-web 包目录直接执行后通过。
- Result:
  - FigmaShell/AgentConfigPanel 定向 64 项通过；合并宠物头像与偏好回归后 4 个文件共 79 项通过。
  - `frontend/apps/agent-web` 目录内 `vue-tsc --noEmit --pretty false`、VitePress 手册构建和 Vite 生产构建通过；既有大 chunk warning 保留。`http://127.0.0.1:4177/` 预览返回 HTTP 200。
  - 未做真实登录态点击验收；需要在有权限的工作台确认对话页按钮可见、选择页隐藏，并验证两类既有接口的实际返回。

### 2026-07-19 - 更换七种宠物并增加个人运行态重载入口

- Why:
  - 用户提供七张新宠物素材，要求替换工作台头像，同时需要分别验证应用个人和公共个人 OpenCode 配置的手动热加载。
- What:
  - 将新素材去除棋盘背景并转换为 512px RGBA 头像，替换旧五种图片角色，保留旧角色 ID 的兼容映射；默认宠物、名册底色和说明文档同步更新。
  - 旧五张素材文件保留在 assets/pets 目录但不再导入；在首次点击小宠物打开的旁路面板增加“Agent 配置更新”入口，公共和应用按钮先展示影响范围确认，再由工作台分别复用既有公共 runtime-reload 和应用 global dispose 接口，运行中任务禁用，公共请求携带当前用户 worktree/server。
- How:
  - 保留 `PetCompanionAvatar` 的进程状态光圈和 ready/异常状态映射不变；AgentConfigPanel 只发事件，AgentWorkbench 负责接口调用、运行态目录重新查询和反馈，避免配置面板直连运行时。
  - 更新 agent-web、前端模块图和用户手册，新增面板事件回归；未新增 API、事件、数据库、环境配置、依赖或 generated SDK。
- Result:
  - `vitest` 定向 3 个文件 31 项通过；agent-web `vue-tsc`、VitePress 用户手册构建、Vite 生产构建通过；构建产物预览 `http://127.0.0.1:4177/` 返回 HTTP 200。
  - 未执行真实登录后的按钮点击验收；需要在有权限且进程 READY 的工作台中首次点击宠物后分别确认两个按钮，观察确认提示、后端返回和运行态目录更新。

### 2026-07-19 - 补齐分支模型测试数据并修复公共个人热加载 500

- Why:
  - 用户要求同时准备可真实提交/推送的隔离 Git 数据、当前应用/公共个人 worktree 的本地 OpenCode 热加载数据，并给出可执行步骤与明确通过标准。实际验收公共个人保存入口时发现 `POST /agent-config/public/runtime-reload` 在 WebFlux 事件线程内调用 Reactor `block()`，软链接已经切换但接口返回 500，形成部分成功状态。
- What:
  - 扩展 `tools/create-workspace-branch-model-test-data.sh`：每次创建应用/公共两个本地 bare remote、已推送基线、发布就绪个人 worktree、clean/dirty/真实冲突和公共个人数据；README 生成可复制的安全 commit/push 命令，并断言个人分支和 `spec/**` 不进入远程 feature。
  - 脚本增加成对的可选真实 worktree 参数，只新增带唯一 tag 的 docs、archive、spec、应用/公共 Agent、Skill 与 rules 未提交样例；不覆盖同名文件、不提交、不推送真实 Gitee。本机已在 F-COSS 应用个人 worktree 和 `public-usr_test_dev` 造入 `20260719` R1 数据。
  - `docs/testing/application-worktree-feature-cases.md` 细化测试设计、隔离数据、Git/热加载/权限/rollout 案例和逐项通过标准；同步 API、API/runtime 模块 README。
  - `AgentConfigController.reloadPublicPersonalRuntime` 把本地同步重载与跨服务器转发统一调度到 `boundedElastic`，避免占用 WebFlux 事件线程；控制器回归明确拒绝在 non-blocking thread 调用同步业务端口。没有修改 OpenCode 原生代码、配置发现规则或 generated SDK。
- How:
  - 复用既有个人 worktree、feature 投影、原生 `git merge --no-edit`、公共受管软链接和 OpenCode `/global/dispose`，没有新增 Git 或 dispose 平行实现。隔离 fixture 的 `origin` 全部为 `.tmp` 下 bare path；真实个人数据保持未提交，供 UI 把 R1 改为 R2 后用 Command/Ctrl+S 验证。
  - 使用 fixture 实际完成应用个人 commit、选择性 feature push 和公共 `HEAD:main` push，确认远程包含 docs/archive/Agent/Skill/rules、不含 spec，也不存在个人分支。按 JDK 25、`.env.test`、test profile 完整重启，初始化当前用户 OpenCode 后真实调用修复后的公共 runtime reload。
- Result:
  - 后端真实 Git/workspace/runtime/API 相关 95 项通过；前端保存、Diff 与 Agent 配置 API 相关 41 项通过，agent-web typecheck 通过；脚本语法、两次生成、Git `fsck`、`git diff --check` 通过。
  - 真实 `runtime-reload` 返回 HTTP 200、`reloaded=true`，公共指针从共享目录切到 `public-usr_test_dev/opencode`；当前 OpenCode 在 4096 健康，应用个人和公共个人 Agent/Skill 的 R1 均可从同一 directory 查询，重启后的后端日志不再出现 Reactor blocking 错误。
  - backend readiness 为 UP、前端 3000 为 200、manager 无 decode/reconnect 循环。未推送真实应用/公共远程，未执行真实多用户或多服务器 rollout；R1 测试文件有意保留未提交，等待用户按文档完成 R1→R2 保存和选择性提交测试。
  - 本轮修复既有内部 HTTP 端点的线程调度，不改变 URL、DTO 或响应结构；不涉及 RunEvent/SSE、数据库、SQL、migration、环境文件、安全权限或 generated SDK。`boundedElastic` 只承接既有最长 10 秒的同步 dispose 等待，避免阻塞事件循环。

### 2026-07-19 - 细化工作区 Git、配置软链与保存热加载文档

- Why:
  - 用户确认实现符合预期，要求把应用普通文件范围、`spec/**` 约束、当前本地 `OPENCODE_CONFIG_DIR` 关系和 Ctrl/Cmd+S 保存语义写清，并整体复核分支、配置与 dispose 逻辑。
- What:
  - `docs/testing/application-worktree-feature-cases.md` 增加普通文件/应用 Agent Diff 边界、发布前置条件、公共分支选择语义、本地 session/受管软链实例、保存入口与热加载文件白名单，以及未初始化进程下应用配置和公共个人预览的不同结果。
  - 修正 `frontend/README.md` 和 `docs/api/http-api.md` 中“公共保存不热加载”的旧口径；同步 workspace README、agent-web PACKAGE 和部署文档。未修改实现代码、OpenCode 源码、API 契约、事件、数据库、环境文件或 generated SDK。
- How:
  - 对照 `ManagedWorkspaceApplicationService`、`PersonalAgentConfigRuntimeReloadService`、`PublicAgentConfigRolloutService`、`OpencodeProcessConfigLinkService`、`AgentWorkbench`、`agentFileLoad` 及其测试；确认无运行进程时应用 `.opencode` 下次 bootstrap 生效，但未推送公共个人预览需在进程 READY 后再次保存或正式推送。
- Result:
  - 后端相关 203 项、前端 44 项与 agent-web typecheck 通过；AI 文档校验、真实 Git fixture、读者问题契约和 `git diff --check` 通过。运行态 backend readiness UP、前端 200、OpenCode 4097 健康，manager `configPath` 与 `current-public-config` 软链一致且当前指向共享公共配置。

### 2026-07-19 - 公共个人配置固定指针与保存热加载

- Why:
  - 应用个人 `.opencode` 已能随个人 worktree 原生加载并在保存后 dispose 本人；公共个人 worktree 仍只能等推送后全局生效，无法在发布前只让当前超管调试。用户确认公共和应用个人的 Agent/Skill/JSONC 保存都应只热加载本人，推送后再按各自发布范围排空，同时禁止新增 OpenCode 四层配置解析或配置副本 runtime。
- What:
  - 每用户进程的 `OPENCODE_CONFIG_DIR` 固定为 `{sessionPath}/.testagent-runtime/current-public-config` 受管软链接：启动默认指向服务器公共共享副本；公共个人保存时原子切到本人 `public-{userId}` worktree 的 `opencode/` 并只调用本人 `/global/dispose`；公共发布排空时恢复共享指针后再 dispose。
  - 新增 `POST /agent-config/public/runtime-reload`，复用公共 worktree 的 owner、服务器和 Java 路由校验。应用个人保存继续直接 dispose 本人，不切换公共指针；应用 Agent/Skill 发布只对已包含固定 feature commit 的目标用户 dispose。
  - manager `start` 显式接收、保存和校验 `configPath`，重启保留该路径；健康旧进程路径与请求不一致时拒绝幂等复用。公共 rollout target 持久化查询补回 `config_scope`，确保 PUBLIC 才恢复共享指针、APPLICATION 不触碰指针。
  - 同步分支模型、配置加载、角色权限、保存/提交/推送影响、dispose 时机、API/manager 协议、部署和模块 README/PACKAGE；测试数据脚本生成了 clean、dirty、冲突和公共个人 fixture。
- How:
  - 复用 OpenCode 官方 `OPENCODE_CONFIG_DIR`、请求工作区 `.opencode` 和原生 `/global/dispose`；不复制配置、不创建应用 runtime、不修改 OpenCode 配置目录解析。软链接采用同目录临时链接加 rename，普通文件/目录占位、无权限或不支持软链接时明确失败，不删除未知内容、不降级复制。
  - 应用 `.opencode/node_modules` 仍由既有企业离线兼容层建立包级软链接，统一指向 programs 只读依赖；它不是公共 Git worktree 或配置 runtime。本轮未修改 `opencode-source` 或 `deploy/internal/opencode-node-compat.patch`。
- Result:
  - workspace/runtime/API 等 14 个相关后端模块测试全部通过；本轮运行时模块 595 项、API 311 项通过，新增 MyBatis scope 映射 4 项通过。扩大到 persistence 全量时仍被既有 `V20260717173000` 的 PostgreSQL `timestamptz` 与 H2 不兼容阻断（76 errors），本轮无 migration，未扩大范围改写已执行迁移。
  - `go test ./...`、前端全工作区 typecheck、定向 Vitest 5 项、`git diff --check` 与分支模型真实 Git fixture 通过。
  - 按 JDK 25、`.env.test`、`test` profile 完整构建并重启 backend、opencode-manager、frontend；readiness 为 UP、前端 3000 返回 200、manager WebSocket 已连接并应用配置。随后通过本地测试账号和平台初始化 API 启动用户 OpenCode：4097 达到 `READY`，原生 `/global/health` 返回 200/`healthy=true`（1.17.7）；manager state 的 `configPath` 与实际 `OPENCODE_CONFIG_DIR` 均为用户 session 下的 `current-public-config`，该软链接当前指向服务器公共共享配置目录。
  - 新增一个内部 HTTP API 和 manager command 可选兼容字段；不新增 RunEvent/SSE、数据库结构、环境文件或 generated SDK 修改。旧版仍直接读取共享 `configPath` 的存量进程需要受管重启一次；不支持软链接的平台会显式失败。
- Verification:
  - `mvn -pl test-agent-api,test-agent-persistence -am test`（至 API 全部通过；persistence 仅既有 H2 migration 基线失败）
  - `go test ./...`
  - `corepack pnpm typecheck && corepack pnpm vitest run apps/agent-web/tests/agent-file-load.test.ts packages/backend-api/tests/agent-config-update.test.ts`
  - `tools/create-workspace-branch-model-test-data.sh`、`git diff --check`
  - `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`

### 2026-07-19 - 应用 feature 固定提交反向合并个人 worktree

- Why:
  - 上一版仅对应用 Agent 白名单做反向投影，普通 docs 推送后仍要求其他成员手动更新；用户确认应用普通文件与 Agent/Skill 都应从 feature 自动反向同步个人 worktree，冲突直接进入现有 Diff，同时公共配置仍保持既有分支与推送模型。
- What:
  - 应用 feature push、跨服务器版本广播、版本副本补偿和兼容 `sync-from-application` 统一按固定 `targetCommitHash` 对本服务器相关个人 worktree 执行原生 `git merge --no-edit <targetCommit>`。clean 时快进或 merge；dirty/staged/untracked 时不 stash/reset/覆盖并标记待同步；冲突保留 `MERGE_HEAD` 与三方 index。
  - 工作区 Diff 新增向后兼容的 `mergeInProgress/applicationUpdatePending/applicationTargetCommit`；全部冲突解决后新增 `POST /workspaces/{workspaceId}/git-conflict/complete` 提交完整 merge index，包含 `.opencode/**` 时继续要求 `APP_ADMIN`。前端在 workspace 与应用 Agent 作用域展示待同步、三方冲突和“完成合并/取消合并”。
  - 应用 Agent/Skill rollout 改为等待本服务器相关个人 worktree 全部包含固定提交后再登记目标用户并走既有全局 dispose；未收敛时保留持久化 retry。普通 docs 只做 Git 合并，不 dispose。
  - 保存时热加载边界收紧：应用个人 worktree 的 Agent/Skill 目录定义与 JSONC 只 dispose 当前用户供调试；公共 Agent/Skill 保存不 dispose，仍以公共分支推送后的全服务器 rollout 为生效边界。
  - 新增 `tools/create-workspace-branch-model-test-data.sh`，在 `.tmp` 生成公共个人、应用 feature、成功 merge、dirty 待同步和真实 `MERGE_HEAD` 冲突 fixture；重写分支模型测试文档并同步 HTTP、广播、模块和前端 README。
- How:
  - 复用 `ManagedWorkspaceApplicationService`、服务器版本广播、个人 worktree、`PublicAgentConfigRolloutCoordinator`、既有三方冲突编辑器和 OpenCode 原生 `/global/dispose`；没有引入应用配置覆盖层，不修改 OpenCode 源码、generated SDK、manager 协议、数据库或环境文件。
  - feature 发布仍只从个人 `HEAD` 定点投影所选非 `spec/**` 路径，个人分支不 push，`spec/**` 对所有角色继续仅本地。反向同步按完整固定 commit 保留 Git 历史，并在本地提交、回退、重新进入 default worktree、版本广播和副本补偿时重试。
- Result:
  - 后端定向真实 Git/workspace/API 共 75 项通过，前端 Agent 路由与 Git 面板 38 项通过，agent-web typecheck、AI 文档校验、脚本语法、`git diff --check` 和完整前后端生产构建通过。
  - 按 JDK 25、`.env.test`、`test` profile 重启 backend、opencode-manager、frontend；readiness 为 UP、前端 200、CORS 正常、manager WebSocket 已连接并应用配置。通过平台初始化默认测试用户后，受管 OpenCode 在 `127.0.0.1:4096` 达到 `READY`，原生 `/global/health` 返回 `healthy=true`、版本 1.17.7；启动日志仅有既有 macOS Netty DNS native fallback。
  - 新增一个内部 HTTP 完成合并入口和三个可选/默认兼容的 Diff 字段；不新增 RunEvent 或广播类型，不改变广播 payload，不涉及数据库/API 外网兼容、性能敏感全表扫描或凭据输出。真实多服务器人工发布仍需目标环境验收，当前由本机真实 Git fixture 与服务测试覆盖。
- Verification:
  - `mvn -pl test-agent-common,test-agent-workspace-management,test-agent-api -am -Dtest=GitWorkspaceServiceRealGitTest,ManagedWorkspaceApplicationServiceTest,ManagedWorkspaceControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `corepack pnpm vitest run apps/agent-web/tests/agent-file-load.test.ts apps/agent-web/tests/git-changes-panel.test.ts`
  - `corepack pnpm --filter @test-agent/agent-web typecheck`
  - `tools/create-workspace-branch-model-test-data.sh`、`tools/verify-ai-docs.sh`、`git diff --check`
  - `./restart-dev-services.sh --profile test --env-file .env.test`

### 2026-07-18 - 修复编辑器复制 Agent 合成路径

- Why:
  - 编辑器页脚把 `agent-workspace:<workspaceId>:::<encodedPath>` 合成 tab 路由当作相对路径拼到 workspace 根目录，剪贴板因此出现 `:::`、`%2F` 和无效绝对路径。
- What:
  - Agent 配置树从公共 worktree/服务端 `agentDirectory` 解析真实绝对路径并固化到 tab；页脚只复制这一条绝对路径，合成 path 继续仅用于身份与读写路由。
  - 同步 frontend、agent-web README/PACKAGE、前端规范和模块地图；未改 HTTP/WebSocket 契约、RunEvent、数据库、后端或 generated SDK。
- How:
  - 复用 `AgentConfigStatus.agentDirectory`、公共 `publicSource`、现有 `AgentFileLoadRequest` 和 `EditorTab`，补普通/Windows/公共 Agent/应用 Agent 回归；Vitest 必须从 frontend 根使用 `--config vitest.config.ts`，否则会缺少 jsdom。
- Result:
  - 定向 Vitest 4 文件 32 项、agent-web typecheck、用户手册与 agent-web 生产 build 通过；按 `.env.test`/`test` 重启三服务后 backend health/readiness UP、frontend 3000 为 200、CORS 与 manager WebSocket 正常。

### 2026-07-18 - 校正 Agent 分支模型与应用发布定向热加载

- Why:
  - 公共 Agent 原有 `public-{userId}` 编辑分支、推送 `master` 和跨服务器 rollout 模型已经正确；此前把公共个人、应用 feature 和应用个人 worktree 误当成 OpenCode 运行时覆盖层，复杂化了实现。
  - 应用普通 docs 与应用 Agent/Skill 的发布效果不同：docs 只应通知其他成员手动更新个人工作区，Agent 配置发布则需要在不覆盖个人调试改动的前提下同步并热加载。
- What:
  - 完整撤销 OpenCode 1.17.8 原生四层加载、三个新增启动环境变量及离线补丁内容；运行时保持原生模型：公共配置由 `OPENCODE_CONFIG_DIR` 加载，应用配置由当前个人工作区 `.opencode` 加载。`OPENCODE_REFERENCES_DIR` 引用能力保留。
  - 公共 Agent 流程不变。应用普通 docs 推送 feature 后继续广播版本更新，其他成员收到更新提示但个人 worktree 不自动覆盖，用户在“更新个人工作区”时同步。
  - 应用 Agent/Skill/`opencode.json(c)` 推送 feature 后，各服务器只把白名单精确投影到本机无 `.opencode` 脏改动的成员个人 worktree；使用 `git commit --only` 保留普通 docs/spec 的 staged/dirty 状态。存在个人配置改动的用户整组跳过并写失败审计，不 dispose。
  - 对成功同步的用户按精确 PID/启动时间登记 rollout target，等待运行空闲后定向调用 `/global/dispose`；端口复用或身份尚未收敛时重试，不能误排空或漏热加载。个人 Agent/Skill/引用 JSONC 保存仍只排空当前用户进程。
  - 应用 Agent Diff、暂存、提交和发布白名单包含 `.opencode/opencode.jsonc`；同步 workspace/runtime、HTTP API、部署、模块图和前端说明。
- How:
  - 复用现有 `PublicAgentConfigRolloutCoordinator`、版本同步广播、个人 worktree、`materializeCommitFiles`、workspace sync 审计和公共 rollout 排空状态机；没有新增平行 Git/dispose 实现，也没有修改 generated SDK、manager 协议或 `.env.local`。
  - 新增 Git 白名单查询与 `commit --only` 原语；应用发布按活跃成员和当前服务器个人 worktree 收敛，普通工作区内容不参与自动投影。
- Result:
  - 定向测试通过：真实 Git 9 项、workspace management 50 项、rollout/runtime 19 项、启动服务 12 项；前端全量 79 个文件通过（1313 passed / 1 skipped），全工作区 typecheck 通过。
  - 后端全量 `mvn test` 中本轮涉及模块及 API 均通过，最终仍被既有公共 rollout migration `V20260717173000` 的 `timestamptz` 与 H2 不兼容阻断（persistence 76 errors）；已执行迁移未改写，避免真实测试库 Flyway checksum 冲突。
  - 按 `.env.test`/`test` profile 完整构建并重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 为 200、CORS 200、manager WebSocket 已连接。manager 使用标准 `/Users/kaka/.opencode/bin/opencode`，未运行自定义四层 OpenCode 源码。
  - 先前测试用应用资产引用关系和个人 `.opencode/opencode.jsonc` 保持删除状态；`.config` 当前仍以 `origin/master@3c89512` 为运行/更新事实源，`enterprise@1ad3d20` 只可评审后选择性合并，不能自动覆盖。
  - 未新增 HTTP 路径或 RunEvent/SSE；保留已执行的应用 rollout scope 数据库兼容迁移，无新 migration。安全上不覆盖个人配置脏改动，进程定向采用精确身份；性能开销仅发生在应用 Agent 发布时，按活跃成员及其本机 worktree 有界执行。

### 2026-07-18 - 更新公共 OpenCode 配置并重启引用功能环境

- Why:
  - 用户需要让最新引用功能代码使用更新后的公共 OpenCode 配置重新启动，并确认远程 `enterprise` 分支与当前配置的事实源关系。
- What:
  - 主项目已位于包含远程最新引用提交 `d1ba3f8c7` 的本地 `main@6e3124457`；公共配置仓库 `master` 从 `37c9ef8` 快进到远程最新 `3c89512`，OpenCode 原生 Agent 配置解析通过。
  - 公共配置 `enterprise@1ad3d20` 与 `master@3c89512` 从 `750c8e9` 分叉：`enterprise` 独有 1 个企业 provider 配置提交，`master` 独有 4 个 Agent/Skill 迭代提交。当前本地 test 环境以 `master` 为准；企业部署应保留 `enterprise` 的 provider 环境变量/内部代理配置，并单独同步 `master` 的新 Agent/Skill，不能用任一分支整树覆盖另一分支。
  - 对比发现远程 `master` 的 `opencode.jsonc` 存在硬编码 API key 候选，而 `enterprise` 使用环境变量引用；未记录凭据值、未擅自修改配置，后续若将 `master` 用于企业交付需先移除并轮换相关凭据。
- How:
  - 刷新主项目两个远端和公共配置远端，核对远端 HEAD、提交祖先关系、左右提交数、文件差异与工作区洁净状态；使用 `OPENCODE_CONFIG_DIR=... opencode agent list` 校验配置。
  - 按 JDK 25、`.env.test`、`test` profile 执行 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`，由统一脚本构建并重启 backend、opencode-manager、frontend 和按需用户 OpenCode 进程。
- Result:
  - 后端 18 模块打包成功（测试按启动脚本跳过）；backend health/readiness 为 `UP`，前端 `127.0.0.1:3000` 返回 200，登录 CORS 预检通过，manager WebSocket 已连接且心跳正常。
  - 用户 OpenCode 进程已在 `4096` 启动，`/global/health` 返回 `healthy=true`、版本 1.17.7；启动初期旧 4097 状态探测失败已由新 4096 进程健康状态收敛。
  - 未修改业务代码、API、事件、数据库、环境文件、generated SDK 或稳定文档；主项目仍为 clean 且相对 `origin/main` ahead 1，公共配置 `master` 与 `origin/master` 对齐且 clean。

### 2026-07-18 - 调整小宠物默认与最大尺寸

- Why:
  - 用户希望小宠物默认更大，并允许更大的桌面展示尺寸。
- What:
  - 将无历史缩放偏好的默认值调整为 150%，上限调整为 250%；保留已有明确缩放偏好，并补充滑杆、边界、位置夹紧和持久化测试。
- How:
  - 复用现有 `normalizePetScale`、本地 `test-agent.pet-companion.v1` 和兼容旧版 Chromium 的普通 range input；尺寸仍通过根元素 width/height 计算，未修改 API、RunEvent、数据库或环境配置。
- Result:
  - 定向宠物偏好与 FigmaShell 测试 51 项通过；agent-web 类型检查、生产构建通过；Vite preview 在 `127.0.0.1:4176` 返回 HTTP 200。

### 2026-07-18 - 将设置引导切换到真实页签

- Why:
  - 用户反馈第 08 步锚定在左侧“版本库管理”入口，却混合展示多个页签的操作，无法按当前页面完成配置。
- What:
  - 首次引导 v7 将应用管理员路径拆为 08“版本库管理”、09“应用人员管理”、10“应用与版本库关联”、11“工作空间管理”，每步只说明当前真实页签；第 12 步进入手册。普通用户仍以 SSH 配置结束并进入第 08 步手册。
  - 引导通过设置面板的真实菜单/页签锚点自动切换页面，异步页签挂载后再定位，设置工作区页签增加引导锚点；测试与手册同步更新。
- How:
  - 复用现有 SettingsDialog、SettingsPanel、SettingsAppWorkspacePanel 和权限分支，只增加初始菜单/页签参数、真实 DOM target 和异步定位轮询；未修改 API、事件、数据库或 Java 代码。
- Result:
  - 定向 4 个前端测试文件 42 项通过；agent-web vue-tsc、用户手册构建和生产构建通过；test profile 三服务重启成功，backend health/readiness、前端首页和手册页面均返回 200/UP。
- Next:
  - 应用内浏览器视觉复核仍可能受既有运行时 `Cannot redefine property: process` 限制，需在可用登录会话中确认实际气泡几何位置。

### 2026-07-18 - 展开应用与版本库及工作区页签说明

- Why:
  - 用户反馈新手引导第 08、09 步只给出概括，无法按设置面板的具体页签和字段完成配置。
- What:
  - 第 08 步按“版本库管理”入口、“应用人员管理”和“应用与版本库关联”分别说明新增、成员、关联与解除；第 09 步展开测试工作库、分支、别名、目录树、保存进度和回工作台选择 workspace/version。
  - 用户手册同步按 Tab 1/2/3 和版本库字段补充部署模式、版本库类型、权限和常见空列表原因；增加引导滚动内容的小标题样式与字段代码样式。
- How:
  - 继续复用现有 SettingsRepositoryPanel、SettingsAppWorkspacePanel 的真实字段和权限边界，只调整引导/手册文案与回归断言，没有新增 API、事件、数据库或 Java 代码。
- Result:
  - 定向 4 个前端测试文件 41 项通过；agent-web vue-tsc、用户手册构建和生产构建通过；test profile 三服务重启成功，backend health/readiness 与前端/手册 HTTP 状态均正常。
- Next:
  - 应用内浏览器登录态视觉复核仍受既有运行时 `Cannot redefine property: process` 限制，需在可用浏览器会话中确认长文案滚动和实际气泡几何位置。

### 2026-07-18 - 提升设置拆分引导版本

- Why:
  - 已浏览过 v5 引导的用户不会重新看到拆分后的设置步骤。
- What:
  - 将首次引导和工作台抑制状态的本地存储版本从 v5 升到 v6，保持 SSH、应用与版本库、应用工作区三步流程对既有用户可见。
- Verification:
  - 定向测试 27 项、agent-web 类型检查和生产构建通过；test profile 三服务重启后 readiness、前端与手册页面返回正常。

### 2026-07-18 - 拆分设置引导并修复弹窗定位

- Why:
  - 用户反馈设置引导内容挤在一个气泡中且气泡因设置弹窗异步挂载落到左上角。
- What:
  - 应用管理员引导拆为 SSH 配置、应用与版本库配置、应用工作区配置三个步骤；普通用户只保留 SSH 步骤和手册入口。
  - 设置菜单项增加真实引导锚点；进入设置步骤后等待弹窗挂载并替换为真实 DOM target，避免无目标定位。
- How:
  - 复用现有 SettingsDialog、SettingsMenu 和权限计算，只增加菜单锚点、角色条件、目标刷新和回归覆盖。
- Result:
  - 定向测试 3 个文件 27 项通过；agent-web 类型检查、用户手册/agent-web 构建通过；test profile 三服务重启成功，readiness、前端和手册 HTTP 状态正常。

### 2026-07-18 - 补充设置引导的具体操作步骤

- Why:
  - 用户反馈设置步骤虽然打开了面板，但没有说明普通用户和应用管理员具体应该点击什么、保存后如何回到工作台。
- What:
  - 首次引导 v5 增加 SSH Key、应用成员、版本库关联、工作空间创建和 workspace/version 选择的短流程；设置手册增加“设置面板怎么用”总览。
- How:
  - 继续复用 SettingsDialog、SettingsMenu 和现有三类配置页面，只调整引导文案、滚动容器、localStorage 引导版本和文档测试断言。
- Result:
  - 定向测试 2 个文件 12 项通过；agent-web 类型检查、用户手册构建、agent-web 生产构建通过；test profile 三服务重启成功，readiness 与前端/手册 HTTP 状态均正常。

### 2026-07-18 - 让新手引导打开设置面板并细化三类配置

- Why:
  - 用户反馈设置步骤只说明齿轮按钮却没有展示真实设置面板，且用户配置、版本库配置、应用工作区配置的操作入口不够明确。
- What:
  - 首次引导 v4 在第 07 步自动打开 SettingsDialog 并锚定设置导航；手册与 FAQ 新增三类配置的入口映射和逐步操作，明确普通用户选 workspace/version 的后续动作。
- How:
  - 复用现有 settingsOpen、SettingsDialog、SettingsMenu、VitePress 和 HelpCenter 链路，仅增加引导事件、真实 DOM 锚点、文案与回归断言。
- Result:
  - 定向测试 3 个文件 26 项通过，agent-web vue-tsc/生产构建和用户手册构建通过；test profile 三服务已重启，后续复核 readiness 与前端 HTTP 状态。

### 2026-07-18 - 新手引导结束后再展示宠物进程面板

- Why:
  - 新手引导进行期间，`NEEDS_INITIALIZATION` 状态 watcher 会自动打开宠物进程面板，遮挡引导内容；用户希望引导结束后再展示。
- What:
  - FigmaShell 新增引导活动态门禁：引导中不自动弹出进程面板，开始引导时会清理已打开的面板，完成或关闭后恢复自动提示。
  - AgentWorkbench 按当前用户的 v4 引导本地记录初始化门禁，并复用 FirstLoginGuide 的 prepare/finish/dismiss 生命周期传递状态；同步前端 README 与 FigmaShell 回归测试。
- How:
  - 仅复用现有 `processStatusInteractionEnabled` watcher、`FirstLoginGuide` 生命周期和 `test-agent.onboarding.v4:{userId}` 本地存储；未修改 API、RunEvent、数据库、环境配置或安全逻辑。
- Result:
  - FigmaShell 定向测试 45 项通过；agent-web vue-tsc/生产构建和 test profile 三服务重启验证通过，readiness 与前端 HTTP 状态正常。

### 2026-07-18 - 统一服务器终端默认配色

- Why:
  - 用户希望服务器终端在不提权、不修改账号配置的前提下，默认区分提示符、目录/文件类型以及 `grep`、`git` 输出颜色。
- What:
  - 服务器 Bash 改为通过 jar 内置、运行时释放到随机临时文件的 rcfile 启动；提示符使用绿色用户/主机和蓝色当前目录，Linux 配置 `ls --color=auto`、macOS 配置 `ls -G`，两端均配置 `grep --color=auto`，Git 使用当前终端能力自动着色。
  - rcfile 最后兼容加载用户已有 `.bashrc`，但不写入用户主目录、系统 shell 配置或全局 Git 配置；服务器 PTY 仍继承启动 Java 的操作系统用户和权限，最小环境仅增加 `COLORTERM=truecolor` 与 `CLICOLOR=1`。
  - 补充 shell 命令、资源内容、非敏感环境和真实 Pty4J 进程回归；同步 runtime README、HTTP API、安全规范和部署说明。
- How:
  - 复用现有 `TerminalProcessFactory`、Pty4J、ticket、WebSocket、限流、超时和审计链路，没有新增 terminal service、shell 插件或权限；临时 rcfile 在 POSIX 系统使用 `0600`，进程退出时由 JVM 清理。
  - 自动化真实输入必须模拟人工速度；Playwright 瞬时逐字符输入会按预期触发既有限流，本次 E2E 使用 70ms 字符间隔验证，不放宽生产限流。
- Result:
  - `TerminalProcessFactoryTest` 6 项通过，`test-agent-app` reactor package 成功；按 `.env.test` / `test` profile / JDK 25 重启 backend、manager、frontend，health/readiness 为 UP、前端返回 200。
  - Playwright 真实登录、二次确认并连接服务器终端后，页面显示彩色提示符，命令返回 `kaka|truecolor|1`，`ls`、`grep`、`git` 别名存在；Java 和 shell 用户均为 `kaka`。未新增或变更 HTTP 路径、RunEvent/SSE、数据库、SQL、migration、generated SDK、依赖或环境配置。

### 2026-07-18 - 服务器终端改为继承 Java 运行用户并取消手工确认文本

- Why:
  - 用户明确不需要 root 或任何额外权限，希望本地和 Linux 都直接使用启动目标 Java 的操作系统用户；原先要求手工输入 `ROOT@linuxServerId` 且校验 UID=0，导致本地无法连接，也增加了不必要的操作步骤。
- What:
  - 服务器终端目标由 `server-root` 改为 `server-shell`，删除 effective UID=0 校验、`HOME=/root`/`USER=root` 等环境伪装；Pty4J 直接启动 `/bin/bash`，操作系统 UID/GID 天然继承 Java 进程，最小环境只写入 Java 用户对应的 `HOME/USER/LOGNAME` 和非敏感基础变量。
  - 前端改为“点击连接服务器终端 → 二次确认目标服务器 → 确认连接”，取消手工输入框；目标绑定值改为 `SERVER@linuxServerId`，取消确认以 `AbortError` 回到 idle，不显示伪失败。所有 root 文案和 API client 方法名同步改为服务器终端语义。
  - 正式环境仍默认关闭并强制 WSS 定向网关；本地 `test` profile 显式启用服务器终端、使用 Java `user.dir` 作为工作目录并允许直连签票 Java 的 `ws://`，未修改 `.env.test` 或 `.env.local`。
  - 同步 HTTP API、安全、部署、多后台、模块与前端包文档；保留 `SUPER_ADMIN`、目标 Java 精确路由、一次性 ticket、Origin、限流、active 租约、超时、清理和无命令正文审计。
- How:
  - 后端 terminal/API 定向测试 22 项通过；前端弹窗、terminal、backend-api 定向测试 77 项通过，13 个前端项目 typecheck、agent-web 生产 build、后端 app reactor package 通过。
  - 按 `.env.test` / `test` profile / JDK 25 重启 backend、opencode-manager 和 frontend，health/readiness 为 UP、前端 3000 返回 200、登录 CORS 和 manager WebSocket 正常。
- Result:
  - Playwright 真实登录后完成“选择服务器工作空间 → 服务器终端 → 二次确认 → WebSocket → 命令输入”，终端执行 `id -un` 写出的用户为 `kaka`，与实际 Java 进程用户一致；终端保持固定高度且可输入。验收截图保存在本机 `.tmp/server-terminal-java-user.png`。
  - 未新增 HTTP 路径、RunEvent/SSE、数据库字段、migration、SQL、generated SDK 或额外权限；`confirmationText` 字段形状保持不变，但确认值从旧 `ROOT@...` 改为 `SERVER@...`，旧前端需与后端同步升级。生产 Linux 仍需按目标 systemd 用户和真实 WSS 网关验收。

### 2026-07-18 - 迁移服务器终端入口并修复 xterm 高度反馈循环

- Why:
  - 用户要求把超级管理员服务器终端放进“选择服务器工作空间”弹窗，并反馈现有 xterm 会持续拉长且无法正常输入。
- What:
  - `ServerWorkspacePickerDialog` 新增绑定左侧当前服务器的“服务器终端”视图，保留 `ROOT@linuxServerId` 逐次确认；切服、返回目录或关闭弹窗都会卸载旧终端并清空确认。运行管理页删除重复入口。
  - `TerminalPanel` 改为有界 viewport + 绝对定位宿主；ResizeObserver 对相同尺寸去重并按动画帧合并 fit，WebSocket open 后同步 cols/rows 并聚焦 xterm。
- How:
  - 继续复用既有 root ticket API、terminal client、xterm/FitAddon 和后端 WSS/PTY 链路，没有新增 API、服务、ticket 类型或安全例外。
  - 前端全量 Vitest 79 files、1273 passed/1 skipped，terminal/agent-web typecheck 与 agent-web 生产 build 通过；`.env.test`/`test` 三服务重启后 health/readiness UP、前端 200、manager health 正常。
- Result:
  - 入口、确认、切服重置、重复 resize 去重、连接后聚焦与键盘 envelope 均有回归覆盖。应用内浏览器运行时因 `Cannot redefine property: process` 未完成登录态视觉点验；macOS 本机默认关闭 root 终端，真实 Linux root + WSS 命令执行仍需目标环境验收。

### 2026-07-18 - 补充设置权限内的新手路径与手册章节

- Why:
  - 用户希望把设置弹窗中普通用户和应用管理员相关的操作纳入新手引导与内置手册，同时不展开超级管理员专属用户管理。
- What:
  - 新增“设置与权限内操作”手册章节并注册到 VitePress、HelpCenter 和宠物问答；引导第 07 步改为说明个人 SSH Key、应用管理、版本库管理和工作空间入口。
  - 同步 FAQ、首次准备、快速开始、手册首页、前端 README/PACKAGE、模块地图，并修正设置角色可见性说明。
- How:
  - 复用现有 SettingsMenu/SettingsAppWorkspacePanel/SettingsRepositoryPanel 的真实权限和操作文案，仅增加手册注册、引导文案与回归断言，没有新增 API、事件或数据状态。
- Result:
  - 定向设置/引导/帮助中心测试 5 个文件 44 项通过；当前 app 的 vue-tsc、用户手册构建、agent-web 生产构建通过；test profile 三服务重启成功，readiness UP、前端 200。构建仍有既有大 chunk 提示。

### 2026-07-18 - 优化普通用户工作区与对话新手路径

- Why:
  - 用户反馈普通用户不知道应用入口、应用选中后工作区仍为空、对话如何建立，以及工作区小地球如何引入需求子条目。
- What:
  - 首次引导 v3 改为锚定真实应用下拉、workspace/version 切换、小地球、新建对话、宠物、设置和手册按钮；明确普通用户不能新建应用、必须选中 workspace/version、首条消息自动建对话。
  - 快速开始、工作区、对话、首次准备、FAQ 和手册首页补充四个入口、空白工作区排查、管理员边界、小地球引入和 `#` 子条目上下文流程；同步前端 README、PACKAGE 和模块地图。
- How:
  - 复用已有 UI、工作区切换、iframe、对话和手册链路，只增加 `data-onboarding` 锚点与文案，没有新增 API、事件或数据状态。
- Result:
  - 5 个前端相关测试文件 185 passed/1 skipped，agent-web typecheck、用户手册构建、agent-web 生产构建通过；test profile 三服务重启完成，backend readiness UP、frontend 200。真实登录态浏览器交互未代填账号，未做登录后视觉验收。

### 2026-07-18 - 发布公共 Mermaid 规约到远端 master

- Why:
  - 用户确认将公共测试设计 Agent 的 Mermaid 11.16.0 规约提交发布到远端主分支，使公共仓库包含该修复。
- What:
  - 将公共配置提交 `3c89512 统一 Mermaid 11.16.0 语法规约` 从现有 `public-usr_test_dev` 分支推送到 `origin/master`。
- How:
  - 推送前执行 `git fetch origin`，确认本地相对 `origin/master` 为 `1 ahead / 0 behind`，随后使用非强制 `git push origin HEAD:master`；最后通过 `git ls-remote` 和远端跟踪引用双重核对。
- Result:
  - 远端 `refs/heads/master` 已从 `37c9ef8` 快进到 `3c89512bae0c6fa681157e61fc4c62e4d8430ed8`，本地公共 worktree clean；未验证平台各节点的公共配置 rollout 状态。

### 2026-07-18 - 公共测试设计 Agent 统一 Mermaid 11.16.0 语法规约

- Why:
  - 公共测试设计 Agent 生成的场景图在节点 label 中直接写入 ASCII 双引号，导致项目使用的 Mermaid 11.16.0 无法解析，图表展示和可视化编辑同时失败。
- What:
  - 在公共 Agent 个人 worktree `public-usr_test_dev` 新增 `test-design/rules/mermaid.md`，集中约束 Mermaid 11.16.0 最低兼容基线、动态 label 转义、ASCII 节点 ID、subgraph 可视化编辑限制和写入/冻结前校验记录。
  - `test-design`、路径法、场景法 skills 以及 generation/review Agents 强制按需读取公共规约；质量门禁和 Phase A manifest 增加 `syntaxBaseline/staticCheck/parserCheck`，模板改为安全的带引号 label 写法，并新增包含 `用户点击"发起取证"` 的回归 eval。
  - 同步公共配置 `README.md` 和 `opencode/AGENTS.md`；公共配置提交为 `3c89512 统一 Mermaid 11.16.0 语法规约`。
- How:
  - 复用现有 test-design rules 加载链路，没有新增平行 Agent、Skill 或运行时代码；parser 不可用时只能记录 `UNAVAILABLE`，不得伪造通过。
  - 使用项目实际 Mermaid 11.16.0 官方 parser 校验公共规约示例、路径模板和场景模板；同时校验 19 个 Agent/Skill frontmatter、规则引用、eval JSON、冲突标记和 `git diff --check`。
- Result:
  - 三个 Mermaid 代码块均通过 11.16.0 解析，公共 Agent/Skill 配置结构校验通过；未修改 API、事件、数据库、前后端代码、环境配置或 generated SDK。
  - 公共配置提交保留在本地 `public-usr_test_dev` 分支，未推送或合并到远端 `master`。

### 2026-07-18 - 超级管理员服务器 root 终端

- Why:
  - 超级管理员需要在运行管理页直接进入当前部署 Linux 服务器，不希望维护额外 SSH 用户名、密码、独立 terminal service、分布式租约或另一套消息协议。
- What:
  - 复用现有 terminal service/ticket/store/WebSocket/限流/超时/清理/审计链路，新增 `server-root` 目标；HTTP POST 通过公共 `BackendJavaRouteResolver`/`BackendHttpForwarder` 路由到目标 Java，WebSocket 由 Nginx 按 `linuxServerId` 精确代理。
  - 进程适配改用 Pty4J，支持真实 resize；服务器终端固定 `/bin/bash`、`/data/testagent` 和最小环境，不继承 Java 密钥。功能默认关闭，仅 `SUPER_ADMIN`、严格确认 `ROOT@linuxServerId`、目标匹配且 Java effective UID 为 0 时签票，公开地址强制 `wss://`。
  - 运行管理服务器行新增 root 终端弹窗，复用改造后的 xterm.js `TerminalPanel`；同步 HTTP API、安全、部署、模块和前端包文档，以及企业 backend/nginx env、TLS 和定向路由渲染脚本。
- How:
  - 没有新增平行 service、Redis lease、数据库或 SSE；workspace 与 server-root ticket 只在目标 JVM 内存中短期保存。Nginx 使用 `TEST_AGENT_NGINX_TERMINAL_ROUTES=linuxServerId=host:port` 生成 exact location，TLS 由证书路径参数显式开启。
  - 后端 terminal/API 定向测试分别 25/16 passed；前端全量 Vitest 77 files、1271 passed/1 skipped，terminal/agent-web typecheck 与生产 build 通过；Nginx 单/多后端、TLS、定向 route 和单机配置脚本通过。
- Result:
  - `.env.test`/`test` profile 三服务真实重启成功，backend health/readiness UP、frontend 3000 返回 200、未认证 root ticket 返回统一 401；fat jar 已确认包含 Pty4J 和 Linux x86-64/aarch64 原生库。
  - macOS 本机不是 Linux root + HTTPS/WSS 企业环境，因此未实际执行 root 命令；生产启用前仍需在目标 Linux 以 root Java、真实 TLS 证书和 WSS 网关完成验收。未改数据库、事件、generated SDK 或 `.env.local`。

### 2026-07-18 - 公共 Agent Diff 面板持续感知磁盘变化

- Why:
  - 既有实现只在当前工作台保存成功后传递一次 revision；该信号被错过或变更来自其他本地 Git/磁盘操作时，已经打开的 Diff 面板会保留旧快照，仍需点击刷新按钮。
- What:
  - 进入“变更”面板时立即复用 `GitChangesPanel.refreshChanges()` 核验三个作用域；停留期间每 5 秒继续调用同一方法，切回文件树/搜索或组件卸载时立即清理定时器。
  - 保留 Agent 文件保存后的 revision 即时刷新，形成“保存立即刷新 + 可见期间兜底核验”两层机制，没有新增 API、事件或第二套 Diff 状态。
  - 同步 frontend、agent-web README/PACKAGE、前端规范和模块图。
- How:
  - TDD 先新增“进入立即刷新、5 秒后再次刷新、离开后停止”组件用例并确认旧实现失败，再扩展 `FigmaFileExplorer`；Git Changes 定向 41 项与 agent-web typecheck 通过。
  - Playwright 真实登录验证公共 Diff 请求在进入面板后新增并持续出现；切回文件树后超过 5 秒，请求计数保持 `5 -> 5`。
- Result:
  - JDK 25 下后端 18 模块打包成功，按 `.env.test`/`test` profile 重启 backend、opencode-manager、frontend；health/readiness UP、前端 3000 返回 200、CORS 正常，manager 无 decode/reconnect 错误。
  - 轮询只在变更面板可见期间执行，不在后台长期扫描 Git；不涉及 HTTP API、RunEvent、数据库、generated SDK、环境配置或安全凭据。
### 2026-07-21 - 固化企业多后台一键部署与扩容配置初始化

- Why:
  - 企业内三台机器已经完成外层包校验和解压，但逐条执行节点包解压、预校验、正式部署、后校验容易漏跑；现场曾出现命令快速结束且 systemd 时间未变化。后续还会增加全新后台，需要可复用的 env 初始化流程。
- What:
  - 完整外层包新增后台、前端无参数入口，从本机网卡识别 `122.233.30.x`，自动选择节点包并连续执行预校验、正式部署和后校验，完整输出写到 `/data/0709/deploy-<IP>.log`。
  - 新后台初始化脚本以包内 `.4` 真实节点配置为基线生成本机 `backend.env`、`docker.env`，只替换 advertised host 和稳定 server ID，不打印密码/token，节点配置归档继续限制在 1 MiB；前端登记脚本在新后台 readiness 通过后幂等追加 Nginx upstream 和 terminal route。
  - 逐机核心脚本从固定两地址扩展为同网段多后台，新增显式 peer 校验参数；前端校验、部署前 readiness 和 Nginx dump 校验按 `nginx.env` 全部后台动态执行，同时保留 `.4/.114` 种子节点约束。
  - 同步企业部署 README、完整多后台操作手册、外层包结构测试、多节点核心测试和一键入口隔离回归测试。
- How:
  - 外层入口只编排现有 `deploy-multi-backend-node.sh` 的三个模式，不复制 Java、Docker 或 Nginx 部署实现；共享函数按文本处理 dotenv，不 source 现场配置。新后台继承集群共享的 DB/Redis/manager/internal proxy 配置，RSA 仍只取 JAR 内 `BOOT-INF/classes/rsa-private.key`。
- Result:
  - 自动 IP/三阶段执行、新 `.115` 配置初始化、前端登记、扩展后的多后台校验、完整包结构和 AI 文档校验均通过；未改 API、事件、数据库、Java/前端业务代码、generated SDK 或 `.env.local`。
  - 本机无法真实连接企业 `.4/.114/.2`，systemd、Docker、Nginx 和跨机 readiness 的最终结果需在企业服务器运行新入口确认；脚本会以非零退出并保留完整日志，不会把未重启误报为成功。

### 2026-07-21 - Nginx 按用户绑定服务器精确首跳路由

- Why:
  - 一台 Linux 服务器严格对应一个 Java 时，用户会话请求仍先落到任意 Java、再由后端权威路由转发，产生了可避免的 Java→Java 二次转发。
- What:
  - 企业 Nginx 将 `TEST_AGENT_NGINX_TERMINAL_ROUTES` 泛化为 `TEST_AGENT_NGINX_SERVER_ROUTES`，为每个 `linuxServerId` 生成精确 HTTP/终端路由；目标 Java 为 primary，其余 Java 为 backup，未知或缺失路由头继续走 `least_conn`。
  - 固定外层离线包封装可复用旧前端节点包：只在临时副本中把旧终端路由键迁移为统一 server route 键，源敏感包保持不变，缺失、重复或新旧并存时拒绝交付。
  - 前端仅在内存保存 `/processes/me` 返回的 `linuxServerId`，为用户 OpenCode、Session、Run、工作空间和 RunEvent/运行态 SSE 动态注入 `X-Test-Agent-Linux-Server-Id`；登录、应用列表和系统管理等共享控制面请求保持普通负载均衡。
  - Nginx 在转发前清除路由提示头和外部 `X-Test-Agent-Backend-Routed`，后端保留 binding/contextToken 权威校验与 Java→Java 兜底；CORS、HTTP/SSE/安全规范及单机、多后台部署手册同步更新。
- How:
  - 配置生成器只接受静态白名单中的安全 server ID 和 backend endpoint，拒绝重复映射及新旧变量同时存在；故障切换仅允许连接错误/超时，未开启 `proxy_next_upstream non_idempotent`。
  - 五组企业部署/完整外层包回归、Shell 语法检查、前端 100 项定向测试、三个 TypeScript 项目 typecheck、lint/build、后端 API 主代码构建与 CORS 2 项测试均通过。
  - 使用 `.env.test` / `test` profile / JDK 25 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 返回 200，路由头 CORS 预检和未认证伪造头 401 契约通过。
- Result:
  - 固定拓扑下，绑定已解析后的会话请求可由 Nginx 直接进入目标 Java；旧前端、未知/过期/伪造提示仍由默认 upstream 和后端权威路由安全兜底。
  - 本机没有目标 Linux Nginx，实际 `nginx -t/-T` 与 primary 故障切换仍需在企业前端机验收；部署脚本会在替换配置前执行 `nginx -t`，生效后检查 `nginx -T`，失败自动回滚。
  - 后端全 reactor 测试仍被任务外既有 `UserManagementApplicationServiceTest` 的旧构造器调用阻断；前端宽泛测试曾遇到任务外 jsdom Canvas 未实现，相关定向测试、生产 build 与真实服务启动均已通过。
  - 未修改数据库、Flyway、RunEvent 类型、generated SDK、`.env.test` 或 `.env.local`。

### 2026-07-21 - 超级管理员安全删除用户并原位补全 TCDS 信息

- Why:
  - TCDS 用户资料接入后，旧降级账号需要清理；已有会话、工作区或进程的存量用户不能删除重建，否则会丢失原 `userId` 对应的业务关系。
- What:
  - 用户管理新增单个/批量物理删除和单个/批量 TCDS 同步。删除为全有或全无，禁止删除当前登录用户；会话、Run、工作区、进程、调度、夜间任务和配置操作等业务引用会阻断，角色、应用成员、登录日志、SSH key、偏好、反馈和统计等账号附属数据在同一事务清理。
  - 删除前后通过 Redis `SCAN` 撤销目标用户登录 Token，并复用 user mutation gate 失效运行上下文；全部关系型 SQL 位于 `UserDeletionMapper.xml`，未新增 JDBC SQL。
  - TCDS 批量同步以最多四路并发完成全部外部查询后再开启短事务，只刷新姓名、研发部门和部门，保留 `userId`、统一认证号、组织、角色、应用成员及历史数据。TCDS 不返回应用成员关系，缺失应用仍通过现有应用管理添加成员。
  - 设置页增加行内和批量删除/TCDS 同步、当前用户删除保护及明确操作说明；同步 HTTP API、安全、后端模块、前端包和测试文档。
- How:
  - 新增领域删除端口、MyBatis XML 实现、Redis Token 按用户撤销、SUPER_ADMIN Controller/API client/共享 DTO 和 Vue 交互；修复此前 TCDS 构造器变更后未同步的用户管理单测基线。
  - 后端用户管理、Controller、MyBatis、Redis 和 SQL 约束共 34 项定向测试通过；前端用户管理/API 84 项定向测试、三个相关 TypeScript 项目 typecheck、生产 build 和后端全 reactor 跳过测试打包通过。
- Result:
  - `.env.test` / `test` / JDK 25 三服务真实重启成功，backend health/readiness 为 UP、frontend 3000 返回 200、登录 CORS 和 manager WebSocket 正常。
  - 前端全量 Vitest 为 1446 passed / 1 skipped / 1 failed；唯一失败仍是任务外 `DirectoryRows.test.ts` 把 role=`radio` 的“上传”按 role=`button` 查询，并伴随 jsdom Canvas 未实现，本次未修改文件浏览器代码。
  - 新增 HTTP API 和高权限删除安全边界；未修改数据库结构、Flyway migration、RunEvent、generated SDK、`.env.test` 或 `.env.local`。

### 2026-07-21 - 修复企业同源构建服务器终端地址解析

- Why:
  - 企业发布以空 `VITE_TEST_AGENT_API_BASE_URL` 构建同源 `/api`，超级管理员打开服务器终端时，前端把空 base 传给 `new URL(ticketUrl, baseUrl)`，浏览器因此报 `pty_ticket_failed: Failed to construct 'URL': Invalid base URL`。
- What:
  - 复用 terminal 包既有 `toWebSocketUrl`：base 非空时保持原解析逻辑；base 为空时直接使用绝对 `ws(s)://`，把绝对 `http(s)://` 转换为 `ws(s)://`，旧后端相对地址保留给浏览器按当前页面解析。
  - 新增空 base 下绝对 WSS ticket 和相对旧 ticket 两个回归用例，并同步 terminal README/PACKAGE 兼容性说明。
- How:
  - terminal/terminal-panel 定向 7 项、加入服务器工作区选择器后 9 项测试通过；terminal 与 agent-web typecheck、空 API base 生产构建通过。
  - 使用 `.env.test`、`test` profile 和 JDK 25 重启三服务，health/readiness、前端 200 和 CORS 通过；真实登录超级管理员后打开服务器终端，PTY 状态到 `open`，执行 `printf 'codex-terminal-ok\n'` 得到同名输出，不再出现 `PTY_TICKET_FAILED`。
  - Nginx Shell/单多后台/完整包验证通过；最终完整包逐层校验外层 ZIP、内层 release 和三份节点包，确认新 `TEST_AGENT_NGINX_SERVER_ROUTES` 唯一、旧键为零、编译产物含空 base 分支，OpenCode Node worker 镜像验证通过。
- Result:
  - 最终交付物为 `deploy/internal/dist/test-agent-two-backend-complete.zip`，SHA256 `2845af8a43d65a67140846a62ab3155c81637cd23c8a7cf75e787e7188468ecd`；内层标准 release SHA256 为 `c78f0fabbe7f28b6ab7a0de8bc458be859584526a562b512fcd29ebebc098dcc`。
  - 前端全量 Vitest 唯一失败仍是任务外既有 `DirectoryRows.test.ts` 把 role=`radio` 的“上传”按 role=`button` 查询；mock Playwright 用例在终端步骤前被当前上传策略隐藏 `notes.txt` 阻断，真实 PTY 浏览器链路已单独通过。
  - 未修改 HTTP API、RunEvent、数据库/Flyway、generated SDK、鉴权或安全契约；本次只修复前端 URL 兼容性并重建离线交付包。

### 2026-07-21 - 应用撤权后隐藏保留工作区

- Why:
  - 移除应用成员后不能直接删除可能含未提交内容的个人 worktree 和历史 Session，但继续在工作台展示旧文件、版本和运行上下文会造成“仍有权限”的误解，并暴露已撤权工作区信息。
- What:
  - 全局最近工作区在映射到托管应用时复核应用启用状态和有效成员关系；撤权、停用或删除后返回空，同时保留最近偏好、个人工作区记录和物理 worktree。非托管兼容工作区保持原语义。
  - 工作台在窗口重新聚焦和前台每 30 秒刷新成员应用目录；当前应用消失后废弃迟到的应用选择响应，清空文件树、编辑器、Diff、Session/Run 与工作区选择，再切换到仍有权限的首个应用或进入空态。
  - 历史 Session 继续作为只读记录保留；`GitChangesPanel` 兼容撤权空态或旧 mock 缺少 `files` 的 Diff 响应，避免异常阻断空态渲染。
  - 同步工作区模块、前端、HTTP API 和后端部署文档，明确“服务器数据保留、当前工作区不可见”的产品及人工磁盘回收语义。
- How:
  - 后端服务测试 56 项和跨模块撤权集成测试 1 项通过；前端 Git Changes 单测 40 项、撤权与普通切应用 Playwright 2 项、agent-web TypeScript 检查及生产构建通过。
  - 使用 `.env.test`、`test` profile 和 JDK 25 重启 backend、opencode-manager、frontend；backend health/readiness 为 UP，frontend 3000 返回 200。
- Result:
  - 撤权后工作空间不删除但不再可见或可重新进入，前台最长感知延迟为 30 秒，窗口重新聚焦会立即刷新；SCM 权限申请地址继续使用 HTTPS。
  - 仅收紧既有 `GET /workspace-management/recent-workspace` 的可见性响应语义；未新增 API、RunEvent、数据库/Flyway、SQL、generated SDK 或环境配置，兼容非托管历史工作区。
  - 共享工作区另有未提交的工作区大文件分片上传改动，本次未修改或暂存这些文件。

### 2026-07-21 - 工作区文件分片上传与渐进式完整预览

- Why:
  - 工作区和 Agent 配置通过单条 WebSocket Base64 消息上传或读取较大文件时，会触发帧大小/内存边界并关闭连接；用户要求上传不设置业务总大小上限，预览可继续读到完整内容，同时明确提示超大文件可能卡顿。
- What:
  - 文件 WebSocket 新增 begin/chunk/complete/abort 分片上传会话，浏览器默认按 256 KiB 顺序发送；服务端写同目录隐藏临时文件、校验声明大小后不覆盖发布，并在取消、失败、断连或残留超时后清理。前端工作区和 Agent 配置上传期间显示全局遮罩、当前文件及字节进度。
  - 5 MiB 仅作为一次性读取和文本编辑阈值；超过后切换为约 512 KiB、UTF-8 边界对齐的渐进只读预览。用户可“继续加载一段”或“加载全部（可能卡顿）”直至 EOF，界面持续显示进度及内存/Monaco 卡顿提醒；文件大小或修改时间变化时停止混合拼接。
  - 同一 WebSocket 连接的文件请求使用 `concatMap` 保序并把阻塞文件 I/O 调度到 `boundedElastic`，不同连接可由线程池并发处理；同步更新文件 RPC、部署参数、安全/前后端规范、模块说明和用户手册。
- How:
  - 后端工作区服务 51 项、文件 WebSocket/帧配置 23 项通过；全 Maven 流程中 workspace 242 项、API 模块及此前偶发的 runtime 调度测试均通过，随后 persistence 被既有 `V20260717173000` 的 `timestamptz` 与 H2 不兼容阻断（76 errors），与本次文件链路无关。
  - 前端相关 4 个测试文件 112 项通过，用户手册、Vue TypeScript 与生产 build 通过；前端全量 1456 passed / 1 skipped，唯一稳定失败仍为任务外 `DirectoryRows.test.ts` 把 role=`radio` 的“上传”按 role=`button` 查询，另一个异步用例单独复跑通过。
  - 使用 JDK 25、`.env.test` 和 `test` profile 重启 backend、opencode-manager、frontend；backend readiness 为 UP、frontend 3000 返回 200。
- Result:
  - 上传不再受应用层文件总大小限制，实际能力由浏览器、网络、磁盘和基础设施超时决定；大文件可分段预览到 EOF，但保持只读，文本编辑/保存仍受默认 5 MiB 安全阈值约束。
  - 仅扩展既有平台文件 WebSocket RPC 和前端交互；未新增 HTTP API、RunEvent 类型、数据库/Flyway、SQL、generated SDK 或环境配置文件，保留旧单帧上传操作用于兼容。
  - 并发出现的 Agent 配置 rollout/worktree claim 改动不属于本次任务，本次提交不暂存这些文件。

### 2026-07-22 - 公共 Agent 发布后台排空与脏副本诊断

- Why:
  - 公共 Agent 远端推送和 rollout 激活已完成后，HTTP 请求仍在“完成并广播更新”阶段同步认领本机 Git/进程排空任务，可能长期占住发布窗口；窗口执行中又禁止关闭。
  - 多服务器的共享运行副本彼此独立，单台服务器变脏时拉取只返回通用冲突，页面还把 `initialized=true + CONFLICT` 显示成“已初始化”，看不到具体文件或恢复建议。
- What:
  - 公共 `update`、`update-and-push`、`publish` 在远端事实确认、持久化 rollout 激活和低延迟广播后直接返回，不再从 HTTP 请求线程认领本机同步；本机及其它服务器统一由广播消费者或默认 5 秒数据库补偿任务继续同步、登记进程和排空。
  - Git 提交/推送进度窗口执行中可关闭，关闭不取消请求；第 5 步改为“创建后台同步任务”。
  - 共享仓库脏状态在拉取异常中复用 Git porcelain 路径并附排查建议；无法解析路径时明确提示在目标服务器执行 `git status --short`。系统管理将脏且已初始化的仓库显示为“存在本地变更”，失败后刷新服务器行，并提供带二次确认的“放弃本地变更并拉取”，只 reset 已跟踪文件，不影响个人公共 worktree、不删除未跟踪文件。
  - 同步更新工作区/前端 README、HTTP API、内部广播和后端部署文档。
- How:
  - 复用既有 `PublicAgentConfigRolloutCoordinator` 持久化状态、Redis 广播和定时补偿，没有新增线程池、状态机或 API；前端继续使用已有 `discardLocalChanges` 请求字段。
  - 后端定向 47 项、工作区模块 243 项（连同 common/domain reactor 均通过）；前端定向 48 项、全 workspace typecheck 和生产 build 通过。一次误带 `--` 的全量 Vitest 为 1456 passed / 1 skipped / 4 failed，失败均是既有 `DirectoryRows` role、时间线异步和 Mermaid/jsdom canvas 基线，与本次两个定向文件无关。
  - 使用 JDK 25、`.env.test`、`test` profile 重启 backend、opencode-manager、frontend；health/readiness UP、前端 3000 返回 200、登录 CORS 正常、manager WebSocket 已连接。
- Result:
  - 公共发布成功响应不再等待服务器排空，进度窗口可以安全隐藏；rollout 仍以数据库任务保证重启、丢广播和跨服务器恢复。
  - `.4` 与 `.114` 状态不一致时，页面会明确指出这是对应服务器共享运行副本的本地差异并列出文件；管理员可先核对再显式恢复。
  - 仅调整既有 HTTP 接口执行时序和错误信息，兼容现有请求/响应字段；未新增 RunEvent、数据库/Flyway、SQL、generated SDK、环境配置或凭据变更。
### 2026-07-22 - 企业双后台增加独立 XXL MySQL 并修正存量库迁移顺序

- Why:
  - 企业双后台交付需要把 XXL-JOB 使用的 MySQL 8.4 作为独立离线镜像部署到现有 PostgreSQL 服务器，并将初始化凭据与两台 Java 配置一次性安全生成、封装。
  - 企业环境上午已经部署过包含 `V20260721213000` 的旧包；新引入但尚未交付的 `V20260721134000` 会触发 Flyway out-of-order，不能按“新库”处理。
- What:
  - 新增 `.147` MySQL 节点配置模板、离线镜像导入/容器部署/验证脚本和自动识别本机 IP 的节点入口；完整包固定包含 `.147/.4/.114/.2` 四份节点包，但节点配置压缩包继续各自小于 1 MiB。
  - 打包脚本新增 `mysql:8.4` linux/amd64 拉取、架构校验和 Docker tar 导出；完整包封装前校验两台后台、前端 Admin upstream 和 MySQL 节点使用同一组应用密码/access token，不打印敏感值。
  - 两台后台强制校验 XXL 开关、`.147:3306`、Admin/executor 端口并在部署后验证本机 Admin；前端校验并探测每个 XXL Admin upstream。
  - 将尚未在企业交付的夜间迁移改为 `V20260722130000`，版本晚于已交付的 `V20260721213000`，存量库不需要开启 `SPRING_FLYWAY_OUT_OF_ORDER` 或手工修改 Flyway 历史。
  - 同步企业部署 README/多后台手册、数据库文档、模块说明和密钥交付安全规范。
- How:
  - dotenv 始终按文本解析，不执行 `source`；MySQL root/应用密码和 XXL access token 使用强随机值生成，只写入 `0600` 敏感节点配置。MySQL 数据固定在 `/data/testagent/mysql`，重复部署只重建容器、不删除数据目录。
  - 最终大包逐层验证发现 `unzip -Z1 | grep -q` 在 `pipefail` 下会因 SIGPIPE 误报 MySQL 镜像缺失；改为完整消费 ZIP 列表，并用镜像条目后 2000 个文件的回归包覆盖该边界。
  - 存量库升级必须先停两台旧 Java，因此固定首节点 `.4` 只做本机全量验证并延后 peer 探测；第二台 `.114` 反查 `.4`，前端再同时检查两台 Java/Admin，避免首节点服务已成功却因 `.114` 尚未启动返回 `verify_exit=1`。
  - 保留工作区中并行出现的 Agent 配置服务和前端管理面板改动，本次不暂存、不覆盖，也不混入干净构建来源。
- Result:
  - 本地 MySQL 8.4 amd64 容器由正式部署脚本启动并为 healthy，应用账号可连接；XXL Admin、Java readiness、前端均通过，本地 PostgreSQL 只记录 `20260722130000`，未启用 out-of-order。
  - 夜间任务持久化集成测试 6 项通过；MySQL、双后台节点、完整包、AI 文档校验均通过。最终外层包 SHA256 为 `442267b3b0ca388d2dd7ea6e1ccca5790ac84f2709ab9bc79432d1119c4dfdb7`，内层 release SHA256 为 `969430681caad50719c7e1ac41367e8e5d266d5582882f711993b7ad0acac2ae`。
  - 涉及企业部署配置、离线镜像、Flyway 版本兼容和密钥交付安全；未修改 HTTP API、RunEvent、generated SDK 或业务 SQL 内容。

### 2026-07-22 - 修复公共 Agent 发布污染历史、待发布恢复与脏 worktree 误诊

- Why:
  - 企业 `.114` 的公共 Agent 本地提交已成功，但个人分支历史含 `@testagent.local` 无效 committer，远端拒绝整段历史，页面仍停在广播阶段且重开后因 Git status clean 丢失重试入口。
  - `.4` 显式拉取实际命中了当前管理员个人 worktree 的 `opencode/opencode.jsonc` 未提交修改，但旧错误只说“Git 工作树存在未提交变更”，导致被误判为 `.114` 共享仓库异常。
- What:
  - 公共 publish 在合并远端后只投影最终文件树，以当前远端提交为唯一父节点和当前管理员企业身份生成线性提交；个人分支先 reset 到该干净提交再按分支 refspec 非强推，切断历史无效提交身份。
  - 公共 Diff 在 porcelain clean 时比较个人/共享 HEAD 与文件树，新增向后兼容的 `publishPending`；页面重开后可直接“重新推送”，不重复本地 commit。有真实未提交文件时仍优先走正常暂存、提交或回退。
  - 公共拉取脏状态返回 `repositoryKind/path/dirtyFiles/discardLocalChangesAllowed`，区分当前管理员个人 worktree 与共享运行副本；系统管理页面展示目标服务器、绝对路径和文件，并允许显式放弃已跟踪修改再拉取，其他管理员 worktree 与未跟踪文件不受影响。
  - 公共发布失败提示明确本地提交已保留、远端与其他服务器未更新；进度弹窗执行中可关闭，发布成功响应不再等待后台 rollout 排空。
- How:
  - 真实临时 Git 仓库验证污染提交不是新发布提交祖先、作者/提交者为企业邮箱、分支 refspec 可实际推送；common Git 43 项、Agent 配置服务 48 项、前端两个目标文件 48 项通过，TypeScript 全 workspace 检查和生产 build 通过。
  - 一次参数误传的前端全量测试为 1493 passed / 1 skipped / 1 failed；唯一失败是既有 `DirectoryRows.test.ts` 把 role=`radio` 的“上传”按 role=`button` 查询，与本次文件无关。
  - 使用 JDK 25、`.env.test`、`test` profile 完整构建并重启 backend、opencode-manager、frontend；health/readiness UP，前端和 CORS 返回 200，manager WebSocket 已连接并应用配置。
- Result:
  - 部署后 `.114` 的 clean 待发布个人提交会恢复“重新推送”入口，并由新 publish 自动消除历史无效提交身份；`.4` 会明确显示个人 worktree 下 `opencode/opencode.jsonc` 的真实脏状态，管理员可按是否保留选择提交或回退后拉取。
  - 仅扩展既有公共 Diff HTTP 响应字段并优化发布/拉取语义；未修改 RunEvent、数据库/Flyway、SQL、generated SDK、环境配置或凭据。

### 2026-07-22 - 企业平台与 MySQL 离线包拆分并补齐容器诊断

- Why:
  - 企业 `.147` 执行旧 MySQL 入口后没有可见 Docker 容器，旧脚本又丢弃 `docker run` 返回的容器 ID，普通 `docker ps` 无法展示已退出容器，现场缺少直接诊断信息。
  - MySQL 8.4 镜像与每次更新的平台 JAR、前端和 worker 绑定在同一个外层包，导致普通平台升级也要重复传输不变的大镜像。
- What:
  - 固定拆为平台 `test-agent-two-backend-complete.zip` 和 MySQL `test-agent-mysql-offline.zip` 两个包；平台包只含 `.4/.114/.2` 和平台 release，MySQL 包只含 amd64 镜像、`.147` 敏感配置及 MySQL 入口。
  - 默认 `package-release.sh` 只构建平台 release；MySQL 用 `--mysql-only` 单独导出，再由 `package-mysql-offline.sh` 无交互覆盖固定文件名。平台封装仍读取 `.147` 源节点包校验两台 Java 与 MySQL 应用密码一致，但不把它打入平台包。
  - MySQL 部署成功时输出容器 ID 和 `docker ps -a`，失败时输出容器状态、退出码和末尾 80 行日志；SELinux 启用时为独占数据目录添加私有标签，始终保留 `/data/testagent/mysql`。
  - 同步企业部署入口和多后台手册，明确首次传两组、后续普通平台升级只传平台包。
- How:
  - MySQL 部署、平台分包、MySQL 分包、自动节点入口、systemd 首装/升级及开发脚本回归通过；正式 MySQL 脚本在本地真实重建 `mysql:8.4` amd64 容器，应用账号连接通过且原数据目录保留。
  - 从提交 `51dca7772` 重新构建后逐层校验两个外层 ZIP、平台内层 release、JAR 内置 RSA、两个 amd64 镜像、节点 SHA 和每份节点配置小于 1 MiB。
- Result:
  - 平台包约 259 MiB，SHA256 `382fea44a0fedf46434954cb0decc75c3855aa89896e167b6bee083185057c92`；MySQL 包约 228 MiB，SHA256 `745845decef03bade44aa43b8c828c1f3076e6781104855caa0d5b400d0c3f10`。
  - 最终文件已写入 `deploy/internal/dist/` 和 `/Users/kaka/Desktop/qr-decode/out/`；未修改 HTTP API、RunEvent、数据库/Flyway、业务 SQL、generated SDK 或现场凭据。

### 2026-07-22 - 企业 XXL 调度切换到外部 MySQL

- Why:
  - 现场明确取消 `.147` MySQL 容器，改为两台 Java 直接连接既有外部 MySQL；继续交付容器镜像和 `.147` 节点包会造成误部署和额外传输。
- What:
  - 两台后台固定连接外部 `122.210.106.43:3306/xxl_job`，当前使用现场指定的既有高权限账号；JDBC 增加 `createDatabaseIfNotExist=true`，库存在后仍由 Admin 子上下文 Flyway 幂等初始化表和任务。
  - 平台外层封装不再读取或校验 `.147` 节点包，只校验 `.4/.114` 的外部 JDBC、账号密码和 access token 一致；当前 U 盘交付恢复为唯一平台 ZIP 与 SHA。
  - 同步单/多后台和企业入口文档；MySQL 容器脚本仅保留为其它隔离环境备用，不属于当前现场交付。
- How:
  - 平台封包、自动节点入口和开发脚本回归通过；从提交 `137b31a86` 完整重建 JAR、前端、programs 和 amd64 worker，逐层确认两份敏感节点配置、内层 release、JAR RSA、无 MySQL 镜像且节点包小于 1 MiB。
- Result:
  - 最终平台包约 259 MiB，SHA256 `92a41d85f6984252c1d02ee4bc49259416e1cf285c0acc807cc9d61f4b626492`，已写入 `deploy/internal/dist/` 和 `/Users/kaka/Desktop/qr-decode/out/`；旧固定名 MySQL 容器包已从两处输出目录移除。
  - Mac 到目标 3306 的 TCP connect 成功，但 MySQL 初始握手和 Docker 内只读登录在 5 秒内超时；外部账号、来源 IP 白名单和实际 MySQL readiness 必须在企业 `.4/.114` 网络继续验证，当前不能宣称远程登录已验证。

### 2026-07-23 - 修复 macOS 隧道接口导致 OpenCode 端口误冲突

- Why:
  - 昨日新增的 manager 外部监听探测会枚举所有本机 IPv4；macOS `utun` 点对点隧道地址 `198.18.0.1` 会接受任意端口连接，导致实际空闲的 4096–4105 全部被误报为 `PORT_CONFLICT`，默认用户无法初始化 TestAgent。
- What:
  - manager 的本机 IPv4 探测排除 `net.FlagPointToPoint` 接口，继续检查 loopback、物理网卡及其它活动非隧道接口；补充纯 flags 单测和 manager README 说明。
- How:
  - 修复前定向测试在 4096 复现 `port 4096 has an active TCP listener`；修复后 `go test ./internal/process -count=1` 与 `go test ./...` 全部通过。
  - 使用 JDK 25、`.env.test`、`test` profile 完整构建并重启 backend、opencode-manager、frontend；默认用户真实初始化成功，OpenCode 1.17.7 监听 4104，平台返回 `READY/RUNNING`，`/global/health` 为 healthy 且 `/global/config` 返回 200。
- Result:
  - 开启当前 VPN/隧道网络时本地端口池可正常分配，同时保留真实宿主机监听冲突保护；backend readiness、frontend 和 CORS 均验证通过。
  - 未修改 HTTP API、RunEvent、数据库/Flyway、generated SDK、环境配置或安全契约；只影响 manager 本机端口可用性判断。
### 2026-07-23 - Redis 7.4.9 企业独立离线升级包

- Why:
  - 企业当前 Redis 5.0 在 XXL-JOB 服务不可用期间出现 `RedisSystemException: Error in execution`；现场决定不做 Redis 5 兼容代码改造，改为沿用本地 Redis 版本并单独升级企业 Redis。
  - Redis 是两台 Java 的共享必需依赖，升级必须与日常平台包解耦，并防止直接覆盖旧容器、误删原数据或出现“容器健康但 RDB 未加载”的假成功。
- What:
  - 本地 Compose Redis tag 从浮动 `7-alpine` 固定为实际运行版本 `7.4.9-alpine`；新增固定官方 amd64 manifest 的独立封包脚本，输出 `test-agent-redis-offline.zip`、SHA、Redis 镜像 tar、强随机密码配置和双后台连接片段。
  - 新增 Redis 配置、容器部署/健康检查脚本及验证脚本；部署固定 `noeviction`、RDB + AOF everysec、protected mode 和密码，拒绝无 `--replace-existing` 的同名容器替换，永不删除数据目录。
  - 新增完整企业升级手册，覆盖 `.4/.114` 停写、Redis 5 只读盘点、最终 RDB 与备份、新目录恢复、密码合并、分步恢复及回滚；说明本包未自带企业 TLS/独立 ACL 用户，不能宣称满足完整生产 TLS 基线。
- How:
  - 首次 Redis 5 RDB 冒烟发现 Redis 7 在配置 `appendonly yes` 且没有 AOF 时会健康启动但不加载旧 RDB；部署脚本据此增加“先关闭 AOF 加载复制的 RDB → 动态生成 Redis 7 multipart AOF → 重启 → 核对所有 DB key 总数”程序。
  - shell 语法、封包结构/权限/密码一致性、危险数据删除静态检查均通过；最终离线 tar 真实加载为 linux/amd64，临时容器通过认证、Redis 7.4.9、PING、SET/GETDEL 验证。
  - 使用官方 Redis 5.0.14 amd64 临时容器在 DB0/DB1 写入两种数据并生成 RDB，最终包成功转换、重启和读取两个 key；全部临时容器与目录已清理，原本地 `test-agent-redis` 保持 running。
- Result:
  - 最终敏感包位于 `deploy/internal/dist/test-agent-redis-offline.zip`，权限 `0600`，SHA256 为 `ee0d6a7d8103c617970fbc0daa66951a0310d64c8c7d13f8c2e74cfa26dff6e2`。
  - 未修改 Java/前端业务代码、HTTP API、RunEvent、数据库/Flyway、SQL、generated SDK 或现有 `.env`；企业现场尚未执行，实际 Redis 主机、数据目录、服务形态、备份可恢复性和防火墙需按手册先确认。

### 2026-07-23 - 基于当前代码重建 OpenCode 1.18.4 企业双后台完整包

- Why:
  - Redis 独立升级包完成后，需要从当前代码重新生成后台、前台、programs、manager/worker 和固定 `.4/.114/.2` 双后台部署包，并确保 OpenCode 1.18.4 升级与新 Redis 凭据一起交付。
- What:
  - 以同源空 `VITE_TEST_AGENT_API_BASE_URL` 完整构建后台 JAR、前端静态包、`test-agent-programs.tar.gz`、linux/amd64 worker 镜像、标准 release ZIP 和双后台外层完整包；manager 构建号为 `V20260723.124040`。
  - `.4`、`.114` 节点 `backend.env` 已与独立 Redis 7.4.9 包的 host、port、64 位密码一致；两台仍共享相同 manager token，并继续连接外部 `122.210.106.43` MySQL，平台包不含 MySQL 镜像。
  - 最终平台包和 Redis 包连同 SHA 已复制到 `/Users/kaka/Desktop/qr-decode/out/`；输出目录中的敏感外层包和 Redis 包均保持 `0600`。
- How:
  - GitHub release 与 Go proxy 下载出现 SSL/EOF 波动时，先并行下载 OpenCode 官方归档到本机临时缓存，按固定大小和 SHA-256 校验后通过支持 Range 的临时 HTTP 服务供 Docker 构建；Docker 内仍再次校验归档 SHA、二进制 SHA 和 `--version`。
  - 启动器 5 项测试和 worker 运行态验证通过，覆盖 OpenCode 1.18.4、glibc 2.31、断网 Tool 依赖、`subagent_depth=2` 与优雅停止；封包、节点部署、systemd、Nginx、Shell、AI 文档验证及外层/内层/节点深度校验通过。
- Result:
  - 最终平台外层包 `test-agent-two-backend-complete.zip` 为约 351 MiB，SHA256 `2cbd4bda1f9662c92995b6ca6f1b8a07dc45e2f34547a433cc8f75e141e1124d`；内层 release SHA256 `750240e2950a93ea18e01213e2c75b06af5914db5618912f8f3aa6379ffa1d2b`。
  - programs SHA256 `26482d883c73dbdd8088dca01965795a92720507058e4695080168c83a74addd`，worker tar SHA256 `aa83c8ee8704cfa162280986d1258f25a7d9f087bfb06a35d5d5b7d6af572c33`；OpenCode 官方归档与二进制 SHA 仍分别为 `4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc`、`6ce6570e7db9a40e7bd3304ebdfff607920bde8cafd2eb5587bd7a26f89ba0b5`。
  - 本次未修改 Java/前端/manager 源码、API、RunEvent、数据库/Flyway、SQL、generated SDK 或 `.env`；企业真实 systemd、Docker、Nginx、外部 MySQL 和跨机网络仍需按 `.4 -> .114 -> .2` 顺序现场验收，OpenCode 既有用户进程需在升级后重启。

### 2026-07-23 - 合并远端 Redis/XXL 登录修复

- Why:
  - `main` 在执行 `git pull --rebase origin` 时停在冲突状态，需要把远端 `9dc1ced8d` 的 Redis 5/HTTP XXL 登录兼容修复与本地五个提交安全合并。
- What:
  - 完成已有交互式 rebase，将本地提交重放到 `origin/main`；解决 XXL 排查手册、集成模块 README 和 `PlatformXxlSsoController` 冲突。
  - 冲突结果同时保留 Redis Lua 原子消费与完整异常日志、SSO 自有 503 状态页、`adminTab` 次生异常说明、外部 `122.210.106.43` MySQL 拓扑和固定 HTTP 入口两节点 `COOKIE_SECURE=false` 约束。
- How:
  - 使用 `git range-diff` 核对五个本地提交均被保留；JDK 25 下运行 `PlatformXxlSsoControllerTest` 4 项，另运行 manager 端口、XXL 诊断、Redis 部署/封包及 AI 文档验证。
- Result:
  - `main` 已基于 `origin/main`，远端/本地计数为 `0/5`，工作区无冲突；全部相关验证通过。未推送远端，未修改 `.env`、API、RunEvent、数据库/Flyway、SQL 或 generated SDK。

### 2026-07-23 - 企业双后台扩至每节点 1000 个 OpenCode 端口并携带会话日志重打包

- Why:
  - 用户要求以 2026-07-22 17:00 后的全部代码、架构和中间件变化重新生成企业包，并明确两台 worker 均直接发布 1000 个 OpenCode 端口；随后补充要求把全部 `.agents/session-log*.md` 一并纳入交付基线。
- What:
  - 当前 `.4 + .114` 双后台节点端口池由 `4096-4115` 调整为 `4096-5095`，逐机预校验和部署后校验同步检查新末端口；多后台手册明确页面全局 `OPENCODE_MANAGER_MAX_PROCESSES` 必须改为 `1000`，并说明 1000 个端口不等价于已验证 1000 进程承载能力。
  - `package-release.sh` 在内层发布 ZIP 的 `.agents/` 下纳入当前仓库全部 `session-log*.md`，并复用既有封装函数增加 `--zip-only`，用于完整二进制构建通过后只重封当前脚本与会话日志；缺少 backend/frontend/programs/worker 任一制品时拒绝生成部分包。
  - 外层完整包封装逐一校验当前仓库的会话日志均已进入内层 ZIP；复用旧现场节点包时只在临时副本补齐 Cookie、大文件和 `4096-5095` 端口池等非密钥字段，不改源敏感包或输出密钥；worker 启动脚本缺省端口上限也同步为 `5095`。
  - 同步双后台、XXL 诊断、完整包和 AI 文档回归。
- How:
  - 使用空 `VITE_TEST_AGENT_API_BASE_URL` 全量重建后端 JAR、前端、programs、linux/amd64 worker 和内层发布 ZIP；manager 构建号为 `V20260723.133043`，OpenCode 1.18.4 官方归档大小、归档 SHA、二进制 SHA 和版本校验通过。
  - 启动器 5 项测试、worker 断网运行态、systemd 首装/升级、双后台节点、完整包、XXL 只读诊断、Shell 语法和 AI 文档校验通过；JAR SHA256 为 `9ac6ce8432b79a86eeab853e68bd9a3ac74a282f3552db2681a57f192f7fd8b7`，programs SHA256 为 `b78df8437165b8dbac5113cc295ba827e7604ab0bedd1561c5078965eeefb5cf`，worker tar SHA256 为 `a7c7dbfc09e0efdeaae6fefab8c1c7edd2ce0cea6c8acd5d394de1a9fa64ba05`。
- Result:
  - 本次会话日志已通过 `--zip-only` 进入重封的内层 ZIP，并复用两台后台既有受控密钥生成 1000 端口节点包和固定名外层完整包；最终 SHA 只在交付回复和配套 `.sha256` 文件中给出，避免把包自身哈希递归写入包内日志。
  - 未修改 Java/前端/manager 业务源码、HTTP API、RunEvent、数据库/Flyway、SQL、generated SDK 或现场 `.env`；企业真实防火墙、Docker 1000 端口映射耗时和 1000 进程资源承载仍需现场验证。

### 2026-07-23 - 固化中转机目录并补齐 Redis 与平台全量手册

- Why:
  - 企业中转机的真实交付目录始终是 `~/Desktop/mimoagent/0709`，之前回复又误写为目标服务器的 `/data/0709`，现有稳定文档和企业部署 skill 没有把这个边界固化得足够清楚。
- What:
  - 在企业部署 README、多后台手册、Redis 离线手册和项目部署 skill 中统一约定：中转机只使用 `~/Desktop/mimoagent/0709`，`.20/.4/.114/.2` 目标服务器才使用 `/data/0709`。
  - 新增 `deploy/internal/FULL-UPGRADE-RUNBOOK.md`，按中转机 → Redis 5 盘点/备份/升级 → `.4` → `.114` → `.2` → 页面配置/验收/回滚给出单文件全量命令。
  - 部署 skill 同步清理旧的 20 端口和 `TEST_AGENT_BACKEND` 示例，固定当前双后台 `4096-5095`、页面 `OPENCODE_MANAGER_MAX_PROCESSES=1000` 和 `.4 → .114 → .2` 顺序。
  - Redis 封包脚本新增 `--zip-only`，只更新已有敏感包中的手册/脚本，保留已与平台节点包匹配的 Redis 密码和 amd64 镜像。
- How:
  - 复用现有 `REDIS-OFFLINE.md`、`MULTI-BACKEND.md` 的数据备份、节点一键部署、验证与回滚命令，新手册只负责把两条已有流程按当前现场拓扑串联，不新增部署实现路径。
  - `tools/verify-ai-docs.sh` 增加新手册存在性、中转机目录、1000 进程参数的静态回归，防止后续再退回错误路径或旧容量。
  - Redis 封包回归覆盖重封前后密码不变、手册路径已更新、镜像 SHA 仍有效和固定文件无交互覆盖。
- Result:
  - 完整手册和目录边界已进入项目稳定文档；它们会随当前会话日志通过 `--zip-only` 重封进企业平台发布包。本次不修改 API、RunEvent、数据库/Flyway、业务代码、现场配置或密钥。

### 2026-07-23 - 工作空间与 Agents 文件树支持右键批量文件操作

- Why:
  - 左侧工作空间和 Agents 树原先依赖双击进入重命名，缺少符合桌面文件管理习惯的右键入口、Ctrl/Cmd 多选、批量删除和多文件拖动；用户同时要求补齐复制、剪切、粘贴。
- What:
  - 两棵树移除双击重命名，统一复用右键菜单；Ctrl/Cmd+单击维护多选，右键支持删除、复制、剪切、粘贴，拖动任一已选项会整体移动。工作空间允许文件/目录剪切和移动，复制继续限制为普通文件；Agents 多选范围为文件，目录作为粘贴和拖放目标。
  - 工作空间批量操作逐项复用既有文件 WebSocket、树缓存、Tab、撤销栈和 Git Diff 刷新链路；Agents 新增 `agent-config.copy`、`agent-config.move` WebSocket RPC，后端复用 `WorkspaceFileService` 的路径安全校验、同名拒绝和复制/移动实现，保留公共级 `SUPER_ADMIN`、应用级 `APP_ADMIN` 及应用白名单约束。
  - 新增共享右键菜单壳和多项删除确认；同步 frontend/backend 模块 README、PACKAGE、用户手册及 HTTP/事件流协议文档。
- How:
  - 前端定向 134 项通过，lint、全 workspace typecheck、生产 build 通过；全量 Vitest 为 1550 passed / 1 skipped，两个 Mermaid 测试在并行运行时波动失败，单 worker 独立复跑 21 项全部通过。
  - JDK 25 下 Agent 配置应用服务与 WebSocket handler 定向 70 项通过；使用 `.env.test`、`test` profile 完整构建并重启 backend、opencode-manager、frontend，health/readiness、前端 3000 和登录 CORS 均通过。
- Result:
  - 工作空间和 Agents 文件树已具备右键重命名及多选删除/剪切/粘贴/拖动，普通文件可复制；批量操作采用顺序执行并明确报告部分成功，不提供跨作用域剪贴板。
  - 新增两个 Agent 文件 WebSocket op；未新增 HTTP 路径、SSE 事件、数据库/Flyway、SQL、依赖、generated SDK 或环境配置变更，现有权限和兼容路径保持不变。

### 2026-07-23 - 修复文件树无操作目录的空白右键菜单

- Why:
  - 工作空间组合目录 `docs` 不可重命名/删除且剪贴板为空时，右键处理仍挂载共享菜单壳，但全部菜单项都被权限条件隐藏，页面因此显示一条类似空输入框的白色长条；Agents 目录存在同类隐患。
- What:
  - 工作空间和 Agents 树在打开菜单前统一判断是否至少存在一项可执行操作；没有改名、删除、复制/剪切、粘贴、撤销或添加到对话等入口时只阻止浏览器原生菜单，不再渲染空菜单壳。
  - 保留组合目录已有的新建/上传行尾入口；当剪贴板可用时仍可右键粘贴，普通可写文件的完整右键菜单不变。
- How:
  - 两个定向 Vitest 文件 54 项通过，前端全 workspace lint 和 typecheck 通过。
  - 使用 `.env.test`/`test` profile 已运行的真实页面复现修复前空菜单；热更新后右键 `docs` 不再出现 `role=menu`，右键 `README.md` 仍显示重命名、删除、添加到对话、复制和剪切。
- Result:
  - 截图中的空白长条已消失，工作空间和 Agents 两棵树均有回归保护。未修改 API、WebSocket op、RunEvent、数据库/Flyway、权限、安全、环境配置或 generated SDK。

### 2026-07-23 - 固化 OpenCode 运行文件 Git 忽略规则

- Why:
  - OpenCode 1.18.4 首次启动会在公共配置目录生成 `package.json`、`package-lock.json` 等依赖元数据；上游只在 `.gitignore` 不存在时写一次规则，双后台中已有但不完整的规则会让公共配置管理持续误报本地变更，`git reset --hard` 又不会清理未跟踪文件。
- What:
  - 新增统一 `opencode-runtime.gitignore` 和幂等补齐脚本；企业后台升级时修复已初始化公共仓库，未初始化节点不提前建目录。
  - 官方启动器在创建 package/lockfile 与模块链接前复用同一清单，worker image/programs 和发布 ZIP 都强制携带该清单；保留已有规则，不删除文件或取消跟踪。
  - 同步企业部署 README、后端扩容/排障文档和 OpenCode 1.18.4 升级说明。
- How:
  - 启动器 Node 测试 6 项、运行文件忽略与 `agents/skills` Git 可见性脚本、双后台节点校验、AI 文档校验、Shell 语法和 `git diff --check` 通过；轻量 `--zip-only` 封装确认新增脚本与清单进入内层 release ZIP。
  - 在外网 Mac 真实构建 `linux/amd64` worker 并导出 programs，断网镜像验证通过 OpenCode 1.18.4、glibc 2.31、Tool 依赖/加载、两处配置目录忽略规则及优雅停止。
- Result:
  - 后续升级已有后台会立即补齐规则；新增后台在公共仓库初始化后的第一次 OpenCode 启动前补齐，不再因运行依赖元数据误报脏仓库。本地 worker 镜像已重建并验证，但未导出正式 worker tar 或完整企业包；未修改 HTTP API、RunEvent、数据库/Flyway、SQL、generated SDK、现场配置或凭据。

### 2026-07-23 - Agent 与 Skill 中文展示并保留英文技术标识

- Why:
  - 公共级和应用级 Agent/Skill 的物理文件名、目录名及运行态候选长期只显示英文；平台已经可以维护双语名称，需要让用户优先看到中文，同时保持 OpenCode、Git 和既有调用标识兼容。
  - 新建表单原先只有一个名称字段且依赖不完整的手写拼音表，中文字符超出映射后会丢失；用户要求英文名选填、留空按完整拼音生成，并同步主 Agent、`@` 子智能体和 `/` Skill 候选。
- What:
  - Agent/Skill 新建表单改为中文名称必填、英文名称选填；引入 `pinyin-pro` 生成完整无声调拼音并规范成小写短横线技术标识，Agent 文件与 Skill 目录继续保持英文。Skill 模板写入 `metadata.display-name/display-name-zh`，`source: test-agent` 继续仅表示平台模板来源；Agent 使用双语 `description`，不扩展 OpenCode Agent metadata，也不修改 OpenCode。
  - Agent 配置列表为 `agents/*.md` 和 `skills/*/SKILL.md` 有界解析双语展示名，并逐段拒绝符号链接越界读取；响应增加可选 `displayName/displayNameEn`。配置树显示中文主名称、英文辅助名称，但所有读取、改名、Git 操作仍使用原始 `path/name`。
  - 对话区主 Agent、`@` Agent 与 `/` Skill 候选同步改为中文主名称、英文辅助说明，插入和提交仍使用稳定英文技术 ID；用户手册、前后端模块 README、共享类型和文件 WebSocket 协议文档同步更新。
- How:
  - 前端 agent-web 类型检查、相关 Vitest 250 项通过（1 项既有跳过），用户手册与 agent-web 生产构建通过；JDK 21 下 workspace-management 及依赖模块共 413 项测试通过。
  - 使用 JDK 25、`.env.test`、`test` profile 完整打包并重启 backend、opencode-manager、frontend；backend health/readiness 为 UP，frontend 3000 返回 200，登录 CORS 正常，manager WebSocket 已连接且日志无重连/解码错误。
- Result:
  - 例如中文“接口自动化测试”且英文留空时，实际 Skill 目录为 `skills/jie-kou-zi-dong-hua-ce-shi/`；用户在配置树和运行态候选看到中文主名称，OpenCode 仍识别英文目录、顶层 `name` 和技术 ID。
  - 仅扩展既有 `FileTreeEntryResponse` 可选响应字段并同步事件流协议文档；未新增 HTTP 路径、RunEvent/SSE 类型、数据库/Flyway、SQL、安全权限或环境配置变更，未修改 generated SDK 和 OpenCode 源码。
