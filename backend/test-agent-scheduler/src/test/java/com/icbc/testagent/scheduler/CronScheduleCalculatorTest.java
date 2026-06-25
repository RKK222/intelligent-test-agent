package com.icbc.testagent.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.PlatformException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CronScheduleCalculatorTest {

    private final CronScheduleCalculator calculator = new CronScheduleCalculator();

    @Test
    void computesNextFireAtWithUtcCronExpression() {
        Instant next = calculator.nextFireAt("0 */5 * * * *", Instant.parse("2026-06-25T00:02:00Z"));

        assertThat(next).isEqualTo(Instant.parse("2026-06-25T00:05:00Z"));
    }

    @Test
    void invalidCronUsesPlatformValidationError() {
        assertThatThrownBy(() -> calculator.nextFireAt("bad cron", Instant.parse("2026-06-25T00:02:00Z")))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("Cron 表达式无效");
    }
}
