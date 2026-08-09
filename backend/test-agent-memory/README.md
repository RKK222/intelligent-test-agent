# test-agent-memory

## 工程定位

QA Agent 长期记忆的业务编排模块。它负责个人记忆、Application 团队记忆、证据、冲突、审核、检索合并、Run 学习和 Skill 提案，不保存聊天记录，也不承担项目知识库职责。

## 数据事实边界

- Mem0 是派生记忆正文、向量和记忆历史的事实源。
- 平台 PostgreSQL 只保存治理元数据、适用范围、最多 200 字的证据摘要、审核、学习 Outbox、Run 使用记录和灰度白名单。
- OpenCode Session 与现有恢复链路仍是原始聊天的事实源；本模块只在学习任务执行期间瞬时读取当前 Run 对应的用户输入和最终回答。
- `displaySummary` 是列表降级展示字段，不是可直接注入 Agent 的记忆事实。

## Mem0 适配边界

- `MemoryDocumentStore` 是本模块访问独立 `memory-service` 的唯一端口；启用开关关闭时装配安全降级实现，不能影响既有 Run。
- `HttpMemoryDocumentStore` 只调用受控的 health、派生记忆 CRUD/search/history 和候选抽取窄接口，请求超时、响应大小和 ID 格式都在适配层收口。
- 原始聊天只允许作为抽取请求的瞬时输入，不得调用派生记忆写入接口保存整段消息；正文、模型短期授权和服务密钥不得进入日志或 `toString()`。
- Python 服务的固定版本、离线模型和运行说明见仓库根目录 `memory-service/README.md`。

## 自动学习与运行时复用

- 只有灰度白名单用户的成功人工根 Run 会写学习 Outbox；Outbox 仅保存 Run、Session、Workspace、用户、Application 和模型定位字段。worker 随后从既有 `session_messages` 恢复链瞬时读取 USER/ASSISTANT 内容，忽略工具输出，并把抽取失败限制在异步重试链路。
- 抽取模型按“系统管理固定 CHAT 模型 → 当前 Run 内部 CHAT 模型”选择；回退前必须再次通过内部目录与 CHAT 探测。每次调用签发绑定用户、Run、模型且只能消费一次的 `mfg_` grant，Redis 只以 SHA-256 摘要寻址。
- 明确要求或手工记忆一次生效；隐式偏好必须在 90 天内由 3 个不同 Session 支撑。临时要求丢弃；明确替代会封存旧版本，隐式冲突进入待确认状态。
- Run 启动前并行搜索个人全局、个人 Application 和团队 Application 范围，重新校验白名单、成员关系、状态、任务类型和正文安全。整个检索预算默认 600ms，最多注入 6 条、约 800 tokens；超时或任一依赖失败都返回空上下文继续原 Run。
- 只有已选中、已写入批量使用记录的条目才进入 `AgentStartRunCommand.system`。当前输入与 Application 规则始终优先，注入内容不得被解释为项目业务事实。

## 允许依赖

- `test-agent-common`、`test-agent-domain`、`test-agent-agent-runtime`、`test-agent-model-gateway`。
- Spring Context/WebFlux 与 Jackson。

## 禁止依赖

- `test-agent-api`、`test-agent-persistence`、`test-agent-app` 和 generated SDK。
- OpenCode 源码、MyBatis mapper、Redis key 或供应商密钥。
- 保存原始聊天、完整 Prompt、完整回答、工具输出或隐藏系统指令。

## 后续 AI 编码指引

记忆策略、集中 Prompt、Mem0 窄接口、模型短期授权、学习和检索编排改这里；领域对象与端口改 `test-agent-domain`，SQL/Redis 实现改 `test-agent-persistence`，HTTP DTO 改 `test-agent-api`。

## 验证

```bash
mvn -q -DappLogDir=target/log -pl test-agent-memory -am test
```

真实适配器聚焦回归可运行：

```bash
mvn -q -DappLogDir=target/log -pl test-agent-memory -am \
  -Dtest=HttpMemoryDocumentStoreTest -Dsurefire.failIfNoSpecifiedTests=false test
```
