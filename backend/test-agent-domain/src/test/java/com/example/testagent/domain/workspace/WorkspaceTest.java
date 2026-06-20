package com.example.testagent.domain.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WorkspaceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-20T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-20T00:00:05Z");

    @Test
    void convenienceConstructorCreatesActiveWorkspaceWithPlaceholderTraceId() {
        Workspace workspace = new Workspace(
                new WorkspaceId("wrk_1234567890abcdef"),
                "Demo",
                "/tmp/demo",
                CREATED_AT);

        assertThat(workspace.status()).isEqualTo(WorkspaceStatus.ACTIVE);
        assertThat(workspace.createdAt()).isEqualTo(CREATED_AT);
        assertThat(workspace.updatedAt()).isEqualTo(CREATED_AT);
        assertThat(workspace.traceId()).isEqualTo("trace_unspecified");
    }

    @Test
    void constructorRejectsUpdatedAtBeforeCreatedAt() {
        assertThatThrownBy(() -> new Workspace(
                        new WorkspaceId("wrk_1234567890abcdef"),
                        "Demo",
                        "/tmp/demo",
                        WorkspaceStatus.ACTIVE,
                        UPDATED_AT,
                        CREATED_AT,
                        "trace_123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("updatedAt");
    }
}
