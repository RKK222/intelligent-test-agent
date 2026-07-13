# 标题监听 CLOSED 状态继续透传 RunEvent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复首轮标题同步后 `CLOSED` token 错误过滤主 Run 后续事件的问题。

**Architecture:** 保持标题监听现有状态机与订阅结构，只把事件白名单限制收窄到真正的标题等待状态。通过带延迟的 Run 事件序列复现生产时序，先证明旧实现失败，再做单条件最小修改。

**Tech Stack:** Java 21、Spring WebFlux、Reactor、JUnit 5、AssertJ、Maven。

## Global Constraints

- 不新增或修改 HTTP API、RunEvent wire name、数据库结构和 generated SDK。
- 不改变 `TITLE_WAIT/TITLE_READING` 的标题事件白名单和取消语义。
- 不修改前端、环境配置或无关模块。

---

### Task 1: CLOSED 状态事件透传回归与修复

**Files:**
- Modify: `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/run/RunApplicationServiceTest.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/RunApplicationService.java`
- Modify: `backend/test-agent-opencode-runtime/README.md`
- Modify: `.agents/session-log.md`

**Interfaces:**
- Consumes: `RunSessionTitleWatchRegistry.TitleWatchToken.state()` 和现有 `RunEventDraft` 流。
- Produces: `acceptsTitleWatchEvent` 对 `ACTIVE/CLOSED` 全量放行、对 `TITLE_WAIT/TITLE_READING` 保持既有白名单。

- [ ] **Step 1: 写出失败回归测试**

  在 `RunApplicationServiceTest` 构造按时间顺序到达的事件流：有效标题 `session.updated`、延迟的
  `message.updated`、延迟的 `run.succeeded`。断言标题已同步、message 已进入 `RecordingRunEventLiveBus`、
  Run 最终为 `SUCCEEDED`。

- [ ] **Step 2: 运行测试并确认 RED**

  Run:

  ```bash
  cd backend
  mvn -pl test-agent-opencode-runtime -am \
    -Dtest=RunApplicationServiceTest#serviceKeepsRoutingRunEventsAfterActiveTitleWatchCloses \
    -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  Expected: FAIL，旧实现关闭标题 token 后过滤延迟的 message 与终态。

- [ ] **Step 3: 写最小实现**

  将全量放行条件改为：

  ```java
  if (token == null
          || token.state() == RunSessionTitleWatchRegistry.State.ACTIVE
          || token.state() == RunSessionTitleWatchRegistry.State.CLOSED) {
      return true;
  }
  ```

- [ ] **Step 4: 运行目标测试并确认 GREEN**

  Run: 与步骤 2 相同。

  Expected: PASS。

- [ ] **Step 5: 同步文档并执行回归**

  Run:

  ```bash
  cd backend
  mvn -pl test-agent-opencode-runtime -am \
    -Dtest=RunApplicationServiceTest,RunSessionTitleWatchRegistryTest \
    -Dsurefire.failIfNoSpecifiedTests=false test
  mvn -pl test-agent-opencode-runtime -am -DskipTests package
  ```

  Expected: 测试和主代码构建均成功。

- [ ] **Step 6: 复核并提交**

  只暂存本计划列出的文件和设计/计划文档，执行 `git diff --check`，提交中文信息：

  ```bash
  git commit -m "修复标题同步后事件流被过滤"
  ```

