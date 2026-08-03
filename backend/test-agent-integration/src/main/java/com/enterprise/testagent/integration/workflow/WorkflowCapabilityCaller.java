package com.enterprise.testagent.integration.workflow;

import com.enterprise.testagent.domain.user.UserId;
import java.util.Objects;

/** HMAC验证后可传给窄能力服务的最小平台调用身份。 */
public record WorkflowCapabilityCaller(UserId userId, String sessionDigest) {
    public WorkflowCapabilityCaller {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionDigest, "sessionDigest must not be null");
    }
}
