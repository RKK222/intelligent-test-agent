# test-agent-workspace-management

## 工程定位

Workspace、文件管理、后续 git/diff、agent 和 skill 管理业务模块。

## 主要职责

- 工作区注册、查询和分页。
- 工作区内文件单层列表、UTF-8 内容读写、文件状态和路径越权拦截。
- 受控浏览 `test-agent.workspace-picker.allowed-roots` 内的本机目录，供前端选择新的 Workspace 根目录。
- 后续与文件相关的 git 操作、差异比对、agent/skill 文件管理优先进入本模块。

## 测试覆盖

- `WorkspaceApplicationServiceTest` 覆盖工作区创建、分页/详情查询、未找到错误和文件服务编排。
- `WorkspaceFileServiceTest` 覆盖 UTF-8 读写、路径穿越拒绝、目录列表排序与上限、文件大小限制和 null 内容写入。
- `WorkspaceDirectoryServiceTest` 覆盖默认根目录、只返回子目录、排序、父目录边界、越权和缺失目录错误码。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Spring Context。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-opencode-sdk-generated`。
- `test-agent-persistence` 实现类。

## 后续 AI 编码指引

新增与 workspace、文件、git、agent 或 skill 管理相关的业务逻辑时优先改这里；HTTP 入口只放在 `test-agent-api`。
