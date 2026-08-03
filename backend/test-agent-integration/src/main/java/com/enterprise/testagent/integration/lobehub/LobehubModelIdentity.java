package com.enterprise.testagent.integration.lobehub;

/** 模型网关验证委托后可信任的最小用户身份。 */
public record LobehubModelIdentity(String userId, String unifiedAuthId, String scope) {
}
