# test-agent-memory

## 工程定位

通用长期记忆的 Java 编排模块。它负责平台治理、个人/团队授权、证据引用、学习 outbox、运行前检索、Run usage 和 Skill 提案；不保存聊天副本，不直连记忆 PostgreSQL，也不承担项目知识库职责。

## 事实与依赖边界

- 平台 PostgreSQL：范围、状态、owner/Application、证据 Session/Run 引用和摘要、审核、学习 outbox、usage、白名单、设置。
- memory-service：派生正文、逻辑版本、Mem0 history、幂等、双 collection 投影/outbox 和向量。
- 平台 Session：原始聊天的唯一事实源。worker 只在成功人工根 Run 的学习任务中瞬时读取本轮 USER/ASSISTANT。
- `MemoryDocumentStore` 是唯一 REST 端口；Controller 不直接调用它，业务层不依赖 generated SDK。

## 学习和检索

- 白名单用户、且能解析到当前 Application 的成功人工根 Run 写无原文学习 outbox；无法解析 Application 时不自动学习，也不会降级生成个人全局记忆。worker 读取本轮消息后调用 `/memories`，固定 `infer=true`、固定管理端 CHAT model，不添加平台抽取 prompt。
- Mem0 原生个人记忆直接 `ACTIVE`。默认 `PERSONAL_APPLICATION`；owner 可提升为 `PERSONAL_GLOBAL`。
- 团队记忆只允许成员手工提案，始终 `CANDIDATE`，必须由 `APP_ADMIN` 审核。个人记忆提案可以携带 `sourceMemoryId`，只复制安全证据引用/摘要。
- 同一记忆的编辑、暂停、范围提升、归档和团队审核在平台事务内通过 `findByIdForUpdate` 锁定治理行；先写未提交的平台状态，再调用同 operationId 可重放的 Mem0 操作，外部失败会回滚平台事务。候选创建的数据库失败补偿使用独立 DELETE operationId，不能复用 ADD 幂等键。
- 原生学习按 `mem0_memory_id` 原子建档；并行 Run 得到同一 Mem0 逻辑 ID 时只有一条治理记录，其余 worker 只追加各自的证据引用。
- Run 前一次 `/search` 包含个人全局、Application 个人和团队三个 scope。总超时默认 2 秒，任何错误 fail-open；最多注入 6 条/约 800 tokens。
- DTO 和运行逻辑不再使用 `taskTypes`、自定义 confidence、QA candidate、显式/隐式/临时来源。

## 模型网关安全

Mem0 回调 `/api/internal/platform/model-gateway/v1/chat/completions|embeddings` 使用 `MemoryModelHmacAuthenticator`。HMAC 覆盖方法、路径、body SHA-256、client/user/run/session/operation、时间、nonce、capability 和 embedding input type。Redis nonce store 原子防重放；时钟偏差默认 30 秒，nonce TTL 默认 2 分钟。CHAT model 必须等于系统记忆设置，Embedding model 必须等于企业 profile 或固定 CPU profile，调用者不能选择供应商。

Java 到 memory-service 使用 `X-Memory-Service-Key`，固定 HTTP/1.1、响应上限和低敏错误。key 只做服务认证；owner、白名单、成员和角色仍在平台校验。禁用开关或服务异常不能阻断普通 Run。

## API 与兼容性

- 新 API：`/api/internal/platform/memory/v1/**`，管理入口 `/api/internal/platform/memory/v1/admin/**`。
- 旧 `/api/internal/platform/qa-memory/v1/**` 返回 `410 API_GONE`。
- 遗留 `qa_*` 表和初始 Flyway 保持字节不变；新增字段只用前向 migration 和 MyBatis XML。
- Application Skill 提案只对当前有效成员可见；成员退出后不能继续读取管理员审核或编辑过的草稿。
- 不新增 RunEvent；“参考了 N 条记忆”继续通过 usage HTTP 批量恢复。

## 允许依赖

- `test-agent-common`、`test-agent-domain`、`test-agent-agent-runtime`、`test-agent-model-gateway`。
- Spring Context/WebFlux 与 Jackson。

## 禁止依赖

- `test-agent-api`、`test-agent-persistence`、`test-agent-app`、generated SDK。
- OpenCode 源码、MyBatis mapper、Redis key 或供应商密钥。
- 保存完整 prompt、回答、工具输出或隐藏系统指令。

## 验证

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) PATH="$JAVA_HOME/bin:$PATH" \
  mvn -q -DappLogDir=target/log -pl test-agent-memory -am test
```

完整部署与浏览器准入见 `docs/deployment/qa-memory.md`。
