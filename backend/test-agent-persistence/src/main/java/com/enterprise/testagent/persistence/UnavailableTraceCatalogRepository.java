package com.enterprise.testagent.persistence;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.trace.TraceCatalogRepository;
import com.enterprise.testagent.domain.trace.TraceModels;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** ClickHouse 未启用时显式关闭 Trace 目录，不把正文或目录降级写入 PostgreSQL。 */
@Repository
@ConditionalOnProperty(name = "test-agent.analytics.clickhouse.enabled", havingValue = "false", matchIfMissing = true)
public class UnavailableTraceCatalogRepository implements TraceCatalogRepository {

    @Override
    public void save(TraceModels.Catalog catalog, List<TraceModels.Span> spans,
                     List<TraceModels.CapabilityFact> capabilityFacts, Instant ingestedAt) {
        throw unavailable();
    }

    @Override
    public Optional<TraceModels.Catalog> find(String traceId) {
        throw unavailable();
    }

    @Override
    public PageResponse<TraceModels.Catalog> search(TraceModels.Filter filter) {
        throw unavailable();
    }

    @Override
    public TraceModels.EventPage events(String traceId, long afterSequence, int limit) {
        throw unavailable();
    }

    private PlatformException unavailable() {
        return new PlatformException(ErrorCode.ANALYTICS_UNAVAILABLE, "ClickHouse Trace 目录未启用");
    }
}
