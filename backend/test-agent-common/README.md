# test-agent-common

## 工程定位

公共基础模块，放置所有后端模块都可以复用的轻量类型和工具。

## 技术栈

- Java 21
- Jackson annotations
- Jakarta Validation API
- Maven library jar

## 主要职责

- 通用异常、错误码、响应模型。
- TraceId、Idempotency-Key、分页、时间、校验相关基础类型。
- Git 远端只读命令、clone/worktree/diff/push 命令执行、`git archive --remote` tar 目录解析和 SSH key AES-GCM 加解密工具。
- 不含业务流程和基础设施访问代码。

## 已有契约

- `ApiResponse<T>`：统一成功响应，字段为 `success`、`data`、`traceId`。
- `ApiErrorResponse`：统一错误响应，字段为 `success=false`、`code`、`message`、`traceId`、`details`。
- `ErrorCode`：平台稳定错误码及 HTTP 状态映射数字。
- `PlatformException`：业务层抛出的平台基础异常。
- `PageRequest`、`PageResponse<T>`：分页请求和响应模型。
- `RuntimeIdGenerator`：生成 Workspace、Session、Run、Message、PTY ticket、代码库、应用工作空间、应用版本工作区、个人工作区、同步记录和 SSH key 的稳定前缀 ID。
- `GitRemoteService`、`ProcessGitCommandExecutor`：封装 `git ls-remote --heads`、`git archive --remote`、超时、输出上限、非交互环境和临时 SSH key 文件清理。
- `GitWorkspaceService`：封装 clone、worktree add、当前分支/origin/head 查询、提交指定文件和 push/force-with-lease。
- `SshKeyCryptoService`：封装个人 SSH 私钥 AES-GCM 加解密和 SHA-256 指纹生成。

## 测试覆盖

- `ApiResponseTest`、`ApiErrorResponseTest`、`PlatformExceptionTest` 覆盖统一响应和平台异常。
- `PageRequestTest`、`PageResponseTest` 覆盖分页边界、offset、总页数和列表防御性复制。
- `ErrorCodeTest`、`RuntimeIdGeneratorTest` 覆盖稳定 HTTP 状态、默认中文说明和运行时 ID 前缀格式。
- `GitRemoteServiceTest` 覆盖分支解析、archive tar 目录解析和 Git 超时错误映射。
- `GitWorkspaceServiceTest` 覆盖 clone 分支、worktree 创建、分支/origin/head 查询、提交、push 和临时 SSH key 清理。
- `SshKeyCryptoServiceTest` 覆盖 SSH key 加解密、指纹和密钥配置错误。

## 允许依赖

- JDK 标准库。
- Jackson annotations。
- Jakarta Validation API。

## 禁止依赖

- `test-agent-domain` 及任何业务模块。
- `test-agent-opencode-sdk-generated`。
- Spring Web、Persistence、App 启动入口。

## 后续 AI 编码指引

如果要新增跨模块共享的简单 DTO、异常、工具类，优先放这里；如果类名包含 Workspace、Session、Run 等业务语义，应先考虑 `test-agent-domain`。
