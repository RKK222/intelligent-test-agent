# test-agent-workspace-management

## 工程定位

Workspace、文件管理、后续 git/diff、agent 和 skill 管理业务模块。

## 主要职责

- 工作区注册、查询和分页。
- 工作区内文件单层列表、UTF-8 内容读写、文件状态和路径越权拦截。
- 后续与文件相关的 git 操作、差异比对、agent/skill 文件管理优先进入本模块。

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
