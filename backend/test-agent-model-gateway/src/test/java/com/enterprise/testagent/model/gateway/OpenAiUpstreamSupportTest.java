package com.enterprise.testagent.model.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.observability.TraceConstants;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class OpenAiUpstreamSupportTest {

    @Test
    void injectsOnlyTrustedIdentityAndBuildsNormalizedTarget() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("browser-token");
        headers.set(OpenAiUpstreamSupport.AUTH_TOKEN_HEADER, "browser-auth-token");
        headers.set(OpenAiUpstreamSupport.PROVIDER_HEADER, "client-provider");
        headers.set(OpenAiUpstreamSupport.UCID_HEADER, "client-ucid");

        OpenAiUpstreamSupport.applyTrustedRequestHeaders(
                headers,
                "provider-token",
                "AUTH_001",
                "trace_gateway",
                MediaType.APPLICATION_JSON,
                List.of(MediaType.TEXT_EVENT_STREAM));

        // 外部 OpenAI 兼容 API 走 Authorization: Bearer，企业网关走 Auth-Token，两者共存。
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer provider-token");
        assertThat(headers.getFirst(OpenAiUpstreamSupport.AUTH_TOKEN_HEADER)).isEqualTo("provider-token");
        assertThat(headers.getFirst(OpenAiUpstreamSupport.PROVIDER_HEADER)).isNull();
        assertThat(headers.getFirst(OpenAiUpstreamSupport.UCID_HEADER)).isEqualTo("AUTH_001");
        assertThat(headers.getFirst(TraceConstants.TRACE_ID_HEADER)).isEqualTo("trace_gateway");
        assertThat(headers.getAccept()).containsExactly(MediaType.TEXT_EVENT_STREAM);
        assertThat(OpenAiUpstreamSupport.targetUrl(
                "http://models.internal/v1/", "chat/completions", "stream=true"))
                .isEqualTo("http://models.internal/v1/chat/completions?stream=true");
    }

    @Test
    void copiesOnlyAllowlistedResponseHeaders() {
        HttpHeaders source = new HttpHeaders();
        source.setContentType(MediaType.APPLICATION_JSON);
        source.set(HttpHeaders.CONTENT_ENCODING, "gzip");
        source.set(HttpHeaders.RETRY_AFTER, "2");
        source.set(HttpHeaders.SET_COOKIE, "must-not-forward=1");
        source.set(TraceConstants.TRACE_ID_HEADER, "trace_upstream");
        HttpHeaders target = new HttpHeaders();

        OpenAiUpstreamSupport.copySafeResponseHeaders(target, source, false);

        assertThat(target.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(target.getFirst(HttpHeaders.CONTENT_ENCODING)).isNull();
        assertThat(target.getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
        assertThat(target.getFirst(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(target.getFirst(TraceConstants.TRACE_ID_HEADER)).isEqualTo("trace_upstream");
    }
}
