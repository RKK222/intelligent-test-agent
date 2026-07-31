package com.enterprise.testagent.domain.appsource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 源码物化保留时长，业务允许 1 小时到 7 天，默认 48 小时。 */
public record AppSourceRetention(int hours) {

    public static final int DEFAULT_HOURS = 48;
    public static final int MAX_HOURS = 7 * 24;

    public AppSourceRetention {
        if (hours < 1 || hours > MAX_HOURS) {
            throw new IllegalArgumentException("retention hours must be between 1 and " + MAX_HOURS);
        }
    }

    public static AppSourceRetention defaultRetention() {
        return new AppSourceRetention(DEFAULT_HOURS);
    }

    /** 到期时间只从后端接受请求的权威 acceptedAt 推导。 */
    public Instant expiresAt(Instant acceptedAt) {
        return Objects.requireNonNull(acceptedAt, "acceptedAt must not be null").plus(hours, ChronoUnit.HOURS);
    }
}
