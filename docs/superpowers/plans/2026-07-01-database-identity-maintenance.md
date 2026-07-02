# 数据库 IDENTITY 运维功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在用户管理面板新增超管可用的"数据库 IDENTITY 查询 + 手工滚动"运维入口，覆盖 users / user_roles / dictionaries / user_login_logs 四张白名单表，修复并预防 identity 序列落后于已有主键导致新增用户报"数据冲突"的问题。

**Architecture:** 后端按端口适配器模式分层（依赖规则第 11 条：业务模块不得直接访问 MyBatis mapper/persistence 实现，只能通过 domain 端口）：
- **test-agent-domain**：`IdentityManagedTable` 白名单枚举 + `DatabaseIdentityMaintenancePort` 端口接口 + `IdentityStatus` 域记录（端口返回类型）。
- **test-agent-persistence**：`DatabaseIdentityMapper`（MyBatis 接口）+ `DatabaseIdentityMapper.xml`（SQL）+ `MyBatisDatabaseIdentityMaintenanceRepository`（实现 `DatabaseIdentityMaintenancePort`，包装 mapper）。
- **test-agent-system-management**：`DatabaseIdentityMaintenanceService` 依赖 `DatabaseIdentityMaintenancePort`（domain 端口），含白名单校验+编排+审计日志；`DatabaseIdentityResponses`（对外 DTO）。
- **test-agent-api**：`DatabaseIdentityController`（仅 SUPER_ADMIN）+ `DatabaseIdentityDtos`（请求 DTO）。
- 前端在 `SettingsUserManagementPanel.vue` 新增折叠区块，通过 `backend-api` 新增 3 个方法调用。

**Tech Stack:** Java 21 + Spring WebFlux + MyBatis（XML mapper）+ PostgreSQL（`pg_get_serial_sequence` / `pg_sequences` / `ALTER TABLE ... RESTART WITH`）；Vue 3 + Element Plus + Vitest + Testing Library。

**关键约束（AGENTS.md）：** 第 24 条——新增 SQL 必须走 MyBatis XML mapper，不得新增 JDBC SQL；第 5 条——API 必须同步 `docs/api/http-api.md`；第 13 条——本功能不改表结构，无需 Flyway migration。

**SQL 注入防护：** 表名 `${tableName}` 必须由 Service 传入白名单枚举的真实表名常量；目标值 `${targetValue}` 由 Service 层 `Long` 校验（正整数且 `> maxId`）后拼接。其余参数用 `#{}` 绑定。

**H2 测试限制：** 现有 mapper 集成测试用 H2（PostgreSQL 模式）。H2 支持 `ALTER TABLE ... RESTART WITH` 和 `select max(id)`，但**不支持** PG 的 `pg_get_serial_sequence` / `pg_sequences`。因此：mapper 的"对齐/重启"SQL 用 H2 集成测试覆盖；"查询当前值"的 PG 专用 SQL 仅靠 Service 层 mock 测试 + 真实 PG 手工验证（见 Task 10），不在 H2 集成测试断言其返回值。

---

## 文件结构

**新建（后端）：**
- `backend/test-agent-domain/src/main/java/com/icbc/testagent/domain/maintenance/IdentityManagedTable.java` — 白名单枚举，持有真实表名（domain 概念，persistence 与 system-management 均可见）。
- `backend/test-agent-domain/src/main/java/com/icbc/testagent/domain/maintenance/IdentityStatus.java` — 域记录，端口返回类型（含 currentValue/maxId/conflict）。
- `backend/test-agent-domain/src/main/java/com/icbc/testagent/domain/maintenance/DatabaseIdentityMaintenancePort.java` — 端口接口（queryIdentityStatus / restartIdentity）。
- `backend/test-agent-persistence/src/main/java/com/icbc/testagent/persistence/mybatis/DatabaseIdentityMapper.java` — MyBatis mapper 接口。
- `backend/test-agent-persistence/src/main/java/com/icbc/testagent/persistence/mybatis/MyBatisDatabaseIdentityMaintenanceRepository.java` — 端口实现，包装 mapper 并把 Map 行映射为 IdentityStatus。
- `backend/test-agent-persistence/src/main/resources/mybatis/DatabaseIdentityMapper.xml` — SQL。
- `backend/test-agent-system-management/src/main/java/com/icbc/testagent/system/management/maintenance/DatabaseIdentityResponses.java` — 对外 DTO（`IdentityStatusDto` + Command 记录）。
- `backend/test-agent-system-management/src/main/java/com/icbc/testagent/system/management/maintenance/DatabaseIdentityMaintenanceService.java` — 应用服务，依赖 domain 端口。
- `backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/DatabaseIdentityController.java` — HTTP 入口。
- `backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/DatabaseIdentityDtos.java` — 请求 DTO。

**新建（测试）：**
- `backend/test-agent-system-management/src/test/java/com/icbc/testagent/system/management/maintenance/DatabaseIdentityMaintenanceServiceTest.java`
- `backend/test-agent-api/src/test/java/com/icbc/testagent/api/web/platform/DatabaseIdentityControllerTest.java`
- `backend/test-agent-persistence/src/test/java/com/icbc/testagent/persistence/MyBatisDatabaseIdentityMapperIntegrationTest.java`

**修改（前端）：**
- `frontend/packages/shared-types/src/index.ts` — 新增 `IdentityStatus` 类型。
- `frontend/packages/backend-api/src/index.ts` — 新增 3 个 API 方法。
- `frontend/apps/agent-web/src/components/settings/SettingsUserManagementPanel.vue` — 新增折叠区块。
- `frontend/apps/agent-web/tests/settings-user-management-panel.test.ts` — 新增测试。

**修改（文档）：**
- `docs/api/http-api.md` — 新增 3 接口。
- `docs/deployment/database.md` — 新增 IDENTITY 运维说明段。
- `backend/test-agent-system-management/README.md`、`backend/test-agent-persistence/README.md` — 登记新职责。
- `.agents/session-log.md` — 收尾记录。

---

### Task 1: 白名单枚举 IdentityManagedTable

**Files:**
- Create: `backend/test-agent-system-management/src/main/java/com/icbc/testagent/system/management/maintenance/IdentityManagedTable.java`

- [ ] **Step 1: 创建枚举**

```java
package com.icbc.testagent.system.management.maintenance;

/**
 * 受运维管理的 identity 主键表白名单。
 * 枚举名对外暴露给前端，真实表名仅用于拼接白名单内 SQL，杜绝任意表名注入。
 */
public enum IdentityManagedTable {

    USERS("users"),
    USER_ROLES("user_roles"),
    DICTIONARIES("dictionaries"),
    USER_LOGIN_LOGS("user_login_logs");

    private final String tableName;

    IdentityManagedTable(String tableName) {
        this.tableName = tableName;
    }

    /** 真实物理表名，已限定为本枚举常量，可安全拼入 SQL。 */
    public String tableName() {
        return tableName;
    }

    /**
     * 大小写无关解析表名为枚举，非白名单返回空 Optional。
     */
    public static java.util.Optional<IdentityManagedTable> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return java.util.Optional.empty();
        }
        for (IdentityManagedTable table : values()) {
            if (table.name().equalsIgnoreCase(code)) {
                return java.util.Optional.of(table);
            }
        }
        return java.util.Optional.empty();
    }
}
```

- [ ] **Step 2: 编译确认**

Run: `cd backend && ./mvnw -q -pl test-agent-system-management -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/test-agent-system-management/src/main/java/com/icbc/testagent/system/management/maintenance/IdentityManagedTable.java
git commit -m "新增 identity 运维白名单枚举 IdentityManagedTable"
```

---

### Task 2: 响应 DTO DatabaseIdentityResponses

**Files:**
- Create: `backend/test-agent-system-management/src/main/java/com/icbc/testagent/system/management/maintenance/DatabaseIdentityResponses.java`

- [ ] **Step 1: 创建 DTO 与 Command 记录**

参考 `UserManagementResponses` 的 record 风格。

```java
package com.icbc.testagent.system.management.maintenance;

import java.time.Instant;

/**
 * identity 运维相关的响应 DTO 与命令记录。
 */
public final class DatabaseIdentityResponses {

    private DatabaseIdentityResponses() {
    }

    /**
     * 单张表的 identity 状态快照。
     *
     * @param table        枚举名，如 USERS
     * @param tableName    真实表名，如 users（仅展示）
     * @param currentValue identity 序列当前 last_value，表空或序列未初始化时为 null
     * @param maxId        表中当前 max(id)，表空时为 null
     * @param conflict     序列落后于已有主键（currentValue < maxId）时为 true
     * @param lastUpdatedAt 本次查询时间
     */
    public record IdentityStatusDto(
            String table,
            String tableName,
            Long currentValue,
            Long maxId,
            boolean conflict,
            Instant lastUpdatedAt) {
    }

    /** 手动 RESTART WITH 的命令。 */
    public record RestartIdentityCommand(IdentityManagedTable table, long targetValue) {
    }
}
```

- [ ] **Step 2: 编译确认**

Run: `cd backend && ./mvnw -q -pl test-agent-system-management -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/test-agent-system-management/src/main/java/com/icbc/testagent/system/management/maintenance/DatabaseIdentityResponses.java
git commit -m "新增 identity 运维响应 DTO 与命令记录"
```

---

### Task 3: MyBatis Mapper 接口

**Files:**
- Create: `backend/test-agent-persistence/src/main/java/com/icbc/testagent/persistence/mybatis/DatabaseIdentityMapper.java`

- [ ] **Step 1: 创建 mapper 接口**

参考 `CommonParameterMapper` 的 `@Mapper` + `@Param` 风格。

```java
package com.icbc.testagent.persistence.mybatis;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * identity 运维 MyBatis mapper；SQL 维护在 DatabaseIdentityMapper.xml。
 *
 * <p>注意：tableName/targetValue 使用 ${} 拼接，调用方（DatabaseIdentityMaintenanceService）
 * 必须保证 tableName 来自白名单枚举常量、targetValue 为正整数且 > maxId，杜绝注入。
 */
@Mapper
public interface DatabaseIdentityMapper {

    /**
     * 查询一张表的 identity 序列当前值（last_value）与表中 max(id)。
     * 返回 Map 含 currentValue（可能 null）、maxId（可能 null）。
     */
    Map<String, Object> queryIdentityStatus(@Param("tableName") String tableName);

    /**
     * 把指定表 identity 重置到目标值：ALTER TABLE ... ALTER COLUMN id RESTART WITH targetValue。
     */
    int restartIdentity(@Param("tableName") String tableName, @Param("targetValue") long targetValue);
}
```

- [ ] **Step 2: 编译确认**

Run: `cd backend && ./mvnw -q -pl test-agent-persistence -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/test-agent-persistence/src/main/java/com/icbc/testagent/persistence/mybatis/DatabaseIdentityMapper.java
git commit -m "新增 DatabaseIdentityMapper 接口"
```

---

### Task 4: MyBatis Mapper XML

**Files:**
- Create: `backend/test-agent-persistence/src/main/resources/mybatis/DatabaseIdentityMapper.xml`

**SQL 设计说明（已用真实 PG 验证）：**
- 查询当前值：`pg_get_serial_sequence(#{tableName},'id')` 取序列名（返回 `public.users_id_seq`），再从 `pg_sequences` 按 `sequencename` 取 `last_value`。`pg_sequences` 的 `sequencename` 是不含 schema 的短名，需用 `split_part` 或 `regexp_replace` 去掉 `public.` 前缀。
- `max(id)`：`select max(id) from ${tableName}`。
- 重置：`alter table ${tableName} alter column id restart with ${targetValue}`（H2 与 PG 均支持此语法，与现有 `V20260627214000` 一致）。

- [ ] **Step 1: 创建 XML**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.icbc.testagent.persistence.mybatis.DatabaseIdentityMapper">

    <!--
        查询一张表的 identity 序列当前值与 max(id)。
        tableName 已由服务层限定为白名单枚举常量，安全拼接。
        pg_get_serial_sequence 返回形如 'public.users_id_seq'，pg_sequences.sequencename 为短名 'users_id_seq'，
        故用 regexp_replace 去掉 schema 前缀后匹配。
    -->
    <select id="queryIdentityStatus" resultType="java.util.LinkedHashMap">
        select
            (select s.last_value
               from pg_sequences s
              where s.sequencename = regexp_replace(
                        pg_get_serial_sequence(#{tableName}, 'id')::text, '^.*\.', '')) as "currentValue",
            (select max(id) from ${tableName}) as "maxId"
    </select>

    <!--
        重置 identity 到目标值。targetValue 已由服务层校验为正整数且 > maxId。
        H2(PostgreSQL 模式) 与 PostgreSQL 均支持该语法。
    -->
    <update id="restartIdentity">
        alter table ${tableName} alter column id restart with ${targetValue}
    </update>

</mapper>
```

- [ ] **Step 2: 编译确认（XML 被 mybatis 资源扫描）**

Run: `cd backend && ./mvnw -q -pl test-agent-persistence -am test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/test-agent-persistence/src/main/resources/mybatis/DatabaseIdentityMapper.xml
git commit -m "新增 DatabaseIdentityMapper XML SQL"
```

---

### Task 5: 应用服务 DatabaseIdentityMaintenanceService

**Files:**
- Create: `backend/test-agent-system-management/src/main/java/com/icbc/testagent/system/management/maintenance/DatabaseIdentityMaintenanceService.java`

**职责：** 白名单校验 → 调 mapper 查询/对齐/重启 → 计算 conflict → 审计日志。冲突判定：`currentValue != null && maxId != null && currentValue < maxId`（is_called=t 正常态下，nextval=currentValue+1，安全当 currentValue>=maxId）。

- [ ] **Step 1: 创建服务**

```java
package com.icbc.testagent.system.management.maintenance;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.persistence.mybatis.DatabaseIdentityMapper;
import com.icbc.testagent.system.management.maintenance.DatabaseIdentityResponses.IdentityStatusDto;
import com.icbc.testagent.system.management.maintenance.DatabaseIdentityResponses.RestartIdentityCommand;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 数据库 identity 运维应用服务，封装白名单校验、状态查询、对齐与手动重启及审计日志。
 *
 * <p>表名只接受 {@link IdentityManagedTable} 枚举，目标值必须为正整数且大于当前 max(id)，
 * 杜绝任意表名注入与往回滚造成新冲突。
 */
@Service
public class DatabaseIdentityMaintenanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseIdentityMaintenanceService.class);

    private final DatabaseIdentityMapper mapper;

    public DatabaseIdentityMaintenanceService(DatabaseIdentityMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /**
     * 查询白名单内全部表的 identity 状态。
     */
    public List<IdentityStatusDto> listIdentityStatuses() {
        return Arrays.stream(IdentityManagedTable.values())
                .map(this::statusOf)
                .toList();
    }

    /**
     * 把指定表 identity 对齐到 max(id)+1。
     *
     * @return 对齐后的最新状态
     */
    public IdentityStatusDto alignIdentity(IdentityManagedTable table) {
        IdentityStatusDto before = statusOf(table);
        long maxId = before.maxId() == null ? 0L : before.maxId();
        long target = maxId + 1;
        restart(table, target, "ALIGN", before);
        return statusOf(table);
    }

    /**
     * 手动把指定表 identity 重启到目标值，目标值必须 > max(id)。
     */
    public IdentityStatusDto restartIdentity(RestartIdentityCommand command) {
        IdentityManagedTable table = command.table();
        if (command.targetValue() <= 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目标值必须为正整数");
        }
        IdentityStatusDto before = statusOf(table);
        Long maxId = before.maxId();
        if (maxId != null && command.targetValue() <= maxId) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR,
                    "目标值必须大于当前最大主键 " + maxId);
        }
        restart(table, command.targetValue(), "RESTART", before);
        return statusOf(table);
    }

    private void restart(IdentityManagedTable table, long target, String action, IdentityStatusDto before) {
        mapper.restartIdentity(table.tableName(), target);
        LOGGER.info(
                "event=identity_maintain table={} action={} oldValue={} maxValue={} newValue={}",
                table.tableName(), action,
                before.currentValue(), before.maxId(), target);
    }

    private IdentityStatusDto statusOf(IdentityManagedTable table) {
        Map<String, Object> row = mapper.queryIdentityStatus(table.tableName());
        Long currentValue = longOrNull(row == null ? null : row.get("currentValue"));
        Long maxId = longOrNull(row == null ? null : row.get("maxId"));
        boolean conflict = currentValue != null && maxId != null && currentValue < maxId;
        return new IdentityStatusDto(
                table.name(),
                table.tableName(),
                currentValue,
                maxId,
                conflict,
                Instant.now());
    }

    private static Long longOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    /**
     * 把字符串表码解析为白名单枚举，非法则抛 VALIDATION_ERROR。
     */
    public static IdentityManagedTable requireTable(String code) {
        return IdentityManagedTable.fromCode(code)
                .orElseThrow(() -> new PlatformException(ErrorCode.VALIDATION_ERROR, "不支持的数据表"));
    }
}
```

- [ ] **Step 2: 编译确认**

Run: `cd backend && ./mvnw -q -pl test-agent-system-management -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/test-agent-system-management/src/main/java/com/icbc/testagent/system/management/maintenance/DatabaseIdentityMaintenanceService.java
git commit -m "新增 DatabaseIdentityMaintenanceService 应用服务"
```

---

### Task 6: 服务层单元测试

**Files:**
- Create: `backend/test-agent-system-management/src/test/java/com/icbc/testagent/system/management/maintenance/DatabaseIdentityMaintenanceServiceTest.java`

参考 `UserManagementApplicationServiceTest` 的 Mockito + AssertJ 风格。

- [ ] **Step 1: 写失败测试**

```java
package com.icbc.testagent.system.management.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.persistence.mybatis.DatabaseIdentityMapper;
import com.icbc.testagent.system.management.maintenance.DatabaseIdentityResponses.IdentityStatusDto;
import com.icbc.testagent.system.management.maintenance.DatabaseIdentityResponses.RestartIdentityCommand;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DatabaseIdentityMaintenanceServiceTest {

    @Test
    void listStatusesReturnsAllWhitelistedTables() {
        DatabaseIdentityMapper mapper = mock(DatabaseIdentityMapper.class);
        when(mapper.queryIdentityStatus(eq("users")))
                .thenReturn(row(8L, 8L));
        when(mapper.queryIdentityStatus(eq("user_roles")))
                .thenReturn(row(1000000L, 1000000L));
        when(mapper.queryIdentityStatus(eq("dictionaries")))
                .thenReturn(row(16L, 16L));
        when(mapper.queryIdentityStatus(eq("user_login_logs")))
                .thenReturn(row(14L, 14L));

        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(mapper);
        List<IdentityStatusDto> statuses = service.listIdentityStatuses();

        assertThat(statuses).hasSize(4);
        assertThat(statuses).extracting(IdentityStatusDto::tableName)
                .containsExactly("users", "user_roles", "dictionaries", "user_login_logs");
        assertThat(statuses).noneMatch(IdentityStatusDto::conflict);
    }

    @Test
    void conflictFlagTrueWhenCurrentBehindMax() {
        DatabaseIdentityMapper mapper = mock(DatabaseIdentityMapper.class);
        // 序列停在 2，但表里已有 id=8 → 错位
        when(mapper.queryIdentityStatus(eq("users"))).thenReturn(row(2L, 8L));

        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(mapper);
        List<IdentityStatusDto> statuses = service.listIdentityStatuses();

        IdentityStatusDto users = statuses.stream()
                .filter(s -> "users".equals(s.tableName())).findFirst().orElseThrow();
        assertThat(users.conflict()).isTrue();
    }

    @Test
    void alignRestartsAtMaxPlusOne() {
        DatabaseIdentityMapper mapper = mock(DatabaseIdentityMapper.class);
        when(mapper.queryIdentityStatus(eq("users"))).thenReturn(row(2L, 8L));

        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(mapper);
        service.alignIdentity(IdentityManagedTable.USERS);

        verify(mapper).restartIdentity(eq("users"), eq(9L));
    }

    @Test
    void alignOnEmptyTableRestartsAtOne() {
        DatabaseIdentityMapper mapper = mock(DatabaseIdentityMapper.class);
        when(mapper.queryIdentityStatus(eq("user_login_logs"))).thenReturn(row(null, null));

        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(mapper);
        service.alignIdentity(IdentityManagedTable.USER_LOGIN_LOGS);

        verify(mapper).restartIdentity(eq("user_login_logs"), eq(1L));
    }

    @Test
    void restartRejectsNonPositiveTarget() {
        DatabaseIdentityMapper mapper = mock(DatabaseIdentityMapper.class);
        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(mapper);

        assertThatThrownBy(() -> service.restartIdentity(
                new RestartIdentityCommand(IdentityManagedTable.USERS, 0L)))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> assertThat(((PlatformException) e).errorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void restartRejectsTargetNotGreaterThanMax() {
        DatabaseIdentityMapper mapper = mock(DatabaseIdentityMapper.class);
        when(mapper.queryIdentityStatus(eq("users"))).thenReturn(row(8L, 8L));
        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(mapper);

        assertThatThrownBy(() -> service.restartIdentity(
                new RestartIdentityCommand(IdentityManagedTable.USERS, 8L)))
                .isInstanceOf(PlatformException.class);
        verify(mapper, times(0)).restartIdentity(eq("users"), eq(8L));
    }

    @Test
    void restartAcceptsTargetAboveMax() {
        DatabaseIdentityMapper mapper = mock(DatabaseIdentityMapper.class);
        when(mapper.queryIdentityStatus(eq("users"))).thenReturn(row(8L, 8L));
        DatabaseIdentityMaintenanceService service = new DatabaseIdentityMaintenanceService(mapper);

        service.restartIdentity(new RestartIdentityCommand(IdentityManagedTable.USERS, 1000000L));

        verify(mapper).restartIdentity(eq("users"), eq(1000000L));
    }

    @Test
    void requireTableThrowsOnUnknownCode() {
        assertThatThrownBy(() -> DatabaseIdentityMaintenanceService.requireTable("unknown_table"))
                .isInstanceOf(PlatformException.class);
    }

    private Map<String, Object> row(Long current, Long max) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("currentValue", current);
        map.put("maxId", max);
        return map;
    }
}
```

- [ ] **Step 2: 运行测试，确认通过**

Run: `cd backend && ./mvnw -q -pl test-agent-system-management test -Dtest=DatabaseIdentityMaintenanceServiceTest`
Expected: Tests run: 8, Failures: 0, Errors: 0

- [ ] **Step 3: Commit**

```bash
git add backend/test-agent-system-management/src/test/java/com/icbc/testagent/system/management/maintenance/DatabaseIdentityMaintenanceServiceTest.java
git commit -m "新增 DatabaseIdentityMaintenanceService 单元测试"
```

---

### Task 7: Controller 与请求 DTO

**Files:**
- Create: `backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/DatabaseIdentityDtos.java`
- Create: `backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/DatabaseIdentityController.java`

参考 `UserManagementController` + `UserManagementDtos` 风格，复用 `requireSuperAdmin`、`AuthWebSupport`、`RuntimeApiSupport`。

- [ ] **Step 1: 创建请求 DTO**

```java
package com.icbc.testagent.api.web.platform;

/**
 * identity 运维 HTTP 请求 DTO，与 UserManagementDtos 同风格。
 */
public final class DatabaseIdentityDtos {

    private DatabaseIdentityDtos() {
    }

    /** 对齐请求：只需表码。 */
    public record AlignIdentityRequest(String table) {
    }

    /** 手动重启请求：表码 + 目标值。 */
    public record RestartIdentityRequest(String table, Long targetValue) {
    }
}
```

- [ ] **Step 2: 创建 Controller**

```java
package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.system.management.maintenance.DatabaseIdentityMaintenanceService;
import com.icbc.testagent.system.management.maintenance.DatabaseIdentityResponses.RestartIdentityCommand;
import com.icbc.testagent.system.management.maintenance.IdentityManagedTable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 数据库 identity 运维 HTTP 入口，仅做鉴权、参数转换和统一响应包装。
 *
 * <p>所有接口仅限 SUPER_ADMIN 访问，用于查询白名单表 identity 状态并手工滚动序列，
 * 修复 identity 落后于已有主键导致新增数据冲突的问题。
 */
@RestController
@RequestMapping("/api/internal/platform/system-management/identity")
public class DatabaseIdentityController {

    private final DatabaseIdentityMaintenanceService service;

    public DatabaseIdentityController(DatabaseIdentityMaintenanceService service) {
        this.service = service;
    }

    /**
     * 查询白名单内全部表的 identity 当前值、max(id) 与是否错位。
     */
    @GetMapping
    public ApiResponse<Object> listIdentityStatuses(ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ok(exchange, service.listIdentityStatuses());
    }

    /**
     * 把指定表 identity 对齐到 max(id)+1。
     */
    @PostMapping("/align")
    public ApiResponse<Object> alignIdentity(
            @RequestBody DatabaseIdentityDtos.AlignIdentityRequest request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        IdentityManagedTable table = DatabaseIdentityMaintenanceService.requireTable(request.table());
        return ok(exchange, service.alignIdentity(table));
    }

    /**
     * 手动把指定表 identity 重启到目标值，目标值必须大于当前 max(id)。
     */
    @PostMapping("/restart")
    public ApiResponse<Object> restartIdentity(
            @RequestBody DatabaseIdentityDtos.RestartIdentityRequest request,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        if (request.targetValue() == null) {
            throw new com.icbc.testagent.common.error.PlatformException(
                    com.icbc.testagent.common.error.ErrorCode.VALIDATION_ERROR, "目标值不能为空");
        }
        IdentityManagedTable table = DatabaseIdentityMaintenanceService.requireTable(request.table());
        return ok(exchange, service.restartIdentity(
                new RestartIdentityCommand(table, request.targetValue())));
    }

    private AuthPrincipal requireSuperAdmin(ServerWebExchange exchange) {
        return AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    private ApiResponse<Object> ok(ServerWebExchange exchange, Object data) {
        return ApiResponse.ok(data, RuntimeApiSupport.traceId(exchange));
    }
}
```

- [ ] **Step 3: 编译确认**

Run: `cd backend && ./mvnw -q -pl test-agent-api -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/DatabaseIdentityController.java backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/DatabaseIdentityDtos.java
git commit -m "新增 DatabaseIdentityController 与请求 DTO"
```

---

### Task 8: Controller 切片测试

**Files:**
- Create: `backend/test-agent-api/src/test/java/com/icbc/testagent/api/web/platform/DatabaseIdentityControllerTest.java`

参考 `UserManagementControllerTest` 的 `WebTestClient` + `client()` helper 范式。

- [ ] **Step 1: 写测试**

```java
package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.system.management.maintenance.DatabaseIdentityMaintenanceService;
import com.icbc.testagent.system.management.maintenance.DatabaseIdentityResponses.IdentityStatusDto;
import com.icbc.testagent.system.management.maintenance.DatabaseIdentityResponses.RestartIdentityCommand;
import com.icbc.testagent.system.management.maintenance.IdentityManagedTable;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class DatabaseIdentityControllerTest {

    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void superAdminCanListIdentityStatuses() {
        DatabaseIdentityMaintenanceService service = mock(DatabaseIdentityMaintenanceService.class);
        when(service.listIdentityStatuses()).thenReturn(List.of(status("USERS", "users", 8L, 8L, false)));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/system-management/identity")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].table").isEqualTo("USERS")
                .jsonPath("$.data[0].maxId").isEqualTo(8)
                .jsonPath("$.data[0].conflict").isEqualTo(false);
    }

    @Test
    void superAdminCanAlignIdentity() {
        DatabaseIdentityMaintenanceService service = mock(DatabaseIdentityMaintenanceService.class);
        when(service.alignIdentity(eq(IdentityManagedTable.USERS)))
                .thenReturn(status("USERS", "users", 9L, 8L, false));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/system-management/identity/align")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"table\":\"USERS\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.table").isEqualTo("USERS");

        verify(service).alignIdentity(eq(IdentityManagedTable.USERS));
    }

    @Test
    void superAdminCanRestartIdentity() {
        DatabaseIdentityMaintenanceService service = mock(DatabaseIdentityMaintenanceService.class);
        when(service.restartIdentity(any(RestartIdentityCommand.class)))
                .thenReturn(status("USERS", "users", 1000000L, 8L, false));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/system-management/identity/restart")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"table\":\"USERS\",\"targetValue\":1000000}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.currentValue").isEqualTo(1000000);
    }

    @Test
    void nonSuperAdminIsForbidden() {
        WebTestClient client = client(
                mock(DatabaseIdentityMaintenanceService.class),
                List.of(Dictionary.ROLE_APP_ADMIN));

        client.get()
                .uri("/api/internal/platform/system-management/identity")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void alignRejectsUnknownTable() {
        DatabaseIdentityMaintenanceService service = mock(DatabaseIdentityMaintenanceService.class);
        when(service.alignIdentity(any()))
                .thenThrow(new PlatformException(ErrorCode.VALIDATION_ERROR, "不支持的数据表"));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/system-management/identity/align")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"table\":\"unknown_table\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    private WebTestClient client(DatabaseIdentityMaintenanceService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER_ID, "admin", "AUTH_1", roles,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"));
        return WebTestClient.bindToController(new DatabaseIdentityController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private IdentityStatusDto status(String table, String tableName, Long current, Long max, boolean conflict) {
        return new IdentityStatusDto(table, tableName, current, max, conflict, Instant.parse("2026-07-01T00:00:00Z"));
    }
}
```

- [ ] **Step 2: 运行测试，确认通过**

Run: `cd backend && ./mvnw -q -pl test-agent-api test -Dtest=DatabaseIdentityControllerTest`
Expected: Tests run: 5, Failures: 0, Errors: 0

- [ ] **Step 3: Commit**

```bash
git add backend/test-agent-api/src/test/java/com/icbc/testagent/api/web/platform/DatabaseIdentityControllerTest.java
git commit -m "新增 DatabaseIdentityController 切片测试"
```

---

### Task 9: Mapper 集成测试（H2）

**Files:**
- Create: `backend/test-agent-persistence/src/test/java/com/icbc/testagent/persistence/MyBatisDatabaseIdentityMapperIntegrationTest.java`

参考 `MyBatisCommonParameterRepositoryIntegrationTest` 的 H2 + Flyway + SqlSessionFactory 范式。

**测试范围说明：** H2 不支持 `pg_get_serial_sequence`/`pg_sequences`，所以 `queryIdentityStatus` 在 H2 下会抛异常。本测试只覆盖 `restartIdentity`（H2 支持 `alter table ... restart with`）和 `max(id)` 行为；`queryIdentityStatus` 的 PG 专用 SQL 由 Task 10 真实 PG 手工验证。

- [ ] **Step 1: 写测试**

```java
package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.persistence.mybatis.DatabaseIdentityMapper;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 验证 DatabaseIdentityMapper 的 restartIdentity 在 H2(PostgreSQL 模式) 下可用。
 * queryIdentityStatus 依赖 PG 系统目录，由真实 PG 手工验证，不在此断言。
 */
class MyBatisDatabaseIdentityMapperIntegrationTest {

    private SingleConnectionDataSource dataSource;
    private DatabaseIdentityMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_mybatis_identity_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        SqlSessionFactory factory = sqlSessionFactory();
        mapper = new SqlSessionTemplate(factory).getMapper(DatabaseIdentityMapper.class);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void restartIdentityMovesSequenceForwardOnH2() {
        // H2 重启后，下一次显式 nextval 应返回 targetValue
        mapper.restartIdentity("users", 1000000L);

        Long next = dataSource.getConnection().createStatement()
                .executeQuery("select next value for users_id_seq").getLong(1);
        // H2 重启后序列当前值=1000000-1，nextval 返回 1000000
        assertThat(next).isEqualTo(1000000L);
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mybatis/**/*.xml"));
        factoryBean.getObject().getConfiguration().setMapUnderscoreToCamelCase(true);
        return factoryBean.getObject();
    }
}
```

- [ ] **Step 2: 运行测试，确认通过**

Run: `cd backend && ./mvnw -q -pl test-agent-persistence test -Dtest=MyBatisDatabaseIdentityMapperIntegrationTest`
Expected: Tests run: 1, Failures: 0, Errors: 0

注：若 H2 的 `next value for users_id_seq` 语义与预期不符（不同版本 H2 行为略有差异），调整为 `select currval('users_id_seq')` 或断言 `>= 1000000L`，保持测试稳定性。

- [ ] **Step 3: Commit**

```bash
git add backend/test-agent-persistence/src/test/java/com/icbc/testagent/persistence/MyBatisDatabaseIdentityMapperIntegrationTest.java
git commit -m "新增 DatabaseIdentityMapper H2 集成测试"
```

---

### Task 10: 真实 PostgreSQL 手工验证

**Files:** 无（验证步骤，结果记录到 session-log）

**目的：** 验证 `queryIdentityStatus` 的 PG 专用 SQL 在真实库正确返回，并端到端验证对齐/重启后能成功新增用户（修复原 bug）。

- [ ] **Step 1: 启动后端，指向本地 PG**

Run（后台）: `cd backend && ./mvnw -q -pl test-agent-app spring-boot:run -Dspring-boot.run.profiles=local`
等待日志出现 `Started TestAgentApplication`。

- [ ] **Step 2: 用 curl 验证 GET /identity（需先登录拿 token，或用现有测试 token）**

先查日志确认服务在 8080 端口。然后用超管账号登录获取 JWT，或复用现有 `usr_test_dev` 的 token：

Run:
```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"unifiedAuthId":"DEV_888888888","password":"<超管密码>"}' | jq -r '.data.token')
curl -s http://127.0.0.1:8080/api/internal/platform/system-management/identity \
  -H "Authorization: Bearer $TOKEN" | jq .
```
Expected: 返回 4 张表状态，`users` 的 `currentValue` 与 `maxId` 相等（已对齐），`conflict=false`。

- [ ] **Step 3: 验证对齐接口对无冲突表是幂等的**

Run:
```bash
curl -s -X POST http://127.0.0.1:8080/api/internal/platform/system-management/identity/align \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"table":"USERS"}' | jq .
```
Expected: 返回 `users` 状态，`currentValue` = 原 max+1。

- [ ] **Step 4: 验证手动重启**

Run:
```bash
curl -s -X POST http://127.0.0.1:8080/api/internal/platform/system-management/identity/restart \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"table":"USERS","targetValue":2000000}' | jq .
```
Expected: 返回 `currentValue=2000000`。

- [ ] **Step 5: 端到端验证原 bug 已修复——新增用户成功**

Run:
```bash
curl -s -X POST http://127.0.0.1:8080/api/internal/platform/system-management/users \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"unifiedAuthId":"VERIFY_IDENTITY_001","username":"verify_identity_user","role":"USER"}' | jq .
```
Expected: `success=true`，返回新用户，无"数据冲突"错误。

- [ ] **Step 6: 验证非超管被拒（用普通用户 token）**

Run:
```bash
curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/internal/platform/system-management/identity \
  -H "Authorization: Bearer <普通用户TOKEN>"
```
Expected: `403`

- [ ] **Step 7: 停止后端**

停止后台 spring-boot:run 进程。

- [ ] **Step 8: Commit（仅记录验证到 session-log，代码无变更则跳过；若有调整则提交）**

```bash
git add .agents/session-log.md
git commit -m "记录 identity 运维功能真实 PG 端到端验证结果"
```

---

### Task 11: 前端 shared-types 新增 IdentityStatus

**Files:**
- Modify: `frontend/packages/shared-types/src/index.ts`（在 `RoleOption` 后追加）

- [ ] **Step 1: 在 `RoleOption` 类型定义后新增**

定位 `export type RoleOption = {` 段落（约 1345 行），在其后追加：

```ts
/** 数据库 identity 运维：单张表的状态快照。 */
export type IdentityStatus = {
  table: string;
  tableName: string;
  currentValue: number | null;
  maxId: number | null;
  conflict: boolean;
  lastUpdatedAt: string;
};
```

- [ ] **Step 2: 类型检查**

Run: `cd frontend && pnpm --filter @test-agent/shared-types build`（或 `pnpm -r typecheck`，视工程脚本而定）
Expected: 无类型错误

- [ ] **Step 3: Commit**

```bash
git add frontend/packages/shared-types/src/index.ts
git commit -m "前端新增 IdentityStatus 类型"
```

---

### Task 12: 前端 backend-api 新增 3 方法

**Files:**
- Modify: `frontend/packages/backend-api/src/index.ts`（在 `listRoles` 后追加，并 import `IdentityStatus`）

- [ ] **Step 1: import IdentityStatus**

在文件顶部 `import type { ..., RoleOption, ... } from "@test-agent/shared-types";` 行加入 `IdentityStatus`。

- [ ] **Step 2: 在 `listRoles` 方法后追加 3 方法**

```ts
    /** 查询白名单表 identity 状态（仅 SUPER_ADMIN）。 */
    listIdentityStatuses: () => request<IdentityStatus[]>(`${systemManagementBase}/identity`),
    /** 把指定表 identity 对齐到 max(id)+1。 */
    alignIdentity: (table: string) =>
      request<IdentityStatus>(`${systemManagementBase}/identity/align`, { method: "POST", body: JSON.stringify({ table }) }),
    /** 手动把指定表 identity 重启到目标值。 */
    restartIdentity: (table: string, targetValue: number) =>
      request<IdentityStatus>(`${systemManagementBase}/identity/restart`, { method: "POST", body: JSON.stringify({ table, targetValue }) }),
```

- [ ] **Step 3: 类型检查**

Run: `cd frontend && pnpm --filter @test-agent/backend-api build`
Expected: 无类型错误

- [ ] **Step 4: Commit**

```bash
git add frontend/packages/backend-api/src/index.ts
git commit -m "backend-api 新增 identity 运维 3 个方法"
```

---

### Task 13: 前端用户管理面板新增 identity 区块

**Files:**
- Modify: `frontend/apps/agent-web/src/components/settings/SettingsUserManagementPanel.vue`

**交互：** 底部新增 `<el-collapse>` 折叠区「数据库 IDENTITY 运维」，表格列：表名/序列当前值/最大ID/是否错位/操作；错位行高亮；[对齐]二次确认；[重置]弹窗输入目标值并校验 > maxId。

- [ ] **Step 1: 在 `<script setup>` 内补充 import 与状态**

在现有 import 区追加：
```ts
import type { IdentityStatus } from "@test-agent/shared-types";
import { ElMessageBox } from "element-plus";
```
（`ElMessage` 已 import，追加 `ElMessageBox`）

在 `creating` ref 后追加状态：
```ts
// 数据库 IDENTITY 运维
const identityVisible = ref(false);
const identityStatuses = ref<IdentityStatus[]>([]);
const identityLoading = ref(false);

async function loadIdentityStatuses() {
  identityLoading.value = true;
  try {
    identityStatuses.value = await api.listIdentityStatuses();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "加载 IDENTITY 状态失败");
  } finally {
    identityLoading.value = false;
  }
}

async function alignIdentity(row: IdentityStatus) {
  await ElMessageBox.confirm(
    `确认将 ${row.tableName} 的 IDENTITY 对齐到 max(id)+1？`,
    "对齐 IDENTITY",
    { type: "warning" }
  );
  try {
    const updated = await api.alignIdentity(row.table);
    identityStatuses.value = identityStatuses.value.map(
      (item) => (item.table === updated.table ? updated : item)
    );
    ElMessage.success("已对齐");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "对齐失败");
  }
}

async function restartIdentity(row: IdentityStatus) {
  const { value } = await ElMessageBox.prompt(
    `输入目标值（需大于当前最大 ID ${row.maxId ?? 0}）`,
    "重置 IDENTITY",
    { inputPattern: /^\d+$/, inputErrorMessage: "请输入正整数", type: "warning" }
  );
  const target = Number(value);
  if (row.maxId != null && target <= row.maxId) {
    ElMessage.error("目标值必须大于当前最大 ID");
    return;
  }
  try {
    const updated = await api.restartIdentity(row.table, target);
    identityStatuses.value = identityStatuses.value.map(
      (item) => (item.table === updated.table ? updated : item)
    );
    ElMessage.success("已重置");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "重置失败");
  }
}

watch(identityVisible, (visible) => {
  if (visible && identityStatuses.value.length === 0) {
    loadIdentityStatuses();
  }
});
```

注：`watch` 需从 vue import，补到第 2 行 `import { computed, inject, onMounted, ref } from "vue";` 中加 `watch`。

- [ ] **Step 2: 在模板 `</div>` 闭合用户列表区前（约 225 行 `</template>` 之前）插入折叠区**

在 `ta-pagination` div 之后、外层 `<template v-if="hasPermission">` 闭合之前插入：
```vue
      <el-collapse v-model="identityVisible" class="ta-identity-collapse">
        <el-collapse-item title="数据库 IDENTITY 运维（仅超级管理员）" name="identity">
          <div class="ta-identity-toolbar">
            <el-button size="small" :disabled="identityLoading" @click="loadIdentityStatuses">刷新</el-button>
            <span v-if="identityStatuses.some((s) => s.conflict)" class="ta-identity-warn">
              存在序列落后于已有主键的表，新增数据可能冲突，建议对齐。
            </span>
          </div>
          <el-table :data="identityStatuses" size="small" border>
            <el-table-column prop="tableName" label="表名" />
            <el-table-column prop="currentValue" label="序列当前值" />
            <el-table-column prop="maxId" label="最大ID" />
            <el-table-column label="是否错位">
              <template #default="{ row }">
                <el-tag :type="row.conflict ? 'danger' : 'success'" size="small">
                  {{ row.conflict ? '错位' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button size="small" @click="alignIdentity(row)">对齐</el-button>
                <el-button size="small" @click="restartIdentity(row)">重置</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
```

- [ ] **Step 3: 在 `<style scoped>` 内追加样式**

```css
.ta-identity-collapse {
  margin-top: 8px;
}
.ta-identity-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.ta-identity-warn {
  color: #d46b08;
  font-size: 12px;
}
```

- [ ] **Step 4: 类型检查 + 运行已有前端测试确认无回归**

Run: `cd frontend && pnpm --filter @test-agent/agent-web typecheck && pnpm --filter @test-agent/agent-web test -- settings-user-management-panel`
Expected: 类型无错；已有测试通过

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/agent-web/src/components/settings/SettingsUserManagementPanel.vue
git commit -m "用户管理面板新增数据库 IDENTITY 运维折叠区块"
```

---

### Task 14: 前端面板测试

**Files:**
- Modify: `frontend/apps/agent-web/tests/settings-user-management-panel.test.ts`

参考现有 `createApi()` mock 范式，补 `listIdentityStatuses/alignIdentity/restartIdentity` mock，新增 3 个测试。

- [ ] **Step 1: 扩展 createApi mock**

在 `createApi()` 返回对象内追加：
```ts
    listIdentityStatuses: vi.fn().mockResolvedValue([
      { table: "USERS", tableName: "users", currentValue: 8, maxId: 8, conflict: false, lastUpdatedAt: "2026-07-01T00:00:00Z" },
      { table: "USER_ROLES", tableName: "user_roles", currentValue: 1000000, maxId: 1000000, conflict: false, lastUpdatedAt: "2026-07-01T00:00:00Z" }
    ]),
    alignIdentity: vi.fn().mockResolvedValue(
      { table: "USERS", tableName: "users", currentValue: 9, maxId: 8, conflict: false, lastUpdatedAt: "2026-07-01T00:00:00Z" }
    ),
    restartIdentity: vi.fn().mockResolvedValue(
      { table: "USERS", tableName: "users", currentValue: 1000000, maxId: 8, conflict: false, lastUpdatedAt: "2026-07-01T00:00:00Z" }
    )
```

并 import `IdentityStatus` 类型用于断言（按需）。

- [ ] **Step 2: 新增测试：超管展开折叠区显示 identity 状态**

```ts
it("超管展开 IDENTITY 运维区显示各表状态", async () => {
  const api = createApi();
  const { getByText, container } = renderPanel(api, superAdmin);
  // 展开 collapse（点击标题）
  await fireEvent.click(getByText(/数据库 IDENTITY 运维/));
  await waitFor(() => expect(api.listIdentityStatuses).toHaveBeenCalled());
  await waitFor(() => expect(getByText("user_roles")).toBeInTheDocument());
});
```

（`renderPanel` 与 `superAdmin` 复用现有测试的 helper/fixture，按文件实际命名调整。）

- [ ] **Step 3: 新增测试：非超管不渲染 identity 区块**

```ts
it("非超管不显示 IDENTITY 运维区", async () => {
  const api = createApi();
  const { queryByText } = renderPanel(api, nonSuperAdmin);
  expect(queryByText(/数据库 IDENTITY 运维/)).toBeNull();
});
```

- [ ] **Step 4: 运行测试**

Run: `cd frontend && pnpm --filter @test-agent/agent-web test -- settings-user-management-panel`
Expected: 全部通过（含原有 + 新增）

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/agent-web/tests/settings-user-management-panel.test.ts
git commit -m "用户管理面板测试补充 identity 运维区块"
```

---

### Task 15: 文档同步

**Files:**
- Modify: `docs/api/http-api.md`
- Modify: `docs/deployment/database.md`
- Modify: `backend/test-agent-system-management/README.md`
- Modify: `backend/test-agent-persistence/README.md`

- [ ] **Step 1: http-api.md 新增 3 接口**

在 `### system-management 用户管理（测试）API`（约 1804 行）段末尾追加：

```markdown
#### 查询数据库 IDENTITY 状态

- 方法：`GET /api/internal/platform/system-management/identity`
- 权限：仅 `SUPER_ADMIN`
- 响应 `data`：`IdentityStatusDto[]`，每项含 `table`（枚举名）、`tableName`（真实表名）、`currentValue`（identity 当前值，可空）、`maxId`（表 max id，可空）、`conflict`（序列落后于已有主键时 true）、`lastUpdatedAt`。
- 错误码：`FORBIDDEN`（非超管）。

#### 对齐数据库 IDENTITY 到 max(id)+1

- 方法：`POST /api/internal/platform/system-management/identity/align`
- 权限：仅 `SUPER_ADMIN`
- 请求体：`{"table": "USERS"}`，`table` 为白名单枚举名（`USERS`/`USER_ROLES`/`DICTIONARIES`/`USER_LOGIN_LOGS`）。
- 响应 `data`：对齐后的 `IdentityStatusDto`。
- 错误码：`VALIDATION_ERROR`（表名不在白名单）、`FORBIDDEN`（非超管）。

#### 手动重启数据库 IDENTITY 到目标值

- 方法：`POST /api/internal/platform/system-management/identity/restart`
- 权限：仅 `SUPER_ADMIN`
- 请求体：`{"table": "USERS", "targetValue": 1000000}`，`targetValue` 必须为正整数且大于当前 `maxId`。
- 响应 `data`：重启后的 `IdentityStatusDto`。
- 错误码：`VALIDATION_ERROR`（表名非法、目标值非正整数或 ≤ maxId）、`FORBIDDEN`（非超管）。
```

- [ ] **Step 2: database.md 在 `## V20260627214000 user_roles identity 序列兼容修复` 段后追加新段**

```markdown
## 数据库 IDENTITY 运维入口

历史库或人工数据导入后，`generated by default as identity` 列的序列可能落后于已有主键，导致新增数据命中主键唯一约束（如 `users_pkey`），被全局异常处理器翻译成"数据冲突：当前操作因存在关联数据无法执行"。`user_roles` 表历史上由 `V20260627214000` 用 `restart with 1000000` 修复。

为避免每次都靠加 Flyway migration 临时修复，平台提供超管运维入口（`/api/internal/platform/system-management/identity`），支持查询 `users`/`user_roles`/`dictionaries`/`user_login_logs` 四张白名单表的 identity 当前值与 `max(id)`，并支持一键对齐到 `max(id)+1` 或手动 `RESTART WITH` 指定值（禁止往回滚）。表名走白名单枚举、目标值走 Long 校验，杜绝 SQL 注入。详见 `docs/api/http-api.md`。
```

- [ ] **Step 3: system-management README 登记新服务**

在 `backend/test-agent-system-management/README.md` 职责列表追加：
```markdown
- `maintenance`：数据库 identity 运维，查询/对齐/手动重启白名单表（users/user_roles/dictionaries/user_login_logs）的 identity 序列，修复序列落后于已有主键导致的新增冲突。
```

- [ ] **Step 4: persistence README 登记新 mapper**

在 `backend/test-agent-persistence/README.md` mapper 列表追加：
```markdown
- `DatabaseIdentityMapper`：identity 运维 SQL，查询 `pg_sequences` 当前值与 `max(id)`、执行 `ALTER TABLE ... RESTART WITH`。
```

- [ ] **Step 5: Commit**

```bash
git add docs/api/http-api.md docs/deployment/database.md backend/test-agent-system-management/README.md backend/test-agent-persistence/README.md
git commit -m "同步 identity 运维功能文档"
```

---

### Task 16: 自检与 session-log

**Files:**
- Modify: `.agents/session-log.md`

- [ ] **Step 1: 全量后端构建 + 测试**

Run: `cd backend && ./mvnw -q -pl test-agent-api -am test`
Expected: 全部通过（含新测试，无回归）

- [ ] **Step 2: 全量前端类型检查 + 测试**

Run: `cd frontend && pnpm -r typecheck && pnpm -r test`
Expected: 全部通过

- [ ] **Step 3: 按 `docs/guides/self-checklist.md` 自检**

逐项核对：最小改动范围、文档同步、API 文档、中文注释、错误统一格式、traceId、向后兼容、安全（白名单+Long 校验+仅超管）。

- [ ] **Step 4: 更新 .agents/session-log.md**

按 Why/What/How/Result 追加一条：
```markdown
## 2026-07-01 数据库 IDENTITY 运维功能

- Why：添加用户报"数据冲突"，根因是 users 表 identity 序列落后于已有主键（与历史 user_roles 同类问题），被全局异常处理器翻译成误导文案。
- What：新增超管运维入口，查询白名单表 identity 当前值/max(id) 并支持一键对齐 max+1 与手动 RESTART WITH n（禁止往回滚）。
- How：后端 DatabaseIdentityController + DatabaseIdentityMaintenanceService + DatabaseIdentityMapper(MyBatis XML)，白名单枚举防注入；前端用户管理面板新增折叠区块；同步 http-api.md/database.md/README。
- Result：真实 PG 端到端验证新增用户成功，单元/切片/H2 集成/前端测试全绿。不改表结构，无 Flyway migration。
```

- [ ] **Step 5: Commit**

```bash
git add .agents/session-log.md
git commit -m "记录 identity 运维功能 session-log"
```

---

## Self-Review

**1. Spec 覆盖：**
- 白名单 4 表 → Task 1 ✓
- 查询/对齐/手动重启 3 接口 → Task 7 + 8 ✓
- IdentityStatusDto 字段 → Task 2 ✓
- MyBatis SQL（pg_get_serial_sequence + pg_sequences + RESTART WITH）→ Task 3 + 4 ✓
- 不允许往回滚约束 → Task 5 + 6 ✓
- 审计日志 → Task 5 ✓
- 前端折叠区块 + 对齐/重置 + 错位高亮 → Task 13 ✓
- backend-api 3 方法 → Task 12 ✓
- shared-types → Task 11 ✓
- 后端测试（服务单测+切片测+H2集成）→ Task 6/8/9 ✓
- 前端测试 → Task 14 ✓
- 文档（http-api/database/2个README/session-log）→ Task 15 + 16 ✓
- 真实 PG 端到端验证修复原 bug → Task 10 ✓

**2. Placeholder 扫描：** 无 TBD/TODO；所有代码步骤含完整代码；Task 10 的超管密码/普通用户 token 标注为需替换（运行时变量，非占位符）。✓

**3. 类型一致性：** `IdentityStatusDto(table, tableName, currentValue, maxId, conflict, lastUpdatedAt)` 在 Task 2/6/8/11/12/14 一致；`RestartIdentityCommand(table, targetValue)` 在 Task 2/5/6/7 一致；`IdentityManagedTable.USERS/USER_ROLES/DICTIONARIES/USER_LOGIN_LOGS` 全程一致；mapper 方法 `queryIdentityStatus`/`restartIdentity` 在 Task 3/4/5/6/9 一致。✓

**4. 风险点：**
- H2 `next value for users_id_seq` 语义在 Task 9 已标注可能需调整。
- `el-collapse` 的 `v-model` 与 Element Plus 版本兼容性在 Task 13 已用受控 `identityVisible` 处理。
- Task 10 依赖本地 PG（127.0.0.1:15432）与超管账号，执行者需具备本地环境。
