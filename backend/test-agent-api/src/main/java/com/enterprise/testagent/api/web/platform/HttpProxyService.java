package com.enterprise.testagent.api.web.platform;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class HttpProxyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProxyService.class);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofMillis(10000);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofMillis(30000);

    private final RestTemplate restTemplate;

    public HttpProxyService() {
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate() {
        return new RestTemplate();
    }

    public ProxyResponse execute(ProxyRequest request) {
        String uri = request.uri();
        String method = request.method();
        Map<String, String> queryParams = request.queryParams();
        Map<String, String> headers = request.headers();
        String body = request.body();
        int connectTimeout = request.connectTimeout() != null ? request.connectTimeout() : (int) DEFAULT_CONNECT_TIMEOUT.toMillis();
        int readTimeout = request.readTimeout() != null ? request.readTimeout() : (int) DEFAULT_READ_TIMEOUT.toMillis();

        try {
            URI targetUri = buildUri(uri, queryParams);
            HttpHeaders httpHeaders = buildHeaders(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    targetUri,
                    HttpMethod.valueOf(method),
                    buildRequestEntity(body, httpHeaders),
                    String.class
            );

            Map<String, String> responseHeaders = extractResponseHeaders(response.getHeaders());

            LOGGER.info("HTTP proxy call success: {} {} -> {}", method, uri, response.getStatusCode());

            return new ProxyResponse(
                    response.getStatusCode().value(),
                    responseHeaders,
                    response.getBody()
            );
        } catch (Exception e) {
            LOGGER.error("HTTP proxy call failed: {} {}, error={}", method, uri, e.getMessage(), e);
            throw new RuntimeException("HTTP proxy call failed: " + e.getMessage(), e);
        }
    }

    private URI buildUri(String uri, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(uri);
        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(builder::queryParam);
        }
        return builder.build().toUri();
    }

    private HttpHeaders buildHeaders(Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        if (headers != null) {
            headers.forEach(httpHeaders::set);
        }
        return httpHeaders;
    }

    private org.springframework.http.HttpEntity<String> buildRequestEntity(String body, HttpHeaders headers) {
        return new org.springframework.http.HttpEntity<>(body, headers);
    }

    private Map<String, String> extractResponseHeaders(HttpHeaders headers) {
        Map<String, String> result = new HashMap<>();
        headers.forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                result.put(key, String.join(", ", values));
            }
        });
        return result;
    }

    public record ProxyRequest(
            String uri,
            String method,
            Map<String, String> queryParams,
            Map<String, String> headers,
            String body,
            Integer connectTimeout,
            Integer readTimeout
    ) {}

    public record ProxyResponse(
            Integer statusCode,
            Map<String, String> headers,
            String body
    ) {}
}
