package com.enterprise.testagent.opencode.runtime.observability;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.trace.TraceCatalogRepository;
import com.enterprise.testagent.domain.trace.TraceModels;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 超级管理员 Trace 目录查询；正文读取仍由归档节点上的 TraceArchiveService 执行。 */
@Service
public class TraceQueryService {

    private final TraceCatalogRepository repository;
    private final RunRepository runRepository;

    public TraceQueryService(TraceCatalogRepository repository) {
        this(repository, null);
    }

    /** 生产查询同时读取 Run 的两项能力快照，ClickHouse 不复制该关系型事实。 */
    @Autowired
    public TraceQueryService(TraceCatalogRepository repository, RunRepository runRepository) {
        this.repository = repository;
        this.runRepository = runRepository;
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
        PageResponse<TraceModels.Catalog> result = repository.search(new TraceModels.Filter(
                start, end, clean(user), clean(organization), clean(agentId), clean(skill), clean(tool),
                upper(status), clean(traceId), clean(runId), resolvedPage, resolvedSize));
        Map<String, Run> runs = runsById(result.items());
        Map<String, java.util.List<String>> skills = skillsByTraceId(result.items());
        return new PageResponse<>(
                result.items().stream()
                        .map(catalog -> enrich(catalog, runs.get(catalog.runId()), skills.get(catalog.traceId())))
                        .toList(),
                result.page(), result.size(), result.total());
    }

    public TraceModels.Catalog require(String traceId) {
        TraceModels.Catalog catalog = repository.find(traceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Trace 不存在"));
        Run run = runById(catalog.runId());
        java.util.List<String> skills = skillsByTraceId(java.util.List.of(catalog))
                .getOrDefault(traceId, java.util.List.of());
        return enrich(catalog, run, skills);
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

    private Map<String, Run> runsById(java.util.List<TraceModels.Catalog> catalogs) {
        if (runRepository == null) {
            return Map.of();
        }
        java.util.List<RunId> ids = catalogs.stream()
                .map(TraceModels.Catalog::runId)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .map(this::runId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return runRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(run -> run.runId().value(), Function.identity()));
    }

    private Run runById(String value) {
        RunId runId = runId(value);
        return runRepository == null || runId == null ? null : runRepository.findById(runId).orElse(null);
    }

    private RunId runId(String value) {
        try {
            return value == null || value.isBlank() ? null : new RunId(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Map<String, java.util.List<String>> skillsByTraceId(java.util.List<TraceModels.Catalog> catalogs) {
        java.util.List<String> traceIds = catalogs.stream()
                .map(TraceModels.Catalog::traceId)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        Map<String, java.util.List<String>> skills = repository.findSkillsByTraceIds(traceIds);
        return skills == null ? Map.of() : skills;
    }

    private TraceModels.Catalog enrich(TraceModels.Catalog catalog, Run run, java.util.List<String> skills) {
        TraceModels.Catalog enriched = catalog.withSkills(skills);
        return run == null
                ? enriched
                : enriched.withRuntimeFeatureSnapshot(run.rtkEnabled(), run.conciseOutputSelected());
    }
}
