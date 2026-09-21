package com.enterprise.testagent.opencode.runtime.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.trace.TraceCatalogRepository;
import com.enterprise.testagent.domain.trace.TraceModels;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TraceQueryServiceTest {

    @Test
    void searchEnrichesRunFeatureSnapshotAndDeduplicatedSkills() {
        TraceCatalogRepository repository = mock(TraceCatalogRepository.class);
        RunRepository runRepository = mock(RunRepository.class);
        TraceModels.Catalog catalog = catalog();
        Run run = new Run(
                new RunId(catalog.runId()),
                new SessionId("ses_trace_query"),
                new WorkspaceId("wrk_trace_query"),
                RunStatus.SUCCEEDED,
                Instant.parse("2026-09-21T00:00:00Z"),
                Instant.parse("2026-09-21T00:01:00Z"),
                catalog.traceId()).withRuntimeFeatureSnapshot(true, true);
        when(repository.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResponse<>(List.of(catalog), 1, 30, 1));
        when(repository.findSkillsByTraceIds(List.of(catalog.traceId())))
                .thenReturn(Map.of(catalog.traceId(), List.of("concise-output", "test-design")));
        when(runRepository.findByIds(List.of(run.runId()))).thenReturn(List.of(run));

        TraceModels.Catalog enriched = new TraceQueryService(repository, runRepository)
                .search(null, null, null, null, null, null, null, null, null, null, 1, 30)
                .items()
                .getFirst();

        assertThat(enriched.rtkEnabled()).isTrue();
        assertThat(enriched.conciseOutputSelected()).isTrue();
        assertThat(enriched.skills()).containsExactly("concise-output", "test-design");
    }

    private TraceModels.Catalog catalog() {
        Instant now = Instant.parse("2026-09-21T00:00:00Z");
        return new TraceModels.Catalog(
                "trc_1234567890abcdef1234567890abcdef",
                "usr_trace_query",
                "trace-user",
                "研发中心",
                "质量部",
                "智能测试",
                "SERVER_PROCESS",
                "OPENCODE_PLUGIN",
                "opc_trace_query",
                null,
                "backend-trace-query",
                "linux-trace-query",
                "remote-trace-query",
                "run_trace_query_1234567890",
                "test-design-agent",
                "COMPLETED",
                "ARCHIVED",
                now,
                now.plusSeconds(60),
                now,
                8,
                8,
                1024,
                0,
                0,
                true,
                true);
    }
}
