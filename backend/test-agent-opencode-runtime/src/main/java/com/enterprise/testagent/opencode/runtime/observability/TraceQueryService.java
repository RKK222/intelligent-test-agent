package com.enterprise.testagent.opencode.runtime.observability;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.trace.TraceCatalogRepository;
import com.enterprise.testagent.domain.trace.TraceModels;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** 超级管理员 Trace 目录查询；正文读取仍由归档节点上的 TraceArchiveService 执行。 */
@Service
public class TraceQueryService {

    private final TraceCatalogRepository repository;

    public TraceQueryService(TraceCatalogRepository repository) {
        this.repository = repository;
    }

    public PageResponse<TraceModels.Catalog> search(
            Instant startTime,
            Instant endTime,
            String user,
            String organization,
            String agentId,
            String skill,
            String tool,
            String status,
            String traceId,
            String runId,
            Integer page,
            Integer pageSize) {
        Instant end = endTime == null ? Instant.now() : endTime;
        Instant start = startTime == null ? end.minusSeconds(7 * 86_400L) : startTime;
        if (!start.isBefore(end)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Trace 查询开始时间必须早于结束时间");
        }
        int resolvedPage = page == null ? 1 : page;
        int resolvedSize = pageSize == null ? 30 : pageSize;
        if (resolvedPage < 1 || resolvedSize < 1 || resolvedSize > 200) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Trace 分页参数无效");
        }
        return repository.search(new TraceModels.Filter(
                start, end, clean(user), clean(organization), clean(agentId), clean(skill), clean(tool),
                upper(status), clean(traceId), clean(runId), resolvedPage, resolvedSize));
    }

    public TraceModels.Catalog require(String traceId) {
        return repository.find(traceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Trace 不存在"));
    }

    /** ClickHouse 语义 Span 首屏；正文仍只允许从冻结归档节点按需读取。 */
    public TraceModels.EventPage trajectory(String traceId, long afterSequence, int limit) {
        require(traceId);
        if (afterSequence < 0 || limit < 1 || limit > 500) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Trace Span 分页参数无效");
        }
        return repository.trajectory(traceId, afterSequence, limit);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upper(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase(java.util.Locale.ROOT);
    }
}
