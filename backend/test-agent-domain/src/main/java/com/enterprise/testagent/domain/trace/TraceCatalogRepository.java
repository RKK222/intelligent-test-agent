package com.enterprise.testagent.domain.trace;

import com.enterprise.testagent.common.pagination.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** ClickHouse Trace 元数据端口；正文归档由运行模块负责，不能经此端口写入。 */
public interface TraceCatalogRepository {

    void save(
            TraceModels.Catalog catalog,
            List<TraceModels.Span> spans,
            List<TraceModels.CapabilityFact> capabilityFacts,
            Instant ingestedAt);

    Optional<TraceModels.Catalog> find(String traceId);

    PageResponse<TraceModels.Catalog> search(TraceModels.Filter filter);

    TraceModels.EventPage events(String traceId, long afterSequence, int limit);

    /** 只返回 DSH 首屏所需的语义 Span，避免把流式 delta 当作时间线记录传给浏览器。 */
    TraceModels.EventPage trajectory(String traceId, long afterSequence, int limit);

    /** 批量返回 Trace 已观测到的 Skill 名称；默认实现兼容未启用 ClickHouse 的仓储。 */
    default Map<String, List<String>> findSkillsByTraceIds(List<String> traceIds) {
        return Map.of();
    }
}
