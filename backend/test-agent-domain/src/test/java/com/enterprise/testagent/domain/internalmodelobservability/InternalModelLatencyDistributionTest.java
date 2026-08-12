package com.enterprise.testagent.domain.internalmodelobservability;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InternalModelLatencyDistributionTest {

    @Test
    void rejectsNegativeLatencyValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InternalModelLatencyDistribution(1, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0));
    }
}
