package com.enterprise.testagent.persistence.clickhouse;

import com.enterprise.testagent.domain.trace.TraceModels;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** Trace 目录、时间线索引和插件 Capability 事实的 ClickHouse mapper。 */
public interface ClickHouseTraceCatalogMapper {

    int insertCatalog(@Param("catalog") TraceModels.Catalog catalog, @Param("version") long version);

    int insertSpans(@Param("spans") List<TraceModels.Span> spans);

    int insertCapabilityFacts(
            @Param("facts") List<TraceModels.CapabilityFact> facts,
            @Param("ingestedAt") Instant ingestedAt);

    TraceModels.Catalog find(@Param("traceId") String traceId);

    List<TraceModels.Catalog> search(
            @Param("filter") TraceModels.Filter filter,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long count(@Param("filter") TraceModels.Filter filter);

    List<TraceModels.Span> events(
            @Param("traceId") String traceId,
            @Param("afterSequence") long afterSequence,
            @Param("limit") int limit);

    long countEvents(@Param("traceId") String traceId);
}
