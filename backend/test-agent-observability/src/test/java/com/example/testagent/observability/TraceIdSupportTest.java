package com.example.testagent.observability;

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
}
