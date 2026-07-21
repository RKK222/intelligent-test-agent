package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchBatchResult;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchResult;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchService;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchStatus;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/** 验证分发先由公共路由器选择精确 Java，同服务器多 JVM 不会误走当前进程。 */
class HttpNightExecutionDispatchGatewayTest {

    @Test
    void forwardsWhenTheSelectedBackendOnTheSameLinuxServerIsAnotherJvm() {
        BackendJavaRouteResolver routes = mock(BackendJavaRouteResolver.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        NightExecutionDispatchService local = mock(NightExecutionDispatchService.class);
        BackendJavaProcess selected = backend("bjp_remote_same_server", "linux-a");
        when(routes.requireBackend("linux-a")).thenReturn(selected);
        when(routes.isCurrent(selected.backendProcessId())).thenReturn(false);
        NightExecutionTaskId taskId = new NightExecutionTaskId("net_gateway_remote");
        NightExecutionInternalDispatchDtos.Response response = new NightExecutionInternalDispatchDtos.Response(
                "linux-a", List.of(new NightExecutionInternalDispatchDtos.TaskResult(
                        taskId.value(), NightExecutionDispatchStatus.STARTED.name(), "run_gateway_remote", null)));
        doReturn(ApiResponse.ok(response, "trace_gateway"))
                .when(forwarder).forwardSystemTyped(
                        eq(selected), eq(NightExecutionInternalDispatchController.PATH), any(), any(),
                        eq("trace_gateway"), eq("xxl-secret"));

        NightExecutionDispatchBatchResult result = gateway(routes, forwarder, local)
                .dispatch("linux-a", List.of(taskId), "trace_gateway")
                .block();

        assertThat(result.results()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(NightExecutionDispatchStatus.STARTED);
            assertThat(item.runId()).isEqualTo("run_gateway_remote");
        });
        verifyNoInteractions(local);
    }

    @Test
    void executesLocallyOnlyWhenTheSelectedBackendProcessIsCurrent() {
        BackendJavaRouteResolver routes = mock(BackendJavaRouteResolver.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        NightExecutionDispatchService local = mock(NightExecutionDispatchService.class);
        BackendJavaProcess selected = backend("bjp_current_gateway", "linux-a");
        when(routes.requireBackend("linux-a")).thenReturn(selected);
        when(routes.isCurrent(selected.backendProcessId())).thenReturn(true);
        NightExecutionTaskId taskId = new NightExecutionTaskId("net_gateway_local");
        NightExecutionDispatchBatchResult expected = new NightExecutionDispatchBatchResult(
                "linux-a", List.of(new NightExecutionDispatchResult(
                        taskId, NightExecutionDispatchStatus.STARTED, "run_gateway_local", null)));
        when(local.dispatchBatch("linux-a", List.of(taskId), "trace_gateway"))
                .thenReturn(Mono.just(expected));

        assertThat(gateway(routes, forwarder, local)
                .dispatch("linux-a", List.of(taskId), "trace_gateway")
                .block()).isEqualTo(expected);

        verifyNoInteractions(forwarder);
    }

    private HttpNightExecutionDispatchGateway gateway(
            BackendJavaRouteResolver routes,
            BackendHttpForwarder forwarder,
            NightExecutionDispatchService local) {
        XxlJobProperties properties = new XxlJobProperties();
        properties.setAccessToken("xxl-secret");
        return new HttpNightExecutionDispatchGateway(routes, forwarder, local, properties);
    }

    private BackendJavaProcess backend(String backendProcessId, String linuxServerId) {
        Instant now = Instant.parse("2026-07-18T13:00:00Z");
        return new BackendJavaProcess(
                new BackendProcessId(backendProcessId), new LinuxServerId(linuxServerId),
                "http://127.0.0.1:8080", BackendJavaProcessStatus.READY,
                now, now, now, now, "trace_gateway");
    }
}
