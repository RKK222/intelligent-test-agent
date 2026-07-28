package com.enterprise.testagent.domain.appsource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 源码物化保留时长，业务允许 1 到 72 小时，默认 48 小时。 */
public record AppSourceRetention(int hours) {

    public static final int DEFAULT_HOURS = 48;

    public AppSourceRetention {
        if (hours < 1 || hours > 72) {
            throw new IllegalArgumentException("retention hours must be between 1 and 72");
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
