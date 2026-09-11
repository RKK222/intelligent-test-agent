package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.opencode.runtime.analytics.AnalyticsQueryService;
import com.enterprise.testagent.opencode.runtime.analytics.AnalyticsSessionUsageQueryService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 运营分析 HTTP 入口，P0 仅允许 SUPER_ADMIN 查看全平台看板和导出。
 */
@RestController
@RequestMapping("/api/internal/platform/analytics")
public class AnalyticsController {

    private final AnalyticsQueryService service;
    private final AnalyticsSessionUsageQueryService sessionUsageService;

    public AnalyticsController(
            AnalyticsQueryService service,
            AnalyticsSessionUsageQueryService sessionUsageService) {
        this.service = service;
        this.sessionUsageService = sessionUsageService;
    }

    @GetMapping("/overview")
    public ApiResponse<AnalyticsModels.Overview> overview(QueryParams params, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(service.overview(filter(params)), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/filter-options")
    public ApiResponse<AnalyticsModels.FilterOptions> filterOptions(QueryParams params, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(service.filterOptions(filter(params)), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/funnel")
    public ApiResponse<AnalyticsModels.Funnel> funnel(QueryParams params, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(service.funnel(filter(params)), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/hourly-heatmap")
    public ApiResponse<AnalyticsModels.HourlyHeatmap> hourlyHeatmap(
            QueryParams params,
            @RequestParam(required = false, defaultValue = "USER_MESSAGES") String metric,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(
                service.hourlyHeatmap(filter(params), heatmapMetric(metric)),
                RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/token-operations")
    public ApiResponse<AnalyticsModels.TokenOperations> tokenOperations(
            QueryParams params,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(service.tokenOperations(filter(params)), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/capabilities")
    public ApiResponse<AnalyticsModels.Capabilities> capabilities(QueryParams params, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(service.capabilities(filter(params)), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/timeseries")
    public ApiResponse<Object> timeseries(QueryParams params, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(service.timeseries(filter(params)), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/peaks")
    public ApiResponse<AnalyticsModels.Peaks> peaks(QueryParams params, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(service.peaks(filter(params)), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/users")
    public ApiResponse<Object> users(QueryParams params, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(service.users(filter(params)), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/organizations")
    public ApiResponse<Object> organizations(
            QueryParams params,
            @RequestParam(required = false, defaultValue = "organization") String groupBy,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(service.organizations(filter(params), groupBy), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/satisfaction")
    public ApiResponse<AnalyticsModels.Satisfaction> satisfaction(QueryParams params, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(service.satisfaction(filter(params)), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/exceptions")
    public ApiResponse<Object> exceptions(QueryParams params, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(service.exceptionDetails(filter(params)), RuntimeApiSupport.traceId(exchange));
    }

    /**
     * 用户×会话发送次数统计；口径依赖业务库的存储模式/来源/归属，直连平台 PostgreSQL，
     * 与其它只读 ClickHouse 的运营分析端点不同。
     */
    @GetMapping("/sessions")
    public ApiResponse<Object> sessions(QueryParams params, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(
                sessionUsageService.sessionMessageUsage(filter(params)),
                RuntimeApiSupport.traceId(exchange));
    }

    /** 用户维度汇总：同一筛选下按用户汇总参与对话数、发送总次数与首末发送时间。 */
    @GetMapping("/sessions/summary")
    public ApiResponse<Object> sessionSummary(QueryParams params, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(
                sessionUsageService.sessionMessageSummary(filter(params)),
                RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            QueryParams params,
            @RequestParam(required = false, defaultValue = "overview") String type,
            ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        byte[] body = service.exportCsvBytes(filter(params), type);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("analytics-" + type + ".csv")
                        .build()
                        .toString())
                .body(body);
    }

    /**
     * 一次导出所有运营分析 Tab 为多 Sheet xlsx，Sheet 名和列头用中文且与网页表格一致。
     */
    @GetMapping(value = "/export-all", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportAll(QueryParams params, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        byte[] body = service.exportAllXlsx(filter(params));
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("analytics-export.xlsx")
                        .build()
                        .toString())
                .body(body);
    }

    private void requireSuperAdmin(ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
    }

    private AnalyticsModels.Filter filter(QueryParams params) {
        return service.filter(
                parseInstant(params.startTime()),
                parseInstant(params.endTime()),
                params.granularity(),
                params.organization(),
                params.rdDepartment(),
                params.department(),
                firstNonBlank(params.user(), params.userId()),
                params.agentId(),
                params.model(),
                params.workspaceId(),
                params.topN(),
                params.page(),
                params.pageSize(),
                params.sort());
    }

    private AnalyticsModels.HeatmapMetric heatmapMetric(String value) {
        try {
            return AnalyticsModels.HeatmapMetric.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "metric 必须是 USER_MESSAGES、PRIMARY_TOKENS 或 CACHE_TOKENS");
        }
    }

    private String firstNonBlank(String preferred, String legacy) {
        return preferred != null && !preferred.isBlank() ? preferred : legacy;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "时间参数必须是 ISO-8601 Instant 格式",
                    Map.of("value", value),
                    exception);
        }
    }

    /**
     * Spring MVC 自动绑定 analytics 通用筛选参数。
     */
    public record QueryParams(
            String startTime,
            String endTime,
            String granularity,
            String organization,
            String rdDepartment,
            String department,
            String user,
            String userId,
            String agentId,
            String model,
            String workspaceId,
            Integer topN,
            Integer page,
            Integer pageSize,
            String cursor,
            String sort) {
    }
}
