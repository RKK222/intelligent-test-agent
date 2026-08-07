package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 内部模型代理可观测性 MyBatis mapper。 */
@Mapper
public interface InternalModelObservabilityMapper {

    void insertCallRecord(InternalModelCallRecordRow row);

    /** 按小时主键原子累加聚合；statHour 由调用方截断到小时。 */
    void incrementHourlyStat(
            @Param("statHour") Instant statHour,
            @Param("providerId") String providerId,
            @Param("model") String model,
            @Param("endpoint") String endpoint,
            @Param("source") String source,
            @Param("outcome") String outcome,
            @Param("durationMillis") long durationMillis,
            @Param("firstTokenMillis") Long firstTokenMillis);

    List<InternalModelCallRecordRow> findCallRecords(
            @Param("providerId") String providerId,
            @Param("outcome") String outcome,
            @Param("source") String source,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countCallRecords(
            @Param("providerId") String providerId,
            @Param("outcome") String outcome,
            @Param("source") String source,
            @Param("from") Instant from,
            @Param("to") Instant to);

    List<InternalModelCallHourlyStatRow> findHourlyStats(
            @Param("providerId") String providerId,
            @Param("source") String source,
            @Param("from") Instant from,
            @Param("to") Instant to);

    int deleteCallRecordsBefore(@Param("cutoff") Instant cutoff);

    int deleteHourlyStatsBefore(@Param("cutoff") Instant cutoff);

    void upsertProbeStatus(InternalModelProbeStatusRow row);

    List<InternalModelProbeStatusRow> findAllProbeStatus();
}
