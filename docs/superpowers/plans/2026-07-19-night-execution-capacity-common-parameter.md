# Night Execution Capacity Common Parameter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the night-execution slot-capacity environment variable with an editable global common parameter initialized to 20 and hot-refreshed into every Java server's memory.

**Architecture:** Seed `NIGHT_EXECUTION_SLOT_CAPACITY` as an editable `platform=all` common parameter. A dedicated runtime registry loads the authoritative database value on startup, listens to the existing cross-server `CommonParameterReloadedEvent`, and atomically replaces its in-memory snapshot; night task submission and reconciliation read only this registry. The generic common-parameter update service performs server-side positive-integer validation before persistence and broadcasting.

**Tech Stack:** Java 21, Spring Boot events, PostgreSQL/Flyway, MyBatis XML-backed common-parameter repository, JUnit 5, Mockito, AssertJ, Maven.

## Global Constraints

- The parameter name is exactly `NIGHT_EXECUTION_SLOT_CAPACITY`, with `platform=all`, `editable=true`, and initial value `20`.
- Only the existing `SUPER_ADMIN` common-parameter management entry may modify the value; do not add an API or frontend page.
- Remove `TEST_AGENT_NIGHT_EXECUTION_SLOT_CAPACITY` completely; there is no environment, Spring-property, or code-default fallback.
- Startup must fail when the database value is missing or invalid; a runtime refresh failure must retain the last valid in-memory value.
- Lowering capacity never cancels existing reservations; new reservations stop while `reservedCount >= capacity`.
- Preserve unrelated dirty work in Mermaid editor files and the reference-file-locate specification.
- New production Java logic and non-obvious branches require Chinese comments; relational SQL changes are Flyway seed data only and do not add JDBC SQL.

---

### Task 1: Seed and validate the common parameter

**Files:**
- Create: `backend/test-agent-persistence/src/main/resources/db/migration/V20260719210000__seed_night_execution_capacity_parameter.sql`
- Modify: `backend/test-agent-persistence/src/test/java/com/enterprise/testagent/persistence/CommonParameterSeedMigrationTest.java`
- Modify: `backend/test-agent-configuration-management/src/main/java/com/enterprise/testagent/configuration/management/CommonParameterManagementApplicationService.java`
- Modify: `backend/test-agent-configuration-management/src/test/java/com/enterprise/testagent/configuration/management/CommonParameterManagementApplicationServiceTest.java`

**Interfaces:**
- Produces common parameter `NIGHT_EXECUTION_SLOT_CAPACITY=20` (`all`, editable).
- Produces `CommonParameterManagementApplicationService` behavior that accepts only positive base-10 integers for this parameter before repository update.

- [ ] **Step 1: Write the failing seed migration test**

Add an assertion that reads `V20260719210000__seed_night_execution_capacity_parameter.sql` and requires the exact ID, English name, Chinese name, value `20`, platform `all`, and `editable=true` tuple.

- [ ] **Step 2: Run the seed test and verify RED**

Run:

```bash
cd backend
mvn -q -pl test-agent-persistence -am -Dtest=CommonParameterSeedMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the migration file does not exist.

- [ ] **Step 3: Add the Flyway seed**

Create one idempotence-by-schema migration insert using:

```sql
insert into common_parameters(
    parameter_id, parameter_english, parameter_chinese, parameter_value,
    platform, editable, created_at, updated_at
)
values (
    'param_night_execution_slot_capacity_all',
    'NIGHT_EXECUTION_SLOT_CAPACITY',
    '夜间任务每时段任务上限',
    '20',
    'all',
    true,
    current_timestamp,
    current_timestamp
);
```

- [ ] **Step 4: Verify the seed test GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Write failing update-validation tests**

Extend `CommonParameterManagementApplicationServiceTest` with a repository fixture for the new editable parameter. Assert:

```java
service.updateValue(parameterId, " 21 ", traceId, userId, username)
```

persists normalized `21`; values `"abc"`, `"0"`, `"-1"`, and `"2147483648"` throw `PlatformException` with `ErrorCode.VALIDATION_ERROR`, never call `updateValue`, never save a change log, and never publish `CommonParameterUpdatedEvent`.

- [ ] **Step 6: Run validation tests and verify RED**

Run:

```bash
cd backend
mvn -q -pl test-agent-configuration-management -am \
  -Dtest=CommonParameterManagementApplicationServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: invalid values reach the repository or publish an update event.

- [ ] **Step 7: Implement parameter-specific server validation**

Before calling `existing.withValue`, recognize the exact English name and parse a trimmed positive `int`. Return the normalized decimal string on success. Convert parse, overflow, zero, and negative failures to the unified `VALIDATION_ERROR` without recording a successful audit or publishing an event. Keep all other common parameters' existing nonblank behavior unchanged.

- [ ] **Step 8: Verify validation tests GREEN**

Run the Step 6 command. Expected: PASS.

### Task 2: Add the startup-loaded, event-refreshed capacity registry

**Files:**
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/night/NightExecutionCapacityRegistry.java`
- Create: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/night/NightExecutionCapacityRegistryTest.java`
- Delete: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/night/NightExecutionProperties.java`
- Delete: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/night/NightExecutionPropertiesTest.java`

**Interfaces:**
- Consumes `CommonParameterValues.resolvedValue("NIGHT_EXECUTION_SLOT_CAPACITY", ParameterPlatform.ALL)`.
- Produces `int currentCapacity()` for night-task consumers.
- Handles `loadOnStartup(ApplicationReadyEvent)` and `onCommonParameterReloaded(CommonParameterReloadedEvent)`.

- [ ] **Step 1: Write the failing registry tests**

Test a fake or mocked `CommonParameterValues` for these behaviors:

- `loadOnStartup` loads `20` and `currentCapacity()` returns `20`.
- missing, blank, nonnumeric, overflow, zero, and negative startup values throw `IllegalStateException` naming only `NIGHT_EXECUTION_SLOT_CAPACITY` and a safe reason.
- a matching refresh event replaces `20` with `30`.
- a null-English-name bulk refresh event reloads.
- an unrelated event does not read or replace the snapshot.
- a failed runtime reload retains the previous capacity and does not throw to the broadcaster.

- [ ] **Step 2: Run registry tests and verify RED**

Run:

```bash
cd backend
mvn -q -pl test-agent-opencode-runtime -am \
  -Dtest=NightExecutionCapacityRegistryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compilation fails because `NightExecutionCapacityRegistry` does not exist.

- [ ] **Step 3: Implement the registry**

Implement a Spring `@Service` with:

```java
public static final String PARAMETER_ENGLISH_NAME = "NIGHT_EXECUTION_SLOT_CAPACITY";

public int currentCapacity();

@EventListener(ApplicationReadyEvent.class)
public void loadOnStartup();

@EventListener
public void onCommonParameterReloaded(CommonParameterReloadedEvent event);
```

Use a volatile immutable snapshot. Startup uses a strict load that throws. Runtime refresh computes and validates a complete new value before replacing the snapshot; on failure it logs a safe warning with the parameter name and traceId and retains the old snapshot. Do not log the raw parameter value.

- [ ] **Step 4: Verify registry tests GREEN**

Run the Step 2 command. Expected: PASS.

### Task 3: Switch all night-capacity consumers and remove environment binding

**Files:**
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/night/NightExecutionTaskApplicationService.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/night/NightExecutionReconcileService.java`
- Modify: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/night/NightExecutionTaskApplicationServiceTest.java`
- Modify: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/night/NightExecutionReconcileServiceTest.java`
- Modify: `backend/test-agent-app/src/main/resources/application.yml`
- Modify: `backend/test-agent-app/src/test/java/com/enterprise/testagent/app/config/TestAgentRuntimePropertiesBindingTest.java`
- Modify: `.env.local.example`

**Interfaces:**
- Consumes `NightExecutionCapacityRegistry.currentCapacity()` in slot listing, create, adjust, and rollover reservation paths.
- Removes all runtime references to `NightExecutionProperties` and `test-agent.night-execution.slot-capacity`.

- [ ] **Step 1: Change consumer tests to require a dynamic registry**

Replace test construction of `NightExecutionProperties` with a mocked registry. Add assertions that after changing the mock from `2` to `3`, subsequent slot queries/reservations use `3`; after lowering to the already-reserved count, `available=false` and create/adjust cannot reserve an extra task. Reconcile tests must verify `reserveSlot(candidate, currentCapacity, now)`.

- [ ] **Step 2: Run consumer tests and verify RED**

Run:

```bash
cd backend
mvn -q -pl test-agent-opencode-runtime -am \
  -Dtest='NightExecutionTaskApplicationServiceTest,NightExecutionReconcileServiceTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation or assertions fail while services still require `NightExecutionProperties`.

- [ ] **Step 3: Replace the service dependency**

Change both service constructors and fields to `NightExecutionCapacityRegistry`. Read `currentCapacity()` once per operation and pass that value consistently into window calculation and repository conditional reservation. Delete `NightExecutionProperties` and its test after all references are gone.

- [ ] **Step 4: Remove environment/Spring binding**

Delete the `test-agent.night-execution.slot-capacity` YAML entry and the `.env.local.example` variable. Remove any binding assertion dedicated to this property while preserving unrelated scheduler properties.

- [ ] **Step 5: Verify consumer tests GREEN and scan old configuration**

Run the Step 2 command, then:

```bash
rg -n "TEST_AGENT_NIGHT_EXECUTION_SLOT_CAPACITY|test-agent\.night-execution\.slot-capacity|NightExecutionProperties" \
  .env.local.example backend frontend docs \
  --glob '!docs/superpowers/specs/2026-07-18-night-execution-task-design.md' \
  --glob '!docs/superpowers/specs/2026-07-19-night-execution-capacity-common-parameter-design.md' \
  --glob '!docs/superpowers/plans/2026-07-19-night-execution-capacity-common-parameter.md'
```

Expected: no production/config references; only the stable documentation entries scheduled for cleanup in Task 4 may remain.

### Task 4: Synchronize stable documentation and verify the feature

**Files:**
- Modify: `backend/README.md`
- Modify: `backend/test-agent-app/README.md`
- Modify: `backend/test-agent-app/src/main/java/com/enterprise/testagent/app/PACKAGE.md` if it mentions night capacity binding
- Modify: `backend/test-agent-configuration-management/README.md`
- Modify: `backend/test-agent-configuration-management/src/main/java/com/enterprise/testagent/configuration/management/PACKAGE.md`
- Modify: `backend/test-agent-opencode-runtime/README.md`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/PACKAGE.md`
- Modify: `backend/test-agent-persistence/README.md`
- Modify: `backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/PACKAGE.md`
- Modify: `docs/api/http-api.md`
- Modify: `docs/deployment/backend.md`
- Modify: `docs/deployment/database.md`
- Modify: `docs/standards/backend.md`
- Modify: `docs/standards/security.md`
- Modify: `.agents/session-log.huangzhenren.md`

**Interfaces:**
- Documents one authoritative editable common parameter, strict startup behavior, cross-server event refresh, and unchanged API/event shapes.

- [ ] **Step 1: Update stable documentation**

Document:

- default seed value `20`, `platform=all`, `editable=true`, and `SUPER_ADMIN` modification entry;
- startup load and `CommonParameterUpdatedEvent → server broadcast → CommonParameterReloadedEvent` refresh flow;
- runtime reload failure retains the last value;
- capacity lowering behavior;
- removal of the old environment variable;
- no new HTTP path, DTO, RunEvent, authorization role, or relationship table.

Update the common-parameter cache rule to name this dedicated night-capacity registry as an explicit, user-approved exception; do not weaken the default no-cache rule for other parameters.

- [ ] **Step 2: Run targeted backend tests**

```bash
cd backend
mvn -q \
  -pl test-agent-configuration-management,test-agent-opencode-runtime,test-agent-persistence,test-agent-app \
  -am \
  -Dtest='CommonParameterSeedMigrationTest,CommonParameterManagementApplicationServiceTest,NightExecutionCapacityRegistryTest,NightExecutionTaskApplicationServiceTest,NightExecutionReconcileServiceTest,TestAgentRuntimePropertiesBindingTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests PASS. If the published PostgreSQL-only `V20260717173000__create_public_agent_config_rollouts.sql` still blocks H2-backed app tests, record that existing baseline separately and rerun the non-H2 unit tests plus `CommonParameterSeedMigrationTest`.

- [ ] **Step 3: Run package and frontend regression checks**

```bash
cd backend && mvn -q clean package -DskipTests
cd ../frontend && corepack pnpm test -- GeneralParamManagementPanel
corepack pnpm typecheck
corepack pnpm build
```

Expected: package, relevant frontend regression, typecheck, and builds PASS. Existing Vite large-chunk warnings are non-fatal.

- [ ] **Step 4: Run repository hygiene checks**

```bash
git diff --check
rg -n "TEST_AGENT_NIGHT_EXECUTION_SLOT_CAPACITY|test-agent\.night-execution\.slot-capacity|NightExecutionProperties" \
  .env.local.example backend frontend docs \
  --glob '!docs/superpowers/specs/2026-07-18-night-execution-task-design.md' \
  --glob '!docs/superpowers/specs/2026-07-19-night-execution-capacity-common-parameter-design.md' \
  --glob '!docs/superpowers/plans/2026-07-19-night-execution-capacity-common-parameter.md'
```

Expected: clean diff and no stale stable-code/config references.

- [ ] **Step 5: Review session logs and add one consolidated entry**

Review every `.agents/session-log*.md`, then add one `Why / What / How / Result` entry to `.agents/session-log.huangzhenren.md`. Include exact verification commands and any pre-existing H2 baseline failure. Do not edit the frozen shared log.

- [ ] **Step 6: Stage only task-owned files and commit**

Do not stage the pre-existing Mermaid editor, frontend README/editor README, or reference-file-locate design changes unless they independently became committed by their owner. Run `git diff --cached --check`, inspect `git diff --cached --name-status`, then commit:

```bash
git commit -m "功能：夜间任务容量改用通用参数"
```
