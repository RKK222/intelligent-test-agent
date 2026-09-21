package com.enterprise.testagent.system.management.team;

/** 团队特权访问审计需要的安全请求快照。 */
public record TeamOversightRequestContext(String traceId, String ipAddress, String userAgent) {
}
