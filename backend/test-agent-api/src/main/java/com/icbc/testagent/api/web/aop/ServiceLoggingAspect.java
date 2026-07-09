package com.icbc.testagent.api.web.aop;

import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.observability.TraceLogContext;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service 方法日志切面，统一记录业务服务入口、出口、耗时和错误。
 *
 * <p>该切面只记录参数类型摘要，不记录完整参数和返回体，避免把 prompt、文件内容、token 或大对象写入日志。
 */
@Aspect
@Component
public class ServiceLoggingAspect {

    /**
     * 切入 Spring Service Bean 的 public 方法，覆盖业务模块和 runtime service。
     */
    @Pointcut("execution(public * com.icbc.testagent..*(..)) && @within(org.springframework.stereotype.Service)")
    public void serviceMethods() {
    }

    /**
     * 环绕记录 service 调用。响应式返回值在订阅完成、取消或错误时记录最终状态。
     */
    @Around("serviceMethods()")
    public Object logServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
        Logger logger = loggerFor(joinPoint);
        String traceId = currentTraceId();
        String methodName = methodName(joinPoint);
        String argsSummary = argsSummary(joinPoint.getArgs());
        long startTime = System.currentTimeMillis();

        logger.info("event=service_entry traceId={} method={} args={}", traceId, methodName, argsSummary);
        try {
            Object result = joinPoint.proceed();
            return handleResult(result, logger, traceId, methodName, startTime);
        } catch (Throwable error) {
            logError(logger, traceId, methodName, System.currentTimeMillis() - startTime, error);
            throw error;
        }
    }

    private Object handleResult(
            Object result,
            Logger logger,
            String traceId,
            String methodName,
            long startTime) {
        if (result instanceof Mono<?> mono) {
            return mono
                    .doOnSuccess(value -> logSuccess(logger, traceId, methodName, startTime, "mono"))
                    .doOnError(error -> logError(logger, traceId, methodName,
                            System.currentTimeMillis() - startTime, error));
        }
        if (result instanceof Flux<?> flux) {
            logger.info("event=service_stream_start traceId={} method={}", traceId, methodName);
            return flux.doFinally(signal -> logger.info(
                    "event=service_stream_end traceId={} method={} signal={} durationMs={}",
                    traceId,
                    methodName,
                    signal,
                    System.currentTimeMillis() - startTime));
        }
        logSuccess(logger, traceId, methodName, startTime, result == null ? "void" : "sync");
        return result;
    }

    private void logSuccess(Logger logger, String traceId, String methodName, long startTime, String returnKind) {
        logger.info(
                "event=service_exit traceId={} method={} durationMs={} status=success returnKind={}",
                traceId,
                methodName,
                System.currentTimeMillis() - startTime,
                returnKind);
    }

    private void logError(Logger logger, String traceId, String methodName, long duration, Throwable error) {
        logger.error(
                "event=service_exit traceId={} method={} durationMs={} status=error errorType={} errorCode={} message={}",
                traceId,
                methodName,
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

    String argsSummary(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        return Arrays.stream(args)
                .map(this::argSummary)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String argSummary(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof ServerWebExchange) {
            return "ServerWebExchange";
        }
        if (arg instanceof FilePart filePart) {
            return "FilePart(filename=" + SensitiveDataMasker.truncate(filePart.filename()) + ")";
        }
        if (arg instanceof CharSequence value) {
            return arg.getClass().getSimpleName() + "(length=" + value.length() + ")";
        }
        if (arg instanceof Number || arg instanceof Boolean || arg instanceof Enum<?>) {
            return arg.getClass().getSimpleName() + "(" + SensitiveDataMasker.truncate(String.valueOf(arg)) + ")";
        }
        return arg.getClass().getSimpleName();
    }

    private String currentTraceId() {
        String traceId = TraceLogContext.currentTraceId();
        return traceId != null ? traceId : "unknown";
    }

    private String methodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        return className + "." + signature.getName();
    }

    private Logger loggerFor(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return LoggerFactory.getLogger(signature.getDeclaringType());
    }
}
