package com.enterprise.testagent.domain.internalmodelobservability;

import com.enterprise.testagent.common.pagination.PageResponse;
import java.time.Instant;
import java.util.List;

/**
 * 内部模型代理调用观测的关系型持久化端口。
 * record 语义：明细 insert 与小时聚合 upsert 在同一事务内完成，避免明细/聚合漂移。
 */
public interface InternalModelCallRecordRepository {

    /** 落一条明细并累加对应小时聚合，调用方保证在事务边界内调用。 */
    void record(InternalModelCallRecord record);

    PageResponse<InternalModelCallRecord> query(InternalModelCallRecordQuery query);

    /** 按 provider/时间返回小时聚合序列，按 stat_hour 升序。 */
    List<InternalModelCallHourlyStat> queryHourlyStats(
            String providerId, InternalModelCallSource source, Instant from, Instant to);

    /** 按筛选范围从调用明细计算 TTFT 五数概括，不用小时均值估算分位数。 */
    InternalModelTtftDistribution queryTtftDistribution(
            String providerId,
            List<InternalModelCallOutcome> outcomes,
            InternalModelCallSource source,
            Instant from,
            Instant to);

    /** 保留旧调用形态，未传 source 时查询所有来源。 */
    default List<InternalModelCallHourlyStat> queryHourlyStats(String providerId, Instant from, Instant to) {
        return queryHourlyStats(providerId, null, from, to);
    }

    /** 删除截止时间之前的明细记录，返回删除行数。 */
    int deleteRecordsBefore(Instant cutoff);

    /** 删除截止时间之前的小时聚合，返回删除行数。 */
    int deleteHourlyStatsBefore(Instant cutoff);
}
