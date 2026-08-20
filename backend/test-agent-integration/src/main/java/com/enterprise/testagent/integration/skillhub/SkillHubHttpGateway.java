package com.enterprise.testagent.integration.skillhub;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ExternalSkill;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ExternalSkillPackage;
import com.enterprise.testagent.domain.hub.SkillHubGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SkillHub HTTP 实现。请求只发往部署配置的固定 origin，日志和异常详情均不包含访问密钥。
 */
final class SkillHubHttpGateway implements SkillHubGateway {

    static final String ACCESS_KEY_HEADER = "X-Skill-Access-Key";
    static final int MAX_CATALOG_BYTES = 4 * 1024 * 1024;
    static final int MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024;

    private final SkillHubProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;

    SkillHubHttpGateway(SkillHubProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        if (!properties.isEnabled()) {
            this.baseUri = null;
            return;
        }
        if (blank(properties.getBaseUrl()) || blank(properties.getAccessKey())) {
            throw new IllegalStateException(
                    "SkillHub enabled requires TEST_AGENT_SKILLHUB_BASE_URL and TEST_AGENT_SKILLHUB_ACCESS_KEY");
        }
        URI configured = URI.create(properties.getBaseUrl().trim());
        if (!("http".equalsIgnoreCase(configured.getScheme()) || "https".equalsIgnoreCase(configured.getScheme()))
                || configured.getHost() == null || configured.getUserInfo() != null
                || configured.getQuery() != null || configured.getFragment() != null) {
            throw new IllegalStateException("SkillHub base URL must be an absolute HTTP(S) origin/path");
        }
        this.baseUri = URI.create(configured.toString().replaceAll("/+$", "") + "/");
    }

    @Override
    public boolean enabled() {
        return properties.isEnabled();
    }

    @Override
    public List<ExternalSkill> listSkills() {
        ensureEnabled();
        JsonNode root = readJson(send(request(baseUri.resolve("list"))), MAX_CATALOG_BYTES);
        int code = root.path("code").asInt(0);
        if (code != 0 && code != 200) {
            throw unavailable("SkillHub 目录查询失败");
        }
        JsonNode values = root.path("result");
        if (!values.isArray()) values = root.path("data");
        if (!values.isArray()) {
            throw unavailable("SkillHub 目录响应格式无效");
        }
        List<ExternalSkill> result = new ArrayList<>();
        for (JsonNode value : values) {
            long id = longValue(value, "id", -1);
            String name = text(value, "name");
            String version = text(value, "version");
            if (id <= 0 || blank(name) || blank(version)) {
                throw unavailable("SkillHub 目录存在缺少 ID、name 或 version 的条目");
            }
            result.add(new ExternalSkill(
                    id, name.trim(), version.trim(), firstText(value, "displayName", "title", "name"),
                    text(value, "description"), text(value, "source"), text(value, "tag"),
                    firstText(value, "phase", "phaseCode"), text(value, "phaseName"),
                    firstText(value, "contributor", "creator", "createdBy"),
                    instant(value, "createdAt", "createTime"),
                    Math.max(0, longValue(value, "downloadNum", longValue(value, "downloadCount", 0)))));
        }
        return List.copyOf(result);
    }

    @Override
    public ExternalSkillPackage download(long id, String version) {
        ensureEnabled();
        if (id <= 0) throw new IllegalArgumentException("SkillHub id must be positive");
        URI uri = baseUri.resolve("download/" + id + "?channel=" + SkillHubDownloadChannel.PLATFORM.code());
        HttpResponse<InputStream> response = send(request(uri));
        byte[] content = readLimited(response.body(), MAX_DOWNLOAD_BYTES, "SkillHub 下载包超过 20 MiB");
        if (content.length == 0) throw unavailable("SkillHub 下载包为空");
        return new ExternalSkillPackage(id, version, content);
    }

    private HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(properties.getRequestTimeout())
                .header(ACCESS_KEY_HEADER, properties.getAccessKey())
                .GET().build();
    }

    private HttpResponse<InputStream> send(HttpRequest request) {
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw unavailable("SkillHub 响应异常");
            }
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("SkillHub 请求已中断", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof PlatformException platformException) throw platformException;
            throw unavailable("SkillHub 当前不可用", exception);
        }
    }

    private JsonNode readJson(HttpResponse<InputStream> response, int limit) {
        byte[] bytes = readLimited(response.body(), limit, "SkillHub 目录响应超过上限");
        try {
            return objectMapper.readTree(bytes);
        } catch (IOException exception) {
            throw unavailable("SkillHub 目录响应不是有效 JSON", exception);
        }
    }

    private byte[] readLimited(InputStream input, int limit, String overflowMessage) {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 64 * 1024))) {
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) throw unavailable(overflowMessage);
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw unavailable("读取 SkillHub 响应失败", exception);
        }
    }

    private void ensureEnabled() {
        if (!enabled()) throw unavailable("SkillHub 集成未启用");
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = text(node, name);
            if (!blank(value)) return value;
        }
        return null;
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private long longValue(JsonNode node, String name, long fallback) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? fallback : value.asLong(fallback);
    }

    private Instant instant(JsonNode node, String... names) {
        String raw = firstText(node, names);
        if (blank(raw)) return null;
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(raw.replace(' ', 'T')).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private PlatformException unavailable(String message) {
        return new PlatformException(ErrorCode.SKILLHUB_UNAVAILABLE, message);
    }

    private PlatformException unavailable(String message, Throwable cause) {
        return new PlatformException(ErrorCode.SKILLHUB_UNAVAILABLE, message, java.util.Map.of(), cause);
    }
}
