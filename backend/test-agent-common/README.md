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
- Git 远端只读命令、clone/worktree/diff/push/pull/fetch/reset/status 命令执行、`git archive --remote` tar 目录解析、SSH key AES-GCM 加解密工具和 RSA-OAEP 公私钥包装。
- 不含业务流程和基础设施访问代码。

## 已有契约

- `ApiResponse<T>`：统一成功响应，字段为 `success`、`data`、`traceId`。
- `ApiErrorResponse`：统一错误响应，字段为 `success=false`、`code`、`message`、`traceId`、`details`。
- `ErrorCode`：平台稳定错误码及 HTTP 状态映射数字。
- `PlatformException`：业务层抛出的平台基础异常。
- `PageRequest`、`PageResponse<T>`：分页请求和响应模型。
- `RuntimeIdGenerator`：生成 Workspace、Session、Run、Message、PTY ticket、代码库、应用工作空间、应用版本工作区、应用版本服务器副本、服务器广播事件、个人工作区、同步记录、SSH key 和 scheduler 运行/计划的稳定前缀 ID。
- `GitRemoteService`、`ProcessGitCommandExecutor`：封装 `git ls-remote --heads`、`git archive --remote`、超时、输出上限、非交互环境和临时 SSH key 文件清理。
- `GitWorkspaceService`：封装 clone、worktree add、同名分支已存在时复用分支挂载 worktree、当前分支/origin/head/status 查询、porcelain 状态解析、diff 文件聚合、提交指定文件、push/force-with-lease、pull --ff-only、fetch、reset --hard 到指定 commit、合并冲突文件列表（`git diff --name-only --diff-filter=U`）和 `merge --abort` 原子命令，供业务层在发布冲突后恢复受控仓库状态。
- `SshKeyCryptoService`：封装个人 SSH 私钥 AES-GCM 加解密和 SHA-256 指纹生成。
- `RsaKeyService`：封装 SSH key 前端混合加密所需的 RSA 公钥导出和私钥解密；解密优先使用浏览器 Web Crypto 对齐的 RSA-OAEP/SHA-256 + MGF1-SHA-256 参数，并兼容历史 Java 默认 OAEP 参数密文。

## 测试覆盖

- `ApiResponseTest`、`ApiErrorResponseTest`、`PlatformExceptionTest` 覆盖统一响应和平台异常。
- `PageRequestTest`、`PageResponseTest` 覆盖分页边界、offset、总页数和列表防御性复制。
- `ErrorCodeTest`、`RuntimeIdGeneratorTest` 覆盖稳定 HTTP 状态、默认中文说明和运行时 ID 前缀格式。
- `GitRemoteServiceTest` 覆盖分支解析、archive tar 目录解析和 Git 超时错误映射。
- `GitWorkspaceServiceTest` 覆盖 clone 分支、worktree 创建、同名分支复用、分支/origin/head/status 查询、porcelain 路径解码、staged/unstaged diff 聚合、提交、push、pull、fetch/reset、合并冲突文件列表解析、merge abort 和临时 SSH key 清理。
- `SshKeyCryptoServiceTest` 覆盖 SSH key 加解密、指纹和密钥配置错误。
- `RsaKeyServiceTest` 覆盖浏览器 Web Crypto RSA-OAEP/SHA-256 密文的后端解密兼容性。

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
