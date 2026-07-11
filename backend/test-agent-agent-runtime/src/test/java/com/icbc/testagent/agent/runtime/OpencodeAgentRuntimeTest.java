package com.icbc.testagent.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.opencode.client.OpencodeClientFacade;
import com.icbc.testagent.opencode.client.OpencodeStartRunCommand;
import com.icbc.testagent.opencode.client.OpencodeStartRunResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class OpencodeAgentRuntimeTest {

    @Test
    void startRunPropagatesOptionalSystemPrompt() {
        OpencodeClientFacade facade = mock(OpencodeClientFacade.class);
        when(facade.startRun(any())).thenReturn(Mono.just(new OpencodeStartRunResult(true)));
        OpencodeAgentRuntime runtime = new OpencodeAgentRuntime(facade);

        runtime.startRun(new AgentStartRunCommand(
                        node(),
                        "ses_remote1234567890abcdef",
                        "/tmp/demo",
                        null,
                        "检查状态",
                        List.of(AgentPromptPart.text("检查状态")),
                        null,
                        "plan",
                        "只做只读检查并输出最终答案",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "trace_1234567890abcdef"))
                .block();

        ArgumentCaptor<OpencodeStartRunCommand> command = ArgumentCaptor.forClass(OpencodeStartRunCommand.class);
        verify(facade).startRun(command.capture());
        assertThat(command.getValue().agent()).isEqualTo("plan");
        assertThat(command.getValue().system()).isEqualTo("只做只读检查并输出最终答案");
        assertThat(command.getValue().parts()).extracting("type").containsExactly("text");
    }

    private static ExecutionNode node() {
        Instant now = Instant.parse("2026-07-11T00:00:00Z");
        return new ExecutionNode(
                new ExecutionNodeId("node_1234567890abcdef"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                4,
                now);
    }
}
