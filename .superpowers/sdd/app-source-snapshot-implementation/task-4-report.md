# Task 4 实施报告：工作台源码入口、四步弹窗与源码模式

## 结论

Task 4 已完成。`agent-web` 已提供应用源码入口、四态紧凑列表、四步物化弹窗、持久化操作进度恢复和显式 `MANAGED/APP_SOURCE` 工作区模式。源码工作区继续复用平台 Workspace 与文件 WebSocket，保留普通文件写入、Session/Run、OpenCode 和会话终端；Git 发布、应用 Agent 发布与应用版本选择在组件和 handler 两层关闭。

本任务未修改 OpenCode 快照、generated SDK、后端 API/事件实现、数据库结构、SQL、Flyway、`.env.local` 或其它环境配置。

## 页面与交互

- `WorkbenchFooter` 在工作空间切换图标右侧新增源码图标，且保持源码、引用配置、服务器工作空间的固定顺序；源码模式改为“返回应用工作区”。
- `AppSourcePicker` 按服务端稳定四态展示 active、expired、个人占用和未下载仓库。打开动作服从 `openable`，不可用原因直接展示，个人占用显示 owner 姓名与 UCID，底部固定“下载版本库”。
- `AppSourceDialog` 使用四步宽弹窗：仓库状态；分支与固定提交精确树；TEAM/PERSONAL、1–72 小时 retention 和覆盖确认；全局及逐服务器安全步骤时间线。
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
- terminal operation 使用 operationId 集合一次性 claim；重复终帧不会重复执行 `open` 或 fallback。`SUCCEEDED/PARTIAL_FAILED` 仍由服务端 `open(targetGeneration)` 决定本机能否切入；失败则清 recent 并回退托管工作区。

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
