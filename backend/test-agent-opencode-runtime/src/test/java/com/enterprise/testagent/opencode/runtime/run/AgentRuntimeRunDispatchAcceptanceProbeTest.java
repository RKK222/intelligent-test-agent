package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentSessionMessage;
import com.enterprise.testagent.agent.runtime.AgentSessionMessagesResult;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.run.RunId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AgentRuntimeRunDispatchAcceptanceProbeTest {

    @Test
    void confirmsExactDispatchMessageIdAndReturnsNotAcceptedOnlyAfterCompletePage() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.agentId()).thenReturn("opencode");
        when(runtime.sessionMessages(any())).thenReturn(Mono.just(new AgentSessionMessagesResult(
                List.of(new AgentSessionMessage(Map.of("id", "msg_dispatch"), List.of())), null, null)));
        RunRecoveryExecutionNodeResolver nodes = mock(RunRecoveryExecutionNodeResolver.class);
        when(nodes.resolve(any(), any())).thenReturn(Optional.of(mock(ExecutionNode.class)));
        AgentRuntimeRunDispatchAcceptanceProbe probe = new AgentRuntimeRunDispatchAcceptanceProbe(
                new AgentRuntimeRegistry(List.of(runtime)), nodes);

        assertThat(probe.probe(request("msg_dispatch"))).isEqualTo(RunDispatchAcceptance.ACCEPTED);
        assertThat(probe.probe(request("msg_missing"))).isEqualTo(RunDispatchAcceptance.NOT_ACCEPTED);
    }

    @Test
    void remoteFailureIsUnknownAndCannotAuthorizeResend() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.agentId()).thenReturn("opencode");
        when(runtime.sessionMessages(any())).thenReturn(Mono.error(new IllegalStateException("unavailable")));
        RunRecoveryExecutionNodeResolver nodes = mock(RunRecoveryExecutionNodeResolver.class);
        when(nodes.resolve(any(), any())).thenReturn(Optional.of(mock(ExecutionNode.class)));
        AgentRuntimeRunDispatchAcceptanceProbe probe = new AgentRuntimeRunDispatchAcceptanceProbe(
                new AgentRuntimeRegistry(List.of(runtime)), nodes);

        assertThat(probe.probe(request("msg_dispatch"))).isEqualTo(RunDispatchAcceptance.UNKNOWN);
    }

    private RunDispatchProbeRequest request(String messageId) {
        return new RunDispatchProbeRequest(
                new RunId("run_dispatch_probe"), "opencode", "remote-session", messageId,
                "node_probe", "http://127.0.0.1:4096", "ocp_probe", "server-a", "trace_probe");
    }
}
