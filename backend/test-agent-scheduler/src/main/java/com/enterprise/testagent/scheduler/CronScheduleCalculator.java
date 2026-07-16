package com.enterprise.testagent.scheduler;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Cron 下次触发时间计算器，统一使用 UTC，避免多节点默认时区差异影响扫描结果。
 */
@Component
public class CronScheduleCalculator {

    /**
     * 计算 from 之后的下一次触发时间；Cron 非法时转换为统一校验错误。
     */
    public Instant nextFireAt(String cronExpression, Instant from) {
        try {
            CronExpression expression = CronExpression.parse(cronExpression);
            ZonedDateTime next = expression.next(ZonedDateTime.ofInstant(from, ZoneOffset.UTC));
            if (next == null) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "Cron 表达式无法计算下一次触发时间",
                        Map.of("cronExpression", cronExpression));
            }
            return next.toInstant();
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "Cron 表达式无效",
                    Map.of("cronExpression", cronExpression),
                    exception);
        }
    }
}
