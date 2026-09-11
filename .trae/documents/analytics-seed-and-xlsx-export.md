# 运营分析：本地 ClickHouse 造数 + xlsx 多 Sheet 导出

## Context（背景）

控制台"运营分析"页面当前所有 Tab 数据全为 0/空，原因有二：
1. 本地默认 `test-agent.analytics.clickhouse.enabled` 未设置，运营查询走 [UnavailableAnalyticsRepository](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/UnavailableAnalyticsRepository.java#L18)，**所有查询抛 `ANALYTICS_UNAVAILABLE`**。
2. 页面没有造数。

同时现有"导出 CSV"按钮按当前 Tab 单类型导出，列名是英文，且 CSV 无法体现网页的多 Tab 布局。

用户已确认两个需求方向：
- **需求 1**：本地起 ClickHouse，给每个 Tab 造可展示的数据。
- **需求 2**：导出改成一次导出所有 Tab，产物为 **xlsx 多 Sheet**，Sheet 名和列头用中文且与网页表格一致。

用户额外确认的两个决策：
- **不改 `.env.local`**：用 `restart-dev-services.sh --with-clickhouse` 自动加载生成的 ClickHouse env。
- **按钮文案改为「导出 Excel」**：产物从 CSV 改 xlsx，文案同步。

## 关键现状

- 已有 [tools/clickhouse-dev-services.sh](file:///Users/guo/Developer/intelligent-test-agent/tools/clickhouse-dev-services.sh) 一键管理本地 ClickHouse 容器（prepare/start/restart/stop/status），端口默认 18123，库名 `testagent_analytics`，生成的 backend env 写到 `.tmp/dev-services/clickhouse/clickhouse-backend.env`（0600）。
- ClickHouse 表 DDL 由 [ClickHouseSchemaMigrator](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/clickhouse/ClickHouseSchemaMigrator.java#L23) 在 Spring 启动时自动执行 `resources/db/clickhouse/*.sql`，**无需手工建表**。
- 已有表 DDL：[V20260813150000__analytics_activity_facts_create_tables.sql](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-persistence/src/main/resources/db/clickhouse/V20260813150000__analytics_activity_facts_create_tables.sql)。
- Apache POI 5.5.1 已是 backend 受管依赖（test-agent-workspace-management 在用），test-agent-api 未直接依赖，需要新增。
- 7 个 Tab：使用总览/用户运营/Token运营/能力使用/组织分析/满意度/异常Run。

## 实施方案

### Part A：本地 ClickHouse 启用 + 造数

#### A1. 启动本地 ClickHouse
- 执行 `tools/clickhouse-dev-services.sh restart`（含 prepare + pull + start + readiness 校验）。
- 脚本会生成 `.tmp/dev-services/clickhouse/clickhouse-backend.env`，含 `TEST_AGENT_ANALYTICS_CLICKHOUSE_ENABLED=true` 等变量。

#### A2. 让后端加载 ClickHouse env（不改 .env.local）
- [restart-dev-services.sh](file:///Users/guo/Developer/intelligent-test-agent/restart-dev-services.sh#L1301-L1350) 已内置 `--with-clickhouse`：加载主 `.env.local` 后，自动 source `.tmp/dev-services/clickhouse/clickhouse-backend.env`，把 4 个 ClickHouse 变量注入 Java 进程。
- 执行：`./restart-dev-services.sh --profile local --with-clickhouse`（重启后端 + opencode + 前端）。
- [backend/README.md](file:///Users/guo/Developer/intelligent-test-agent/backend/README.md#L103-L108) 已记录该机制：随机密码和 JDBC 配置只写入 `.tmp/dev-services/clickhouse`，不修改 `.env.local`。

#### A3. 重启后端让 ClickHouseSchemaMigrator 建表
- 上述 `--with-clickhouse` 重启时，Spring 启动 `ClickHouseSchemaMigrator.afterPropertiesSet()` 自动执行 DDL，建好所有 ClickHouse 表。

#### A4. 编写并执行造数 SQL 脚本
- 新建 `tools/seed-analytics-clickhouse.sh`（bash + clickhouse-client），作为**本地开发造数脚本**，不属于 Flyway migration（AGENTS.md 规则 14 禁止 Flyway 承载测试数据）。
- 脚本读取 `.tmp/dev-services/clickhouse/clickhouse-dev.env` 拿端口/库/账号/密码，用 `clickhouse-client` 执行 INSERT。
- 造数范围（覆盖全部 7 个 Tab，时间窗取最近 30 天）：

| ClickHouse 表 | 喂养的 Tab | 造数要点 |
|---|---|---|
| `analytics_user_activity_hourly` + `analytics_user_activity_daily` | 使用总览/用户运营/组织分析/Token运营/Run趋势/漏斗 | 3~5 个用户 × 多个小时 bucket，含 login/session/message/run/token/diff/feedback 全字段非零值，跨 3 个机构、2 个研发部、3 个部门 |
| `analytics_feedback_facts` | 满意度 | 8~12 条反馈，POSITIVE/NEGATIVE 各半，含 reason_code 和 comment |
| `analytics_activity_facts` | 异常Run + p95 | 10 条 FAILED + 5 条 CANCELLED + 若干 SUCCEEDED（带 duration_ms 供 p95 计算） |
| `analytics_capability_facts` | 能力使用 | AGENT/SKILL/TOOL 各 2~3 个，含 STARTED/SUCCEEDED/FAILED 状态 |
| `analytics_user_dimensions` | 用户数 + 筛选项 | 5~8 个用户维度，含 organization/rd_department/department 用于筛选项下拉 |
| `analytics_rollup_watermarks` | 新鲜度文案 | job_name=analytics-rollup，status=FRESH，generated_at=now |

- 脚本幂等：每次先 `TRUNCATE` 这几张表再插入，避免重复。
- 造完后**无需重启**，页面刷新即可看到数据（汇总表直接被查询，不走 rollup job）。

### Part B：xlsx 多 Sheet 导出

#### B1. 加 POI 依赖
- 编辑 [backend/test-agent-opencode-runtime/pom.xml](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-opencode-runtime/pom.xml)，加 `poi` + `poi-ooxml`（版本用 backend 根 pom 的 `${apache-poi.version}`，受管版本 5.5.1）。
- 注意：`exportAllXlsx` 在 `AnalyticsQueryService`，位于 test-agent-opencode-runtime 模块，因此 POI 依赖加在该模块而非 test-agent-api。

#### B2. 后端新增 xlsx 全量导出
- 在 [AnalyticsController.java](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/AnalyticsController.java#L119) 新增 `GET /api/internal/platform/analytics/export-all`，`produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"`，超管鉴权，调 service。
- 在 [AnalyticsQueryService.java](file:///Users/guo/Developer/intelligent-test-agent/backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/analytics/AnalyticsQueryService.java#L408) 新增 `exportAllXlsx(Filter)`，复用现有 `overview/timeseries/users/organizations/feedbackDetails/exceptionDetails/tokenOperations/capabilities` 查询方法，用 POI `XSSFWorkbook` 构建 7 个 Sheet：

| Sheet 名（中文） | 来源方法 | 列头（中文，与网页一致） |
|---|---|---|
| 使用总览 | `overview()` + `funnel()` + `timeseries()` + `hourlyHeatmap()` | 指标, 数值（KPI 表）；漏斗：阶段, 用户数, 转化率, 定义；Run 趋势：时间点, Run, 成功, 失败, 取消, 登录用户, 活跃用户, 用户消息, AI 回复；小时热力：日期 + 24 小时矩阵（用户消息 / 主 Token / 缓存 Token 三张） |
| 用户运营 | `users()` | 用户, 机构, 研发部, 部门, 登录, 会话, 消息, Run, 成功率, 满意率, Token |
| Token运营 | `tokenOperations()` | Token 汇总：指标, 数值, 说明（总 Token 使用量/日人均/使用率/重复使用率/缓存 Token，对应网页顶部 5 张卡）；每日 Token：日期, 使用用户, 总 Token, 日人均, 主 Token, 缓存读, 缓存写；用户排行：用户, 使用强度, Token 日, 总 Token, Token 日均 |
| 能力使用 | `capabilities()` | 类型, 名称, 使用率, 使用用户, 调用次数, 成功, 失败, 取消, 未完成 |
| 组织分析 | `organizations()` | 维度, 名称, 登录用户, 活跃用户, 深度用户, Run, 成功率, 满意率, Token |
| 满意度 | `feedbackDetails()` | 时间, 用户, 组织, 会话, Run, 反馈, 原因, 备注 |
| 异常Run | `exceptionDetails()` | 时间, Run, 用户, 组织, 状态 |

- 列头加粗 + 浅灰背景（`CellStyle` + `setFillForegroundColor(IndexedColors.GREY_25_PERCENT)`）以贴近网页表头样式。
- **导出取全量、不受网页分页限制**：网页明细/排行接口默认 `pageSize=20`、`topN=20`，导出改用 `unlimitedFilter` 把 page 归 1、topN 放宽到 `EXPORT_ROW_LIMIT`；明细类（用户/满意度/异常Run）因 `PageResponse` 单页上限 200，再通过 `collectAll` 翻页取满 total。
- 返回 `byte[]`，`Content-Disposition: attachment; filename=analytics-export.xlsx`。

#### B3. 前端调用改造
- [backend-api/src/index.ts](file:///Users/guo/Developer/intelligent-test-agent/frontend/packages/backend-api/src/index.ts#L2678) 新增 `exportAnalyticsXlsx(params)`，请求 `/export-all` 并返回 blob。
- [AnalyticsManagementPanel.vue](file:///Users/guo/Developer/intelligent-test-agent/frontend/apps/agent-web/src/components/system/AnalyticsManagementPanel.vue#L204) 改 `exportCsv()` → `exportAll()`：
  - 不再按 Tab 选 type，直接调 `exportAnalyticsXlsx(params.value)`。
  - 下载文件名 `运营分析-${YYYYMMDD}.xlsx`。
  - 按钮文案改为"导出 Excel"（原"导出 CSV"会误导，用户已确认）。

#### B4. 文档同步
- [docs/api/http-api.md](file:///Users/guo/Developer/intelligent-test-agent/docs/api/http-api.md) 增加 `/export-all` 接口说明。
- 不再修改 Flyway migration（本任务不涉及表结构变更）。

## 关键文件

| 文件 | 改动 |
|---|---|
| `.env.local` | **不改**（用 `--with-clickhouse` 启动加载生成 env） |
| `tools/seed-analytics-clickhouse.sh` | 新建本地造数脚本 |
| `backend/test-agent-opencode-runtime/pom.xml` | 加 POI 依赖 |
| `backend/test-agent-api/.../AnalyticsController.java` | 加 `/export-all` |
| `backend/test-agent-opencode-runtime/.../AnalyticsQueryService.java` | 加 `exportAllXlsx` |
| `frontend/packages/backend-api/src/index.ts` | 加 `exportAnalyticsXlsx` |
| `frontend/apps/agent-web/.../AnalyticsManagementPanel.vue` | 改导出按钮逻辑 |
| `docs/api/http-api.md` | 同步 API |

## 复用的现有实现
- `AnalyticsQueryService.overview/funnel/timeseries/users/organizations/tokenOperations/capabilities/feedbackDetails/exceptionDetails` — 直接复用查询方法，不重写数据获取逻辑。
- `tools/clickhouse-dev-services.sh` — 复用本地 ClickHouse 生命周期管理。
- backend 根 pom 的 `apache-poi.version=5.5.1` 受管依赖。

## 验证方式
1. `tools/clickhouse-dev-services.sh status` 确认 ClickHouse 就绪。
2. 重启后端，看日志出现 `ClickHouse schema migration` 成功。
3. `tools/seed-analytics-clickhouse.sh` 执行后，浏览器刷新运营分析页，**7 个 Tab 均有非零数据**，顶部 6 个 KPI 卡片有值。
4. 点"导出 Excel"，下载 `运营分析-YYYYMMDD.xlsx`，打开后**7 个 Sheet**，Sheet 名/列头全中文且与网页一致，数据与页面一致。
5. `mvn -pl backend/test-agent-api test` 编译通过。
6. 前端 `pnpm --filter @test-agent/agent-web build` 通过。

## 风险与未完成项
- **不改 `.env.local`**：已确认走 `restart-dev-services.sh --with-clickhouse` 自动加载生成的 ClickHouse env，符合 AGENTS.md 规则 21。
- 造数脚本只覆盖本地开发，不进 Flyway（规则 14）。
- capabilities Tab 的 CH 查询会优先读 `analytics_trace_catalog`，回退 `analytics_capability_facts`；若 trace_catalog 为空，走 capability_facts 即可，脚本会同时灌 capability_facts。
- p95 由 CH `quantileExact(0.95)(duration_ms)` 直接算，不依赖 histogram 表，所以 `analytics_activity_facts` 带 duration_ms 即可。
