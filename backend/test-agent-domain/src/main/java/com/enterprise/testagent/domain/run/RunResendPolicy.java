package com.enterprise.testagent.domain.run;

import java.time.Duration;

/** 定时执行自动重发的固定额度和退避策略。 */
public final class RunResendPolicy {

    public static final int MAX_AUTOMATIC_ATTEMPTS = 3;

    private RunResendPolicy() {
    }

    /** 自动重发按第 1/2/3 次分别等待 1/2/4 分钟。 */
    public static Duration automaticDelay(int automaticAttempt) {
        return switch (automaticAttempt) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(2);
            case 3 -> Duration.ofMinutes(4);
            default -> throw new IllegalArgumentException(
                    "automaticAttempt must be between 1 and " + MAX_AUTOMATIC_ATTEMPTS);
        };
    }

    public static boolean canScheduleAutomatic(int completedAutomaticAttempts) {
        if (completedAutomaticAttempts < 0) {
            throw new IllegalArgumentException("completedAutomaticAttempts must not be negative");
        }
        return completedAutomaticAttempts < MAX_AUTOMATIC_ATTEMPTS;
    }
}
