package com.enterprise.testagent.integration.lobehub;

import java.time.Instant;

/** 平台前端可用于隐藏表单 POST 交接的短期票据。 */
public record LobehubSsoTicketIssue(String ticket, Instant expiresAt, String consumeUrl) {
}
