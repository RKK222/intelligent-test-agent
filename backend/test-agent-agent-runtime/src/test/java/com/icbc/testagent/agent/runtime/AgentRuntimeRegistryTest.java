package com.icbc.testagent.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRuntimeRegistryTest {

    @Test
    void resolvesOpencodeRuntimeAndNormalizesAgentId() {
        AgentRuntime opencode = new FakeAgentRuntime("opencode");
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(List.of(opencode));

        assertThat(registry.require(" OPENCODE ").agentId()).isEqualTo(opencode.agentId());
        assertThat(registry.defaultAgentId()).isEqualTo("opencode");
    }

    @Test
    void rejectsUnknownAgentWithUnifiedPlatformError() {
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(List.of(new FakeAgentRuntime("opencode")));

        assertThatThrownBy(() -> registry.require("otheragent"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(exception.details()).containsEntry("agentId", "otheragent");
                });
    }

    @Test
    void keepsUuidDispatchMessageIdAsDefaultForOtherAgents() {
        AgentRuntime runtime = new FakeAgentRuntime("other");

        assertThat(runtime.createDispatchMessageId()).matches("msg_[0-9a-f]{32}");
    }

    @Test
    void observedRuntimeDelegatesDispatchMessageIdGeneration() {
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(List.of(new DispatchIdAgentRuntime("opencode")));

        assertThat(registry.require("opencode").createDispatchMessageId()).isEqualTo("msg_delegate_dispatch");
    }

    private record FakeAgentRuntime(String agentId) implements AgentRuntime {
    }

    private record DispatchIdAgentRuntime(String agentId) implements AgentRuntime {

        @Override
        public String createDispatchMessageId() {
            return "msg_delegate_dispatch";
        }
    }
}
