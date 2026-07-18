package com.enterprise.testagent.domain.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReferenceRepositoryDomainTest {

    @Test
    void identifiesActiveRepositoryOperations() {
        assertThat(ReferenceRepositoryStatus.INITIALIZING.active()).isTrue();
        assertThat(ReferenceRepositoryStatus.VERIFYING.active()).isTrue();
        assertThat(ReferenceRepositoryStatus.SYNCHRONIZING.active()).isTrue();
        assertThat(ReferenceRepositoryStatus.UNINITIALIZED.active()).isFalse();
        assertThat(ReferenceRepositoryStatus.READY.active()).isFalse();
        assertThat(ReferenceRepositoryStatus.FAILED.active()).isFalse();
    }

    @Test
    void capsTransientRetryBackoffAtFiveMinutes() {
        assertThat(ReferenceRepositoryReplica.retryDelay(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(ReferenceRepositoryReplica.retryDelay(2)).isEqualTo(Duration.ofSeconds(10));
        assertThat(ReferenceRepositoryReplica.retryDelay(7)).isEqualTo(Duration.ofMinutes(5));
        assertThat(ReferenceRepositoryReplica.retryDelay(99)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void replicaLeaseBelongsOnlyToSameGenerationAndToken() {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        ReferenceRepositoryReplica replica = new ReferenceRepositoryReplica(
                new CodeRepositoryId("repo_assets"),
                new LinuxServerId("server-a"),
                3L,
                ReferenceRepositoryReplicaStatus.PROCESSING,
                "main",
                null,
                0,
                null,
                "lease-current",
                now.plusSeconds(30),
                null,
                null,
                now,
                now);

        assertThat(replica.ownsLease(3L, "lease-current", now)).isTrue();
        assertThat(replica.ownsLease(2L, "lease-current", now)).isFalse();
        assertThat(replica.ownsLease(3L, "lease-stale", now)).isFalse();
        assertThat(replica.ownsLease(3L, "lease-current", now.plusSeconds(31))).isFalse();
    }

    @Test
    void repositoryStatePreservesCredentialUserAndGeneration() {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        ReferenceRepositoryState state = new ReferenceRepositoryState(
                new CodeRepositoryId("repo_assets"),
                "main",
                "abc123",
                4L,
                ReferenceRepositoryStatus.SYNCHRONIZING,
                new UserId("usr_admin"),
                "trace_reference",
                null,
                now,
                now,
                now);

        assertThat(state.generation()).isEqualTo(4L);
        assertThat(state.credentialUserId()).isEqualTo(new UserId("usr_admin"));
        assertThat(state.status().active()).isTrue();
    }

    @Test
    void repositoryStateAndReplicaPreserveOperationAndVerificationSnapshot() {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        ReferenceRepositoryState state = new ReferenceRepositoryState(
                new CodeRepositoryId("repo_assets"),
                "release",
                "def456",
                5L,
                ReferenceRepositoryStatus.SYNCHRONIZING,
                ReferenceRepositoryOperationType.SWITCH_BRANCH,
                new UserId("usr_admin"),
                "trace_switch",
                null,
                now,
                now,
                now);
        ReferenceRepositoryReplica replica = new ReferenceRepositoryReplica(
                new CodeRepositoryId("repo_assets"),
                new LinuxServerId("server-a"),
                5L,
                ReferenceRepositoryReplicaStatus.READY,
                "release",
                "def456",
                0,
                null,
                null,
                null,
                null,
                now,
                now,
                now,
                now);

        assertThat(state.operationType()).isEqualTo(ReferenceRepositoryOperationType.SWITCH_BRANCH);
        assertThat(replica.verifiedAt()).isEqualTo(now);
    }
}
