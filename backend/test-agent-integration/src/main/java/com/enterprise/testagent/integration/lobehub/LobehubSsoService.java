package com.enterprise.testagent.integration.lobehub;

import com.enterprise.testagent.domain.auth.AuthPrincipal;

/** API 入口依赖的 LobeHub SSO 应用边界。 */
public interface LobehubSsoService {

    LobehubSsoTicketIssue issue(AuthPrincipal principal);

    LobehubSsoRedeemResult redeem(String ticket);

    LobehubModelIdentity authenticateModelGrant(String grant);

    void revokeModelGrant(String grant);
}
