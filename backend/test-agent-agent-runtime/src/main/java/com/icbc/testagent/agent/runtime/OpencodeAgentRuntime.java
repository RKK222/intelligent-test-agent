package com.icbc.testagent.agent.runtime;

import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.opencode.client.OpencodeCancelCommand;
import com.icbc.testagent.opencode.client.OpencodeCancelResult;
import com.icbc.testagent.opencode.client.OpencodeClientFacade;
import com.icbc.testagent.opencode.client.OpencodeCreateSessionCommand;
import com.icbc.testagent.opencode.client.OpencodeCreateSessionResult;
import com.icbc.testagent.opencode.client.OpencodeDiffCommand;
import com.icbc.testagent.opencode.client.OpencodeDiffFile;
import com.icbc.testagent.opencode.client.OpencodeDiffResult;
import com.icbc.testagent.opencode.client.OpencodeMessageIdGenerator;
import com.icbc.testagent.opencode.client.OpencodePromptPart;
import com.icbc.testagent.opencode.client.OpencodeRejectDiffCommand;
import com.icbc.testagent.opencode.client.OpencodeRejectDiffResult;
import com.icbc.testagent.opencode.client.OpencodeRuntimeCommand;
import com.icbc.testagent.opencode.client.OpencodeRuntimeResult;
import com.icbc.testagent.opencode.client.OpencodeSessionExistsCommand;
import com.icbc.testagent.opencode.client.OpencodeSessionMessage;
import com.icbc.testagent.opencode.client.OpencodeSessionMessagesCommand;
import com.icbc.testagent.opencode.client.OpencodeSessionMessagesResult;
import com.icbc.testagent.opencode.client.OpencodeStartRunCommand;
import com.icbc.testagent.opencode.client.OpencodeStartRunResult;
import com.icbc.testagent.opencode.client.OpencodeStartCommand;
import com.icbc.testagent.opencode.client.OpencodeStreamEventsCommand;
import java.util.Objects;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * opencode 运行时适配器，唯一把通用 AgentRuntime 命令转换为 OpencodeClientFacade 调用的实现。
 */
@Service
public class OpencodeAgentRuntime implements AgentRuntime {

    private final OpencodeClientFacade opencodeClientFacade;
    private final OpencodeMessageIdGenerator messageIdGenerator = new OpencodeMessageIdGenerator();

    /**
     * 注入 opencode facade；generated SDK 仍只在 opencode-client 模块内部使用。
     */
    public OpencodeAgentRuntime(OpencodeClientFacade opencodeClientFacade) {
        this.opencodeClientFacade = Objects.requireNonNull(opencodeClientFacade, "opencodeClientFacade must not be null");
    }

    @Override
    public String agentId() {
        return AgentRuntimeRegistry.DEFAULT_AGENT_ID;
    }

    @Override
    public String createDispatchMessageId() {
        return messageIdGenerator.nextId();
    }

    @Override
    public Mono<AgentCreateSessionResult> createSession(AgentCreateSessionCommand command) {
        return opencodeClientFacade.createSession(new OpencodeCreateSessionCommand(
                        command.node(),
                        command.directory(),
                        command.workspace(),
                        command.title(),
                        command.traceId()))
                .map(this::toCreateSessionResult);
    }

    @Override
    public Mono<Boolean> sessionExists(AgentSessionExistsCommand command) {
        return opencodeClientFacade.sessionExists(new OpencodeSessionExistsCommand(
                command.node(),
                command.remoteSessionId(),
                command.traceId()));
    }

    @Override
    public Mono<AgentStartRunResult> startRun(AgentStartRunCommand command) {
        if (command.command() != null) {
            return opencodeClientFacade.startCommand(new OpencodeStartCommand(
                            command.node(),
                            command.remoteSessionId(),
                            command.directory(),
                            command.workspace(),
                            command.command(),
                            command.arguments(),
                            command.parts().stream().map(this::toOpencodePromptPart).toList(),
                            command.messageId(),
                            command.agent(),
                            command.modelProviderId(),
                            command.modelId(),
                            command.variant(),
                            command.traceId()))
                    .map(this::toStartRunResult);
        }
        return opencodeClientFacade.startRun(new OpencodeStartRunCommand(
                        command.node(),
                        command.remoteSessionId(),
                        command.directory(),
                        command.workspace(),
                        command.prompt(),
                        command.parts().stream().map(this::toOpencodePromptPart).toList(),
                        command.messageId(),
                        command.agent(),
                        command.system(),
                        command.modelProviderId(),
                        command.modelId(),
                        command.variant(),
                        command.tools(),
                        command.traceId()))
                .map(this::toStartRunResult);
    }

    @Override
    public Mono<AgentCancelResult> cancelSession(AgentCancelCommand command) {
        return opencodeClientFacade.cancelSession(new OpencodeCancelCommand(
                        command.node(),
                        command.remoteSessionId(),
                        command.directory(),
                        command.workspace(),
                        command.traceId()))
                .map(this::toCancelResult);
    }

    @Override
    public Flux<RunEventDraft> streamRunEvents(AgentStreamEventsCommand command) {
        return opencodeClientFacade.streamRunEvents(new OpencodeStreamEventsCommand(
                command.node(),
                command.runId(),
                command.remoteSessionId(),
                command.directory(),
                command.workspace(),
                command.traceId()));
    }

    /** 把 OpenCode facade 的真实 HTTP/SSE 握手边界映射到通用 AgentRuntime。 */
    @Override
    public AgentEventStream openRunEventStream(AgentStreamEventsCommand command) {
        com.icbc.testagent.opencode.client.OpencodeRunEventStream opened = opencodeClientFacade.openRunEventStream(
                new OpencodeStreamEventsCommand(
                        command.node(),
                        command.runId(),
                        command.remoteSessionId(),
                        command.directory(),
                        command.workspace(),
                        command.traceId()));
        return new AgentEventStream(opened.ready(), opened.events());
    }

    @Override
    public Mono<AgentDiffResult> getDiff(AgentDiffCommand command) {
        return opencodeClientFacade.getDiff(new OpencodeDiffCommand(
                        command.node(),
                        command.remoteSessionId(),
                        command.directory(),
                        command.workspace(),
                        command.messageId(),
                        command.traceId()))
                .map(this::toDiffResult);
    }

    @Override
    public Mono<AgentRejectDiffResult> rejectDiff(AgentRejectDiffCommand command) {
        return opencodeClientFacade.rejectDiff(new OpencodeRejectDiffCommand(
                        command.node(),
                        command.remoteSessionId(),
                        command.directory(),
                        command.workspace(),
                        command.messageId(),
                        command.partId(),
                        command.traceId()))
                .map(this::toRejectDiffResult);
    }

    @Override
    public Mono<AgentRuntimeResult> runtime(AgentRuntimeCommand command) {
        return opencodeClientFacade.runtime(new OpencodeRuntimeCommand(
                        command.node(),
                        command.method(),
                        command.path(),
                        command.directory(),
                        command.workspace(),
                        command.query(),
                        command.body(),
                        command.traceId()))
                .map(this::toRuntimeResult);
    }

    @Override
    public Mono<AgentSessionMessagesResult> sessionMessages(AgentSessionMessagesCommand command) {
        return opencodeClientFacade.sessionMessages(new OpencodeSessionMessagesCommand(
                        command.node(),
                        command.remoteSessionId(),
                        command.limit(),
                        command.order(),
                        command.cursor(),
                        command.traceId()))
                .map(this::toSessionMessagesResult);
    }

    private AgentCreateSessionResult toCreateSessionResult(OpencodeCreateSessionResult result) {
        return new AgentCreateSessionResult(result.opencodeSessionId());
    }

    private AgentStartRunResult toStartRunResult(OpencodeStartRunResult result) {
        return new AgentStartRunResult(result.accepted());
    }

    private AgentCancelResult toCancelResult(OpencodeCancelResult result) {
        return new AgentCancelResult(result.cancelled());
    }

    private AgentDiffResult toDiffResult(OpencodeDiffResult result) {
        return new AgentDiffResult(result.files().stream().map(this::toDiffFile).toList());
    }

    private AgentDiffFile toDiffFile(OpencodeDiffFile file) {
        return new AgentDiffFile(file.path(), file.patch(), file.additions(), file.deletions(), file.status());
    }

    private AgentRejectDiffResult toRejectDiffResult(OpencodeRejectDiffResult result) {
        return new AgentRejectDiffResult(result.rejected());
    }

    private AgentRuntimeResult toRuntimeResult(OpencodeRuntimeResult result) {
        return new AgentRuntimeResult(result.body());
    }

    private AgentSessionMessagesResult toSessionMessagesResult(OpencodeSessionMessagesResult result) {
        return new AgentSessionMessagesResult(
                result.messages().stream().map(this::toSessionMessage).toList(),
                result.previousCursor(),
                result.nextCursor());
    }

    private AgentSessionMessage toSessionMessage(OpencodeSessionMessage message) {
        return new AgentSessionMessage(message.message(), message.parts());
    }

    private OpencodePromptPart toOpencodePromptPart(AgentPromptPart part) {
        return switch (part.type()) {
            case "text" -> OpencodePromptPart.text(part.text());
            case "file" -> OpencodePromptPart.file(part.url(), part.mime(), part.filename(), part.source());
            case "agent" -> OpencodePromptPart.agent(part.agentName(), part.source());
            default -> throw new IllegalArgumentException("Unsupported agent prompt part type: " + part.type());
        };
    }
}
