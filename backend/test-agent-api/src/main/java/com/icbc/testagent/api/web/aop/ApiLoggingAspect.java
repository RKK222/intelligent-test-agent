package com.icbc.testagent.api.web.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.observability.TraceLogContext;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * API 日志切面，统一记录所有 Controller 方法的入口和出口日志。
 *
 * <p>日志内容包含：
 * <ul>
 *   <li>traceId：请求追踪 ID，从 MDC 获取</li>
 *   <li>userId：用户 ID，未认证时为 "anonymous"</li>
 *   <li>请求方法、路径、HTTP 方法</li>
 *   <li>请求体和响应体（已脱敏）</li>
 *   <li>执行耗时</li>
 * </ul>
 *
 * <p>支持 WebFlux 响应式返回类型：
 * <ul>
 *   <li>Mono：在订阅完成时记录日志</li>
 *   <li>Flux：记录流开始和结束事件</li>
 *   <li>同步返回：立即记录日志</li>
 * </ul>
 */
@Aspect
@Component
public class ApiLoggingAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiLoggingAspect.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 切入所有 Controller 的 public 方法。
     */
    @Pointcut("execution(public * com.icbc.testagent.api.web..*Controller.*(..))")
    public void controllerMethods() {
    }

    /**
     * 环绕通知，记录 API 入口和出口日志。
     */
    @Around("controllerMethods()")
    public Object logApiCall(ProceedingJoinPoint joinPoint) throws Throwable {
        ServerWebExchange exchange = extractExchange(joinPoint.getArgs());
        String traceId = currentTraceId();
        String userId = extractUserId(exchange);
        String methodName = methodName(joinPoint);
        String httpMethod = httpMethod(exchange);
        String path = requestPath(exchange);
        String clientIp = clientIp(exchange);

        // 记录入口日志
        String requestBody = extractRequestBody(joinPoint.getArgs());
        LOGGER.info("event=api_entry traceId={} userId={} method={} httpMethod={} path={} clientIp={} requestBody={}",
                traceId, userId, methodName, httpMethod, path, clientIp, requestBody);

        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            return handleResult(result, traceId, userId, methodName, startTime);
        } catch (Throwable error) {
            // 异常情况记录错误日志
            long duration = System.currentTimeMillis() - startTime;
            logError(traceId, userId, methodName, duration, error);
            throw error;
        }
    }

    /**
     * 根据返回类型处理日志记录。
     */
    private Object handleResult(Object result, String traceId, String userId, String methodName, long startTime) {
        if (result instanceof Mono<?> mono) {
            return mono
                    .doOnNext(value -> logSuccess(traceId, userId, methodName, startTime, value))
                    .doOnError(error -> logError(traceId, userId, methodName,
                            System.currentTimeMillis() - startTime, error));
        } else if (result instanceof Flux<?> flux) {
            // SSE 流式接口，记录流开始事件
            LOGGER.info("event=api_stream_start traceId={} userId={} method={}", traceId, userId, methodName);
            return flux.doFinally(signal ->
                    LOGGER.info("event=api_stream_end traceId={} userId={} method={} signal={} durationMs={}",
                            traceId, userId, methodName, signal, System.currentTimeMillis() - startTime));
        } else {
            // 同步返回
            logSuccess(traceId, userId, methodName, startTime, result);
            return result;
        }
    }

    /**
     * 记录成功出口日志。
     */
    private void logSuccess(String traceId, String userId, String methodName, long startTime, Object result) {
        long duration = System.currentTimeMillis() - startTime;
        String responseBody = serializeResponse(result);
        LOGGER.info("event=api_exit traceId={} userId={} method={} durationMs={} status=success responseBody={}",
                traceId, userId, methodName, duration, responseBody);
    }

    /**
     * 记录错误出口日志。
     */
    private void logError(String traceId, String userId, String methodName, long duration, Throwable error) {
        String errorType = error.getClass().getSimpleName();
        String errorCode = extractErrorCode(error);
        String errorMessage = error.getMessage();

        LOGGER.warn("event=api_exit traceId={} userId={} method={} durationMs={} status=error errorType={} errorCode={} message={}",
                traceId, userId, methodName, duration, errorType, errorCode, errorMessage);
    }

    /**
     * 从异常中提取错误码。
     */
    private String extractErrorCode(Throwable error) {
        if (error instanceof PlatformException platformException) {
            return platformException.errorCode().name();
        }
        return "UNKNOWN";
    }

    /**
     * 从方法参数中提取 ServerWebExchange。
     */
    private ServerWebExchange extractExchange(Object[] args) {
        if (args == null) return null;
        return Arrays.stream(args)
                .filter(ServerWebExchange.class::isInstance)
                .map(ServerWebExchange.class::cast)
                .findFirst()
                .orElse(null);
    }

    /**
     * 从参数中提取请求体（仅提取 @RequestBody 标注的参数）。
     */
    private String extractRequestBody(Object[] args) {
        if (args == null) return "";

        // 尝试序列化第一个非 ServerWebExchange、非 FilePart 的参数
        for (Object arg : args) {
            if (arg == null) continue;
            if (arg instanceof ServerWebExchange) continue;
            if (arg instanceof FilePart) {
                return "[file:" + ((FilePart) arg).filename() + "]";
            }
            try {
                String json = OBJECT_MAPPER.writeValueAsString(arg);
                return SensitiveDataMasker.mask(json);
            } catch (Exception e) {
                // 序列化失败，返回类型名
                return "[" + arg.getClass().getSimpleName() + "]";
            }
        }
        return "";
    }

    /**
     * 序列化响应对象。
     */
    private String serializeResponse(Object result) {
        if (result == null) return "null";
        try {
            String json = OBJECT_MAPPER.writeValueAsString(result);
            return SensitiveDataMasker.mask(json);
        } catch (Exception e) {
            return "[" + result.getClass().getSimpleName() + "]";
        }
    }

    /**
     * 获取当前 traceId，优先从 MDC 获取。
     */
    private String currentTraceId() {
        String traceId = TraceLogContext.currentTraceId();
        return traceId != null ? traceId : "unknown";
    }

    /**
     * 提取用户 ID，未认证时返回 "anonymous"。
     */
    private String extractUserId(ServerWebExchange exchange) {
        if (exchange == null) return "anonymous";
        return AuthWebSupport.getOptionalAuthPrincipal(exchange)
                .map(p -> p.userId().value())
                .orElse("anonymous");
    }

    /**
     * 获取方法名（类名.方法名格式）。
     */
    private String methodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        return className + "." + signature.getName();
    }

    /**
     * 获取 HTTP 方法名。
     */
    private String httpMethod(ServerWebExchange exchange) {
        if (exchange == null) return "UNKNOWN";
        return exchange.getRequest().getMethod().name();
    }

    /**
     * 获取请求路径。
     */
    private String requestPath(ServerWebExchange exchange) {
        if (exchange == null) return "unknown";
        return exchange.getRequest().getPath().value();
    }

    /**
     * 获取客户端 IP。
     */
    private String clientIp(ServerWebExchange exchange) {
        if (exchange == null) return "unknown";
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getHostString()
                : "unknown";
    }
}
