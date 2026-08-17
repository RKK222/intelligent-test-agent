package com.enterprise.testagent.opencode.runtime.internalmodel.observability;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallHourlyStat;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcomeGroup;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecord;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordQuery;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordRepository;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallSource;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelProbeStatus;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelProbeStatusRepository;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelLatencyDistribution;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelThroughputDistribution;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 内部模型代理可观测性查询服务，收口页大小与时间范围防护，供管理 API 调用。
 */
@Service
public class InternalModelObservabilityQueryService {

    static final int MAX_PAGE_SIZE = 100;
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
    private static final Duration MAX_WINDOW = Duration.ofDays(31);

    private final InternalModelCallRecordRepository callRecordRepository;
    private final InternalModelProbeStatusRepository probeStatusRepository;

    public InternalModelObservabilityQueryService(
            InternalModelCallRecordRepository callRecordRepository,
            InternalModelProbeStatusRepository probeStatusRepository) {
        this.callRecordRepository =
                Objects.requireNonNull(callRecordRepository, "callRecordRepository must not be null");
        this.probeStatusRepository =
                Objects.requireNonNull(probeStatusRepository, "probeStatusRepository must not be null");
    }

    public PageResponse<InternalModelCallRecord> queryCallRecords(
            String providerId,
            InternalModelCallOutcome outcome,
            InternalModelCallOutcomeGroup outcomeGroup,
            InternalModelCallSource source,
            String ucid,
            Instant from,
            Instant to,
            PageRequest pageRequest) {
        int size = Math.min(pageRequest.size(), MAX_PAGE_SIZE);
        PageRequest bounded = new PageRequest(pageRequest.page(), size);
        Instant[] window = boundedWindow(from, to);
        List<InternalModelCallOutcome> outcomes = outcome != null
                ? List.of(outcome)
                : outcomeGroup == null ? List.of() : outcomeGroup.outcomes();
        return callRecordRepository.query(new InternalModelCallRecordQuery(
                providerId, outcomes, source, ucid, window[0], window[1], bounded));
    }

    /** 保留未传 ucid 的调用形态，兼容既有内部调用与测试。 */
    public PageResponse<InternalModelCallRecord> queryCallRecords(
            String providerId,
            InternalModelCallOutcome outcome,
            InternalModelCallOutcomeGroup outcomeGroup,
            InternalModelCallSource source,
            Instant from,
            Instant to,
            PageRequest pageRequest) {
        return queryCallRecords(providerId, outcome, outcomeGroup, source, null, from, to, pageRequest);
    }

    /** 保留旧的单 outcome 查询形态，兼容既有内部调用与测试。 */
    public PageResponse<InternalModelCallRecord> queryCallRecords(
            String providerId,
            InternalModelCallOutcome outcome,
            InternalModelCallSource source,
            Instant from,
            Instant to,
            PageRequest pageRequest) {
        return queryCallRecords(providerId, outcome, null, source, null, from, to, pageRequest);
    }

    public List<InternalModelCallHourlyStat> queryHourlyStats(
            String providerId, InternalModelCallSource source, Instant from, Instant to) {
        Instant[] window = boundedWindow(from, to);
        return callRecordRepository.queryHourlyStats(providerId, source, window[0], window[1]);
    }

    /**
     * 查询 TTFT 平均值与五数概括。分位数必须基于单次调用明细计算，不能从小时平均值反推。
     */
    public InternalModelLatencyDistribution queryTtftDistribution(
            String providerId,
            InternalModelCallOutcomeGroup outcomeGroup,
            InternalModelCallSource source,
            Instant from,
            Instant to) {
        Instant[] window = boundedWindow(from, to);
        List<InternalModelCallOutcome> outcomes = outcomeGroup == null ? List.of() : outcomeGroup.outcomes();
        return callRecordRepository.queryTtftDistribution(
                providerId, outcomes, source, window[0], window[1]);
    }

    /** ITL 与 TPOT 是同一指标，只统计上游返回准确输出 Token 数的完整流。 */
    public InternalModelLatencyDistribution queryItlDistribution(
            String providerId,
            InternalModelCallOutcomeGroup outcomeGroup,
            InternalModelCallSource source,
            Instant from,
            Instant to) {
        Instant[] window = boundedWindow(from, to);
        List<InternalModelCallOutcome> outcomes = outcomeGroup == null ? List.of() : outcomeGroup.outcomes();
        return callRecordRepository.queryItlDistribution(
                providerId, outcomes, source, window[0], window[1]);
    }

    /** Output TPS 必须逐条计算后再聚合，不能用 1000 / 平均 ITL 反推。 */
    public InternalModelThroughputDistribution queryTpsDistribution(
            String providerId,
            InternalModelCallOutcomeGroup outcomeGroup,
            InternalModelCallSource source,
            Instant from,
            Instant to) {
        Instant[] window = boundedWindow(from, to);
        List<InternalModelCallOutcome> outcomes = outcomeGroup == null ? List.of() : outcomeGroup.outcomes();
        return callRecordRepository.queryTpsDistribution(
                providerId, outcomes, source, window[0], window[1]);
    }

    /** 保留旧调用形态，未传 source 时查询所有来源。 */
    public List<InternalModelCallHourlyStat> queryHourlyStats(String providerId, Instant from, Instant to) {
        return queryHourlyStats(providerId, null, from, to);
    }

    public List<InternalModelProbeStatus> findProbeStatus() {
        return probeStatusRepository.findAll();
    }

    /**
     * 归一化时间范围：未提供时默认最近 24 小时；最大 31 天，避免明细表全表扫描。
     */
    private Instant[] boundedWindow(Instant from, Instant to) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(DEFAULT_WINDOW) : from;
        if (Duration.between(start, end).compareTo(MAX_WINDOW) > 0) {
            start = end.minus(MAX_WINDOW);
        }
        return new Instant[] {start, end};
    }
}
