package com.enterprise.testagent.opencode.runtime.internalmodel.observability;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;

/**
 * 将转发链路异常与信号映射为观测结果分类。
 *
 * <p>只消费异常类型与低基数信号，绝不读取异常 message（message 可能携带 URL 等不稳定内容），
 * 符合安全脱敏边界：观测只保留稳定错误信息。</p>
 */
public final class InternalModelCallOutcomeClassifier {

    private InternalModelCallOutcomeClassifier() {
    }

    /** 转发链路上可区分三种超时的信号上下文。 */
    public record TimeoutSignals(boolean firstByteMarked, boolean firstEventMarked, boolean streaming) {
    }

    /**
     * 将 doOnError 收到的异常分类。PROXY_AUTH_FAILED 与 PROVIDER_UNAVAILABLE 属请求前同步阶段，
     * 由调用方直接构造 outcome，不经过本方法。
     */
    public static InternalModelCallOutcome classify(Throwable error, TimeoutSignals signals) {
        if (error == null) {
            return InternalModelCallOutcome.UNKNOWN_ERROR;
        }
        Throwable unwrapped = Exceptions.unwrap(error);
        if (unwrapped instanceof PlatformException platformException) {
            return classifyPlatformException(platformException);
        }
        if (unwrapped instanceof WebClientRequestException) {
            Throwable cause = rootCause(unwrapped);
            if (isConnectFailure(cause)) {
                return InternalModelCallOutcome.UPSTREAM_CONNECT_FAILED;
            }
            // 连接类之外落到通用流中途失败判定。
            return classifyStreamOrUnknown(unwrapped, signals);
        }
        if (unwrapped instanceof TimeoutException) {
            return classifyTimeout(signals);
        }
        return classifyStreamOrUnknown(unwrapped, signals);
    }

    /**
     * 非连接、非超时的响应体/流中途错误：首事件已到且处于流式转发则归为流读取失败，
     * 否则未知。响应体中途异常（如连接被上游掐断、解码失败）通常不是 WebClientRequestException。
     */
    private static InternalModelCallOutcome classifyStreamOrUnknown(Throwable unwrapped, TimeoutSignals signals) {
        if (signals != null && signals.streaming() && signals.firstEventMarked()) {
            return InternalModelCallOutcome.UPSTREAM_STREAM_FAILED;
        }
        return InternalModelCallOutcome.UNKNOWN_ERROR;
    }

    private static InternalModelCallOutcome classifyPlatformException(PlatformException exception) {
        if (exception.errorCode() == ErrorCode.UNAUTHENTICATED) {
            return InternalModelCallOutcome.PROXY_AUTH_FAILED;
        }
        return InternalModelCallOutcome.REQUEST_INVALID;
    }

    private static InternalModelCallOutcome classifyTimeout(TimeoutSignals signals) {
        if (signals == null) {
            return InternalModelCallOutcome.UPSTREAM_FIRST_RESPONSE_TIMEOUT;
        }
        if (!signals.firstByteMarked()) {
            return InternalModelCallOutcome.UPSTREAM_FIRST_RESPONSE_TIMEOUT;
        }
        if (signals.streaming() && !signals.firstEventMarked()) {
            return InternalModelCallOutcome.UPSTREAM_FIRST_EVENT_TIMEOUT;
        }
        return InternalModelCallOutcome.UPSTREAM_STREAM_IDLE_TIMEOUT;
    }

    /** 剥离 Reactor 包装后的异常类简名；不存 message。 */
    public static String errorClass(Throwable error) {
        if (error == null) {
            return null;
        }
        Throwable unwrapped = Exceptions.unwrap(error);
        String simpleName = unwrapped.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? null : simpleName;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean isConnectFailure(Throwable cause) {
        if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
            return true;
        }
        // Reactor Netty 连接超时异常类名稳定：ConnectTimeoutException。
        return cause != null && cause.getClass().getSimpleName().equals("ConnectTimeoutException");
    }
}
