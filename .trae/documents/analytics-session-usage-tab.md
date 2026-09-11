# 运营分析新增「会话消息」Tab 实现计划

目标分支：`release`（不新增部署节点，复用现有 PostgreSQL 业务库）。

## Summary

在「运营分析」页面新增一个 Tab，展示**所选时间范围内，每位用户在每个会话中发送的用户消息条数**，统计口径与 [tools/query-user-message-statistics.sql](file:///Users/guo/Developer/intelligent-test-agent/tools/query-user-message-statistics.sql) 完全一致。

- 表格列：`用户名`、`会话名`、`用户消息数`、`首次发送时间`、`最后发送时间`。
- 会话名超长时单元格内截断（省略号），鼠标悬浮通过 `title` 显示完整文本。
- 筛选完全复用运营分析现有表单：时间范围（开始/结束/快速范围）、机构、研发部、部门、用户关键字；服务端分页，每页 20 条。
- 该 Tab 的口径等价于统计 SQL 中「该用户对话区间总次数」+ 该对话的首次/末次发送时间（去掉 SQL 的一次性“双周期”固定窗口，改用表单时间范围）。

## Current State Analysis

- 页面：[AnalyticsManagementPanel.vue](file:///Users/guo/Developer/intelligent-test-agent/frontend/apps/agent-web/src/components/system/AnalyticsManagementPanel.vue) 单文件承载筛选表单 + 7 个 Tab；`TabKey` 联合类型 + `activeTab` 切换；明细类 Tab（users/satisfaction/exceptions）走服务端分页 `PageResponse`，各自持有页码并在筛选变化时 `resetListPages()` 回第 1 页。
- API 客户端：[backend-api/index.ts](file:///Users/guo/Developer/intelligent-test-agent/frontend/packages/backend-api/src/index.ts#L2654-L2681) 中 `getAnalytics*` 统一走 `${analyticsBase}`。
- DTO：[shared-types/index.ts](file:///Users/guo/Developer/intelligent-test-agent/frontend/packages/shared-types/src/index.ts#L1486-L1516) 定义 `AnalyticsQueryParams`，明细行类型在 L1817 起。
- HTTP 入口：[AnalyticsController.java](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/AnalyticsController.java) 每端点只做 `SUPER_ADMIN` 鉴权 + `service.filter(params)` 规范化 + 调 `AnalyticsQueryService`。
- 领域模型/端口：[AnalyticsModels.java](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-domain/src/main/java/com/enterprise/testagent/domain/analytics/AnalyticsModels.java)、[AnalyticsRepository.java](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-domain/src/main/java/com/enterprise/testagent/domain/analytics/AnalyticsRepository.java)。
- 现有 `AnalyticsRepository` 的两个运行时 Bean 是 ClickHouse 版与「未启用即 503」版（[ClickHouseAnalyticsRepository.java](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/clickhouse/ClickHouseAnalyticsRepository.java)、[UnavailableAnalyticsRepository.java](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/UnavailableAnalyticsRepository.java)），都受 `test-agent.analytics.clickhouse.enabled` 开关约束。
- 业务库相关表：`sessions(session_id, created_by_user_id, source_type, title)`、`session_messages(message_id, session_id, run_id, role, created_at, source_type, sender_user_id)`、`runs(run_id, session_id, created_at, storage_mode, source_type, message_sender_user_id, triggered_by_user_id)`、`users(user_id, username, unified_auth_id, organization, rd_department, department, status)`。`source_type` 取值 `MANUAL/SCHEDULED_TASK/SIDE_QUESTION`，`runs.storage_mode` 取值 `LEGACY_FULL/REDIS_SUMMARY`。
- MyBatis 配置：PostgreSQL `classpath*:mybatis/**/*.xml`，`map-underscore-to-camel-case=true`（[application.yml](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-app/src/main/resources/application.yml#L49-L52)）。新增 XML 放 `mybatis/` 即被扫描。
- 现有硬规则：文档多处声明「运营分析 API 只读 ClickHouse、不扫描 PostgreSQL 业务事实」——[http-api.md](file:///Users/guo/Developer/intelligent-test-agent/docs/api/http-api.md#L242)、[opencode-runtime README](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-opencode-runtime/README.md#L179)、[api README](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-api/README.md#L87)。
- 既有测试参考：[AnalyticsQueryServiceTest.java](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/analytics/AnalyticsQueryServiceTest.java)、[ClickHouseAnalyticsIntegrationTest.java](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-persistence/src/test/java/com/enterprise/testagent/persistence/ClickHouseAnalyticsIntegrationTest.java)、PostgreSQL Testcontainers 集成测试范式见 [AnalyticsEventOutboxPostgresqlIntegrationTest.java](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-persistence/src/test/java/com/enterprise/testagent/persistence/AnalyticsEventOutboxPostgresqlIntegrationTest.java)、前端 [analytics-management-panel.test.ts](file:///Users/guo/Developer/intelligent-test-agent/frontend/apps/agent-web/tests/analytics-management-panel.test.ts)。

## 统计口径（与 SQL 对齐，必须逐条实现）

1. 时间：发送时间 `>= startTime` 且 `< endTime`（开始包含、结束不包含）。
2. `LEGACY_FULL` 分支：`session_messages.role='USER'`，每条计一次，发送时间取 `session_messages.created_at`；`COALESCE(runs.storage_mode,'LEGACY_FULL')='LEGACY_FULL'`；`COALESCE(runs.source_type, session_messages.source_type,'MANUAL')='MANUAL'`。
3. `REDIS_SUMMARY` 分支：`runs.storage_mode='REDIS_SUMMARY'`，每个 Run 锚点计一次，发送时间取 `runs.created_at`；`COALESCE(runs.source_type,'MANUAL')='MANUAL'`；与第 2 支按 `storage_mode` 互斥，禁止再叠加该 Run 的 USER 消息。
4. 两分支都排除 `COALESCE(sessions.source_type,'MANUAL')='SIDE_QUESTION'` 的会话；不计 AI 回复、不计 `SCHEDULED_TASK` 自动触发；定时会话中的后续人工发送（run 为 `MANUAL`）仍计入。
5. 归属回退链：`session_messages.sender_user_id → runs.message_sender_user_id → runs.triggered_by_user_id → sessions.created_by_user_id`；全空则归「未知用户」，`userId` 为空。
6. 汇总维度：按 `(user_id, session_id)` 分组；`用户消息数=COUNT(*)`，`首次/最后发送时间=MIN/MAX(sent_at)`；只输出有发送记录的分组，不补 0。
7. 不做 `users.status` 过滤（与 SQL 一致）。

## 数据来源与规则例外（需明确记录并同步文档）

该口径依赖业务库的 `storage_mode`、业务 `source_type` 与跨表归属链，ClickHouse 事实表未采集这些字段，无法复现。因此**该 Tab 直连平台 PostgreSQL**（`sessions`/`session_messages`/`runs`/`users`）。

这会突破既有「运营分析只能读 ClickHouse」规则。按用户明确要求，作为**记录在案的例外**：仅新增的 `/sessions` 端点读业务库，其余运营分析端点仍只读 ClickHouse。实现完成后同步修订上述三处文档措辞。

## Proposed Changes

### 后端

1. **领域模型**（[AnalyticsModels.java](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-domain/src/main/java/com/enterprise/testagent/domain/analytics/AnalyticsModels.java)）
   ```java
   public record SessionUsageRow(
           String userId,
           String username,
           String sessionId,
           String sessionTitle,
           long userMessageCount,
           Instant firstMessageAt,
           Instant lastMessageAt) {
   }
   ```

2. **领域端口**（新文件 `domain/analytics/AnalyticsSessionUsageRepository.java`）——独立于 `AnalyticsRepository` 与 ClickHouse 开关，始终可用：
   ```java
   public interface AnalyticsSessionUsageRepository {
       PageResponse<AnalyticsModels.SessionUsageRow> sessionMessageUsage(AnalyticsModels.Filter filter);
   }
   ```

3. **持久化行模型**（新文件 `persistence/mybatis/AnalyticsSessionUsageRow.java`，构造器映射，CH 不涉及）
   ```java
   public record AnalyticsSessionUsageRow(
           String userId, String username, String sessionId, String sessionTitle,
           long userMessageCount, Instant firstMessageAt, Instant lastMessageAt) {}
   ```

4. **MyBatis Mapper**（新文件 `persistence/mybatis/AnalyticsSessionUsageMapper.java` + `resources/mybatis/AnalyticsSessionUsageMapper.xml`）
   - Mapper 方法：
     ```java
     List<AnalyticsSessionUsageRow> sessionMessageUsage(
         @Param("startInclusive") Instant startInclusive, @Param("endExclusive") Instant endExclusive,
         @Param("organization") String organization, @Param("rdDepartment") String rdDepartment,
         @Param("department") String department, @Param("userKeyword") String userKeyword,
         @Param("limit") int limit, @Param("offset") long offset);
     long countSessionMessageUsage(/* 同上，去掉 limit/offset */);
     ```
   - XML 用共享 `<sql id="userMessages">`（两条 UNION ALL 分支，口径见上）与 `<sql id="userFilters">`（机构/研发部/部门/用户关键字 + `LEFT JOIN users`），供分页 SELECT 与 COUNT SELECT 复用：
     ```sql
     -- 分页主查询
     select user_id, username, session_id, session_title,
            count(*) as user_message_count,
            min(sent_at) as first_message_at, max(sent_at) as last_message_at
     from ( <include refid="userMessages"/> ) um
     left join users u on u.user_id = um.user_id
     where 1 = 1 <include refid="userFilters"/>
     group by user_id, username, session_id, session_title
     order by user_message_count desc, last_message_at desc, session_id
     limit #{limit} offset #{offset}
     ```
     `username = COALESCE(u.username, um.user_id, '未知用户')`；`userFilters` 里 `userKeyword` 用 `ILIKE '%'||kw||'%'` 同时匹配 `u.username`/`u.unified_auth_id`/`um.user_id`（空/NULL 表示全部）。时间与 `role`/`storage_mode`/`source_type`/`SIDE_QUESTION` 过滤放在 `userMessages` 两支内，保证可下推。
   - COUNT 查询：`select count(*) from (select user_id, session_id from (userMessages) um left join users u ... where 机构... group by 1,2) t`，与主查询同一过滤与分组。

5. **仓储实现**（新文件 `persistence/mybatis/MyBatisAnalyticsSessionUsageRepository.java`，`@Repository`）
   - 计算 `pageSize/offset`（同 `AnalyticsRepository` 明细实现写法），调 mapper 取当前页 + total，映射为 `AnalyticsModels.SessionUsageRow`，返回 `PageResponse`。

6. **查询服务**（新文件 `opencode-runtime/.../analytics/AnalyticsSessionUsageQueryService.java`）
   ```java
   @Service
   public class AnalyticsSessionUsageQueryService {
       public PageResponse<AnalyticsModels.SessionUsageRow> sessionMessageUsage(AnalyticsModels.Filter filter) {
           return repository.sessionMessageUsage(filter);
       }
   }
   ```
   - 独立成类以隔离「业务库例外」，且不改动 `AnalyticsQueryService` 构造器（其两个测试文件的约 13 处 `new AnalyticsQueryService(new FakeAnalyticsRepository(...))` 无需改动）。

7. **Controller**（[AnalyticsController.java](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/AnalyticsController.java)）
   - 构造器追加注入 `AnalyticsSessionUsageQueryService`（沿用已有 `AnalyticsQueryService.filter(params)` 生成校验过的 `Filter`）：
     ```java
     @GetMapping("/sessions")
     public ApiResponse<Object> sessions(QueryParams params, ServerWebExchange exchange) {
         requireSuperAdmin(exchange);
         return ApiResponse.ok(sessionUsageService.sessionMessageUsage(filter(params)),
                 RuntimeApiSupport.traceId(exchange));
     }
     ```
   - 路由：`GET /api/internal/platform/analytics/sessions`。

### 前端

8. **类型**（[shared-types/index.ts](file:///Users/guo/Developer/intelligent-test-agent/frontend/packages/shared-types/src/index.ts)）
   ```ts
   export type AnalyticsSessionUsageRow = {
     userId?: string | null;
     username?: string | null;
     sessionId: string;
     sessionTitle?: string | null;
     userMessageCount: number;
     firstMessageAt?: string | null;
     lastMessageAt?: string | null;
   };
   ```

9. **API 客户端**（[backend-api/index.ts](file:///Users/guo/Developer/intelligent-test-agent/frontend/packages/backend-api/src/index.ts)）
   ```ts
   getAnalyticsSessionUsage: (params: AnalyticsQueryParams = {}) =>
     request<PageResponse<AnalyticsSessionUsageRow>>(`${analyticsBase}/sessions${query({ ...params })}`),
   ```
   并在文件顶部类型导入中加入 `AnalyticsSessionUsageRow`。

10. **面板**（[AnalyticsManagementPanel.vue](file:///Users/guo/Developer/intelligent-test-agent/frontend/apps/agent-web/src/components/system/AnalyticsManagementPanel.vue)）
    - `TabKey` 追加 `"sessions"`；新增 `sessionsPage` 页码 ref 与 `sessionParams`（`...params` + `page: sessionsPage`）。
    - 新增 `sessionUsageQuery`（`enabled: () => activeTab === "sessions"`），并接入 `refresh()`、`resetListPages()`，新增 `changeSessionPage()`。
    - 在「用户运营」之后插入 Tab 按钮：`<button :class="{ active: activeTab === 'sessions' }" @click="activeTab = 'sessions'">会话消息</button>`。
    - 新增 `<section v-else-if="activeTab === 'sessions'">`：标题「会话消息统计」+ 表头 `用户名 / 会话名 / 用户消息数 / 首次发送时间 / 最后发送时间`；用户名取 `row.username || row.userId || '未知用户'`；时间用 `new Date(x).toLocaleString('zh-CN')`；会话名单元格：
      ```html
      <span class="ta-ellipsis" :title="row.sessionTitle || '-'">{{ row.sessionTitle || '-' }}</span>
      ```
    - 分页条与 users 一致：`v-if="(sessionUsageQuery.data.value?.total ?? 0) > SERVER_PAGE_SIZE"`，`total` 用服务端返回。
    - 样式：`.ta-ellipsis { display:inline-block; max-width:320px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; vertical-align:bottom; }`。

### 文档（必须同步）

11. [docs/api/http-api.md](file:///Users/guo/Developer/intelligent-test-agent/docs/api/http-api.md)：运营分析表新增 `GET /sessions` 行（返回 `PageResponse<AnalyticsSessionUsageRow>`，说明按 `用户 × 会话` 统计用户消息条数）；修订 L242 段落，明确「仅 `/sessions` 读业务库」的例外边界；测试清单补新增测试类。
12. [backend/test-agent-opencode-runtime/README.md](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-opencode-runtime/README.md#L179)、[backend/test-agent-api/README.md](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-api/README.md#L87)：把「不得扫描 PostgreSQL 业务事实」改为「除 `/sessions` 会话消息统计外」。
13. [backend/test-agent-persistence/README.md](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-persistence/README.md)：补 `AnalyticsSessionUsageMapper` 口径说明。
14. [frontend/apps/agent-web/README.md](file:///Users/guo/Developer/intelligent-test-agent/frontend/apps/agent-web/README.md#L164)（运营分析 Tab 段落）、[backend-api README](file:///Users/guo/Developer/intelligent-test-agent/frontend/packages/backend-api/README.md#L63)、[shared-types README](file:///Users/guo/Developer/intelligent-test-agent/frontend/packages/shared-types/README.md#L42)：补新 Tab / 新方法 / 新 DTO，并注明「该 Tab 直连业务库」与其它 Tab 数据来源不同。

## Assumptions & Decisions

- **时间范围**取自表单（`startTime`/`endTime`），不使用统计 SQL 的固定双周期（`2026-08-31~09-08`、`09-09~当天`）；双周期只是那份一次性报表的交付形态。
- **附加列**仅「首次/最后发送时间」（用户已确认）；不额外展示机构/研发部/部门/Run 数/Token 等列。
- **机构/研发部/部门筛选**作用于实际发送人的当前 `users` 记录；未知用户（`users` 无匹配）在设置组织筛选时会被排除，与 SQL 关键词筛选语义一致。
- **不做 users.status 过滤**，与统计 SQL 保持一致（避免与 SQL 结果口径分叉）。
- **分页**服务端分页，`pageSize=20`；排序固定「用户消息数降序、最后发送时间降序、会话 ID」。
- **不纳入 `export-all`**：本轮不把新 Tab 加入多 Sheet 导出（用户未要求）。后续如需，需同步扩展 `AnalyticsQueryService.exportAllXlsx`。
- 该 Tab 在 ClickHouse 未启用时也能用（不依赖 ClickHouse 开关），这是「业务库例外」的必然结果。
- 不新增 Flyway migration；`session_messages(sender_user_id, role, created_at)` 与 `runs(created_at, ...)` 等既有索引已可支撑，不新增 DDL。
- 新增/修改的人工维护代码补中文注释；不修改 OpenCode 源码；不修改 `.env.local`。

## Verification

后端（`backend/` 目录）：
1. 编译与单测：`mvn -pl test-agent-persistence,test-agent-opencode-runtime,test-agent-api -am test`
   - `AnalyticsControllerTest`：新增 `/sessions` 的 `SUPER_ADMIN` 成功 + 非授权拒绝断言。
   - `AnalyticsSessionUsageQueryServiceTest`：fake 端口返回分页，断言透传与空结果。
2. 集成测试：新增 `AnalyticsSessionUsagePostgresqlIntegrationTest`（`@Testcontainers(disabledWithoutDocker = true)`，`postgres:16-alpine` + Flyway + `JdbcClient` 造数），覆盖：周期首日零点/末日最后一微秒、`endTime` 次秒排除、共享会话归属实际发送人、`REDIS_SUMMARY` 锚点去重（其 USER 消息不重复计）、`SCHEDULED_TASK`/`SIDE_QUESTION`/AI 回复排除、未知用户 `userId` 为空且 `username='未知用户'`、机构/研发部/部门/用户关键字筛选、分页与 total 一致。

前端（`frontend/` 目录）：
3. 类型检查：`corepack pnpm -r typecheck`（至少 `shared-types`、`backend-api`、`agent-web`）。
4. 单测：`corepack pnpm test`；在 [analytics-management-panel.test.ts](file:///Users/guo/Developer/intelligent-test-agent/frontend/apps/agent-web/tests/analytics-management-panel.test.ts) 的 `api()` 桩补 `getAnalyticsSessionUsage`，新增用例断言：点击「会话消息」渲染行、`.ta-ellipsis` 的 `title` 为完整会话名、翻页请求 `page=2`。

手动验收（按 `.env.test` 指向 `192.168.8.100:15432/testagent_dev`）：
5. `SUPER_ADMIN` 打开运营分析 →「会话消息」→ 切换时间范围/机构/研发部/部门/用户关键字，核对列表随筛选变化、分页正常、超长会话名截断且悬浮显示全称；与 [query-user-message-statistics.sql](file:///Users/guo/Developer/intelligent-test-agent/tools/query-user-message-statistics.sql) 在同区间、同人员下的「该用户对话区间总次数」抽样对齐。

完成标准（按 AGENTS.md）：列出改动文件、执行命令、已同步文档；说明新增了「仅 `/sessions` 读业务库」的例外；按需更新 `.agents/session-log.{id}.md` 并随提交保留；提交信息用中文。
