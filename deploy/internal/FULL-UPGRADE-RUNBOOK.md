# Redis 5 升级与双后台平台全量执行手册

本手册用于当前企业现场：先将 `122.233.30.20` 的 Redis 5 受控升级到 Redis 7.4.9，再使用固定名离线包依次部署 `.4`、`.114`和 `.2`。所有命令都按实际操作机器分组，任一关键校验失败都必须停止，不继续下一台。

更底层的 Redis 迁移解释见 [REDIS-OFFLINE.md](REDIS-OFFLINE.md)，双后台配置、扩容和路由细节见 [MULTI-BACKEND.md](MULTI-BACKEND.md)。

## 1. 目录边界

| 位置 | 固定目录 | 用途 |
|---|---|---|
| Mac 外网构建机 | `/Users/kaka/Desktop/intelligent-test-agent/deploy/internal/dist` | 构建及生成离线包 |
| 企业内部中转机 | `~/Desktop/mimoagent/0709` | U 盘交付物校验和 `scp` 起点 |
| `.20/.4/.114/.2` 目标服务器 | `/data/0709` | 服务器接收、校验和解压目录 |
| 平台运行目录 | `/data/testagent` | 配置、数据、程序、部署脚本和镜像 |

中转机不创建、不使用 `/data/0709`。`/data/0709` 只属于目标服务器。

## 2. 交付物和机器

中转机 `~/Desktop/mimoagent/0709` 应包含：

```text
test-agent-two-backend-complete.zip
test-agent-two-backend-complete.zip.sha256
test-agent-redis-offline.zip
test-agent-redis-offline.zip.sha256
```

| 机器 | 角色 | 接收文件 |
|---|---|---|
| `122.233.30.20` | Redis | Redis ZIP 及 SHA |
| `122.233.30.4` | 后台 A + worker A | 平台 ZIP 及 SHA |
| `122.233.30.114` | 后台 B + worker B | 平台 ZIP 及 SHA |
| `122.233.30.2` | 前端 Nginx | 平台 ZIP 及 SHA |
| `122.233.30.147` | PostgreSQL | 不上传、不手工部署 |
| `122.210.106.43` | 外部 XXL MySQL | 不上传，只验证 `3306` 连通性 |

平台外层 ZIP 已包含内层发布 ZIP、`.4/.114/.2` 三台节点包和一键入口；不再单独上传内层 ZIP 或节点包。

## 3. 中转机校验和分发

当前机器：企业内部中转机。

```bash
cd ~/Desktop/mimoagent/0709
pwd
sha256sum -c test-agent-two-backend-complete.zip.sha256
sha256sum -c test-agent-redis-offline.zip.sha256
unzip -t test-agent-two-backend-complete.zip
unzip -t test-agent-redis-offline.zip
```

成功条件：`pwd` 是当前用户家目录下的 `Desktop/mimoagent/0709`；两个 SHA 均为 `OK`；两个 ZIP 均无压缩错误。

为目标服务器创建接收目录：

```bash
ssh root@122.233.30.20 'install -d -m 0755 /data/0709'
ssh root@122.233.30.4 'install -d -m 0755 /data/0709'
ssh root@122.233.30.114 'install -d -m 0755 /data/0709'
ssh root@122.233.30.2 'install -d -m 0755 /data/0709'
```

分发 Redis 包：

```bash
scp ~/Desktop/mimoagent/0709/test-agent-redis-offline.zip \
  ~/Desktop/mimoagent/0709/test-agent-redis-offline.zip.sha256 \
  root@122.233.30.20:/data/0709/
```

分发平台包到 `.4`：

```bash
scp ~/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip \
  ~/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip.sha256 \
  root@122.233.30.4:/data/0709/
```

分发平台包到 `.114`：

```bash
scp ~/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip \
  ~/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip.sha256 \
  root@122.233.30.114:/data/0709/
```

分发平台包到 `.2`：

```bash
scp ~/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip \
  ~/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip.sha256 \
  root@122.233.30.2:/data/0709/
```

## 4. Redis 新包预校验

当前机器：`122.233.30.20`。

```bash
cd /data/0709
sha256sum -c test-agent-redis-offline.zip.sha256
unzip -t test-agent-redis-offline.zip
unzip -oq test-agent-redis-offline.zip
cd /data/0709/test-agent-redis-offline
sha256sum -c test-agent-redis_7.4.9-alpine-linux-amd64.tar.sha256
./deploy-redis.sh \
  --env-file /data/0709/test-agent-redis-offline/config/redis.env \
  --config-file /data/0709/test-agent-redis-offline/config/redis.conf \
  --image-tar /data/0709/test-agent-redis-offline/test-agent-redis_7.4.9-alpine-linux-amd64.tar \
  validate
```

只有看到镜像 tar `OK` 和 `Redis configuration validation passed` 才继续。

## 5. 升级前连通性检查

当前机器：`122.233.30.4`。

```bash
nc -vz 122.233.30.147 5432
nc -vz 122.210.106.43 3306
nc -vz 122.233.30.20 6379
systemctl status test-agent-backend --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

当前机器：`122.233.30.114`。

```bash
nc -vz 122.233.30.147 5432
nc -vz 122.210.106.43 3306
nc -vz 122.233.30.20 6379
systemctl status test-agent-backend --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

PostgreSQL、外部 MySQL 或当前 Redis 不可达时先处理网络，不进入停机窗口。

## 6. 停止两台 Java，阻断 Redis 写入

当前机器：`122.233.30.4`。

```bash
systemctl stop test-agent-backend
systemctl is-active test-agent-backend || true
ss -lntp | grep ':8080 ' || true
```

当前机器：`122.233.30.114`。

```bash
systemctl stop test-agent-backend
systemctl is-active test-agent-backend || true
ss -lntp | grep ':8080 ' || true
```

两台均应显示 `inactive`，旧 Java 不再监听 8080。

## 7. 只读盘点 Redis 5

当前机器：`122.233.30.20`。

```bash
hostname -f
ss -lntp | grep ':6379 '
ps -ef | grep '[r]edis-server'
systemctl status redis redis-server --no-pager || true
docker ps -a --filter publish=6379 \
  --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
find /etc/redis /data /var/lib/redis \
  -maxdepth 5 \
  -type f \
  \( -name '*.conf' -o -name 'dump.rdb' -o -name 'appendonly.aof' \) \
  -ls 2>/dev/null
```

使用旧 Redis 密码查询；无密码时直接回车：

```bash
read -rsp 'Existing Redis password: ' redis5_auth
printf '\n'
if [[ -n "${redis5_auth}" ]]; then
  export REDISCLI_AUTH="${redis5_auth}"
else
  unset REDISCLI_AUTH
fi
redis-cli -h 127.0.0.1 -p 6379 PING
redis-cli -h 127.0.0.1 -p 6379 INFO server | grep '^redis_version:'
redis-cli -h 127.0.0.1 -p 6379 CONFIG GET dir
redis-cli -h 127.0.0.1 -p 6379 CONFIG GET dbfilename
redis-cli -h 127.0.0.1 -p 6379 CONFIG GET appendonly
redis-cli -h 127.0.0.1 -p 6379 CONFIG GET appenddirname
redis-cli -h 127.0.0.1 -p 6379 INFO persistence
redis-cli -h 127.0.0.1 -p 6379 INFO keyspace
```

只有 `PING` 返回 `PONG`、版本为 5.x，且已确认实际数据目录、RDB/AOF 形态才继续。

```bash
redis5_data_dir="$(
  redis-cli -h 127.0.0.1 -p 6379 --raw CONFIG GET dir |
  sed -n '2p'
)"
redis5_dbfilename="$(
  redis-cli -h 127.0.0.1 -p 6379 --raw CONFIG GET dbfilename |
  sed -n '2p'
)"
printf 'Redis data dir: %s\n' "${redis5_data_dir}"
printf 'Redis dbfilename: %s\n' "${redis5_dbfilename}"
test -n "${redis5_data_dir}"
test -n "${redis5_dbfilename}"
test -d "${redis5_data_dir}"
case "${redis5_data_dir}" in
  /data/*|/var/lib/redis|/var/lib/redis/*)
    printf 'Redis data directory accepted: %s\n' "${redis5_data_dir}"
    ;;
  *)
    printf 'Unexpected Redis data directory: %s\n' "${redis5_data_dir}" >&2
    exit 1
    ;;
esac
```

实际目录不在允许范围时停止，不根据文件名猜测。

## 8. 生成最终 RDB 和可恢复备份

当前机器：`122.233.30.20`。

```bash
redis-cli -h 127.0.0.1 -p 6379 BGSAVE
redis-cli -h 127.0.0.1 -p 6379 LASTSAVE
redis-cli -h 127.0.0.1 -p 6379 INFO persistence |
  grep -E 'rdb_bgsave_in_progress|rdb_last_bgsave_status|rdb_last_save_time'
```

重复查询直到 `rdb_bgsave_in_progress:0` 且 `rdb_last_bgsave_status:ok`。

```bash
test -f "${redis5_data_dir}/${redis5_dbfilename}"
install -d -m 0700 /data/testagent/redis-backup
redis5_backup_stamp="$(date +%Y%m%d%H%M%S)"
redis5_backup_file="/data/testagent/redis-backup/redis5-before-7.4.9-${redis5_backup_stamp}.tar.gz"
tar -C "${redis5_data_dir}" -czf "${redis5_backup_file}" .
chmod 0600 "${redis5_backup_file}"
sha256sum "${redis5_backup_file}" > "${redis5_backup_file}.sha256"
sha256sum -c "${redis5_backup_file}.sha256"
ls -lh "${redis5_backup_file}" "${redis5_backup_file}.sha256"
```

备份 SHA 不是 `OK` 时不得停止 Redis 5。

## 9. 停止 Redis 5

当前机器：`122.233.30.20`。以下三种方式只执行盘点确认的一种。

systemd `redis`：

```bash
systemctl stop redis
systemctl is-active redis || true
```

systemd `redis-server`：

```bash
systemctl stop redis-server
systemctl is-active redis-server || true
```

Docker：

```bash
docker stop <第7节确认的Redis5容器名>
docker ps -a --filter name=^/<第7节确认的Redis5容器名>$
```

确认 6379 已释放：

```bash
ss -lntp | grep ':6379 ' || true
```

## 10. 创建 Redis 7 独立数据副本

当前机器：`122.233.30.20`。

```bash
install -d -m 0700 /data/testagent/redis/data
find /data/testagent/redis/data -mindepth 1 -maxdepth 2 -ls
```

目标目录有未知文件或失败残留时先人工确认，禁止执行 `rm -rf`。首次升级且目录为空时：

```bash
test -f "${redis5_data_dir}/${redis5_dbfilename}"
install -m 0600 \
  "${redis5_data_dir}/${redis5_dbfilename}" \
  /data/testagent/redis/data/dump.rdb
test -f /data/testagent/redis/data/dump.rdb
test ! -e /data/testagent/redis/data/appendonlydir
ls -lh /data/testagent/redis/data/dump.rdb
sha256sum /data/testagent/redis/data/dump.rdb
unset REDISCLI_AUTH
unset redis5_auth
```

旧 Redis 目录和备份全部保留。

## 11. 加载并启动 Redis 7.4.9

当前机器：`122.233.30.20`。

```bash
cd /data/0709/test-agent-redis-offline
docker load -i test-agent-redis_7.4.9-alpine-linux-amd64.tar
docker image inspect test-agent-redis:7.4.9-alpine \
  --format 'os={{.Os}} arch={{.Architecture}} id={{.Id}}'
docker ps -a --filter name=^/test-agent-redis$ \
  --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
```

镜像必须显示 `os=linux arch=amd64`。部署脚本运行容器时不传 `--platform`，以兼容未启用
experimental features 的旧版 Docker daemon；脚本仍会在运行前强制校验镜像架构。不存在同名容器时：

```bash
./deploy-redis.sh \
  --env-file /data/0709/test-agent-redis-offline/config/redis.env \
  --config-file /data/0709/test-agent-redis-offline/config/redis.conf \
  --image-tar /data/0709/test-agent-redis-offline/test-agent-redis_7.4.9-alpine-linux-amd64.tar \
  deploy
```

只有确认同名容器就是已备份、已停止的旧 Redis 容器时，才允许：

```bash
./deploy-redis.sh \
  --env-file /data/0709/test-agent-redis-offline/config/redis.env \
  --config-file /data/0709/test-agent-redis-offline/config/redis.conf \
  --image-tar /data/0709/test-agent-redis-offline/test-agent-redis_7.4.9-alpine-linux-amd64.tar \
  --replace-existing \
  deploy
```

## 12. 验证 Redis 7

当前机器：`122.233.30.20`。

```bash
cd /data/0709/test-agent-redis-offline
./deploy-redis.sh \
  --env-file /data/0709/test-agent-redis-offline/config/redis.env \
  --config-file /data/0709/test-agent-redis-offline/config/redis.conf \
  status
./deploy-redis.sh \
  --env-file /data/0709/test-agent-redis-offline/config/redis.env \
  --config-file /data/0709/test-agent-redis-offline/config/redis.conf \
  verify
docker ps -a --filter name=^/test-agent-redis$ \
  --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
docker logs --tail 100 test-agent-redis
```

容器必须为 `healthy`，版本必须为 7.4.9，`PING`、`GETDEL`和迁移前后 key 数必须通过。当前平台节点包的 Redis host、port 和密码已与 Redis 包匹配，随后的平台一键部署会安装对应 `backend.env`，不需要通过聊天传递密码。

## 13. 部署后台 A `.4`

当前机器：`122.233.30.4`。

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -t test-agent-two-backend-complete.zip
unzip -oq test-agent-two-backend-complete.zip
cd /data/0709/test-agent-two-backend-complete
bash deploy-backend-node.sh
```

部署后验证：

```bash
systemctl status test-agent-backend --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
curl -fsS http://127.0.0.1:18080/xxl-job-admin/actuator/health/readiness
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost
docker ps -a --filter name=^/test-agent-opencode-worker$ \
  --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
docker port test-agent-opencode-worker | grep -E '^(4096|5095)/tcp'
docker inspect test-agent-opencode-worker \
  --format 'pids={{.HostConfig.PidsLimit}} nofile={{json .HostConfig.Ulimits}}'
docker logs --tail 200 test-agent-opencode-worker |
  grep -E 'event=manager_config_update status=applied|manager config update applied'
journalctl -u test-agent-backend --since '-10 min' --no-pager |
  grep -E 'RedisSystemException|RedisConnectionFailureException|NOAUTH|WRONGPASS' || true
```

成功条件：Java/XXL readiness 通过；`.serverid=test-agent-backend-122-233-30-4`；`.serverhost=122.233.30.4`；worker 端口首尾为 4096/5095；`PidsLimit=8192`；无 Redis 认证错误。

## 14. 部署后台 B `.114`

当前机器：`122.233.30.114`。

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -t test-agent-two-backend-complete.zip
unzip -oq test-agent-two-backend-complete.zip
cd /data/0709/test-agent-two-backend-complete
bash deploy-backend-node.sh
```

部署后验证：

```bash
systemctl status test-agent-backend --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
curl -fsS http://127.0.0.1:18080/xxl-job-admin/actuator/health/readiness
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost
docker ps -a --filter name=^/test-agent-opencode-worker$ \
  --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
docker port test-agent-opencode-worker | grep -E '^(4096|5095)/tcp'
docker inspect test-agent-opencode-worker \
  --format 'pids={{.HostConfig.PidsLimit}} nofile={{json .HostConfig.Ulimits}}'
docker logs --tail 200 test-agent-opencode-worker |
  grep -E 'event=manager_config_update status=applied|manager config update applied'
journalctl -u test-agent-backend --since '-10 min' --no-pager |
  grep -E 'RedisSystemException|RedisConnectionFailureException|NOAUTH|WRONGPASS' || true
```

成功条件：Java/XXL readiness 通过；`.serverid=test-agent-backend-122-233-30-114`；`.serverhost=122.233.30.114`；worker 端口首尾为 4096/5095；`PidsLimit=8192`；无 Redis 认证错误。

## 15. 部署前端 `.2`

当前机器：`122.233.30.2`。

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -t test-agent-two-backend-complete.zip
unzip -oq test-agent-two-backend-complete.zip
cd /data/0709/test-agent-two-backend-complete
bash deploy-frontend-node.sh
```

部署后验证：

```bash
/data/apps/nginx/sbin/nginx -t -p /data/apps/nginx -c conf/nginx.conf
curl -fsSI http://127.0.0.1:9996/
curl -fsSI http://122.233.30.2:9996/
curl -fsSI http://mimo.sdc.cs.icbc:9996/
```

Nginx 配置必须通过，入口返回正常 HTTP 200/30x，且部署入口对两个 Java 和两个 XXL Admin 的检查必须成功。

## 16. 页面配置和存量进程重启

1. 超级管理员进入“系统管理 → 配置管理 → 通用参数管理”。
2. 将全局 `OPENCODE_MANAGER_MAX_PROCESSES` 改为 `1000`。
3. 保存后进入运行管理，两台 manager 都必须显示 `portStart=4096`、`portEnd=5095`、`maxProcesses=1000`。
4. 进入“系统管理 → 配置管理 → TestAgent 公共配置管理”，确认 `.4`、`.114` 都已初始化，两台的公共 `opencode.jsonc` 都包含 `includeUsage=false`。
5. 在运行管理中受控重启存量用户 OpenCode 进程；只重启 Java 不会让已运行子进程重新读取 OpenCode 1.18.4 配置和用户隔离环境。
6. 现有内部模型供应商会继续使用迁移后的 Token；只有启用多供应商复用 Token 时才需要在页面新增/关联 Token。修改后点击“刷新 Java 内存”并确认两台 Java 都已配置。

## 17. 业务验收

1. 登录成功。
2. 首页和系统管理正常。
3. XXL-JOB 管理页正常打开。
4. 运行管理能看到 `.4/.114` 两台 Java 和两个 manager。
5. 两台 manager 都显示 `4096/5095/1000`。
6. 新建一个普通用户 OpenCode 进程并完成一次真实模型对话。
7. 切换另一用户再初始化，确认能按集群负载分配。
8. 后台无 `NOAUTH`、`WRONGPASS`、Redis 命令不支持、MySQL 或 PostgreSQL 连接错误。

## 18. 脏数据处理边界

本次不删除：

```text
/data/testagent/data
用户 session
用户 workspace
个人 worktree
manager state
用户进程 binding
PostgreSQL 数据
MySQL 数据
flyway_schema_history
Redis 5 原数据目录
/data/testagent/redis-backup
```

不手工修改 Flyway 历史。发现无主 OpenCode 进程时，在运行管理核对服务器、容器、端口、PID、用户和启动时间后单独停止，不批量删除 manager state 或用户目录。浏览器显示旧静态资源时只做强制刷新，不清数据库。

## 19. manager 与端口排查

manager 连接或端口异常时按以下顺序：

1. 确认 Java 已先启动，`/data/testagent/data/.serverid` 和 `.serverhost` 值正确。
2. 确认 `backend.env` 的 `SYS_DATA_ROOT_DIR=/data/testagent/data` 与 `docker.env` 的 `TEST_AGENT_DATA_ROOT=/data/testagent/data` 一致。
3. 确认同机 `backend.env` 和 `docker.env` 的 manager token 一致，只比较长度/哈希，不打印值。
4. 使用 `ss -lntp` 检查 4096 和 5095 的真实占用者，不直接删除 state。
5. 检查 worker 日志是否出现 `event=manager_config_update status=applied`。
6. 公共配置目录缺失或为空时，由超级管理员在 TestAgent 公共配置页初始化，不手工直接运行 `opencode-manager run`。

## 20. Redis 回滚

Redis 7 数据加载、验证或 Java 连接失败时，先停两台 Java。

`122.233.30.4`：

```bash
systemctl stop test-agent-backend
```

`122.233.30.114`：

```bash
systemctl stop test-agent-backend
```

`122.233.30.20`：

```bash
cd /data/0709/test-agent-redis-offline
./deploy-redis.sh \
  --env-file /data/0709/test-agent-redis-offline/config/redis.env \
  --config-file /data/0709/test-agent-redis-offline/config/redis.conf \
  stop
```

然后只按升级前确认的真实方式启动 Redis 5：

```bash
systemctl start redis
systemctl status redis --no-pager
```

或：

```bash
systemctl start redis-server
systemctl status redis-server --no-pager
```

或：

```bash
docker start <升级前确认的Redis5容器名>
docker ps -a --filter name=^/<升级前确认的Redis5容器名>$
```

恢复两台后台升级前的 `backend.env` 备份后，先验证 Redis 5，再依次启动 `.4`和 `.114`。不得用 Redis 7 已写过的 `/data/testagent/redis/data` 启动 Redis 5；Redis 5 必须使用原目录或升级前备份。

## 21. 容量和安全边界

- 每台 worker 映射 4096-5095 共 1000 个端口，不等于已完成 1000 进程压测。CPU、内存、`PidsLimit=8192`、`nproc=8192`、文件句柄和模型并发都要另行压测。
- Redis 离线包不含 TLS 证书；防火墙只允许 `.4`、`.114` 和授权运维源访问 6379。
- 4096-5095 只对受信内网开放，浏览器不直连这些端口。
- 平台 ZIP 含真实节点密码/token 和 JAR 内置 RSA 私钥，必须按敏感交付物限制读取、传输和留存。
