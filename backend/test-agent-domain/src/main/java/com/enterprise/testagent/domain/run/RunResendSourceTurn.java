package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Objects;

/** 关系型投影中的最后一条用户消息边界，不包含正文。 */
public record RunResendSourceTurn(RunId runId, String remoteMessageId) {

    public RunResendSourceTurn {
        Objects.requireNonNull(runId, "runId must not be null");
        remoteMessageId = DomainValidation.requireText(remoteMessageId, "remoteMessageId");
    }
}
