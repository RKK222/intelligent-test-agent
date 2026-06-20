# 包说明：com.example.testagent.workspace

## 职责

Workspace 和文件管理业务包，负责工作区注册、查询、目录选择、文件路径归一化、越权路径拒绝、UTF-8 文件读写和文件状态查询。

## 不负责

- 不定义 HTTP Controller 或 API DTO。
- 不直接调用 opencode server 或 generated SDK。
- 不实现数据库 Repository。

## 主要程序清单

- `WorkspaceApplicationService`：工作区注册、分页查询、详情查询和文件服务编排。
- `WorkspaceFileService`：文件系统访问、root 归一化、大小限制和越权路径拦截。
- `WorkspaceDirectoryService`：受控列出允许根目录内的一层子目录，供前端选择 Workspace 根目录。
- `FileTreeEntryResponse`、`FileContentResponse`、`FileStatusResponse`：文件业务返回模型，由 API 层包装。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Spring Context。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-persistence` 实现细节。
- `test-agent-opencode-sdk-generated`。

## 修改时必须同步更新

- `backend/test-agent-workspace-management/README.md`。
- `docs/api/http-api.md`，如果文件或 workspace API 行为变化。
- `docs/standards/backend.md`，如果测试策略变化。
