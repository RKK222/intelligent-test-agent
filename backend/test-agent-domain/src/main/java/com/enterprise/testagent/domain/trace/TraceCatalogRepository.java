package com.enterprise.testagent.domain.trace;

import com.enterprise.testagent.common.pagination.PageResponse;
import java.time.Instant;
import java.util.List;
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
}
