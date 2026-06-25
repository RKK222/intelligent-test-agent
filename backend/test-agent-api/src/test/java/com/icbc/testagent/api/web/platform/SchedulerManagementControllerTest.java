package com.icbc.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.scheduler.ScheduledTask;
import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRun;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunFilter;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunId;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.icbc.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.scheduler.ScheduledTaskUpdateCommand;
import com.icbc.testagent.scheduler.SchedulerManagementService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
        when(service.findTasks(eq(new PageRequest(1, 20))))
                .thenReturn(new PageResponse<>(List.of(task()), 1, 20, 1));
        WebTestClient client = client(service, List.of(Dictionary.ROLE_SUPER_ADMIN));

        client.get()
                .uri("/api/internal/platform/scheduler-management/tasks?page=1&size=20")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].taskKey").isEqualTo("daily.cleanup")
                .jsonPath("$.data.items[0].lockTtlSeconds").isEqualTo(300)
                .jsonPath("$.data.items[0].registrationStatus").isEqualTo("REGISTERED");
    }

    @Test
    void superAdminCanPatchTaskAndTriggerManualRun() {
        SchedulerManagementService service = org.mockito.Mockito.mock(SchedulerManagementService.class);
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
                .jsonPath("$.data.status").isEqualTo("PENDING")
                .jsonPath("$.data.requestedByUserId").isEqualTo(ADMIN_USER_ID.value());
    }

    @Test
    void superAdminCanFilterRunsAndQueryRunDetail() {
        SchedulerManagementService service = org.mockito.Mockito.mock(SchedulerManagementService.class);
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
                .jsonPath("$.data.items[0].status").isEqualTo("SUCCEEDED");

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
        if (status == ScheduledTaskRunStatus.SUCCEEDED) {
            return running.succeed(Map.of("ok", true), NOW.plusSeconds(1));
        }
        return running.fail("INTERNAL_ERROR", "failed", NOW.plusSeconds(1));
    }
}
