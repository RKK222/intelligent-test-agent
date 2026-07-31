# 独立长程任务 HTTP API

## 边界

`/workflow-api/v1/**` 由 Nginx 同源直接转发到 Python `workflow-service`，不经过 Java。Java 不定义或保存 workflow conversation、message、task、run、report、event。

浏览器请求使用平台原 Bearer Token。Python 不签发第二套登录 Cookie；每次请求对平台 Redis 做精确 `GET test-agent:token:<token>` 和 `PTTL`，并校验 `AuthPrincipal`。成功响应使用 `{ "data": ... }`，失败响应固定为：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请求参数不合法",
  "traceId": "trace_...",
  "details": {}
}
```

`Authorization`、Token Redis 键、AuthPrincipal 原文、SSH 凭据、模型 grant 和下游异常正文不得进入错误或审计详情。

## 浏览器 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/workflow-api/v1/me` | 当前平台身份最小投影。 |
| GET | `/workflow-api/v1/definitions` | 已注册且可执行的工作流；当前只有 `code-change-impact-analysis`。 |
| GET | `/workflow-api/v1/repositories` | 当前用户在所有启用应用中可访问的应用代码库，按应用分组。 |
| GET | `/workflow-api/v1/repositories/{repositoryId}/branches` | 当前授权仓库远端分支及默认分支标记。 |
| GET | `/workflow-api/v1/conversations` | 当前用户会话；`SUPER_ADMIN` 可带 `ownerUserId`，Python 会向 Java 实时复核角色。 |
| POST | `/workflow-api/v1/conversations` | 新建独立会话。 |
| GET | `/workflow-api/v1/conversations/{conversationId}` | 会话及长期保存消息。 |
| POST | `/workflow-api/v1/conversations/{conversationId}/messages` | 提交自然语言或结构化输入。 |
| GET | `/workflow-api/v1/conversations/{conversationId}/events` | 带 Authorization 的原生 AG-UI SSE。 |
| POST | `/workflow-api/v1/runs/{runId}/cancel` | 标记取消、写模型授权撤销墓碑并删除任务工作区。 |
| GET | `/workflow-api/v1/tasks/{taskId}/reports` | 不可变报告版本，当前版本标记 `current=true`。 |
| GET | `/workflow-api/v1/reports/{reportVersionId}/download` | 下载 Markdown 报告。 |

`/workflow-api/v1/health` 只表示进程存活；`/workflow-api/v1/ready` 同时检查 workflow PostgreSQL 与认证 Redis。

## 消息请求与幂等

```json
{
  "clientRequestId": "req_...",
  "text": "分析订单分支的影响",
  "structuredInput": {
    "repositories": [
      {
        "repositoryId": "repo_...",
        "targetBranch": "feature/impact",
        "baselineBranch": "main"
      }
    ],
    "mode": "SINGLE",
    "analyzerIds": ["codex"],
    "scopeSelectors": [
      {"repositoryId": "repo_...", "kind": "SYMBOL", "value": "OrderService"}
    ]
  }
}
```

- `clientRequestId` 在同一会话内幂等；并发重复请求只生成一个确定性 message/task/run。
- `mode=SINGLE` 必须且只能选择一个智能体；`REVIEW` 最多三个。当前代码智能体白名单为 `codex`、`opencode`。
- `scopeSelectors.kind` 为 `PROGRAM/MODULE/DIRECTORY/FILE/SYMBOL`。
- 同一会话由 PostgreSQL 部分唯一索引保证只能有一个 `QUEUED/RUNNING/WAITING_INPUT` run；并行任务必须新建会话。
- 低置信度、意图冲突、默认分支无法解析或范围多义时返回 `requiredInput` 并通过 AG-UI 发布输入卡；字段完整后自动创建或恢复 run，不再二次确认。

run 状态：`QUEUED/RUNNING/WAITING_INPUT/SUCCEEDED/PARTIAL_FAILED/FAILED/CANCELED`。

工作区状态：`NONE/PROVISIONING/ACTIVE/STOPPED_RETAINED/RESUMING/EXPIRED/CLEANUP_FAILED`。

## 场景 1 固定语义

- 比较坐标为 `merge-base(defaultHead,targetHead)..targetHead`，并冻结默认/基线分支、目标分支、两端 HEAD 与 merge-base。
- checkout ticket 同时绑定 user/session/repository/task/run/runner/Runner 公钥/目标分支/基线分支，一次性消费。
- 无共同祖先、分支不存在或目标分支与 ticket 不一致时安全失败。
- 空 diff 直接发布成功报告，不调用代码智能体。
- Worker从部署配置选择平台模型网关公开模型ID，并随HMAC保护的Runner请求下发；平台grant到达Runner后只经stdin交给任务容器内独立UID的回环relay。Codex/OpenCode固定使用该模型ID、`127.0.0.1` relay和一次性本地token，不能直接持有平台grant或回退工具默认provider。
- 至少一个分析器成功可发布报告；部分分析器失败或 AgentScope 综合连续失败时为 `PARTIAL_FAILED`。综合失败使用带 `analyzerId` 证据来源的确定性降级报告；全部代码分析器失败才为 `FAILED` 且不发布报告。
- 局部重分析复用原 task 和冻结坐标，创建新 run/checkpoint namespace/报告版本；多义范围进入 `WAITING_INPUT`。
- 工作区从最后一次分析、局部重分析或报告追问成功结束后滚动保留 48 小时。已过期工作区不会因报告追问复活；过期后仍可基于报告问答，需要源码必须新建任务。

## Java 窄能力 API

以下 `/api/internal/workflow-capabilities/v1/**` 只供 Python/Runner 服务调用，不是浏览器 API：

| 方法与路径 | 能力 |
|---|---|
| `GET /repositories` | 按启用应用列出当前成员可访问代码库。 |
| `GET /repositories/{id}/branches` | 实时成员复核后读取远端分支。 |
| `POST /repositories/authorize` | 批量复核代码库类型、关联和成员权限。 |
| `POST /checkout-tickets` | 签发目标/基线分支与 Runner 公钥绑定的一次性 ticket。 |
| `POST /checkout-tickets/{ticket}/consume` | Runner 独立 HMAC 兑换，Java 此时才解密并重新封装个人 SSH key。 |
| `POST /model-grants` | 签发 user/task/run/client/analyzer 绑定的短期模型 grant。 |
| `POST /model-grants/{id}/refresh` | 只延长同一 opaque grant；重新检查 session 与当前用户状态。 |
| `POST /model-grants/{id}/revoke` | 精确撤销。 |
| `POST /model-grants/revoke-run` | 先写 run 撤销墓碑，再撤销已存在 grant，阻止取消竞态。 |
| `POST /permissions/super-admin/verify` | 跨用户报告/会话访问前实时复核角色。 |

Python HMAC canonical 字符串为：

```text
METHOD\nPATH\nBODY_SHA256\nUSER_ID\nSESSION_DIGEST\nTIMESTAMP\nNONCE\nworkflow
```

Runner 兑换 canonical 使用 `METHOD/PATH/BODY_SHA256/RUNNER_ID/TIMESTAMP/NONCE`。Java 校验固定 client ID、时钟窗口、Redis nonce、平台 session marker、用户状态、角色、应用成员与仓库当前状态；内部请求不传原始 Bearer。

## 兼容性

该 API 是新增独立命名空间，不修改现有 `/api/**` OpenCode/LobeHub 契约。新增字段优先可选；状态与事件新增值必须同步前端 reducer 和 `docs/api/workflow-ag-ui.md`。
