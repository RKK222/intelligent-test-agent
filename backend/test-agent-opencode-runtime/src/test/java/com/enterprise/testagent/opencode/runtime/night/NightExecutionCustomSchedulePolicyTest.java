package com.enterprise.testagent.opencode.runtime.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 验证超级管理员测试定时使用服务端时钟，并且只接受未来二十四小时内的整分钟。 */
class NightExecutionCustomSchedulePolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-24T03:00:30Z");

    @Test
    void createsOneMinuteDisplayRangeAndFifteenMinuteRetryWindow() {
        Instant requested = Instant.parse("2026-07-24T03:01:00Z");

        NightExecutionCustomSchedulePolicy.Schedule schedule =
                NightExecutionCustomSchedulePolicy.resolve(requested, NOW);

        assertThat(schedule.slotStart()).isEqualTo(requested);
        assertThat(schedule.slotEnd()).isEqualTo(requested.plusSeconds(60));
        assertThat(schedule.windowEnd()).isEqualTo(requested.plusSeconds(900));
    }

    @Test
    void rejectsTimeBeforeTheNextWholeMinute() {
        assertValidationFailure(Instant.parse("2026-07-24T03:00:00Z"));
    }

    @Test
    void rejectsTimeWithSecondsOrNanoseconds() {
        assertValidationFailure(Instant.parse("2026-07-24T03:01:01Z"));
        assertValidationFailure(Instant.parse("2026-07-24T03:01:00.001Z"));
    }

    @Test
    void rejectsTimeLaterThanTwentyFourHoursFromNow() {
        assertValidationFailure(Instant.parse("2026-07-25T03:01:00Z"));
    }

    private void assertValidationFailure(Instant requested) {
        assertThatThrownBy(() -> NightExecutionCustomSchedulePolicy.resolve(requested, NOW))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
