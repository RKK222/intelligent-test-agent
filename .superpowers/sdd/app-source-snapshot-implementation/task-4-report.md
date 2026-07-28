# Task 4 实施报告：工作台源码入口、四步弹窗与源码模式

## 结论

Task 4 已完成。`agent-web` 已提供应用源码入口、四态紧凑列表、四步物化弹窗、持久化操作进度恢复和显式 `MANAGED/APP_SOURCE` 工作区模式。源码工作区继续复用平台 Workspace 与文件 WebSocket，保留普通文件写入、Session/Run、OpenCode 和会话终端；Git 发布、应用 Agent 发布与应用版本选择在组件和 handler 两层关闭。

本任务未修改 OpenCode 快照、generated SDK、后端 API/事件实现、数据库结构、SQL、Flyway、`.env.local` 或其它环境配置。

## 页面与交互

- `WorkbenchFooter` 在工作空间切换图标右侧新增源码图标，且保持源码、引用配置、服务器工作空间的固定顺序；源码模式改为“返回应用工作区”。
- `AppSourcePicker` 的紧凑入口只展示可打开或曾下载的 active、expired、个人占用状态；`NOT_DOWNLOADED` 只进入管理弹窗。打开动作服从 `openable`，不可用原因直接展示，个人占用显示 owner 姓名与 UCID，底部固定“下载版本库”。
- `AppSourceDialog` 使用四步宽弹窗：全部关联仓库显式选择；分支与固定提交精确树；TEAM/PERSONAL、默认 48 小时且限制 1–72 小时的 retention 和覆盖确认；全局及逐服务器安全步骤时间线。
- 服务端索引返回的最近 exact selection 默认勾选并以低强调成功色标记；提交始终发送完整 `{path,type}` 集合、`expectedGeneration` 和树 snapshot 的 `expectedTreeCommit`。
- operation 执行后没有取消入口。关闭只停止当前 observation；重开从 `latestOperation` 数据库 snapshot 开始，再申请新 ticket。终态显示 safe summary、耗时、commit、traceId、服务器错误和 partial failure 重试。

## 工作区语义与能力边界

- `app-source-workspace.ts` 定义 `SelectedWorkspaceKind = MANAGED | APP_SOURCE`。source context 只保存 app、repository、generation、purpose、workspace、server 和 expiry 逻辑身份，不接收或构造物理根目录。
- source `open` 后按响应 `workspaceId` 读取真实 Workspace，再沿用既有文件 route/ticket/RPC、Session、Run、OpenCode 与 terminal 链路。
- `ordinaryWorkspaceCanWrite` 是普通 Workspace 文件能力的唯一判断：源码快照自身可写；托管应用仍只允许个人 worktree。编辑读取/保存、创建、复制、移动、上传、聊天附件、重命名、删除和安全可逆操作均复用该能力。
- 源码模式隐藏 Git Changes、应用 Agent 区和版本选择；Git diff/open handler、应用 Agent mutation/runtime reload handler 继续保留防御性守卫。普通文件操作后的 Git diff 刷新会静默跳过，不把已成功文件写入误报为失败。
- 切回应用版本、个人工作区或其它托管 Workspace 时清理独立 recent。刷新和窗口 focus 读取 recent 后重新调用 `open` 校验成员、expiry、generation 和本机 READY 副本；确定失效时清 recent 并回退普通应用工作区。

## 异步 authority 与终态收敛

- 根树首载和同一分支的懒加载子目录共享 app/repository/branch authority；切 branch、切 repository 或关闭弹窗会使整组旧响应失效。迟到 catch/finally 不覆盖新请求的 snapshot、loading 或 error。
- progress observation 绑定 token、operationId、app、repository、仓库 generation 和 target generation。关闭弹窗、切仓库、generation 变化或新 observation 都会拒绝旧 snapshot/WS 回调。
- terminal operation 使用 operationId 集合一次性 claim；重复终帧不会重复执行 `open` 或 fallback。`SUCCEEDED/PARTIAL_FAILED` 仍由服务端 `open(targetGeneration)` 决定本机能否切入；只有结构化失效错误清 recent 并回退，暂时服务错误保留当前能力和重试上下文。

## TDD RED / GREEN 证据

1. **入口顺序。** `WorkbenchFooter.test.ts` 先因缺少源码按钮失败；加入工作空间切换右侧入口后 GREEN。
2. **源码列表。** `AppSourcePicker.test.ts` 先因组件缺失失败；实现四态颜色、不可用原因、owner 与固定底部动作后 GREEN。
3. **四步物化。** `AppSourceDialog.test.ts` 先因组件缺失失败；实现完整 exact set、snapshot commit、TEAM→PERSONAL、72 小时与覆盖确认后 GREEN。
4. **异步分支默认。** 首次下载仓库没有历史 branch，测试在异步 branches 到达后仍得到空选择；增加第一分支默认选择后 RED→GREEN。
5. **全局安全步骤。** 进度测试先找不到全局 `safeSummary`；加入全局与服务器两级步骤轨迹后 RED→GREEN。
6. **索引选择强调。** 默认勾选存在但没有视觉语义；新增 `is-indexed-selection` 后 RED→GREEN。
7. **源码能力。** `figma-file-explorer.test.ts` 先因源码 banner/门禁缺失失败；加入可写文件树、隐藏 Git/Agents/版本和返回入口后 GREEN。
8. **普通写入回归。** Playwright 首次在源码 workspace 编辑后找不到保存按钮；根因是既有代码用 `currentPersonalWorkspaceId` 反推全部写权限。抽出 `ordinaryWorkspaceCanWrite` 并让组件、读取、保存及全部结构 handler 共用后，真实保存与根目录新建文件在 Chromium/mobile 均 GREEN。
9. **同步终态切代。** 物化 API 直接返回 `SUCCEEDED` 时，工作台只观察非终态，未打开 target generation；统一 terminal 收敛后自动 `open(targetGeneration)`，Playwright RED→GREEN。
10. **branch/repository 竞态。** authority 单测先因缺少 token/helper 失败；加入 root/lazy 共享 authority、repo/close 失效和 stale finally 防护后 GREEN。
11. **旧 WS 迟到。** observation 测试先因缺少 operation/repository generation 校验与终态 claim 失败；加入完整 authority 和一次性终态处理后 GREEN。
12. **recent fallback。** Playwright mock 覆盖无效 recent 的重新 open、清理和托管工作区回退，桌面/移动均 GREEN。

## 最终验证

- `corepack pnpm test apps/agent-web/tests/AppSourcePicker.test.ts apps/agent-web/tests/AppSourceDialog.test.ts apps/agent-web/tests/app-source-workspace.test.ts apps/agent-web/tests/WorkbenchFooter.test.ts apps/agent-web/tests/figma-file-explorer.test.ts`：5 files / 38 tests，通过。
- `corepack pnpm --filter @test-agent/agent-web typecheck`：通过，无 TypeScript 诊断。
- `corepack pnpm playwright test apps/agent-web/tests/workbench.spec.ts --grep "application source|invalid recent"`：Chromium/mobile 4/4，通过。
- `corepack pnpm test apps/agent-web/tests`：57 files / 923 passed / 1 skipped，通过；jsdom 输出既有 Canvas `getContext` 提示，不影响结果。
- `corepack pnpm --filter @test-agent/agent-web build`：用户手册与 agent-web 生产构建通过；仅输出既有大 chunk 提示。
- `git diff --check` 与 `git diff --cached --check`：均通过；精确暂存仅包含本任务的前端、测试、文档与报告文件。

## 文档同步

- `frontend/README.md`：应用源码 UI、普通写能力、能力门禁与 recent 恢复边界。
- `frontend/apps/agent-web/README.md`：工作台职责、异步 authority、组件/Playwright 测试说明。
- `frontend/apps/agent-web/src/PACKAGE.md`：新增组件、helper 与 `AgentWorkbench` 编排职责。
- `docs/architecture/module-map.md`：`apps/agent-web` 与 Workspace 文件 WebSocket 的职责边界。

## 影响与兼容性

- **API：** 只消费 Task 3 已有应用源码 API/client，没有新增或修改 HTTP wire。
- **事件：** 只消费独立应用源码进度 WebSocket，不新增 RunEvent/SSE 类型；关闭 observation 不发送取消。
- **数据库：** 无结构、migration、SQL 或持久化模型变化。
- **性能：** 页面同一时刻只保留一个源码进度连接；关闭、切仓库和终态都会释放。目录树按需懒加载，authority 只维护常量级内存状态。
- **安全：** 不保存物理路径、凭据或原始 Git 错误；所有打开/恢复均由服务端重新鉴权。前端门禁不是权限事实源，Workspace 每条文件 RPC 仍由后端实时授权。
- **兼容性：** 普通托管应用继续使用个人 worktree 写权限与现有 Git/Agent 流程；源码能力是显式新模式，不改变旧 Workspace、RunEvent 或 DTO。

## 未完成事项与风险

- 本任务只接入前端，不改变后端物化和权限事实；多服务器真实 Git/副本联调沿用 Task 2/3 的后端验收。
- Playwright 使用平台 API/File WebSocket mock 验证桌面与移动交互，没有执行真实多 Java 进度 WebSocket 联调。
- 提交前已回顾全部 `.agents/session-log*.md` 近期条目，未发现覆盖、丢弃或误合并其它开发者成果的风险。按 Task 4 协作约束不更新 session log，由最终汇总任务统一决定是否记录会话信息。

## Fix Round 1（2026-07-28）

### 修复结果

- 已有 TEAM generation 的 PERSONAL 选项禁用并说明原因，组件 submit 与 `AgentWorkbench` materialize handler 都拒绝伪造降级；PERSONAL→TEAM、TEAM→TEAM 保持可用。
- 只有 PENDING/RUNNING latest operation 自动进入步骤 4 并恢复观察。SUCCEEDED/PARTIAL_FAILED/FAILED 可查看历史，也可用“重新下载/更新配置”回到步骤 1。
- branches/tree、materialization、progress 分别使用独立低敏错误状态，只在发生步骤展示。branch、tree snapshot commit 与 exact set 在组件和父层共同校验；当前树不存在的历史路径显式列为失效并允许移除。
- 四步第 1 步展示当前应用全部 repository summary 并要求显式选择；紧凑入口隐藏 `NOT_DOWNLOADED`。快速切仓库时旧 branches/tree response、catch 和 finally 都因 dialog/tree authority 失效。
- 来源工作区能力统一覆盖文件树、编辑器与 Run Diff footer、FigmaShell 应用个人配置重载、Agent/Skill Hub mutation、版本选择/新增、Workspace Agent 文件保存与配置变更；UI 隐藏与 handler 首部拒绝同时生效，普通文件写、Session/Run、OpenCode 和终端不变。
- source selection/recovery 使用 token、app、repository、generation、workspace kind 的完整 intent。切应用、撤权、返回 managed、关闭入口/弹窗或卸载统一清理 picker、dialog、tree、socket、重连 timer 与旧 authority；A 的迟到 list/open/getWorkspace 不能覆盖 B。
- recent 只按 `BackendApiError.code` 的 `FORBIDDEN/NOT_FOUND/CONFLICT` 判断权威失效，不从 message 猜测。current source 收到空 recent 会回退；网络、超时及 5xx 保留 source/recent 并显示重试提示；初始 managed + 空 recent 保持静默。
- PENDING/RUNNING observation 意外断线或 client failure 后，工作台以 250ms 起、4s 封顶的有界退避串行执行 operation snapshot、新 ticket 与新 WebSocket；连接中、timer、authority 和 terminal 都有单一所有权，主动关闭不发送 cancel。backend-api 会区分意外 close 与调用方 close，并将前者映射为一次安全 client failure。
- 新下载和已有下载更新的 retention 默认值统一为 48 小时；仍在提交时父 handler 会拒绝重复 materialize/retry。
- 同代码路径顺带修复 active 但本机无 READY 副本仍显示成功色的问题；该状态保留可见、禁用打开并显示原因。

### Round 1 TDD 证据

1. **Picker/Dialog RED：** 2 files / 11 tests 中 7 failed、4 passed；失败分别证明 `NOT_DOWNLOADED` 泄漏、24 小时默认值、TEAM 可降级、未列全仓库、终态锁步骤 4、步骤错误不可见、旧 branch commit/phantom path 仍可提交。实现后 11/11 GREEN。
2. **Purpose/intent/error RED：** `app-source-workspace.test.ts` 9 tests 中 3 failed；缺少 purpose 转换、完整 intent equality 与结构化恢复错误判断。实现后 9/9 GREEN。
3. **挂载/client RED：** `FigmaEditorArea` 未透传 `workspaceKind`、已打开的 Hub mutation 弹窗在权限撤销后仍写入、backend-api 意外 close 不报告失败，共 3 failed / 18 passed。实现后 3 files / 21 tests GREEN。
4. **Workbench/Playwright RED：** 来源编辑器仍暴露版本 cascade；current source 的 503 被错误清 recent/fallback；A 的迟到 repository/getWorkspace 覆盖 B；下载弹窗没有父级 repository 选择；断线没有自动恢复；关闭重开残留步骤 4。修复后的真实 mock 流程覆盖 DB RUNNING snapshot、ticket1 首帧、断线、ticket2 step、关闭无 cancel、ticket3 重开 PARTIAL_FAILED、target generation 只 open 一次及 ticket4 retry。
5. **同路径 Minor RED：** active 但 `openable=false` 的紧凑条目仍带 `is-active`；测试 1 failed / 2 passed。改为 `is-unavailable` 后 3/3 GREEN。

### Round 1 最终验证

- `corepack pnpm test apps/agent-web/tests/AppSourcePicker.test.ts apps/agent-web/tests/AppSourceDialog.test.ts apps/agent-web/tests/app-source-workspace.test.ts apps/agent-web/tests/FigmaEditorArea.test.ts apps/agent-web/tests/agent-skill-hub.test.ts packages/backend-api/tests/app-source.test.ts`：6 files / 41 tests，通过。
- `corepack pnpm test apps/agent-web/tests`：57 files / 934 passed / 1 skipped，通过；仅输出既有 jsdom Canvas `getContext` 提示。
- `corepack pnpm typecheck`：13 个 workspace package 范围通过。
- `corepack pnpm lint`：13 个 workspace package 范围通过。
- `corepack pnpm --filter @test-agent/agent-web build`：用户手册与 agent-web 生产构建通过；仅输出既有大 chunk 提示。
- `corepack pnpm playwright test apps/agent-web/tests/workbench.spec.ts --project=chromium --project=mobile --grep "application source|source progress|recent source|late source repository" --reporter=line`：12/12，通过。
- `git diff --check` 与 `git diff --cached --check`：通过。
- `tools/verify-ai-docs.sh`：通过。

### Round 1 影响与风险

- HTTP API、RunEvent、数据库、SQL、Flyway、安全路由和 OpenCode 快照均未变化；backend-api 仅补充独立源码进度 WebSocket 的意外 close 安全通知，主动 close 的既有语义保持不变。
- reconnect 同一时刻最多执行一个 snapshot/ticket/connection 链，退避上限为 4 秒；关闭、切换、终态和卸载清 timer，不增加轮询常驻负担。
- Playwright 覆盖真实浏览器 WebSocket mock 与 Chromium/mobile，但仍未替代多 Java、多服务器实际 Git 副本联调。
- 按 Fix Round 1 约束不更新本机 `.agents/session-log.{id}.md`；提交前再次回顾全部 session logs。

## Fix Round 2（2026-07-28）

### 修复结果

- lazy child 发现 `targetCommit` 与 root 不一致时，现在会失效整棵 tree authority、清空 snapshot/branch/loading，并要求重新加载真实 root。同 authority 下其它已在途 child 的迟到响应无法被安装为 root，新 root 成功前下一步与提交保持禁用。
- `diffFileCanWrite` 集中表达 Run Diff 保存能力：APP_SOURCE 只放行普通 Workspace 文件，`PUBLIC/WORKSPACE` Agent 都拒绝；MANAGED 继续按 `SUPER_ADMIN/APP_ADMIN` 作用域授权。`DiffViewer.writable`、父 handler 和 mutation 都会重新校验，伪造或迟到 `saveFile` 不会调用 Agent 写 API。
- managed 与 source 切换统一使用 `AppSourceIntentAuthority`。版本选择、新增版本、应用切换、服务器 Workspace 和历史 Session 异步链都传入真实 `isCurrent`，`applyManagedWorkspace` 不再提供永久 current 默认值。managed A 迟到不能覆盖 source B，source A 迟到也不能覆盖 managed B。
- repository summary 刷新改用独立 list authority，不再创建全局 source intent。旧 terminal operation 的 `open` 或 `finally` 跨越新 B 选择返回时，只能丢弃自身结果，不会使 B 静默失效。
- `connectAppSourceProgress` 新增可选 `AbortSignal`；ticket 请求与 CONNECTING socket 都可受控中止。关闭弹窗、切仓库、teardown 或卸载会立即关闭未 open socket，以 `AbortError` 静默收敛，不自动重连也不发后台 cancel。打开超时/失败会移除 abort listener。
- 每次新建进度 socket 都分配独立 connection epoch，event/failed/close 同时校验 observation 和 epoch。socket1 断线后的迟到 failed/terminal 无法改写 socket2 状态、claim terminal 或关闭新连接。
- reconnect attempt 不再因 socket `open` 清零，只有通过 authority/epoch 校验的 operation frame 才清零。连续无有效帧的 open→disconnect 按 250ms→500ms→1s 增长，上限仍为 4s。
- `AgentSkillHub` 的发布/引用/取消/冲突弹窗在 `canManage=false` 时关闭且 handler 保留门禁。`FigmaShell` 的 WORKSPACE 配置重载、`WorkbenchFooter` 的新增版本、个人 Git pull 确认/结果弹窗也会在进入 APP_SOURCE 时立即关闭/重置，child/parent confirm handler 均再次检查能力。

### Round 2 TDD 证据

1. **tree drift RED：** 两个同 authority child 并发，第一个 commit 漂移后第二个曾能伪装 root 并重新启用下一步。整树 invalidation 后需返回分支步骤重载 root 才 GREEN。
2. **Diff 双门禁 RED：** 新增纯 guard 矩阵单测先证明 source Agent 没有独立能力判断；抽出 helper 后覆盖 source 普通文件、PUBLIC/WORKSPACE Agent 和 managed 角色矩阵。Playwright 保留真实 DiffViewer emit→managed Agent 写入集成断言，并用源码断言确认组件及 handler/mutation 两个后备门禁。
3. **双向 selection RED：** managed A pending→source B 与 source A pending→managed B 首先分别证明旧链可夺回 workspace；统一 intent 且每个 await 后复核后 GREEN。
4. **terminal refresh RED：** A terminal `open` 被 gate，用户开始 B 后释放 A；旧 `finally` 会使 B 失效。独立 list authority 与原 source authority 校验后，最终文件树稳定指向 B。
5. **CONNECTING RED：** backend-api 延迟 open 的 socket 在关闭后曾等到 3 秒超时。AbortSignal 单测与工作台 Playwright 现证明关闭后立即记录 `ast_1` close，350ms 内不申请新 ticket、不 cancel。
6. **connection epoch/backoff RED：** socket1 迟到 failed/terminal 曾可影响 socket2，且每次 open 会把退避重置为 250ms。epoch 门禁与有效 operation frame 重置后，旧 frame 均忽略，ticket 时间差符合 250/500/1000ms。
7. **capability modal RED：** Hub、FigmaShell 和 WorkbenchFooter 的已打开弹窗在 capability 变 false 后仍存在；component watch + confirm guard 后聚焦 Vitest GREEN。Personal Pull 使用 Playwright 验证进入 source 即重置。

### Round 2 最终验证

- `corepack pnpm exec vitest run packages/backend-api/tests/app-source.test.ts apps/agent-web/tests/AppSourcePicker.test.ts apps/agent-web/tests/AppSourceDialog.test.ts apps/agent-web/tests/app-source-workspace.test.ts apps/agent-web/tests/FigmaEditorArea.test.ts apps/agent-web/tests/agent-skill-hub.test.ts apps/agent-web/tests/FigmaShell.test.ts apps/agent-web/tests/WorkbenchFooter.test.ts apps/agent-web/tests/figma-file-explorer.test.ts`：9 files / 122 tests，通过。
- `corepack pnpm exec vitest run apps/agent-web/tests`：57 files / 937 passed / 1 skipped，通过；仅有既有 jsdom Canvas `getContext` 提示。
- `corepack pnpm typecheck`：13 个 workspace package 范围通过。
- `corepack pnpm --filter @test-agent/agent-web build`：用户手册与 agent-web 生产构建通过；仅输出既有大 chunk 提示。
- `corepack pnpm playwright test workbench.spec.ts --project=chromium --project=mobile --grep "drifting lazy|stale socket epoch|exponential backoff|connecting progress socket|stale terminal apply|pending managed version|starting a managed version|DiffViewer save emit|entering source mode closes"`：18/18，通过。
- `git diff --check` 与 `git diff --cached --check`：通过。

### Round 2 文档、影响与风险

- 已同步 `frontend/README.md`、`frontend/apps/agent-web/README.md`、`frontend/apps/agent-web/src/PACKAGE.md`、`frontend/packages/backend-api/README.md`、`frontend/packages/diff-viewer/README.md` 和 `docs/architecture/module-map.md`。
- **API：** 未修改 HTTP wire；backend-api 只给既有 `connectAppSourceProgress` 增加向后兼容的可选 `signal`。**事件：** 未新增 RunEvent/SSE 或进度 envelope。**数据库：** 无 SQL、MyBatis、Flyway 或字段变化。
- **性能：** repository list 只保留常量级 authority，每个 observation 同时最多一条有效 socket；连续无帧断线避免 250ms 紧密重试。**安全：** 前端双门禁不替代后端文件授权，不保存物理路径、ticket 或凭据。**兼容性：** 新参数可选，managed 的普通文件、Agent 角色能力和 source 的普通写入语义保持不变。
- 未修改 OpenCode 快照、generated SDK、`.env.local` 或环境配置。真实多 Java/多服务器副本联调仍属 Task 2/3 后端验收，本轮浏览器使用平台 HTTP/文件 WebSocket mock。
- 按 Fix Round 2 约束不更新本机 `.agents/session-log.{id}.md`；提交前回顾全部 `.agents/session-log*.md` 近期条目。

## Fix Round 3（2026-07-28）

### 修复结果

- 当前进度 socket 收到无 operation 的 failure/断线回调后，会在关闭连接、清理 current connection 和安排重连之前立即递增并激活一个失效 epoch。原 socket 随后即使在 250ms 退避期或 replacement operation snapshot 尚未返回时继续投递 `step`、`failed`、`completed`，也无法更新 operation、claim terminal、重置退避或触发 generation open。
- source Run Diff 的保存实现继续保留 `DiffViewer.writable/readOnly`、父 `handleSaveDiffFile` 与 mutation 写入前复核三层门禁，没有新增 test-only 暴露。新增浏览器行为测试真实打开 APP_SOURCE workspace，以生产 `loadDiffSource("run")` 和 `handleRunEvent(diff.proposed)` 挂载 `DiffViewer`，再从组件发出真实 `saveFile`：PUBLIC/WORKSPACE Agent 路径保持零 `agent-config.write`，普通源码路径精确产生一次目标 source workspace 的 `workspace.write`。

### Round 3 TDD 证据

1. **失败 epoch 窗口 RED→GREEN：** socket1 在 20ms 断线，退避期投递旧 step，并在第二次 operation snapshot 被 gate 阻塞时投递旧 failed/completed。修复前终态实际触发一次 `open:app_gcms:repo-code:2`（期望 0）；失败分支先作废 epoch 后 focused Chromium GREEN，释放 snapshot gate 后仍按 fresh ticket 建立 socket2 并显示“新连接已恢复”。
2. **source Diff 行为级 mutation RED→GREEN：** 正常生产代码下真实 source Run Diff emit 已 GREEN。为验证新增断言确实能捕获回归，临时同时移除父 handler 与 mutation 两层门禁；测试从期望 `agent-config.write=0` 变为实际 2 次写入（WORKSPACE、PUBLIC 各一次）并 RED。随后立即恢复原三层门禁，focused Chromium 再次 GREEN；临时 mutation 未保留在工作树或提交中。

### Round 3 最终验证

- `corepack pnpm exec vitest run packages/backend-api/tests/app-source.test.ts apps/agent-web/tests/AppSourcePicker.test.ts apps/agent-web/tests/AppSourceDialog.test.ts apps/agent-web/tests/app-source-workspace.test.ts apps/agent-web/tests/FigmaEditorArea.test.ts apps/agent-web/tests/agent-skill-hub.test.ts apps/agent-web/tests/FigmaShell.test.ts apps/agent-web/tests/WorkbenchFooter.test.ts apps/agent-web/tests/figma-file-explorer.test.ts`：9 files / 122 tests，通过。
- `corepack pnpm playwright test apps/agent-web/tests/workbench.spec.ts --project=chromium --project=mobile --grep "drifting lazy|stale socket epoch|exponential backoff|connecting progress socket|stale terminal apply|pending managed version|starting a managed version|DiffViewer save emit|entering source mode closes|failed socket epoch|source Run Diff save dispatch" --reporter=line`：22/22，通过。
- `corepack pnpm exec vitest run apps/agent-web/tests`：57 files / 937 passed / 1 skipped，通过；仅输出既有 jsdom Canvas `getContext` 提示。
- `corepack pnpm typecheck`：13 个 workspace package 范围通过。
- `corepack pnpm --filter @test-agent/agent-web build`：用户手册与 agent-web 生产构建通过；仅输出既有大 chunk 提示。
- `tools/verify-ai-docs.sh`、`git diff --check` 与 `git diff --cached --check`：通过。

### Round 3 文档、影响与风险

- 已同步 `frontend/README.md`、`frontend/apps/agent-web/README.md`、`frontend/apps/agent-web/src/PACKAGE.md` 和 `docs/architecture/module-map.md` 的 failure epoch 窗口说明，并在本报告保留行为证据。
- **API/事件：** 未修改 HTTP wire、RunEvent/SSE 或进度 WebSocket envelope。**数据库：** 无 SQL、MyBatis、Flyway 或字段变化。**性能：** 只在当前失败回调执行一次常量级 epoch 递增，不增加连接、轮询或缓存。**安全：** source Agent 保存继续由三层前端门禁和后端文件授权共同保护；测试明确证明普通源码仍路由到目标 source workspace。**兼容性：** Round 2 的 tree fencing、双向 selection authority、terminal refresh、CONNECTING abort、指数退避和 modal 收敛均通过桌面/移动回归。
- 未修改 OpenCode 快照、generated SDK、`.env.local` 或环境配置。真实多 Java/多服务器副本联调仍属 Task 2/3 后端验收；本轮浏览器继续使用平台 HTTP/文件 WebSocket mock。
- 按 Fix Round 3 约束不更新本机 `.agents/session-log.{id}.md`；提交前已回顾全部 `.agents/session-log*.md` 近期条目，未发现覆盖、丢弃或误合并其他开发者成果的风险。
