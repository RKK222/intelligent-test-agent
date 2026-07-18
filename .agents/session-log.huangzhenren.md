# Session Log - huangzhenren

> 按提交者 `git config user.name` 分文件维护，新增条目置于 `## Entries` 顶部。
> 提交前需回顾所有 `.agents/session-log*.md`（含已冻结旧档 `.agents/session-log.md`）的近期条目。

## Entries

### 2026-07-18 - 新增 Mermaid State Diagram 可视化编辑

- Why:
  - 现有 Mermaid 编辑器只支持 Flowchart 与 Sequence，无法安全编辑 State Diagram 的递归层级、并发区域和伪状态；直接复用 Flow 的扁平模型与重复边规则会破坏 State 语义。
- What:
  - 新增 `stateDiagram` / `stateDiagram-v2` 递归 Scope/Region 领域模型、自研 parser、规范化 serializer、严格校验、B1 紧凑 metadata 和分层 ELK 布局；支持开始/结束、普通状态与多行说明、自循环、标签转换、复合/嵌套状态、Choice、Fork/Join、并发 Region、Note、各层方向和限定直接样式。
  - 新增“概览 + 聚焦”State 画布：根层展示复合状态摘要，双击进入内部，面包屑返回，聚焦层同时展示全部并发 Region；支持元素点击/拖放创建、状态与转换就地编辑、拖线/端点重连、Note 属性和受限颜色编辑。
  - 把通用屏幕坐标拖线控制器抽象为可注入领域连接规则，Flow 保持原规则，State 允许同端点多转换并阻止跨 Scope/Region、伪状态非法方向和基数上限；外部包导出、Markdown 围栏并发保护、官方 Mermaid 双重校验与保存链路不变。
  - 扩展领域、布局、组件、Markdown 和 workbench Playwright 回归；同步前端总 README、editor README/PACKAGE、模块地图以及已批准的设计/执行计划。
- How:
  - 按 TDD 从解析/序列化/校验/metadata 开始，再实现递归布局、概览/聚焦画布和 Markdown 集成；无法安全映射的结构语法降级源码编辑，注释、复杂样式、class/accessibility 指令按 Scope 原样保留。
  - State 应用时先做完整领域校验，再生成规范源码并调用 Mermaid 11.16.0 官方 parser；任一步失败都保留草稿且不覆盖 Markdown。坐标与转换端口继续使用既有紧凑 envelope，损坏/重复 marker 原样保留且不制造第二个 marker。
  - 精确排除共享工作区中并行出现的 backend、scheduler、workspace、AgentWorkbench、file-explorer 等无关改动，只暂存 State 编辑器相关文件和本条记录。
- Result:
  - editor 全量 20 个测试文件、402 项通过；前端全量 Vitest 84 个文件通过（1383 passed / 1 skipped），13 个 workspace 的 lint/typecheck、用户手册和生产 build 通过。目标 workbench Playwright Chromium 1/1 通过，验证 Flowchart、Sequence、State 共用一次 workspace 保存链路。
  - 生产构建确认 `MermaidEditorDialog` 与 `elk.bundled` 仍为独立懒加载资源；只保留既有大 chunk 提示。未新增初始页面依赖，DOMPurify 边界未变。
  - 不涉及后端、HTTP API、RunEvent/广播、数据库、SQL、migration、generated SDK、环境配置、安全权限或外部包导出；Flowchart 与 Sequence 回归全部通过，无未完成编码项。
- Verification:
  - `corepack pnpm exec vitest run packages/editor/tests --reporter=dot`
  - `corepack pnpm test`、`corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm build`
  - `corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --project=chromium -g "Markdown Mermaid Flowchart、Sequence 和 State"`
  - `git diff --check`、全部 session log 近期条目回顾和共享工作区暂存范围复核。
- Next:
  - 在真实浏览器使用超深嵌套、超长 Note/状态说明和大量并发 Region 做人工可用性验收；不影响本次交付范围。

### 2026-07-18 - 定稿夜间定时执行任务设计

- Why:
  - 白天算力不足，需要让用户在现有对话中提交一次性夜间任务，并在北京时间 21:00 至次日 07:00 的 15 分钟时段内无人值守执行。
- What:
  - 新增夜间任务设计规范，确认定时图标位于发送按钮左侧，取消“执行位置”选择；任务始终绑定当前对话，需要新会话时先使用现有新建对话入口。
  - 待执行任务采用当前对话锁定卡与“待执行任务”页签双重展示；同一 Session 只允许一个待执行任务，但其他会话仍可使用。
  - 明确全局时段硬容量、最空闲且尽量早的推荐、同夜窗口顺延、07:00 最终失败、进程自动启动、来源标识和失败卡关闭规则。
  - 技术设计复用 scheduler `USER_PLAN`、运行记录、Redis 锁和 handler；一次性正文放独立业务表，新增 MyBatis/Flyway、执行亲和和服务端内部 scheduled Run 入口，不使用 Cron 计划 payload 保存正文。
- How:
  - 结合现有 `FigmaChatPanel`/`AgentWorkbench`、Session/Run 来源字段、scheduler runner、用户进程公共启动与多 Java 路由约束，固定交互、API、状态机、数据安全、并发容量和测试验收边界。
  - 设计自检消除了“空 Session 最终失败即归档”与“失败状态可见”的冲突：最终失败先解除锁并保留可关闭卡片，关闭后才按是否为空决定归档。
- Result:
  - 本次仅新增设计文档和本条会话记录，未修改代码、API、RunEvent、数据库、环境配置或 generated SDK，也未触碰工作区内并行开发的已有修改。
  - 设计规划未来新增普通用户 HTTP API、来源 DTO 字段、Flyway/MyBatis 数据模型和 scheduler USER_PLAN 能力；待用户复核规范后再编写实施计划。
- Verification:
  - `git diff --check -- docs/superpowers/specs/2026-07-18-night-execution-task-design.md .agents/session-log.huangzhenren.md`
  - 规范占位符、内部一致性、范围和歧义自检；提交前回顾全部 `.agents/session-log*.md` 近期条目。
- Next:
  - 用户确认书面规范后，按 `superpowers:writing-plans` 生成可执行实施计划。

### 2026-07-18 - 增强 Mermaid SequenceDiagram 结构化编辑能力

- Why:
  - 既有 Sequence 编辑器只有参与者与平铺消息，无法安全表达控制片段、生命周期、激活、Note 和嵌套调用，也不能像 Flowchart 一样在画布、结构和属性之间完成可视化编辑。
- What:
  - 将 Sequence 领域模型升级为递归语句 AST，覆盖 8 类参与者、别名/box、10 种标准箭头、消息、Note、注释、显式与快捷激活、自调用、create/destroy、autonumber、多行文本，以及 `loop`、`alt/else`、`opt`、`par/and`、`critical/option`、`break`、`rect` 任意嵌套。
  - parser 改为 tokenizer + 显式容器栈，serializer 依据源码锚点、父容器和语义指纹最小差异回写；CRLF、缩进、大小写、旧紧凑坐标 metadata 与未修改快捷语法保持不变。半箭头、中央连接、Actor 菜单、标题/无障碍指令、`par_over`、分号串联和未知配置作为局部锁定纯文本无损保留。
  - 新增不可变命令/语义校验与确定性布局，统一处理重命名引用、跨分支移动、端点重绑、级联删除、分组连续性、分支生命周期与激活规则；`create` 保持全图唯一，互斥分支 `destroy` 状态隔离。
  - Sequence 使用单个专用 SVG 场景和“元素 / 结构 / 属性”右侧三标签；支持元素点击/拖放创建、生命线拖建消息、消息端点拖拽重绑、参与者横向拖序、结构树键盘/拖放、画布双向选中、就地编辑和带影响数的参与者级联删除确认。Vue Flow 仅复用视口能力。
  - 扩展 Markdown、组件、领域、命令、布局和 workbench mock E2E；将测试引导键同步到产品当前 `onboarding.v7`，恢复目标 Chromium/mobile 场景。同步设计、实施计划、前端总 README、editor README 与包级说明。
- How:
  - 按 TDD 分层补齐 parser/serializer、命令、布局、组件和保存链回归；最终复审发现互斥分支重复 `create` 漏检后，先用命令测试复现，再共享全图 `created` 集合并继续按分支克隆 `destroyed`，修复后复审无 P0/P1。
  - 精确排除同一工作树中并发出现的 backend、API/规范、AgentWorkbench、file-explorer、夜间执行设计等无关变更，只暂存本任务文件与本条记录。
- Result:
  - Sequence 定向 6 文件 89 项通过；前端全量 Vitest 81 个文件通过（1353 passed / 1 skipped），lint、13 个 workspace typecheck、用户手册与生产 build 通过；目标 workbench Playwright 在 Chromium/mobile 2/2 通过，构建仅保留既有大 chunk 提示。
  - 不涉及 HTTP API、RunEvent/广播、数据库、后端、manager、generated SDK、依赖版本、环境配置、安全权限或既有 Markdown 保存契约；高级语法首批按设计仅锁定保留，没有未完成的编码项。
- Verification:
  - `corepack pnpm exec vitest run packages/editor/tests/mermaid-sequence-domain.test.ts packages/editor/tests/mermaid-sequence-commands.test.ts packages/editor/tests/mermaid-sequence-layout.test.ts packages/editor/tests/SequenceVisualEditor.test.ts packages/editor/tests/MarkdownPreview.test.ts packages/editor/tests/mermaid-compact-metadata.test.ts --reporter=dot`
  - `corepack pnpm test`、`corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm build`
  - `corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep "Markdown Mermaid Flowchart 和 Sequence"`
  - `git diff --check`、全部 session log 回顾、冲突标记扫描与最终只读代码复审。
- Next:
  - 在真实浏览器手工验证超深嵌套和超长多行文本的可用性；半箭头、中央连接、Actor 菜单等高级创建入口继续按既定后续范围评估。

### 2026-07-18 - 点击引用资产库展示真实同步进度

- Why:
  - 引用配置左侧已初始化资产库卡片实际调用 `synchronizeReferenceRepository`，但现有步骤弹层只绑定右侧 `/verify` 且只接受 `operation=VERIFY_POINTERS`，导致卡片同步期间页面没有可见进度。
- What:
  - 将核验专用弹层状态抽象为 `SYNCHRONIZE/VERIFY_POINTERS` 操作感知状态机；卡片在同步 POST 返回前打开“创建同步任务 → 各服务器同步 → 汇总同步结果”，右侧按钮继续展示只读核验步骤，两者不互相追加请求。
  - 同步模式逐服务器展示等待同步、同步中、已同步、同步失败、等待重试和离线延后；POST 失败可原地重试，活动任务可直接接管既有 generation，终态手动关闭后焦点回到触发卡片。
  - 弹层继续按仓库、operation、请求序号和 generation fencing，复用既有 2 秒 `/status` 轮询、父弹层锁定和焦点限制；未改后端接口或状态语义。
  - 同步前端工程/agent-web README、PACKAGE、引用配置用户手册以及方案/实施计划。
- How:
  - TDD 先增加 deferred 同步请求回归，确认原实现只因缺少 `资产库同步进度` 失败，再抽象进度状态、动态文案和触发焦点；补充同步请求失败重试、活动同步接管、未初始化兼容和卡片焦点恢复测试。
  - 全量校验首次并行执行时，`typecheck/lint` 同时写 VitePress `.temp` 引发临时模块竞争，agent-chat 异步 Markdown 用例也在资源竞争下超时；两项独立复跑及全量顺序复跑均通过，未修改无关代码。
- Result:
  - 引用配置组件测试 45/45 通过；前端全量 Vitest 79 个文件通过（1309 passed / 1 skipped），13 个项目 typecheck、lint、用户手册和生产 build 通过，构建仅保留既有大 chunk 提示。
  - 仅改变前端交互和稳定文档；不涉及 HTTP API、RunEvent/广播、数据库、后端、manager、generated SDK、环境配置或安全权限。Playwright 仍沿用上一条记录中的 onboarding v2/v7 既有基线，本次未扩大范围修改。
- Verification:
  - `corepack pnpm exec vitest run apps/agent-web/tests/reference-configuration-dialog.test.ts`
  - `corepack pnpm test`、`corepack pnpm typecheck`、`corepack pnpm lint`、`corepack pnpm build`
  - `git diff --check`、全部 session log 回顾和只读差异审查。
- Next:
  - 在真实多服务器环境点击左侧资产库卡片，人工确认长耗时同步的逐节点状态与后台实际 HEAD 收敛一致；Playwright 引导基线修复继续作为独立任务。

### 2026-07-18 - Service 日志切面改为仅在异常时打印

- Why:
  - `ServiceLoggingAspect` 对每个 `@Service` public 方法都打 `service_entry` + `service_exit(success)` 两条 INFO，正常调用产生大量噪声（如 `PublicAgentConfigRolloutService.claimPendingSync` 每 5 秒一条），单条 INFO 不携带结果或异常，诊断价值低。
- What:
  - 删除 `service_entry`、成功 `service_exit`、Mono `doOnSuccess`、Flux `service_stream_start/end`；仅保留异常时 ERROR 日志，并在其中补 `args` 字段（沿用 `argsSummary`，仅类型+轻量值，避免泄漏 prompt/token）。
  - 同步 `package-info.java` 与 `test-agent-api/README.md` 中该切面的描述。
- How:
  - 改 `logServiceCall`：正常返回静默；Mono/Flux 仅挂 `doOnError`；同步异常走 `catch` 记录后原样抛出。
  - `argsSummary` 改为仅在 `logError` 内惰性计算，去掉每次成功调用都算参数摘要的开销；`argsSummary`/`argSummary` 方法保留（有单测直接覆盖）。
- Result:
  - `mvn -pl test-agent-api -am test -Dtest=ServiceLoggingAspectTest -Dsurefire.failIfNoSpecifiedTests=false`：4 passed / 0 failures，BUILD SUCCESS。
  - 仅日志行为变化，无 API/事件/数据库/兼容性影响；事件名 `service_exit status=error` 保留，兼容既有日志查询。
- Pitfalls:
  - 正常调用不再有任何日志；原先依赖 `service_entry` 作为调用频率心跳的观测需改用其他来源。traceId=unknown 的后台调用异常仍会记录。
- Verification:
  - `mvn -pl test-agent-api -am test -Dtest=ServiceLoggingAspectTest -Dsurefire.failIfNoSpecifiedTests=false`
- Next:
  - None。

### 2026-07-18 - 引用资产 Git 指针核验进度与服务器路径

- Why:
  - “刷新 Git 指针”需要等待多服务器核验，原页面只有按钮转圈，用户无法判断任务创建、各节点处理和汇总所处阶段；已选仓库也缺少服务器绝对目录，现场排查不便。
- What:
  - 引用资产库统一状态响应新增可空 `repositoryPath`，由当前平台解析后的 `OPENCODE_REFERENCES_DIR` 与已校验英文名拼接并规范化；参数缺失、历史非法名称或旧响应缺字段时前端显示“服务器路径暂不可用”，不阻断仓库列表。
  - “刷新 Git 指针”在 POST 返回前打开三阶段嵌套弹层，展示创建核验任务、逐服务器核验和汇总结果；逐节点映射等待、处理中、完成、阻塞、等待重试与离线延后，并展示目标分支/HEAD、就绪数量、安全错误和 traceId。
  - 弹层按仓库 ID、请求序号与核验 generation fencing；执行期间禁止关闭弹层和外层引用配置页、拦截 Escape 并限制焦点，终态由用户手动关闭后恢复到当前刷新按钮。POST 失败可重试，状态轮询临时失败沿用 2 秒自动重试。
  - 同步 HTTP API、workspace/API README 与 PACKAGE、前端/backend-api 说明、模块地图、安全规范和引用配置用户手册；未新增接口、事件、广播、数据库、manager 协议或环境配置。
- How:
  - 继续复用既有 `/verify`、`/status` 与状态轮询；后端只在业务响应组装时派生路径，并捕获参数/旧数据路径错误返回空，不记录物理路径。
  - TDD 覆盖路径规范化、缺参、历史非法英文名、Controller JSON、旧响应兼容、弹层先于请求完成出现、三阶段/逐服务器状态、成功/失败/离线、轮询暂时失败、重试、关闭限制和焦点恢复。
  - 调试中确认 Vue 更新仓库快照会替换弹层与按钮 DOM，测试必须断言当前节点；业务状态实际已正确推进，未用放宽状态机掩盖失败。
- Result:
  - 后端定向 reactor 通过：workspace 47 项、API 3 项；前端全量 Vitest 79 个文件通过（1305 passed / 1 skipped），13 个项目 typecheck、lint、用户手册和生产 build 通过，构建仅保留既有大 chunk 提示。
  - 引用组合树 Playwright 的 Chromium/mobile 场景均渲染出目标节点，但当前 `HEAD` 的首次引导已使用 `v7`，E2E 基线仍写入 `test-agent.onboarding.v2`，`el-tour` 遮罩拦截点击后两项超时；本次未扩大范围修改该既有基线。
  - 仅增加向后兼容的可空 HTTP 响应字段；路径只对既有 `APP_ADMIN` 接口可见且不进入日志/错误。未修改 RunEvent、内部广播、Flyway/MyBatis、generated SDK、manager 或 `.env.local`。
- Verification:
  - `mvn -pl test-agent-workspace-management,test-agent-api -am -Dtest='ReferenceRepositoryApplicationServiceTest,ReferenceRepositoryControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `corepack pnpm test`、`corepack pnpm typecheck`、`corepack pnpm lint`、`corepack pnpm build`
  - `corepack pnpm e2e --grep "workspace tree merges references with source colors and exposes non-merged aliases"`（被上述既有 v2/v7 引导基线阻断）
  - `git diff --check`、session log 回顾与只读差异审查。
- Next:
  - 在独立任务中把 Playwright 的首次引导抑制键从硬编码旧版本改为与产品版本同步，再恢复引用组合树 Chromium/mobile 定向绿灯；真实多服务器环境仍需按上一条 session log 的计划完成实际路径与 HEAD 人工验收。

### 2026-07-18 - 引用资产分支切换与服务器指针核验

- Why:
  - 应用管理员需要在引用资产库初始化后受控切换分支并同步所有服务器，同时在配置页查看每台服务器实际 Git branch/HEAD，确认多节点是否真正收敛。
- What:
  - 新增 `switch-branch` 与只读 `verify` 内部 API、`SWITCH_BRANCH/VERIFY_POINTERS` 操作类型、逐服务器 `online/matchesTarget/verifiedAt/syncedAt` 响应，以及 `operation_type/verified_at` Flyway 增量迁移和 MyBatis XML 映射。
  - 分支切换以远端 `ls-remote` 固定目标提交并 CAS 推进 generation；worker 写回实际读取的 branch/HEAD，核验只读本地 Git 元数据。活动任务和历史副本由广播、租约 fencing、在线/离线补偿继续收敛。
  - 配置弹窗增加分支选择和旧/新分支二次确认、目标指针与逐服务器实际指针/在线状态/匹配状态/同步及核验时间、完整 HEAD 复制和“刷新 Git 指针”。成功切换后刷新工作区引用组合树。
  - 前端以选择代次、后端 generation、同代次请求发起序号三层 fencing 丢弃迟到状态；READY 先通知工作区刷新，再独立加载弹窗目录。关闭/重开、失败后重新同步、模糊请求结果和多仓库 pending 均保留有限补偿语义。
  - 最终审查补出并修复 single-branch clone 切换分支缺陷：切换时显式 fetch `+refs/heads/<branch>:refs/remotes/origin/<branch>`，不存在的本地分支从已固定提交创建，不依赖旧 fetchspec 的 `--track`；已有目标本地分支仍执行可快进校验。
  - 同步更新 HTTP、事件、数据库、后端部署、模块 README/PACKAGE、前端包说明和用户手册。
- How:
  - API 与业务层继续要求 `APP_ADMIN`（`SUPER_ADMIN` 继承），并复核应用关联和 `APPLICATION_ASSET_REPOSITORY` 类型；广播名称及仅含 `repositoryId/generation/traceId` 的安全 payload 保持不变，不进入 RunEvent SSE。
  - 数据库副本保留上一代实际指针快照，新 generation 不用目标值伪造实际值；`matchesTarget` 只对完整可信的 `READY` 观察为真。核验使用禁用 optional lock、untracked cache 和 fsmonitor 的只读 Git status。
  - TDD 先复现 single-branch 无共同历史分支切换失败、READY 目录请求期间关闭导致刷新丢失、同 generation 迟到回滚、被拒快照误消费 pending，以及重开首轮列表失败后补偿轮询停摆，再做最小修复；最终综合审查无 Critical/Important。
- Result:
  - 引用/Git/API/MyBatis/Flyway 定向 114 项通过，含真实 single-branch clone 切换到无共同历史分支；后端用户指定 Reactor 中除 persistence 外均成功，persistence 仍被既有 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `TIMESTAMPTZ` 与 H2 不兼容统一阻断（76 errors），本次引用 MyBatis/PostgreSQL/migration 用例均通过。
  - 前端全量 Vitest 77 个文件通过（1290 passed / 1 skipped），13 个项目 typecheck、lint、用户手册和生产 build 通过；构建仅有既有大 chunk 提示。manager `go test ./...` 的 6 个包通过。
  - 全量 Playwright 为 123 passed / 70 failed / 1 skipped；失败是近期日志已记录的 35 类 workbench 文件加载、Agent、Help、设置、模型和运行流旧基线在 Chromium/mobile 的成对失败，精确数量不能与此前提前停止的套件等同。本次引用组合树 Chromium/mobile 定向 2/2 通过。
  - 涉及新增内部 HTTP API、数据库兼容字段和既有内部广播消费语义；不修改广播 payload、RunEvent、generated SDK、`.env.local`、manager 协议或已运行 OpenCode 进程。显式 refspec 不经 shell，凭据仍只在发起/目标 worker 内使用。
- Verification:
  - `mvn -pl test-agent-api,test-agent-persistence -am -Dtest='GitWorkspaceServiceTest,GitWorkspaceServiceRealGitTest,ReferenceRepository*,MyBatisReferenceRepository*' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl test-agent-common,test-agent-domain,test-agent-workspace-management,test-agent-opencode-runtime,test-agent-persistence,test-agent-api -am test`（除上述既有 H2 migration 阻断外，其余目标模块成功）
  - `corepack pnpm test && corepack pnpm typecheck && corepack pnpm build`、`corepack pnpm lint`
  - `corepack pnpm e2e`、`corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep 'workspace tree merges references'`
  - `go test ./...`、`git diff --check`、冲突标记扫描和三轮只读代码审查。
- Next:
  - 在真实双服务器环境完成分支切换、部分节点离线/恢复、逐服务器 HEAD 展示与引用组合树刷新人工验收；另行修复既有 H2 migration 和 workbench E2E 基线。

### 2026-07-18 - 在应用工作区文件树展示引用目录

- Why:
  - 引用配置保存后，应用工作区文件树还只能读取物理工作区，无法按 `merge` 展示引用资产，也无法区分只读引用内容。
- What:
  - 新增 `workspace.view.list/read` 文件 WebSocket 组合视图：`merge=true` 按 `sdd-folder-name` 合并到工作区同名一级目录，纯引用文件/目录标记为蓝色只读；工作区已有同名目录返回 `MIXED` 并保持普通颜色。`merge=false` 以参考别名作为只读一级目录。
  - 后端每次从最新 JSONC 重建挂载，只接受能反向验证到当前应用资产库、本机同 generation READY 副本和平台引用根目录的配置；拒绝物理路径伪造、`.git`、路径穿越与符号链接。单引用失败转为局部 warning，每层最多 1000 项。
  - 工作区目录从 `WORKSPACE` 变为 `MIXED` 时保持稳定 ID；前端刷新按目录深度逐层用新父节点返回的 locator 重放展开状态，并汇总、去重所有已加载目录的 warnings/truncated，恢复后清除旧告警。
  - 引用文件可只读打开、重试、复制逻辑路径和加入对话上下文；保存、重命名、删除、移动、粘贴及撤销入口按来源关闭，普通工作区写操作继续走原 RPC。
  - 加固文件通道权限：托管工作区在 route、ticket 和每条 RPC 都实时校验有效应用成员；非托管 Workspace 默认拒绝，仅 `SUPER_ADMIN` 服务器工作空间兼容入口放行。会话运行上下文对历史非托管 Workspace 的 Session owner 兼容规则不变。
  - 同步 HTTP/文件 WebSocket 协议、安全规范、后端模块 README/PACKAGE、前端包说明和用户手册。
- How:
  - 组合视图继续走平台文件 WebSocket route/ticket/RPC，不新增 Java 间 HTTP 文件代理；引用副本读取复用既有本机安全目录服务，不把物理路径返回浏览器。
  - 前端复用现有 FileExplorer，通过稳定节点 ID 作为缓存/展开键，并保留原 `workspace.list/read/write` 兼容调用。
  - TDD 覆盖 merge/alias、颜色与写入口、JSONC/READY/路径安全、稳定 ID、刷新 locator、告警恢复、鉴权及读失败重试；最终只读复审未发现 Critical/Important 并批准。
- Result:
  - 后端 workspace/runtime/api 聚焦测试 49 项通过；用户指定 Reactor 中 common/domain/workspace/runtime/api 全量均通过。persistence 仍被既有 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `TIMESTAMPTZ` 与 H2 不兼容统一阻断（76 errors），本次未改数据库或该 migration。
  - 前端全量 Vitest 77 个文件通过（1269 passed / 1 skipped），13 个项目 typecheck、用户手册和生产 build 通过；构建仅有既有大 chunk 提示。manager `go test ./...` 通过。
  - 新增引用树 Playwright 在 Chromium/mobile 2 项通过；三个既有工作区切换/未选择工作区场景在两项目共 6 项仍超时或找不到旧空态文案，本次没有扩大范围修复这些既有 E2E 基线。
  - 涉及文件 WebSocket 协议与权限安全边界；不新增 HTTP 端点、RunEvent、数据库表/migration、manager 协议或环境配置，不修改 generated SDK。组合视图为懒加载且每层限 1000 项；真实双服务器人工验收仍沿用上一条引用同步功能的待办。
- Verification:
  - `mvn -pl test-agent-workspace-management,test-agent-opencode-runtime,test-agent-api -am -Dtest='WorkspaceViewApplicationServiceTest,ManagedConversationWorkspaceAccessAuthorizerTest,WorkspaceFileRoutingServiceTest,WorkspaceFileSocketTicketServiceTest,WorkspaceFileWebSocketHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl test-agent-common,test-agent-domain,test-agent-workspace-management,test-agent-opencode-runtime,test-agent-persistence,test-agent-api -am test`（除上述既有 H2 migration 阻断外，其余目标模块成功）
  - `corepack pnpm test && corepack pnpm typecheck && corepack pnpm build`
  - `corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep 'workspace tree merges references'`
  - `go test ./...`
  - `git diff --check`、冲突标记扫描和最终只读代码复审。
- Next:
  - 单独修复既有 H2 migration 与 workbench E2E 工作区选择基线后重跑完整套件；在真实双服务器部署完成引用副本与组合文件树人工验收。

### 2026-07-18 - 增加引用配置与资产库多节点同步

- Why:
  - 应用管理员需要在个人工作区内初始化应用资产库、保证所有在线 Java 服务器副本收敛到同一远端提交，并把可选的首层 SDD 目录安全写入工作区 `.opencode/opencode.jsonc`。
- What:
  - 新增引用库状态/副本领域模型、MyBatis XML 仓储、Flyway `V20260718110000`、APP_ADMIN 内部 API 与 `reference-repository.sync-requested` 内部广播；状态机按固定远端分支 HEAD、代次、数据库租约和定时补偿完成多节点同步，离线节点延后补齐。
  - Git 副本初始化采用同级临时目录和原子改名；已有副本校验 origin、分支、干净状态和快进关系，拒绝未知目录、脏仓库、分叉、符号链接及路径穿越，不删除或强制覆盖冲突内容。
  - 前端仅在 APP_ADMIN/SUPER_ADMIN 的个人应用工作区显示引用配置入口；双栏弹窗覆盖分支选择、2 秒状态轮询、逐服务器错误、首层橙色目录选择及 JSONC 保存/更新。`jsonc-parser` 以最小补丁保留注释、尾逗号、未知字段，并阻止非法 JSONC、非对象 references、Git/字符串同名引用和路径冲突。
  - 新启动及平台管理重启的 OpenCode 进程由公共启动服务注入 `OPENCODE_REFERENCES_DIR`；manager 安全命令展示该非敏感变量，未改变 configUpdate 协议或既有进程。
  - 同步 HTTP API、事件、数据库、部署、模块 README/PACKAGE、manager/前端说明及用户手册；修正既有参数种子 migration 注释中的 Flyway 占位符字面量，使 PostgreSQL 全量迁移可解析，参数值语义未变。
- How:
  - 复用现有应用关联、分支查询、服务器广播、在线服务器快照、公共启动服务和平台文件 WebSocket route/ticket/RPC；凭据只在发起节点解密，广播不携带凭据、SSH key、文件内容且不进入 RunEvent SSE。
  - 代次 CAS、租约 token/fencing 与本机文件锁共同避免并发 worker 回写；临时网络错误指数退避，永久 Git 冲突等待管理员重新同步；目录树每层限制 1000 项。
- Result:
  - 引用功能后端定向 reactor 通过，相关 122 项测试全部通过（含真实 Git、安全路径、租约/补偿、MyBatis H2、Testcontainers PostgreSQL、权限 API、配置冻结、运行时注入和 Spring 装配）；`go test ./...` 通过。
  - 前端全量 Vitest 75 个文件通过（1247 passed / 1 skipped），typecheck、lint、用户手册和生产 build 通过。
  - 用户指定的后端全量命令在 persistence 被既有 `V20260717173000` 的 `timestamptz` 与 H2 不兼容阻断（76 errors；引用库 PostgreSQL/MyBatis 用例本身通过）；前端 E2E 在既有 `workbench.spec.ts` Agent 树隐藏/`agents` 目录缺失处连续失败 14 项，随后停止，2 项中断、176 项未运行。本次未扩大范围修改这些基线问题。
  - 本机没有真实双服务器环境，离线恢复、跨 Java 租约互斥和副本收敛由测试覆盖，仍需在双服务器部署环境执行计划中的人工验收。
  - 涉及新增内部 HTTP API、内部广播、两张数据库表和兼容性配置字段；不修改 generated SDK、`.env.local`、OpenCode 源码或 manager 协议。既有运行进程不重启，引用目录在下次平台管理启动/重启后生效。
- Verification:
  - `mvn -pl test-agent-app -am '-Dtest=GitWorkspaceServiceRealGitTest,ReferenceRepository*,ConfigurationManagementApplicationServiceTest,ConfigurationManagementControllerTest,OpencodeProcessStartupServiceTest,RuntimeManagementCommandServiceTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl test-agent-persistence -am -Dtest=MyBatisReferenceRepositoryRepositoryIntegrationTest,MyBatisReferenceRepositoryPostgresqlIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `go test ./...`
  - `corepack pnpm test && corepack pnpm typecheck && corepack pnpm build`、`corepack pnpm lint`
- Next:
  - 修复既有 H2 migration 兼容和 workbench E2E Agent 树基线后重跑完整套件；在真实双服务器环境完成离线恢复与 HEAD 一致性人工验收。

### 2026-07-18 - 新增引用资产通用参数

- Why:
  - 需要为引用资产（规格文档、参考素材等）提供统一根目录参数 `OPENCODE_REFERENCES_DIR`，并约定规格驱动（SDD）场景识别规格目录的名称清单参数 `REFERENCES_SDD_FOLDER_NAMES`，作为后续引用资产消费功能的配置基础。
- What:
  - 新增 Flyway migration `V20260718100000__seed_references_params.sql`，向 `common_parameters` 种子化两个 `all` 平台参数：
    - `OPENCODE_REFERENCES_DIR` = `${SYS_DATA_ROOT_DIR}/agent-opencode/references`，中文名「引用资产根目录」，`editable=false`（只读，不允许修改）。
    - `REFERENCES_SDD_FOLDER_NAMES` = `docs,spec`，中文名「规格驱动标准目录名称」，`editable=true`（可改）。
  - `OPENCODE_REFERENCES_DIR` 的值用 `'$' || '{SYS_DATA_ROOT_DIR}/...'` 拼接规避 Flyway `${...}` 占位符替换，复用 `all` 行引用平台参数 `SYS_DATA_ROOT_DIR` 的解析能力，与 `OPENCODE_SESSION_DIR` 等路径参数一致。
  - 同步 `CommonParameterSeedMigrationTest`（SQL 内容校验）、`MyBatisCommonParameterRepositoryIntegrationTest`（迁移后断言）、`docs/deployment/database.md`、`backend/test-agent-persistence/README.md`。
- How:
  - 沿用既有种子迁移模式：`all` 平台单行 + 显式 `editable` 列；`SYS_DATA_ROOT_DIR` 引用沿用 consolidate 迁移（V20260629230000）的字符串拼接写法。
  - 用户原始值 `docs,spce` 经确认改为 `docs,spec`（SDD 规格目录通用命名，`spce` 疑为笔误；该值 editable，可后续调整）。
- Result:
  - `CommonParameterSeedMigrationTest` 2 个方法通过（含新增 `referencesParamsSeedContainsAllPlatformDefaults`）；BUILD SUCCESS。
  - `MyBatisCommonParameterRepositoryIntegrationTest` 因预存在问题无法端到端验证（见 Pitfalls），新增断言按构造正确。
  - 未修改 API、RunEvent、DTO、前端、安全、环境配置或 generated SDK；无新增依赖。
- Pitfalls:
  - main HEAD 上跑全量 Flyway 的 H2 测试（含 `MyBatisCommonParameterRepositoryIntegrationTest`）被 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `timestamptz` 类型阻断（H2 不识别，已用干净 HEAD 复跑确认预存在，提交 `7b0df66a9` 引入）；本次未修（AGENTS 规则 1 最小范围）。生产 PostgreSQL 支持 `timestamptz`，迁移可正常执行；待该 H2 兼容问题修复后，本迁移可在 H2 端到端验证。
  - 工作区存在前一会话（同一提交者，今日「工作空间创建只允许选择测试工作库」）未提交的前端改动；任务期间并发会话已将其提交为 `1fee2cc3f 修复工作空间版本库类型筛选`（含前端代码与前一会话 session log 条目）。本次改动与前端无关，未暂存、未触碰前端文件；本提交 session log diff 仅含本次新条目。
- Verification:
  - `mvn -f backend/pom.xml -pl test-agent-persistence -am test -Dtest=CommonParameterSeedMigrationTest` 通过。
  - 干净 HEAD（`git stash -u`）复跑确认 `timestamptz` 失败与本次改动无关。
- Next:
  - 后续若有引用资产消费方，按 `CommonParameterValues.resolvedValue("OPENCODE_REFERENCES_DIR")` 读取根目录、按 `REFERENCES_SDD_FOLDER_NAMES` 逗号拆分识别规格目录。

### 2026-07-18 - 工作空间创建只允许选择测试工作库

- Why:
  - 应用工作空间创建下拉此前直接使用全部已关联版本库，应用代码库或应用资产库也会被展示并可能默认选中，与工作空间只能关联测试工作库的业务约束不一致。
- What:
  - “工作空间管理”的已关联版本库下拉改为只展示测试工作库，并在标签后提示“只能关联类型为测试工作库的版本库。”；“应用与版本库关联”页仍展示全部类型。
  - 默认版本库选择同步从过滤后的列表产生；没有已关联测试工作库时保持空选择且不读取分支。
  - 同步 agent-web README/PACKAGE、内置用户手册和组件回归测试。
- How:
  - 按 `repositoryType=TEST_WORK_REPOSITORY` 筛选；显式类型优先，只有类型缺失时才回退 `standard=true`，兼容历史数据但不让冲突旧字段覆盖新类型。
  - TDD 先确认混合类型下拉仍显示并默认读取应用代码库，再实现派生列表；将旧的非测试工作库创建用例替换为空选择回归。
- Result:
  - 前端全量 Vitest 72 个文件通过（1206 passed / 1 skipped）；用户手册、Vue TypeScript 检查和生产 Vite build 通过，构建仅保留既有大 chunk 提示；`git diff --check` 通过。
  - 未修改 API、RunEvent、DTO、数据库、后端、安全、环境配置或 generated SDK；无新增依赖。

### 2026-07-17 - 会话日志改为按提交者分文件

- Why:
  - 多人/多智能体同时修改单一 `.agents/session-log.md` 频繁冲突；实测两次读取间隔内顶部条目已变化，确认存在活跃并发写。按 `git config user.name` 分文件可消除并发写冲突。
- What:
  - 修改 `AGENTS.md` 规则 22/23 与完成标准、`docs/guides/self-checklist.md`、`docs/guides/ai-workflow.md`、`.opencode/skills/code-update-handoff/SKILL.md`。
  - 约定每位提交者写入 `.agents/session-log.{id}.md`；旧的共享 `.agents/session-log.md` 原地冻结为历史归档，仅回顾时阅读、不再续写。
- How:
  - `{id}` 取 `git config user.name`，转小写、连续非 `[a-z0-9]` 字符折叠为单个 `-` 并去首尾 `-`，结果为空时回退 `hostname -s`（本机为 `huangzhenren`）。
  - 旧档内容完全不动（不在顶部加横幅），冻结语义只由规则文档承载，避免与正在发生的并发写冲突。
  - 回顾范围扩展为全部 `.agents/session-log*.md` 近期条目。
- Result:
  - 各提交者只写自己的文件，消除并发写冲突；旧历史保留为只读归档；回顾覆盖所有 session-log 文件。
- Pitfalls:
  - 同一提交者在两台机器并发操作仍写同一文件（罕见）；两个不同提交者清洗后同名需手动区分；纯分析/问答会话不产生条目。
- Verification:
  - 未改代码，无单测；仅文档与约定变更。提交前已回顾旧档近期条目，与本次约定变更无冲突。
- Next:
  - None。
