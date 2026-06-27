package com.icbc.testagent.opencode.runtime.process.socket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 管理进程控制面 JSON 文本帧编解码器，统一把 Jackson 异常转换为平台错误。
 */
@Component
public class ManagerControlMessageCodec {

    private final ObjectMapper objectMapper;

    /**
     * 注入应用共享 ObjectMapper。
     */
    public ManagerControlMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null")
                .copy()
                .findAndRegisterModules();
    }

    /**
     * 将消息编码为 WebSocket 文本帧。
     */
    public String encode(ManagerControlMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "管理进程消息编码失败");
        }
    }

    /**
     * 从 WebSocket 文本帧解析消息，非法 JSON 映射为稳定错误码。
     */
    public ManagerControlMessage decode(String payload) {
        try {
            return objectMapper.readValue(payload, ManagerControlMessage.class);
        } catch (JsonProcessingException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "管理进程消息格式无效");
        }
    }
}
