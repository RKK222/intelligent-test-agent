# Phase 00 总体路线图

## 阶段目标

把 `test-agent` 建设为单后端服务包加完全自研 Web IDE 前端的平台。后端负责领域、路由、执行、事件和持久化；前端负责工作台、文件、编辑器、Diff、Agent 对话、测试报告和 Skill Studio。

详细设计见 `docs/design/00-roadmap-design.md`。

## 可验收功能清单

1. 后端保持单可部署服务包：`test-agent-app`。
2. 前端规划为完全自研，不引入外部 Web 项目作为页面基础。
3. 所有前端请求通过 `backend-api` 调用平台后端。
4. 所有前端实时输出通过 RunEvent SSE。
5. 每个阶段都有明确功能、修改项目和验收方式。

## 修改项目

- `docs/implementation-plan.md`
- `docs/frontend/*`
- `docs/api/*`
- `docs/architecture/dependency-rules.md`
- `backend/*`
- 未来 `frontend/*`
- `tools/*`

## 阶段拆分

1. Phase 01：后端领域模型和契约。
2. Phase 02：持久化和执行节点路由。
3. Phase 03：opencode client 与事件体系。
4. Phase 04：后端 API 运行时。
5. Phase 05：本地集成和开发环境。
6. Phase 06：前端基础工程。
7. Phase 07：工作台、文件树和编辑器。
8. Phase 08：Agent 对话、Diff 和运行 MVP。
9. Phase 09：测试报告和 Skill Studio。
10. Phase 10：端到端加固和发布验收。
11. Phase 11：opencode Web 交互功能复刻（会话、对话、工具、Diff、权限、提问、Agent、上下文、斜杠命令、终端、MCP 等）。

## 验收方式

- `tools/verify-ai-docs.sh` 通过。
- `tools/verify-backend-skeleton.sh` 通过。
- 后端 `mvn clean package -DskipTests` 通过。
- 创建前端后，前端 lint、typecheck、unit test、e2e test 按阶段逐步纳入。

## 文档同步

任一阶段变更必须同步对应 README、PACKAGE.md、API 文档、事件文档和本阶段计划。
