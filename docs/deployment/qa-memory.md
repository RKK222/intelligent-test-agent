# QA Agent 长期记忆 V1 部署与验收

本文档说明 QA Agent 长期记忆的数据边界、本地启动、企业部署、健康检查、灰度和回滚。V1 的目标是让 Agent 长期复用测试人员稳定的工作习惯，不是新建一份聊天记录或项目知识库。

## 能力边界

| 信息类型 | V1 事实源与处理方式 | 为什么不混存 |
|---|---|---|
| 原始聊天 | 继续由 OpenCode Session 和现有会话恢复链路保存；学习 worker 仅在处理当前 Run 时瞬时读取用户输入与最终回答 | 聊天记录是可回放的业务证据，而记忆是经提取、确认和版本化的派生结论；复制会导致隐私边界和删除语义混乱 |
| 个人记忆 | Mem0 保存派生正文、向量和历史；平台 PostgreSQL 保存用户、全局/应用范围、状态、证据摘要与乐观版本 | 这是“这个测试人员怎么工作”的可治理事实 |
| 团队记忆 | 仅在 Application 边界内生效；普通成员提案，`APP_ADMIN` 审核 | 团队规则不应被写成某个人的画像，也不能跨 Application 泄漏 |
| 项目业务知识 | 不进入本系统，后续由独立项目知识库管理 | 业务事实的生命周期、权限和召回规则与个人习惯不同 |
| 通用测试方法 | 由已生效记忆发起 Skill 提案，审核后生成可编辑 `SKILL.md` 草稿，继续走现有 Git/发布/Hub 流程 | 可复用方法是可发布能力，不应无审核地变成所有人的个人画像 |
| 静态用户画像 | 只能作为页面汇总视图，从当前有效记忆即时投影 | 汇总文本不具备证据、作用域、版本和冲突语义，不能作为事实源 |

Mem0 不保存原始消息。定制 history manager 的 `save_messages()` 为空操作，`get_last_messages()` 固定返回空；带鉴权的 readiness 必须同时报告 `rawMessageCount=0`。页面上的证据摘要最多 200 字，用于人工判断，不是聊天正文镜像。

## 运行链路

```text
成功的人工根 Run
  -> 平台 PostgreSQL 学习 Outbox（只存定位字段）
  -> worker 从现有 session_messages 瞬时读取 USER/ASSISTANT
  -> 固定内部 CHAT 模型，或经目录与 CHAT 探测确认的当前内部模型
  -> 用户 + Run + 模型 + TTL 绑定的一次性 mfg_ 授权
  -> memory-service 提取候选
  -> 平台阈值、范围、冲突和审核治理
  -> Mem0 保存已治理的派生记忆
```

Run 开始前分别搜索个人全局、个人 Application 和团队 Application，Java 再次校验白名单、成员关系、状态和任务类型。检索总预算默认 600 ms，最多 6 条、约 800 tokens；超时或任一依赖失败时继续原 Run。只有实际写入使用记录的记忆才注入 `AgentStartRunCommand.system`，当前用户要求始终优先。

## 固定数据面

- Mem0：`mem0ai==2.0.3`。
- Embedding provider：`LOCAL_BGE`。
- 模型：`BAAI/bge-small-zh-v1.5`。
- revision：`7999e1d3359715c523056ef9478215996d62a620`。
- 运行方式：CPU、512 维、L2 归一化，中文 query 使用固定检索前缀。
- 模型目录：构建期写入镜像的 `/models/BAAI__bge-small-zh-v1.5`；运行期开启 HuggingFace/Transformers offline 与 Mem0 telemetry 禁用。
- 向量库：独立 PostgreSQL + pgvector，不与平台 PostgreSQL 共库。
- 集合名：provider、model、revision、dimension、collection version 和 profile 摘要共同决定。

未来企业 Embedding 可用时，必须新增 Provider 和新集合，运行双写/回填与对比验证后再切换；禁止用新模型直接覆盖 `LOCAL_BGE` 集合。

## 本地启动

`deploy/dev/memory-compose.yml` 是个人开发专用 Compose，固定工程名 `test-agent-memory-dev`，只管理 `memory-postgres` 和 `memory-service`。默认不启动，也不探测、停止已存在的记忆容器。

在独立 worktree 中使用现有绝对路径 `.env.test`，不复制或修改环境文件。源码和 Git 状态仍在记忆
worktree；`TEST_AGENT_ROOT`、`TESTAGENT` 与 `SYS_DATA_ROOT_DIR` 必须显式复用主工作区已有的本地运行数据，
否则启动脚本会默认查找记忆 worktree 下的空 `.testagent`，用户 OpenCode 初始化将因公共 Agent 配置源目录不可用而失败：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent-memory-v1
export TEST_AGENT_ROOT=/Users/kaka/Desktop/intelligent-test-agent
export TESTAGENT="$TEST_AGENT_ROOT"
export SYS_DATA_ROOT_DIR="$TEST_AGENT_ROOT/.testagent"
JAVA_VERSION=25 ./restart-dev-services.sh \
  --profile test \
  --env-file /Users/kaka/Desktop/intelligent-test-agent/.env.test \
  --skip-frontend-build \
  --without-workflow \
  --with-memory
```

启动输出必须同时确认上述 `TEST_AGENT_ROOT` 与 `SYS_DATA_ROOT_DIR`；该覆盖只改变本地运行数据位置，不会让
backend/frontend 构建物脱离记忆 worktree，也不会复制、清理或回退主工作区中的 Agent 配置。

首次 `--with-memory` 会拉取固定 pgvector 镜像，构建 memory-service 并在构建期下载固定 revision 的 BGE 权重，因此构建机需要一次网络访问；完成后容器运行不访问 HuggingFace。默认主机端口为：

| 服务 | 地址 | 边界 |
|---|---|---|
| memory-service | `127.0.0.1:18888` | 只对本机发布；`/health` 只表示进程存活，平台使用带 key 的 `/memory-api/v1/ready` |
| memory-postgres | `127.0.0.1:15433` | 只对本机发布，数据库名与用户均为 `qa_memory` |
| Java backend | `127.0.0.1:8080` | memory-service 通过固定 model-gateway base path 回调 |
| frontend | `127.0.0.1:3000` | `/memories` 和“系统管理 → 记忆能力” |

辅助脚本可单独使用：

```bash
tools/memory-dev-services.sh prepare
tools/memory-dev-services.sh build
tools/memory-dev-services.sh start
tools/memory-dev-services.sh status
tools/memory-dev-services.sh stop
```

`prepare` 生成两个 `0600` 文件：

- `.tmp/dev-services/memory/memory-dev.env`：Compose 专用 API key、pgvector 密码、端口和镜像标识。
- `.tmp/dev-services/memory/memory-backend.env`：Java 仅需的 `enabled/service-url/service-api-key`，不含 pgvector 密码。

脚本不 `source` dotenv，不回显密钥，也不执行 `docker compose down`。`stop` 只停止该 Compose 工程的两个容器，保留 pgvector 和 Mem0 history 卷。
启动不依赖 Compose 的普通 health 等待；脚本会在 CPU 模型冷启动期间最多等待 7 分钟，并且只在带鉴权 readiness 返回
`UP` 且 `rawMessageCount=0` 后放行。

## 灰度与管理

数据库 migration 后白名单默认为空。这意味着即使 `TEST_AGENT_MEMORY_ENABLED=true`，存量用户的对话也不会学习或注入记忆。超级管理员先在“系统管理 → 记忆能力”检查 Mem0、pgvector、BGE、CHAT 和队列，再逐用户加入白名单。

建议灰度顺序：

1. 只加入一名内部测试人员，人工新增一条个人记忆并跨 Session 验证注入徽标。
2. 验证明确要求一次生效，隐式偏好必须在 90 天内由 3 个不同 Session 支撑。
3. 在一个 Application 内验证普通成员只能提案，`APP_ADMIN` 可批准/拒绝；移除成员后立即无权检索。
4. 再扩大白名单，持续观察 Outbox 待处理/失败数、检索超时和实际使用记录。

固定 CHAT 模型未设置时，只有当前 Run 模型来自平台内部目录且 CHAT 探测成功才能回退。外部模型、未探测模型或网关不可用时，学习任务重试/失败，但不阻断 QA Run。

## 企业离线部署边界

企业环境不使用开发 Compose。`memory-service/Dockerfile` 是独立可部署产物；需在可联网的构建机生成目标 Linux 架构镜像，同时获取固定 `pgvector/pgvector:0.8.1-pg16` 镜像，再通过企业标准的 image tar、SHA-256、SBOM 和许可清单流程转运。不得在离线现场下载模型，也不得把开发机 `.tmp` 密钥带入交付包。

生产拓扑要求：

- 独立 PostgreSQL 16 + pgvector 0.8.1 数据库，使用独立账号，不扫描 Java Flyway location。
- V1 只允许单活 memory-service；`/data/mem0-history.db` 与 pgvector 数据库必须同时备份。未完成 history store 外置化前不得水平扩容。
- 容器使用 UID/GID `10004`、只读根文件系统、只读 `/models`，仅 `/data` 可写；丢弃 Linux capabilities 并禁止 privilege escalation。
- `TEST_AGENT_MEMORY_SERVICE_API_KEY` 和数据库密码由配置中心或 `0600` 敏感文件注入，不放在命令行、日志或镜像层。Java 节点只获得相同的 service URL/key，不获得 Mem0 数据库密码。
- memory-service 只能访问 pgvector 和 Java 固定 `/api/internal/platform/model-gateway/v1`；禁止直连模型供应商或公网。
- Java 使用 `TEST_AGENT_MEMORY_ENABLED=true`、`TEST_AGENT_MEMORY_SERVICE_URL`、`TEST_AGENT_MEMORY_SERVICE_API_KEY`；所有节点配置必须一致。

发布顺序为“备份 → 独立 pgvector 数据库 → memory-service → 平台 PostgreSQL migration → Java → 前端 → 健康检查 → 单用户白名单”。正式启用前必须确认交付 JAR 内 migration 字节与已验收源文件一致。

## Migration 与备份

平台 migration：

```text
backend/test-agent-persistence/src/main/resources/db/migration/
V20260809120000__create_qa_memory_governance.sql
SHA-256 b2ae5639284208be8bc09952d9143c3dd0d8a2bf649b6601aed4225e586af18a
```

该 migration 只创建治理、证据摘要、审核、Outbox、Run 使用、白名单、Skill 提案和设置表，不存原始 Prompt/回答。一旦在任一需要保留的数据库执行，文件名和字节不得修改；后续只能新增更高版本 migration。

备份必须作为同一变更窗口的两个受控产物：

1. pgvector 数据库一致性备份，保留集合、metadata 和向量。
2. memory-service `/data/mem0-history.db` 快照，保留 Mem0 派生历史。

平台库、pgvector 和 history 的恢复点必须记录在同一变更单中。恢复后先保持白名单关闭，验证 readiness 和抽样历史后再开放。

## 验收清单

```bash
# Python 合同与单元测试
cd memory-service
PYTHONPATH=src .venv/bin/pytest tests

# Java 定向及相关全量测试
JAVA_HOME=<jdk-25-home> PATH="$JAVA_HOME/bin:$PATH" \
  mvn -f backend/pom.xml \
  -pl test-agent-memory,test-agent-model-gateway,test-agent-opencode-runtime,test-agent-api,test-agent-persistence,test-agent-app \
  -am test

# 前端
cd frontend
corepack pnpm test
corepack pnpm --filter @test-agent/agent-web typecheck
corepack pnpm --filter @test-agent/agent-web build

# 开发数据面与脚本边界
cd ..
tools/verify-dev-scripts.sh
tools/memory-dev-services.sh status
```

运行态必须通过：

- backend `/actuator/health/readiness`、frontend `3000` 和 memory-service authenticated readiness 均为 UP。
- readiness 返回 `rawMessageCount=0`，pgvector extension 可用，Embedding profile 为固定 512 维版本。
- 空白名单时既有对话正常，不写学习 Outbox，不注入记忆。
- 白名单用户的明确记忆能跨 Session 生效，完成卡只显示真正注入的数量。
- 两名用户、两个 Application 的团队隔离、成员移除、`APP_ADMIN` 审核和越权拒绝通过。
- Skill 提案审核后仅生成草稿，不自动写工作区、提交、发布或撤回。
- memory-service、CHAT 或 pgvector 不可用时，QA Run 按无记忆降级并保留可观测错误，日志不含原始聊天、grant 或 service key。

## 回滚

1. 先从白名单移除所有用户，确认新 Run 不再产生 Outbox 和使用记录。
2. 将所有 Java 节点的 `TEST_AGENT_MEMORY_ENABLED=false` 并重启；记忆不可用不影响原对话链路。
3. 停止 memory-service，保留 pgvector 和 history 备份；未经数据所有者批准不删卷、不删库。
4. Flyway migration 不回退、不 `repair`。旧 Java 不读取新表，新表保留以便审计和再启用。
