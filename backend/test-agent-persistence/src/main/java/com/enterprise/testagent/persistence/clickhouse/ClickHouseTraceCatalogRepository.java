package com.enterprise.testagent.persistence.clickhouse;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.trace.TraceCatalogRepository;
import com.enterprise.testagent.domain.trace.TraceModels;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** ClickHouse Trace 目录实现；异常统一映射为 Trace 服务不可用，且不会输出正文或路径。 */
@Repository
@ConditionalOnProperty(name = "test-agent.analytics.clickhouse.enabled", havingValue = "true")
public class ClickHouseTraceCatalogRepository implements TraceCatalogRepository {

    private final ClickHouseTraceCatalogMapper mapper;

    public ClickHouseTraceCatalogRepository(ClickHouseTraceCatalogMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public void save(
            TraceModels.Catalog catalog,
            List<TraceModels.Span> spans,
            List<TraceModels.CapabilityFact> capabilityFacts,
            Instant ingestedAt) {
        available(() -> {
            mapper.insertCatalog(catalog, Math.max(1, catalog.updatedAt().toEpochMilli()));
            if (!spans.isEmpty()) {
                mapper.insertSpans(spans);
            }
            if (!capabilityFacts.isEmpty()) {
                mapper.insertCapabilityFacts(capabilityFacts, ingestedAt);
            }
            return null;
        });
    }

    @Override
    public Optional<TraceModels.Catalog> find(String traceId) {
        return available(() -> Optional.ofNullable(mapper.find(traceId)));
    }

    @Override
    public PageResponse<TraceModels.Catalog> search(TraceModels.Filter filter) {
        return available(() -> {
            long offset = (long) Math.max(0, filter.page() - 1) * filter.pageSize();
            return new PageResponse<>(
                    mapper.search(filter, filter.pageSize(), offset),
                    filter.page(),
                    filter.pageSize(),
                    mapper.count(filter));
        });
    }

    @Override
    public TraceModels.EventPage events(String traceId, long afterSequence, int limit) {
        return available(() -> new TraceModels.EventPage(
                mapper.events(traceId, afterSequence, limit),
                mapper.countEvents(traceId),
                find(traceId).map(TraceModels.Catalog::completeThrough).orElse(0L)));
    }

    @Override
    public TraceModels.EventPage trajectory(String traceId, long afterSequence, int limit) {
        return available(() -> new TraceModels.EventPage(
                mapper.trajectory(traceId, afterSequence, limit),
                mapper.countTrajectory(traceId),
                find(traceId).map(TraceModels.Catalog::completeThrough).orElse(0L)));
    }

    @Override
    public Map<String, List<String>> findSkillsByTraceIds(List<String> traceIds) {
        if (traceIds == null || traceIds.isEmpty()) {
            return Map.of();
        }
        return available(() -> {
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (TraceSkillRow row : mapper.findSkillsByTraceIds(traceIds)) {
                result.computeIfAbsent(row.traceId(), ignored -> new java.util.ArrayList<>()).add(row.skillName());
            }
            return result;
        });
    }

    private <T> T available(java.util.concurrent.Callable<T> call) {
        try {
            return call.call();
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.ANALYTICS_UNAVAILABLE, "Trace 目录查询暂不可用");
        }
    }
}
