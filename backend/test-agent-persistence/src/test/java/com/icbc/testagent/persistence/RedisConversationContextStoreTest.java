package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.run.ConversationContextStore;
import com.icbc.testagent.domain.run.ConversationContextIssueLease;
import com.icbc.testagent.domain.run.ConversationRunContext;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisConversationContextStoreTest {

    private static final String TOKEN = "ctx_secret-token-that-must-never-be-stored-in-plain-text";
    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private RedisConversationContextStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new RedisConversationContextStore(
                redisTemplate,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void beginIssueAtomicallyCapturesIssuanceAndSessionGenerations() {
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn("{\"issueGeneration\":7,\"userSessionGeneration\":3,\"userGeneration\":4,"
                        + "\"sessionGeneration\":5,\"contextGeneration\":6}");

        ConversationContextIssueLease lease = store.beginIssue(
                new UserId("usr_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"));

        assertThat(lease.issueGeneration()).isEqualTo(7);
        assertThat(lease.userSessionGeneration()).isEqualTo(3);
        assertThat(lease.userGeneration()).isEqualTo(4);
        assertThat(lease.sessionGeneration()).isEqualTo(5);
        assertThat(lease.contextGeneration()).isEqualTo(6);
        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(script.capture(), keys.capture());
        assertThat(keys.getValue()).hasSize(7).allSatisfy(key ->
                assertThat(String.valueOf(key)).contains("{conversation-context}"));
        assertThat(script.getValue().getScriptAsString())
                .contains("issueGeneration", "userGeneration", "sessionGeneration", "contextGeneration", "SCARD");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void saveIfCurrentUsesOneAtomicCasWithHashedSameSlotKeysAndFiveReverseIndexes() {
        when(redisTemplate.execute(
                        any(RedisScript.class),
                        anyList(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn("stored");
        ConversationContextIssueLease lease = new ConversationContextIssueLease(
                new UserId("usr_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                7,
                3,
                5);

        boolean saved = store.saveIfCurrent(
                TOKEN,
                context(NOW.plus(ConversationContextStore.CONTEXT_TTL)),
                lease);

        assertThat(saved).isTrue();
        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object> ttl = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate).execute(
                script.capture(),
                keys.capture(),
                anyString(),
                ttl.capture(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString());
        assertThat(keys.getValue()).hasSize(16).allSatisfy(key ->
                assertThat(String.valueOf(key)).contains("{conversation-context}"));
        assertThat(keys.getValue().getFirst().toString()).doesNotContain(TOKEN);
        assertThat(script.getValue().getScriptAsString())
                .contains(
                        "userSessionGeneration",
                        "userGeneration",
                        "sessionGeneration",
                        "workspaceGeneration",
                        "processGeneration",
                        "contextGeneration",
                        "issueGenerationMatches",
                        "SCARD",
                        "ZREMRANGEBYSCORE",
                        "ZADD",
                        "PX");
        assertThat(ttl.getValue()).isEqualTo(String.valueOf(Duration.ofHours(24).toMillis()));
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void saveIfCurrentRejectsAContextWhoseIssuanceFenceChanged() {
        when(redisTemplate.execute(
                        any(RedisScript.class),
                        anyList(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(null);
        ConversationContextIssueLease lease = new ConversationContextIssueLease(
                new UserId("usr_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                7,
                3,
                5);

        assertThat(store.saveIfCurrent(TOKEN, context(NOW.plusSeconds(60)), lease)).isFalse();
    }

    @Test
    void peekReadsWithoutRenewingAnyTtl() throws Exception {
        ConversationRunContext stored = context(NOW.plusSeconds(60));
        when(valueOperations.get(anyString())).thenReturn(storedEnvelope(stored));

        Optional<ConversationRunContext> loaded = store.peek(TOKEN);

        assertThat(loaded).contains(stored);
        verify(valueOperations).get(anyString());
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void touchAtomicallyChecksGenerationBeforeSlidingExpiry() throws Exception {
        ConversationRunContext expected = context(NOW.plusSeconds(60));
        ConversationRunContext refreshed = context(NOW.plus(ConversationContextStore.CONTEXT_TTL));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(storedEnvelope(refreshed));

        Optional<ConversationRunContext> loaded = store.touch(TOKEN, expected);

        assertThat(loaded).contains(refreshed);
        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(script.capture(), keys.capture(), any(), any(), any(), any());
        assertThat(keys.getValue()).hasSize(15);
        assertThat(script.getValue().getScriptAsString())
                .contains("generationMatches", "return nil", "expiresAt");
    }

    @Test
    void generationMismatchCannotReviveInvalidatedToken() {
        ConversationRunContext expected = context(NOW.plusSeconds(60));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(null);

        assertThat(store.touch(TOKEN, expected)).isEmpty();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void invalidationSupportsUserSessionUserSessionWorkspaceAndProcessGenerations() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        store.invalidate(new UserId("usr_1234567890abcdef"), new SessionId("ses_1234567890abcdef"));
        store.invalidateUser(new UserId("usr_1234567890abcdef"));
        store.invalidateSession(new SessionId("ses_1234567890abcdef"));
        store.invalidateWorkspace(new WorkspaceId("wrk_1234567890abcdef"));
        store.invalidateProcess("ocp_1234567890abcdef");

        ArgumentCaptor<RedisScript> scripts = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, org.mockito.Mockito.times(5)).execute(scripts.capture(), keys.capture(), any());
        assertThat(keys.getAllValues()).allSatisfy(pair -> {
            assertThat(pair).hasSize(3);
            assertThat(String.valueOf(pair.get(1))).contains(":generation:");
            assertThat(String.valueOf(pair.get(2))).contains(":generation:issue");
        });
        assertThat(scripts.getAllValues()).allSatisfy(script ->
                assertThat(script.getScriptAsString()).contains("INCR", "ZRANGE", "ZREMRANGEBYSCORE", "DEL"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void routeResolveChecksCurrentGenerationsWithoutRefreshingTtl() throws Exception {
        ConversationRunContext context = context(NOW.plusSeconds(60));
        when(valueOperations.get(anyString())).thenReturn(storedEnvelope(context));
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(storedEnvelope(context));

        Optional<ConversationRunContext> resolved = store.resolveForRouting(
                TOKEN,
                context.userId(),
                context.agentId(),
                context.sessionId());

        assertThat(resolved).contains(context);
        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(script.capture(), keys.capture());
        assertThat(keys.getValue()).hasSize(15);
        assertThat(script.getValue().getScriptAsString()).contains("generationsMatch", "SCARD");
        assertThat(script.getValue().getScriptAsString()).doesNotContain("PEXPIRE", "SET");
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void revokeAndRestoreUsePerArchiveTokenWhileGlobalInvalidationIsGenerationOnly() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(0L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(1L);
        SessionId sessionId = new SessionId("ses_1234567890abcdef");

        var revocation = store.revokeSession(sessionId);
        store.restoreSessionRevocation(revocation);
        store.invalidateAll();

        ArgumentCaptor<RedisScript> scripts = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scripts.capture(), anyList(), any(), any(), any());
        assertThat(scripts.getValue().getScriptAsString()).contains("SADD", "PEXPIRE", "ZRANGE");
        verify(redisTemplate).execute(scripts.capture(), anyList(), any());
        assertThat(scripts.getValue().getScriptAsString()).contains("SREM");
        verify(redisTemplate).execute(any(RedisScript.class), anyList());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void userAndWorkspaceMutationsKeepGateUntilAtomicCompletionOrExplicitAbort() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(0L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        var userMutation = store.beginUserMutation(new UserId("usr_1234567890abcdef"));
        store.completeUserMutation(userMutation);
        var abortedUserMutation = store.beginUserMutation(new UserId("usr_1234567890abcdef"));
        store.abortUserMutation(abortedUserMutation);
        var workspaceMutation = store.beginWorkspaceMutation(new WorkspaceId("wrk_1234567890abcdef"));
        store.completeWorkspaceMutation(workspaceMutation);
        var abortedWorkspaceMutation = store.beginWorkspaceMutation(new WorkspaceId("wrk_1234567890abcdef"));
        store.abortWorkspaceMutation(abortedWorkspaceMutation);

        ArgumentCaptor<RedisScript> mutationScripts = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate, org.mockito.Mockito.times(4))
                .execute(mutationScripts.capture(), anyList(), any(), any(), any());
        verify(redisTemplate, org.mockito.Mockito.times(2))
                .execute(mutationScripts.capture(), anyList(), any(), any());
        assertThat(mutationScripts.getAllValues())
                .anySatisfy(script -> assertThat(script.getScriptAsString())
                        .contains("SADD", "PEXPIRE", "ZRANGE", "DEL"))
                .anySatisfy(script -> assertThat(script.getScriptAsString()).contains("SREM", "ZRANGE", "DEL"));
        verify(redisTemplate, org.mockito.Mockito.times(2))
                .execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void redisFailureMapsToRuntimeStateUnavailableWithoutFallback() {
        when(valueOperations.get(anyString()))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));

        assertThatThrownBy(() -> store.peek(TOKEN))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RUNTIME_STATE_UNAVAILABLE));
    }

    private String storedEnvelope(ConversationRunContext context) throws Exception {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("context", context);
        value.put(
                "userSessionIndexKey",
                "test-agent:{conversation-context}:index:user-session:usr_1234567890abcdef:ses_1234567890abcdef");
        value.put(
                "userIndexKey",
                "test-agent:{conversation-context}:index:user:usr_1234567890abcdef");
        value.put(
                "sessionIndexKey",
                "test-agent:{conversation-context}:index:session:ses_1234567890abcdef");
        value.put(
                "workspaceIndexKey",
                "test-agent:{conversation-context}:index:workspace:wrk_1234567890abcdef");
        value.put(
                "processIndexKey",
                "test-agent:{conversation-context}:index:process:ocp_1234567890abcdef");
        value.put("userSessionGeneration", 0);
        value.put("userGeneration", 0);
        value.put("sessionGeneration", 0);
        value.put("workspaceGeneration", 0);
        value.put("processGeneration", 0);
        value.put("contextGeneration", 0);
        return objectMapper.writeValueAsString(value);
    }

    private static ConversationRunContext context(Instant expiresAt) {
        Workspace workspace = new Workspace(
                new WorkspaceId("wrk_1234567890abcdef"),
                "demo",
                "/srv/workspaces/demo",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "server-a",
                "trace_test");
        ExecutionNode node = new ExecutionNode(
                new ExecutionNodeId("node_ocp_1234567890abcdef"),
                "http://10.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                NOW,
                Set.of("opencode"),
                NOW,
                NOW,
                "trace_test");
        Session session = new Session(
                new SessionId("ses_1234567890abcdef"),
                workspace.workspaceId(),
                "session",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_test",
                "remote-session-1",
                node.executionNodeId());
        AgentSessionBinding binding = new AgentSessionBinding(
                session.sessionId(),
                "opencode",
                "remote-session-1",
                node.executionNodeId(),
                NOW,
                NOW,
                "trace_test");
        return new ConversationRunContext(
                new UserId("usr_1234567890abcdef"),
                "opencode",
                "ocp_1234567890abcdef",
                "server-a",
                session,
                workspace,
                node,
                binding,
                1,
                expiresAt);
    }
}
