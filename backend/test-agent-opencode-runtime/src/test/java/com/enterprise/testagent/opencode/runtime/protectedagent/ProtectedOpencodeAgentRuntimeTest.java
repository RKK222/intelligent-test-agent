package com.enterprise.testagent.opencode.runtime.protectedagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentCreateSessionCommand;
import com.enterprise.testagent.agent.runtime.AgentCreateSessionResult;
import com.enterprise.testagent.agent.runtime.AgentSessionExistsCommand;
import com.enterprise.testagent.agent.runtime.AgentStartRunCommand;
import com.enterprise.testagent.agent.runtime.AgentStartRunResult;
import com.enterprise.testagent.agent.runtime.OpencodeAgentRuntime;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class ProtectedOpencodeAgentRuntimeTest {

    private static final String REMOTE_SESSION = "ses_protected1234567890";

    @Test
    void storesServerDirectoryAndRewritesPromptDirectory() {
        OpencodeAgentRuntime delegate = org.mockito.Mockito.mock(OpencodeAgentRuntime.class);
        InMemoryDirectoryStore directories = new InMemoryDirectoryStore();
        ProtectedOpencodeAgentRuntime runtime = new ProtectedOpencodeAgentRuntime(delegate, directories);
        when(delegate.createSession(any())).thenReturn(Mono.just(new AgentCreateSessionResult(REMOTE_SESSION)));
        when(delegate.startRun(any())).thenReturn(Mono.just(new AgentStartRunResult(true)));

        runtime.createSession(new AgentCreateSessionCommand(
                        node(), "/srv/protected/ses_1", null, null, "trace_protected"))
                .block();
        runtime.startRun(new AgentStartRunCommand(
                        node(), REMOTE_SESSION, "/Users/local/workspace", null, "hello", List.of(), null,
                        "build", "system", null, null, null, Map.of("bash", false), null, null,
                        "trace_protected"))
                .block();

        ArgumentCaptor<AgentStartRunCommand> captor = ArgumentCaptor.forClass(AgentStartRunCommand.class);
        verify(delegate).startRun(captor.capture());
        assertThat(captor.getValue().directory()).isEqualTo("/srv/protected/ses_1");
        assertThat(captor.getValue().directory()).doesNotContain("/Users/local/workspace");
    }

    @Test
    void missingDirectoryMakesHistoricalSessionUnavailableWithoutRemoteProbe() {
        OpencodeAgentRuntime delegate = org.mockito.Mockito.mock(OpencodeAgentRuntime.class);
        ProtectedOpencodeAgentRuntime runtime = new ProtectedOpencodeAgentRuntime(delegate, new InMemoryDirectoryStore());

        Boolean exists = runtime.sessionExists(new AgentSessionExistsCommand(
                        node(), REMOTE_SESSION, "trace_protected"))
                .block();

        assertThat(exists).isFalse();
        verify(delegate, never()).sessionExists(any());
    }

    private static ExecutionNode node() {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        return new ExecutionNode(
                new ExecutionNodeId("node_protected1234567890"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                4,
                now);
    }

    private static final class InMemoryDirectoryStore implements ProtectedAgentSessionDirectoryStore {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public void save(String remoteSessionId, String directory, java.time.Duration ttl) {
            values.put(remoteSessionId, directory);
        }

        @Override
        public Optional<String> find(String remoteSessionId) {
            return Optional.ofNullable(values.get(remoteSessionId));
        }
    }
}
