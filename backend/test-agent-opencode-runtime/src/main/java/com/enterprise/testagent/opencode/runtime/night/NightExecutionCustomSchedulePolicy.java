package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 超级管理员测试定时的服务端时间边界，避免依赖浏览器时区和客户端 min/max。 */
final class NightExecutionCustomSchedulePolicy {

    private static final Duration DISPLAY_DURATION = Duration.ofMinutes(1);
    private static final Duration RETRY_WINDOW = Duration.ofMinutes(15);
    private static final Duration MAX_AHEAD = Duration.ofHours(24);

    private NightExecutionCustomSchedulePolicy() {
    }

    /** 校验整分钟计划时间，并生成显示区间与短暂分发重试窗口。 */
    static Schedule resolve(Instant requested, Instant now) {
        Objects.requireNonNull(requested, "requested must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (!requested.equals(requested.truncatedTo(ChronoUnit.MINUTES))) {
            throw invalid("测试定时时间必须精确到分钟");
        }
        Instant earliest = now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES);
        Instant latest = now.plus(MAX_AHEAD);
        if (requested.isBefore(earliest) || requested.isAfter(latest)) {
            throw invalid("测试定时时间必须介于下一完整分钟和未来 24 小时之间");
        }
        return new Schedule(
                requested,
                requested.plus(DISPLAY_DURATION),
                requested.plus(RETRY_WINDOW));
    }

    private static PlatformException invalid(String message) {
        return new PlatformException(ErrorCode.VALIDATION_ERROR, message);
    }

    /** 自定义计划在既有任务聚合中的三个时间边界。 */
    record Schedule(Instant slotStart, Instant slotEnd, Instant windowEnd) {
    }
}
