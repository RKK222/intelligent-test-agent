package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallHourlyStat;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecord;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordQuery;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordRepository;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 内部模型代理调用观测 MyBatis Repository：明细 insert 与小时聚合 upsert 同一事务。 */
@Repository
public class MyBatisInternalModelCallRecordRepository implements InternalModelCallRecordRepository {

    private final InternalModelObservabilityMapper mapper;

    public MyBatisInternalModelCallRecordRepository(InternalModelObservabilityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void record(InternalModelCallRecord record) {
        mapper.insertCallRecord(toRow(record));
        mapper.incrementHourlyStat(
                record.startedAt().truncatedTo(ChronoUnit.HOURS),
                record.providerId(),
                record.model(),
                record.endpoint(),
                record.source().name(),
                record.outcome().name(),
                record.durationMillis(),
                record.firstTokenMillis(),
                record.streamCompleteMillis());
    }

    @Override
    public PageResponse<InternalModelCallRecord> query(InternalModelCallRecordQuery query) {
        List<String> outcomes = query.outcomes().stream().map(Enum::name).toList();
        String source = query.source() == null ? null : query.source().name();
        List<InternalModelCallRecordRow> rows = mapper.findCallRecords(
                query.providerId(), outcomes, source, query.from(), query.to(),
                query.page().size(), query.page().offset());
        long total = mapper.countCallRecords(
                query.providerId(), outcomes, source, query.from(), query.to());
        return new PageResponse<>(
                rows.stream().map(this::toDomain).toList(),
                query.page().page(),
                query.page().size(),
                total);
    }

    @Override
    public List<InternalModelCallHourlyStat> queryHourlyStats(
            String providerId, InternalModelCallSource source, Instant from, Instant to) {
        return mapper.findHourlyStats(
                        providerId, source == null ? null : source.name(), from, to).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int deleteRecordsBefore(Instant cutoff) {
        return mapper.deleteCallRecordsBefore(cutoff);
    }

    @Override
    public int deleteHourlyStatsBefore(Instant cutoff) {
        return mapper.deleteHourlyStatsBefore(cutoff);
    }

    private InternalModelCallRecordRow toRow(InternalModelCallRecord record) {
        return new InternalModelCallRecordRow(
                null,
                record.providerId(),
                record.model(),
                record.endpoint(),
                record.source().name(),
                record.outcome().name(),
                record.httpStatus(),
                record.errorClass(),
                record.streaming(),
                record.durationMillis(),
                record.firstByteMillis(),
                record.firstTokenMillis(),
                record.streamCompleteMillis(),
                record.traceId(),
                record.ucid(),
                record.startedAt());
    }

    private InternalModelCallRecord toDomain(InternalModelCallRecordRow row) {
        return new InternalModelCallRecord(
                row.id(),
                row.providerId(),
                row.model(),
                row.endpoint(),
                InternalModelCallSource.valueOf(row.source()),
                InternalModelCallOutcome.valueOf(row.outcome()),
                row.httpStatus(),
                row.errorClass(),
                row.streaming(),
                row.durationMillis(),
                row.firstByteMillis(),
                row.firstTokenMillis(),
                row.streamCompleteMillis(),
                row.traceId(),
                row.ucid(),
                row.startedAt());
    }

    private InternalModelCallHourlyStat toDomain(InternalModelCallHourlyStatRow row) {
        return new InternalModelCallHourlyStat(
                row.statHour(),
                row.providerId(),
                row.model(),
                row.endpoint(),
                row.source(),
                row.outcome(),
                row.requestCount(),
                row.durationMillisSum(),
                row.durationMillisMax(),
                row.firstTokenMillisSum(),
                row.firstTokenMillisMax(),
                row.firstTokenCount(),
                row.streamCompleteMillisSum(),
                row.streamCompleteMillisMax(),
                row.streamCompleteCount());
    }
}
