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
                .containsExactly(
                        RunEventType.SESSION_CHILD_DISCOVERED,
                        RunEventType.SESSION_SCOPE_UPDATED,
                        RunEventType.SESSION_STATUS);
        RunEventDraft childStatus = routed.get(2);
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
                .containsExactly(
                        RunEventType.SESSION_CHILD_DISCOVERED,
                        RunEventType.SESSION_SCOPE_UPDATED,
                        RunEventType.SESSION_ERROR);
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
                .containsExactly(
                        RunEventType.SESSION_CHILD_DISCOVERED,
                        RunEventType.SESSION_SCOPE_UPDATED,
                        RunEventType.MESSAGE_PART_UPDATED);
        RunSessionScopeSession child = repository.findSession(RUN_ID, CHILD_SESSION_ID).orElseThrow();
        assertThat(child.discoverySource()).isEqualTo("TASK_PART");
        assertThat(child.parentSessionId()).isEqualTo(ROOT_SESSION_ID);
        assertThat(child.taskMessageId()).isEqualTo("msg_task");
        assertThat(child.taskPartId()).isEqualTo("part_task");
        assertThat(child.taskCallId()).isEqualTo("call_task");
        assertThat(routed.get(0).payload()).containsEntry("parentSessionId", ROOT_SESSION_ID);
    }

    @Test
    void pendingRootTaskPartBindsNextCreatedChildSession() {
        RunEventDraft pendingTask = new RunEventDraft(
                RUN_ID,
                RunEventType.MESSAGE_PART_UPDATED,
                "trace_scope1234567890abcdef",
                NOW.minusMillis(10),
                payload(Map.of(
                        "rawType", "message.part.updated",
                        "sessionID", ROOT_SESSION_ID,
                        "messageID", "msg_task",
                        "part", Map.of(
                                "id", "part_task",
                                "messageID", "msg_task",
                                "sessionID", ROOT_SESSION_ID,
                                "type", "tool",
                                "tool", "task",
                                "callID", "call_task",
                                "state", Map.of("status", "pending", "input", Map.of(), "raw", "")))),
                rootScope());
        RunEventDraft childCreated = new RunEventDraft(
                RUN_ID,
                RunEventType.SESSION_CREATED,
                "trace_scope1234567890abcdef",
                NOW,
                payload(Map.of(
                        "rawType", "session.created",
                        "sessionID", CHILD_SESSION_ID,
                        "parentID", ROOT_SESSION_ID,
                        "info", Map.of(
                                "id", CHILD_SESSION_ID,
                                "parentID", ROOT_SESSION_ID,
                                "agent", "explore",
                                "title", "Explore project structure (@explore subagent)"))),
                rootScope());

        List<RunEventDraft> routed = route(pendingTask, childCreated);

        assertThat(routed).extracting(RunEventDraft::type)
                .containsExactly(
                        RunEventType.MESSAGE_PART_UPDATED,
                        RunEventType.SESSION_CHILD_DISCOVERED,
                        RunEventType.SESSION_SCOPE_UPDATED,
                        RunEventType.SESSION_CREATED);
        RunSessionScopeSession child = repository.findSession(RUN_ID, CHILD_SESSION_ID).orElseThrow();
        assertThat(child.taskMessageId()).isEqualTo("msg_task");
        assertThat(child.taskPartId()).isEqualTo("part_task");
        assertThat(child.taskCallId()).isEqualTo("call_task");
        assertThat(child.metadata()).containsEntry("agent", "explore")
                .containsEntry("title", "Explore project structure");
        assertThat(routed.get(1).payload()).containsEntry("taskMessageId", "msg_task")
                .containsEntry("taskPartId", "part_task")
                .containsEntry("taskCallId", "call_task")
                .containsEntry("agent", "explore")
                .containsEntry("title", "Explore project structure");
    }

    @Test
    void multiplePendingTaskPartsBindCreatedChildrenByFifo() {
        RunEventDraft firstTask = pendingTaskDraft("msg_first", "part_first", "call_first", NOW.minusMillis(30));
        RunEventDraft secondTask = pendingTaskDraft("msg_second", "part_second", "call_second", NOW.minusMillis(20));
        RunEventDraft firstChild = childSessionCreatedDraft("ses_child_first1234567890", "Explore backend (@explore subagent)");
        RunEventDraft secondChild = childSessionCreatedDraft("ses_child_second1234567890", "Explore frontend (@explore subagent)");

        route(firstTask, secondTask, firstChild, secondChild);

        assertThat(repository.findSession(RUN_ID, "ses_child_first1234567890")).get()
                .extracting(RunSessionScopeSession::taskPartId)
                .isEqualTo("part_first");
        assertThat(repository.findSession(RUN_ID, "ses_child_second1234567890")).get()
                .extracting(RunSessionScopeSession::taskPartId)
                .isEqualTo("part_second");
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
                .containsExactly(
                        RunEventType.SESSION_CHILD_DISCOVERED,
                        RunEventType.SESSION_SCOPE_UPDATED,
                        RunEventType.SESSION_UPDATED);
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
                .containsExactly(
                        RunEventType.SESSION_CHILD_DISCOVERED,
                        RunEventType.SESSION_SCOPE_UPDATED,
                        RunEventType.OPENCODE_EVENT_UNKNOWN);
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
                        RunEventType.SESSION_SCOPE_UPDATED,
                        RunEventType.SESSION_STATUS,
                        RunEventType.SESSION_UPDATED);
        assertThat(routed.get(2).occurredAt()).isEqualTo(NOW.minusSeconds(1));
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

    @Test
    void duplicateNativeTaskAndChildCreatedRawEventsDoNotCreateDuplicateDiscovery() {
        RunEventDraft task = pendingTaskDraft("msg_task", "prt_task", "call_task", NOW.minusMillis(10));
        task = new RunEventDraft(task.runId(), task.type(), task.traceId(), task.occurredAt(),
                withRawEventId(task.payload(), "evt_task"), task.scopeContext());
        RunEventDraft duplicateTask = new RunEventDraft(task.runId(), task.type(), task.traceId(), task.occurredAt(),
                withRawEventId(task.payload(), "evt_task"), task.scopeContext());
        RunEventDraft childCreated = childSessionCreatedDraft(CHILD_SESSION_ID, "Explore project (@explore subagent)");
        childCreated = new RunEventDraft(childCreated.runId(), childCreated.type(), childCreated.traceId(),
                childCreated.occurredAt(), withRawEventId(childCreated.payload(), "evt_child_created"),
                childCreated.scopeContext());
        RunEventDraft duplicateChildCreated = new RunEventDraft(childCreated.runId(), childCreated.type(),
                childCreated.traceId(), childCreated.occurredAt(),
                withRawEventId(childCreated.payload(), "evt_child_created"), childCreated.scopeContext());

        assertThat(route(task)).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.MESSAGE_PART_UPDATED);
        assertThat(route(duplicateTask)).isEmpty();

        assertThat(route(childCreated)).extracting(RunEventDraft::type)
                .containsExactly(
                        RunEventType.SESSION_CHILD_DISCOVERED,
                        RunEventType.SESSION_SCOPE_UPDATED,
                        RunEventType.SESSION_CREATED);
        assertThat(route(duplicateChildCreated)).isEmpty();
        assertThat(repository.sessions).hasSize(1);
        assertThat(repository.findSession(RUN_ID, CHILD_SESSION_ID)).get()
                .extracting(RunSessionScopeSession::taskPartId)
                .isEqualTo("prt_task");
    }

    @Test
    void knownChildPermissionQuestionAndSessionDiffStayInChildScope() {
        route(new RunEventDraft(
                RUN_ID,
                RunEventType.SESSION_UPDATED,
                "trace_scope1234567890abcdef",
                NOW,
                payload(Map.of("rawType", "session.updated", "sessionID", CHILD_SESSION_ID, "parentID", ROOT_SESSION_ID)),
                rootScope()));

        List<RunEventDraft> routed = route(
                childScopedDraft(RunEventType.PERMISSION_ASKED, "permission.v2.asked"),
                childScopedDraft(RunEventType.QUESTION_ASKED, "question.v2.asked"),
                childScopedDraft(RunEventType.SESSION_DIFF, "session.diff"));

        assertThat(routed).extracting(RunEventDraft::type)
                .containsExactly(
                        RunEventType.PERMISSION_ASKED,
                        RunEventType.QUESTION_ASKED,
                        RunEventType.SESSION_DIFF);
        assertThat(routed).allSatisfy(draft -> {
            assertThat(draft.scopeContext().sessionId()).isEqualTo(CHILD_SESSION_ID);
            assertThat(draft.scopeContext().childSession()).isTrue();
            assertThat(draft.payload()).containsEntry("rootSessionId", ROOT_SESSION_ID)
                    .containsEntry("sessionId", CHILD_SESSION_ID)
                    .containsEntry("isChildSession", true);
        });
    }

    @Test
    void dropsGlobalNonRunUnknownEvents() {
        for (String rawType : List.of(
                "heartbeat",
                "server.heartbeat",
                "tui.toast.show",
                "pty.output",
                "workspace.updated",
                "worktree.updated",
                "installation.updated",
                "plugin.updated",
                "catalog.updated")) {
            RunEventDraft draft = new RunEventDraft(
                    RUN_ID,
                    RunEventType.OPENCODE_EVENT_UNKNOWN,
                    "trace_scope1234567890abcdef",
                    NOW,
                    payload(Map.of("rawType", rawType)),
                    rootScope());

            assertThat(route(draft)).as(rawType).isEmpty();
        }
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

    private RunEventDraft childScopedDraft(RunEventType type, String rawType) {
        return new RunEventDraft(
                RUN_ID,
                type,
                "trace_scope1234567890abcdef",
                NOW,
                payload(Map.of("rawType", rawType, "sessionID", CHILD_SESSION_ID)),
                rootScope());
    }

    private RunEventDraft pendingTaskDraft(String messageId, String partId, String callId, Instant occurredAt) {
        return new RunEventDraft(
                RUN_ID,
                RunEventType.MESSAGE_PART_UPDATED,
                "trace_scope1234567890abcdef",
                occurredAt,
                payload(Map.of(
                        "rawType", "message.part.updated",
                        "sessionID", ROOT_SESSION_ID,
                        "messageID", messageId,
                        "part", Map.of(
                                "id", partId,
                                "messageID", messageId,
                                "sessionID", ROOT_SESSION_ID,
                                "type", "tool",
                                "tool", "task",
                                "callID", callId,
                                "state", Map.of("status", "pending", "input", Map.of(), "raw", "")))),
                rootScope());
    }

    private RunEventDraft childSessionCreatedDraft(String sessionId, String title) {
        return new RunEventDraft(
                RUN_ID,
                RunEventType.SESSION_CREATED,
                "trace_scope1234567890abcdef",
                NOW,
                payload(Map.of(
                        "rawType", "session.created",
                        "sessionID", sessionId,
                        "parentID", ROOT_SESSION_ID,
                        "info", Map.of(
                                "id", sessionId,
                                "parentID", ROOT_SESSION_ID,
                                "agent", "explore",
                                "title", title))),
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
