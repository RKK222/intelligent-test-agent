package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.RunId;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunTerminalProjectionOutboxPayloadTest {

    @Test
    void enrichesTerminalDraftWithoutDroppingExistingPayload() {
        RunEventDraft original = new RunEventDraft(
                new RunId("run_terminal_outbox_payload"),
                RunEventType.RUN_FAILED,
                "trace_terminal_outbox_payload",
                Instant.parse("2026-07-11T12:00:00Z"),
                Map.of("status", "FAILED", "message", "safe failure"));

        RunEventDraft enriched = RunTerminalProjectionOutboxPayload.enrich(
                original,
                "TRANSPORT_ERROR",
                "STREAM_ERROR",
                "safe failure",
                false);

        assertThat(enriched.payload())
                .containsEntry("status", "FAILED")
                .containsEntry("terminalSource", "TRANSPORT_ERROR")
                .containsEntry("terminalReasonCode", "STREAM_ERROR")
                .containsEntry("safeErrorMessage", "safe failure")
                .containsEntry("remoteStopConfirmed", false);
        assertThat(enriched.traceId()).isEqualTo(original.traceId());
        assertThat(enriched.occurredAt()).isEqualTo(original.occurredAt());
    }
}
