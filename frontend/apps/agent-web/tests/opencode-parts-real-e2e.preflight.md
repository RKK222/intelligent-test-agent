# OpenCode Part 真实 E2E 前置勘察

## 勘察结论

本文件记录 2026-07-11 在项目根目录使用 `.env.test`、Spring `test` profile 和 OpenCode `1.17.7` 完成的一次真实、可清理勘察。所有 ID 均来自标题前缀为 `e2e_part_preflight_` 的一次性资源；本文只保留 ID 前缀和 JSON 字段，不保留实际 ID、模型正文、Authorization、Cookie、内部代理密钥或其他用户会话内容。

勘察使用的真实链路为：

```text
配置管理异步创建应用工作空间
  -> runtime Workspace
  -> 平台 Session
  -> /api/internal/agent/opencode/runs
  -> RunEvent SSE
  -> OpenCode HTTP message
  -> 平台 messages(refresh=true) / session-tree messages
```

启动与健康检查命令：

```bash
if [[ -z "${JAVA_HOME:-}" ]]; then
  [[ "$(uname -s)" == "Darwin" ]] || { echo "请先设置 JDK 25 的 JAVA_HOME" >&2; exit 1; }
  export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
fi
"$JAVA_HOME/bin/java" -version 2>&1 | head -1 | grep -Eq 'version "25([.]|")' || {
  echo "JAVA_HOME 必须指向 JDK 25" >&2
  exit 1
}
export PATH="$JAVA_HOME/bin:$PWD/.tmp/dev-bin:/opt/homebrew/opt/libpq/bin:$PATH"
./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
curl -fsSI http://127.0.0.1:3000
opencode --version
```

实测结果为 readiness `UP`、前端 HTTP 200、OpenCode `1.17.7`。

## 一次性 Workspace、Session 与 Run

当前代码已没有旧版 `POST /api/internal/platform/workspace-management/workspaces` 创建入口。真实 E2E 应复用配置管理的现行异步创建链路：

1. `POST /api/internal/platform/configuration-management/applications/{appId}/workspaces`
2. `GET /api/internal/platform/configuration-management/workspace-create-operations/{operationId}`，轮询到 `SUCCEEDED`
3. `GET /api/internal/platform/workspace-management/applications/{appId}/workspace-templates/{applicationWorkspaceId}/versions`，从目标 `versionId` 取得 `runtimeWorkspace.workspaceId` 和 `workspaceRootPath`

创建 payload 的实际字段为：

```json
{
  "repositoryId": "<test-repository-id>",
  "branch": "<existing-test-branch>",
  "directoryPath": "e2e_part_preflight_<unique>",
  "workspaceName": "e2e_part_preflight_<unique>",
  "directoryNew": true,
  "version": "yyyyMMdd",
  "operationId": "wco_e2e_part_preflight_<unique>"
}
```

`operationId` 必须匹配 `^wco_[A-Za-z0-9_-]{8,128}$`；非标准代码库的 `version` 必须是 `yyyyMMdd`。accepted 响应 `data` 的字段为 `operationId/status/createdAt`；最终 operation 的字段为 `operationId/status/currentStep/errorCode/errorMessage/workspaceId/versionId/steps/createdAt/updatedAt`。

随后调用：

```text
POST /api/internal/platform/opencode-runtime/sessions
POST /api/internal/agent/opencode/runs
GET  /api/internal/agent/opencode/runs/{runId}/events
```

Session 创建 payload 是 `workspaceId/title`，响应 `data` 包含 `sessionId/workspaceId/title/status/pinned/createdAt/updatedAt` 等 Session 字段。Run 创建 payload 是 `sessionId/prompt/parts`，其中本次真实文本 Part 为 `{type:"text",text:"..."}`；响应 `data` 包含 `runId/sessionId/status/startedAt/completedAt/traceId` 等平台 Run 字段。

实测 `run.started` SSE 的 `payload` 只有 `status: "RUNNING"`，**不包含** remote Session ID。因此后续工具不能假设从 `run.started.payload.sessionID` 取映射。remote Session ID 可从同一 SSE 的原生恢复事件中取得，例如：

- `run.succeeded.payload.sessionID/rootSessionId/sessionId`；
- `message.updated.payload.message.sessionID`；
- `message.part.updated.payload.part.sessionID`；
- Session tree 的 root `sessions[].sessionId`。

本次 Run 约 5 秒进入 `SUCCEEDED`，SSE 实际出现 `run.started`、`run.succeeded`、`message.updated` 和 `message.part.updated`。SSE 使用 `curl --max-time` 做有界采集；Playwright/Node 实现必须在 `finally` 主动 abort，不能留下 reader 或 socket。

## OpenCode 原始消息接口

实测路由：

```text
GET http://127.0.0.1:{managerPort}/session/{remoteSessionId}/message?directory={urlEncodedWorkspaceRoot}
```

顶层响应是数组，每项结构为：

```text
{
  info: {
    id, sessionID, role, time,
    // user: agent, model, summary；assistant 还可有 parentID/providerID/modelID/mode/agent/path/cost/tokens/finish
  },
  parts: Part[]
}
```

本次自然触发响应包含 `text/reasoning/step-start/step-finish`。实测字段如下：

| type | 原始字段 |
| --- | --- |
| `text` | `id/messageID/sessionID/type/text` |
| `reasoning` | `id/messageID/sessionID/type/text/time` |
| `step-start` | `id/messageID/sessionID/type/snapshot` |
| `step-finish` | `id/messageID/sessionID/type/reason/snapshot/cost/tokens` |

所有 12 种 Part 的完整字段仍以 OpenCode 1.17.7 schema 和后续夹具用例为准；preflight 不能把本次四种自然输出误当作完整矩阵。

## 平台消息与 Session tree

### 平台 messages

实测路由：

```text
GET /api/internal/platform/opencode-runtime/sessions/{platformSessionId}/messages?page=1&size=100&refresh=true
```

响应是统一 envelope：`success/traceId/data`。`data` 是 `items/page/size/total`；每个 message item 的字段为：

```text
messageId, sessionId, runId, role, content,
remoteMessageId, parts, tokens, costUsd, createdAt, updatedAt
```

`refresh=true` 会从远端刷新快照。原始 reasoning、step-start、step-finish Part 保留 `id/sessionID/messageID`，平台还补齐 `partID/partId/messageId`。平台 user text 投影是兼容结构，实际可包含 `type/text/content/name/path/url/uri/mimeType/source/label/agentId/metadata`，断言时应按角色和来源区分，不能要求它与 assistant 原始 text 的键集合完全相同。

### Session tree messages

实测 agent-scoped 主路由：

```text
GET /api/internal/agent/opencode/sessions/{platformSessionId}/session-tree/messages
```

兼容路由仍存在，但 E2E 应复用 agent-scoped 路由。响应 `data` 的真实字段为：

```text
sessionId,
sessions[],
messagesBySessionId: Record<remoteSessionId, Array<MessageOrPartProjection>>,
childSessionIdByTaskPartId,
events[]
```

`sessions[]` 字段为 `rootSessionId/sessionId/parentSessionId/childSession/taskMessageId/taskPartId/taskCallId`。`messagesBySessionId` **不是** `sessions[].messages`；数组中 message entry 使用 `message` 字段，Part entry 使用 `part` 字段。Part entry 同时带 `messageID/sessionId/isChildSession/messageId/rootSessionId`。后续 E2E helper 必须按这一真实 shape 解析。

## 当前用户进程、manager state 与 SQLite

实测平台路由：

```text
GET /api/internal/agent/opencode/processes/me
```

统一 envelope 的 `data` 字段为：

```text
status, initializable, message, processId,
linuxServerId, containerId, port, baseUrl,
checkedAt, serviceStatus, serviceAddress
```

本次最终状态为 `READY/RUNNING`。manager state 位于：

```text
.tmp/dev-services/opencode-manager-state/processes/{port}.json
```

实际字段为：

```text
port, pid, baseUrl, sessionPath, configPath, startedAt, startCommand, traceId
```

平台 `processes/me.data.port` 与 state `port` 一致；state `pid` 与操作系统 `ps` 以及该 port 的 `LISTEN` PID 一致。`startCommand` 含运行环境信息，证据文件不得复制或输出该字段。

当前 manager 使用稳定用户目录而不是计划草案中的按端口目录：

```text
{sessionPath}/opencode/opencode.db
```

其中 `sessionPath` 必须来自目标 port 的 manager state，并且实测位于项目拥有的 `.testagent/agent-opencode/.session/users/` 下。后续夹具必须同时校验 state port、PID/listener、平台 process port 和数据库路径；不得把数据库路径硬编码为 `.tmp/dev-services/opencode-manager-session/{port}`。

OpenCode 1.17.7 实测 SQLite schema：

```sql
CREATE TABLE session (
  id text PRIMARY KEY,
  project_id text NOT NULL,
  workspace_id text,
  parent_id text,
  slug text NOT NULL,
  directory text NOT NULL,
  path text,
  title text NOT NULL,
  version text NOT NULL,
  share_url text,
  summary_additions integer,
  summary_deletions integer,
  summary_files integer,
  summary_diffs text,
  metadata text,
  cost real DEFAULT 0 NOT NULL,
  tokens_input integer DEFAULT 0 NOT NULL,
  tokens_output integer DEFAULT 0 NOT NULL,
  tokens_reasoning integer DEFAULT 0 NOT NULL,
  tokens_cache_read integer DEFAULT 0 NOT NULL,
  tokens_cache_write integer DEFAULT 0 NOT NULL,
  revert text,
  permission text,
  agent text,
  model text,
  time_created integer NOT NULL,
  time_updated integer NOT NULL,
  time_compacting integer,
  time_archived integer,
  FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);

CREATE TABLE message (
  id text PRIMARY KEY,
  session_id text NOT NULL,
  time_created integer NOT NULL,
  time_updated integer NOT NULL,
  data text NOT NULL,
  FOREIGN KEY (session_id) REFERENCES session(id) ON DELETE CASCADE
);

CREATE TABLE part (
  id text PRIMARY KEY,
  message_id text NOT NULL,
  session_id text NOT NULL,
  time_created integer NOT NULL,
  time_updated integer NOT NULL,
  data text NOT NULL,
  FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE
);
```

索引分别为 `session_project_idx/session_workspace_idx/session_parent_idx`、`message_session_time_created_id_idx`、`part_message_id_id_idx/part_session_idx`。`message.data` 和 `part.data` 承载完整 JSON；后续夹具必须复用测试 Session 已有行的 JSON 形状，不能把 Part 字段拆成不存在的列。

## 删除接口与清理闭环

真实删除顺序和响应为：

1. 查询 `GET /sessions/{platformSessionId}/active-run`；仅在非空时调用 `POST /api/internal/agent/opencode/runs/{runId}/cancel`。
2. `DELETE http://127.0.0.1:{port}/session/{remoteSessionId}?directory={workspaceRoot}`：OpenCode 原生永久删除，响应 JSON boolean `true`。
3. `DELETE /api/internal/platform/opencode-runtime/sessions/{platformSessionId}`：平台当前是软删除，响应为 `status: "ARCHIVED"` 的 Session DTO，不应把它描述成数据库硬删除。
4. `DELETE /api/internal/platform/configuration-management/applications/{appId}/workspaces/{applicationWorkspaceId}`：响应统一 envelope，`data: null`。
5. 防御性删除本次测试专属 `workspaceRootPath`；只能删除带本轮唯一 marker 且位于项目 `.testagent/agent-opencode/workspace/` 下的目录。

本次 finally 已验证：

- 用户历史按唯一标题搜索 `total=0`；
- OpenCode message GET 返回 HTTP 404；
- SQLite 中该 remote Session 的 `session/message/part` 行数均为 0；
- 配置管理 application workspace 列表无对应 ID；
- 临时 workspace root 不存在；
- SSE curl 已结束，临时 token/state/raw JSON 均已删除。

## 后续实现复用点与安全边界

- 复用 `frontend/packages/backend-api/src/index.ts` 已有的 `createSession/startRun/listSessionMessages/getSessionTreeMessages/getMyOpencodeProcess/deleteSession` 路由契约；Task 2 再提取 real E2E HTTP helper，不在 preflight 新建第二套生产客户端。
- 复用 `SessionController`、`RunController`、`ConfigurationManagementController` 的现行 route；不新增生产 API。
- remote ID 从 Session tree 或原生恢复事件解析，不从 `run.started` 猜测。
- manager state 只读；除明确带唯一 marker 的测试 Session transaction 外，不写用户 SQLite，不读取或输出其他 Session 的 `data`。
- 不记录 JWT、Cookie、manager token、内部代理 key、`startCommand` 或用户非测试消息；错误文本也不得拼入 Bearer token。
- 不修改 `.env.test`、`.env.local`、用户 OpenCode 配置、generated SDK、平台数据库 schema 或 RunEvent wire name。
- 所有网络流、Run、远端 Session、平台 Session、配置工作空间和物理临时目录都必须进入同一 `try/finally` 清理栈；任一清理失败都应使 real E2E 失败并报告残留 ID 前缀。
