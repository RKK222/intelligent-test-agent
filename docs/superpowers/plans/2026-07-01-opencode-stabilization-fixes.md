# OpenCode Stabilization Fixes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 OpenCode 状态宽限、启动失败状态和文件树跨 Workspace 重试竞态，同时保留 OpenCode 原生 Agent/Skill 发现与执行机制。

**Architecture:** Java 继续通过 `OpencodeProcessManagerGateway` 控制 Manager，普通 Agent/Skill/runtime 请求继续通过 `AgentRuntime` 调用 OpenCode 原生 API。状态层仅增加基于最后成功探测时间的短暂宽限，不新增 Skill/Agent 扫描或匹配逻辑。

**Tech Stack:** Java 21、JUnit 5、Vue 3、TypeScript、Vitest、Go。

---

### Task 1: 状态和启动收敛

**Files:**
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/process/UserOpencodeProcessAssignmentService.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/process/OpencodeProcessStartupService.java`
- Test: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/process/UserOpencodeProcessAssignmentServiceTest.java`
- Test: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/process/OpencodeProcessStartupServiceTest.java`

- [ ] 新增失败测试：STALE 只在最后成功探测后的短暂窗口内放行。
- [ ] 新增失败测试：Manager 控制面异常不进入启动轮询。
- [ ] 运行定向测试确认失败原因。
- [ ] 实现统一宽限判断和启动失败最终状态回写。
- [ ] 运行定向测试确认通过。

### Task 2: 文件树重试隔离

**Files:**
- Modify: `frontend/apps/agent-web/src/components/workbench-utils.ts`
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`
- Test: `frontend/apps/agent-web/tests/workbench-utils.test.ts`

- [ ] 新增失败测试：Workspace 代际变化后旧重试不可应用。
- [ ] 实现代际判断并在 Workspace reset 时取消旧定时器。
- [ ] 运行前端测试、类型检查和构建。

### Task 3: 文档、运行和提交

**Files:**
- Modify: `backend/test-agent-opencode-runtime/README.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `.agents/session-log.md`

- [ ] 记录状态宽限、原生 Agent/Skill 边界和文件树重试行为。
- [ ] 运行 Java、Go、前端回归。
- [ ] 按项目本地启动流程启动服务并检查健康状态。
- [ ] 仅暂存本任务文件，复核 session log 后使用中文提交信息提交。
