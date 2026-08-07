package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.support.DomainValidation;

/** 手动撤销重发入口的服务层命令。 */
public record CreateRunResendCommand(
        String expectedRemoteMessageId,
        RunId expectedRunId,
        String contextToken,
        String clientRequestId) {

    public CreateRunResendCommand {
        expectedRemoteMessageId = DomainValidation.requireText(
                expectedRemoteMessageId, "expectedRemoteMessageId");
        contextToken = DomainValidation.requireText(contextToken, "contextToken");
        clientRequestId = DomainValidation.requireText(clientRequestId, "clientRequestId");
    }
}
