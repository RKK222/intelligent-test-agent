# test-agent-integration

## 工程定位

非 opencode 外部系统联动业务模块。

## 当前状态

当前仅提供 Maven 模块和包边界骨架，不包含虚假业务实现。

## 允许依赖

- `test-agent-common`。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-opencode-sdk-generated`。

## 后续 AI 编码指引

新增外部系统联动时先判断是否属于 opencode；opencode 相关放 `test-agent-opencode-runtime`，其他系统联动放本模块。
