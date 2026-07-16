# 包说明：com.icbc.testagent.common

## 职责

公共基础包，放置跨后端模块复用的轻量类型、错误基础、响应模型、TraceId、分页、时间和校验工具。

## 不负责

- 不承载 Workspace、Session、Run 等业务规则。
- 不访问数据库、Redis、HTTP 或 opencode server。
- 不依赖 Spring Web 或应用入口。

## 主要程序清单

- `package-info.java`：说明 common 包是后端共享基础类型边界。
- `api.ApiResponse`：统一成功响应模型。
- `api.ApiErrorResponse`：统一错误响应模型。
- `error.ErrorCode`：稳定错误码和 HTTP 状态数字。
- `error.PlatformException`：平台基础异常。
- `pagination.PageRequest`：分页请求模型。
- `pagination.PageResponse`：分页响应模型。
- `id.RuntimeIdGenerator`：生成运行态业务 ID，包括 scheduler 运行记录和计划 ID。
- `git.GitCommitIdentity`：生成并校验单次 Git 提交身份；平台用户没有邮箱字段时使用 `testagent.local` 保留域名补足 Git 必需的 email。
- `git.GitWorkspaceService`：封装本地 Git 原子命令，包括 clone/worktree/status/diff/按操作人身份 commit/push/pull/fetch/reset、冲突文件列表和 merge abort；提交身份只对当前命令生效，不承载业务发布流程。
- 后续可新增 Idempotency-Key、时间和校验工具。

## 允许依赖

- JDK 标准库。
- Jackson annotations。
- Jakarta Validation API。

## 禁止依赖

- `test-agent-domain` 及任何业务模块。
- `test-agent-opencode-sdk-generated`。
- Spring Web、Persistence、App 启动入口。

## 上游调用方

- 所有后端模块。

## 下游依赖

- 仅 JDK 和轻量基础库。

## 测试位置

- common 模块单元测试。
- 通用校验、时间、错误码和分页逻辑必须有边界测试。

## 修改时必须同步更新

- `backend/test-agent-common/README.md`。
- 本文件。
- 受影响模块的 README 或 PACKAGE.md。
- `docs/standards/backend.md`，如果公共类型边界变化。
