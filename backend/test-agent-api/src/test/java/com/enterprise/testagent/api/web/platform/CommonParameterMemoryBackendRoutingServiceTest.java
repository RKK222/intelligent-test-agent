package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.configuration.management.CommonParameterMemoryApplicationService;
import com.enterprise.testagent.configuration.management.CommonParameterMemoryResponses.ClusterResponse;
import com.enterprise.testagent.configuration.management.CommonParameterMemoryResponses.ProcessResponse;
import com.enterprise.testagent.configuration.management.CommonParameterMemoryResponses.ProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class CommonParameterMemoryBackendRoutingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    @SuppressWarnings("unchecked")
    void aggregatesEveryJavaProcessWithoutMergingProcessesOnTheSameServer() {
        CommonParameterMemoryApplicationService localService = mock(CommonParameterMemoryApplicationService.class);
        BackendJavaRouteResolver resolver = mock(BackendJavaRouteResolver.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        BackendJavaProcess current = backend("bjp_current_backend", "server-b", "http://server-b:8080");
        BackendJavaProcess first = backend("bjp_first_backend", "server-a", "http://server-a:8080");
        BackendJavaProcess second = backend("bjp_second_backend", "server-a", "http://server-a:18080");
        when(resolver.liveBackendSnapshots(500)).thenReturn(List.of(snapshot(current), snapshot(second), snapshot(first)));
        stubTarget(resolver, current, true);
        stubTarget(resolver, first, false);
        stubTarget(resolver, second, false);
        when(localService.current()).thenReturn(response(current, ProcessStatus.SUCCESS));
        doAnswer(invocation -> {
            BackendJavaProcess target = invocation.getArgument(1);
            return ApiResponse.ok(response(target, ProcessStatus.SUCCESS), TRACE_ID);
        }).when(forwarder).forwardTyped(
                any(), any(BackendJavaProcess.class), anyString(), eq("GET"), isNull(), any(TypeReference.class));
        CommonParameterMemoryBackendRoutingService service = service(localService, resolver, forwarder, Duration.ofSeconds(10));

        ClusterResponse result = service.queryAll(exchange("GET", "/memory-values")).block();

        assertThat(result).isNotNull();
        assertThat(result.totalProcesses()).isEqualTo(3);
        assertThat(result.successfulProcesses()).isEqualTo(3);
        assertThat(result.processes()).extracting(ProcessResponse::backendProcessId)
                .containsExactly("bjp_first_backend", "bjp_second_backend", "bjp_current_backend");
    }

    @Test
    @SuppressWarnings("unchecked")
    void refreshesOneRemoteJavaWithExactPathAndPostMethod() {
        CommonParameterMemoryApplicationService localService = mock(CommonParameterMemoryApplicationService.class);
        BackendJavaRouteResolver resolver = mock(BackendJavaRouteResolver.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        BackendJavaProcess remote = backend("bjp_target_backend", "server-a", "http://server-a:8080");
        stubTarget(resolver, remote, false);
        when(forwarder.forwardTyped(
                any(), eq(remote),
                eq("/api/internal/platform/configuration-management/common-parameters/memory-values/bjp_target_backend/refresh"),
                eq("POST"), isNull(), any(TypeReference.class)))
                .thenReturn(ApiResponse.ok(response(remote, ProcessStatus.SUCCESS), TRACE_ID));
        CommonParameterMemoryBackendRoutingService service = service(localService, resolver, forwarder, Duration.ofSeconds(10));

        ProcessResponse result = service.refreshOne(
                exchange("POST", "/memory-values/bjp_target_backend/refresh"),
                remote.backendProcessId(),
                TRACE_ID).block();

        assertThat(result).isNotNull();
        assertThat(result.backendProcessId()).isEqualTo("bjp_target_backend");
        verify(localService, never()).refresh(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void clusterKeepsPartialAndUnavailableProcessResultsWithHttpLevelSuccessData() {
        CommonParameterMemoryApplicationService localService = mock(CommonParameterMemoryApplicationService.class);
        BackendJavaRouteResolver resolver = mock(BackendJavaRouteResolver.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        BackendJavaProcess partial = backend("bjp_partial_backend", "server-a", "http://server-a:8080");
        BackendJavaProcess unavailable = backend("bjp_unavailable_backend", "server-c", "http://server-c:8080");
        when(resolver.liveBackendSnapshots(500)).thenReturn(List.of(snapshot(unavailable), snapshot(partial)));
        stubTarget(resolver, partial, false);
        stubTarget(resolver, unavailable, false);
        doAnswer(invocation -> {
            BackendJavaProcess target = invocation.getArgument(1);
            if (target.backendProcessId().equals(unavailable.backendProcessId())) {
                throw new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "目标 Java 进程不可用",
                        Map.of("backendProcessId", target.backendProcessId().value()));
            }
            return ApiResponse.ok(response(target, ProcessStatus.PARTIAL), TRACE_ID);
        }).when(forwarder).forwardTyped(
                any(), any(BackendJavaProcess.class), anyString(), eq("POST"), isNull(), any(TypeReference.class));
        CommonParameterMemoryBackendRoutingService service = service(localService, resolver, forwarder, Duration.ofSeconds(10));

        ClusterResponse result = service.refreshAll(exchange("POST", "/memory-values/refresh"), TRACE_ID).block();

        assertThat(result).isNotNull();
        assertThat(result.partiallySuccessfulProcesses()).isEqualTo(1);
        assertThat(result.failedProcesses()).isEqualTo(1);
        assertThat(result.processes()).extracting(ProcessResponse::status)
                .containsExactly(ProcessStatus.PARTIAL, ProcessStatus.UNAVAILABLE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void clusterKeepsUnavailablePlaceholderWhenRemoteSuccessEnvelopeHasNoData() {
        CommonParameterMemoryApplicationService localService = mock(CommonParameterMemoryApplicationService.class);
        BackendJavaRouteResolver resolver = mock(BackendJavaRouteResolver.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        BackendJavaProcess remote = backend("bjp_empty_backend", "server-a", "http://server-a:8080");
        when(resolver.liveBackendSnapshots(500)).thenReturn(List.of(snapshot(remote)));
        stubTarget(resolver, remote, false);
        when(forwarder.forwardTyped(
                any(), eq(remote), anyString(), eq("GET"), isNull(), any(TypeReference.class)))
                .thenReturn(ApiResponse.ok(null, TRACE_ID));
        CommonParameterMemoryBackendRoutingService service = service(
                localService, resolver, forwarder, Duration.ofSeconds(10));

        ClusterResponse result = service.queryAll(exchange("GET", "/memory-values")).block();

        assertThat(result).isNotNull();
        assertThat(result.totalProcesses()).isEqualTo(1);
        assertThat(result.failedProcesses()).isEqualTo(1);
        assertThat(result.processes()).singleElement().satisfies(process -> {
            assertThat(process.backendProcessId()).isEqualTo("bjp_empty_backend");
            assertThat(process.status()).isEqualTo(ProcessStatus.UNAVAILABLE);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void singleProcessTimeoutAndRoutedLoopBothReturnUnifiedUnavailable() {
        CommonParameterMemoryApplicationService localService = mock(CommonParameterMemoryApplicationService.class);
        BackendJavaRouteResolver resolver = mock(BackendJavaRouteResolver.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        BackendJavaProcess remote = backend("bjp_slow_backend", "server-a", "http://server-a:8080");
        stubTarget(resolver, remote, false);
        doAnswer(invocation -> {
            LockSupport.parkNanos(Duration.ofMillis(200).toNanos());
            return ApiResponse.ok(response(remote, ProcessStatus.SUCCESS), TRACE_ID);
        }).when(forwarder).forwardTyped(
                any(), eq(remote), anyString(), eq("GET"), isNull(), any(TypeReference.class));
        CommonParameterMemoryBackendRoutingService service = service(localService, resolver, forwarder, Duration.ofMillis(20));

        assertThatThrownBy(() -> service.queryOne(exchange("GET", "/memory-values/bjp_slow_backend"), remote.backendProcessId()).block())
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));

        MockServerWebExchange routedExchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/memory-values/bjp_slow_backend")
                .header(BackendHttpForwarder.ROUTED_HEADER, "true")
                .build());
        assertThatThrownBy(() -> service.queryOne(routedExchange, remote.backendProcessId()).block())
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void singleRemoteFailureDoesNotExposeUnderlyingForwardingCause() {
        CommonParameterMemoryApplicationService localService = mock(CommonParameterMemoryApplicationService.class);
        BackendJavaRouteResolver resolver = mock(BackendJavaRouteResolver.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        BackendJavaProcess remote = backend("bjp_failed_backend", "server-a", "http://server-a:8080");
        stubTarget(resolver, remote, false);
        doAnswer(invocation -> {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "目标服务器后端不可用",
                    Map.of("backendProcessId", remote.backendProcessId().value()),
                    new IllegalStateException("raw-database-secret"));
        }).when(forwarder).forwardTyped(
                any(), eq(remote), anyString(), eq("GET"), isNull(), any(TypeReference.class));
        CommonParameterMemoryBackendRoutingService service = service(localService, resolver, forwarder, Duration.ofSeconds(10));

        assertThatThrownBy(() -> service.queryOne(
                        exchange("GET", "/memory-values/bjp_failed_backend"),
                        remote.backendProcessId()).block())
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage()).doesNotContain("raw-database-secret");
                });
    }

    private static CommonParameterMemoryBackendRoutingService service(
            CommonParameterMemoryApplicationService localService,
            BackendJavaRouteResolver resolver,
            BackendHttpForwarder forwarder,
            Duration timeout) {
        return new CommonParameterMemoryBackendRoutingService(
                localService,
                resolver,
                forwarder,
                Clock.fixed(NOW, ZoneOffset.UTC),
                timeout);
    }

    private static void stubTarget(
            BackendJavaRouteResolver resolver,
            BackendJavaProcess backend,
            boolean current) {
        when(resolver.requireBackend(backend.backendProcessId())).thenReturn(backend);
        when(resolver.isCurrent(backend.backendProcessId())).thenReturn(current);
    }

    private static MockServerWebExchange exchange(String method, String path) {
        MockServerHttpRequest.BaseBuilder<?> request = "POST".equals(method)
                ? MockServerHttpRequest.post(path)
                : MockServerHttpRequest.get(path);
        return MockServerWebExchange.from(request.header("X-Trace-Id", TRACE_ID).build());
    }

    private static BackendJavaProcess backend(String processId, String serverId, String listenUrl) {
        return new BackendJavaProcess(
                new BackendProcessId(processId),
                new LinuxServerId(serverId),
                listenUrl,
                BackendJavaProcessStatus.READY,
                NOW.minusSeconds(60),
                NOW,
                NOW.minusSeconds(60),
                NOW,
                TRACE_ID);
    }

    private static BackendRuntimeSnapshot snapshot(BackendJavaProcess backend) {
        return new BackendRuntimeSnapshot(
                new LinuxServer(
                        backend.linuxServerId(),
                        backend.linuxServerId().value(),
                        LinuxServerStatus.READY,
                        Map.of("backendListenUrl", backend.listenUrl()),
                        NOW,
                        NOW,
                        NOW,
                        TRACE_ID),
                backend);
    }

    private static ProcessResponse response(BackendJavaProcess backend, ProcessStatus status) {
        return new ProcessResponse(
                backend.backendProcessId().value(),
                backend.linuxServerId().value(),
                backend.listenUrl(),
                "instance-" + backend.backendProcessId().value(),
                NOW,
                status,
                status == ProcessStatus.SUCCESS ? null : "REFRESH_PARTIAL",
                status == ProcessStatus.SUCCESS ? null : "部分参数刷新失败",
                List.of());
    }
}
