package com.enterprise.testagent.api.web.aop;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.observability.TraceIdSupport;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * WebSocket handler 日志切面，补齐非 Controller 前端长连接操作的入口和结束状态。
 */
@Aspect
@Component
public class WebSocketLoggingAspect {

    /**
     * 切入 API 层 WebSocketHandler 的 handle 方法。
     */
    @Pointcut("execution(public reactor.core.publisher.Mono com.enterprise.testagent.api.web..*WebSocketHandler.handle(..))")
    public void webSocketHandleMethods() {
    }

    /**
     * WebSocket 长连接在 Mono 完成、取消或异常时记录结束日志。
     */
    @Around("webSocketHandleMethods()")
    public Object logWebSocket(ProceedingJoinPoint joinPoint) throws Throwable {
        Logger logger = loggerFor(joinPoint);
        WebSocketSession session = extractSession(joinPoint.getArgs());
        String traceId = traceId(session);
        String methodName = methodName(joinPoint);
        String path = path(session);
        long startTime = System.currentTimeMillis();
        logger.info("event=websocket_entry traceId={} method={} path={}", traceId, methodName, path);
        try {
            Object result = joinPoint.proceed();
            if (result instanceof Mono<?> mono) {
                return mono
                        .doOnError(error -> logError(logger, traceId, methodName, path,
                                System.currentTimeMillis() - startTime, error))
                        .doFinally(signal -> {
                            if (signal != SignalType.ON_ERROR) {
                                logger.info("event=websocket_exit traceId={} method={} path={} signal={} durationMs={} status=success",
                                        traceId,
                                        methodName,
                                        path,
                                        signal,
                                        System.currentTimeMillis() - startTime);
                            }
                        });
            }
            logger.info("event=websocket_exit traceId={} method={} path={} signal=sync durationMs={} status=success",
                    traceId,
                    methodName,
                    path,
                    System.currentTimeMillis() - startTime);
            return result;
        } catch (Throwable error) {
            logError(logger, traceId, methodName, path, System.currentTimeMillis() - startTime, error);
            throw error;
        }
    }

    private void logError(Logger logger, String traceId, String methodName, String path, long duration, Throwable error) {
        logger.error("event=websocket_exit traceId={} method={} path={} durationMs={} status=error errorType={} errorCode={} message={}",
                traceId,
                methodName,
                path,
                duration,
                error.getClass().getSimpleName(),
                errorCode(error),
                SensitiveDataMasker.truncate(error.getMessage()),
                error);
    }

    private String errorCode(Throwable error) {
        if (error instanceof PlatformException platformException) {
            return platformException.errorCode().name();
        }
        return "UNKNOWN";
    }

    WebSocketSession extractSession(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof WebSocketSession session) {
                return session;
            }
        }
        return null;
    }

    String path(WebSocketSession session) {
        if (session == null || session.getHandshakeInfo() == null || session.getHandshakeInfo().getUri() == null) {
            return "unknown";
        }
        return session.getHandshakeInfo().getUri().getPath();
    }

    private String traceId(WebSocketSession session) {
        if (session == null || session.getHandshakeInfo() == null) {
            return TraceIdSupport.generate();
        }
        return TraceIdSupport.resolve(session.getHandshakeInfo().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
    }

    private String methodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    private Logger loggerFor(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return LoggerFactory.getLogger(signature.getDeclaringType());
    }
}
