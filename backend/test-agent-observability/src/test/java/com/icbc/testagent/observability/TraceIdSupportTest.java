package com.icbc.testagent.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TraceIdSupportTest {

    @Test
    void resolveKeepsValidInboundTraceId() {
        String traceId = TraceIdSupport.resolve("trace_1234567890abcdef");

        assertThat(traceId).isEqualTo("trace_1234567890abcdef");
    }

    @Test
    void resolveGeneratesTraceIdWhenInboundIsMissingOrInvalid() {
        String missing = TraceIdSupport.resolve(null);
        String invalid = TraceIdSupport.resolve("../../../secret");

        assertThat(missing).startsWith("trace_");
        assertThat(invalid).startsWith("trace_");
        assertThat(invalid).isNotEqualTo("../../../secret");
        assertThat(TraceIdSupport.isValid(missing)).isTrue();
        assertThat(TraceIdSupport.isValid(invalid)).isTrue();
    }

    @Test
    void isValidRejectsUnsafeLengthAndCharacters() {
        assertThat(TraceIdSupport.isValid("trace_1234567890abcdef-ABC_def")).isTrue();
        assertThat(TraceIdSupport.isValid("trace_short")).isFalse();
        assertThat(TraceIdSupport.isValid("trace_" + "a".repeat(100))).isFalse();
        assertThat(TraceIdSupport.isValid("trace_1234567890abc/def")).isFalse();
        assertThat(TraceIdSupport.isValid("span_1234567890abcdef")).isFalse();
    }
}
