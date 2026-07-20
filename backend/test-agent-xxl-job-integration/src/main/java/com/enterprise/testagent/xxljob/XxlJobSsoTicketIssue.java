package com.enterprise.testagent.xxljob;

import java.time.Instant;

/** 平台入口返回给前端的一次性表单票据。 */
public record XxlJobSsoTicketIssue(String ticket, Instant expiresAt, String formAction) {
}
