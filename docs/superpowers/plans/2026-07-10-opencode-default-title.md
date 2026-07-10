# OpenCode 默认会话标题 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让新建 OpenCode 根会话保留默认标题，以触发其内置 AI 会话命名。

**Architecture:** 平台 Session 保持现有临时标题；`AgentRuntimeTargetResolver` 只改变远端创建参数。通用命令允许可选标题，SDK 网关仅在有标题时发送该字段，既有 `session.updated` 同步不变。

**Tech Stack:** Java 21、Spring WebFlux、JUnit 5、Mockito、OpenCode HTTP API。

---

## Chunk 1: 远端会话创建参数

### Task 1: 覆盖默认标题创建行为

**Files:**
- Modify: `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/runtime/OpencodeRuntimeApplicationServiceTest.java`
- Modify: `backend/test-agent-opencode-client/src/test/java/com/icbc/testagent/opencode/client/GeneratedOpencodeSdkGatewayTest.java`

- [ ] **Step 1: 写入失败测试**

为首次或重建的用户远端会话断言 `AgentCreateSessionCommand.title()` 为 `null`；为 SDK gateway 增加无标题时请求体为 `{}` 的断言。

- [ ] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME=/Users/kaka/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home PATH="$JAVA_HOME/bin:$PATH" mvn -pl test-agent-opencode-runtime,test-agent-opencode-client -am -Dtest=OpencodeRuntimeApplicationServiceTest,GeneratedOpencodeSdkGatewayTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: 新增断言因当前标题必填且被透传而失败。

- [ ] **Step 3: 最小实现**

允许 `AgentCreateSessionCommand` 与 `OpencodeCreateSessionCommand` 的标题为空；在根会话创建处传入 `null`；在 `GeneratedOpencodeSdkGateway.createSession` 中按标题是否存在构造空请求体或含 `title` 的请求体。

- [ ] **Step 4: 运行定向测试确认通过**

运行与 Step 2 相同的命令，确认相关测试通过。

- [ ] **Step 5: 同步文档、启动与提交**

更新 runtime/client README 和事件说明（仅在需要时），运行 `git diff --check`，使用 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 重启，验证 health/readiness 与新建会话的标题事件；提交中文 commit。
