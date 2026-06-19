package com.example.testagent.opencode.runtime.run;

import com.example.testagent.domain.event.RunEventType;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeRepository;
import com.example.testagent.domain.run.Run;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.run.RunRepository;
import com.example.testagent.domain.session.Session;
import com.example.testagent.domain.session.SessionRepository;
import com.example.testagent.event.RunEventSsePayload;
import com.example.testagent.opencode.client.OpencodeClientFacade;
import com.example.testagent.opencode.client.OpencodeSessionMessage;
import com.example.testagent.opencode.client.OpencodeSessionMessagesCommand;
import com.example.testagent.opencode.client.OpencodeSessionMessagesResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * SSE 建连时从 opencode projected messages 恢复消息内容；平台本地不再用 run_events 补存消息正文。
 */
@Service
public class RunMessageRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunMessageRecoveryService.class);
    private static final int RECOVERY_MESSAGE_LIMIT = 100;
    private static final String RECOVERY_ORDER = "asc";

    private final RunRepository runRepository;
    private final SessionRepository sessionRepository;
    private final ExecutionNodeRepository executionNodeRepository;
    private final OpencodeClientFacade opencodeClientFacade;

    public RunMessageRecoveryService(
            RunRepository runRepository,
            SessionRepository sessionRepository,
            ExecutionNodeRepository executionNodeRepository,
            OpencodeClientFacade opencodeClientFacade) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.executionNodeRepository = Objects.requireNonNull(executionNodeRepository, "executionNodeRepository must not be null");
        this.opencodeClientFacade = Objects.requireNonNull(opencodeClientFacade, "opencodeClientFacade must not be null");
    }

    public Flux<RunEventSsePayload> recover(RunId runId, String traceId) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        return Mono.fromCallable(() -> recoverSync(runId, traceId))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    LOGGER.warn("Failed to recover run messages from opencode, runId={}, traceId={}",
                            runId.value(), traceId, error);
                    return Mono.just(List.of());
                })
                .flatMapMany(Flux::fromIterable);
    }

    private List<RunEventSsePayload> recoverSync(RunId runId, String traceId) {
        Run run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            return List.of();
        }
        Session session = sessionRepository.findById(run.sessionId()).orElse(null);
        if (session == null || !session.hasOpencodeSessionMapping()) {
            return List.of();
        }
        ExecutionNode node = executionNodeRepository.findById(session.opencodeExecutionNodeId()).orElse(null);
        if (node == null) {
            return List.of();
        }
        OpencodeSessionMessagesResult result = opencodeClientFacade.sessionMessages(new OpencodeSessionMessagesCommand(
                        node,
                        session.opencodeSessionId(),
                        RECOVERY_MESSAGE_LIMIT,
                        RECOVERY_ORDER,
                        null,
                        traceId))
                .block();
        return result == null ? List.of() : toSnapshotEvents(runId, traceId, result.messages());
    }

    private List<RunEventSsePayload> toSnapshotEvents(
            RunId runId,
            String traceId,
            List<OpencodeSessionMessage> messages) {
        Instant occurredAt = Instant.now();
        List<RunEventSsePayload> events = new ArrayList<>();
        for (OpencodeSessionMessage message : messages) {
            Map<String, Object> messagePayload = normalizeMessage(message.message());
            String messageId = text(messagePayload.get("id"));
            events.add(transientPayload(
                    runId,
                    RunEventType.MESSAGE_UPDATED,
                    traceId,
                    occurredAt,
                    Map.of("message", messagePayload)));
            for (Map<String, Object> part : message.parts()) {
                Map<String, Object> partPayload = normalizePart(part, messageId);
                LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                String partMessageId = text(partPayload.get("messageID"));
                if (partMessageId != null) {
                    payload.put("messageID", partMessageId);
                    payload.put("messageId", partMessageId);
                }
                payload.put("part", partPayload);
                events.add(transientPayload(
                        runId,
                        RunEventType.MESSAGE_PART_UPDATED,
                        traceId,
                        occurredAt,
                        Map.copyOf(payload)));
            }
        }
        return List.copyOf(events);
    }

    private RunEventSsePayload transientPayload(
            RunId runId,
            RunEventType type,
            String traceId,
            Instant occurredAt,
            Map<String, Object> payload) {
        return new RunEventSsePayload(
                transientEventId(),
                runId.value(),
                0L,
                type.wireName(),
                traceId,
                occurredAt,
                payload);
    }

    private Map<String, Object> normalizeMessage(Map<String, Object> message) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(message);
        String messageId = text(normalized.get("id"));
        if (messageId != null) {
            normalized.putIfAbsent("messageID", messageId);
            normalized.putIfAbsent("messageId", messageId);
        }
        String type = text(normalized.get("type"));
        normalized.putIfAbsent("role", "user".equals(type) ? "user" : "assistant");
        return Map.copyOf(normalized);
    }

    private Map<String, Object> normalizePart(Map<String, Object> part, String messageId) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(part);
        if (messageId != null) {
            normalized.putIfAbsent("messageID", messageId);
            normalized.putIfAbsent("messageId", messageId);
        }
        String partId = text(normalized.get("id"));
        if (partId != null) {
            normalized.putIfAbsent("partID", partId);
            normalized.putIfAbsent("partId", partId);
        }
        return Map.copyOf(normalized);
    }

    private String transientEventId() {
        return "evt_live_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String text(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }
}
