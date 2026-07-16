package com.enterprise.testagent.api.web.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.observability.TraceLogContext;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 切入所有 Controller 的 public 方法。
     */
    @Pointcut("execution(public * com.enterprise.testagent.api.web..*Controller.*(..))")
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
        org.slf4j.Logger logger = loggerFor(joinPoint);

        // 前端每次 HTTP 操作进入 Controller 时记录一次入口，出口在响应完成后记录。
        String requestBody = extractRequestBody(joinPoint.getArgs());
        logger.info("event=api_entry traceId={} userId={} method={} httpMethod={} path={} clientIp={} requestBody={}",
                traceId, userId, methodName, httpMethod, path, clientIp, requestBody);

        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            return handleResult(result, logger, traceId, userId, methodName, httpMethod, path, startTime);
        } catch (Throwable error) {
            long duration = System.currentTimeMillis() - startTime;
            logError(logger, traceId, userId, methodName, httpMethod, path, duration, error);
            throw error;
        }
    }

    /**
     * 根据返回类型处理日志记录。
     */
    private Object handleResult(
            Object result,
            org.slf4j.Logger logger,
            String traceId,
            String userId,
            String methodName,
            String httpMethod,
            String path,
            long startTime) {
        if (result instanceof Mono<?> mono) {
            return mono
                    .doOnSuccess(value -> logSuccess(logger, traceId, userId, methodName, httpMethod, path, startTime, value))
                    .doOnError(error -> logError(logger, traceId, userId, methodName, httpMethod, path,
                            System.currentTimeMillis() - startTime, error));
        } else if (result instanceof Flux<?> flux) {
            logger.info("event=api_stream_start traceId={} userId={} method={} httpMethod={} path={}",
                    traceId, userId, methodName, httpMethod, path);
            return flux.doFinally(signal ->
                    logger.info("event=api_stream_end traceId={} userId={} method={} httpMethod={} path={} signal={} durationMs={}",
                            traceId, userId, methodName, httpMethod, path, signal, System.currentTimeMillis() - startTime));
        } else {
            logSuccess(logger, traceId, userId, methodName, httpMethod, path, startTime, result);
            return result;
        }
    }

    /**
     * 记录成功出口日志。
     */
    private void logSuccess(
            org.slf4j.Logger logger,
            String traceId,
            String userId,
            String methodName,
            String httpMethod,
            String path,
            long startTime,
            Object result) {
        long duration = System.currentTimeMillis() - startTime;
        String responseBody = serializeResponse(result);
        logger.info("event=api_exit traceId={} userId={} method={} httpMethod={} path={} durationMs={} status=success responseBody={}",
                traceId, userId, methodName, httpMethod, path, duration, responseBody);
    }

    /**
     * 记录错误出口日志。
     */
    private void logError(
            org.slf4j.Logger logger,
            String traceId,
            String userId,
            String methodName,
            String httpMethod,
            String path,
            long duration,
            Throwable error) {
        String errorType = error.getClass().getSimpleName();
        String errorCode = extractErrorCode(error);
        String errorMessage = error.getMessage();

        logger.error("event=api_exit traceId={} userId={} method={} httpMethod={} path={} durationMs={} status=error errorType={} errorCode={} message={}",
                traceId, userId, methodName, httpMethod, path, duration, errorType, errorCode, errorMessage, error);
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
    ServerWebExchange extractExchange(Object[] args) {
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
    String extractRequestBody(Object[] args) {
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
    String extractUserId(ServerWebExchange exchange) {
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
     * 使用目标 Controller 的 logger，便于 Log4j2 按具体 Controller 包名分流 SSE 日志。
     */
    private org.slf4j.Logger loggerFor(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return LoggerFactory.getLogger(signature.getDeclaringType());
    }

    /**
     * 获取 HTTP 方法名。
     */
    String httpMethod(ServerWebExchange exchange) {
        if (exchange == null) return "UNKNOWN";
        return exchange.getRequest().getMethod().name();
    }

    /**
     * 获取请求路径。
     */
    String requestPath(ServerWebExchange exchange) {
        if (exchange == null) return "unknown";
        return exchange.getRequest().getPath().value();
    }

    /**
     * 获取客户端 IP。
     */
    String clientIp(ServerWebExchange exchange) {
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
