package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.analytics.AnalyticsEventOutboxRepository;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 运营 outbox 的 PostgreSQL MyBatis mapper。 */
@Mapper
public interface AnalyticsEventOutboxMapper {

    List<AnalyticsEventOutboxRepository.Event> pending(
            @Param("limit") int limit,
            @Param("now") Instant now);

    int markPublished(@Param("ids") List<Long> ids, @Param("publishedAt") Instant publishedAt);

    int markFailed(
            @Param("ids") List<Long> ids,
            @Param("error") String error,
            @Param("nextAttemptAt") Instant nextAttemptAt);

    List<AnalyticsRedisOutboxRunRow> redisCandidates(
            @Param("recentTerminalThreshold") Instant recentTerminalThreshold,
            @Param("limit") int limit);

    int updateRedisCheckpoint(
            @Param("runId") String runId,
            @Param("lastStreamId") String lastStreamId,
            @Param("updatedAt") Instant updatedAt);

    int insertRedisCheckpoint(
            @Param("runId") String runId,
            @Param("lastStreamId") String lastStreamId,
            @Param("updatedAt") Instant updatedAt);

    List<AnalyticsBackfillEventRow> backfillDetailEvents(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);

    List<AnalyticsBackfillEventRow> backfillUserDimensionEvents();

    String cutoverStatus(@Param("cutoverId") String cutoverId);

    int upsertVerifiedCutover(
            @Param("cutoverId") String cutoverId,
            @Param("sourceCount") long sourceCount,
            @Param("targetCount") long targetCount,
            @Param("coverageStart") Instant coverageStart,
            @Param("coverageEnd") Instant coverageEnd,
            @Param("verifiedAt") Instant verifiedAt);

    void cleanupLegacyRollups();
}
