package com.enterprise.testagent.opencode.runtime.night;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NightExecutionWindowCalculatorTest {

    private final NightExecutionWindowCalculator calculator = new NightExecutionWindowCalculator();

    @Test
    void daytimeBuildsFortyShanghaiSlotsAndRecommendsLeastReservedEarliest() {
        Instant now = Instant.parse("2026-07-18T04:03:00Z"); // 北京时间 12:03
        Instant secondSlot = Instant.parse("2026-07-18T13:15:00Z");

        var window = calculator.nextWindow(now, Map.of(
                Instant.parse("2026-07-18T13:00:00Z"), 3,
                secondSlot, 1), 4);

        assertThat(window.timeZone()).isEqualTo("Asia/Shanghai");
        assertThat(window.slots()).hasSize(40);
        assertThat(window.slots().getFirst().slotStart()).isEqualTo(Instant.parse("2026-07-18T13:00:00Z"));
        assertThat(window.slots().get(2).recommended()).isTrue();
    }

    @Test
    void exactBoundaryIsSelectableAndAfterSevenUsesNextNight() {
        var atBoundary = calculator.nextWindow(
                Instant.parse("2026-07-18T13:15:00Z"), Map.of(), 2);
        var afterWindow = calculator.nextWindow(
                Instant.parse("2026-07-18T23:00:00Z"), Map.of(), 2); // 北京时间 07:00

        assertThat(atBoundary.slots().getFirst().slotStart()).isEqualTo(Instant.parse("2026-07-18T13:15:00Z"));
        assertThat(afterWindow.slots().getFirst().slotStart()).isEqualTo(Instant.parse("2026-07-19T13:00:00Z"));
    }
}
