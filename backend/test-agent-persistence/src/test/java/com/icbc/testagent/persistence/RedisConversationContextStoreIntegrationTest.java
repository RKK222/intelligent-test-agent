package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.run.ConversationRunContext;
import com.icbc.testagent.domain.run.ConversationContextStore;
import com.icbc.testagent.domain.run.ConversationContextIssueLease;
import com.icbc.testagent.domain.run.ConversationContextSessionRevocation;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 真实 Redis/Lua 验证；CI 或本地提供 {@code -Dtest.redis.port=<port>} 时执行。
 */
class RedisConversationContextStoreIntegrationTest {

    private static final String TOKEN = "ctx_real-redis-secret";
    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

    @Test
    void invalidationGenerationPreventsPeekedContextFromBeingRevivedByTouch() {
        String configuredPort = System.getProperty("test.redis.port");
        Assumptions.assumeTrue(configuredPort != null && !configuredPort.isBlank());
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                "127.0.0.1",
                Integer.parseInt(configuredPort));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        RedisConversationContextStore store = new RedisConversationContextStore(
                redisTemplate,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.fixed(NOW, ZoneOffset.UTC));
        ConversationRunContext context = context();
        try {
            ConversationContextIssueLease initialLease = store.beginIssue(context.userId(), context.sessionId());
            assertThat(store.saveIfCurrent(TOKEN, context, initialLease)).isTrue();
            ConversationRunContext peeked = store.peek(TOKEN).orElseThrow();
            assertThat(peeked.processSnapshot()).isEqualTo(context.processSnapshot());
            assertThat(store.resolveForRouting(TOKEN, context.userId(), "opencode", context.sessionId()))
                    .contains(context);
            assertThat(redisTemplate.keys("test-agent:{conversation-context}:*"))
                    .allSatisfy(key -> assertThat(key).doesNotContain(TOKEN));

            store.invalidateWorkspace(context.workspaceId());

            assertThat(store.touch(TOKEN, peeked)).isEmpty();
            assertThat(store.peek(TOKEN)).isEmpty();

            ConversationContextIssueLease lateLease = store.beginIssue(context.userId(), context.sessionId());
            store.invalidateSession(context.sessionId());
            assertThat(store.saveIfCurrent(TOKEN, context, lateLease)).isFalse();
            assertThat(store.peek(TOKEN)).isEmpty();

            ConversationContextIssueLease freshLease = store.beginIssue(context.userId(), context.sessionId());
            assertThat(store.saveIfCurrent(TOKEN, context, freshLease)).isTrue();
            assertThat(store.touch(TOKEN, context)).isPresent();
            store.invalidateAll();
            assertThat(store.touch(TOKEN, context)).isEmpty();

            ConversationContextIssueLease beforeUserMutation =
                    store.beginIssue(context.userId(), context.sessionId());
            assertThat(store.saveIfCurrent(TOKEN, context, beforeUserMutation)).isTrue();
            var userMutation = store.beginUserMutation(context.userId());
            assertGateTtl(
                    redisTemplate,
                    "test-agent:{conversation-context}:mutating:user:" + context.userId().value());
            assertThat(store.resolveForRouting(TOKEN, context.userId(), "opencode", context.sessionId())).isEmpty();
            assertThatThrownBy(() -> store.beginIssue(context.userId(), context.sessionId()))
                    .isInstanceOfSatisfying(PlatformException.class, exception ->
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONVERSATION_CONTEXT_EXPIRED));
            store.completeUserMutation(userMutation);

            ConversationContextIssueLease duringWorkspaceMutation =
                    store.beginIssue(context.userId(), context.sessionId());
            var workspaceMutation = store.beginWorkspaceMutation(context.workspaceId());
            assertGateTtl(
                    redisTemplate,
                    "test-agent:{conversation-context}:mutating:workspace:" + context.workspaceId().value());
            assertThat(store.saveIfCurrent(TOKEN, context, duringWorkspaceMutation)).isFalse();
            store.completeWorkspaceMutation(workspaceMutation);

            var abortedWorkspaceMutation = store.beginWorkspaceMutation(context.workspaceId());
            store.abortWorkspaceMutation(abortedWorkspaceMutation);
            ConversationContextIssueLease afterWorkspaceAbort =
                    store.beginIssue(context.userId(), context.sessionId());
            assertThat(store.saveIfCurrent(TOKEN, context, afterWorkspaceAbort)).isTrue();

            ConversationContextIssueLease beforeRevoke = store.beginIssue(context.userId(), context.sessionId());
            CountDownLatch revokeStart = new CountDownLatch(1);
            CompletableFuture<ConversationContextSessionRevocation> firstRevoke = CompletableFuture.supplyAsync(() -> {
                await(revokeStart);
                return store.revokeSession(context.sessionId());
            });
            CompletableFuture<ConversationContextSessionRevocation> secondRevoke = CompletableFuture.supplyAsync(() -> {
                await(revokeStart);
                return store.revokeSession(context.sessionId());
            });
            revokeStart.countDown();
            ConversationContextSessionRevocation first = firstRevoke.join();
            ConversationContextSessionRevocation concurrent = secondRevoke.join();
            assertGateTtl(
                    redisTemplate,
                    "test-agent:{conversation-context}:revoked:session:" + context.sessionId().value());
            assertThat(store.saveIfCurrent(TOKEN, context, beforeRevoke)).isFalse();
            assertThatThrownBy(() -> store.beginIssue(context.userId(), context.sessionId()))
                    .isInstanceOfSatisfying(PlatformException.class, exception ->
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONVERSATION_CONTEXT_EXPIRED));

            store.restoreSessionRevocation(first);
            assertThatThrownBy(() -> store.beginIssue(context.userId(), context.sessionId()))
                    .isInstanceOf(PlatformException.class);
            store.restoreSessionRevocation(concurrent);

            ConversationContextIssueLease afterRollback = store.beginIssue(context.userId(), context.sessionId());
            assertThat(store.saveIfCurrent(TOKEN, context, afterRollback)).isTrue();
            store.invalidateProcess(context.processId());
            assertThat(store.peek(TOKEN)).isEmpty();
        } finally {
            Set<String> keys = redisTemplate.keys("test-agent:{conversation-context}:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            connectionFactory.destroy();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while coordinating Redis revoke test", exception);
        }
    }

    private static void assertGateTtl(StringRedisTemplate redisTemplate, String gateKey) {
        Long ttlMillis = redisTemplate.getExpire(gateKey, TimeUnit.MILLISECONDS);
        assertThat(ttlMillis)
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(ConversationContextStore.CONTEXT_TTL.toMillis());
    }

    private static ConversationRunContext context() {
        UserId userId = new UserId("usr_1234567890abcdef");
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
                node.executionNodeId(),
                false,
                com.icbc.testagent.domain.session.ConversationSourceType.MANUAL,
                null,
                userId);
        AgentSessionBinding binding = new AgentSessionBinding(
                session.sessionId(),
                "opencode",
                "remote-session-1",
                node.executionNodeId(),
                NOW,
                NOW,
                "trace_test");
        OpencodeServerProcess process = new OpencodeServerProcess(
                new OpencodeProcessId("ocp_1234567890abcdef"),
                userId,
                new LinuxServerId("server-a"),
                new OpencodeContainerId("ctr_1234567890abcdef"),
                4096,
                43210L,
                "http://10.0.0.1:4096",
                OpencodeServerProcessStatus.RUNNING,
                "/srv/opencode/sessions/4096",
                "/srv/opencode/config",
                NOW.minusSeconds(120),
                NOW.minusSeconds(5),
                "healthy",
                NOW.minusSeconds(120),
                NOW,
                "trace_test");
        return new ConversationRunContext(
                userId,
                "opencode",
                "ocp_1234567890abcdef",
                "server-a",
                process,
                session,
                workspace,
                node,
                binding,
                1,
                NOW.plusSeconds(3600));
    }
}
