package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.analytics.AnalyticsModels;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.opencode.runtime.analytics.AnalyticsQueryService;
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

    public AnalyticsController(AnalyticsQueryService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<AnalyticsModels.Overview> overview(QueryParams params, ServerWebExchange exchange) {
        requireSuperAdmin(exchange);
        return ApiResponse.ok(service.overview(filter(params)), RuntimeApiSupport.traceId(exchange));
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
                params.userId(),
                params.agentId(),
                params.model(),
                params.workspaceId(),
                params.topN(),
                params.page(),
                params.pageSize(),
                params.sort());
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
