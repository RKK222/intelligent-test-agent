# AI 编码约定

本文档是 Codex 和 Claude 修改本项目时必须先阅读的入口规范。任何任务都必须先理解相关文档和代码边界，再做最小范围修改。

## 必读顺序

1. 先读 `docs/README.md`，确认任务类型对应的规范文档。
2. 研发流程先读 `docs/guides/ai-workflow.md` 和 `docs/guides/self-checklist.md`。
3. 后端任务读 `backend/README.md`、目标模块 `README.md`，再读 `docs/standards/backend.md` 和 `docs/architecture/dependency-rules.md`。
4. 前端任务读 `frontend/README.md`、目标 app/package `README.md`，再读 `docs/standards/frontend.md`。
5. 找功能定位代码读 `docs/architecture/module-map.md`。
6. API、事件、数据库、安全、部署相关任务必须读 `docs/api/`、`docs/deployment/`、`docs/standards/security.md` 对应文档。

根目录 `requirements/` 下的历史设计、需求草案和阶段计划**不作为编码依据**，仅供追溯。

## 强制规则

1. 只改与任务直接相关的最小范围，不允许顺手重构无关代码。
2. 修改前必须先分析需要改的代码、文档和测试位置。
3. 修改后必须同步更新稳定文档，包括工程 README、模块/包 README、API 文档和测试说明。
4. 人工维护代码的新增或复杂修改必须有中文注释；generated SDK 不手工补注释。
5. API 必须有文档，新增或变更 API 必须同步更新 `docs/api/http-api.md` 和 `docs/api/event-stream.md`。
6. Controller 不得直接调用 generated SDK 或 Repository。
7. 业务层不得依赖 `test-agent-opencode-sdk-generated`。
8. 前端不得直连 opencode server，必须通过 `backend-api` 调用 `test-agent-app`，实时事件必须通过 RunEvent SSE。
9. generated SDK 不能手改，只能通过 `tools/generate-opencode-java-sdk.sh` 重新生成后同步。
10. 数据库结构变更必须有 Flyway migration，不能只改实体或 Repository，并同步 `docs/deployment/database.md`。
11. API、DTO、事件类型、数据库字段变更必须考虑向后兼容。
12. 鉴权、限流、日志脱敏和密钥管理必须按 `docs/standards/security.md` 执行。
13. 错误必须统一格式返回，不能把任意异常直接抛给前端。
14. 关键流程必须携带或生成 traceId，禁止用 `System.out.println` 作为正式日志。
15. 完成前必须按 `docs/guides/self-checklist.md` 自检。
16. 后端新增文件前必须先按 `docs/architecture/module-map.md` 和 `docs/architecture/dependency-rules.md` 分析是否已有合适工程；没有合适工程时，按业务边界新建 Maven module 后再落文件。模块 README 即包级说明。
17. **未经用户明确要求，不得修改 `.env.local` 等环境配置文件**。此类文件包含敏感的数据库连接、API 密钥等，仅在用户明确指示时方可修改。
18. 每次会话收尾时，如果本次出现了值得保留给后续开发者/智能体的新增信息（例如新的坑、验证结论、外部状态变化或明确决策），再更新 `.agents/session-log.md`，用 `Why / What / How / Result` 说明本次变更；同一会话内的零散小改动合并为一条，不要按文件或命令频繁记账。该文件属于仓库内容，应随本次 git 提交一起保留，必要时可与其他改动一并推送远程。

## 完成标准

每次修改结束前，AI 必须说明：

- 修改了哪些代码或文档。
- 执行了哪些测试或校验命令。
- 哪些文档已同步更新。
- 是否涉及 API、事件、数据库、性能、安全或兼容性。
- 是否存在未完成事项或风险。
- 是否已按需更新 `.agents/session-log.md` 并纳入本次提交。
- 不要随意新建git分支，除非我明确的告诉你需要新建。完成后必须自动提交 git 且 commit 信息用中文。
