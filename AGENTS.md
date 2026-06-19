# AI 编码约定

本文档是 Codex 和 Claude 修改本项目时必须先阅读的入口规范。任何任务都必须先理解相关文档和代码边界，再做最小范围修改。

## 必读顺序

1. 先读 `docs/README.md`，确认任务类型对应的规范文档。
2. 后端任务读 `backend/README.md`、目标模块 `README.md`、目标包 `PACKAGE.md`。
3. 前端任务读 `docs/frontend/*.md`；前端是完全自研 Web IDE，未来创建 `frontend/` 后，还必须读对应 workspace、app、package 的 `README.md` 和包级说明。
4. API、事件、数据库、安全、性能相关任务必须读对应 `docs/api/`、`docs/backend/`、`docs/security/`、`docs/architecture/` 文档。
5. 分阶段实施任务必须先读 `docs/implementation-plan.md` 和 `docs/plan/` 下对应阶段计划。

## 强制规则

1. 只改与任务直接相关的最小范围，不允许顺手重构无关代码。
2. 修改前必须先分析需要改的代码、文档和测试位置。
3. 修改后必须同步更新稳定文档，包括项目说明、工程 README、包级说明、API 文档和测试说明。
4. 人工维护代码的新增或复杂修改必须有中文注释；generated SDK 不手工补注释。
5. API 必须有文档，新增或变更 API 必须同步更新 `docs/api/`。
6. Controller 不得直接调用 generated SDK 或 Repository。
7. 业务层不得依赖 `test-agent-opencode-sdk-generated`。
8. 前端不得直连 opencode server，必须通过 `backend-api` 调用 `test-agent-app`，实时事件必须通过 RunEvent SSE。
9. generated SDK 不能手改，只能通过 `tools/generate-opencode-java-sdk.sh` 重新生成后同步。
10. 数据库结构变更必须有 Flyway migration，不能只改实体或 Repository。
11. API、DTO、事件类型、数据库字段变更必须考虑向后兼容。
12. 鉴权、限流、日志脱敏和密钥管理必须按 `docs/security/security-standards.md` 执行。
13. 错误必须统一格式返回，不能把任意异常直接抛给前端。
14. 关键流程必须携带或生成 traceId，禁止用 `System.out.println` 作为正式日志。
15. 完成前必须按 `docs/development/ai-self-checklist.md` 自检。

## 完成标准

每次修改结束前，AI 必须说明：

- 修改了哪些代码或文档。
- 执行了哪些测试或校验命令。
- 哪些文档已同步更新。
- 是否涉及 API、事件、数据库、性能、安全或兼容性。
- 是否存在未完成事项或风险。
