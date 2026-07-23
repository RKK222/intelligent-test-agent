# OpenCode 1.18.4 升级与兼容性评估

## 固定版本与来源

| 项目 | 升级前 | 当前固定值 |
|---|---:|---:|
| OpenCode | 1.17.8 | 1.18.4 |
| OpenCode tag commit | `11e47f91496005aab4d7c5a2d0a7da5d2651b4ac` | `49c69c5ed3ccf706b61b3febb43c8aaff7f8325e` |
| Java SDK 生成器 | 7.23.0 | 7.24.0 |
| `@opencode-ai/plugin` / `@opencode-ai/sdk` | 1.17.8 | 1.18.4 |
| `effect` | 4.0.0-beta.74 | 4.0.0-beta.83 |

企业 worker 使用上游 release 的 `opencode-linux-x64-baseline.tar.gz`，不从源码构建。1.18.4 归档大小为 59,265,643 bytes，SHA-256 为 `4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc`，解压后二进制 SHA-256 为 `6ce6570e7db9a40e7bd3304ebdfff607920bde8cafd2eb5587bd7a26f89ba0b5`。源码快照只用于审计和行为参考。

## API 差异

从 1.17.8 和 1.18.4 官方 `/doc` 比较得到：paths 从 150 增至 162，schemas 从 339 增至 472，HTTP operations 从 175 增至 188；没有删除 operation。新增 operation 为：

- `GET /experimental/capabilities`
- `GET /api/session/active`
- `POST /api/session/{sessionID}/agent`
- `POST /api/session/{sessionID}/model`
- `POST /api/session/{sessionID}/revert/stage`
- `POST /api/session/{sessionID}/revert/clear`
- `POST /api/session/{sessionID}/revert/commit`
- `GET /api/session/{sessionID}/history`
- `GET /api/session/{sessionID}/event`
- `POST /api/session/{sessionID}/interrupt`
- `GET /api/session/{sessionID}/message/{messageID}`
- `POST /api/session/{sessionID}/permission`
- `GET /api/session/{sessionID}/permission/{requestID}`

平台现有调用使用的 method、path、参数和响应契约未发生破坏性变化。1.18.4 规范已使用 OpenAPI Generator 7.24.0 重新生成，生成工程和后端 `test-agent-opencode-sdk-generated` 均已编译；生成源码禁止手改。

模型层变化中，`FileSystemEntry.mime` 已移除；前端现有转换优先读取 `type` 并仅把 `mime` 作为旧版回退，因此无需业务改造。事件联合类型删除部分旧 `session.next.*` 定义并增加 revert 事件；平台 mapper 对未知 raw event 保持 `opencode.event.unknown` 透传，旧事件测试继续保留。

## 行为影响与适配

- 1.18.2 起增加 `subagent_depth`，上游默认值为 1。平台启动器根据随包 `VERSION` 判断能力：1.18.2 及以上以 `OPENCODE_CONFIG_CONTENT` 强制设为 2，支持且仅要求 root → child → grandchild；1.17.8 回滚运行时会移除该版本不识别的字段。已有配置内容会合并，非法内容直接失败，避免静默丢配置。
- `RunSessionScopeRouter` 现在按 task part 所属 session 建立 parent。root task 仍创建 child，child task 创建 grandchild；task part 保持在发起它的 scope，不再把 grandchild 错挂 root。
- 官方单文件程序在完全断网时仍会检查配置目录依赖元数据。启动器会为 XDG 全局配置、`OPENCODE_CONFIG_DIR` 和当前工作区 `.opencode` 非覆盖式链接随包交付的 `package.json`、lockfile 和 Tool 依赖；内网运行不执行 npm 下载。
- `includeUsage=false` 仍须保留。1.18.4 对 openai-compatible provider 仍会在未显式关闭时设置 `includeUsage=true`，企业内部不支持该字段的接口会受影响。
- 本次不修改平台 HTTP API、RunEvent SSE wire shape、数据库结构、Flyway、鉴权和密钥配置。

## 交付、升级与回滚

完整升级包包含 worker image tar 和 `test-agent-programs.tar.gz`。镜像内置路径与外挂 programs 路径都使用 `/usr/local/lib/opencode` 布局，入口为 `bin/opencode`，官方程序为 `bin/opencode-official`，`RELEASE` 文件记录版本、asset、归档/二进制 SHA 和 release commit。

升级时必须同时替换 worker image 和 programs，再重启用户 OpenCode 进程；不能只覆盖单个二进制或 `node_modules`。1.17.8 回滚包使用同名官方 baseline asset（54,769,220 bytes）、GitHub release 记录的归档 SHA-256 `9b34bf34bdc66ea34ddd5858a131febf28b6247693acbfb5fb5c9ad94d90388b`，构建时还执行归档内程序的 `--version` 校验，并配套 1.17.8 plugin/SDK lockfile。回滚同样同时替换 image 和 programs，不清理用户 session、manager state 或平台数据库。

## 验证

```bash
node --test tools/test-opencode-official-launcher.mjs
tools/generate-opencode-java-sdk.sh
mvn -f backend/pom.xml -pl test-agent-opencode-client,test-agent-opencode-runtime -am test
corepack pnpm --dir frontend vitest run packages/agent-chat/tests/opencode-like-state.test.ts
tools/verify-opencode-node-worker-image.sh test-agent-opencode-worker:internal
EXPECTED_OPENCODE_VERSION=1.17.8 \
EXPECTED_OPENCODE_ASSET_SHA256=9b34bf34bdc66ea34ddd5858a131febf28b6247693acbfb5fb5c9ad94d90388b \
EXPECTED_OPENCODE_SUBAGENT_DEPTH=unsupported \
tools/verify-opencode-node-worker-image.sh test-agent-opencode-worker:1.17.8
```

worker 验证覆盖 glibc 2.31、官方版本/asset 元数据、断网启动、健康检查、按统一认证号隔离的 PID 1 HOME/XDG/TMP/config 环境、`/path` 解析和各 `opencode` 普通子目录、两处自定义 Tool、1.18.4 的 `subagent_depth=2`、1.17.8 的旧配置兼容、依赖链接和五秒内优雅停止。
