package com.example.testagent.opencode.client;

import com.example.testagent.domain.support.DomainValidation;

/**
 * 远端 opencode 会话创建结果，只向应用层暴露远端 session id。
 */
public record OpencodeCreateSessionResult(String opencodeSessionId) {

    public OpencodeCreateSessionResult {
        opencodeSessionId = DomainValidation.requireText(opencodeSessionId, "opencodeSessionId");
    }
}
