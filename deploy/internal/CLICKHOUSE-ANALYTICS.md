# ClickHouse 运营分析离线部署与切换

本手册用于在企业网络新增一个独立 x86_64 ClickHouse 进程，固定使用 ClickHouse 26.3.17.56，承载平台全部运营用户维度、行为事实、小时/日汇总、Token 使用与 Agent/Skill/Tool 调用统计。平台业务库仍使用 PostgreSQL；PostgreSQL 只保留业务主数据、临时事务 outbox、Redis 消费检查点、回填切换记录和任务锁，不作为任何运营查询来源。当前首次试部署按现场指定把 ClickHouse 容器共置在原平台 PostgreSQL 节点 `122.233.30.147`，但必须继续保留 PostgreSQL 的 `5432`、进程和全部数据目录；ClickHouse 只使用 `8123` 与 `/data/testagent/clickhouse`，资源或端口不满足时停止，不得为部署 ClickHouse 清理或迁移原 PG 数据。

本交付不使用 Docker Compose。ClickHouse 节点只运行一个 `--restart unless-stopped` 容器，数据落在 `/data/testagent/clickhouse/data`，日志落在 `/data/testagent/clickhouse/log`。原始运营事实 TTL 为 2 年，汇总表不设置 TTL。外网打包按多架构索引 digest `sha256:422be85a...fcb3` 固定上游，并直接导出 linux/amd64 archive，避免 Apple Silicon 本地 arm64 tag 污染交付物。当前现场 Docker 18.09 的默认 seccomp 会把新镜像使用的系统调用返回为 `EPERM`；按现场明确批准，交付脚本固定使用 `--privileged` 启动 ClickHouse。该例外不修改 Docker daemon，也不改变共置 PostgreSQL 容器的启动参数，但会显著放宽 ClickHouse 容器对宿主的权限。容器进程仍由官方入口降权为 UID/GID `101:101`；持久目录和 `0600` 用户配置统一归该 UID/GID 所有，禁止额外设置 `CLICKHOUSE_RUN_AS_ROOT=1`，否则 ClickHouse 会因进程用户与数据所有者不一致而退出。

## 1. 地址与目录

先确定 `CLICKHOUSE_HOST`。当前试部署固定取原平台 PostgreSQL 节点 `CLICKHOUSE_HOST=122.233.30.147`。防火墙只允许 `122.233.30.4` 和 `122.233.30.114` 访问 `8123/tcp`，不得把端口暴露到用户网段。由于它与平台 PG 共用宿主机，部署前必须额外确认 CPU、内存和 `/data` 容量；资源不足时停止并重新申请专机，不能挤占 PostgreSQL 已有容量。

| 机器 | 固定目录 | 用途 |
|---|---|---|
| 外网 Mac 构建机 | `deploy/internal/dist` | 拉取 linux/amd64 镜像并生成离线包 |
| 企业内部中转机 | `~/Desktop/mimoagent/0709` | U 盘校验与分发；不使用 `/data/0709` |
| `.147` 平台 PG/ClickHouse 共置节点 | `/data/0709` | 接收、校验和解压离线包 |
| `.147` 平台 PG/ClickHouse 共置节点 | `/data/testagent/clickhouse` | ClickHouse 独立持久数据与日志 |
| 两台 Java 后端 | `/data/testagent/config/backend.env` | ClickHouse JDBC 配置与回填开关 |

## 2. 外网 Mac 打包

在仓库根目录执行：

```bash
deploy/internal/package-clickhouse-offline.sh
tools/verify-internal-clickhouse-package.sh
tools/verify-internal-clickhouse-deploy.sh
```

默认生成 `testagent_analytics` 用户和随机 64 位十六进制密码。仅在已经用防火墙限制来源的内网试部署中，可用环境变量生成更易手工输入的账号密码；密码不会打印到日志，仍只写入 `0600` 交付配置，不能把真实值提交到 Git：

```bash
TEST_AGENT_CLICKHOUSE_PACKAGE_USERNAME=ck \
TEST_AGENT_CLICKHOUSE_PACKAGE_PASSWORD='<8-64位URL安全字符>' \
  deploy/internal/package-clickhouse-offline.sh
```

输出固定为：

```text
deploy/internal/dist/test-agent-clickhouse-offline.zip
deploy/internal/dist/test-agent-clickhouse-offline.zip.sha256
```

包内包含随机 64 位十六进制密码和匹配的 SHA-256 用户配置，属于敏感文件，必须保持 `0600`。打包日志不会打印密码。若复用已下载的 linux/amd64 镜像 tar：

```bash
deploy/internal/package-clickhouse-offline.sh \
  --image-tar /absolute/path/test-agent-clickhouse_26.3.17.56-linux-amd64.tar
```

## 3. 企业中转机校验与分发

当前机器：企业内部中转机。

```bash
cd ~/Desktop/mimoagent/0709
sha256sum -c test-agent-clickhouse-offline.zip.sha256
unzip -t test-agent-clickhouse-offline.zip
ssh root@122.233.30.147 'install -d -m 0755 /data/0709'
scp test-agent-clickhouse-offline.zip test-agent-clickhouse-offline.zip.sha256 \
  root@122.233.30.147:/data/0709/
```

成功条件：SHA 为 `OK`，ZIP 无损坏。中转机不创建 `/data/0709`。

## 4. `.147` 平台 PG/ClickHouse 共置节点部署

当前机器：`122.233.30.147`。

企业目标机不预装 `psql`、`jq`、`rg`，本流程不依赖它们。解压前先用系统自带命令只读确认现有 PostgreSQL 与目标目录；本节点预期 `5432` 已监听且能发现 PG 数据，二者都必须保留。若 `8123` 已监听、目标目录非空、内存或磁盘容量不足，必须停止确认，不能删除原数据：

```bash
uname -m
docker version --format 'docker={{.Server.Version}}'
docker info --format 'security={{.SecurityOptions}}'
docker ps -a --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
ss -lnt 2>/dev/null | grep -E ':(5432|8123)[[:space:]]' || true
find /data /var/lib -maxdepth 4 -type f -name PG_VERSION -print 2>/dev/null
free -h
if [ -d /data/testagent/clickhouse ] && [ -n "$(find /data/testagent/clickhouse -mindepth 1 -print -quit 2>/dev/null)" ]; then
  echo 'STOP: /data/testagent/clickhouse 已有内容，先确认归属'
  exit 1
fi
df -h /data
```

成功条件：`uname -m` 为 `x86_64`，Docker 可用，`5432` 的平台 PG 保持原状，`8123` 没有既有监听，目标目录为空或不存在，内存与 `/data` 容量满足 PostgreSQL 和两年运营事实的共同容量评估。现有 PG 进程和 `PG_VERSION` 文件全部保留。

```bash
cd /data/0709
sha256sum -c test-agent-clickhouse-offline.zip.sha256
unzip -t test-agent-clickhouse-offline.zip
unzip -oq test-agent-clickhouse-offline.zip
cd /data/0709/test-agent-clickhouse-offline
sha256sum -c test-agent-clickhouse_26.3.17.56-linux-amd64.tar.sha256

./deploy-clickhouse.sh \
  --env-file /data/0709/test-agent-clickhouse-offline/config/clickhouse.env \
  --users-config /data/0709/test-agent-clickhouse-offline/config/clickhouse-users.xml \
  --image-tar /data/0709/test-agent-clickhouse-offline/test-agent-clickhouse_26.3.17.56-linux-amd64.tar \
  validate

./deploy-clickhouse.sh \
  --env-file /data/0709/test-agent-clickhouse-offline/config/clickhouse.env \
  --users-config /data/0709/test-agent-clickhouse-offline/config/clickhouse-users.xml \
  --image-tar /data/0709/test-agent-clickhouse-offline/test-agent-clickhouse_26.3.17.56-linux-amd64.tar \
  deploy
```

脚本会校验 linux/amd64 架构、镜像版本、密码摘要、持久目录和数据库存在性，并以现场批准的 `--privileged` 创建 ClickHouse 容器；数据、日志和 `0600` 用户配置均归镜像内 UID/GID `101:101` 所有，进程不以 root 运行。本机认证与建库使用容器内 `clickhouse-client`，不依赖宿主安装客户端，也不因 Docker DNAT 把宿主回环来源改写成网桥地址而扩大用户白名单。首次部署成功后执行：

```bash
./deploy-clickhouse.sh \
  --env-file /data/0709/test-agent-clickhouse-offline/config/clickhouse.env \
  --users-config /data/0709/test-agent-clickhouse-offline/config/clickhouse-users.xml \
  verify
docker ps --filter name=test-agent-clickhouse
```

不得删除 `/data/testagent/clickhouse/data`。容器需要替换时使用 `--replace-existing`，该参数只替换容器，不删除数据。

## 5. 双后端连接预检

把包内 `config/backend-clickhouse.env` 的 `REPLACE_CLICKHOUSE_HOST` 替换为 `122.233.30.147`，然后将同一组配置合并到 `.4` 和 `.114` 的 `/data/testagent/config/backend.env`。不要 `source` 整个 dotenv；按现有部署脚本的 dotenv 读取方式更新。两台机器分别验证：

```bash
nc -vz 122.233.30.147 8123
curl -fsS -u 'testagent_analytics:REPLACE_WITH_BUNDLE_PASSWORD' \
  --data-binary 'select version(), currentDatabase()' \
  'http://122.233.30.147:8123/?database=testagent_analytics'
```

必须返回版本 `26.3.17.56` 和数据库 `testagent_analytics`。密码不要写入 shell history；现场应从受控 0600 配置读取后执行等价检查。

## 6. 首次发布与历史回填

回填使用同一套 Java 程序，不在 ClickHouse 专机运行额外脚本。先在两台后端都配置：

```dotenv
TEST_AGENT_ANALYTICS_CLICKHOUSE_ENABLED=true
TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=false
TEST_AGENT_ANALYTICS_CLICKHOUSE_CLEANUP_LEGACY_ROLLUPS=false
```

按既有双后台流程发布并重启 `.4`、`.114`。Java 启动时会执行 ClickHouse 幂等 schema migration；checksum 不一致会拒绝启动。完整平台包的 `.4` 节点部署入口会在常规 Java、worker 和健康校验通过后调用 `run-analytics-clickhouse-backfill.sh`：若 `/data/testagent/config/analytics-clickhouse-backfill.state` 已登记 `analytics-v1=VERIFIED`，直接跳过 Runner 和额外 Java 重启；旧包已成功但尚无状态文件时，从 `/data/testagent/logs/backend.log` 中只接受 Java 的 `verified=true` 完成行并自动补登。只有两者均不存在时才临时开启回填并重启 Java。`.114` 和后续扩容节点始终保持关闭。打包阶段只封装这项能力，真实回填发生在能访问生产 PostgreSQL/ClickHouse 的 `.4` 部署阶段。

历史回填只允许一个后台节点开启，另一个保持 `BACKFILL_ENABLED=false`。包内选择明确的 UTC 覆盖窗口：

```dotenv
TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=true
TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_START=2025-01-01T00:00:00Z
TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_END=
TEST_AGENT_ANALYTICS_CLICKHOUSE_CLEANUP_LEGACY_ROLLUPS=false
```

自动入口会临时把开关改为 `true` 并重启该节点。回填先补全未删除/停用用户维度，再按自然日分块、每批 500 条写行为事实；事实 ID 使用稳定 `backfill-v1:` 前缀。源事件数、ClickHouse 原始事件数、活动事实与用户维度事实之和完全一致后才写入 `analytics_clickhouse_cutovers=VERIFIED` 和 ClickHouse freshness 水位。入口按 journal cursor 增量读取本次启动日志，同时从 `/data/testagent/logs/backend.log` 的本次启动字节偏移读取同一 Java 的完成/失败信号；旧 systemd 无法稳定返回 cursor 增量时不再丢失完成行。每轮输出最新一条 hourly/daily 汇总进度，且只接受 Java 日志 `ClickHouse 运营回填完成` 作为成功；不使用固定行数窗口。默认最多等待 7200 秒。

成功后入口以 `0600` 原子写入本机完成状态，再立即把落盘的 `BACKFILL_ENABLED` 恢复为 `false`。当前 Java 只在启动时读取一次该开关，因此无需为关闭开关再次中断服务；未来普通重启和平台增量发布都不会重复进入 Runner。完成状态缺键、重复、未知版本、未知 cutover 或非 `VERIFIED` 时失败关闭，不能把它当成跳过依据。失败、超时或中断时入口会先恢复 `false`、重启普通服务，再以非零状态阻断 `.114` 和 `.2` 的后续发布。只有明确的紧急恢复场景才可在逐机入口传 `--skip-analytics-backfill`，并必须另行完成本节验收，不能作为正常发布参数。

旧包若终端已经卡在 `Starting idempotent ClickHouse analytics backfill`，先在原终端按 `Ctrl+C`；旧脚本的退出 trap 必须把开关恢复为 `false` 并重启普通 Java。不要直接杀 shell 或继续 `.114`。随后检查 `.4`：

```bash
grep -E 'ClickHouse 运营回填完成, skipped=(true|false), verified=true,' \
  /data/testagent/logs/backend.log | tail -n 3
grep '^TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=' \
  /data/testagent/config/backend.env
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

三项成功条件分别为：至少一条 Java 完成行、开关为 `false`、readiness 成功。再部署含本修复的新包；新入口会自动把旧成功行迁移为完成状态并输出 `skip runner and backend restart`。若第一条没有输出，只能由数据库管理员再次确认 PostgreSQL `analytics_clickhouse_cutovers` 中 `analytics-v1` 的完整状态和计数；不得仅凭 ClickHouse 表“看起来有数据”手工伪造状态文件。

不使用完整多后台入口、而是直接调用标准后台部署脚本时，首节点必须显式加 `--run-analytics-backfill`。也可在已经安装同批次脚本的 `.4` 单独执行以下等价动作；命令不读取或打印数据库密码：

```bash
bash /data/testagent/deploy/internal/run-analytics-clickhouse-backfill.sh \
  --backend-env /data/testagent/config/backend.env \
  --backend-service test-agent-backend \
  --timeout-seconds 7200
```

随后用超级管理员页面验收：

- 小时热力图选择用户消息、主 Token、缓存 Token，日期与小时均按 `Asia/Shanghai`。
- 用户漏斗以 ClickHouse 当前未删除的全部用户为首层，活跃/深度口径与页面备注一致，三层数量保持单调。
- Token 使用人数、日人均 Token、重复使用率有数据。
- Agent、Skill、Tool 能看到调用人数与使用率。
- `freshness.status=FRESH`，覆盖窗口与回填窗口一致。

## 7. 验收后清理 PostgreSQL 旧汇总

这是不可逆的结构清理步骤，只能在 ClickHouse 验收通过、备份完成且发布负责人批准后执行。仍只在一个后台节点短期开启：

```dotenv
TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=true
TEST_AGENT_ANALYTICS_CLICKHOUSE_CLEANUP_LEGACY_ROLLUPS=true
```

服务检测到既有 `VERIFIED` 记录后不会重复回填，只删除 PostgreSQL 的五张旧运营汇总/水位表；`analytics_job_locks`、事务 outbox、Redis 检查点和切换记录继续保留。启动成功后把两个开关都恢复为 `false` 并再次重启。禁止在计数未校验或 ClickHouse 不可查询时手工删除表。

## 8. 故障与回滚

ClickHouse 不可用时，业务登录、消息、Run 和反馈写入不阻塞；PostgreSQL outbox 保留未发布事件并每分钟重试。运营查询明确返回 `503 ANALYTICS_UNAVAILABLE`，不会偷偷回退到 PostgreSQL 扫描。

如果日志出现以下错误，说明容器已经越过端口映射阶段，但旧 Docker seccomp 把 ClickHouse 读取时区目录所需的新系统调用返回为 `EPERM`：

```text
Could not determine time zone from TZ variable value: 'UTC'
filesystem error: in weakly_canonical: Operation not permitted ["/usr/share/zoneinfo/"]
```

先执行 `docker stop test-agent-clickhouse` 停止重启循环，换用内含 `--privileged` 启动参数的新包，再通过
`--replace-existing deploy` 重新创建当前 ClickHouse 容器。不得把 daemon 全局改成 unconfined，也不得删除
`/data/testagent/clickhouse/data`。宿主 Docker、runc 与 libseccomp 完成受控升级并在真实节点以非 privileged
方式回归全部离线镜像后，才可移除该例外。

已经把旧包解压到 `.147` 时无需重新传输约 251 MiB 镜像。先备份并原地补齐 `--privileged` 和 `0600` 配置文件所有者，同时移除排障期间可能加入的 root 运行变量；命令可重复执行，发现既有备份但脚本仍未正确修改时会停止，避免覆盖恢复点：

```bash
cd /data/0709/test-agent-clickhouse-offline
if ! grep -Fq -- '--privileged' deploy-clickhouse.sh; then
  test ! -e deploy-clickhouse.sh.bak-before-privileged || {
    echo 'STOP: backup already exists but script is not patched'
    exit 1
  }
  cp -p deploy-clickhouse.sh deploy-clickhouse.sh.bak-before-privileged
  sed -i 's/^docker run -d \\$/docker run -d --privileged \\/' deploy-clickhouse.sh
fi
sed -i '/CLICKHOUSE_RUN_AS_ROOT=1/d' deploy-clickhouse.sh
if ! grep -Fq 'chown 101:101 "${installed_config}"' deploy-clickhouse.sh; then
  sed -i '/^install -m 0600 "${USERS_CONFIG_FILE}" "${installed_config}"$/a chown 101:101 "${installed_config}"' \
    deploy-clickhouse.sh
fi
bash -n deploy-clickhouse.sh
test "$(grep -c 'chown 101:101 "${installed_config}"' deploy-clickhouse.sh)" -eq 1
test "$(grep -c 'CLICKHOUSE_RUN_AS_ROOT=1' deploy-clickhouse.sh)" -eq 0
grep -n -B 3 -A 3 '^docker run -d' deploy-clickhouse.sh
```

预期显示 `docker run -d --privileged \`，并且其上方存在一次 `chown 101:101 "${installed_config}"`，没有 `CLICKHOUSE_RUN_AS_ROOT=1`。任一条件不满足时停止，不要执行 deploy。

如果排障日志出现下面的错误，说明进程被强制改为 root，但数据仍属于官方 clickhouse 用户；不要递归把数据改为 root，也不要删除或重新初始化数据，按上面的原地脚本修复恢复 UID/GID `101:101` 后重新创建容器：

```text
Code: 430. Effective user of the process (root) does not match the owner of the data (clickhouse).
MISMATCHING_USERS_FOR_PROCESS_AND_DATA
```

清理旧汇总前的回滚：

1. 两台后端设置 `TEST_AGENT_ANALYTICS_CLICKHOUSE_ENABLED=false`。
2. 按既有顺序重启后端；业务功能继续可用，运营查询明确返回 `503 ANALYTICS_UNAVAILABLE`，不会读取 PostgreSQL 旧汇总。
3. 在 ClickHouse 专机停止容器但保留数据：

```bash
cd /data/0709/test-agent-clickhouse-offline
./deploy-clickhouse.sh \
  --env-file /data/0709/test-agent-clickhouse-offline/config/clickhouse.env \
  --users-config /data/0709/test-agent-clickhouse-offline/config/clickhouse-users.xml \
  stop
```

清理旧汇总后的回滚不能仅切开关，必须从发布前 PostgreSQL 备份恢复旧表或修复 ClickHouse 后继续使用；因此清理必须最后执行。ClickHouse 数据恢复时停止容器，恢复 `/data/testagent/clickhouse/data` 的同版本备份，再启动原版本容器并执行 `verify`。

## 9. 数据与安全边界

- ClickHouse 只保存运营字段、用户与组织归属快照、计数、Token 数、能力名和状态，不保存 prompt、用户消息正文、assistant 正文、反馈评论或费用。
- Agent 主 Run、task 子 Agent、skill 与普通 tool 分开统计；同一次调用用 `runId + scopeId + callId` 去重，终态替换开始态。
- PostgreSQL outbox 与业务写同事务；ClickHouse 故障只造成运营数据延迟，不反向失败业务事务。
- Redis Summary Run 使用同槽 `analytics-outbox` stream，候选 Run 来自 PostgreSQL 活动/近期终态索引，不执行 Redis `SCAN`；实际发送人由 Run 权威字段补齐。
- 历史组织维度采用回填时当前用户归属，并以 `CURRENT_ORG_BACKFILL` 标识；新事件采用事件发生时快照。

## 10. 本地 dev 联调

企业专机部署脚本不用于开发机。macOS/Linux 在仓库根目录执行：

```bash
./restart-dev-services.sh --profile test --env-file .env.test --with-clickhouse
```

需要同时验证通用记忆时追加 `--with-memory`。本地 helper 固定使用 ClickHouse 26.3.17.56，只把 HTTP 暴露到
`127.0.0.1:18123`，数据保存在 `test-agent-clickhouse-dev-data-v1` Docker volume。随机密码、自定义用户配置和
后端 JDBC dotenv 位于 `.tmp/dev-services/clickhouse`，权限为 `0600`，不会写入 `.env.test` 或仓库。可单独执行
`tools/clickhouse-dev-services.sh status|stop|restart`；`stop` 只停止容器，不删除数据卷。
