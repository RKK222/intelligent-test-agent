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
- 后端 JAR 结构校验复用固定 Maven JDK 21 构建镜像，Jenkins 宿主只需 Jenkins 自身的 Java 运行时，不要求
  额外安装 JDK `jar` 命令。
- 现有 `abc` 工作树保持原样。首次成功发布只停止该工作树占用 `18082` 的 Java 和占用 `3000` 的 Vite；
  `4096` OpenCode 进程、`/home/abc/intelligent-test-agent-dev/.testagent` 数据根和未提交文件均保留。
- 企业离线发布仍按 `deploy/internal/README.md` 执行；不能把本地 Jenkins 产物上传到企业内替代标准离线包。

## 任务结构

流水线参考同机 `precisiontesttool-release` 的人工发布模型：禁并发、保留 30 次记录、`DEPLOY/ROLLBACK` 参数、
不可变 `release-{BUILD_NUMBER}-{commit前8位}` 标签、发布清单、日志留存和发布后验证。差异在于本项目直接在
Jenkins 所在测试机用 Docker Compose 管理两个容器，不经过 Portainer：

| 组件 | 容器 | 入口 | 数据 |
|---|---|---|---|
| Java 后端 | `test-agent-jenkins-backend` | `http://192.168.8.100:18082` | 宿主真实路径 `/data/offload/home/abc/intelligent-test-agent-dev/.testagent` 挂载为原逻辑路径 `/home/abc/intelligent-test-agent-dev/.testagent` |
| agent-web | `test-agent-jenkins-frontend` | `http://192.168.8.100:3000` | 不落业务数据 |
| OpenCode | 现有 `abc` 进程 | `http://127.0.0.1:4096` | Jenkins 不停止、不重建 |

发布目录位于 `/data2/deploy/intelligent-test-agent/releases/`，每个标签包含源码快照、后端 JAR、前端静态文件、
Compose 模型、逐文件 SHA-256 和发布前后 Flyway history。日志位于
`/data2/deploy/intelligent-test-agent/logs/`；共享缓存继续复用 `/data2/deploy/shared/`。

## 数据库门禁

`DEPLOY` 在停止旧进程前先完成两层检查：

1. 运行 `FlywayMigrationNamingTest`，锁定 migration 命名、重复版本和已冻结文件字节。
2. 从 `test-agent-postgres` 当前 `test_agent` 数据库做一致性逻辑复制，创建唯一临时库和独立 Redis，使用本次
   后端 JAR 完成真实 PostgreSQL 升级与 readiness 检查。验证容器、网络和临时库随后按精确名称清理；源库只读。

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
  UID `1000` 的 `abc` 容器用户写入。
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
curl -fsS http://192.168.8.100:3000/
docker ps --filter name=test-agent-jenkins-
```

成功条件是后端 readiness 返回 `{"status":"UP"}`、前端返回 HTTP 200、两个固定容器均为 `Up`，并且 Jenkins
构建页最终状态为 `SUCCESS`。
