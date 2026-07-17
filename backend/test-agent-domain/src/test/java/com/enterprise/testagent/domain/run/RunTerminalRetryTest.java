package com.enterprise.testagent.domain.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证终态待落库记录的严格退避与 24 小时安全保留边界。 */
class RunTerminalRetryTest {

    private static final Instant NOW = Instant.parse("2026-07-11T02:00:00Z");

    @Test
    void schedulesStrictBackoffAndCapsAtFiveMinutes() {
        RunTerminalRetry retry = RunTerminalRetry.pending(projection(NOW.plus(Duration.ofDays(2))), NOW);

        assertThat(retry.state()).isEqualTo(RunTerminalRetryState.TERMINAL_PENDING_DB);
        assertThat(retry.failedAttempts()).isEqualTo(1);
        assertThat(Duration.between(NOW, retry.nextAttemptAt())).isEqualTo(Duration.ofSeconds(5));
        assertThat(retry.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));

        List<Duration> expected = List.of(
                Duration.ofSeconds(15),
                Duration.ofSeconds(30),
                Duration.ofMinutes(1),
                Duration.ofMinutes(2),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5));
        for (Duration delay : expected) {
            Instant failedAt = retry.nextAttemptAt();
            retry = retry.rescheduleAfterFailure(failedAt).orElseThrow();
            assertThat(Duration.between(failedAt, retry.nextAttemptAt())).isEqualTo(delay);
        }
        assertThat(retry.failedAttempts()).isEqualTo(7);
    }

    @Test
    void usesEarlierDetailsExpiryAndStopsWhenNoFullBackoffFits() {
        Instant detailsExpiry = NOW.plusSeconds(8);
        RunTerminalRetry retry = RunTerminalRetry.pending(projection(detailsExpiry), NOW);

        assertThat(retry.expiresAt()).isEqualTo(detailsExpiry);
        assertThat(retry.rescheduleAfterFailure(retry.nextAttemptAt())).isEmpty();
        assertThat(retry.isExpired(detailsExpiry)).isTrue();
    }

    @Test
    void keepsSanitizedProjectionForTwentyFourHoursWhenRawDetailsAlreadyExpired() {
        RunTerminalRetry retry = RunTerminalRetry.pending(projection(NOW), NOW);

        assertThat(retry.nextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(retry.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void rejectsRetentionBeyondTwentyFourHours() {
        RunTerminalProjection projection = projection(NOW.plus(Duration.ofDays(2)));

        assertThatThrownBy(() -> new RunTerminalRetry(
                        projection,
                        RunTerminalRetryState.TERMINAL_PENDING_DB,
                        1,
                        NOW,
                        NOW.plusSeconds(5),
                        NOW.plus(Duration.ofHours(24)).plusMillis(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("24 hours");
    }

    private RunTerminalProjection projection(Instant detailsExpiresAt) {
        return new RunTerminalProjection(
                new RunId("run_terminal_retry_domain"),
                new SessionId("ses_terminal_retry_domain"),
                RunStatus.SUCCEEDED,
                1,
                "REMOTE_ROOT",
                "COMPLETED",
                null,
                false,
                3,
                detailsExpiresAt,
                "remote-root",
                RunDiffCounts.empty(),
                null,
                null,
                TokenUsage.empty(),
                BigDecimal.ZERO,
                "trace_terminal_retry_domain",
                NOW,
                "opencode",
                ConversationSourceType.MANUAL,
                null,
                new UserId("usr_terminal_retry_domain"),
                List.of());
    }
}
