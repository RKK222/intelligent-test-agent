# OpenCode 源码边界

## 只读快照

本项目严格禁止直接修改 OpenCode 源码。`opencode-source/opencode-1.18.4/` 是按上游 tag 和 commit 固定的只读源码快照，仅用于源码审计、行为对照和 OpenAPI 兼容性分析。

禁止在该目录中提交以下变更：

- OpenCode 源码、测试、配置、构建脚本或资源的补丁、功能修改和缺陷修复。
- 为平台适配临时加入的日志、接口、兼容分支或构建产物。
- 直接编辑快照文件后继续生成 worker、OpenAPI 或 Java SDK。

如果平台行为需要调整，必须在本项目的后端适配层、前端复刻工程、worker 启动器或受控配置层实现，不得通过修改 OpenCode 源码绕过项目边界。需要升级 OpenCode 时，只能按 `docs/deployment/opencode-upgrade-1.18.4.md` 的流程重新获取干净上游快照，并同步评估 API、SDK 和运行时兼容性；不得在快照上做本地补丁。

## 生成 SDK 边界

`backend/test-agent-opencode-sdk-generated/` 同样禁止手工修改 generated Java 源码。SDK 变更必须通过 `tools/generate-opencode-java-sdk.sh` 重新生成，再按模块文档同步。
