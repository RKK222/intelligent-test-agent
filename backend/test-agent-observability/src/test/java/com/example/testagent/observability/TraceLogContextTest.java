package com.example.testagent.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TraceLogContextTest {

    @AfterEach
    void clearTraceId() {
        TraceLogContext.clearTraceId();
    }

    @Test
    void withTraceIdWritesAndRestoresMdcTraceId() {
        TraceLogContext.putTraceId("trace_previous123456");

        try (TraceLogContext.Scope ignored = TraceLogContext.withTraceId("trace_1234567890abcdef")) {
            assertThat(TraceLogContext.currentTraceId()).isEqualTo("trace_1234567890abcdef");
        }

        assertThat(TraceLogContext.currentTraceId()).isEqualTo("trace_previous123456");
    }

    @Test
    void putTraceIdClearsInvalidValue() {
        TraceLogContext.putTraceId("bad trace id");

        assertThat(TraceLogContext.currentTraceId()).isNull();
    }
}
