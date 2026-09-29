package com.enterprise.testagent.agent.runtime;

import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.opencode.client.OpencodeCancelCommand;
import com.enterprise.testagent.opencode.client.OpencodeCancelResult;
import com.enterprise.testagent.opencode.client.OpencodeClientFacade;
import com.enterprise.testagent.opencode.client.OpencodeCreateSessionCommand;
import com.enterprise.testagent.opencode.client.OpencodeCreateSessionResult;
import com.enterprise.testagent.opencode.client.OpencodeDiffCommand;
import com.enterprise.testagent.opencode.client.OpencodeDiffFile;
import com.enterprise.testagent.opencode.client.OpencodeDiffResult;
import com.enterprise.testagent.opencode.client.OpencodeMessageIdGenerator;
import com.enterprise.testagent.opencode.client.OpencodePromptPart;
import com.enterprise.testagent.opencode.client.OpencodeRejectDiffCommand;
import com.enterprise.testagent.opencode.client.OpencodeRejectDiffResult;
import com.enterprise.testagent.opencode.client.OpencodeRuntimeCommand;
import com.enterprise.testagent.opencode.client.OpencodeRuntimeResult;
import com.enterprise.testagent.opencode.client.OpencodeSessionExistsCommand;
import com.enterprise.testagent.opencode.client.OpencodeSessionMessage;
import com.enterprise.testagent.opencode.client.OpencodeSessionMessagesCommand;
import com.enterprise.testagent.opencode.client.OpencodeSessionMessagesResult;
import com.enterprise.testagent.opencode.client.OpencodeStartRunCommand;
import com.enterprise.testagent.opencode.client.OpencodeStartRunResult;
import com.enterprise.testagent.opencode.client.OpencodeStartCommand;
import com.enterprise.testagent.opencode.client.OpencodeStreamEventsCommand;
import com.enterprise.testagent.opencode.client.OpencodeUnrevertCommand;
import java.util.ArrayList;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                            commandParts(command),
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

    /**
     * OpenCode 2.0.18 的原生 command 请求没有 system 字段；用仅发送给 OpenCode 的内联文件 part
     * 承载本次平台 system 上下文，保持用户参数与平台持久化消息原文不变。
     */
    private List<OpencodePromptPart> commandParts(AgentStartRunCommand command) {
        List<OpencodePromptPart> parts = new ArrayList<>(
                command.parts().stream().map(this::toOpencodePromptPart).toList());
        if (command.system() != null) {
            parts.add(OpencodePromptPart.internalRunContext(command.system()));
        }
        return List.copyOf(parts);
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
        com.enterprise.testagent.opencode.client.OpencodeRunEventStream opened = opencodeClientFacade.openRunEventStream(
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

    @Override
    public Mono<AgentReplayableTurn> loadReplayableTurn(AgentReplayableTurnCommand command) {
        return opencodeClientFacade.sessionMessages(new OpencodeSessionMessagesCommand(
                        command.node(), command.remoteSessionId(), 200, "desc", null, command.traceId()))
                .map(result -> {
                    OpencodeSessionMessage lastUserMessage = result.messages().stream()
                            .filter(message -> "user".equals(text(message.message(), "role")))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("session has no replayable user message"));
                    if (!command.messageId().equals(text(lastUserMessage.message(), "id"))) {
                        throw new IllegalStateException("target is not the last user message");
                    }
                    return toReplayableTurn(lastUserMessage);
                });
    }

    @Override
    public Mono<AgentRevertTurnResult> revertTurn(AgentRevertTurnCommand command) {
        return opencodeClientFacade.rejectDiff(new OpencodeRejectDiffCommand(
                        command.node(), command.remoteSessionId(), command.directory(), command.workspace(),
                        command.messageId(), null, command.traceId()))
                .map(result -> new AgentRevertTurnResult(result.rejected()));
    }

    @Override
    public Mono<AgentUnrevertTurnResult> unrevertTurn(AgentUnrevertTurnCommand command) {
        return opencodeClientFacade.unrevert(new OpencodeUnrevertCommand(
                        command.node(), command.remoteSessionId(), command.directory(), command.workspace(),
                        command.traceId()))
                .map(result -> new AgentUnrevertTurnResult(result.unreverted()));
    }

    @Override
    public Mono<AgentMessageProbeResult> probeMessage(AgentMessageProbeCommand command) {
        return opencodeClientFacade.sessionMessages(new OpencodeSessionMessagesCommand(
                        command.node(), command.remoteSessionId(), 200, "desc", null, command.traceId()))
                .map(result -> new AgentMessageProbeResult(result.messages().stream()
                        .anyMatch(message -> command.messageId().equals(text(message.message(), "id")))));
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
            // text source 仅供平台在 dispatch 前识别降级附件，OpenCode TextPartInput 不支持该字段。
            case "text" -> OpencodePromptPart.text(part.text());
            case "file" -> OpencodePromptPart.file(part.url(), part.mime(), part.filename(), part.source());
            case "agent" -> OpencodePromptPart.agent(part.agentName(), part.source());
            case "subtask" -> OpencodePromptPart.subtask(
                    part.text(), part.filename(), part.agentName(), part.source());
            default -> throw new IllegalArgumentException("Unsupported agent prompt part type: " + part.type());
        };
    }

    private AgentReplayableTurn toReplayableTurn(OpencodeSessionMessage source) {
        if (!"user".equals(text(source.message(), "role"))) {
            throw new IllegalStateException("only user messages can be replayed");
        }
        List<AgentPromptPart> parts = source.parts().stream().map(this::toAgentPromptPart).toList();
        String prompt = parts.stream()
                .filter(part -> "text".equals(part.type()) && part.text() != null && !part.text().isBlank())
                .map(AgentPromptPart::text)
                .findFirst()
                .orElseGet(() -> parts.stream()
                        .filter(part -> "subtask".equals(part.type()))
                        .map(AgentPromptPart::text)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("user message has no replayable prompt")));
        Map<String, Object> model = map(source.message().get("model"));
        return new AgentReplayableTurn(
                text(source.message(), "id"),
                prompt,
                parts,
                text(source.message(), "agent"),
                text(model, "providerID"),
                text(model, "modelID"),
                text(source.message(), "variant"));
    }

    private AgentPromptPart toAgentPromptPart(Map<String, Object> part) {
        String type = text(part, "type");
        return switch (type) {
            case "text" -> AgentPromptPart.text(text(part, "text"));
            case "file" -> AgentPromptPart.file(
                    text(part, "url"), text(part, "mime"), text(part, "filename"), map(part.get("source")));
            case "agent" -> AgentPromptPart.agent(text(part, "name"), map(part.get("source")));
            case "subtask" -> AgentPromptPart.subtask(
                    text(part, "prompt"), text(part, "description"), text(part, "agent"), subtaskMetadata(part));
            default -> throw new IllegalStateException("unsupported replayable prompt part type: " + type);
        };
    }

    private Map<String, Object> subtaskMetadata(Map<String, Object> part) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (part.get("model") instanceof Map<?, ?> model) result.put("model", Map.copyOf(model));
        if (part.get("command") != null) result.put("command", part.get("command"));
        return Map.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }
}
