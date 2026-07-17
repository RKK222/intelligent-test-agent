package com.enterprise.testagent.opencode.runtime.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TerminalProcessSessionTest {

    @Test
    void appliesOutputLimiterBeforePublishingProcessOutput() {
        TerminalProcessSession session = new TerminalProcessSession(
                new StaticOutputProcess("abcdef"),
                new TerminalOutputLimiter(3, 10));

        TerminalServerMessage output = session.output()
                .filter(message -> "output".equals(message.type()))
                .blockFirst(Duration.ofSeconds(1));

        assertThat(output).isNotNull();
        assertThat(output.data()).isEqualTo("abc");
        assertThat(output.truncated()).isTrue();
    }

    private static final class StaticOutputProcess extends Process {
        private final InputStream inputStream;
        private final OutputStream outputStream = new ByteArrayOutputStream();

        private StaticOutputProcess(String output) {
            this.inputStream = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
        }
    }
}
