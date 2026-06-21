package com.icbc.testagent.opencode.client;

import com.icbc.testagent.domain.support.DomainValidation;

/**
 * 远端 opencode 会话创建结果，只向应用层暴露远端 session id。
 */
public record OpencodeCreateSessionResult(String opencodeSessionId) {

    /**
     * 校验远端 session id 非空，避免无效 session 继续进入运行编排。
     */
    public OpencodeCreateSessionResult {
        opencodeSessionId = DomainValidation.requireText(opencodeSessionId, "opencodeSessionId");
    }
}
