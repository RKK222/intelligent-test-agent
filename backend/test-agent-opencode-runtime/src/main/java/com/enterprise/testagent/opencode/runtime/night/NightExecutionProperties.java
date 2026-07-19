package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 夜间执行部署配置；容量缺失或非法时保持应用可启动，但功能 fail-closed。 */
@Component
@ConfigurationProperties(prefix = "test-agent.night-execution")
public class NightExecutionProperties {

    private String slotCapacity;

    public String getSlotCapacity() {
        return slotCapacity;
    }

    public void setSlotCapacity(String slotCapacity) {
        this.slotCapacity = slotCapacity;
    }

    /** 返回生产显式配置的正整数容量，否则抛出稳定的功能不可用错误。 */
    public int requireSlotCapacity() {
        if (slotCapacity == null || slotCapacity.isBlank()) {
            throw new PlatformException(
                    ErrorCode.NIGHT_EXECUTION_UNAVAILABLE,
                    "夜间执行时段容量尚未配置，请联系管理员");
        }
        try {
            int value = Integer.parseInt(slotCapacity.trim());
            if (value < 1) throw new NumberFormatException("not positive");
            return value;
        } catch (NumberFormatException exception) {
            throw new PlatformException(
                    ErrorCode.NIGHT_EXECUTION_UNAVAILABLE,
                    "夜间执行时段容量配置无效，请联系管理员");
        }
    }
}
