# test-agent-memory

## 工程定位

QA Agent 长期记忆的业务编排模块。它负责个人记忆、Application 团队记忆、证据、冲突、审核、检索合并、Run 学习和 Skill 提案，不保存聊天记录，也不承担项目知识库职责。

## 数据事实边界

- Mem0 是派生记忆正文、向量和记忆历史的事实源。
- 平台 PostgreSQL 只保存治理元数据、适用范围、最多 200 字的证据摘要、审核、学习 Outbox、Run 使用记录和灰度白名单。
- OpenCode Session 与现有恢复链路仍是原始聊天的事实源；本模块只在学习任务执行期间瞬时读取当前 Run 对应的用户输入和最终回答。
- `displaySummary` 是列表降级展示字段，不是可直接注入 Agent 的记忆事实。

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
