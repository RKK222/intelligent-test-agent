package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.scheduler.ScheduledTask;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRegistrationStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRun;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunFilter;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.scheduler.ScheduledTaskUpdateCommand;
import com.enterprise.testagent.scheduler.SchedulerDiagnostics;
import com.enterprise.testagent.scheduler.SchedulerRuntimeDiagnostics;
import com.enterprise.testagent.scheduler.SchedulerManagementService;
import com.enterprise.testagent.scheduler.ScheduledTaskLockInspection;
import com.enterprise.testagent.scheduler.ScheduledTaskRuntimeDiagnostics;
import com.enterprise.testagent.scheduler.ScheduledTaskDiagnosis;
import com.enterprise.testagent.scheduler.SchedulerDiagnosticBlocker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class SchedulerManagementControllerTest {

    private static final Instant NOW = Instant.parse("2026-06-25T00:00:00Z");
    private static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("daily.cleanup");
    private static final ScheduledTaskRunId TASK_RUN_ID = new ScheduledTaskRunId("str_1234567890abcdef");
    private static final UserId ADMIN_USER_ID = new UserId("usr_admin_1234567890");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void superAdminCanListTasks() {
        SchedulerManagementService service = org.mockito.Mockito.mock(SchedulerManagementService.class);
        mockLabels(service);
        when(service.findTasks(eq(new PageRequest(1, 20))))
                .thenReturn(new PageResponse<>(List.of(task()), 1, 20, 1));
        when(service.findCurrentRunByTaskKey(eq(TASK_KEY)))
                .thenReturn(Optional.of(run(ScheduledTaskRunStatus.RUNNING, ScheduledTaskTriggerType.CRON)));
        when(service.findLatestRunByTaskKey(eq(TASK_KEY)))
                .thenReturn(Optional.of(run(ScheduledTaskRunStatus.SUCCEEDED, ScheduledTaskTriggerType.MANUAL)));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/scheduler-management/tasks?page=1&size=20")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].taskKey").isEqualTo("daily.cleanup")
                .jsonPath("$.data.items[0].lockTtlSeconds").isEqualTo(300)
                .jsonPath("$.data.items[0].registrationStatus").isEqualTo("REGISTERED")
                .jsonPath("$.data.items[0].registrationStatusLabel").isEqualTo("已注册")
                .jsonPath("$.data.items[0].currentRun.status").isEqualTo("RUNNING")
                .jsonPath("$.data.items[0].currentRun.statusLabel").isEqualTo("运行中")
                .jsonPath("$.data.items[0].latestRun.status").isEqualTo("SUCCEEDED")
                .jsonPath("$.data.items[0].latestRun.statusLabel").isEqualTo("成功");
    }

    @Test
    void superAdminCanPatchTaskAndTriggerManualRun() {
        SchedulerManagementService service = org.mockito.Mockito.mock(SchedulerManagementService.class);
        mockLabels(service);
        when(service.updateTask(
                        eq(TASK_KEY),
                        argThat(command -> command != null
                                && Boolean.FALSE.equals(command.enabled())
                                && "0 0 3 * * *".equals(command.cronExpression())
                                && Duration.ofSeconds(600).equals(command.lockTtl())),
                        eq(TRACE_ID)))
                .thenReturn(task().withAdminSchedule(false, "0 0 3 * * *", Duration.ofMinutes(10), NOW));
        when(service.trigger(eq(TASK_KEY), eq(ADMIN_USER_ID), eq(TRACE_ID)))
                .thenReturn(run(ScheduledTaskRunStatus.PENDING, ScheduledTaskTriggerType.MANUAL));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.patch()
                .uri("/api/internal/platform/scheduler-management/tasks/daily.cleanup")
                .header("X-Trace-Id", TRACE_ID)
                .bodyValue(Map.of("enabled", false, "cronExpression", "0 0 3 * * *", "lockTtlSeconds", 600))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.enabled").isEqualTo(false)
                .jsonPath("$.data.cronExpression").isEqualTo("0 0 3 * * *")
                .jsonPath("$.data.lockTtlSeconds").isEqualTo(600);

        client.post()
                .uri("/api/internal/platform/scheduler-management/tasks/daily.cleanup/trigger")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.taskRunId").isEqualTo(TASK_RUN_ID.value())
                .jsonPath("$.data.triggerType").isEqualTo("MANUAL")
                .jsonPath("$.data.triggerTypeLabel").isEqualTo("手工触发")
                .jsonPath("$.data.status").isEqualTo("PENDING")
                .jsonPath("$.data.statusLabel").isEqualTo("待执行")
                .jsonPath("$.data.requestedByUserId").isEqualTo(ADMIN_USER_ID.value());
    }

    @Test
    void superAdminCanFilterRunsAndQueryRunDetail() {
        SchedulerManagementService service = org.mockito.Mockito.mock(SchedulerManagementService.class);
        mockLabels(service);
        when(service.findRuns(
                        argThat(filter -> filterMatches(filter)),
                        eq(new PageRequest(2, 10))))
                .thenReturn(new PageResponse<>(
                        List.of(run(ScheduledTaskRunStatus.SUCCEEDED, ScheduledTaskTriggerType.MANUAL)),
                        2,
                        10,
                        1));
        when(service.getRun(eq(TASK_RUN_ID)))
                .thenReturn(run(ScheduledTaskRunStatus.SUCCEEDED, ScheduledTaskTriggerType.MANUAL));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/scheduler-management/runs?taskKey=daily.cleanup&status=SUCCEEDED&triggerType=MANUAL&requestedByUserId=usr_admin_1234567890&page=2&size=10")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].taskRunId").isEqualTo(TASK_RUN_ID.value())
                .jsonPath("$.data.items[0].status").isEqualTo("SUCCEEDED")
                .jsonPath("$.data.items[0].statusLabel").isEqualTo("成功");

        client.get()
                .uri("/api/internal/platform/scheduler-management/runs/str_1234567890abcdef")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.taskKey").isEqualTo("daily.cleanup")
                .jsonPath("$.data.result.ok").isEqualTo(true);
    }

    @Test
    void superAdminCanStopRunningSchedulerRun() {
        SchedulerManagementService service = org.mockito.Mockito.mock(SchedulerManagementService.class);
        mockLabels(service);
        when(service.stopRun(eq(TASK_RUN_ID), eq(ADMIN_USER_ID), eq(TRACE_ID)))
                .thenReturn(run(ScheduledTaskRunStatus.STOPPING, ScheduledTaskTriggerType.MANUAL));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.post()
                .uri("/api/internal/platform/scheduler-management/runs/str_1234567890abcdef/stop")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("STOPPING")
                .jsonPath("$.data.statusLabel").isEqualTo("停止中")
                .jsonPath("$.data.stopRequestedByUserId").isEqualTo(ADMIN_USER_ID.value())
                .jsonPath("$.data.stopReason").isEqualTo("管理员手工停止");
    }

    @Test
    void superAdminCanQuerySchedulerDiagnostics() {
        SchedulerManagementService service = org.mockito.Mockito.mock(SchedulerManagementService.class);
        when(service.diagnostics(eq(TASK_KEY))).thenReturn(diagnostics());
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/scheduler-management/diagnostics?taskKey=daily.cleanup")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.scheduler.enabled").isEqualTo(true)
                .jsonPath("$.data.scheduler.runnerRunning").isEqualTo(false)
                .jsonPath("$.data.scheduler.instanceId").isEqualTo("scheduler-test-instance")
                .jsonPath("$.data.redisLock.locked").isEqualTo(true)
                .jsonPath("$.data.redisLock.ttlMillis").isEqualTo(42000)
                .jsonPath("$.data.task.taskKey").isEqualTo("daily.cleanup")
                .jsonPath("$.data.task.pendingManualRunCount").isEqualTo(1)
                .jsonPath("$.data.diagnosis.manualTriggerReady").isEqualTo(false)
                .jsonPath("$.data.diagnosis.blockers[0].code").isEqualTo("RUNNER_NOT_RUNNING");
    }

    @Test
    void appAdminAndAnonymousUsersCannotAccessSchedulerManagement() {
        SchedulerManagementService service = org.mockito.Mockito.mock(SchedulerManagementService.class);

        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).get()
                .uri("/api/internal/platform/scheduler-management/tasks")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");

        WebTestClient.bindToController(new SchedulerManagementController(service))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build()
                .get()
                .uri("/api/internal/platform/scheduler-management/tasks")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void invalidRunStatusUsesUnifiedValidationError() {
        SchedulerManagementService service = org.mockito.Mockito.mock(SchedulerManagementService.class);

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).get()
                .uri("/api/internal/platform/scheduler-management/runs?status=UNKNOWN")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.details.status").isEqualTo("UNKNOWN");
    }

    private static WebTestClient client(SchedulerManagementService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                ADMIN_USER_ID,
                "admin",
                "AUTH_1",
                roles,
                NOW,
                NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new SchedulerManagementController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static boolean filterMatches(ScheduledTaskRunFilter filter) {
        return filter.taskKey().equals(TASK_KEY)
                && filter.status() == ScheduledTaskRunStatus.SUCCEEDED
                && filter.triggerType() == ScheduledTaskTriggerType.MANUAL
                && filter.requestedByUserId().equals(ADMIN_USER_ID);
    }

    private static ScheduledTask task() {
        return ScheduledTask.registered(
                        TASK_KEY,
                        "Daily Cleanup",
                        "0 0 2 * * *",
                        Duration.ofMinutes(5),
                        NOW.minusSeconds(3600),
                        TRACE_ID)
                .withNextFireAt(Instant.parse("2026-06-25T02:00:00Z"), NOW.minusSeconds(3600));
    }

    private static ScheduledTaskRun run(ScheduledTaskRunStatus status, ScheduledTaskTriggerType triggerType) {
        ScheduledTaskRun pending = ScheduledTaskRun.pending(
                TASK_RUN_ID,
                TASK_KEY,
                null,
                triggerType,
                ADMIN_USER_ID,
                NOW,
                TRACE_ID);
        if (status == ScheduledTaskRunStatus.PENDING) {
            return pending;
        }
        ScheduledTaskRun running = pending.start("scheduler-test-instance", NOW);
        if (status == ScheduledTaskRunStatus.RUNNING) {
            return running;
        }
        if (status == ScheduledTaskRunStatus.STOPPING) {
            return running.requestStop(ADMIN_USER_ID, "管理员手工停止", NOW.plusSeconds(1));
        }
        if (status == ScheduledTaskRunStatus.MANUALLY_STOPPED) {
            return running.requestStop(ADMIN_USER_ID, "管理员手工停止", NOW.plusSeconds(1))
                    .manuallyStopped(NOW.plusSeconds(2));
        }
        if (status == ScheduledTaskRunStatus.SUCCEEDED) {
            return running.succeed(Map.of("ok", true), NOW.plusSeconds(1));
        }
        return running.fail("INTERNAL_ERROR", "failed", NOW.plusSeconds(1));
    }

    private static SchedulerDiagnostics diagnostics() {
        return new SchedulerDiagnostics(
                new SchedulerRuntimeDiagnostics(
                        true,
                        false,
                        "scheduler-test-instance",
                        30,
                        50,
                        50,
                        NOW.minusSeconds(30),
                        NOW.minusSeconds(20),
                        null),
                ScheduledTaskLockInspection.locked("test-agent:scheduler:lock:daily.cleanup", 42_000L),
                new ScheduledTaskRuntimeDiagnostics(
                        "daily.cleanup",
                        true,
                        "REGISTERED",
                        "已注册",
                        Instant.parse("2026-06-25T02:00:00Z"),
                        300,
                        null,
                        null,
                        1),
                new ScheduledTaskDiagnosis(
                        false,
                        false,
                        List.of(new SchedulerDiagnosticBlocker("RUNNER_NOT_RUNNING", "后台扫描线程未运行"))));
    }

    private static void mockLabels(SchedulerManagementService service) {
        when(service.registrationStatusLabel(ScheduledTaskRegistrationStatus.REGISTERED)).thenReturn("已注册");
        when(service.runStatusLabel(ScheduledTaskRunStatus.PENDING)).thenReturn("待执行");
        when(service.runStatusLabel(ScheduledTaskRunStatus.RUNNING)).thenReturn("运行中");
        when(service.runStatusLabel(ScheduledTaskRunStatus.STOPPING)).thenReturn("停止中");
        when(service.runStatusLabel(ScheduledTaskRunStatus.SUCCEEDED)).thenReturn("成功");
        when(service.runStatusLabel(ScheduledTaskRunStatus.MANUALLY_STOPPED)).thenReturn("人工停止");
        when(service.triggerTypeLabel(ScheduledTaskTriggerType.CRON)).thenReturn("定时触发");
        when(service.triggerTypeLabel(ScheduledTaskTriggerType.MANUAL)).thenReturn("手工触发");
        when(service.findCurrentRunByTaskKey(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(service.findLatestRunByTaskKey(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
    }
}
