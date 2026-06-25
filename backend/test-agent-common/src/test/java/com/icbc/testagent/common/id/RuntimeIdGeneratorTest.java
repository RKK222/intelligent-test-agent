package com.icbc.testagent.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuntimeIdGeneratorTest {

    @Test
    void generatedRuntimeIdsUseStablePrefixesAndCompactUuidBody() {
        assertThat(RuntimeIdGenerator.workspaceId()).matches("wrk_[0-9a-f]{32}");
        assertThat(RuntimeIdGenerator.sessionId()).matches("ses_[0-9a-f]{32}");
        assertThat(RuntimeIdGenerator.runId()).matches("run_[0-9a-f]{32}");
        assertThat(RuntimeIdGenerator.messageId()).matches("msg_[0-9a-f]{32}");
        assertThat(RuntimeIdGenerator.terminalTicketId()).matches("pty_[0-9a-f]{32}");
        assertThat(RuntimeIdGenerator.managerCommandId()).matches("mcmd_[0-9a-f]{32}");
        assertThat(RuntimeIdGenerator.scheduledTaskRunId()).matches("str_[0-9a-f]{32}");
        assertThat(RuntimeIdGenerator.scheduledTaskPlanId()).matches("stp_[0-9a-f]{32}");
    }

    @Test
    void generatedIdsAreUniqueAcrossCalls() {
        assertThat(RuntimeIdGenerator.runId()).isNotEqualTo(RuntimeIdGenerator.runId());
    }
}
