package com.enterprise.testagent.opencode.runtime.internalmodel.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallOutcome;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/** 覆盖失败分类映射全表：连接失败、三种超时、平台异常、未归类。 */
class InternalModelCallOutcomeClassifierTest {

    private final InternalModelCallOutcomeClassifier.TimeoutSignals noSignals =
            new InternalModelCallOutcomeClassifier.TimeoutSignals(false, false, false);
    private final InternalModelCallOutcomeClassifier.TimeoutSignals firstByteOnly =
            new InternalModelCallOutcomeClassifier.TimeoutSignals(true, false, true);
    private final InternalModelCallOutcomeClassifier.TimeoutSignals firstEventReached =
            new InternalModelCallOutcomeClassifier.TimeoutSignals(true, true, true);

    @Test
    void classifiesConnectFailures() {
        assertThat(InternalModelCallOutcomeClassifier.classify(
                webClientError(new ConnectException("connection refused")),
                noSignals)).isEqualTo(InternalModelCallOutcome.UPSTREAM_CONNECT_FAILED);
        assertThat(InternalModelCallOutcomeClassifier.classify(
                webClientError(new UnknownHostException("no such host")),
                noSignals)).isEqualTo(InternalModelCallOutcome.UPSTREAM_CONNECT_FAILED);
        // 非连接类 WebClient 异常不误判为连接失败。
        assertThat(InternalModelCallOutcomeClassifier.classify(
                webClientError(new IOException("read failed")),
                noSignals)).isEqualTo(InternalModelCallOutcome.UNKNOWN_ERROR);
    }

    private WebClientRequestException webClientError(Throwable cause) {
        return new WebClientRequestException(
                cause, HttpMethod.POST, URI.create("http://localhost/probe"), new HttpHeaders());
    }

    @Test
    void classifiesThreeTimeoutKinds() {
        assertThat(InternalModelCallOutcomeClassifier.classify(
                new TimeoutException("first response"), noSignals))
                .isEqualTo(InternalModelCallOutcome.UPSTREAM_FIRST_RESPONSE_TIMEOUT);
        // 首响应已到、SSE 首事件未到 -> 首事件超时。
        assertThat(InternalModelCallOutcomeClassifier.classify(
                new TimeoutException("first event"), firstByteOnly))
                .isEqualTo(InternalModelCallOutcome.UPSTREAM_FIRST_EVENT_TIMEOUT);
        // 首事件已到 -> 流空闲超时。
        assertThat(InternalModelCallOutcomeClassifier.classify(
                new TimeoutException("idle"), firstEventReached))
                .isEqualTo(InternalModelCallOutcome.UPSTREAM_STREAM_IDLE_TIMEOUT);
        // 非流式：首字节已到但无首事件概念 -> 流空闲。
        assertThat(InternalModelCallOutcomeClassifier.classify(
                new TimeoutException("idle"),
                new InternalModelCallOutcomeClassifier.TimeoutSignals(true, false, false)))
                .isEqualTo(InternalModelCallOutcome.UPSTREAM_STREAM_IDLE_TIMEOUT);
    }

    @Test
    void classifiesTimeoutWrappedByBlockingOrNettyClient() {
        assertThat(InternalModelCallOutcomeClassifier.classify(
                new IllegalStateException("blocking timeout", new TimeoutException()), noSignals))
                .isEqualTo(InternalModelCallOutcome.UPSTREAM_FIRST_RESPONSE_TIMEOUT);
        assertThat(InternalModelCallOutcomeClassifier.classify(
                webClientError(io.netty.handler.timeout.ReadTimeoutException.INSTANCE), noSignals))
                .isEqualTo(InternalModelCallOutcome.UPSTREAM_FIRST_RESPONSE_TIMEOUT);
    }

    @Test
    void classifiesPlatformExceptions() {
        assertThat(InternalModelCallOutcomeClassifier.classify(
                new PlatformException(ErrorCode.UNAUTHENTICATED, "proxy auth failed"), noSignals))
                .isEqualTo(InternalModelCallOutcome.PROXY_AUTH_FAILED);
        assertThat(InternalModelCallOutcomeClassifier.classify(
                new PlatformException(ErrorCode.VALIDATION_ERROR, "missing model"), noSignals))
                .isEqualTo(InternalModelCallOutcome.REQUEST_INVALID);
    }

    @Test
    void classifiesMidStreamFailureAfterFirstEvent() {
        // 非 responses 分支：首事件已到但流中途抛非超时异常 -> 流读取失败。
        InternalModelCallOutcomeClassifier.TimeoutSignals midStream =
                new InternalModelCallOutcomeClassifier.TimeoutSignals(true, true, true);
        assertThat(InternalModelCallOutcomeClassifier.classify(
                webClientError(new IOException("read failed")), midStream))
                .isEqualTo(InternalModelCallOutcome.UPSTREAM_STREAM_FAILED);
    }

    @Test
    void fallsBackToUnknownError() {
        assertThat(InternalModelCallOutcomeClassifier.classify(
                new IllegalStateException("boom"), noSignals))
                .isEqualTo(InternalModelCallOutcome.UNKNOWN_ERROR);
        assertThat(InternalModelCallOutcomeClassifier.classify(null, noSignals))
                .isEqualTo(InternalModelCallOutcome.UNKNOWN_ERROR);
    }

    @Test
    void extractsErrorClassWithoutMessage() {
        assertThat(InternalModelCallOutcomeClassifier.errorClass(
                new ConnectException("secret host:secret-path")))
                .isEqualTo("ConnectException");
        assertThat(InternalModelCallOutcomeClassifier.errorClass(null)).isNull();
    }
}
