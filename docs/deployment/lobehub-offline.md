# LobeHub 企业离线部署

本文描述 LobeHub `v2.2.11-platform.1` 的离线制品、安装、启动、备份和回滚。架构与 fork 必须实现的行为见
`docs/architecture/lobehub-integration.md`。本仓库不包含独立 fork 源码，只有通过制品门禁的外部构建结果
才能进入全量包或 LobeHub-only 包。

## 版本矩阵

| 组件 | 固定基线 | 说明 |
|---|---|---|
| LobeHub upstream | `v2.2.11` / `5b4cef6` | 独立内部 fork，不修改本仓库 OpenCode 快照 |
| LobeHub internal | `v2.2.11-platform.1` / `7d16863c88b8acbacda6d9ee15df0840749e0aaa` | 平台契约版本 `1` |
| LobeHub database | ParadeDB / PostgreSQL 17 | 独立库、账号、密码、数据卷和 migration |
| Redis | 现有企业实例 DB 0 | 独立 ACL 用户；LobeHub key/channel 仅 `lobehub:app:*` |
| RustFS | `release.env` 中的不可变 tag、Docker image ID 和 tar SHA-256 | 私有 bucket，容器以 UID 10001 写数据 |
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

RustFS `9000/9001` 固定只绑定 `127.0.0.1`；app `3210` 默认同样绑定 loopback，并由同机反向代理提供访问。
若反向代理必须位于另一台主机，`LOBEHUB_APP_BIND_ADDRESS` 只能填写 LobeHub 主机获批的具体内网 IPv4，且
主机防火墙必须只允许该代理源地址访问 3210；脚本拒绝 `0.0.0.0`、IPv6 wildcard 和 multicast。ParadeDB
只在专用 Docker network 内可达。平台 Flyway 永远不得连接 LobeHub 数据库。

## 本地开发启动

根目录脚本默认行为不变，不启动 LobeHub。需要完整联调时必须显式增加 `--with-lobehub`；脚本会使用同级
`../lobehub-platform`、启动本地 ParadeDB/RustFS、复用 `.env.test` 指向的 Redis 并固定
`REDIS_PREFIX=lobehub:app:`，再完成 fork migration 和 dev server 启动。它只在
`.tmp/dev-services/lobehub-dev.env` 生成 mode `0600` 的开发密钥，不修改 `.env.local` 或 `.env.test`。开发
helper 还会启动单独的本地 scheduler 进程，复用 fork 的生产 loopback 实现；其 Bearer secret 不出现在命令行。
本地 Compose 的 ParadeDB、RustFS 和 MC 默认值与企业介质使用相同的批准 digest，不使用浮动 `latest`；只有
显式的 `LOBEHUB_DEV_*_IMAGE` 开发覆盖才会改变它们。

```bash
cd /Users/huang/workspace/intelligent-test-agent-gitee
export JAVA_VERSION=25
export JAVA_HOME=$(/usr/libexec/java_home -v "${JAVA_VERSION}")
export PATH="${JAVA_HOME}/bin:/Users/huang/workspace/intelligent-test-agent-gitee/.tmp/dev-bin:/opt/homebrew/opt/libpq/bin:${PATH}"
"${JAVA_HOME}/bin/java" -version
./restart-dev-services.sh --profile test --env-file .env.test --with-lobehub
```

本机未安装 JDK 25 时才把 `JAVA_VERSION` 改为 `21`；解析不到 25/21 时停止，不使用 Java 17。

成功条件是脚本依次打印 backend、frontend 和 `LobeHub: http://127.0.0.1:3210`；直接请求聊天根路径在没有
LobeHub Session 时应返回到平台固定 `/lobehub/launch` 的 307。状态和停止依赖可分别执行：

```bash
tools/lobehub-dev-services.sh status
tools/lobehub-dev-services.sh stop
```

不带 `--with-lobehub` 时，根脚本既不探测也不停止 LobeHub，避免影响已有开发环境。

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
LOBEHUB_FORK_COMMIT=7d16863c88b8acbacda6d9ee15df0840749e0aaa
LOBEHUB_PLATFORM_CONTRACT_VERSION=1
LOBEHUB_PARADEDB_POSTGRES_MAJOR=17
LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=true
LOBEHUB_LINUX_EXECUTION_DEFAULT=false
LOBEHUB_APP_IMAGE=test-agent/lobehub:v2.2.11-platform.1
LOBEHUB_APP_IMAGE_ID=sha256:<64 lowercase hex>
LOBEHUB_PARADEDB_IMAGE=test-agent/paradedb:pg17-v2.2.11-platform.1
LOBEHUB_PARADEDB_IMAGE_ID=sha256:<64 lowercase hex>
LOBEHUB_RUSTFS_IMAGE=test-agent/rustfs:v2.2.11-platform.1
LOBEHUB_RUSTFS_IMAGE_ID=sha256:<64 lowercase hex>
```

`windows-authenticode-verification.txt` 使用 `KEY=value`，至少包含 `AUTHENTICODE_STATUS=Valid`、
`AUTHENTICODE_SUBJECT`、`AUTHENTICODE_THUMBPRINT` 和与实际 EXE 一致的 `AUTHENTICODE_FILE_SHA256`。打包脚本
会失败关闭校验上述契约；现场导入后还会用 `docker image inspect` 确认 tar 提供预期 tag、`linux/amd64`
架构和精确 image ID。这里不使用 `repository@manifestDigest` 作为离线运行引用，因为 `docker save/load` 不恢复
Registry `RepoDigest` 映射；镜像 tar 本身仍由 `SHA256SUMS` 锁定，app 镜像还必须以 OCI label 记录 fork commit。

## Mac 打包与转运

外网 Mac 先确认独立 fork 是锁定提交且工作树干净，再构建真实制品。当前批准的基础镜像摘要命令为：

```bash
cd /Users/huang/workspace/intelligent-test-agent-gitee
git -C /Users/huang/workspace/lobehub-platform rev-parse HEAD
git -C /Users/huang/workspace/lobehub-platform status --short

deploy/internal/build-lobehub-artifacts.sh \
  --fork-dir /Users/huang/workspace/lobehub-platform \
  --output-dir /absolute/path/to/lobehub-release-artifacts \
  --windows-client /absolute/path/to/lobehub-windows-x64.exe \
  --windows-signature-evidence /absolute/path/to/windows-authenticode-verification.txt \
  --linux-client /absolute/path/to/lobehub-linux-x86_64.tar.gz \
  --node-base-image node@sha256:d45d78e7929b46875bbd4e29bea672d5bc48186c6c3588306521c815e78352d6 \
  --busybox-base-image busybox@sha256:1cfa4e2b09e127b9c4ed43578d3f3c18e7d44ea47b9ea98475c0cbe9086525f8 \
  --paradedb-source-image paradedb/paradedb@sha256:ba45fac6b9b3bd5e91407c9c4b1657b8300b78beb24f60eb03b266120b26699c \
  --rustfs-source-image rustfs/rustfs@sha256:e4151ec4728f4d39a714a2c5f6e1f4bc8e0789fe5af4874b95121bae39c6cd12 \
  --mc-source-image minio/mc@sha256:eb4ea9884b77704230e2423e9004d2fa738dc272876b9cc41a297d29443b8780
```

构建器从锁定 fork commit 的 `git archive` 建立独立 Docker context，执行 frozen-lockfile 安装、生成
`linux/amd64` 镜像、源码包、SPDX SBOM、生产依赖许可证清单、资源审批清单和精确校验和；不会把未提交源码
混入镜像。fork 的 Dockerfile frontend 固定到 SHA-256，pnpm/corepack 下载使用不含源码或 secret 的 BuildKit
内容寻址缓存，并对 registry 抖动做有界重试；构建被网络中断后应使用完全相同的摘要参数和 `--force` 重跑，
不能改锁文件或临时放宽 frozen 校验。构建机的 Docker VM 至少分配 8 GiB 内存；fork 会把 Next.js 静态生成
限制为两个 worker，避免 10 CPU / 8 GiB 常见配置在页面数据收集阶段耗尽内存。无签名客户端时可加
`--server-only` 生成真实服务端阶段介质用于部署演练，但该目录会被下一步完整
打包门禁明确拒绝。不得伪造客户端或 Authenticode 证据。

服务端阶段构建完成且三张镜像仍在本机时，执行真实运行时冒烟。脚本拒绝覆盖同名容器，临时创建带
key/channel/command ACL 的 Redis，并实际完成 PostgreSQL 17 启动、LobeHub migration、私有 RustFS bucket、
app readiness、离线工作流阻断、内部 scheduler 鉴权和容器密钥隔离检查；退出时只清理自己创建的资源：

```bash
TEST_AGENT_LOBEHUB_ARTIFACT_DIR=/absolute/path/to/lobehub-release-artifacts \
  deploy/internal/tests/lobehub-runtime-smoke-test.sh
```

Apple Silicon 上通过 Docker 执行 `linux/amd64` 镜像，只能证明镜像和部署脚本契约可运行；脚本会明确标记
Docker Desktop 的 UID chown 兼容垫片，不能替代企业 Linux 主机上的真实 UID 10001、网络、防火墙和内核验收。

外网 Mac 汇集并验证全部产物后执行：

```bash
TEST_AGENT_LOBEHUB_ARTIFACT_DIR=/absolute/path/to/lobehub-release-artifacts \
  deploy/internal/package-release.sh --lobehub-only
```

生成 `deploy/internal/dist/test-agent-lobehub-offline.zip` 及 `.sha256`。全量平台包使用：

```bash
TEST_AGENT_LOBEHUB_ARTIFACT_DIR=/absolute/path/to/lobehub-release-artifacts \
  deploy/internal/package-release.sh --with-lobehub
```

将外层 ZIP 和 `.sha256` 通过 U 盘导入企业内部中转机固定目录 `~/Desktop/mimoagent/0709`。中转机校验后
再 `scp` 到目标服务器 `/data/0709`；不要在中转机创建 `/data/0709`，也不要描述为从外网 Mac 直接 scp。
不得在现场联网补拉镜像、npm 包、Marketplace、Connector 或客户端。

当前仓库已经验证可生成约 2.2 GB 的真实服务端阶段目录；完整 `test-agent-lobehub-offline.zip` 仍以企业签名
Windows x64 客户端、批准的 Linux x86_64 客户端及匹配证据为硬门禁。三项未提供前，介质状态只能记为
“服务端阶段已验证”，不能记为“完整企业介质已完成”。

## 企业现场逐机执行单

以下 `<lobehub-host>` 和 `<proxy-host>` 必须在现场变更单中替换为审批后的固定主机；每一步失败立即停止。

1. 企业内部中转机在固定目录校验并传输，预期 SHA 校验输出 `OK`：

   ```bash
   cd ~/Desktop/mimoagent/0709
   shasum -a 256 -c test-agent-lobehub-offline.zip.sha256
   scp test-agent-lobehub-offline.zip test-agent-lobehub-offline.zip.sha256 root@<lobehub-host>:/data/0709/
   ```

2. `<lobehub-host>` 再次校验、解压和安装；安装成功会提示制品位于 `/data/testagent`：

   ```bash
   cd /data/0709
   sha256sum -c test-agent-lobehub-offline.zip.sha256
   install -d -m 0750 /data/0709/test-agent-lobehub-offline
   unzip -q test-agent-lobehub-offline.zip -d /data/0709/test-agent-lobehub-offline
   cd /data/0709/test-agent-lobehub-offline
   sudo deploy/internal/install-lobehub-offline.sh
   ```

3. `<lobehub-host>` 按 `deploy/internal/lobehub.env.example` 填写
   `/data/testagent/config/lobehub.env`，权限必须为 `0600`；真实密码、Cookie、HMAC 和模型密钥不得回传聊天或
   写入命令行。平台后台的 `TEST_AGENT_LOBEHUB_HMAC_SECRET` 必须与该文件的
   `PLATFORM_SSO_HMAC_SECRET` 一致。

4. Redis 管理员为 LobeHub 创建仅允许 `lobehub:app:*` key/channel 的独立 ACL 用户后，`<lobehub-host>`
   逐项预检并只启动数据依赖。预期分别看到配置通过、Redis 检查零退出码、PostgreSQL 17 ready 和 migration 成功：

   ```bash
   /data/testagent/deploy/internal/lobehub-docker.sh validate-config
   /data/testagent/deploy/internal/lobehub-docker.sh check-redis
   /data/testagent/deploy/internal/lobehub-docker.sh start-db
   /data/testagent/deploy/internal/lobehub-docker.sh migrate
   /data/testagent/deploy/internal/lobehub-docker.sh start-rustfs
   /data/testagent/deploy/internal/lobehub-docker.sh init-bucket
   ```

5. 平台后台节点按现有企业发布手册升级 migration/backend，并确认 health、签票、兑换和模型网关端点已就绪；
   首台出现 Flyway 未知 checksum 或失败记录时立即停止，不继续其它后台或 LobeHub app。

6. `<lobehub-host>` 启动 app 并检查三个容器，预期 app、db、rustfs 都为运行状态：

   ```bash
   /data/testagent/deploy/internal/lobehub-docker.sh start-app
   /data/testagent/deploy/internal/lobehub-docker.sh status
   ```

7. `<proxy-host>` 根据包内 `deploy/internal/nginx/lobehub.conf.template` 配置固定聊天域名，替换
   `__LOBEHUB_SERVER_NAME__` 和 `__LOBEHUB_UPSTREAM__`。同机代理使用 `127.0.0.1:3210`；跨机代理使用与
   `LOBEHUB_APP_BIND_ADDRESS` 相同的 `<lobehub-host-ip>:3210`，并先验证仅该代理源能连通。不得暴露
   ParadeDB、Redis 或 RustFS console。执行该主机既有 Nginx 配置检查和 binary reload，检查失败不得 reload。

8. 平台后台管理员完成模型能力探测，浏览器再验收平台“通用问答”入口、直接聊天域名回平台、部门 Workspace、
   企业模型流式回答和双系统独立退出。全部验收通过后才把公共参数 `LOBEHUB_ENABLED` 改为 `true`。

## 安装与配置

在现场先校验外层 zip SHA-256，解压后以 root 执行：

```bash
deploy/internal/install-lobehub-offline.sh
```

安装器复核内部清单、导入三份镜像、验证 tag/image ID/架构、安装客户端/来源/SBOM、创建 systemd unit，并在不存在时
创建 mode `0600` 的 `/data/testagent/config/lobehub.env` 模板。它不会覆盖既有实际配置。

根据 `deploy/internal/lobehub.env.example` 配置独立数据库密码、共享 Redis ACL 账号、RustFS 密钥、固定平台
地址、HMAC、委托加密密钥、Key Vault、Session 密钥和独立内部 scheduler 密钥。后者使用
`openssl rand -hex 32` 单独生成，不能复用 HMAC、Session 或 Key Vault 密钥。平台 `backend.env` 中
`TEST_AGENT_LOBEHUB_HMAC_SECRET` 必须与 fork 一致。公共非敏感参数通过平台通用参数管理维护：

- `LOBEHUB_ENABLED`
- `LOBEHUB_BASE_URL`
- `LOBEHUB_SSO_EMAIL_DOMAIN`
- `LOBEHUB_INITIAL_OWNER_UNIFIED_AUTH_ID`

初次启动前保持 `LOBEHUB_ENABLED=false`。

配置完成后先执行 `lobehub-docker.sh validate-config`。预检要求三个镜像 tag 与已安装 `release.env` 完全一致、
全部占位值已替换、`DATABASE_DRIVER=node`、数据库/Redis 密码至少 16 字节、HMAC/Session/RustFS secret 至少
32 字节，且委托加密和 Key Vault 密钥必须是 Base64 编码的精确 32 字节；同时强制
`PLATFORM_SSO_ENABLED=1`、`LOBEHUB_ENTERPRISE_OFFLINE=1`、`AGENT_RUNTIME_MODE=local`、
`TELEMETRY_DISABLED=1`、固定 HTTP 路径和 `LOBEHUB_DEVICE_EXECUTION_MODE=disabled`。scheduler secret
必须至少 32 字节；`LOBEHUB_APP_BIND_ADDRESS` 必须是非 wildcard 的具体 IPv4。缺失、queue 模式或危险绑定
会在 migration 和容器启动前失败关闭。`start-app` 只有在根路径真实返回 HTTP 响应后才成功，否则删除失败
容器并使 systemd 启动失败；`status` 同样执行实时 readiness，而不是只看 Docker 进程存在。
`start-db` 还要求容器 PID 1 已从初始化临时服务器切换为正式 `postgres` 后才接受 `pg_isready` 和
`server_version_num`，避免首次安装在 entrypoint 切换窗口提前启动 migration。

`lobehub.env` 必须是非符号链接的单一 `KEY=value` 文件，mode 为 `0600`，禁止重复键、CRLF 和非法键名。
启动脚本会在 mode `0600` 的临时文件中生成容器最小环境：ParadeDB 只得到三项 PostgreSQL 变量，RustFS 只
得到自己的 access/secret，app/migration 只得到数据库 URL、Redis、S3、认证、模型委托和离线策略所需变量；
镜像清单、MC 凭据和其它容器 secret 不横向注入。临时文件在命令退出时删除，secret 不拼入 Docker 命令行。

app 镜像不创建公网 QStash schedule。单实例 launcher 每分钟只向容器自身 loopback 受保护入口扫描一次定时
任务，单次 30 秒超时且不重叠；到期任务以内嵌方式继承 `createdByUserId` 的模型委托。当前手册禁止启动第二个
app 副本；在没有跨实例选主/锁前横向复制会导致重复 sweep。

`v2.2.11-platform.1` 对 Windows 和 Linux 都强制 `LOBEHUB_DEVICE_EXECUTION_MODE=disabled`，没有可用的
“验收后改 true”路径。Linux 真实沙箱能力需在后续版本另行实现、测试和审批；当前版本修改其它旧布尔变量
不会放开执行入口。

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
`$connection_upgrade` map 一起加载，并把 `__LOBEHUB_SERVER_NAME__` 替换为固定聊天域名、
`__LOBEHUB_UPSTREAM__` 替换为上述同机或跨机固定地址。跨机 3210 必须加源地址防火墙；禁止把平台兑换、
Redis、ParadeDB 或 RustFS console 暴露到非隔离网段。

## 验收

- 认证：平台入口、新标签同步打开、直接域名回平台、登录后恢复、独立退出、票据过期/重放、HMAC/nonce
  重放、固定回跳、URL/存储/日志无票据。
- Workspace：NFKC/空白/英文大小写归一化、跨组织同名合并、并发首次单创建、空部门拒绝、调动保留双权限、
  默认私有、显式共享和管理员撤销审计。
- 模型：动态目录、九项能力探测、JSON/SSE/multipart、SSE 中断、错误脱敏、供应商密钥/UCID 注入、用户停用
  即时拒绝、委托轮换/撤销、后台任务无系统账号兜底和 OpenCode proxy 回归。
- 后台任务：直接访问 `/api/workflows/*` 返回 403；缺失/错误 scheduler Bearer 返回 503/401；定时任务只由
  单实例 loopback scheduler 以内嵌 local runtime 执行，日志、浏览器和进程命令行均无密钥。
- 离线：物理断公网运行，无搜索、遥测、CDN、Marketplace、Connector 或下载；镜像、源码、SBOM、许可证、
  白名单和 RustFS 私有访问均复核。
- Windows：RDS 多账号配置隔离；UI/API/深链/Labs 均无法开启任何本地执行；签名状态和文件摘要匹配。
- Linux：在目标发行版/内核验证文件、环境、网络、socket、并发、进程终止和逃逸；未通过保持执行关闭。
- 数据：H2、真实 PostgreSQL、完整 Flyway、已部署基线升级、共享 Redis ACL/前缀碰撞、备份恢复和回滚演练。
- 部署：危险 app bind 被配置预检拒绝；`start-app/status` 通过真实 HTTP readiness；同机或跨机 Nginx upstream
  与 bind 一致，跨机 3210 只允许代理源地址；外网构建机的真实镜像冒烟通过，现场仍重复逐机验收。
- 密钥隔离：重复 dotenv key/符号链接/非 0600 配置被拒绝；`docker inspect` 确认 ParadeDB、RustFS 不含
  HMAC、Session、模型委托或其它容器凭据，临时 env 文件已删除。

## 备份与回滚

升级前一致性备份 LobeHub ParadeDB、RustFS 数据和外部密钥，记录三个镜像 digest 与平台 JAR/前端版本。
回滚优先设置 `LOBEHUB_ENABLED=false`、从反向代理摘除聊天入口并停止 LobeHub。只有新 schema 明确向后兼容时
才回退镜像；否则恢复同一时点数据库快照、RustFS 和密钥。平台 migration 不做 down migration，不使用 Flyway
`repair`、`outOfOrder` 或手工修改历史表。恢复后先保持入口关闭，重复完整验收再开放。
