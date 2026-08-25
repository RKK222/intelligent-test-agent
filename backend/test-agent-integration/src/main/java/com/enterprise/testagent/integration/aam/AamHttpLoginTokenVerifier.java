package com.enterprise.testagent.integration.aam;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AamLoginTokenVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * AAM HTTP 验真实现。该边界不记录用户号、Token、请求 URL 或上游响应正文。
 */
public class AamHttpLoginTokenVerifier implements AamLoginTokenVerifier {

    private static final String CHECK_LOGIN_PATH = "/aam/checkLogin";

    private final URI checkLoginUri;
    private final AamProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AamHttpLoginTokenVerifier(
            AamProperties properties,
            HttpClient httpClient,
            ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.checkLoginUri = validateOrigin(properties.getBaseUrl()).resolve(CHECK_LOGIN_PATH);
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        if (properties.getRequestTimeout() == null
                || properties.getRequestTimeout().isZero()
                || properties.getRequestTimeout().isNegative()
                || properties.getMaxResponseBytes() < 1) {
            throw new IllegalStateException("AAM 请求边界配置无效");
        }
    }

    @Override
    public void verify(String userId, String token) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("Token", required(token));
            payload.put("userId", required(userId));
            HttpRequest request = HttpRequest.newBuilder(checkLoginUri)
                    .timeout(properties.getRequestTimeout())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            try (InputStream body = response.body()) {
                if (status == 401 || status == 403) {
                    throw unauthenticated();
                }
                if (status < 200 || status >= 300) {
                    throw unavailable();
                }
                byte[] responseBytes = readBounded(body, properties.getMaxResponseBytes());
                JsonNode code = objectMapper.readTree(responseBytes).get("code");
                if (code == null || !code.isIntegralNumber()) {
                    throw unavailable();
                }
                if (code.intValue() != 200) {
                    throw unauthenticated();
                }
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private static URI validateOrigin(String rawBaseUrl) {
        try {
            if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
                throw new IllegalArgumentException();
            }
            URI uri = URI.create(rawBaseUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String path = uri.getRawPath();
            if (!uri.isAbsolute()
                    || uri.isOpaque()
                    || uri.getHost() == null
                    || !("http".equals(scheme) || "https".equals(scheme))
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || !(path == null || path.isEmpty() || "/".equals(path))) {
                throw new IllegalArgumentException();
            }
            return new URI(scheme, null, uri.getHost(), uri.getPort(), "/", null, null);
        } catch (Exception exception) {
            throw new IllegalStateException("TEST_AGENT_AAM_BASE_URL 配置无效");
        }
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) {
                throw unavailable();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR);
        }
        return value.trim();
    }

    private static PlatformException unauthenticated() {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, "AAM 登录凭据无效");
    }

    private static PlatformException unavailable() {
        return new PlatformException(ErrorCode.EXTERNAL_API_UNAVAILABLE, "AAM 登录认证服务暂不可用");
    }
}
