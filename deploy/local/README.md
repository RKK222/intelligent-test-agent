# 本地 Jenkins 发布

本目录负责 `192.168.8.100` 测试机的 Jenkins 发布。它不替代 `deploy/internal/` 的企业离线包，也不修改企业
`backend.env`、`docker.env` 或节点拓扑。目标分支固定为 `release`，Jenkins 地址为
`http://192.168.8.100:18081`，任务名固定为 `intelligent-test-agent-release`。

## 复用边界

- 后端继续使用根 Maven reactor、`test-agent-app` 可执行 JAR 和现有 Flyway migration；前端继续使用
  `frontend` pnpm workspace 与 `agent-web` 生产构建。
- 运行配置从测试机已有 `/data/offload/home/abc/intelligent-test-agent-dev/.env.test` 受控复制到
  `/data2/deploy/intelligent-test-agent/shared/runtime.env`，权限只能为 `0640 root:jenkins` 或更严格。流水线不
  输出、归档或写入 Git 任何密码、Token 或数据库连接密钥。
- 前端固定使用带 git 的 `node:22.16.0-bookworm` 构建镜像；VitePress 会读取页面对应的 Git 提交时间，不能
  换成不含 git 的 slim 镜像。
- 100 测试机的前端仍执行生产优化构建，但构建容器固定注入 `VITE_ENV=localhost`，因此显示现有账号密码登录页，
  不跳转 AAM。该例外只用于本机 Jenkins 测试制品；企业离线包和其他 production 构建继续使用 AAM 默认，不得复制此参数。
- 后端验证和正式运行固定复用 `maven:3.9.9-eclipse-temurin-21`；体验工作区和应用资产会在运行期调用 Git，
  不能换成不含 git 的纯 JRE 镜像。流水线在宿主门禁中同时检查镜像内的 Java 和 Git。
- 现场 `127.0.0.1:16379/13306` 由 MockCenter 占用；正式容器显式把 Redis 与 XXL MySQL 主机覆盖为
  `192.168.8.100`，继续从受控 `runtime.env` 读取端口、库名、用户和密钥，不能把密钥写进 Compose 或 Git。
- 现场 `18080` 同样由 MockCenter 占用；Jenkins 发布实例把内置 XXL Admin 固定到 `18083`，并让前端
  `/xxl-job-admin/` 同源代理与发布健康门禁使用同一端口，不停止或修改 MockCenter。
- Java 按现场配置监听服务器 LAN 地址而不是 Docker bridge gateway；Nginx 的 `/api/` 与 `/xxl-job-admin/`
  统一代理到 `192.168.8.100` 对应端口，不能改回解析为 `172.17.0.1` 的 `host.docker.internal`。
- 正式 Java 容器用 `SERVER_ADDRESS=0.0.0.0` 同时满足 LAN 入口和同 JVM executor 对 Admin 的 loopback readiness；
  对集群公布的身份仍固定为 `TEST_AGENT_SERVER_ADVERTISED_HOST=192.168.8.100`。executor 使用 `9999`，发布门禁必须
  等到该端口真实监听，不能只验证主服务和 Admin。
- 后端 JAR 结构校验复用固定 Maven JDK 21 构建镜像，Jenkins 宿主只需 Jenkins 自身的 Java 运行时，不要求
  额外安装 JDK `jar` 命令。
- 现有 `abc` 工作树保持原样。首次成功发布只停止该工作树占用 `18082` 的 Java 和占用 `3000` 的 Vite；
  `4096` OpenCode 进程、数据库 `SYS_DATA_ROOT_DIR` 指向的 `/data/.testagent` 数据根和未提交文件均保留。
- 企业离线发布仍按 `deploy/internal/README.md` 执行；不能把本地 Jenkins 产物上传到企业内替代标准离线包。

## 任务结构

流水线参考同机 `precisiontesttool-release` 的人工发布模型：禁并发、保留 30 次记录、`DEPLOY/ROLLBACK` 参数、
不可变 `release-{BUILD_NUMBER}-{commit前8位}` 标签、发布清单、日志留存和发布后验证。差异在于本项目直接在
Jenkins 所在测试机用 Docker Compose 管理两个容器，不经过 Portainer：

| 组件 | 容器 | 入口 | 数据 |
|---|---|---|---|
| Java 后端 | `test-agent-jenkins-backend` | `http://192.168.8.100:18082` | 宿主 `/data/.testagent` 原路径挂载，必须与数据库 Linux 平台 `SYS_DATA_ROOT_DIR` 完全一致 |
| XXL Admin / executor | Java 后端内的 Servlet 子上下文与调度执行器 | `http://192.168.8.100:3000/xxl-job-admin/`（同源代理到 `18083`）/ `192.168.8.100:9999` | 复用受控 XXL MySQL 配置 |
| agent-web | `test-agent-jenkins-frontend` | `http://192.168.8.100:3000` | 不落业务数据 |
| OpenCode | 现有 `abc` 进程 | `http://127.0.0.1:4096` | Jenkins 不停止、不重建 |

发布目录位于 `/data2/deploy/intelligent-test-agent/releases/`，每个标签包含源码快照、后端 JAR、前端静态文件、
Compose 模型、逐文件 SHA-256 和发布前后 Flyway history。日志位于
`/data2/deploy/intelligent-test-agent/logs/`；共享缓存继续复用 `/data2/deploy/shared/`。
源码快照整体只读挂载，`source/backend/logs` 与 `source/temp` 仅作为预建的嵌套挂载点，实际写入分别落到受控
日志目录和运行时临时目录，不回写不可变发布源码。

## 数据库门禁

`DEPLOY` 在停止旧进程前先完成两层检查：

1. 运行 `FlywayMigrationNamingTest`，锁定 migration 命名、重复版本和已冻结文件字节。
2. 从受控 `runtime.env` 读取当前应用数据库名和角色（测试基线为 `testagent_dev`），在 `test-agent-postgres` 内做
   一致性逻辑复制；临时库归应用角色所有并以该角色恢复对象权限，再配合独立 Redis 使用本次后端 JAR 完成真实
   PostgreSQL 升级与 readiness 检查。门禁先读取克隆源库的 Linux `SYS_DATA_ROOT_DIR`，要求正式挂载目标与其
   完全一致；验证数据根再挂载到相同容器路径，并复用发布源码中的体验工作区模板脚本建立独立 Git 仓库，既覆盖
   启动契约，也不会读取或修改真实运行数据。验证容器、网络和临时库随后按精确名称清理；源库只读。

验证后端只在 Docker bridge 内把 `SERVER_ADDRESS` 覆盖为 `0.0.0.0`，并复用克隆库已有的 Linux server ID，避免
把宿主绑定地址或虚构 server ID 带入临时环境；数据库通用参数仍是路径权威来源，不能用同名环境变量覆盖。
这些验证覆盖不进入正式发布容器。

升级验证失败时不得执行宿主接管。正式启动前再次保存源库全部
`installed_rank/version/description/checksum/success`；新后端 readiness 通过后保存升级后 history。未知 checksum、
Flyway validate 失败或 readiness 失败时任务失败，不执行 `repair`、`outOfOrder` 或手工修改 history。

## Jenkins 主机一次性配置

服务器需要满足：

- 私有 GitLab 项目 `wrui/intelligent-test-agent`，Jenkins 使用只读 Deploy Key；凭据 ID 固定为
  `intelligent-test-agent-git-ssh`。
- `/usr/local/sbin/test-agent-jenkins-host-control` 是仓库脚本的 root 所有固定副本；sudoers 只允许 Jenkins 无密码
  执行它的 `status` 和 `stop-legacy`，不能允许 Jenkins 以 root 执行工作区脚本。
- `jenkins` 属于 `docker` 组；发布、日志、共享缓存目录归 `jenkins:jenkins`，运行时临时目录和后端日志允许
  UID `1000` 的 `abc` 容器用户写入。由于 `/data2` 是启用权限检查的 FUSE 合并盘，
  `/data2/deploy/intelligent-test-agent`、`releases` 和 `shared` 必须至少允许其他用户穿越（推荐 `0751`）；
  发布清单会用真实运行 UID 挂载并读取 JAR，父目录权限不满足时在停止旧服务前失败。
- Jenkins 任务从 SCM 读取根 `Jenkinsfile`，分支固定为 `*/release`，不得改为 `latest`、任意分支或工作区内联脚本。

一次性配置完成后，日常发布只在 Jenkins 参数页选择 `ACTION=DEPLOY`。回滚必须填写已经存在且清单校验通过的
不可变标签；数据库 migration 只前进不后退，回滚前仍需确认旧应用对当前数据库向后兼容。

## 验证

仓库自检：

```bash
tools/verify-jenkins-release.sh
```

测试机发布后：

```bash
curl -fsS http://192.168.8.100:18082/actuator/health/readiness
curl -fsS http://192.168.8.100:18083/xxl-job-admin/actuator/health/readiness
curl -fsS http://192.168.8.100:3000/xxl-job-admin/actuator/health/readiness
python3 -c 'import socket; socket.create_connection(("192.168.8.100", 9999), timeout=2).close()'
curl -fsS http://192.168.8.100:3000/
docker ps --filter name=test-agent-jenkins-
```

成功条件是后端与 XXL Admin 两个 readiness 均返回 `{"status":"UP"}`、前端及其 XXL 同源代理可访问、executor
`9999` 可建立 TCP 连接、两个固定容器均为 `Up`，并且 Jenkins 构建页最终状态为 `SUCCESS`。后端首次启动可能需要
完成 Flyway、配置加载和 Git 初始化，流水线最多等待 240 秒；等待超时或容器停止才判失败，不能在
`docker compose up` 后只做一次瞬时探测。
