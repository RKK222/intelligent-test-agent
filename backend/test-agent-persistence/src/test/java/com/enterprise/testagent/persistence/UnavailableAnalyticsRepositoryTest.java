package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 锁定 ClickHouse 未启用时不得静默回退 PostgreSQL 运营查询。 */
class UnavailableAnalyticsRepositoryTest {

    @Test
    void queryFailsWithStableUnavailableError() {
        AnalyticsModels.Filter filter = new AnalyticsModels.Filter(
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                AnalyticsModels.Granularity.DAY,
                null, null, null, null, null, null, null,
                10, 1, 20, null);

        assertThatThrownBy(() -> new UnavailableAnalyticsRepository().queryRollups(filter))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.ANALYTICS_UNAVAILABLE));
    }
}
