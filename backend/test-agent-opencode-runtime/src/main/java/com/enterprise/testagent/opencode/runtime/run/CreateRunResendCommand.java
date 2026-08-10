package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.support.DomainValidation;

/** 手动撤销重发入口的服务层命令。 */
public record CreateRunResendCommand(
        String expectedRemoteMessageId,
        RunId expectedRunId,
        String contextToken,
        String clientRequestId,
        String editedPrompt) {

    /** 兼容自动重发和既有调用方：未提供编辑内容时精确重放原始用户轮次。 */
    public CreateRunResendCommand(
            String expectedRemoteMessageId,
            RunId expectedRunId,
            String contextToken,
            String clientRequestId) {
        this(expectedRemoteMessageId, expectedRunId, contextToken, clientRequestId, null);
    }

    public CreateRunResendCommand {
        expectedRemoteMessageId = DomainValidation.requireText(
                expectedRemoteMessageId, "expectedRemoteMessageId");
        contextToken = DomainValidation.requireText(contextToken, "contextToken");
        clientRequestId = DomainValidation.requireText(clientRequestId, "clientRequestId");
        editedPrompt = editedPrompt == null
                ? null
                : DomainValidation.requireText(editedPrompt, "editedPrompt");
    }
}
