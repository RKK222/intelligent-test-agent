package com.enterprise.testagent.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.opencode.client.OpencodeClientFacade;
import com.enterprise.testagent.opencode.client.OpencodePromptPart;
import com.enterprise.testagent.opencode.client.OpencodeRejectDiffResult;
import com.enterprise.testagent.opencode.client.OpencodeSessionMessage;
import com.enterprise.testagent.opencode.client.OpencodeSessionMessagesResult;
import com.enterprise.testagent.opencode.client.OpencodeStartCommand;
import com.enterprise.testagent.opencode.client.OpencodeUnrevertResult;
import com.enterprise.testagent.opencode.client.OpencodeStartRunCommand;
import com.enterprise.testagent.opencode.client.OpencodeStartRunResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class OpencodeAgentRuntimeTest {

    @Test
    void loadsReplayableUserTurnWithOriginalModelAgentVariantAndParts() {
        OpencodeClientFacade facade = mock(OpencodeClientFacade.class);
        List<OpencodeSessionMessage> messages = List.of(
                new OpencodeSessionMessage(
                        Map.of(
                                "id", "msg_original1234567890",
                                "role", "user",
                                "agent", "build",
                                "variant", "high",
                                "model", Map.of("providerID", "openai", "modelID", "gpt-5")),
                        List.of(
                                Map.of("type", "text", "text", "检查附件"),
                                Map.of("type", "file", "url", "file:///tmp/a.txt", "mime", "text/plain",
                                        "filename", "a.txt", "source", Map.of("value", "@a.txt", "start", 0, "end", 6)),
                                Map.of("type", "agent", "name", "review",
                                        "source", Map.of("value", "@review", "start", 7, "end", 14)),
                                Map.of("type", "subtask", "prompt", "核对实现", "description", "审查代码",
                                        "agent", "review", "command", "review"))),
                new OpencodeSessionMessage(
                        Map.of("id", "msg_assistant123456", "role", "assistant"),
                        List.of(Map.of("type", "text", "text", "旧回答"))));
        when(facade.sessionMessages(any())).thenReturn(Mono.just(
                new OpencodeSessionMessagesResult(messages, null, null)));
        OpencodeAgentRuntime runtime = new OpencodeAgentRuntime(facade);

        AgentReplayableTurn turn = runtime.loadReplayableTurn(new AgentReplayableTurnCommand(
                        node(), "ses_remote1234567890abcdef", "/tmp/demo", null,
                        "msg_original1234567890", "trace_1234567890abcdef"))
                .block();

        assertThat(turn.messageId()).isEqualTo("msg_original1234567890");
        assertThat(turn.prompt()).isEqualTo("检查附件");
        assertThat(turn.agent()).isEqualTo("build");
        assertThat(turn.modelProviderId()).isEqualTo("openai");
        assertThat(turn.modelId()).isEqualTo("gpt-5");
        assertThat(turn.variant()).isEqualTo("high");
        assertThat(turn.parts()).extracting(AgentPromptPart::type)
                .containsExactly("text", "file", "agent", "subtask");
    }

    @Test
    void rejectsReplayWhenTargetIsNotTheLastRemoteUserMessage() {
        OpencodeClientFacade facade = mock(OpencodeClientFacade.class);
        when(facade.sessionMessages(any())).thenReturn(Mono.just(new OpencodeSessionMessagesResult(
                List.of(
                        new OpencodeSessionMessage(
                                Map.of("id", "msg_new1234567890", "role", "user"),
                                List.of(Map.of("type", "text", "text", "new"))),
                        new OpencodeSessionMessage(
                                Map.of("id", "msg_old1234567890", "role", "user"),
                                List.of(Map.of("type", "text", "text", "old")))),
                null,
                null)));
        OpencodeAgentRuntime runtime = new OpencodeAgentRuntime(facade);

        assertThatThrownBy(() -> runtime.loadReplayableTurn(new AgentReplayableTurnCommand(
                        node(), "ses_remote1234567890abcdef", "/tmp/demo", null,
                        "msg_old1234567890", "trace_1234567890abcdef"))
                .block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last user message");
    }

    @Test
    void revertsUnrevertsAndProbesStableMessageThroughNeutralRuntimeApi() {
        OpencodeClientFacade facade = mock(OpencodeClientFacade.class);
        when(facade.rejectDiff(any())).thenReturn(Mono.just(new OpencodeRejectDiffResult(true)));
        when(facade.unrevert(any())).thenReturn(Mono.just(new OpencodeUnrevertResult(true)));
        when(facade.sessionMessages(any())).thenReturn(Mono.just(new OpencodeSessionMessagesResult(List.of(
                new OpencodeSessionMessage(Map.of("id", "msg_stable1234567890", "role", "user"), List.of())),
                null, null)));
        OpencodeAgentRuntime runtime = new OpencodeAgentRuntime(facade);
        AgentRevertTurnCommand command = new AgentRevertTurnCommand(
                node(), "ses_remote1234567890abcdef", "/tmp/demo", null,
                "msg_original1234567890", "trace_1234567890abcdef");

        assertThat(runtime.revertTurn(command).block().reverted()).isTrue();
        assertThat(runtime.unrevertTurn(new AgentUnrevertTurnCommand(
                node(), "ses_remote1234567890abcdef", "/tmp/demo", null,
                "trace_1234567890abcdef")).block().unreverted()).isTrue();
        assertThat(runtime.probeMessage(new AgentMessageProbeCommand(
                node(), "ses_remote1234567890abcdef", "msg_stable1234567890",
                "trace_1234567890abcdef")).block().present()).isTrue();
    }

    @Test
    void createsOpencodeCompatibleDispatchMessageId() {
        OpencodeAgentRuntime runtime = new OpencodeAgentRuntime(mock(OpencodeClientFacade.class));

        assertThat(runtime.createDispatchMessageId()).matches("msg_[0-9a-f]{12}[0-9A-Za-z]{14}");
    }

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
                        Map.of("*", false),
                        null,
                        null,
                        "trace_1234567890abcdef"))
                .block();

        ArgumentCaptor<OpencodeStartRunCommand> command = ArgumentCaptor.forClass(OpencodeStartRunCommand.class);
        verify(facade).startRun(command.capture());
        assertThat(command.getValue().agent()).isEqualTo("plan");
        assertThat(command.getValue().system()).isEqualTo("只做只读检查并输出最终答案");
        assertThat(command.getValue().tools()).containsExactly(Map.entry("*", false));
        assertThat(command.getValue().parts()).extracting("type").containsExactly("text");
    }

    @Test
    void commandRunCarriesSystemContextAsInternalInlineAttachment() {
        OpencodeClientFacade facade = mock(OpencodeClientFacade.class);
        when(facade.startCommand(any())).thenReturn(Mono.just(new OpencodeStartRunResult(true)));
        OpencodeAgentRuntime runtime = new OpencodeAgentRuntime(facade);

        runtime.startRun(new AgentStartRunCommand(
                        node(),
                        "ses_remote1234567890abcdef",
                        "/tmp/demo",
                        null,
                        "/review",
                        List.of(AgentPromptPart.text("/review")),
                        null,
                        "build",
                        "<automation_references readonly=\"true\" />",
                        null,
                        null,
                        null,
                        Map.of(),
                        "review",
                        "",
                        "trace_1234567890abcdef"))
                .block();

        ArgumentCaptor<OpencodeStartCommand> captured = ArgumentCaptor.forClass(OpencodeStartCommand.class);
        verify(facade).startCommand(captured.capture());
        assertThat(captured.getValue().arguments()).isEmpty();
        assertThat(captured.getValue().parts()).extracting(OpencodePromptPart::type)
                .containsExactly("text", "file");
        assertThat(captured.getValue().parts().get(1)).satisfies(part -> {
            assertThat(part.type()).isEqualTo("file");
            assertThat(part.filename()).isEqualTo(".testagent-run-context.txt");
            String encoded = part.url().substring(part.url().indexOf(',') + 1);
            assertThat(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8))
                    .isEqualTo("<automation_references readonly=\"true\" />");
        });
    }

    @Test
    void startRunDoesNotForwardInternalTextSourceMetadata() {
        OpencodeClientFacade facade = mock(OpencodeClientFacade.class);
        when(facade.startRun(any())).thenReturn(Mono.just(new OpencodeStartRunResult(true)));
        OpencodeAgentRuntime runtime = new OpencodeAgentRuntime(facade);

        runtime.startRun(new AgentStartRunCommand(
                        node(),
                        "ses_remote1234567890abcdef",
                        "/tmp/demo",
                        null,
                        "分析附件",
                        List.of(
                                AgentPromptPart.text("分析附件"),
                                AgentPromptPart.text(
                                        "用户上传的附件已保存在当前工作区",
                                        Map.of("contextType", "workspace_attachment", "deliveryMode", "workspace"))),
                        null,
                        "build",
                        null,
                        null,
                        null,
                        null,
                        Map.of(),
                        null,
                        null,
                        "trace_1234567890abcdef"))
                .block();

        ArgumentCaptor<OpencodeStartRunCommand> command = ArgumentCaptor.forClass(OpencodeStartRunCommand.class);
        verify(facade).startRun(command.capture());
        assertThat(command.getValue().parts()).extracting("type").containsExactly("text", "text");
        assertThat(command.getValue().parts()).allSatisfy(part -> assertThat(part.source()).isEmpty());
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
