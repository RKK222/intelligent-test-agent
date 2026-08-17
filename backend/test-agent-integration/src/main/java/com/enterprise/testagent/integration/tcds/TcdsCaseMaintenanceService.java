package com.enterprise.testagent.integration.tcds;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 调用 TCDS 案例维护接口；目标地址、工具标识和固定业务字段均不接受客户端覆盖。 */
public class TcdsCaseMaintenanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcdsCaseMaintenanceService.class);
    private static final String CREATE_GRAPH_CASE_PATH = "/graphDesign/createGraphCase";
    private static final String TASK_TYPES_PATH = "/task/getTaskTypes";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final int MAX_TASK_TYPE_COUNT = 100;
    private static final int MAX_TASK_TYPE_NAME_LENGTH = 128;
    private static final int MAX_TASK_TYPE_VALUE_LENGTH = 32;
    private static final int MAX_LOG_CASE_COUNT = 20;
    private static final int MAX_LOG_PAYLOAD_LENGTH = 8 * 1024;
    private static final int LOG_DIGEST_LENGTH = 16;
    private static final String REDACTED = "[REDACTED]";
    private final TcdsHttpRequestFactory requestFactory;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** 与其它 TCDS 能力共享部署地址、HTTP client 和 toolId 注入程序。 */
    TcdsCaseMaintenanceService(
            TcdsHttpRequestFactory requestFactory,
            HttpClient httpClient,
            ObjectMapper objectMapper) {
        this.requestFactory = Objects.requireNonNull(requestFactory);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    /** 实时读取生产 TCDS 的任务类型，不使用本地快照或把上游附加字段透传给浏览器。 */
    public List<TcdsTaskTypeOption> getTaskTypes(String traceId) {
        String normalizedTraceId = requireText(traceId, "traceId 不能为空");
        try {
            HttpRequest request = requestFactory.request(TASK_TYPES_PATH, REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            List<TcdsTaskTypeOption> taskTypes;
            try (InputStream responseBody = response.body()) {
                taskTypes = parseTaskTypes(response.statusCode(), responseBody);
            }
            LOGGER.info(
                    "TCDS task types loaded, taskTypeCount={}, traceId={}",
                    taskTypes.size(),
                    normalizedTraceId);
            return taskTypes;
        } catch (PlatformException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw unavailable("TCDS 任务类型服务请求超时", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("TCDS 任务类型请求被中断", exception);
        } catch (JsonProcessingException exception) {
            throw unavailable("TCDS 任务类型服务响应格式无效", exception);
        } catch (IOException exception) {
            throw unavailable("TCDS 任务类型服务暂不可用", exception);
        }
    }

    /** 使用认证主体的统一认证号组装完整报文，并校验 TCDS 业务返回码。 */
    public void maintain(
            String itemNo,
            String unifiedAuthId,
            List<TcdsCaseInput> cases,
            String traceId) {
        String normalizedItemNo = requireText(itemNo, "需求子条目编号不能为空");
        String normalizedUserId = requireText(unifiedAuthId, "当前用户缺少统一认证号");
        String normalizedTraceId = requireText(traceId, "traceId 不能为空");
        if (cases == null || cases.isEmpty()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "案例列表不能为空");
        }
        Set<String> allowedTaskTypes = getTaskTypes(normalizedTraceId).stream()
                .map(TcdsTaskTypeOption::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        validateCaseTaskTypes(cases, allowedTaskTypes);

        UpstreamRequest requestBody = new UpstreamRequest(
                "1",
                "文本理解生成法",
                normalizedItemNo,
                normalizedUserId,
                cases.stream().map(UpstreamCase::from).toList());
        try {
            byte[] serializedRequest = objectMapper.writeValueAsBytes(requestBody);
            LOGGER.info(
                    "event=tcds_create_graph_case_request operation=createGraphCase itemNo={} caseCount={} "
                            + "traceId={} payload={}",
                    normalizedItemNo,
                    cases.size(),
                    normalizedTraceId,
                    safeRequestLogPayload(normalizedItemNo, cases));
            HttpRequest request = requestFactory.request(CREATE_GRAPH_CASE_PATH, REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(serializedRequest))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            InputStream rawResponseBody = response.body();
            byte[] responseBytes = null;
            if (rawResponseBody != null) {
                try (InputStream responseBody = rawResponseBody) {
                    responseBytes = readBoundedResponse(responseBody);
                }
            }
            LOGGER.info(
                    "event=tcds_create_graph_case_response operation=createGraphCase itemNo={} httpStatus={} "
                            + "traceId={} payload={}",
                    normalizedItemNo,
                    response.statusCode(),
                    normalizedTraceId,
                    safeResponseLogPayload(response.statusCode(), responseBytes));
            verifyMaintenanceResponse(response.statusCode(), responseBytes);
            LOGGER.info(
                    "TCDS case maintenance succeeded, itemNo={}, caseCount={}, traceId={}",
                    normalizedItemNo,
                    cases.size(),
                    normalizedTraceId);
        } catch (PlatformException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw unavailable("TCDS 案例维护服务请求超时", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("TCDS 案例维护请求被中断", exception);
        } catch (JsonProcessingException exception) {
            throw unavailable("TCDS 案例维护服务响应格式无效", exception);
        } catch (IOException exception) {
            throw unavailable("TCDS 案例维护服务暂不可用", exception);
        }
    }

    private List<TcdsTaskTypeOption> parseTaskTypes(int statusCode, InputStream responseBody) throws IOException {
        JsonNode root = readResponseRoot(statusCode, responseBody, "TCDS 任务类型");
        if (root.path("code").intValue() != 0) {
            throw unavailable("TCDS 任务类型服务返回失败", null);
        }
        JsonNode subItemTypes = root.path("data").path("subItemTypes");
        if (!subItemTypes.isArray() || subItemTypes.isEmpty() || subItemTypes.size() > MAX_TASK_TYPE_COUNT) {
            throw unavailable("TCDS 任务类型服务响应格式无效", null);
        }

        List<TcdsTaskTypeOption> options = new java.util.ArrayList<>(subItemTypes.size());
        Set<String> values = new HashSet<>();
        Set<String> taskTypeNames = new HashSet<>();
        for (JsonNode item : subItemTypes) {
            if (!item.isObject()) {
                throw unavailable("TCDS 任务类型服务响应格式无效", null);
            }
            String name = normalizedUpstreamText(item.get("name"), MAX_TASK_TYPE_NAME_LENGTH);
            String value = normalizedUpstreamText(item.get("value"), MAX_TASK_TYPE_VALUE_LENGTH);
            if (name == null || value == null || name.contains(",") || name.contains("，")
                    || !values.add(value) || !taskTypeNames.add(name)) {
                throw unavailable("TCDS 任务类型服务响应格式无效", null);
            }
            options.add(new TcdsTaskTypeOption(name, value));
        }
        return List.copyOf(options);
    }

    /** 提交前使用同一时刻的实时任务类型集合校验浏览器传入的业务名称。 */
    private static void validateCaseTaskTypes(List<TcdsCaseInput> cases, Set<String> allowedTaskTypes) {
        if (cases.stream().anyMatch(Objects::isNull)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "案例不能为空");
        }
        boolean invalid = cases.stream()
                .flatMap(input -> Arrays.stream(input.taskType().split(",", -1)))
                .anyMatch(taskType -> !allowedTaskTypes.contains(taskType));
        if (invalid) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "任务类型无效");
        }
    }

    private void verifyMaintenanceResponse(int statusCode, byte[] responseBody) throws IOException {
        JsonNode root = readResponseRoot(statusCode, responseBody, "TCDS 案例维护");
        if (root.path("code").intValue() != 0) {
            throw new PlatformException(ErrorCode.CONFLICT, safeBusinessMessage(root.path("msg").asText("")));
        }
    }

    /** 对两个 TCDS 接口统一执行 HTTP、响应体大小、JSON 根节点和业务码字段校验。 */
    private JsonNode readResponseRoot(int statusCode, InputStream responseBody, String operation) throws IOException {
        return readResponseRoot(statusCode, responseBody == null ? null : readBoundedResponse(responseBody), operation);
    }

    private JsonNode readResponseRoot(int statusCode, byte[] body, String operation) throws IOException {
        if (statusCode < 200 || statusCode >= 300) {
            throw unavailable(operation + "服务暂不可用", null);
        }
        if (body == null) {
            throw unavailable(operation + "服务响应格式无效", null);
        }
        if (body.length == 0 || body.length > MAX_RESPONSE_BYTES) {
            throw unavailable(operation + "服务响应格式无效", null);
        }
        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.isObject()) {
            throw unavailable(operation + "服务响应格式无效", null);
        }
        JsonNode code = root.get("code");
        if (code == null || !code.canConvertToInt()) {
            throw unavailable(operation + "服务响应格式无效", null);
        }
        return root;
    }

    /** 只读取上限再多一个字节，既标识超限响应，也避免异常上游占用无界内存。 */
    private static byte[] readBoundedResponse(InputStream responseBody) throws IOException {
        return responseBody.readNBytes(MAX_RESPONSE_BYTES + 1);
    }

    /**
     * 生成 createGraphCase 请求的安全日志报文。认证号完全删除，案例正文只保留长度和摘要，
     * 同时限制案例预览数量和整条日志大小，避免日志成为业务数据副本。
     */
    String safeRequestLogPayload(String itemNo, List<TcdsCaseInput> cases)
            throws JsonProcessingException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("aiFlag", "1");
        payload.put("method", "文本理解生成法");
        payload.put("itemNo", safeLogText(itemNo, 128));
        payload.put("userId", REDACTED);
        payload.put("caseCount", cases.size());
        ArrayNode caseList = payload.putArray("caseList");
        cases.stream().limit(MAX_LOG_CASE_COUNT).forEach(input -> {
            ObjectNode caseNode = caseList.addObject();
            caseNode.set("name", sensitiveTextSummary(input.name()));
            caseNode.set("step", sensitiveTextSummary(input.step()));
            caseNode.set("data", sensitiveTextSummary(input.data()));
            caseNode.set("expect", sensitiveTextSummary(input.expect()));
            caseNode.set("dataDependencies", sensitiveTextSummary(""));
            caseNode.put("isAICase", "是");
            caseNode.put("isUpdate", "否");
            caseNode.put("caseFlag", "2");
            caseNode.put("taskType", safeLogText(input.taskType(), 512));
        });
        if (cases.size() > MAX_LOG_CASE_COUNT) {
            payload.put("omittedCaseCount", cases.size() - MAX_LOG_CASE_COUNT);
        }
        return boundedLogPayload(payload, requestLogFallback(itemNo, cases.size()));
    }

    /**
     * 生成 createGraphCase 响应的安全日志报文。业务码和有界消息用于诊断，data 及未知正文只记录元数据摘要。
     */
    String safeResponseLogPayload(int statusCode, byte[] body) throws IOException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("httpStatus", statusCode);
        if (body == null) {
            payload.put("bodyState", "missing");
            return objectMapper.writeValueAsString(payload);
        }
        payload.put("bodyBytesRead", body.length);
        if (body.length > MAX_RESPONSE_BYTES) {
            payload.put("bodyState", "oversized");
            payload.put("bodyPrefixSha256", shortSha256(body));
            return objectMapper.writeValueAsString(payload);
        }
        payload.put("bodySha256", shortSha256(body));
        if (body.length == 0) {
            payload.put("bodyState", "empty");
            return objectMapper.writeValueAsString(payload);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                payload.put("bodyState", "invalid_root");
                return objectMapper.writeValueAsString(payload);
            }
            JsonNode code = root.get("code");
            if (code != null && code.canConvertToInt()) {
                payload.put("code", code.intValue());
            } else {
                payload.put("codeState", "invalid");
            }
            JsonNode msg = root.get("msg");
            if (msg != null && msg.isTextual()) {
                payload.put("msg", safeUpstreamMessage(msg.textValue()));
            }
            if (root.has("data")) {
                payload.set("data", responseDataSummary(root.get("data")));
            }
            payload.put("bodyState", "parsed");
            return boundedLogPayload(payload, responseLogFallback(statusCode, body, code, msg));
        } catch (JsonProcessingException exception) {
            payload.put("bodyState", "invalid_json");
            return objectMapper.writeValueAsString(payload);
        }
    }

    private ObjectNode requestLogFallback(String itemNo, int caseCount) {
        ObjectNode fallback = objectMapper.createObjectNode();
        fallback.put("aiFlag", "1");
        fallback.put("method", "文本理解生成法");
        fallback.put("itemNo", safeLogText(itemNo, 128));
        fallback.put("userId", REDACTED);
        fallback.put("caseCount", caseCount);
        fallback.put("payloadState", "truncated");
        return fallback;
    }

    private ObjectNode responseLogFallback(int statusCode, byte[] body, JsonNode code, JsonNode msg) {
        ObjectNode fallback = objectMapper.createObjectNode();
        fallback.put("httpStatus", statusCode);
        fallback.put("bodyBytesRead", body.length);
        fallback.put("bodySha256", shortSha256(body));
        if (code != null && code.canConvertToInt()) {
            fallback.put("code", code.intValue());
        }
        if (msg != null && msg.isTextual()) {
            fallback.put("msg", safeUpstreamMessage(msg.textValue()));
        }
        fallback.put("payloadState", "truncated");
        return fallback;
    }

    private String boundedLogPayload(ObjectNode payload, ObjectNode fallback) throws JsonProcessingException {
        String serialized = objectMapper.writeValueAsString(payload);
        byte[] serializedBytes = serialized.getBytes(StandardCharsets.UTF_8);
        if (serializedBytes.length <= MAX_LOG_PAYLOAD_LENGTH) {
            return serialized;
        }
        fallback.put("payloadBytes", serializedBytes.length);
        fallback.put("payloadSha256", shortSha256(serializedBytes));
        return objectMapper.writeValueAsString(fallback);
    }

    private ObjectNode sensitiveTextSummary(String value) {
        String normalized = value == null ? "" : value;
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("length", normalized.length());
        summary.put("bytes", bytes.length);
        summary.put("sha256", shortSha256(bytes));
        return summary;
    }

    private ObjectNode responseDataSummary(JsonNode data) throws JsonProcessingException {
        ObjectNode summary = objectMapper.createObjectNode();
        if (data == null || data.isNull()) {
            summary.put("type", "null");
            return summary;
        }
        if (data.isArray()) {
            summary.put("type", "array");
            summary.put("size", data.size());
        } else if (data.isObject()) {
            summary.put("type", "object");
            summary.put("fieldCount", data.size());
        } else if (data.isTextual()) {
            summary.put("type", "string");
            summary.put("length", data.textValue().length());
        } else {
            summary.put("type", data.getNodeType().name().toLowerCase(java.util.Locale.ROOT));
        }
        summary.put("sha256", shortSha256(objectMapper.writeValueAsBytes(data)));
        return summary;
    }

    private static String shortSha256(byte[] value) {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
            return digest.substring(0, LOG_DIGEST_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private static String safeLogText(String value, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    /** 上游业务消息可保留用于排障，但其中常见凭据赋值、Bearer 值和长数字身份必须再次脱敏。 */
    private static String safeUpstreamMessage(String value) {
        String normalized = safeLogText(value, 200);
        normalized = TcdsHttpRequestFactory.redactToolId(normalized);
        normalized = normalized.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer " + REDACTED);
        normalized = normalized.replaceAll(
                "(?i)(authorization|cookie|token|password|secret|toolId)\\s*[:=]\\s*[^\\s,;]+",
                "$1=" + REDACTED);
        return normalized.replaceAll("(?<!\\d)\\d{6,}(?!\\d)", REDACTED);
    }

    /** 上游枚举仅接受有界、无控制字符的文本，避免异常数据进入页面。 */
    private static String normalizedUpstreamText(JsonNode node, int maxLength) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String rawValue = node.textValue();
        if (rawValue.codePoints().anyMatch(Character::isISOControl)) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isBlank() || value.length() > maxLength) {
            return null;
        }
        return value;
    }

    private static String safeBusinessMessage(String value) {
        String normalized = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (normalized.isBlank()) {
            return "TCDS 案例维护失败";
        }
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }

    private static String requireText(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private static PlatformException unavailable(String message, Throwable cause) {
        return new PlatformException(ErrorCode.EXTERNAL_API_UNAVAILABLE, message, java.util.Map.of(), cause);
    }

    private record UpstreamRequest(
            String aiFlag,
            String method,
            String itemNo,
            String userId,
            List<UpstreamCase> caseList) {
    }

    private record UpstreamCase(
            String name,
            String step,
            String data,
            String expect,
            String dataDependencies,
            String isAICase,
            String isUpdate,
            String caseFlag,
            String taskType) {

        private static UpstreamCase from(TcdsCaseInput input) {
            return new UpstreamCase(
                    input.name(),
                    input.step(),
                    input.data(),
                    input.expect(),
                    "",
                    "是",
                    "否",
                    "2",
                    input.taskType());
        }
    }
}
