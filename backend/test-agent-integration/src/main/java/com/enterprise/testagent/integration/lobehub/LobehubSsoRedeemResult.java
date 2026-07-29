package com.enterprise.testagent.integration.lobehub;

import java.time.Instant;
import java.util.List;

/** LobeHub 服务端兑换票据后获得的 JIT 身份和模型委托。 */
public record LobehubSsoRedeemResult(
        String userId,
        String unifiedAuthId,
        String username,
        String email,
        String department,
        String departmentKey,
        String instanceRole,
        List<String> platformRoles,
        String modelGrant,
        Instant grantExpiresAt) {

    /** 防止可变角色列表逃出服务边界。 */
    public LobehubSsoRedeemResult {
        platformRoles = platformRoles == null ? List.of() : List.copyOf(platformRoles);
    }
}
