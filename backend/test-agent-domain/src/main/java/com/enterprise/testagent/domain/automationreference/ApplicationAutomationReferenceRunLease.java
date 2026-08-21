package com.enterprise.testagent.domain.automationreference;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.util.Objects;

/** 根 Run 实际使用的自动化引用代次；仅用于共享副本生命周期，不进入消息或提示词。 */
public record ApplicationAutomationReferenceRunLease(
        ApplicationId appId,
        CodeRepositoryId repositoryId,
        long generation,
        LinuxServerId linuxServerId) {

    public ApplicationAutomationReferenceRunLease {
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        if (generation < 1L) {
            throw new IllegalArgumentException("generation must be positive");
        }
    }
}
