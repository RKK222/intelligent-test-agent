package com.icbc.testagent.domain.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DomainValidationTest {

    @Test
    void requirePrefixedIdAcceptsExpectedPrefixOnly() {
        assertThat(DomainValidation.requirePrefixedId("run_123", "run_", "runId")).isEqualTo("run_123");

        assertThatThrownBy(() -> DomainValidation.requirePrefixedId("ses_123", "run_", "runId"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("run_");
    }

    @Test
    void requireTextRejectsNullAndBlankValues() {
        assertThat(DomainValidation.requireText(" value ", "field")).isEqualTo(" value ");
        assertThatThrownBy(() -> DomainValidation.requireText(null, "field"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("field");
        assertThatThrownBy(() -> DomainValidation.requireText(" ", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }

    @Test
    void requireInstantRejectsNullValues() {
        Instant now = Instant.parse("2026-06-20T00:00:00Z");

        assertThat(DomainValidation.requireInstant(now, "createdAt")).isSameAs(now);
        assertThatThrownBy(() -> DomainValidation.requireInstant(null, "createdAt"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("createdAt");
    }
}
