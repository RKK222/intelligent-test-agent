package com.enterprise.testagent.api.web.aop;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.observability.TraceLogContext;
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
 * Service 方法日志切面，仅在抛出异常时记录方法、参数摘要、耗时和错误，避免对正常调用产生日志噪声。
 *
 * <p>该切面只记录参数类型摘要，不记录完整参数和返回体，避免把 prompt、文件内容、token 或大对象写入日志。
 */
@Aspect
@Component
public class ServiceLoggingAspect {

    /**
     * 切入 Spring Service Bean 的 public 方法，覆盖业务模块和 runtime service。
     */
    @Pointcut("execution(public * com.enterprise.testagent..*(..)) && @within(org.springframework.stereotype.Service)")
    public void serviceMethods() {
    }

    /**
     * 环绕 service 调用；正常返回保持静默，仅在抛出异常时记录方法、参数摘要、耗时和错误。
     * 响应式返回值在错误信号到达订阅者时记录，原异常继续向上游传播。
     */
    @Around("serviceMethods()")
    public Object logServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
        Logger logger = loggerFor(joinPoint);
        String traceId = currentTraceId();
        String methodName = methodName(joinPoint);
        Object[] args = joinPoint.getArgs();
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            if (result instanceof Mono<?> mono) {
                return mono.doOnError(error -> logError(logger, traceId, methodName, args, startTime, error));
            }
            if (result instanceof Flux<?> flux) {
                return flux.doOnError(error -> logError(logger, traceId, methodName, args, startTime, error));
            }
            return result;
        } catch (Throwable error) {
            logError(logger, traceId, methodName, args, startTime, error);
            throw error;
        }
    }

    /**
     * 异常时记录 ERROR 日志；参数摘要只含类型和轻量值，不记录完整参数与返回体。
     */
    private void logError(
            Logger logger,
            String traceId,
            String methodName,
            Object[] args,
            long startTime,
            Throwable error) {
        logger.error(
                "event=service_exit traceId={} method={} durationMs={} status=error args={} errorType={} errorCode={} message={}",
                traceId,
                methodName,
                System.currentTimeMillis() - startTime,
                argsSummary(args),
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
