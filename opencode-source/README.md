# OpenCode 源码快照

当前审计与前端行为参考固定为 OpenCode `1.18.4`：

- 上游仓库：`https://github.com/anomalyco/opencode`
- tag：`v1.18.4`
- commit：`49c69c5ed3ccf706b61b3febb43c8aaff7f8325e`
- 本地目录：`opencode-source/opencode-1.18.4/`

该目录只用于源码审计、行为对照和 OpenAPI 兼容性分析，不参与企业 worker 的可执行程序构建。**本项目严格禁止修改该目录中的 OpenCode 源码、测试、配置、构建脚本、资源或临时补丁。** worker 固定下载上游官方 `opencode-linux-x64-baseline.tar.gz`，同时校验发布归档和解压后二进制的 SHA-256。

升级快照时必须按 tag 对应 commit 获取干净源码，排除 `.git`、`node_modules` 和构建产物，并同步更新 `docs/deployment/opencode-upgrade-1.18.4.md`、OpenAPI 快照和生成 SDK；升级只能整体替换为干净上游快照，不得在快照上保留本地修改。
