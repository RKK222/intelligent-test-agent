# OpenCode 会话标题兜底 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让快速结束的首轮 OpenCode 对话仍能得到原生 AI 会话标题。

**Architecture:** 保留 root `session.updated` 的原生优先级，过滤 OpenCode 默认标题；终态成功后用临时远端 session 同步调用同一个 `title` agent，并通过既有事件确认字段更新页面。

**Tech Stack:** Java 21、Spring WebFlux、JUnit 5、Mockito、OpenCode HTTP API。

---

## Chunk 1: 原生 title 调用与事件同步

### Task 1: 覆盖原生调用和首轮兜底

**Files:**

- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/runtime/OpencodeRuntimeApplicationService.java`
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/OpencodeSessionTitleProperties.java`
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/OpencodeSessionTitlePolicy.java`
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/RunSessionTitleFallbackService.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/RunApplicationService.java`
- Create: `backend/test-agent-domain/src/main/java/com/icbc/testagent/domain/session/SessionTitleUpdateRepository.java`
- Create: `backend/test-agent-persistence/src/main/java/com/icbc/testagent/persistence/mybatis/MyBatisSessionTitleUpdateRepository.java`
- Create: `backend/test-agent-persistence/src/main/java/com/icbc/testagent/persistence/mybatis/SessionTitleUpdateMapper.java`
- Create: `backend/test-agent-persistence/src/main/resources/mybatis/SessionTitleUpdateMapper.xml`
- Test: `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/runtime/OpencodeRuntimeApplicationServiceTest.java`
- Test: `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/run/RunApplicationServiceTest.java`
- Create: `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/run/RunSessionTitleFallbackServiceTest.java`
- Create: `backend/test-agent-persistence/src/test/java/com/icbc/testagent/persistence/MyBatisSessionTitleUpdateRepositoryIntegrationTest.java`

- [x] **Step 1: 写入失败测试**

覆盖默认时间戳标题不覆盖平台标题、临时 session 必定删除、原生标题已同步时不兜底、root Run 成功后调度兜底。

- [x] **Step 2: 运行测试确认失败**

新增测试先因缺少 `generateNativeSessionTitle`、兜底服务和默认标题过滤而编译失败或断言失败。

- [x] **Step 3: 最小实现**

通过既有 `AgentRuntime` 路由创建/轮询/删除临时 session；仅在用户首条消息所属的成功 root Run 触发；通过 MyBatis XML 条件更新保证标题仍为初始临时值，再将结果用已有 `session.updated` SSE 契约写回。

- [x] **Step 4: 运行定向测试确认通过**

运行 `mvn -pl test-agent-opencode-runtime -am -Dtest=RunApplicationServiceTest,RunSessionTitleFallbackServiceTest,OpencodeRuntimeApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，以及 `mvn -pl test-agent-persistence -am -Dtest=MyBatisSessionTitleUpdateRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`。

- [ ] **Step 5: 文档、真实启动与提交**

同步 runtime/后端/API 说明、session log；执行定向回归、打包检查、`git diff --check`，重启本地三服务并新建快速对话验证最终标题。
