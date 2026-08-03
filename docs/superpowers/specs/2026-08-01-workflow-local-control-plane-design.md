# 本地工作流控制面启动设计

## 背景

`/workflow-chat` 已通过 Vite 将 `/workflow-api/**` 代理到 Python 工作流服务。当前本地开发重启链只启动 Java、OpenCode Manager 和前端，没有初始化或启动 Python 工作流服务，因此浏览器访问 `127.0.0.1:8090` 时得到连接拒绝，前端最终显示“工作流服务暂不可用（HTTP 502）”。

工作流生产部署已经包含独立 PostgreSQL、Redis ACL、Python API/Worker、Runner 和分析任务镜像，但这些脚本面向 Linux 企业节点。Analysis Runner 的安全边界依赖宿主机 `DOCKER-USER`/`iptables` 白名单和真实 Docker 18.09 验收，macOS Docker Desktop 不能证明该边界，因而本地开发链不得启动或模拟分析容器。

## 目标

- `restart-dev-services.sh` 默认初始化并启动本地工作流控制面，使页面、认证、会话、仓库选择和任务持久化可用。
- 启动独立 Python API 和 Worker；Java仍只提供既有窄平台能力，不代理工作流请求。
- 支持通过显式环境变量连接合规的外部 Linux Runner。
- 未配置或无法访问 Runner 时保持控制面就绪，并让分析 run 以明确错误结束。
- 所有本地生成的密码、HMAC 和 RSA 密钥只保存在 `.tmp/dev-services/workflow/`，不修改任何 `.env.local`、`.env.test` 或其他用户配置。
- 初始化和重启可重复执行，失败时给出脱敏且可定位的日志。

## 非目标

- 不在 macOS Docker Desktop 上启动 `analysis-task`、挂载 Docker Socket或绕过分析网络校验。
- 不改变生产 Nginx、纯 Docker 离线部署或 Runner 的安全约束。
- 不新增或修改工作流 HTTP API、AG-UI 事件、业务表结构或 Java 工作流业务对象。
- 不自动创建、配置或管理远端 Linux Runner。
- 不清理已有本地工作流数据库、Redis ACL、报告或会话数据。

## 方案选择

采用“本地控制面 + 可选外部 Linux Runner”。

未采用的方案：

- 完整本机 Runner：macOS Docker Desktop 无法通过宿主机 `DOCKER-USER`/`iptables` 安全验收，禁止以标签模拟或放宽网络策略。
- 只保留外部工作流服务：不能解决默认开发环境的 HTTP 502，且继续要求开发者手工维护整套 Python 控制面。

## 组件边界

### 本地工作流助手

新增单一职责的 Bash 助手，负责以下动作：

- 安全读取调用方已经选定的开发 dotenv，不执行其中的 shell 内容。
- 创建 `.tmp/dev-services/workflow/`，生成并复用本地数据库密码、Redis ACL 密码、三组相互独立的 HMAC 和 RSA 公私钥。
- 幂等初始化 PostgreSQL 独立角色与数据库、Redis ACL、Alembic 业务表和 LangGraph checkpoint 表。
- 准备固定 Python 3.12 虚拟环境及锁定依赖。
- 启动、停止、探测 Python API 与 Worker，并管理精确 PID 文件和日志文件。
- 输出供根重启脚本加载的受控环境文件；不得把密钥值写入标准输出或命令行参数。

助手不负责启动 Runner、创建分析 Docker 网络或构建分析镜像。

### 根重启脚本

`restart-dev-services.sh` 在 Java 启动前调用工作流助手的准备动作并加载生成配置，使 Java 与 Python 获得匹配的 HMAC、Runner ID/公钥和模型网关地址。Java、Python API、Python Worker、OpenCode Manager 与前端仍是独立进程。

默认启用工作流控制面。新增 `--without-workflow` 作为显式兼容开关；关闭时保持原来的三服务启动行为。停止流程只终止 PID 文件精确指向的 Python API/Worker，不删除数据库、ACL或密钥。

### Python API 与 Worker

- API 固定监听 `127.0.0.1:8090`，Vite继续同源代理 `/workflow-api/**`。
- Worker使用独立进程和同一工作流数据库，不访问 Docker Socket。
- 默认 Runner地址为 `http://127.0.0.1:8091`。调用方可用现有 `TEST_AGENT_WORKFLOW_*` 和 `TEST_AGENT_RUNNER_*` 配置显式覆盖本地占位值，连接外部 Linux Runner。
- 外部 Runner 的 ID、公钥、Worker HMAC、平台 HMAC 和可路由模型网关地址必须成组提供；不接受只覆盖其中一部分的混合配置。
- 本地助手只生成占位 Runner 密钥以满足 Java/Python配置校验，不启动持有对应私钥的 Runner进程。

## 初始化与启动流程

1. 根脚本解析参数和dotenv，确认 PostgreSQL、Redis 和服务端口配置。
2. 工作流助手创建权限受限的状态目录；已有完整状态时复用，缺失或不一致时失败关闭，不静默生成半套配置。
3. 使用现有 PostgreSQL管理员连接执行 `deploy/internal/workflow/bootstrap-workflow.sql`，创建或更新独立 owner/runtime角色和数据库。
4. 使用 owner连接执行 Alembic迁移与 LangGraph checkpoint初始化；长运行API/Worker只获得 runtime连接串。
5. 为平台Redis创建独立ACL用户，仅允许 `PING` 以及 `test-agent:token:*` 下的 `GET/PTTL`。无密码管理员连接只允许用于已验证为回环地址的本地Redis。
6. 根脚本加载生成环境后启动Java，确保窄能力接口获得与Python匹配的签名和Runner公钥配置。
7. 工作流助手启动API并轮询 `/workflow-api/v1/ready`；数据库或Redis未就绪时停止本次新进程并返回非零状态。
8. API就绪后启动Worker；随后继续启动OpenCode Manager和前端。
9. 重启完成摘要只输出服务状态、端口和日志路径，不输出连接串、密码、Token、HMAC或PEM正文。

## 配置优先级

1. 调用进程显式提供且完整的外部Runner配置。
2. 已存在的 `.tmp/dev-services/workflow/workflow-dev.env` 本地稳定配置。
3. 首次启动时生成的本地随机配置。

平台数据库、Redis和模型基础配置仍来自根脚本选定的dotenv。助手不写回该文件。显式外部Runner配置必须通过格式、完整性和公钥文件权限校验；私钥始终留在外部Runner节点，本地控制面不得复制或读取。

## 运行与错误语义

- `/workflow-api/v1/health` 表示API进程存活；`/workflow-api/v1/ready` 只验证独立PostgreSQL和认证Redis。Runner缺失不影响控制面就绪，因此不会把正常页面访问重新变成502。
- Java窄能力接口配置或签名不一致时，仓库查询返回现有统一错误结构并保留traceId，日志只记录错误类别。
- Worker访问Runner失败时，任务按既有run失败路径发布durable `RUN_ERROR`/终态事件，用户看到“Analysis Runner不可用”类明确消息；不得静默排队、无限重试或降级到本机容器。
- 初始化、迁移、ACL校验或API就绪失败时，根重启命令失败并指出对应日志；不得在工作流未就绪时宣告整体启动成功。
- 重复停止是幂等的。PID与实际进程命令不匹配时拒绝终止，避免杀死无关进程。

## 安全设计

- 状态目录权限为 `0700`，敏感文件为 `0600`；生成时使用原子写入和安全临时文件。
- 密码和HMAC至少32随机字节，三组HMAC不得复用；RSA密钥满足现有Runner的OAEP公钥格式要求。
- PostgreSQL密码通过环境或标准输入传递，不进入命令参数；Redis ACL密码同样不得进入 `ps` 或日志。
- Redis ACL启动后必须验证允许 `PING/GET/PTTL`，并验证拒绝 `SCAN`、`KEYS`、写入和其他前缀。
- Authorization、平台Token、Redis键、数据库连接串和PEM正文进入日志前必须脱敏或完全省略。
- Python API/Worker不挂载Docker Socket；本地流程不创建伪受限网络，不启动Runner和分析容器。

## 测试设计

### 合同与单元测试

- 首次生成、重复准备、配置缺失和半套外部Runner配置。
- 状态目录/文件权限、HMAC互异、密钥复用及日志脱敏。
- PID精确匹配、幂等停止、占用端口和异常子进程。
- dotenv安全解析、`--without-workflow` 参数和根脚本调用顺序。
- Redis ACL允许项、禁止项和非回环无密码管理员拒绝逻辑。

### 真实本地集成验证

- 使用当前本地PostgreSQL创建独立角色/数据库，执行Alembic和checkpoint初始化，并用runtime账号验证最小权限访问。
- 使用当前本地Redis创建ACL，验证Token精确读取和禁止命令。
- 启动Java、API、Worker和前端，验证8090监听、`health/ready`正常、浏览器不再显示HTTP 502。
- 使用已登录Token验证`/me`、definitions、会话创建和仓库查询。
- Runner不存在时提交分析，验证出现明确失败事件且没有Docker容器或Socket访问。
- 若提供合规外部Runner，则额外验证Runner公钥/签名配对和任务进入分析流程；外部节点不作为本次本地修复的强制验收条件。

### 回归

- 工作流Python测试。
- 开发脚本轻量校验。
- 工作流前端客户端与页面测试、类型检查和构建。
- 受影响Java窄能力配置测试；未修改Java实现时不扩大到无关模块重构。

## 文档与兼容性

- 更新 `docs/guides/ai-workflow.md`、`workflow-service/README.md` 和根重启脚本帮助，记录默认启动、关闭开关、日志位置及外部Runner接入方式。
- 检查并按需更新前端开发说明；不改变 `/workflow-api/v1/**` 或AG-UI协议文档正文。
- 不涉及Java Flyway、Python Alembic版本新增、关系型业务SQL、API/DTO/事件字段变化。
- 生产工作流离线部署和Nginx路径不变；本地辅助配置只存在于 `.tmp/dev-services`。

## 验收标准

- 执行默认本地重启后，`127.0.0.1:8090` 有Python API监听且 `/workflow-api/v1/ready` 返回成功。
- `/workflow-chat` 不再显示“工作流服务暂不可用（HTTP 502）”。
- 页面可读取当前用户、工作流定义、仓库和会话数据。
- 无外部Runner时不创建任何分析容器，分析任务以明确Runner不可用错误结束。
- `--without-workflow` 保持既有Java、Manager和前端启动能力。
- `.env.test`、`.env.local` 未被修改；日志和进程参数中无敏感值。
- 本次相关测试和自检通过，其他开发者的未提交改动未被覆盖或纳入提交。
