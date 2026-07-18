# Session Log - rkk222

> 按提交者 `git config user.name` 分文件维护，新增条目置于 `## Entries` 顶部。
> 提交前需回顾所有 `.agents/session-log*.md`（含已冻结旧档 `.agents/session-log.md`）的近期条目。

## Entries

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
