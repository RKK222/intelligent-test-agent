# LobeHub 企业离线部署

本文描述 LobeHub `v2.2.11-platform.5` 的离线制品、安装、启动、备份和回滚。架构与 fork 必须实现的行为见
`docs/architecture/lobehub-integration.md`。本仓库不包含独立 fork 源码，只有通过制品门禁的外部构建结果
才能进入全量包或 LobeHub-only 包。独立 fork 的企业 Git 转运和导入见
`docs/deployment/lobehub-fork-transfer.md`，不能用运行 ZIP 代替源码托管。

## 版本矩阵

| 组件 | 固定基线 | 说明 |
|---|---|---|
| LobeHub upstream | `v2.2.11` / `5b4cef6` | 独立内部 fork，不修改本仓库 OpenCode 快照 |
| LobeHub internal | `v2.2.11-platform.5` / `57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c` | 平台契约版本 `2` |
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
  deploy/internal/lobehub-backup.sh
  deploy/internal/lobehub-redis-acl.sh
  deploy/internal/lobehub-platform-probe.mjs
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
`REDIS_PREFIX=lobehub:app`（上游自动追加分隔冒号，实际 key 为 `lobehub:app:*`），再完成 fork migration
和 dev server 启动。它只在
`.tmp/dev-services/lobehub-dev.env` 生成 mode `0600` 的开发密钥，不修改 `.env.local` 或 `.env.test`。开发
helper 还会启动单独的本地 scheduler 进程，复用 fork 的生产 loopback 实现；其 Bearer secret 不出现在命令行。
helper 固定写入 `LOBEHUB_DEV_HOST=127.0.0.1`，fork 启动序列据此向 Next.js 传入 `-H 127.0.0.1`；只把访问
URL 写成 loopback 不算通过，验收时还必须用 `lsof` 或等价命令确认 `3210` 没有监听 wildcard。
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

该模式直接覆盖 Web、平台票据、Workspace、模型网关和服务端客户端认证 API 联调。已构建的企业 Desktop/CLI
固定访问 `http://chat.internal`，不会读取本地环境变量或 `127.0.0.1:3210`；需要联调安装包时，开发机必须由
运维显式把 `chat.internal` 解析到受控本地反向代理，并由该代理转发到 `127.0.0.1:3210`。启动脚本不修改
`/etc/hosts`、不提权占用 80 端口，也不削弱客户端固定域名策略。

## 独立 fork 企业转运

外网 Mac 从干净、锁定的同级 fork 生成自包含 Git Bundle，不需要把完整开发工作目录或 GitHub credential
转入内网：

```bash
deploy/internal/build-lobehub-fork-transfer.sh \
  --fork-dir /Users/huang/workspace/lobehub-platform \
  --output-dir deploy/internal/dist-lobehub-fork-transfer
```

工具只发布 `refs/heads/main` 和 `refs/tags/v2.2.11-platform.5`，扫描 fork 增量全部可达对象中的高置信
私钥/token 格式，并执行 Bundle verify、独立 clone、包内
`SHA256SUMS` 和外层 ZIP SHA-256。当前真实转运件已复制到外网 Mac
`~/Desktop/mimoagent/0709/lobehub-fork-transfer`，外层 SHA-256 为
`2cbca71e90d0fa5925363c530538506e019227a56f0caeae8cf89e0d677843a2`。这只表示可转运介质已就绪；企业 Git
管理员尚未提供内部远端并完成 push/`ls-remote` 验证，因此不能记为内部源码托管完成。完整导入、权限和失败
处理见 [LobeHub 独立 fork 企业 Git 转运与导入](lobehub-fork-transfer.md)。

## 外部 fork 制品契约

外网构建机输出目录必须至少包含：

```text
release.env
SHA256SUMS
approved-resources.json
LICENSES.txt
windows-authenticode-verification.txt
linux-client-verification.txt
linux-client-acceptance-record.txt
linux-client-build-evidence.txt
images/lobehub-image.tar
images/paradedb-image.tar
images/rustfs-image.tar
clients/lobehub-windows-x64.exe
clients/lobehub-linux-x86_64.tar.gz
bin/mc-linux-amd64
sbom/lobehub.spdx.json
source/lobehub-v2.2.11-platform.5.tar.gz
```

`SHA256SUMS` 必须恰好覆盖除自身外的全部普通文件，不允许绝对路径、`..`、空白文件名、重复项或符号链接。
`release.env` 至少包含：

```dotenv
LOBEHUB_INTERNAL_VERSION=v2.2.11-platform.5
LOBEHUB_UPSTREAM_VERSION=v2.2.11
LOBEHUB_UPSTREAM_COMMIT=5b4cef6
LOBEHUB_FORK_COMMIT=57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c
LOBEHUB_PLATFORM_CONTRACT_VERSION=2
LOBEHUB_PARADEDB_POSTGRES_MAJOR=17
LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED=true
LOBEHUB_LINUX_CLIENT_APPROVED=true
LOBEHUB_LINUX_EXECUTION_DEFAULT=false
LOBEHUB_APP_IMAGE=test-agent/lobehub:v2.2.11-platform.5
LOBEHUB_APP_IMAGE_ID=sha256:<64 lowercase hex>
LOBEHUB_PARADEDB_IMAGE=test-agent/paradedb:pg17-v2.2.11-platform.5
LOBEHUB_PARADEDB_IMAGE_ID=sha256:<64 lowercase hex>
LOBEHUB_RUSTFS_IMAGE=test-agent/rustfs:v2.2.11-platform.5
LOBEHUB_RUSTFS_IMAGE_ID=sha256:<64 lowercase hex>
```

`windows-authenticode-verification.txt` 使用 `KEY=value`，必须包含 `AUTHENTICODE_STATUS=Valid`、非占位
`AUTHENTICODE_SUBJECT`、40/64 位十六进制 `AUTHENTICODE_THUMBPRINT`、与实际 EXE 一致的
`AUTHENTICODE_FILE_SHA256`，以及匹配的内部版本、fork commit、`CLIENT_ARCHITECTURE=x64` 和
`CLIENT_EXECUTION_MODE=disabled`。`linux-client-build-evidence.txt` 必须绑定 Linux 候选件、版本、commit、
x86_64、执行禁用状态、真实构建 OS/内核以及稳定的非占位构建人身份；`linux-client-verification.txt` 必须为
`Approved`，同时绑定候选件、构建证据摘要、`linux-client-acceptance-record.txt`、版本、commit、x86_64、
实际目标 OS/内核和审批人。构建人与审批人必须是大小写无关比较后仍不同的身份。验收记录必须把登录、
无公网依赖、运行期下载阻断、本地数据隔离和设备执行禁用全部记为成功，并关联真实变更单号。详细原生构建与
双人审批步骤见 [LobeHub 企业客户端原生构建与审批](lobehub-client-build.md)。打包脚本
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
  --linux-build-evidence /absolute/path/to/linux-client-build-evidence.txt \
  --linux-approval-evidence /absolute/path/to/linux-client-verification.txt \
  --linux-acceptance-record /absolute/path/to/linux-client-acceptance-record.txt \
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

已完成服务端构建和运行态冒烟后，不需要在正式客户端返回时重拉镜像或重建三份约 2.2 GB 的镜像 tar。使用
无网络定稿工具创建新目录；它先验证原 server-only 清单、锁定版本、镜像身份和执行禁用状态，再调用与打包、
安装相同的客户端门禁：

```bash
deploy/internal/finalize-lobehub-artifacts.sh \
  --server-artifact-dir /absolute/path/to/lobehub-server-only \
  --output-dir /absolute/path/to/lobehub-release-artifacts \
  --windows-client /absolute/path/to/lobehub-windows-x64.exe \
  --windows-signature-evidence /absolute/path/to/windows-authenticode-verification.txt \
  --linux-client /absolute/path/to/lobehub-linux-x86_64.tar.gz \
  --linux-build-evidence /absolute/path/to/linux-client-build-evidence.txt \
  --linux-approval-evidence /absolute/path/to/linux-client-verification.txt \
  --linux-acceptance-record /absolute/path/to/linux-client-acceptance-record.txt
```

源目录不会被修改；输出父目录必须预先存在，工具不会在受保护输入内隐式创建路径。输出已存在时默认拒绝，
只有明确 `--force` 才替换该精确目录。工具使用相邻发布锁，并在校验前后重新核对 server-only 清单、六项
客户端/证据输入和输出 inode；协作发布冲突、输入在复制期间变化或目标被并发替换都会失败，且不会删除并发
创建的目标。输出不得等于或包含任一
客户端、签名/审批证据、版本锁、部署脚本、平台仓库或 server-only 源目录。定稿需要为新目录预留至少
server-only 大小和后续 ZIP 的磁盘空间。摘要篡改、符号链接或其它特殊文件、版本不符、原目录已声称客户端通过、Windows
非 `Valid`、Linux 非 `Approved`、占位审批人或任一摘要不匹配都会失败，且不会生成输出目录。定稿成功后仍须
用下一步 `package-release.sh` 完成独立的最终准入。

服务端阶段构建完成且三张镜像仍在本机时，执行真实运行时冒烟。脚本拒绝覆盖同名容器，临时创建从
`-@all` 开始、显式列出命令且同时限制 key/channel 的 Redis ACL，并实际完成 PostgreSQL 17 启动、LobeHub
migration、私有 RustFS bucket、app readiness、平台 HMAC 成功请求与同 nonce 重放拒绝、离线工作流阻断、
内部 scheduler 鉴权和容器密钥隔离检查。随后它向数据库和对象存储写入
证明数据，执行停机冷备份、移走临时数据、恢复、再次启动和部署验收，并确认两类证明数据仍存在；退出时只
清理自己创建的资源：

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

当前 `.5` server-only 实物位于 `deploy/internal/dist-lobehub-server`，大小约 2.2 GB；其 `release.env`
SHA-256 为 `3c94d96377fc192301be0851dbd3eeaf15965108eb20fa67e069e2ed9919cc03`，`SHA256SUMS` 文件自身 SHA-256 为
`39c17085322d130045bdbe94d969be9b32ca6abc758ce37b6597ec35a4c4dccf`，应用镜像 ID 为
`sha256:7f504fb3723402bd6b17165db02937964799beefc96ae22b34bd8ef60c5ce866`。该目录已经从三张真实 tar 完成
PostgreSQL 17 migration、显式 Redis ACL 与越权拒绝、平台 HMAC/nonce 重放探测、私有 RustFS、app readiness、
离线/调度门禁、证明数据冷备恢复和二次部署验收。`.5` 原生客户端构建工具包 SHA-256 为
`10fba3e98938252eb0ca7a3a40d0425d8f043ebe268ee267c2e019f3e2210ee1`，独立 fork 转运 ZIP SHA-256 为
`2cbca71e90d0fa5925363c530538506e019227a56f0caeae8cf89e0d677843a2`；完整打包门禁确认
因缺少 Windows/Linux 正式客户端而失败关闭。完整 `test-agent-lobehub-offline.zip` 仍以企业签名 Windows x64
客户端及证据、批准的 Linux x86_64 客户端、审批证据和独立验收记录为硬门禁。上述外部结果未提供前，运行
介质状态只能记为“服务端阶段已验证”，不能记为“完整企业介质已完成”；fork 状态只能记为“转运介质已就绪”，
不能记为“企业内部 Git 已托管”。

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

4. Redis 管理员在 `<lobehub-host>` 通过随包脚本创建最小权限用户。管理员密码只用交互式静默输入进入
   `REDISCLI_AUTH`，应用密码由 mode `0600` 的 `lobehub.env` 经 stdin 交给 `redis-cli -x`；两者都不得进入
   argv 或日志。脚本从 `-@all` 开始，只允许固定命令及 `lobehub:app:*` key/channel。随后逐项预检并只启动
   数据依赖。预期看到 ACL applied、配置通过、Redis 检查零退出码、PostgreSQL 17 ready 和 migration 成功：

   ```bash
   read -r -s -p 'Redis administrator password: ' REDISCLI_AUTH
   printf '\n'
   export REDISCLI_AUTH
   /data/testagent/deploy/internal/lobehub-redis-acl.sh apply \
     --env-file /data/testagent/config/lobehub.env \
     --admin-user <redis-admin-user>
   unset REDISCLI_AUTH

   /data/testagent/deploy/internal/lobehub-docker.sh validate-config
   /data/testagent/deploy/internal/lobehub-docker.sh check-redis
   /data/testagent/deploy/internal/lobehub-docker.sh start-db
   /data/testagent/deploy/internal/lobehub-docker.sh migrate
   /data/testagent/deploy/internal/lobehub-docker.sh start-rustfs
   /data/testagent/deploy/internal/lobehub-docker.sh init-bucket
   ```

5. 平台后台节点按现有企业发布手册升级 migration/backend，并确认 health、签票、兑换和模型网关端点已就绪；
   首台出现 Flyway 未知 checksum 或失败记录时立即停止，不继续其它后台或 LobeHub app。

6. `<lobehub-host>` 启动 app 并执行完整部署验收。预期 app、db、rustfs 都为运行状态；验收还会以只读挂载的
   探针调用固定平台撤销端点，证明有效 HMAC 可用且同 nonce 重放为 401，最后打印
   `LobeHub deployment verification passed`：

   ```bash
   /data/testagent/deploy/internal/lobehub-docker.sh start-app
   /data/testagent/deploy/internal/lobehub-docker.sh status
   /data/testagent/deploy/internal/lobehub-docker.sh verify-deployment
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

安装器复核内部清单及包内版本锁、拒绝符号链接和 FIFO/socket/device 等特殊文件、导入三份镜像、验证
tag/image ID/架构、安装客户端/来源/SBOM、ACL/平台探针和 systemd unit，并在不存在时创建 mode `0600` 的
`/data/testagent/config/lobehub.env` 模板。它不会覆盖既有实际配置。

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

预检还会交叉核对连接目标，而不是只分别检查 URL 格式：`DATABASE_URL` 必须指向固定 ParadeDB 容器及配置的
库名/用户，`REDIS_URL` 必须与独立 Redis host/port/user 和 DB 0 一致，S3/MC 必须使用同一 RustFS 凭据与私有
bucket，`INTERNAL_APP_URL` 必须指向固定 app 容器，平台 launch、兑换、撤销和模型网关必须同源。URL 中密码
必须预先 URL encode；会改变 authority/path/query 的未编码 `@`、`/`、`?`、`#` 会被拒绝。

`lobehub.env` 必须是非符号链接的单一 `KEY=value` 文件，mode 为 `0600`，禁止重复键、CRLF 和非法键名。
启动脚本会在 mode `0600` 的临时文件中生成容器最小环境：ParadeDB 只得到三项 PostgreSQL 变量，RustFS 只
得到自己的 access/secret，app/migration 只得到数据库 URL、Redis、S3、认证、模型委托和离线策略所需变量；
镜像清单、MC 凭据和其它容器 secret 不横向注入。临时文件在命令退出时删除，secret 不拼入 Docker 命令行。

app 镜像不创建公网 QStash schedule。单实例 launcher 每分钟只向容器自身 loopback 受保护入口扫描一次定时
任务，单次 30 秒超时且不重叠；到期任务以内嵌方式继承 `createdByUserId` 的模型委托。当前手册禁止启动第二个
app 副本；在没有跨实例选主/锁前横向复制会导致重复 sweep。

`v2.2.11-platform.5` 对 Windows 和 Linux 都强制 `LOBEHUB_DEVICE_EXECUTION_MODE=disabled`，没有可用的
“验收后改 true”路径。Linux 真实沙箱能力需在后续版本另行实现、测试和审批；当前版本修改其它旧布尔变量
不会放开执行入口。

## Redis ACL

使用 `lobehub-redis-acl.sh apply` 为 fork 创建独立 Redis 用户，key pattern 和 channel pattern 都只允许
`lobehub:app:*`；命令权限必须从 `-@all` 开始使用脚本内固定白名单，不得用 `+@all`。Agent Runtime 的物理 key
也由 ioredis `keyPrefix` 约束；列表操作只允许游标 `SCAN`，明确禁止阻塞式 `KEYS`。至少禁止 `CONFIG`、`ACL`、
`MODULE`、`KEYS`、`SCRIPT FLUSH`、`FLUSH*` 以及其它管理命令。客户端认证的
request/code/session 状态机使用仓库中经过测试的固定 Lua 脚本，因此必须允许 `EVAL`，但不得放宽 key/channel
pattern，也不得允许任意管理命令。`lobehub-docker.sh check-redis` 会验证：

- 当前账号能 PING、写删 `lobehub:app:*`、执行受 key pattern 约束的 Lua 并发布 `lobehub:app:*` channel。
- 不能写 `test-agent:*` key，Lua 不能访问前缀外 key，不能发布 `test-agent:*` channel，不能执行
  `CONFIG`、`ACL`、`MODULE`、`FLUSHDB`、`KEYS` 或 `SCRIPT FLUSH`。

平台票据、nonce 和委托使用现有平台 Redis 连接，固定前缀 `test-agent:lobehub-sso:*`。现场还必须通过 fork
集成测试确认上游所有 key/pubsub 都遵守 `REDIS_PREFIX=lobehub:app`，不存在无前缀旁路。

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
9. 执行 `lobehub-docker.sh verify-deployment`，检查镜像、端口、restart policy、PostgreSQL 17、Redis ACL、
   私有 bucket、HTTP 策略、容器密钥隔离，以及平台 HMAC 成功请求与 nonce 重放拒绝；失败不得开放入口。
10. 管理员维护模型目录，对每一项声明能力执行探测；只有成功能力才进入 `/models`。
11. 完成认证、部门 Workspace、模型、断网和客户端验收后，最后设置 `LOBEHUB_ENABLED=true`。

全部 LobeHub 容器的 systemd 启停入口：

```bash
systemctl enable --now test-agent-lobehub
systemctl status test-agent-lobehub
```

`lobehub-docker.sh start` 只负责共享 Redis 检查、LobeHub DB/migration、RustFS/init 和 app；平台升级仍按上述
外层顺序单独执行。每次配置变更、升级、恢复或主机重启后仍须单独执行 `verify-deployment`；`status` 不能替代
完整验收。

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
- 部署：危险 app bind 和交叉错接的 DB/Redis/S3/app/平台 URL 被配置预检拒绝；`start-app/status` 通过真实
  HTTP readiness；`verify-deployment` 复核运行镜像、端口、restart policy、PostgreSQL 17、Redis ACL、私有
  bucket、HTTP 离线策略、密钥隔离和平台 HMAC/nonce 重放拒绝；同机或跨机 Nginx upstream 与 bind 一致，跨机 3210 只允许代理源地址；
  外网构建机的真实镜像冷备份恢复冒烟通过，现场仍重复逐机验收。
- 密钥隔离：重复 dotenv key/符号链接/非 0600 配置被拒绝；`docker inspect` 确认 ParadeDB、RustFS 不含
  HMAC、Session、模型委托或其它容器凭据，临时 env 文件已删除。

## 备份与回滚

`lobehub-backup.sh` 只备份 LobeHub 的 ParadeDB、RustFS、`lobehub.env` 和已安装 `release.env`；平台 PostgreSQL
仍必须按平台发布手册单独备份。归档包含明文 secret，固定 mode `0600`，只能保存到 `/data/testagent` 以外的
受控加密介质。工具不会替运维人员停服务，三个固定 LobeHub 容器中任一个仍运行都会失败关闭。

升级前先关闭平台入口并从反向代理摘除聊天域名，然后执行一致性冷备份：

```bash
systemctl stop test-agent-lobehub
install -d -m 0700 /data/backup/lobehub/change-<change-id>
/data/testagent/deploy/internal/lobehub-backup.sh create \
  --output-dir /data/backup/lobehub/change-<change-id>
/data/testagent/deploy/internal/lobehub-backup.sh verify \
  --archive /data/backup/lobehub/change-<change-id>/test-agent-lobehub-backup-<UTC>.tar.gz
```

必须同时保留 `.tar.gz` 和同名 `.sha256`，并把归档 SHA-256、三个镜像 ID、平台 JAR/前端版本和变更单关联。
若只是备份而不升级，可重新启动并执行 `verify-deployment` 后再恢复入口。

需要恢复时保持入口关闭和三个容器停止，先执行 `verify`，再用显式确认参数恢复：

```bash
/data/testagent/deploy/internal/lobehub-backup.sh restore \
  --archive /data/backup/lobehub/change-<change-id>/test-agent-lobehub-backup-<UTC>.tar.gz \
  --confirm-restore
systemctl start test-agent-lobehub
/data/testagent/deploy/internal/lobehub-docker.sh verify-deployment
```

恢复会先把当前四个目标保存在
`/data/testagent/lobehub/restore-rollback/<UTC>/original/`，不会删除整个平台目录；替换中途失败会尽力自动放回
原数据，并把失败的新文件保存在同一目录的 `failed-new/`。不要在验证完成前删除 rollback 目录。恢复后仍需完成
认证、Workspace、模型和附件抽查，全部通过才恢复反向代理和 `LOBEHUB_ENABLED=true`。

回滚优先关闭入口并停止 LobeHub。只有新 schema 明确向后兼容时才回退镜像；否则恢复同一时点的上述 LobeHub
归档和平台 PostgreSQL 快照。平台 migration 不做 down migration，不使用 Flyway `repair`、`outOfOrder` 或
手工修改历史表。
