package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventScopeContext;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.event.RunSessionScope;
import com.icbc.testagent.domain.event.RunSessionScopeRepository;
import com.icbc.testagent.domain.event.RunSessionScopeSession;
import com.icbc.testagent.domain.run.RunId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunSessionScopeRouterTest {

    private static final RunId RUN_ID = new RunId("run_scope1234567890abcdef");
    private static final String ROOT_SESSION_ID = "ses_root1234567890abcdef";
    private static final String CHILD_SESSION_ID = "ses_child1234567890abcdef";
    private static final Instant NOW = Instant.parse("2026-07-03T02:00:00Z");

    private FakeRunSessionScopeRepository repository;
    private FakeRunSessionScopeRuntimeCache cache;
    private RunSessionScopeRouter router;

    @BeforeEach
    void setUp() {
        repository = new FakeRunSessionScopeRepository();
        cache = new FakeRunSessionScopeRuntimeCache();
        router = new RunSessionScopeRouter(repository, cache);
    }

    @Test
    void rootIdleKeepsRunSucceededTerminal() {
        List<RunEventDraft> routed = route(
                sessionDraft(RunEventType.SESSION_STATUS, ROOT_SESSION_ID, null, Map.of("type", "idle")),
                sessionDraft(RunEventType.RUN_SUCCEEDED, ROOT_SESSION_ID, null, Map.of("type", "idle")));

        assertThat(routed).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.SESSION_STATUS, RunEventType.RUN_SUCCEEDED);
        assertThat(routed).allSatisfy(draft -> assertThat(draft.scopeContext().childSession()).isFalse());
    }

    @Test
    void childIdleOnlyKeepsSessionStatusAndDiscoversChild() {
        List<RunEventDraft> routed = route(
                sessionDraft(RunEventType.SESSION_STATUS, CHILD_SESSION_ID, ROOT_SESSION_ID, Map.of("type", "idle")),
                sessionDraft(RunEventType.RUN_SUCCEEDED, CHILD_SESSION_ID, ROOT_SESSION_ID, Map.of("type", "idle")));

        assertThat(routed).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.SESSION_CHILD_DISCOVERED, RunEventType.SESSION_STATUS);
        RunEventDraft childStatus = routed.get(1);
        assertThat(childStatus.scopeContext().sessionId()).isEqualTo(CHILD_SESSION_ID);
        assertThat(childStatus.scopeContext().parentSessionId()).isEqualTo(ROOT_SESSION_ID);
        assertThat(childStatus.scopeContext().childSession()).isTrue();
        assertThat(repository.findSession(RUN_ID, CHILD_SESSION_ID))
                .get()
                .extracting(RunSessionScopeSession::discoverySource)
                .isEqualTo("SESSION_EVENT");
    }

    @Test
    void childErrorDoesNotFailRun() {
        List<RunEventDraft> routed = route(
                sessionDraft(RunEventType.SESSION_ERROR, CHILD_SESSION_ID, ROOT_SESSION_ID, null),
                sessionDraft(RunEventType.RUN_FAILED, CHILD_SESSION_ID, ROOT_SESSION_ID, null));

        assertThat(routed).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.SESSION_CHILD_DISCOVERED, RunEventType.SESSION_ERROR);
        assertThat(routed).noneMatch(draft -> draft.type() == RunEventType.RUN_FAILED);
    }

    @Test
    void taskPartMetadataDiscoversChildAndBindsTaskFields() {
        RunEventDraft draft = new RunEventDraft(
                RUN_ID,
                RunEventType.MESSAGE_PART_UPDATED,
                "trace_scope1234567890abcdef",
                NOW,
                payload(Map.of(
                        "rawType", "message.part.updated",
                        "messageID", "msg_task",
                        "partID", "part_task",
                        "part", Map.of(
                                "id", "part_task",
                                "messageID", "msg_task",
                                "callID", "call_task",
                                "metadata", Map.of("sessionID", CHILD_SESSION_ID)))),
                rootScope());

        List<RunEventDraft> routed = route(draft);

        assertThat(routed).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.SESSION_CHILD_DISCOVERED, RunEventType.MESSAGE_PART_UPDATED);
        RunSessionScopeSession child = repository.findSession(RUN_ID, CHILD_SESSION_ID).orElseThrow();
        assertThat(child.discoverySource()).isEqualTo("TASK_PART");
        assertThat(child.taskMessageId()).isEqualTo("msg_task");
        assertThat(child.taskPartId()).isEqualTo("part_task");
        assertThat(child.taskCallId()).isEqualTo("call_task");
    }

    @Test
    void sessionUpdatedParentIdDiscoversChild() {
        List<RunEventDraft> routed = route(new RunEventDraft(
                RUN_ID,
                RunEventType.SESSION_UPDATED,
                "trace_scope1234567890abcdef",
                NOW,
                payload(Map.of("rawType", "session.updated", "sessionID", CHILD_SESSION_ID, "parentID", ROOT_SESSION_ID)),
                rootScope()));

        assertThat(routed).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.SESSION_CHILD_DISCOVERED, RunEventType.SESSION_UPDATED);
        assertThat(repository.findSession(RUN_ID, CHILD_SESSION_ID))
                .get()
                .extracting(RunSessionScopeSession::discoverySource)
                .isEqualTo("SESSION_EVENT");
    }

    @Test
    void sessionChildrenBootstrapDiscoversRootChildCandidate() {
        List<RunEventDraft> routed = route(new RunEventDraft(
                RUN_ID,
                RunEventType.OPENCODE_EVENT_UNKNOWN,
                "trace_scope1234567890abcdef",
                NOW,
                payload(Map.of(
                        "rawType", "session.children",
                        "sessionID", ROOT_SESSION_ID,
                        "children", List.of(Map.of("id", CHILD_SESSION_ID, "parentID", ROOT_SESSION_ID)))),
                rootScope()));

        assertThat(routed).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.SESSION_CHILD_DISCOVERED, RunEventType.OPENCODE_EVENT_UNKNOWN);
        assertThat(repository.findSession(RUN_ID, CHILD_SESSION_ID))
                .get()
                .extracting(RunSessionScopeSession::discoverySource)
                .isEqualTo("BOOTSTRAP");
    }

    @Test
    void unknownNestedSessionEventIsNotRoutedAsRoot() {
        List<RunEventDraft> routed = route(new RunEventDraft(
                RUN_ID,
                RunEventType.MESSAGE_UPDATED,
                "trace_scope1234567890abcdef",
                NOW,
                payload(Map.of(
                        "rawType", "message.updated",
                        "rawPayload", Map.of(
                                "type", "message.updated",
                                "properties", Map.of("info", Map.of("sessionID", "ses_other1234567890abcdef"))),
                        "info", Map.of("sessionID", "ses_other1234567890abcdef"))),
                rootScope()));

        assertThat(routed).isEmpty();
    }

    @Test
    void unknownChildEventIsPendingAndDrainedAfterDiscovery() {
        RunEventDraft earlyChild = new RunEventDraft(
                RUN_ID,
                RunEventType.SESSION_STATUS,
                "trace_scope1234567890abcdef",
                NOW.minusSeconds(1),
                payload(Map.of(
                        "rawType", "session.status",
                        "rawEventId", "evt_child_status",
                        "sessionID", CHILD_SESSION_ID,
                        "status", Map.of("type", "busy"))),
                rootScope());

        assertThat(route(earlyChild)).isEmpty();
        assertThat(cache.pending.get(CHILD_SESSION_ID)).hasSize(1);

        List<RunEventDraft> routed = route(new RunEventDraft(
                RUN_ID,
                RunEventType.SESSION_UPDATED,
                "trace_scope1234567890abcdef",
                NOW,
                payload(Map.of("rawType", "session.updated", "sessionID", CHILD_SESSION_ID, "parentID", ROOT_SESSION_ID)),
                rootScope()));

        assertThat(routed).extracting(RunEventDraft::type)
                .containsExactly(
                        RunEventType.SESSION_CHILD_DISCOVERED,
                        RunEventType.SESSION_STATUS,
                        RunEventType.SESSION_UPDATED);
        assertThat(routed.get(1).occurredAt()).isEqualTo(NOW.minusSeconds(1));
        assertThat(cache.pending).doesNotContainKey(CHILD_SESSION_ID);
    }

    @Test
    void duplicateRawEventIdIsDroppedWhenRedisDedupClaimsItAlreadyExists() {
        RunEventDraft first = sessionDraft(RunEventType.SESSION_STATUS, ROOT_SESSION_ID, null, Map.of("type", "busy"));
        first = new RunEventDraft(first.runId(), first.type(), first.traceId(), first.occurredAt(),
                withRawEventId(first.payload(), "evt_dup"), first.scopeContext());
        RunEventDraft second = new RunEventDraft(first.runId(), first.type(), first.traceId(), first.occurredAt(),
                withRawEventId(first.payload(), "evt_dup"), first.scopeContext());

        assertThat(route(first)).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.SESSION_STATUS);
        assertThat(route(second)).isEmpty();
    }

    private List<RunEventDraft> route(RunEventDraft... drafts) {
        List<RunEventDraft> routed = new ArrayList<>();
        for (RunEventDraft draft : drafts) {
            routed.addAll(router.route(rootScope(), draft));
        }
        return routed;
    }

    private RunEventDraft sessionDraft(
            RunEventType type,
            String sessionId,
            String parentSessionId,
            Map<String, Object> status) {
        LinkedHashMap<String, Object> payload = payload(Map.of(
                "rawType", type == RunEventType.SESSION_ERROR || type == RunEventType.RUN_FAILED
                        ? "session.error"
                        : "session.status",
                "sessionID", sessionId));
        if (parentSessionId != null) {
            payload.put("parentID", parentSessionId);
        }
        if (status != null) {
            payload.put("status", status);
        }
        return new RunEventDraft(
                RUN_ID,
                type,
                "trace_scope1234567890abcdef",
                NOW,
                payload,
                rootScope());
    }

    private RunEventScopeContext rootScope() {
        return RunEventScopeContext.root(RUN_ID, ROOT_SESSION_ID);
    }

    private LinkedHashMap<String, Object> payload(Map<String, Object> values) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(values);
        payload.putAll(rootScope().toPayloadMetadata());
        return payload;
    }

    private LinkedHashMap<String, Object> withRawEventId(Map<String, Object> payload, String rawEventId) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>(payload);
        copy.put("rawEventId", rawEventId);
        return copy;
    }

    private static final class FakeRunSessionScopeRepository implements RunSessionScopeRepository {

        private final Map<String, RunSessionScopeSession> sessions = new LinkedHashMap<>();

        @Override
        public void upsertScope(RunSessionScope scope) {
        }

        @Override
        public void upsertSession(RunSessionScopeSession session) {
            sessions.put(session.sessionId(), session);
        }

        @Override
        public List<RunSessionScopeSession> findSessionsByRunId(RunId runId) {
            return List.copyOf(sessions.values());
        }

        @Override
        public List<RunSessionScopeSession> findSessionsByRootSessionId(String rootSessionId) {
            return sessions.values().stream()
                    .filter(session -> session.rootSessionId().equals(rootSessionId))
                    .toList();
        }

        @Override
        public Optional<RunSessionScopeSession> findSession(RunId runId, String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }
    }

    private static final class FakeRunSessionScopeRuntimeCache extends RunSessionScopeRuntimeCache {

        private final Set<String> dedup = new HashSet<>();
        private final Map<String, List<RunEventDraft>> pending = new LinkedHashMap<>();

        private FakeRunSessionScopeRuntimeCache() {
            super(null, new ObjectMapper().findAndRegisterModules());
        }

        @Override
        boolean claimRawEvent(RunId runId, String sessionId, String rawEventId) {
            return dedup.add(runId.value() + ":" + sessionId + ":" + rawEventId);
        }

        @Override
        boolean appendPending(String sessionId, RunEventDraft draft) {
            pending.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add(draft);
            return true;
        }

        @Override
        List<RunEventDraft> drainPending(RunId runId, String sessionId) {
            List<RunEventDraft> drained = pending.remove(sessionId);
            return drained == null ? List.of() : drained;
        }
    }
}
