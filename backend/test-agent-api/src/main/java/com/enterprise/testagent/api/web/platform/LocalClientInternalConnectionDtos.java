package com.enterprise.testagent.api.web.platform;

/** 后台 Java 间精确关闭旧客户端连接的内部 DTO。 */
final class LocalClientInternalConnectionDtos {

    private LocalClientInternalConnectionDtos() {
    }

    record RevokeRequest(String clientInstanceId, long connectionGeneration, String reason) {
    }

    record RevokeResponse(boolean closed) {
    }
}
