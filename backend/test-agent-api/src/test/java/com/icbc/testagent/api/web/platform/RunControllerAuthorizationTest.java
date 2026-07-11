package com.icbc.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.GlobalExceptionHandler;
import com.icbc.testagent.api.web.common.TraceIdWebFilter;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.event.RunEventSseMapper;
import com.icbc.testagent.event.RunEventSseStreamService;
import com.icbc.testagent.opencode.runtime.run.RunApplicationService;
import com.icbc.testagent.opencode.runtime.run.RunDiffActionResponse;
import com.icbc.testagent.opencode.runtime.run.RunDiffApplicationService;
import com.icbc.testagent.opencode.runtime.run.RunDiffResponse;
import com.icbc.testagent.opencode.runtime.run.RunHistoryRecoveryResult;
import com.icbc.testagent.opencode.runtime.run.RunHistoryRecoverySource;
import com.icbc.testagent.opencode.runtime.run.RunMessageRecoveryService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RunControllerAuthorizationTest {

    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");
    private static final UserId OTHER_USER = new UserId("usr_other_user");
    private static final RunId RUN_ID = new RunId("run_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void foreignUserIsRejectedBeforeEveryRunReadOrSideEffect() {
        RunApplicationService runService = mock(RunApplicationService.class);
        RunDiffApplicationService diffService = mock(RunDiffApplicationService.class);
        RunEventSseStreamService eventStreamService = mock(RunEventSseStreamService.class);
        RunMessageRecoveryService recoveryService = mock(RunMessageRecoveryService.class);
        doThrow(new PlatformException(ErrorCode.FORBIDDEN, "无权访问该 Run"))
                .when(runService).requireRunAccess(OTHER_USER, RUN_ID);
        when(runService.getRun(RUN_ID)).thenReturn(run());
        when(runService.cancelRun(eq(RUN_ID), any())).thenReturn(run());
        when(runService.storageMetadata(RUN_ID)).thenReturn(Optional.empty());
        when(runService.eventStorageMode(RUN_ID)).thenReturn(RunStorageMode.REDIS_SUMMARY);
        when(diffService.getDiff(eq(RUN_ID), any())).thenReturn(new RunDiffResponse(RUN_ID.value(), List.of()));
        when(diffService.acceptDiff(eq(RUN_ID), any()))
                .thenReturn(new RunDiffActionResponse(RUN_ID.value(), "accept", "accepted", 0));
        when(diffService.rejectDiff(eq(RUN_ID), any()))
                .thenReturn(new RunDiffActionResponse(RUN_ID.value(), "reject", "rejected", 0));
        when(eventStreamService.streamAfterWithSnapshot(eq(RUN_ID), any(), any(), any(Integer.class), any()))
                .thenReturn(Flux.empty());
        when(recoveryService.recoverHistory(eq(RUN_ID), any()))
                .thenReturn(Mono.just(RunHistoryRecoveryResult.full(
                        List.of(), null, RunHistoryRecoverySource.REDIS)));
        WebTestClient client = WebTestClient.bindToController(new RunController(
                        runService,
                        diffService,
                        eventStreamService,
                        recoveryService,
                        new RunEventSseMapper()))
                .controllerAdvice(new GlobalExceptionHandler())
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, new AuthPrincipal(
                            "token",
                            OTHER_USER,
                            "other",
                            "other",
                            List.of("APP_ADMIN"),
                            NOW,
                            NOW.plusSeconds(3600)));
                    return chain.filter(exchange);
                })
                .build();

        assertForbidden(client, "GET", "/api/internal/platform/opencode-runtime/runs/" + RUN_ID.value());
        assertForbidden(client, "POST", "/api/internal/platform/opencode-runtime/runs/" + RUN_ID.value() + "/cancel");
        assertForbidden(client, "GET", "/api/internal/platform/opencode-runtime/runs/" + RUN_ID.value() + "/diff");
        assertForbidden(client, "POST", "/api/internal/platform/opencode-runtime/runs/" + RUN_ID.value() + "/diff/accept");
        assertForbidden(client, "POST", "/api/internal/platform/opencode-runtime/runs/" + RUN_ID.value() + "/diff/reject");
        assertForbidden(client, "GET", "/api/internal/platform/opencode-runtime/runs/" + RUN_ID.value() + "/events");
        assertForbidden(client, "GET", "/api/internal/platform/opencode-runtime/runs/" + RUN_ID.value() + "/session-tree/messages");

        verify(runService, never()).getRun(RUN_ID);
        verify(runService, never()).cancelRun(eq(RUN_ID), any());
        verify(runService, never()).eventStorageMode(RUN_ID);
        verifyNoInteractions(diffService, eventStreamService, recoveryService);
    }

    @Test
    void sseAuthorizationRunsOffWebfluxEventLoopBeforeStreamCreation() {
        RunApplicationService runService = mock(RunApplicationService.class);
        RunEventSseStreamService eventStreamService = mock(RunEventSseStreamService.class);
        org.mockito.Mockito.doAnswer(ignored -> {
            assertThat(Thread.currentThread().getName()).contains("boundedElastic");
            return null;
        }).when(runService).requireRunAccess(OTHER_USER, RUN_ID);
        when(runService.eventStorageMode(RUN_ID)).thenReturn(RunStorageMode.REDIS_SUMMARY);
        when(eventStreamService.streamAfterWithSnapshot(eq(RUN_ID), any(), any(), any(Integer.class), any()))
                .thenReturn(Flux.empty());
        WebTestClient client = WebTestClient.bindToController(new RunController(
                        runService,
                        null,
                        eventStreamService))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, new AuthPrincipal(
                            "token", OTHER_USER, "other", "other", List.of("APP_ADMIN"),
                            NOW, NOW.plusSeconds(3600)));
                    return chain.filter(exchange);
                })
                .build();

        client.get()
                .uri("/api/internal/platform/opencode-runtime/runs/" + RUN_ID.value() + "/events")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk();

        verify(eventStreamService).streamAfterWithSnapshot(eq(RUN_ID), any(), any(), eq(100), any());
    }

    private static void assertForbidden(WebTestClient client, String method, String uri) {
        WebTestClient.RequestHeadersSpec<?> request = "POST".equals(method)
                ? client.post().uri(uri)
                : client.get().uri(uri);
        request.header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    private static Run run() {
        return new Run(
                RUN_ID,
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                RunStatus.RUNNING,
                NOW,
                NOW,
                TRACE_ID);
    }
}
