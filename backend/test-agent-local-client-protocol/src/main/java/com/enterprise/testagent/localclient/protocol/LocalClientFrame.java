package com.enterprise.testagent.localclient.protocol;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 反向隧道统一帧。注册帧的 generation 为 null，认证成功后的全部帧必须携带当前换代号。
 */
public record LocalClientFrame(
        String protocolVersion,
        LocalClientFrameType type,
        String requestId,
        String traceId,
        Long connectionGeneration,
        JsonNode payload) {
}
