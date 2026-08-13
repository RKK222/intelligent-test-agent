# ClickHouse 运营分析离线部署与切换

本手册用于在企业网络新增一台独立 x86_64 ClickHouse 服务器，固定使用 ClickHouse 26.3.17.56，承载平台全部运营用户维度、行为事实、小时/日汇总、Token 使用与 Agent/Skill/Tool 调用统计。平台业务库仍使用 PostgreSQL；PostgreSQL 只保留业务主数据、临时事务 outbox、Redis 消费检查点、回填切换记录和任务锁，不作为任何运营查询来源。

本交付不使用 Docker Compose。ClickHouse 专机只运行一个 `--restart unless-stopped` 容器，数据落在 `/data/testagent/clickhouse/data`，日志落在 `/data/testagent/clickhouse/log`。原始运营事实 TTL 为 2 年，汇总表不设置 TTL。

## 1. 地址与目录

先确定 `CLICKHOUSE_HOST`，它必须是未分配给 `.20/.4/.114/.2` 的独立 x86_64 Linux 服务器。防火墙只允许 `122.233.30.4` 和 `122.233.30.114` 访问 `8123/tcp`，不得把端口暴露到用户网段。

| 机器 | 固定目录 | 用途 |
|---|---|---|
| 外网 Mac 构建机 | `deploy/internal/dist` | 拉取 linux/amd64 镜像并生成离线包 |
| 企业内部中转机 | `~/Desktop/mimoagent/0709` | U 盘校验与分发；不使用 `/data/0709` |
| ClickHouse 专机 | `/data/0709` | 接收、校验和解压离线包 |
| ClickHouse 专机 | `/data/testagent/clickhouse` | 持久数据与日志 |
| 两台 Java 后端 | `/data/testagent/config/backend.env` | ClickHouse JDBC 配置与回填开关 |

## 2. 外网 Mac 打包

在仓库根目录执行：

```bash
deploy/internal/package-clickhouse-offline.sh
tools/verify-internal-clickhouse-package.sh
tools/verify-internal-clickhouse-deploy.sh
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
ssh root@CLICKHOUSE_HOST 'install -d -m 0755 /data/0709'
scp test-agent-clickhouse-offline.zip test-agent-clickhouse-offline.zip.sha256 \
  root@CLICKHOUSE_HOST:/data/0709/
```

成功条件：SHA 为 `OK`，ZIP 无损坏。中转机不创建 `/data/0709`。

## 4. ClickHouse 专机部署

当前机器：`CLICKHOUSE_HOST`。

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

脚本会校验 linux/amd64 架构、镜像版本、密码摘要、持久目录和数据库存在性。首次部署成功后执行：

```bash
./deploy-clickhouse.sh \
  --env-file /data/0709/test-agent-clickhouse-offline/config/clickhouse.env \
  --users-config /data/0709/test-agent-clickhouse-offline/config/clickhouse-users.xml \
  verify
docker ps --filter name=test-agent-clickhouse
```

不得删除 `/data/testagent/clickhouse/data`。容器需要替换时使用 `--replace-existing`，该参数只替换容器，不删除数据。

## 5. 双后端连接预检

把包内 `config/backend-clickhouse.env` 的 `REPLACE_CLICKHOUSE_HOST` 替换为专机 IP，然后将同一组配置合并到 `.4` 和 `.114` 的 `/data/testagent/config/backend.env`。不要 `source` 整个 dotenv；按现有部署脚本的 dotenv 读取方式更新。两台机器分别验证：

```bash
nc -vz CLICKHOUSE_HOST 8123
curl -fsS -u 'testagent_analytics:REPLACE_WITH_BUNDLE_PASSWORD' \
  --data-binary 'select version(), currentDatabase()' \
  'http://CLICKHOUSE_HOST:8123/?database=testagent_analytics'
```

必须返回版本 `26.3.17.56` 和数据库 `testagent_analytics`。密码不要写入 shell history；现场应从受控 0600 配置读取后执行等价检查。

## 6. 首次发布与历史回填

回填使用同一套 Java 程序，不在 ClickHouse 专机运行额外脚本。先在两台后端都配置：

```dotenv
TEST_AGENT_ANALYTICS_CLICKHOUSE_ENABLED=true
TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=false
TEST_AGENT_ANALYTICS_CLICKHOUSE_CLEANUP_LEGACY_ROLLUPS=false
```

按既有双后台流程发布并重启 `.4`、`.114`。Java 启动时会执行 ClickHouse 幂等 schema migration；checksum 不一致会拒绝启动。确认两台 readiness 正常后，在 XXL-JOB 中确认 `opencode-runtime.analytics-ingestion` 每分钟运行，`opencode-runtime.analytics-rollup` 每 5 分钟运行。

历史回填只允许一个后台节点开启，另一个保持 `BACKFILL_ENABLED=false`。选择明确的 UTC 覆盖窗口：

```dotenv
TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=true
TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_START=2025-01-01T00:00:00Z
TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_END=2026-08-14T00:00:00Z
TEST_AGENT_ANALYTICS_CLICKHOUSE_CLEANUP_LEGACY_ROLLUPS=false
```

重启该节点。回填先补全未删除/停用用户维度，再按自然日分块、每批 500 条写行为事实；事实 ID 使用稳定 `backfill-v1:` 前缀。源事件数、ClickHouse 原始事件数、活动事实与用户维度事实之和完全一致后才写入 `analytics_clickhouse_cutovers=VERIFIED` 和 ClickHouse freshness 水位。失败会中止该节点启动，不会清理 PostgreSQL 旧汇总表。

完成后立即把 `BACKFILL_ENABLED=false` 并再次重启。用超级管理员页面验收：

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
