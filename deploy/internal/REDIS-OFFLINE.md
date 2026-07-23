# Redis 7.4.9 企业离线升级

本交付把当前本地开发环境实际运行的 Redis `7.4.9` 单独封装为 `linux/amd64` Docker 离线包，不修改 Java 业务代码，也不并入日常平台升级包。企业当前 Redis 5.0 升级前必须先确认部署形态、停写并完成可恢复备份；脚本不会删除任何数据目录。

## 交付内容

Mac 外网环境执行：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
./deploy/internal/package-redis-offline.sh
```

固定产物：

```text
deploy/internal/dist/test-agent-redis-offline.zip
deploy/internal/dist/test-agent-redis-offline.zip.sha256
```

ZIP 内含：

- `test-agent-redis_7.4.9-alpine-linux-amd64.tar` 及 SHA256；
- `deploy-redis.sh`、`redis-healthcheck.sh`；
- `config/redis.env`、`config/redis.conf`；
- `config/backend-redis.env`，供 `.4`、`.114` 两台后台人工合并连接密码。

配置包含随机生成的 Redis 密码，因此 ZIP、配置文件和传输介质均按敏感文件处理，权限保持 `0600`，不得粘贴到聊天、工单或日志。

## 固定部署基线

| 项目 | 值 |
|---|---|
| Redis | `7.4.9-alpine` |
| 镜像平台 | `linux/amd64` |
| 容器名 | `test-agent-redis` |
| 宿主端口 | `6379` |
| 数据目录 | `/data/testagent/redis/data` |
| 持久化 | RDB + AOF，`appendfsync everysec` |
| 内存淘汰 | `noeviction` |
| 认证 | 64 位十六进制随机密码 |
| Docker IPv4 转发 | `net.ipv4.ip_forward = 1` |

当前应用配置只启用 Redis 密码认证，包内没有企业证书，因此本包不启用 Redis TLS。必须通过企业防火墙/安全组只允许 `.4`、`.114` 和授权运维源访问 `6379`；如安全基线强制 TLS，需要另行提供证书并同步验证 Spring Redis SSL 配置，不能仅靠本包宣称满足 TLS 要求。

### Docker 网络前置条件

Redis 容器通过 `-p 6379:6379` 和 Docker DNAT 向 `.4/.114` 提供服务。Redis 容器 `healthy`、本机访问
`122.233.30.20:6379` 成功以及宿主机显示 `0.0.0.0:6379`，都不能替代跨机检查；如果
`net.ipv4.ip_forward=0`，远端 SYN 可以到达 `.20`，但不会转发到容器，Java 会出现
`Connect timed out`、readiness `DOWN` 或 systemd 反复重启。此时新增 `INPUT` 放行规则无效，因为
Docker DNAT 后的流量走 `FORWARD/DOCKER-USER`。

在 Redis 服务器 `.20` 开始停机升级前必须执行：

```bash
sysctl net.ipv4.ip_forward
test "$(sysctl -n net.ipv4.ip_forward)" = "1"
```

不是 `1` 时先临时恢复并确认：

```bash
sysctl -w net.ipv4.ip_forward=1
```

再检查是否有企业基线把它设置回 `0`，通过企业批准的 sysctl 配置持久化；不得只依赖本次临时值：

```bash
grep -RnsE \
  '^[[:space:]]*net\.ipv4\.ip_forward[[:space:]]*=' \
  /etc/sysctl.conf /etc/sysctl.d /usr/lib/sysctl.d \
  2>/dev/null || true
```

当前 `deploy-redis.sh deploy` 和 `verify` 都会在访问 Docker 前强制检查该值并失败提示，但脚本不会
擅自修改宿主机网络策略。Redis 启动并通过本机 `verify` 后，必须分别在 `.4`、`.114` 执行：

```bash
timeout 3 bash -c '</dev/tcp/122.233.30.20/6379'
echo $?
```

两台都必须返回 `0`，否则不得启动 Java。若 `.20` 抓包能看到远端 SYN、却没有 SYN-ACK，先检查
`net.ipv4.ip_forward`，再检查 `FORWARD/DOCKER-USER`；完全看不到 SYN 才转交网络侧排查 VLAN/ACL。

## 1. 在中转机校验并传到 Redis 服务器

以下示例沿用当前部署清单中的 Redis 地址 `122.233.30.20`；如果现场实际地址不同，先修正目标地址，禁止照抄部署到未知主机。

在企业中转机执行：

```bash
cd ~/Desktop/mimoagent/0709
sha256sum -c test-agent-redis-offline.zip.sha256
unzip -t test-agent-redis-offline.zip
ssh root@122.233.30.20 'install -d -m 0755 /data/0709'
scp ~/Desktop/mimoagent/0709/test-agent-redis-offline.zip \
  ~/Desktop/mimoagent/0709/test-agent-redis-offline.zip.sha256 \
  root@122.233.30.20:/data/0709/
```

中转机固定使用 `~/Desktop/mimoagent/0709`，不在中转机创建或使用 `/data/0709`；后者只是 Redis 目标服务器的接收目录。

在 `122.233.30.20` 执行：

```bash
cd /data/0709
sha256sum -c test-agent-redis-offline.zip.sha256
unzip -oq test-agent-redis-offline.zip
cd /data/0709/test-agent-redis-offline
sha256sum -c test-agent-redis_7.4.9-alpine-linux-amd64.tar.sha256
./deploy-redis.sh --env-file ./config/redis.env --config-file ./config/redis.conf \
  --image-tar ./test-agent-redis_7.4.9-alpine-linux-amd64.tar validate
```

只看到两次校验 `OK` 和 `Redis configuration validation passed` 才继续。

## 2. 升级前只读盘点 Redis 5

先判断 Redis 5 是 systemd、裸进程还是容器部署，确认配置文件、数据目录、RDB/AOF 和密码来源：

```bash
hostname -f
ss -lntp | grep ':6379 '
ps -ef | grep '[r]edis-server'
systemctl status redis redis-server --no-pager || true
docker ps -a --filter publish=6379 --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
find /etc/redis /data -maxdepth 4 -type f \( -name '*.conf' -o -name 'dump.rdb' -o -name 'appendonly.aof' \) -ls 2>/dev/null
```

使用现有密码进行只读查询时，不把密码写进命令行历史：

```bash
read -rsp 'Existing Redis password: ' redis5_auth
printf '\n'
if [[ -n "${redis5_auth}" ]]; then
  export REDISCLI_AUTH="${redis5_auth}"
else
  unset REDISCLI_AUTH
fi
redis-cli -h 127.0.0.1 -p 6379 INFO server | grep '^redis_version:'
redis-cli -h 127.0.0.1 -p 6379 CONFIG GET dir
redis-cli -h 127.0.0.1 -p 6379 CONFIG GET dbfilename
redis-cli -h 127.0.0.1 -p 6379 CONFIG GET appendonly
redis-cli -h 127.0.0.1 -p 6379 CONFIG GET appenddirname
redis-cli -h 127.0.0.1 -p 6379 INFO persistence
unset REDISCLI_AUTH
unset redis5_auth
```

如果命令显示的实际数据目录、AOF 形态或 Redis 主机与本文不一致，先停在这里记录现场结构，不要执行替换。

## 3. 停止写入并备份

Redis 是 `.4` 与 `.114` 两个后台的共享必需依赖，升级窗口内两个后台都要停止。分别登录两台后台执行：

```bash
systemctl stop test-agent-backend
systemctl is-active test-agent-backend || true
ss -lntp | grep ':8080 ' || true
```

回到 Redis 服务器，使用现有密码生成最终 RDB：

```bash
read -rsp 'Existing Redis password: ' redis5_auth
printf '\n'
if [[ -n "${redis5_auth}" ]]; then
  export REDISCLI_AUTH="${redis5_auth}"
else
  unset REDISCLI_AUTH
fi
redis-cli -h 127.0.0.1 -p 6379 BGSAVE
redis-cli -h 127.0.0.1 -p 6379 LASTSAVE
redis-cli -h 127.0.0.1 -p 6379 INFO persistence | grep -E 'rdb_bgsave_in_progress|rdb_last_bgsave_status'
unset REDISCLI_AUTH
unset redis5_auth
```

等待 `rdb_bgsave_in_progress:0` 且 `rdb_last_bgsave_status:ok`。随后按第 2 步确认的真实数据目录制作带时间戳的只读备份，并由运维人员确认恢复路径。下面仅是结构示例，`<旧Redis数据目录>` 必须替换成已确认的精确绝对路径：

```bash
install -d -m 0700 /data/testagent/redis-backup
tar -C <旧Redis数据目录> -czf /data/testagent/redis-backup/redis5-before-7.4.9.tar.gz .
sha256sum /data/testagent/redis-backup/redis5-before-7.4.9.tar.gz \
  > /data/testagent/redis-backup/redis5-before-7.4.9.tar.gz.sha256
sha256sum -c /data/testagent/redis-backup/redis5-before-7.4.9.tar.gz.sha256
```

未确认备份可恢复时不要停止 Redis 5，不要执行下一步。

## 4. 准备 Redis 7 数据副本

先停止 Redis 5 的真实服务单元或容器。以下三条只执行与现场形态匹配的一条：

```bash
systemctl stop redis
systemctl stop redis-server
docker stop <第2步确认的Redis5容器名>
```

确认 `6379` 已释放：

```bash
ss -lntp | grep ':6379 ' || true
```

把最终 `dump.rdb` 从备份恢复到新的、独立且没有 `appendonlydir` 的数据目录。不要让 Redis 7 直接写 Redis 5 原目录：

```bash
install -d -m 0700 /data/testagent/redis/data
install -m 0600 <已确认的最终dump.rdb路径> /data/testagent/redis/data/dump.rdb
ls -l /data/testagent/redis/data/dump.rdb
test ! -e /data/testagent/redis/data/appendonlydir
```

如果 Redis 5 只有 AOF、没有可用的最终 RDB，先回滚启动 Redis 5 并完成 `BGSAVE`；不要把未知 AOF 目录直接交给新容器试错。

## 5. 加载并启动 Redis 7.4.9

仍在 `122.233.30.20` 执行：

```bash
cd /data/0709/test-agent-redis-offline
docker load -i test-agent-redis_7.4.9-alpine-linux-amd64.tar
docker image inspect test-agent-redis:7.4.9-alpine \
  --format 'os={{.Os}} arch={{.Architecture}} id={{.Id}}'
sysctl net.ipv4.ip_forward
./deploy-redis.sh --env-file ./config/redis.env --config-file ./config/redis.conf \
  --image-tar ./test-agent-redis_7.4.9-alpine-linux-amd64.tar deploy
```

部署脚本会先用 `docker image inspect` 强制确认镜像为 `linux/amd64`，运行容器时不再传递
`--platform`，兼容未启用 experimental features 的旧版 Docker daemon；这不会放宽镜像架构校验。
脚本还会强制确认 `net.ipv4.ip_forward=1`，避免本机 Redis 验证正常、远端后台却持续超时。
配置文件继续保持宿主机 `0600`。脚本启动容器时会在容器内复制配置并用 `setpriv` 切换到
`redis` 用户，避免 Linux bind mount 因 UID 权限报 `permission denied`，不需要把含密码配置改成 `0644`。

如果现场正在使用旧版脚本且已经出现配置文件权限错误，先临时准备运行时配置副本：

```bash
install -d -m 0700 /data/testagent/redis/config
install -m 0400 /data/0709/test-agent-redis-offline/config/redis.conf \
  /data/testagent/redis/config/redis.conf
chown 999:1000 /data/testagent/redis/config/redis.conf
```

随后把 `--config-file` 改为 `/data/testagent/redis/config/redis.conf` 重试；原始包内配置不改权限。

如果现场已有同名容器，脚本会拒绝覆盖。确认它就是已备份并停止的旧容器后，才允许显式执行：

```bash
./deploy-redis.sh --env-file ./config/redis.env --config-file ./config/redis.conf \
  --image-tar ./test-agent-redis_7.4.9-alpine-linux-amd64.tar \
  --replace-existing deploy
```

检测到只有 `dump.rdb` 时，部署脚本会先以关闭 AOF 的方式加载该副本，再动态生成 Redis 7 multipart AOF，重启后核对迁移前后所有 DB 的 key 总数；任一步失败都会停止，不会把“空库但容器健康”当成成功。部署成功还会验证 `PONG`、服务版本和项目依赖的 `GETDEL`，且不会读取业务 key。继续检查：

```bash
./deploy-redis.sh --env-file ./config/redis.env --config-file ./config/redis.conf status
./deploy-redis.sh --env-file ./config/redis.env --config-file ./config/redis.conf verify
docker logs --tail 100 test-agent-redis
```

## 6. 更新两台后台密码并恢复服务

`config/backend-redis.env` 与本次 Redis 配置使用同一密码。在 Redis 服务器上以受控方式分别传给 `.4`、`.114`，不要通过聊天复制：

```bash
scp /data/0709/test-agent-redis-offline/config/backend-redis.env root@122.233.30.4:/data/0709/backend-redis.env
scp /data/0709/test-agent-redis-offline/config/backend-redis.env root@122.233.30.114:/data/0709/backend-redis.env
```

分别在 `.4`、`.114` 核对文件权限和三个键，再将值人工合并到 `/data/testagent/config/backend.env`。不要直接覆盖完整后台配置：

```bash
chmod 0600 /data/0709/backend-redis.env
grep -E '^TEST_AGENT_REDIS_(HOST|PORT)=' /data/0709/backend-redis.env
grep -E '^TEST_AGENT_REDIS_PASSWORD=' /data/0709/backend-redis.env | sed 's/=.*/=<redacted>/'
```

先在 `.4` 启动并检查，再在 `.114` 重复：

```bash
systemctl start test-agent-backend
systemctl status test-agent-backend --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
journalctl -u test-agent-backend --since '-5 min' --no-pager | \
  grep -E 'RedisSystemException|RedisConnectionFailureException|NOAUTH|WRONGPASS' || true
```

两台 readiness 都通过后，再验证登录、XXL-JOB 管理页和一次正常业务操作。浏览器的 `adminTab` JavaScript 异常与 Redis 服务异常是两条独立问题链；Redis 升级只处理后端 `RedisSystemException: Error in execution` 的兼容/运行依赖，不替代浏览器兼容修复。

## 回滚

一旦 Redis 7 启动、数据加载或后台验证失败：

1. 停止两台后台；
2. 在 Redis 服务器执行 `./deploy-redis.sh ... stop`；
3. 恢复原 Redis 5 服务和第 3 步备份；
4. 将两台 `backend.env` 恢复为原 Redis 密码；
5. 先验证 Redis 5，再依次启动 `.4`、`.114`。

不要用 Redis 7 已写过的数据目录直接启动 Redis 5；回滚必须使用升级前备份。
