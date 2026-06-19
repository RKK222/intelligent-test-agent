# test-agent-system-management

## 工程定位

系统内部管理业务模块，后续承载用户、角色、权限等平台管理能力。

## 当前状态

当前仅提供 Maven 模块和包边界骨架，不包含虚假业务实现。

## 允许依赖

- `test-agent-common`。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-opencode-sdk-generated`。

## 后续 AI 编码指引

新增用户、角色、权限等平台内部管理业务时优先改这里；API 入口放在 `test-agent-api`。
