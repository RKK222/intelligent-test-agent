package com.enterprise.testagent.system.management.localclient;

import com.enterprise.testagent.domain.user.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** client key 审计日志；仅记录用户、动作、结果和 traceId，禁止记录明文、密文或摘要。 */
@Component
public class LocalClientCredentialAuditLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalClientCredentialAuditLogger.class);

    public void success(UserId userId, String action, String traceId) {
        LOGGER.info(
                "event=local_client_credential_audit action={} result=SUCCESS userId={} traceId={}",
                action,
                userId.value(),
                traceId);
    }

    public void failure(UserId userId, String action, String traceId, String reason) {
        LOGGER.warn(
                "event=local_client_credential_audit action={} result=FAILED userId={} traceId={} reason={}",
                action,
                userId.value(),
                traceId,
                reason);
    }
}
