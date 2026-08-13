package com.enterprise.testagent.integration.tcds;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.tcds.TcdsGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCDS HTTP 实现。所有业务地址均相对部署期 base URL 构造，token 与文档签名地址不会离开该适配器。
 */
public class TcdsHttpGateway implements TcdsGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcdsHttpGateway.class);
    private static final String TOKEN_HEADER = "token";
    private static final String TOOL_ID_HEADER = "toolId";
    // 沿用现有 TCDS 用户查询客户端的非敏感工具标识，避免重构后改变内部接口鉴权契约。
    private static final String TOOL_ID = "66f36bfa5c1c6105572b0118880261d6";
    private static final long MAX_JSON_BYTES = 10L * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TcdsHttpGateway(TcdsProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUri = requireBaseUri(properties == null ? null : properties.getBaseUrl());
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Optional<UserProfile> findUser(String unifiedAuthId) {
        try {
            JsonNode response = getJson(
                    "/user/getUserByLoginName",
                    Map.of("userId", required(unifiedAuthId, "unifiedAuthId")),
                    null);
            // 该存量接口兼容直接用户对象与标准 code/data 包络两种部署版本。
            JsonNode data = response.has("code") ? data(response) : response;
            if (data == null || data.isNull()) return Optional.empty();
            return Optional.of(new UserProfile(
                    text(data, "fullname"),
                    text(data, "loginname"),
                    text(data, "basement"),
                    text(data, "departname")));
        } catch (RuntimeException exception) {
            // 登录建号保持既有 fail-open 语义；日志不记录统一认证号、URL、响应正文或 token。
            LOGGER.warn("TCDS user profile lookup failed: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public List<Application> listApplications(String unifiedAuthId) {
        Session session = login(unifiedAuthId);
        JsonNode data = data(getJson(
                "/caseEntrance/getAppAndDeptList",
                Map.of("userId", session.unifiedAuthId(), "app", "", "dept", ""),
                session.token()));
        JsonNode applications = data == null ? null : data.path("app");
        if (applications == null || !applications.isArray()) return List.of();
        List<Application> result = new ArrayList<>();
        for (JsonNode application : applications) {
            String appName = firstText(application, "appName", "appShortName", "name");
            String appShortName = firstText(application, "appShortName", "appName", "shortName");
            if (!isBlank(appName) && !isBlank(appShortName)) {
                result.add(new Application(appName.trim(), appShortName.trim()));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public List<RequirementItem> listRequirementItems(String unifiedAuthId, String appShortName, String editionId) {
        Session session = login(unifiedAuthId);
        JsonNode envelope = postJson(
                "/subItem/getSubItemInfo",
                Map.of(
                        "editionId", required(editionId, "editionId"),
                        "appShortName", required(appShortName, "appShortName"),
                        "tester", session.unifiedAuthId(),
                        "port", ""),
                session.token());
        JsonNode items = data(envelope);
        if (items == null || !items.isArray()) return List.of();
        List<RequirementItem> result = new ArrayList<>();
        for (JsonNode item : items) {
            String itemNo = text(item, "itemNo");
            String itemName = text(item, "itemName");
            if (isBlank(itemNo) || isBlank(itemName)) continue;
            List<RequirementSubItem> children = new ArrayList<>();
            JsonNode rawChildren = item.path("children");
            if (rawChildren.isArray()) {
                for (JsonNode child : rawChildren) {
                    String childNo = text(child, "itemNo");
                    String childName = text(child, "itemName");
                    if (isBlank(childNo) || isBlank(childName)) continue;
                    children.add(new RequirementSubItem(childNo.trim(), childName.trim(), documents(child.path("graphList"))));
                }
            }
            result.add(new RequirementItem(itemNo.trim(), itemName.trim(), children));
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<Document> findFallbackDesignDocument(String unifiedAuthId, String subItemNo) {
        Session session = login(unifiedAuthId);
        JsonNode raw = data(getJson(
                "/minio/getTestMinioGraphByItemNo",
                Map.of("itemNo", required(subItemNo, "subItemNo"), "userId", session.unifiedAuthId()),
                session.token()));
        return Optional.ofNullable(document(raw));
    }

    @Override
    public DownloadedDocument download(Document document, long maxBytes) {
        Objects.requireNonNull(document, "document must not be null");
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        URI current = requireHttpUri(document.uri(), "TCDS 文档地址无效");
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "*/*")
                    .GET()
                    .build();
            try {
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    response.body().close();
                    if (redirects == MAX_REDIRECTS) throw upstream("TCDS 文档重定向次数过多");
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> upstream("TCDS 文档重定向无效"));
                    current = requireHttpUri(current.resolve(location), "TCDS 文档重定向地址无效");
                    continue;
                }
                if (status < 200 || status >= 300) {
                    response.body().close();
                    throw upstream("TCDS 文档下载失败");
                }
                String contentLength = response.headers().firstValue("Content-Length").orElse(null);
                if (contentLength != null && parseLength(contentLength) > maxBytes) {
                    response.body().close();
                    throw new PlatformException(ErrorCode.PAYLOAD_TOO_LARGE, "TCDS 文档超过单文件大小限制");
                }
                byte[] content;
                try (InputStream body = response.body()) {
                    content = readBounded(body, maxBytes, "TCDS 文档超过单文件大小限制");
                }
                return new DownloadedDocument(
                        content,
                        response.headers().firstValue("Content-Type").orElse("application/octet-stream"));
            } catch (PlatformException exception) {
                throw exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw upstream("TCDS 文档下载被中断");
            } catch (Exception exception) {
                throw upstream("TCDS 文档下载失败");
            }
        }
        throw upstream("TCDS 文档下载失败");
    }

    private Session login(String unifiedAuthId) {
        String normalized = required(unifiedAuthId, "unifiedAuthId");
        JsonNode data = data(getJson("/loginByUserId", Map.of("userId", normalized), null));
        String token = data == null ? null : text(data, "token");
        if (isBlank(token)) throw upstream("TCDS 登录失败");
        return new Session(normalized, token);
    }

    private JsonNode getJson(String path, Map<String, String> query, String token) {
        StringBuilder suffix = new StringBuilder(path);
        if (query != null && !query.isEmpty()) {
            suffix.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (!first) suffix.append('&');
                first = false;
                suffix.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
            }
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(resolve(suffix.toString()))
                .timeout(Duration.ofSeconds(30))
                .header(TOOL_ID_HEADER, TOOL_ID)
                .GET();
        if (!isBlank(token)) request.header(TOKEN_HEADER, token);
        return sendJson(request.build());
    }

    private JsonNode postJson(String path, Object body, String token) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header(TOOL_ID_HEADER, TOOL_ID)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)));
            if (!isBlank(token)) request.header(TOKEN_HEADER, token);
            return sendJson(request.build());
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw upstream("TCDS 请求构造失败");
        }
    }

    private JsonNode sendJson(HttpRequest request) {
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw upstream("TCDS 服务响应异常");
                }
                byte[] bytes = readBounded(body, MAX_JSON_BYTES, "TCDS 响应超过大小限制");
                return objectMapper.readTree(bytes);
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw upstream("TCDS 请求被中断");
        } catch (Exception exception) {
            throw upstream("TCDS 服务暂不可用");
        }
    }

    private JsonNode data(JsonNode envelope) {
        if (envelope == null || !"0".equals(envelope.path("code").asText())) {
            throw upstream("TCDS 服务响应异常");
        }
        return envelope.get("data");
    }

    private List<Document> documents(JsonNode rawDocuments) {
        if (rawDocuments == null || !rawDocuments.isArray()) return List.of();
        List<Document> documents = new ArrayList<>();
        for (JsonNode raw : rawDocuments) {
            Document document = document(raw);
            if (document != null) documents.add(document);
        }
        return List.copyOf(documents);
    }

    private Document document(JsonNode raw) {
        if (raw == null || raw.isNull()) return null;
        String fileName = text(raw, "fileName");
        String filePath = text(raw, "filePath");
        String fileType = text(raw, "fileType");
        if (isBlank(fileName) || isBlank(filePath) || isBlank(fileType)) return null;
        try {
            return new Document(fileName.trim(), URI.create(filePath.trim()), fileType.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private URI resolve(String pathAndQuery) {
        return baseUri.resolve(pathAndQuery.startsWith("/") ? pathAndQuery.substring(1) : pathAndQuery);
    }

    private static URI requireBaseUri(String raw) {
        if (isBlank(raw)) throw new IllegalStateException("TEST_AGENT_TCDS_BASE_URL 未配置");
        try {
            URI uri = URI.create(raw.trim());
            requireHttpUri(uri, "TEST_AGENT_TCDS_BASE_URL 必须是 HTTP/HTTPS 绝对地址");
            String normalized = uri.toString().endsWith("/") ? uri.toString() : uri + "/";
            return URI.create(normalized);
        } catch (PlatformException | IllegalArgumentException exception) {
            throw new IllegalStateException("TEST_AGENT_TCDS_BASE_URL 配置无效");
        }
    }

    private static URI requireHttpUri(URI uri, String message) {
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, message);
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, message);
        }
        return uri;
    }

    private static byte[] readBounded(InputStream input, long maxBytes, String message) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) throw new PlatformException(ErrorCode.PAYLOAD_TOO_LARGE, message);
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static long parseLength(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!isBlank(value)) return value;
        }
        return null;
    }

    private static String required(String value, String field) {
        if (isBlank(value)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 不能为空");
        }
        return value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static PlatformException upstream(String message) {
        return new PlatformException(ErrorCode.EXTERNAL_API_UNAVAILABLE, message);
    }

    private record Session(String unifiedAuthId, String token) {
    }
}
