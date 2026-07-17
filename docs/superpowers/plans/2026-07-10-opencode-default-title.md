# OpenCode 默认会话标题 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让新建 OpenCode 根会话保留默认标题，以触发其内置 AI 会话命名。

**Architecture:** 平台 Session 保持现有临时标题；`AgentRuntimeTargetResolver` 只改变远端创建参数。通用命令允许可选标题，SDK 网关仅在有标题时发送该字段，既有 `session.updated` 同步不变。

**Tech Stack:** Java 21、Spring WebFlux、JUnit 5、Mockito、OpenCode HTTP API。

---

## Chunk 1: 远端会话创建参数

### Task 1: 覆盖默认标题创建行为

**Files:**
- Modify: `backend/test-agent-agent-runtime/src/main/java/com/enterprise/testagent/agent/runtime/AgentCreateSessionCommand.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/runtime/AgentRuntimeTargetResolver.java`
- Modify: `backend/test-agent-opencode-client/src/main/java/com/enterprise/testagent/opencode/client/OpencodeCreateSessionCommand.java`
- Modify: `backend/test-agent-opencode-client/src/main/java/com/enterprise/testagent/opencode/client/GeneratedOpencodeSdkGateway.java`
- Modify: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/run/RunApplicationServiceTest.java`
- Modify: `backend/test-agent-opencode-client/src/test/java/com/enterprise/testagent/opencode/client/GeneratedOpencodeSdkGatewayTest.java`
- Create: `backend/test-agent-agent-runtime/src/test/java/com/enterprise/testagent/agent/runtime/AgentCreateSessionCommandTest.java`
- Create: `backend/test-agent-opencode-client/src/test/java/com/enterprise/testagent/opencode/client/OpencodeCreateSessionCommandTest.java`
- Modify: `backend/test-agent-agent-runtime/README.md`
- Modify: `backend/test-agent-opencode-client/README.md`
- Modify: `backend/test-agent-opencode-runtime/README.md`

- [x] **Step 1: 写入失败测试**

为首次或重建的用户远端会话断言 `AgentCreateSessionCommand.title()` 为 `null`；为两个 command record 增加 `null`、空白标题归一化为缺失及显式标题保持的断言；为 SDK gateway 增加无标题时请求体为 `{}` 的断言。

- [x] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME=/Users/kaka/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home PATH="$JAVA_HOME/bin:$PATH" mvn -pl test-agent-agent-runtime,test-agent-opencode-runtime,test-agent-opencode-client -am -Dtest=AgentCreateSessionCommandTest,OpencodeCreateSessionCommandTest,RunApplicationServiceTest,GeneratedOpencodeSdkGatewayTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: 新增断言因当前标题必填且被透传而失败。

- [x] **Step 3: 最小实现**

允许 `AgentCreateSessionCommand` 与 `OpencodeCreateSessionCommand` 的标题为空；在根会话创建处传入 `null`；在 `GeneratedOpencodeSdkGateway.createSession` 中按标题是否存在构造空请求体或含 `title` 的请求体。

- [x] **Step 4: 运行定向测试确认通过**

运行与 Step 2 相同的命令，确认相关测试通过。

- [ ] **Step 5: 同步文档、启动与提交**

更新 `backend/test-agent-agent-runtime/README.md`、`backend/test-agent-opencode-client/README.md` 与 `backend/test-agent-opencode-runtime/README.md`，说明创建根会话可不传标题以触发 OpenCode 内置命名；不修改事件 wire 文档，因为既有 `session.updated` 语义未变。运行 `git diff --check`，使用 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 重启，验证 health/readiness 与新建会话的标题事件；提交中文 commit。
