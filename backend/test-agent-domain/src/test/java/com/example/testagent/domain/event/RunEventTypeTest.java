package com.example.testagent.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RunEventTypeTest {

    @Test
    void runEventTypeExposesStableWireNames() {
        assertThat(RunEventType.RUN_STARTED.wireName()).isEqualTo("run.started");
        assertThat(RunEventType.ASSISTANT_MESSAGE_DELTA.wireName()).isEqualTo("assistant.message.delta");
        assertThat(RunEventType.TOOL_FINISHED.wireName()).isEqualTo("tool.finished");
        assertThat(RunEventType.OPENCODE_EVENT_UNKNOWN.wireName()).isEqualTo("opencode.event.unknown");
    }

    @Test
    void runEventTypeCanBeResolvedFromWireName() {
        assertThat(RunEventType.fromWireName("diff.proposed")).contains(RunEventType.DIFF_PROPOSED);
        assertThat(RunEventType.fromWireName("missing.event")).isEmpty();
    }
}
