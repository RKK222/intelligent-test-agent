# Phase 00 总体路线图详细设计

## 目标

Phase 00 不新增业务运行时代码，负责把平台演进边界固化为可验收路线图：后端保持单可部署服务包 `test-agent-app`，前端完全自研，所有前端 HTTP 和实时事件都经过平台后端契约。

## 设计决策

- 后端继续采用 Maven multi-module，只有 `test-agent-app` 产出可执行 Spring Boot jar。
- `test-agent-opencode-sdk-generated` 仅保存 generated SDK，业务代码只能通过后续 `test-agent-opencode-client` facade 间接使用。
- 前端目录在后续 Phase 06 创建；当前只维护 `docs/frontend/*` 契约和边界。
- 所有实时输出统一抽象为平台 RunEvent SSE，不把 opencode raw event 直接暴露给前端。

## 阶段验收

- 文档验收：`tools/verify-ai-docs.sh`。
- 后端骨架验收：`tools/verify-backend-skeleton.sh`。
- 后端构建验收必须显式使用 JDK 21：

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package -DskipTests
```

## 后续阶段衔接

Phase 01 先建立领域模型、统一响应、错误和 traceId 契约；Phase 02 再落数据库和路由持久化；Phase 04 才实现真实对外 Runtime API。该顺序避免前端和持久化提前耦合不稳定模型。
