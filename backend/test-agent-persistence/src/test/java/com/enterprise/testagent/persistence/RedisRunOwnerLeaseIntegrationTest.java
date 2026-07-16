package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.event.RunSessionScope;
import com.enterprise.testagent.domain.event.RunSessionScopeSession;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunOwnerLease;
import com.enterprise.testagent.domain.run.RunRuntimeInput;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 使用真实 Redis 验证 owner lease、fencing token 与未派发 Run 清理。 */
class RedisRunOwnerLeaseIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-11T06:00:00Z");

    @Test
    void ownerLeaseAllowsOnlyCurrentOwnerAndIncrementsFenceAfterRelease() {
        Fixture fixture = fixture();
        try {
            RunRuntimeManifest manifest = manifest("run_owner_lease");
            fixture.store.initialize(manifest, input(manifest.runId()));

            RunOwnerLease first = fixture.store.claimOwnerLease(
                    manifest.runId(), manifest.backendProcessId()).orElseThrow();

            assertThat(first.fencingToken()).isEqualTo(1L);
            assertThat(first.expiresAt()).isEqualTo(NOW.plusSeconds(15));
            assertThat(fixture.store.claimOwnerLease(manifest.runId(), "bjp_owner_b")).isEmpty();
            assertThat(fixture.store.renewOwnerLease(first)).contains(first);
            assertThat(fixture.store.renewOwnerLease(new RunOwnerLease(
                    manifest.runId(), manifest.backendProcessId(), 99L, first.expiresAt()))).isEmpty();
            assertThat(fixture.store.releaseOwnerLease(new RunOwnerLease(
                    manifest.runId(), manifest.backendProcessId(), 99L, first.expiresAt()))).isFalse();
            assertThat(fixture.store.releaseOwnerLease(first)).isTrue();

            RunOwnerLease takeover = fixture.store.claimOwnerLease(manifest.runId(), "bjp_owner_b").orElseThrow();
            assertThat(takeover.fencingToken()).isEqualTo(2L);
        } finally {
            fixture.close();
        }
    }

    @Test
    void conditionalClaimRequiresUnchangedActiveManifestAndAlwaysAdvancesFence() {
        Fixture fixture = fixture();
        try {
            RunRuntimeManifest manifest = manifest("run_owner_conditional_claim");
            fixture.store.initialize(manifest, input(manifest.runId()));
            RunOwnerLease initial = fixture.store.claimOwnerLease(
                    manifest.runId(), manifest.backendProcessId()).orElseThrow();
            RunRuntimeManifest expected = fixture.store.findManifest(manifest.runId()).orElseThrow();

            RunOwnerLease replacement = fixture.store.claimOwnerLeaseIfUnchanged(
                    expected, manifest.backendProcessId()).orElseThrow();

            assertThat(replacement.fencingToken()).isEqualTo(initial.fencingToken() + 1L);
            assertThat(fixture.store.renewOwnerLease(initial)).isEmpty();
            assertThat(fixture.store.releaseOwnerLease(replacement)).isTrue();
            fixture.store.appendDurable(event(manifest.runId(), RunEventType.MESSAGE_UPDATED, "changed"));
            assertThat(fixture.store.claimOwnerLeaseIfUnchanged(expected, "bjp_owner_b")).isEmpty();

            RunRuntimeManifest current = fixture.store.findManifest(manifest.runId()).orElseThrow();
            RunOwnerLease takeover = fixture.store.claimOwnerLeaseIfUnchanged(current, "bjp_owner_b").orElseThrow();
            fixture.store.appendDurable(
                    event(manifest.runId(), RunEventType.RUN_SUCCEEDED, "terminal"), takeover);
            assertThat(fixture.store.releaseOwnerLease(takeover)).isTrue();
            assertThat(fixture.store.claimOwnerLease(manifest.runId(), "bjp_owner_c")).isEmpty();
            assertThat(fixture.store.claimOwnerLeaseIfUnchanged(
                    fixture.store.findManifest(manifest.runId()).orElseThrow(), "bjp_owner_c")).isEmpty();
        } finally {
            fixture.close();
        }
    }

    @Test
    void staleFenceCannotMutateEventsBindingScopeDedupOrPendingQueue() {
        Fixture fixture = fixture();
        try {
            RunRuntimeManifest manifest = manifest("run_owner_fenced_mutations");
            fixture.store.initialize(manifest, input(manifest.runId()));
            RunOwnerLease stale = fixture.store.claimOwnerLease(
                    manifest.runId(), manifest.backendProcessId()).orElseThrow();
            RunOwnerLease current = fixture.store.claimOwnerLeaseIfUnchanged(
                    fixture.store.findManifest(manifest.runId()).orElseThrow(), manifest.backendProcessId())
                    .orElseThrow();
            RunEventDraft durable = event(manifest.runId(), RunEventType.MESSAGE_UPDATED, "durable");
            RunEventDraft transientEvent = event(manifest.runId(), RunEventType.MESSAGE_PART_DELTA, "transient");
            RunSessionScope scope = new RunSessionScope(
                    manifest.runId(), manifest.rootRemoteSessionId(), 1L, "trace_owner_fence", NOW, NOW, Map.of());
            RunSessionScopeSession root = new RunSessionScopeSession(
                    manifest.runId(), manifest.rootRemoteSessionId(), manifest.rootRemoteSessionId(), null,
                    false, "ROOT", null, null, null, "trace_owner_fence", NOW, NOW, Map.of());

            assertFenceRejected(() -> fixture.store.appendDurable(durable, stale));
            assertFenceRejected(() -> fixture.store.projectTransient(transientEvent, stale));
            assertFenceRejected(() -> fixture.store.bindRemoteSession(manifest.runId(), "remote-stale", stale));
            assertFenceRejected(() -> fixture.store.saveScope(scope, root, stale));
            assertFenceRejected(() -> fixture.store.claimRawEvent(
                    manifest.runId(), manifest.rootRemoteSessionId(), "raw-fenced", stale));
            assertFenceRejected(() -> fixture.store.appendPending("child-fenced", durable, stale));
            assertThat(fixture.store.findManifest(manifest.runId()).orElseThrow().lastSeq()).isZero();
            assertThat(fixture.store.findScopeSession(manifest.runId(), manifest.rootRemoteSessionId())).isEmpty();

            fixture.store.appendDurable(durable, current);
            assertThat(fixture.store.projectTransient(transientEvent, current)).isTrue();
            fixture.store.bindRemoteSession(manifest.runId(), "remote-current", current);
            fixture.store.saveScope(scope, root, current);
            assertThat(fixture.store.claimRawEvent(
                    manifest.runId(), manifest.rootRemoteSessionId(), "raw-fenced", current)).isTrue();
            fixture.store.appendPending("child-fenced", durable, current);
            assertFenceRejected(() -> fixture.store.drainPending(
                    manifest.runId(), "child-fenced", stale));
            assertThat(fixture.store.drainPending(manifest.runId(), "child-fenced", current))
                    .containsExactly(durable);
            assertThat(fixture.store.findManifest(manifest.runId()).orElseThrow().rootRemoteSessionId())
                    .isEqualTo("remote-current");
            assertThat(fixture.store.findScopeSession(manifest.runId(), manifest.rootRemoteSessionId()))
                    .contains(root);
        } finally {
            fixture.close();
        }
    }

    @Test
    void discardBeforeDispatchRemovesRuntimeDetailsAndAllRequestIndexes() {
        Fixture fixture = fixture();
        try {
            RunRuntimeManifest manifest = manifest("run_unanchored_discard");
            assertThat(fixture.store.claimClientRequest(
                    manifest.sessionId(), manifest.clientRequestId(), manifest.runId())).isTrue();
            fixture.store.initialize(manifest, input(manifest.runId()));
            assertThat(fixture.store.claimOwnerLease(
                    manifest.runId(), manifest.backendProcessId())).isPresent();

            fixture.store.discardBeforeDispatch(manifest.runId());

            assertThat(fixture.store.findManifest(manifest.runId())).isEmpty();
            assertThat(fixture.store.findInput(manifest.runId())).isEmpty();
            assertThat(fixture.store.findByClientRequest(
                    manifest.sessionId(), manifest.clientRequestId())).isEmpty();
            assertThat(fixture.store.findActiveBySession(manifest.sessionId())).isEmpty();
            assertThat(fixture.store.findActiveByUser(manifest.userId())).isEmpty();
            assertThat(fixture.store.findActiveByServer(manifest.producerLinuxServerId())).isEmpty();
            assertThat(fixture.store.findRecentBySession(manifest.sessionId(), 10)).isEmpty();
            assertThat(fixture.redis.keys("test-agent:run:{" + manifest.runId().value() + "}:*")).isEmpty();
        } finally {
            fixture.close();
        }
    }

    private Fixture fixture() {
        String configuredPort = System.getProperty("test.redis.port");
        Assumptions.assumeTrue(configuredPort != null && !configuredPort.isBlank());
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                "127.0.0.1", Integer.parseInt(configuredPort));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        RedisRunRuntimeStore store = new RedisRunRuntimeStore(
                redis,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofMinutes(10),
                Duration.ofMinutes(30),
                100,
                1024 * 1024,
                64);
        return new Fixture(redis, connectionFactory, store);
    }

    private RunRuntimeManifest manifest(String runId) {
        return new RunRuntimeManifest(
                new RunId(runId), RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_owner_lease"), new SessionId("ses_owner_lease"),
                new WorkspaceId("wrk_owner_lease"), "opencode", "req_owner_lease",
                "msg_dispatch_owner_lease", "server-a", "bjp_server_a", "node_owner_lease",
                "ocp_owner_lease", "remote-owner-lease", RunStatus.RUNNING,
                0L, 0L, 1L, 0L, false, 0L, 0L,
                null, null, null, NOW.plus(Duration.ofHours(3)), NOW, NOW);
    }

    private RunRuntimeInput input(RunId runId) {
        return new RunRuntimeInput(
                runId, "仅存在 Redis 的原始输入", List.of(Map.of("type", "text")),
                "msg_dispatch_owner_lease", NOW);
    }

    private RunEventDraft event(RunId runId, RunEventType type, String text) {
        return new RunEventDraft(
                runId,
                type,
                "trace_owner_fence",
                NOW.plusSeconds(1),
                Map.of(
                        "sessionId", "remote-owner-lease",
                        "messageId", "msg-owner-lease",
                        "partId", "part-owner-lease",
                        "field", "text",
                        "text", text,
                        "delta", text));
    }

    private void assertFenceRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable mutation) {
        assertThatThrownBy(mutation)
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    private record Fixture(
            StringRedisTemplate redis,
            LettuceConnectionFactory connectionFactory,
            RedisRunRuntimeStore store) {

        private void close() {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            connectionFactory.destroy();
        }
    }
}
