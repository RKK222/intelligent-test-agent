package com.enterprise.testagent.opencode.runtime.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import org.junit.jupiter.api.Test;

/** 验证夜间容量必须由部署显式配置，并在缺失或非法时安全关闭功能。 */
class NightExecutionPropertiesTest {

    @Test
    void returnsConfiguredPositiveCapacity() {
        NightExecutionProperties properties = new NightExecutionProperties();
        properties.setSlotCapacity(" 6 ");

        assertThat(properties.requireSlotCapacity()).isEqualTo(6);
    }

    @Test
    void rejectsMissingOrInvalidCapacityWithStableError() {
        NightExecutionProperties properties = new NightExecutionProperties();

        assertThatThrownBy(properties::requireSlotCapacity)
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.NIGHT_EXECUTION_UNAVAILABLE));

        properties.setSlotCapacity("0");
        assertThatThrownBy(properties::requireSlotCapacity)
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.NIGHT_EXECUTION_UNAVAILABLE));
    }
}
