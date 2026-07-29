# LobeHub 企业离线部署

本文描述 LobeHub `v2.2.11-platform.1` 的离线制品、安装、启动、备份和回滚。架构与 fork 必须实现的行为见
`docs/architecture/lobehub-integration.md`。本仓库不包含独立 fork 源码，只有通过制品门禁的外部构建结果
才能进入全量包或 LobeHub-only 包。

## 版本矩阵

| 组件 | 固定基线 | 说明 |
|---|---|---|
| LobeHub upstream | `v2.2.11` / `5b4cef6` | 独立内部 fork，不修改本仓库 OpenCode 快照 |
| LobeHub internal | `v2.2.11-platform.1` | 平台契约版本 `1` |
| LobeHub database | ParadeDB / PostgreSQL 17 | 独立库、账号、密码、数据卷和 migration |
| Redis | 现有企业实例 DB 0 | 独立 ACL 用户；LobeHub key/channel 仅 `lobehub:app:*` |
| RustFS | 外部制品 `release.env` 中的摘要镜像 | 私有 bucket，容器以 UID 10001 写数据 |
| 客户端 | Windows x64、Linux x86_64 | Windows 必须 Authenticode Valid；Linux 执行默认关闭 |

版本事实源为 `deploy/internal/lobehub/version.env`。升级任一项时必须同时升级内部版本、制品契约和本文，
不能只替换镜像 tag。

## 拓扑与目录

LobeHub app、ParadeDB 和 RustFS 独立容器运行，平台 Java、共享 Redis 和 HTTP 反向代理是外部依赖；不使用
Docker Compose。默认目录：

```text
/data/testagent/
  config/lobehub.env                 # 0600，实际 secret
  deploy/internal/lobehub-docker.sh
  lobehub/paradedb/                  # 独立数据库卷
  lobehub/rustfs/data/               # UID 10001
  lobehub/rustfs/logs/
  lobehub/bin/mc-linux-amd64
  lobehub/clients/
  lobehub/release/                   # source/SBOM/license/provenance
  tmp/model-gateway/                 # 平台 multipart 临时目录
```

容器只把 app `3210` 和 RustFS `9000/9001` 绑定到 `127.0.0.1`，由企业反向代理和隔离网络提供访问。
ParadeDB 只在专用 Docker network 内可达。平台 Flyway 永远不得连接 LobeHub 数据库。

## 外部 fork 制品契约

外网构建机输出目录必须至少包含：

```text
release.env
SHA256SUMS
approved-resources.json
LICENSES.txt
windows-authenticode-verification.txt
images/lobehub-image.tar
images/paradedb-image.tar
images/rustfs-image.tar
clients/lobehub-windows-x64.exe
clients/lobehub-linux-x86_64.tar.gz
bin/mc-linux-amd64
sbom/lobehub.spdx.json
source/lobehub-v2.2.11-platform.1.tar.gz
```

`SHA256SUMS` 必须恰好覆盖除自身外的全部普通文件，不允许绝对路径、`..`、空白文件名、重复项或符号链接。
`release.env` 至少包含：

```dotenv
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.1
LOBEHUB_UPSTREAM_VERSION=v2.2.11
LOBEHUB_UPSTREAM_COMMIT=5b4cef6
LOBEHUB_PLATFORM_CONTRACT_VERSION=1
LOBEHUB_PARADEDB_POSTGRES_MAJOR=17
LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=true
LOBEHUB_LINUX_EXECUTION_DEFAULT=false
LOBEHUB_APP_IMAGE=registry.internal/...@sha256:<64 lowercase hex>
LOBEHUB_PARADEDB_IMAGE=registry.internal/...@sha256:<64 lowercase hex>
LOBEHUB_RUSTFS_IMAGE=registry.internal/...@sha256:<64 lowercase hex>
```

`windows-authenticode-verification.txt` 使用 `KEY=value`，至少包含 `AUTHENTICODE_STATUS=Valid`、
`AUTHENTICODE_SUBJECT`、`AUTHENTICODE_THUMBPRINT` 和与实际 EXE 一致的 `AUTHENTICODE_FILE_SHA256`。打包脚本
会失败关闭校验上述契约；现场导入后还会用 `docker image inspect` 确认 tar 真正提供 `release.env` 中的摘要引用。

## Mac 打包与转运

外网 Mac 汇集经过审批的 fork、Windows 和 Linux 构建产物后执行：

```bash
TEST_AGENT_LOBEHUB_ARTIFACT_DIR=/absolute/path/to/lobehub-release-artifacts \
  deploy/internal/package-release.sh --lobehub-only
```

生成 `deploy/internal/dist/test-agent-lobehub-offline.zip` 及 `.sha256`。全量平台包使用：

```bash
TEST_AGENT_LOBEHUB_ARTIFACT_DIR=/absolute/path/to/lobehub-release-artifacts \
  deploy/internal/package-release.sh --with-lobehub
```

将产物汇集到 `~/Desktop/mimoagent/0709`，按既有受控介质流程转运，现场上传至 `/data/0709`。不得在现场
联网补拉镜像、npm 包、Marketplace、Connector 或客户端。

## 安装与配置

在现场先校验外层 zip SHA-256，解压后以 root 执行：

```bash
deploy/internal/install-lobehub-offline.sh
```

安装器复核内部清单、导入三份镜像、验证摘要引用、安装客户端/来源/SBOM、创建 systemd unit，并在不存在时
创建 mode `0600` 的 `/data/testagent/config/lobehub.env` 模板。它不会覆盖既有实际配置。

根据 `deploy/internal/lobehub.env.example` 配置独立数据库密码、共享 Redis ACL 账号、RustFS 密钥、固定平台
地址、HMAC、委托加密密钥、Key Vault 和 Session 密钥。平台 `backend.env` 中
`TEST_AGENT_LOBEHUB_HMAC_SECRET` 必须与 fork 一致。公共非敏感参数通过平台通用参数管理维护：

- `LOBEHUB_ENABLED`
- `LOBEHUB_BASE_URL`
- `LOBEHUB_SSO_EMAIL_DOMAIN`
- `LOBEHUB_INITIAL_OWNER_UNIFIED_AUTH_ID`

初次启动前保持 `LOBEHUB_ENABLED=false`。

配置完成后先执行 `lobehub-docker.sh validate-config`。预检要求三个镜像引用与已安装 `release.env` 完全一致、
全部占位值已替换、数据库/Redis 密码至少 16 字节、HMAC/委托加密/Key Vault/Session/RustFS secret 至少
32 字节，并强制离线、本地认证关闭、企业模型唯一适配器、24 小时 Session 和纯 HTTP Cookie 契约。任何一项
不满足时 migration 和容器启动都会失败关闭。

Windows 执行能力始终必须为 `false`。Linux 默认也为 `false`；只有目标机真实沙箱验收通过后，才能设置为
`true` 并提供 root 所有、mode `0600` 的绝对路径审批文件。文件必须记录
`LINUX_SANDBOX_STATUS=PASSED`、目标发行版和与当前 `uname -r` 完全一致的内核版本，否则启动被拒绝。

## Redis ACL

为 fork 创建独立 Redis 用户，key pattern 和 channel pattern 都只允许 `lobehub:app:*`；按实际上游命令清单
授予最小命令，至少禁止 `CONFIG`、`ACL`、`MODULE`、`KEYS`、`FLUSH*`、`EVAL`（除非 fork 有经过审计的
固定脚本）以及其它管理命令。`lobehub-docker.sh check-redis` 会验证：

- 当前账号能 PING、写删 `lobehub:app:*` 并发布 `lobehub:app:*` channel。
- 不能写 `test-agent:*` key，不能发布 `test-agent:*` channel，不能执行 `CONFIG GET`。

平台票据、nonce 和委托使用现有平台 Redis 连接，固定前缀 `test-agent:lobehub-sso:*`。现场还必须通过 fork
集成测试确认上游所有 key/pubsub 都遵守 `REDIS_PREFIX=lobehub:app:`，不存在无前缀旁路。

## 启动顺序

严格按以下顺序开放：

1. 备份平台 PostgreSQL、LobeHub ParadeDB、RustFS 和所有 LobeHub 密钥。
2. 盘点每个目标平台库的 `flyway_schema_history`，确认新 migration 版本高于全部已执行版本且无分叉。
3. `lobehub-docker.sh validate-config`，再执行 `check-redis`。
4. `lobehub-docker.sh start-db`，等待 `pg_isready` 并确认 `server_version_num` 为 PostgreSQL 17。
5. `lobehub-docker.sh migrate`；迁移失败立即停止，不能跳过。
6. `lobehub-docker.sh start-rustfs` 和 `init-bucket`；初始化会显式执行 `anonymous set none`，bucket 保持私有。
7. 升级平台 migration/backend，确认 health 和三类 SSO API。
8. `lobehub-docker.sh start-app`，再配置 HTTP 反向代理/DNS。
9. 管理员维护模型目录，对每一项声明能力执行探测；只有成功能力才进入 `/models`。
10. 完成认证、部门 Workspace、模型、断网和客户端验收后，最后设置 `LOBEHUB_ENABLED=true`。

全部 LobeHub 容器的 systemd 启停入口：

```bash
systemctl enable --now test-agent-lobehub
systemctl status test-agent-lobehub
```

`lobehub-docker.sh start` 只负责共享 Redis 检查、LobeHub DB/migration、RustFS/init 和 app；平台升级仍按上述
外层顺序单独执行。

## HTTP 现场配置与剩余风险

当前现场明确使用纯 HTTP：Cookie 必须是 host-only、HttpOnly、SameSite=Lax、`Secure=false`，聊天域名、
平台兑换/模型网关、Redis、数据库和对象存储只能从企业隔离网段访问。一次性表单票据、LobeHub Session
Cookie 和服务端模型委托仍可能被同网段被动监听或主动劫持；HMAC、nonce、短票据、24 小时 Session、委托
scope 和网络 ACL 只能降低风险，不能替代 TLS。TLS 是后续必须评审的安全升级项。

反向代理模板位于 `deploy/internal/nginx/lobehub.conf.template`；需与现有 gateway 模板中的
`$connection_upgrade` map 一起加载，并把 `__LOBEHUB_SERVER_NAME__` 替换为固定聊天域名。禁止把平台兑换、
Redis、ParadeDB 或 RustFS console 暴露到非隔离网段。

## 验收

- 认证：平台入口、新标签同步打开、直接域名回平台、登录后恢复、独立退出、票据过期/重放、HMAC/nonce
  重放、固定回跳、URL/存储/日志无票据。
- Workspace：NFKC/空白/英文大小写归一化、跨组织同名合并、并发首次单创建、空部门拒绝、调动保留双权限、
  默认私有、显式共享和管理员撤销审计。
- 模型：动态目录、九项能力探测、JSON/SSE/multipart、SSE 中断、错误脱敏、供应商密钥/UCID 注入、用户停用
  即时拒绝、委托轮换/撤销、后台任务无系统账号兜底和 OpenCode proxy 回归。
- 离线：物理断公网运行，无搜索、遥测、CDN、Marketplace、Connector 或下载；镜像、源码、SBOM、许可证、
  白名单和 RustFS 私有访问均复核。
- Windows：RDS 多账号配置隔离；UI/API/深链/Labs 均无法开启任何本地执行；签名状态和文件摘要匹配。
- Linux：在目标发行版/内核验证文件、环境、网络、socket、并发、进程终止和逃逸；未通过保持执行关闭。
- 数据：H2、真实 PostgreSQL、完整 Flyway、已部署基线升级、共享 Redis ACL/前缀碰撞、备份恢复和回滚演练。

## 备份与回滚

升级前一致性备份 LobeHub ParadeDB、RustFS 数据和外部密钥，记录三个镜像 digest 与平台 JAR/前端版本。
回滚优先设置 `LOBEHUB_ENABLED=false`、从反向代理摘除聊天入口并停止 LobeHub。只有新 schema 明确向后兼容时
才回退镜像；否则恢复同一时点数据库快照、RustFS 和密钥。平台 migration 不做 down migration，不使用 Flyway
`repair`、`outOfOrder` 或手工修改历史表。恢复后先保持入口关闭，重复完整验收再开放。
