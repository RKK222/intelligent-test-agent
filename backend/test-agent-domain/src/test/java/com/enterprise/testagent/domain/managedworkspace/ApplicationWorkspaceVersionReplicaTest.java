package com.enterprise.testagent.domain.managedworkspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 副本时间按 PostgreSQL 微秒舍入精度校验，同时保留真实倒序拦截。 */
class ApplicationWorkspaceVersionReplicaTest {
    @Test
    void acceptsSameDatabaseMicrosecondAcrossSecondBoundary() {
        Instant created = Instant.parse("2026-09-07T09:00:01Z");
        Instant observed = created.minusNanos(211);
        var ready = replica(created).ready("commit", observed, "trace_precision");
        assertThat(ready.lastSyncedAt()).isEqualTo(observed);
    }

    @Test
    void rejectsEarlierDatabaseMicrosecond() {
        Instant created = Instant.parse("2026-09-07T09:00:01Z");
        assertThatThrownBy(() -> replica(created).ready("commit", created.minusNanos(1000), "trace_precision"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("updatedAt must not be before createdAt");
    }

    private ApplicationWorkspaceVersionReplica replica(Instant created) {
        return new ApplicationWorkspaceVersionReplica(new ApplicationWorkspaceVersionReplicaId("awr_test"),
                new ApplicationWorkspaceVersionId("awv_test"), "server_test", "/repo", "/repo/workspace",
                new WorkspaceId("wrk_test"), "commit", WorkspaceReplicaSyncStatus.READY, null,
                created, "trace_precision", created, created);
    }
}
