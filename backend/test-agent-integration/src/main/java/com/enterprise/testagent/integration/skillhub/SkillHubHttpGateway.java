package com.enterprise.testagent.integration.skillhub;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ExternalSkill;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ExternalSkillPackage;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadFile;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadProgress;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadRequest;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadSubmission;
import com.enterprise.testagent.domain.hub.SkillHubGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * SkillHub HTTP 实现。请求只发往部署配置的固定 origin，日志和异常详情均不包含访问密钥。
 */
final class SkillHubHttpGateway implements SkillHubGateway {

    static final String ACCESS_KEY_HEADER = "X-Skill-Access-Key";
    static final int MAX_CATALOG_BYTES = 4 * 1024 * 1024;
    static final int MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024;
    private static final Pattern MEDIA_TYPE_PATTERN =
            Pattern.compile("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+");

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
        JsonNode values = successfulResult(
                readJson(send(get(baseUri.resolve("list"))), MAX_CATALOG_BYTES),
                "SkillHub 目录查询失败");
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
    public SkillHubUploadSubmission upload(SkillHubUploadRequest request) {
        ensureEnabled();
        Objects.requireNonNull(request, "request must not be null");
        String boundary = "testagent-skillhub-" + UUID.randomUUID().toString().replace("-", "");
        List<byte[]> body = new ArrayList<>();
        addFormField(body, boundary, "source", request.source());
        addFormField(body, boundary, "phase", request.phase());
        addFile(body, boundary, "file", request.skillPackage());
        addFile(body, boundary, "safetyReportPic", request.safetyReportPicture());
        addFile(body, boundary, "directoryStructurePic", request.directoryStructurePicture());
        addFile(body, boundary, "runningEffectPic", request.runningEffectPicture());
        body.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest httpRequest = requestBuilder(baseUri.resolve("upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(body))
                .build();
        JsonNode result = successfulResult(
                readJson(send(httpRequest), MAX_CATALOG_BYTES),
                "SkillHub 上传提交失败");
        String taskId = result != null && result.isTextual() ? result.textValue() : null;
        if (!validTaskId(taskId)) {
            throw unavailable("SkillHub 上传响应 taskId 格式无效");
        }
        return new SkillHubUploadSubmission(taskId);
    }

    @Override
    public SkillHubUploadProgress uploadProgress(String taskId) {
        ensureEnabled();
        String normalizedTaskId = taskId == null ? "" : taskId.trim();
        if (!validTaskId(normalizedTaskId)) {
            throw new IllegalArgumentException("SkillHub taskId format is invalid");
        }
        URI uri = baseUri.resolve("upload/progress?taskId="
                + URLEncoder.encode(normalizedTaskId, StandardCharsets.UTF_8));
        JsonNode result = successfulResult(
                readJson(send(get(uri)), MAX_CATALOG_BYTES),
                "SkillHub 上传进度查询失败");
        JsonNode progressNode = result == null ? null : result.get("progress");
        JsonNode messageNode = result == null ? null : result.get("message");
        if (progressNode == null || !progressNode.isIntegralNumber()
                || messageNode == null || !messageNode.isTextual()) {
            throw unavailable("SkillHub 上传进度响应格式无效");
        }
        int progress = progressNode.intValue();
        if (progress != -1 && (progress < 10 || progress > 100)) {
            throw unavailable("SkillHub 上传进度值无效");
        }
        return new SkillHubUploadProgress(progress, messageNode.textValue());
    }

    @Override
    public ExternalSkillPackage download(long id, String version) {
        ensureEnabled();
        if (id <= 0) throw new IllegalArgumentException("SkillHub id must be positive");
        URI uri = baseUri.resolve("download/" + id + "?channel=" + SkillHubDownloadChannel.PLATFORM.code());
        HttpResponse<InputStream> response = send(get(uri));
        Long contentLength = validateDownloadHeaders(response);
        byte[] content = readLimited(response.body(), MAX_DOWNLOAD_BYTES, "SkillHub 下载包超过 20 MiB");
        if (content.length == 0) throw unavailable("SkillHub 下载包为空");
        if (contentLength != null && content.length != contentLength) {
            throw unavailable("SkillHub 下载包长度与响应头不一致");
        }
        return new ExternalSkillPackage(id, version, content);
    }

    private HttpRequest get(URI uri) {
        return requestBuilder(uri).GET().build();
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(properties.getRequestTimeout())
                .header(ACCESS_KEY_HEADER, properties.getAccessKey());
    }

    private HttpResponse<InputStream> send(HttpRequest request) {
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw unavailableForStatus(response.statusCode());
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

    private JsonNode successfulResult(JsonNode root, String failureMessage) {
        JsonNode code = root == null ? null : root.get("code");
        if (code == null || !code.isIntegralNumber() || code.intValue() != 0) {
            throw unavailable(failureMessage);
        }
        if (!root.has("result")) {
            throw unavailable("SkillHub 响应缺少 result 字段");
        }
        return root.get("result");
    }

    /**
     * 文档定义的标准响应是 application/octet-stream + 附件文件名 + Content-Length。
     * 企业反向代理可能把 ZIP 标为 application/zip，或改为 chunked 传输而省略后两项；这些传输差异
     * 不降低安全边界，正文仍受 20 MiB 上限、ZIP 解包和根 SKILL.md 校验约束。
     */
    private Long validateDownloadHeaders(HttpResponse<InputStream> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String normalizedType = contentType.split(";", 2)[0].trim();
        if (!"application/octet-stream".equalsIgnoreCase(normalizedType)
                && !"application/zip".equalsIgnoreCase(normalizedType)) {
            closeQuietly(response.body());
            throw unavailable("SkillHub 下载响应类型无效");
        }
        String disposition = response.headers().firstValue("Content-Disposition").orElse("");
        if (!blank(disposition)
                && !disposition.toLowerCase(java.util.Locale.ROOT).startsWith("attachment")) {
            closeQuietly(response.body());
            throw unavailable("SkillHub 下载响应附件类型无效");
        }
        String contentLengthHeader = response.headers().firstValue("Content-Length").orElse(null);
        if (blank(contentLengthHeader)) return null;
        long contentLength;
        try {
            contentLength = Long.parseLong(contentLengthHeader);
        } catch (NumberFormatException exception) {
            closeQuietly(response.body());
            throw unavailable("SkillHub 下载响应长度无效", exception);
        }
        if (contentLength <= 0 || contentLength > MAX_DOWNLOAD_BYTES) {
            closeQuietly(response.body());
            throw unavailable("SkillHub 下载响应长度超限");
        }
        return contentLength;
    }

    /** 上游正文可能包含内部信息，错误只保留安全状态分类和 HTTP 状态。 */
    private PlatformException unavailableForStatus(int statusCode) {
        String message = switch (statusCode) {
            case 401 -> "SkillHub 访问凭据无效或无权限";
            case 404 -> "SkillHub Skill 不存在或已下架";
            default -> statusCode >= 500 ? "SkillHub 服务异常" : "SkillHub 响应异常";
        };
        return new PlatformException(
                ErrorCode.SKILLHUB_UNAVAILABLE,
                message,
                java.util.Map.of("upstreamStatus", statusCode));
    }

    private void addFormField(List<byte[]> body, String boundary, String name, String value) {
        if (value == null) throw new IllegalArgumentException("SkillHub form field must not be null");
        addPart(body, boundary,
                "Content-Disposition: form-data; name=\"" + name + "\"\r\n"
                        + "Content-Type: text/plain; charset=UTF-8",
                value.getBytes(StandardCharsets.UTF_8));
    }

    private void addFile(List<byte[]> body, String boundary, String name, SkillHubUploadFile file) {
        Objects.requireNonNull(file, "SkillHub upload file must not be null");
        String filename = safeHeaderValue(file.filename(), "filename");
        String contentType = safeHeaderValue(file.contentType(), "contentType");
        if (!MEDIA_TYPE_PATTERN.matcher(contentType).matches()) {
            throw new IllegalArgumentException("SkillHub upload contentType format is invalid");
        }
        addPart(body, boundary,
                "Content-Disposition: form-data; name=\"" + name + "\"; filename=\""
                        + filename.replace("\\", "\\\\").replace("\"", "\\\"") + "\"\r\n"
                        + "Content-Type: " + contentType,
                file.content());
    }

    private void addPart(List<byte[]> body, String boundary, String headers, byte[] content) {
        body.add(("--" + boundary + "\r\n" + headers + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.add(content);
        body.add("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String safeHeaderValue(String value, String field) {
        if (blank(value) || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("SkillHub upload " + field + " is invalid");
        }
        return value;
    }

    /** 文档只规定 taskId 为 {userId}_{timestamp}；不额外限制企业 userId 字符集。 */
    private boolean validTaskId(String taskId) {
        if (blank(taskId) || taskId.length() > 256 || taskId.indexOf('\r') >= 0 || taskId.indexOf('\n') >= 0) {
            return false;
        }
        int separator = taskId.lastIndexOf('_');
        if (separator <= 0 || separator == taskId.length() - 1) return false;
        String timestamp = taskId.substring(separator + 1);
        return timestamp.length() >= 10 && timestamp.length() <= 17
                && timestamp.chars().allMatch(Character::isDigit);
    }

    private void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // 响应已经判定非法，关闭失败不覆盖稳定业务错误。
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
