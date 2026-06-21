package com.icbc.testagent.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.domain.run.RunId;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunEventTest {

    @Test
    void runEventKeepsAppendOnlyIdentityAndPayload() {
        RunEvent event = new RunEvent(
                new RunEventId("evt_1234567890abcdef"),
                new RunId("run_1234567890abcdef"),
                1L,
                RunEventType.RUN_STARTED,
                "trace_1234567890abcdef",
                Instant.parse("2026-06-19T00:00:00Z"),
                Map.of("status", "running"));

        assertThat(event.seq()).isEqualTo(1L);
        assertThat(event.type()).isEqualTo(RunEventType.RUN_STARTED);
        assertThat(event.payload()).containsEntry("status", "running");
    }

    @Test
    void runEventRejectsNonPositiveSeq() {
        assertThatThrownBy(() -> new RunEvent(
                        new RunEventId("evt_1234567890abcdef"),
                        new RunId("run_1234567890abcdef"),
                        0L,
                        RunEventType.RUN_STARTED,
                        "trace_1234567890abcdef",
                        Instant.parse("2026-06-19T00:00:00Z"),
                        Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seq");
    }
}
