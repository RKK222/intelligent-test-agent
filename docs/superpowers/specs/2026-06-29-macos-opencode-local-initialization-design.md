# macOS opencode 本地初始化修复设计

## 背景与目标

远程分支合并后，macOS 已进入后端平台枚举和数据库参数表，但本地 `.env.test` 启动仍存在三类不一致：

1. macOS 通用参数指向 `/tmp/test-agent` 或 `$HOME/test-agent`，不符合本项目本地目录约定。
2. 公共配置 Git 地址仍为 `UNCONFIGURED`，公共配置初始化按钮被后端禁用；用户 opencode 进程又要求公共配置目录已初始化且非空，形成不可用链路。
3. 通用参数页面没有 `macos` 筛选项，且平台选择需要再次点击“筛选”才发起查询。

目标是在不修改 `.env.test`、不绕过公共配置 Git 管理和不放宽 manager 安全检查的前提下，让 macOS 本地启动统一使用项目根目录 `temp/`，公共配置可初始化，用户 opencode 进程可启动，平台筛选选择后立即刷新。

## 方案

### 路径与配置

新增 Flyway 兼容性数据迁移，不修改已经发布的 macOS 迁移。macOS 参数使用 `$TEST_AGENT_ROOT` 环境变量保存可移植的绝对路径来源：

| 参数 | 新值 |
|---|---|
| `OPENCODE_SESSION_DIR` | `$TEST_AGENT_ROOT/temp/opencode-session` |
| `OPENCODE_APP_WORKSPACE_ROOT` | `$TEST_AGENT_ROOT/temp/workspace/appworkspace` |
| `OPENCODE_PERSONAL_WORKTREE_ROOT` | `$TEST_AGENT_ROOT/temp/workspace/personalworktree` |
| `OPENCODE_PUBLIC_CONFIG_GIT_ROOT` | `$TEST_AGENT_ROOT/temp/opencode-config` |
| `OPENCODE_PUBLIC_CONFIG_DIR` | `$TEST_AGENT_ROOT/temp/opencode-config/opencode` |
| `OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT` | `$TEST_AGENT_ROOT/temp/opencode-configdev` |

公共配置 Git 地址仅在当前值仍为 `UNCONFIGURED` 时更新为 `git@gitee.com:huangzhenren/opencodeconfig.git`，避免覆盖已有环境的显式配置。

根目录启动脚本为 `TEST_AGENT_ROOT` 提供默认值 `${ROOT_DIR}`，同时允许调用方预先设置同名环境变量覆盖。Java 通用参数解析器继续负责把 `$TEST_AGENT_ROOT` 展开为绝对路径，manager 只接收展开结果。

### 前端交互

通用参数页面直接把平台下拉绑定到生效筛选值：

- 新增 `macos` 选项。
- 选择任意平台后把页码重置为 1，Vue Query 的 query key 变化后自动请求。
- 保留“重置”和“刷新”，移除需要二次确认的“筛选”按钮及草稿状态。

### 目录迁移与初始化

停止现有本地服务后，只删除本项目旧目录 `/tmp/test-agent` 和存在时的 `$HOME/tmp/test-agent`，不清理其他 `/tmp` 或用户目录。新目录由公共配置初始化和 manager 启动流程按需创建。

重启后通过现有运维页面初始化公共配置仓库。仓库必须在 `opencode/` 下包含非空配置，否则沿用现有 `CONFLICT` 错误，不新增空目录占位或绕过检查。随后重新初始化当前用户 opencode 进程。

## 测试与验收

1. 后端测试先证明合并后的旧用例错误地拒绝 `macos`，再改为接受 `macos` 且继续拒绝未知平台。
2. 前端组件测试先证明平台选项缺少 `macos`、选择后不自动查询，再做最小实现。
3. Flyway/持久化测试验证 macOS 参数和公共 Git 地址的新值。
4. 执行后端相关模块测试、前端定向测试、typecheck、构建和开发脚本校验。
5. 使用 `.env.test` 启动三服务，验证 health/readiness、前端、CORS、manager 日志。
6. 在真实页面验证 `macos` 选择后自动刷新、公共配置仓库初始化成功、用户 opencode 进程变为可用。

## 兼容性与安全

- 不新增或变更 HTTP API、SSE 事件、DTO 和数据库结构。
- 新迁移只修正系统参数数据，不写入测试样例或个人业务数据。
- 不修改 `.env.test`，不输出 SSH key、token 或数据库密码。
- 不放宽 manager 对公共配置目录存在、可读且非空的校验。
- 已有非 `UNCONFIGURED` 公共 Git 地址不被覆盖。
