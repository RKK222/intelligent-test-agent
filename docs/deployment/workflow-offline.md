# Python 长程任务企业离线部署

## 架构与交付边界

```text
Browser /workflow-chat
  -> Nginx /workflow-api/ -> workflow-service API
  -> Java /api/**          -> 现有平台能力

workflow-service Worker -> Runner controller -> 每任务一个 analysis-task 容器
workflow-service/Worker  -> 独立 test_agent_workflow PostgreSQL
workflow-service API     -> 平台 Redis 只读 Token ACL
Python/Runner            -> Java workflow capability HMAC API
analysis-task            -> 唯一白名单模型网关
```

Java 不代理 workflow HTTP/SSE。API/Worker 不挂 Docker Socket；只有独立分析节点 Runner 可以挂载。部署不使用 Docker Compose。

## 制品

在 Mac 或构建节点执行：

```bash
deploy/internal/package-workflow-offline.sh \
  --env-file deploy/internal/workflow/build.env \
  --output-dir deploy/internal/dist
```

构建目标固定 `linux/amd64`，输出 `test-agent-workflow-offline-VyyyyMMdd.HHmmss.tar.gz`，包含：

- `workflow-service`、`runner-controller`、`analysis-task` 三个 image tar。
- 三个 SPDX JSON SBOM、Python许可证清单、完整 `uv.lock`、Dockerfile、分析入口与固定输出清理助手。
- image inspect 元数据、基础/工具镜像 digest 与最终 image ID。
- `SHA256SUMS` 和纯 Docker 管理/网络/数据库/Redis ACL 脚本。

基础 Python、uv、Syft 镜像必须以 `@sha256` 固定；预装 Codex/OpenCode 工具镜像必须同时固定本地 tag 与完整 image ID。脚本拒绝覆盖同版本产物。

## 数据库

使用现有 PostgreSQL 集群中的独立数据库和最小权限账号。管理员先审阅并执行：

```bash
psql -v workflow_password='强随机密码' -f deploy/internal/workflow/bootstrap-workflow.sql
```

运行账号只拥有 `test_agent_workflow`；迁移账号通过 `TEST_AGENT_WORKFLOW_MIGRATION_DATABASE_URL` 临时注入。初始化顺序：

```bash
deploy/internal/workflow/workflow-docker.sh initialize \
  --role control \
  --env-file /data/testagent/config/workflow.env \
  --release-env /data/testagent/releases/workflow/release.env
```

该命令依次执行 Alembic 与 LangGraph checkpoint setup。独立数据库不使用 Java Flyway/MyBatis，不修改平台 `flyway_schema_history`。

## Redis Token 只读账号

以环境变量从 stdin 注入管理员与新账号密码后执行：

```bash
deploy/internal/workflow/redis-workflow-acl.sh
```

ACL 只允许 `PING`、`GET`、`PTTL` 和 `~test-agent:token:*`；脚本会反向验证 `SCAN/KEYS/SET` 与其他前缀均被拒绝。生产 `workflow.env` 使用该独立账号 URL，不使用平台 Redis 管理账号。

Java 内部的 checkout、nonce 和模型 grant 使用现有 Java Redis连接及独立 `test-agent:workflow-capability:*` 前缀，不属于 Python只读ACL。

## 密钥与信任材料

至少配置：

- Python ↔ Java 的 32 字节以上 workflow HMAC 密钥。
- Worker ↔ Runner 的独立 32 字节以上 HMAC 密钥。
- Runner ↔ Java ticket 兑换的独立 32 字节以上 HMAC 密钥。
- Runner RSA 公私钥；Java与Python只读公钥，Runner私钥文件由容器 uid `10003` 拥有且模式 `0400/0600`。
- Git `known_hosts` 非空普通文件，模式 `0444/0644`，不得是符号链接。
- workflow PostgreSQL、Redis ACL账号密码。

Java侧的 `TEST_AGENT_WORKFLOW_CAPABILITY_HMAC_SECRET` 必须等于Python侧
`TEST_AGENT_WORKFLOW_PLATFORM_HMAC_SECRET`；Java侧
`TEST_AGENT_WORKFLOW_RUNNER_PLATFORM_HMAC_SECRET` 必须等于Runner侧
`TEST_AGENT_RUNNER_PLATFORM_HMAC_SECRET`。两组值分别写入 `backend.env` 和
`workflow.env`，不为了校验方便把Java专用别名复制进Python/Runner容器。三条服务通道的密钥必须彼此不同。
Java的Runner ID、公钥路径和返回给分析容器的模型网关URL也必须在
`backend.env` 中显式配置，示例见 `deploy/internal/backend.env.example`；独立workflow
离线包同时携带可追加到Java配置的 `deploy/java-capability.env.example` 片段。

Workflow 到 Java、Worker 到 Runner、Runner 到 Java 的固定控制面 HTTP 调用不会继承宿主机的
HTTP(S)/SOCKS 代理配置。部署必须为这些地址提供直接路由、DNS 和 TLS 信任，不能依赖系统代理转发内部签名请求。

`workflow.env`中的`TEST_AGENT_WORKFLOW_INTENT_MODEL_NAME`、
`TEST_AGENT_WORKFLOW_SYNTHESIS_MODEL_NAME`、`TEST_AGENT_WORKFLOW_REPORT_QA_MODEL_NAME`和
`TEST_AGENT_WORKFLOW_ANALYSIS_MODEL_NAME`都必须是平台模型网关`/models`返回的公开模型ID。
其中analysis模型会同时固定给Codex与OpenCode：Codex使用`responses`，OpenCode使用
OpenAI-compatible接口；工具配置只保存回环relay地址和一次性本地token，不保存平台grant。上线前必须分别验证两种接口与该模型兼容。
真实分析容器验收还必须确认relay以UID `10002`运行、智能体以UID `10001`运行：平台grant只经relay stdin传入，智能体的环境、请求文件、命令行、`/proc`读取、工具日志和结果都不能出现grant；relay仅监听`127.0.0.1`，停止生命线stdin后端口立即关闭。OpenCode必须实际使用镜像内`test-agent-safe-shell`，不得回退到系统默认shell；任一探针失败都阻断交付。
双/三智能体验收需证明同任务调用按顺序进入容器，并在每次调用后执行容器重启；前一智能体刻意遗留的后台进程必须消失，未执行智能体的输出目录不可列举、读取或改名，而冻结源码挂载、当前智能体独立HOME/输出和后续综合仍可继续。创建后、每次模型执行前、每次调用后重启及保留恢复后都要立即复核容器处于运行态、冻结镜像digest、精确源码/输出挂载、受限网络、CPU/内存/PID/nofile/tmpfs、非root、只读根、cap-drop与no-new-privileges，任一约束丢失都阻断任务。重启复核与后续强制删除同时失败时，Runner本地状态及Python控制库租约必须收敛为可重试的`CLEANUP_FAILED`，不能遗留`ACTIVE`。

dotenv 与密钥不进入离线包、Git、命令行或日志。`workflow-docker.sh` 把 env、release manifest、公私钥和 known_hosts 的哈希纳入容器配置摘要；任何信任材料变化都会重建精确容器。

## 分析网络

`analysis-network.sh` 在 Docker 18.09 的宿主机创建 `test-agent-analysis-*` 私有桥接网络，并建立独立 iptables chain：

1. 放行 `ESTABLISHED,RELATED`。
2. 仅放行固定模型网关 IPv4/CIDR 与端口。
3. 拒绝其他出站。

来自分析子网的 jump 必须是 `DOCKER-USER` 第一条规则，防止更早的 ACCEPT 绕过。Runner 每次就绪与创建任务前还会复核网络 driver、scope、subnet 和三个固定标签。模型网关 URL 必须使用落在白名单内的数字 IPv4 与相同端口；Runner 管理端口不能被任务子网访问。

应用规则：

```bash
deploy/internal/workflow/analysis-network.sh \
  --env-file /data/testagent/config/workflow.env \
  --apply
```

已有分析容器时脚本拒绝重写防火墙。

## 启动与 Nginx

控制节点：

```bash
deploy/internal/workflow/workflow-docker.sh start --role control \
  --env-file /data/testagent/config/workflow.env \
  --release-env /data/testagent/releases/workflow/release.env
```

分析节点：

```bash
deploy/internal/workflow/workflow-docker.sh start --role runner \
  --env-file /data/testagent/config/workflow.env \
  --release-env /data/testagent/releases/workflow/release.env
```

Nginx 配置 `TEST_AGENT_NGINX_WORKFLOW_UPSTREAM` 后，`configure-nginx.sh` 生成 `/workflow-api/` 到 Python 的独立 upstream；该 location 保留 Authorization、关闭响应缓冲并支持长 SSE 读取，不落到 Java backend upstream。

## 发布顺序

1. 创建 workflow PostgreSQL库/账号并配置 Redis只读ACL。
2. 发布 Java共享能力接口与支持 workflow grant 的模型网关。
3. 执行 Alembic/checkpoint初始化，启动 workflow API/Worker。
4. 配置受限网络并启动 Runner。
5. 发布前端与 Nginx直达路由。

停止 Runner 前必须确认没有 `test-agent-analysis-*` 任务容器；脚本会阻断带活动任务的停止。

## 验收

自动契约：

```bash
tools/verify-workflow-architecture.sh
tools/verify-internal-nginx-config.sh
deploy/internal/tests/workflow-offline-test.sh
```

正式环境还必须完成：

- 真实 PostgreSQL 的迁移、唯一约束、租约接管、checkpoint恢复和清理状态。
- 企业 Redis 5 的 HMAC nonce、ticket一次消费、模型grant刷新/撤销墓碑及Token ACL。
- 真实 Docker 18.09 的非root、只读根、cap-drop、no-new-privileges、资源限制、只读源码、无Docker Socket与网络阻断。
- 真实 Docker 18.09 上验证智能体间输出隔离，以及正常退出、强制取消和到期清理都能删除含 `0700/000` 私有目录的源码、session与原始日志；任何残留必须得到 `CLEANUP_FAILED`，不能报告成功。
- 真实模型网关上的 Codex、OpenCode、双/三智能体和单智能体失败场景。
- 注入一次及持续的 workflow PostgreSQL 工作区状态写入失败：一次失败必须重试后收敛；持续失败不得终态化 run，租约到期接管后必须最终写入 `CLEANUP_FAILED` 并进入清理队列。
- 至少两个跨应用仓库的联合分析、根目录及嵌套 `.gitattributes` 的 LFS（必须基于已冻结的当前 detached ref 完整拉取）、授权/未授权submodule、空diff与局部重分析。
- 浏览器 Authorization fetch SSE、快照、Last-Event-ID续传、登出断流和Markdown报告。

若真实 Docker 18.09 下 Codex/OpenCode 无法在非特权容器稳定运行，必须阻断交付，不能切换到 root、`--privileged`、开放网络或挂载Docker Socket。

## 保留与回滚

- 初次/局部分析完成后停止容器，分析或有效期内报告追问结束后滚动保留48小时；恢复只接受 `STOPPED_RETAINED` 并重新检查容器安全约束。控制面按数据库过期租约调用 Runner 清理，Runner 在任务锁内复读并先关闭活动状态，以成功的精确列表查询区分“不存在”和Docker故障；先停止当前分析，等待执行锁释放以淘汰排队请求，再次停止容器后才放宽输出权限、执行固定权限恢复助手并验证整树删除。失败记录 `CLEANUP_FAILED` 并重试，已过期工作区不会被追问复活。
- `workflow.env` 只是部署输入；启动脚本会生成 mode `0600` 的按进程白名单临时文件。数据库 owner URL 只进入一次性初始化容器，API/Worker只持有运行账号，Runner不会收到数据库、Redis或Python到Java能力密钥。
- 主动取消并行写模型 run 撤销墓碑/撤销现有grant和删除容器、源码、输出；删除成功把租约收敛为 `EXPIRED`，Runner 调用失败则立即写带到期时间的 `CLEANUP_FAILED`，由 Worker 持久重试。重复取消既有 `CANCELED` run 可安全重试，不能改写其他终态。
- 过期后删除容器、源码、工具原始日志和临时凭据；对话、结构化结果和Markdown报告长期保留。
- 回滚前停止新入口与Worker，等待或取消活动任务，再回滚前端/Nginx、Runner、Python、Java。Alembic生产回滚默认采用前向修复，不在有数据环境盲目执行 downgrade。
