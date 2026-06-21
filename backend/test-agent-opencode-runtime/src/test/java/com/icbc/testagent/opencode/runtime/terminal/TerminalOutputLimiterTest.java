package com.icbc.testagent.opencode.runtime.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TerminalOutputLimiterTest {

    @Test
    void truncatesSingleOutputFrameAndMarksEnvelope() {
        TerminalOutputLimiter limiter = new TerminalOutputLimiter(5, 20);

        List<TerminalServerMessage> messages = limiter.output("abcdef", 1);

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().type()).isEqualTo("output");
        assertThat(messages.getFirst().data()).isEqualTo("abcde");
        assertThat(messages.getFirst().truncated()).isTrue();
    }

    @Test
    void truncatesWhenCumulativeBudgetWouldBeExceeded() {
        TerminalOutputLimiter limiter = new TerminalOutputLimiter(10, 8);

        assertThat(limiter.output("12345", 1).getFirst().data()).isEqualTo("12345");
        List<TerminalServerMessage> messages = limiter.output("67890", 2);

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().data()).isEqualTo("678");
        assertThat(messages.getFirst().truncated()).isTrue();
    }

    @Test
    void emitsWarningWhenOutputBudgetIsAlreadyExhausted() {
        TerminalOutputLimiter limiter = new TerminalOutputLimiter(10, 3);

        assertThat(limiter.output("abc", 1).getFirst().truncated()).isFalse();
        List<TerminalServerMessage> messages = limiter.output("d", 2);

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().type()).isEqualTo("warning");
        assertThat(messages.getFirst().errorCode()).isEqualTo("PTY_OUTPUT_TRUNCATED");
    }
}
