package com.icbc.testagent.api.web.common;

import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.observability.TraceIdSupport;
import java.util.Map;
import java.util.function.Function;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Runtime API 入口层共享工具，只处理 HTTP 边界转换，不承载业务规则。
 */
public final class RuntimeApiSupport {

    /**
     * 工具类不允许实例化，避免在 Controller 中持有状态。
     */
    private RuntimeApiSupport() {
    }

    /**
     * 优先使用 TraceIdWebFilter 已解析的 traceId，缺失时从请求头重新生成或规范化。
     */
    public static String traceId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(TraceConstants.TRACE_ID_ATTRIBUTE);
        if (attribute instanceof String traceId && TraceIdSupport.isValid(traceId)) {
            return traceId;
        }
        return TraceIdSupport.resolve(exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
    }

    /**
     * 统一分页默认值与校验错误格式，避免各 Controller 重复处理 page/size。
     */
    public static PageRequest pageRequest(Integer page, Integer size) {
        try {
            return new PageRequest(page == null ? 1 : page, size == null ? 50 : size);
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("page", page == null ? 1 : page, "size", size == null ? 50 : size),
                    exception);
        }
    }

    /**
     * 统一封装可能阻塞的运行态代理响应，保持 traceId 和 ApiResponse 契约一致。
     */
    public static Mono<ApiResponse<Object>> blockingObjectResponse(
            ServerWebExchange exchange,
            Function<String, Object> action) {
        String traceId = traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(action.apply(traceId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
