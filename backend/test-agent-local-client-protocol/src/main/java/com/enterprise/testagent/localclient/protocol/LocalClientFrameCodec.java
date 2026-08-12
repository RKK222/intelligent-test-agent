package com.enterprise.testagent.localclient.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Objects;

/** JSON 帧编解码与版本、必填字段、消息上限校验的唯一入口。 */
public final class LocalClientFrameCodec {

    private final ObjectMapper objectMapper;

    public LocalClientFrameCodec() {
        this(new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    public LocalClientFrameCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public String encode(LocalClientFrame frame) {
        LocalClientFrame validated = validate(frame);
        try {
            String encoded = objectMapper.writeValueAsString(validated);
            requireFrameSize(encoded);
            return encoded;
        } catch (JsonProcessingException exception) {
            throw new LocalClientProtocolException("FRAME_ENCODE_FAILED", "本地客户端协议帧编码失败", exception);
        }
    }

    public LocalClientFrame decode(String encoded) {
        requireFrameSize(encoded);
        try {
            return validate(objectMapper.readValue(encoded, LocalClientFrame.class));
        } catch (LocalClientProtocolException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new LocalClientProtocolException("FRAME_DECODE_FAILED", "本地客户端协议帧不是有效 JSON", exception);
        }
    }

    public JsonNode payload(Object value) {
        return objectMapper.valueToTree(value);
    }

    public <T> T payload(LocalClientFrame frame, Class<T> type) {
        validate(frame);
        try {
            return objectMapper.treeToValue(frame.payload(), type);
        } catch (JsonProcessingException exception) {
            throw new LocalClientProtocolException("PAYLOAD_DECODE_FAILED", "本地客户端协议载荷无法解析", exception);
        }
    }

    private static LocalClientFrame validate(LocalClientFrame frame) {
        if (frame == null) {
            throw new LocalClientProtocolException("FRAME_REQUIRED", "本地客户端协议帧不能为空");
        }
        if (!LocalClientProtocol.VERSION.equals(frame.protocolVersion())) {
            throw new LocalClientProtocolException("PROTOCOL_VERSION_UNSUPPORTED", "本地客户端协议版本不兼容");
        }
        if (frame.type() == null) {
            throw new LocalClientProtocolException("FRAME_TYPE_REQUIRED", "本地客户端协议帧缺少 type");
        }
        requireIdentifier(frame.requestId(), "requestId");
        requireIdentifier(frame.traceId(), "traceId");
        if (frame.type() != LocalClientFrameType.REGISTER
                && frame.type() != LocalClientFrameType.ERROR
                && frame.connectionGeneration() == null) {
            throw new LocalClientProtocolException("GENERATION_REQUIRED", "认证后的协议帧缺少 connectionGeneration");
        }
        if (frame.connectionGeneration() != null && frame.connectionGeneration() < 1) {
            throw new LocalClientProtocolException("GENERATION_INVALID", "connectionGeneration 必须为正数");
        }
        if (frame.payload() == null || frame.payload().isNull()) {
            throw new LocalClientProtocolException("PAYLOAD_REQUIRED", "本地客户端协议帧缺少 payload");
        }
        return frame;
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new LocalClientProtocolException("IDENTIFIER_INVALID", field + " 必须为 1 到 128 个字符");
        }
    }

    private static void requireFrameSize(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new LocalClientProtocolException("FRAME_REQUIRED", "本地客户端协议帧不能为空");
        }
        if (encoded.length() > LocalClientProtocol.MAX_FRAME_CHARS) {
            throw new LocalClientProtocolException("FRAME_TOO_LARGE", "本地客户端协议帧超过大小上限");
        }
    }
}
