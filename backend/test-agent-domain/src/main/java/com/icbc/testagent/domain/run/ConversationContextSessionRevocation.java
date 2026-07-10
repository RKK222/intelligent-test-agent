package com.icbc.testagent.domain.run;

import com.icbc.testagent.domain.session.SessionId;
import java.util.Objects;

/**
 * 会话归档前取得的 Redis 撤销凭证；归档失败时只能撤回自己写入的 gate 成员。
 */
public record ConversationContextSessionRevocation(SessionId sessionId, String revokeToken) {

    public ConversationContextSessionRevocation {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (revokeToken == null || revokeToken.isBlank()) {
            throw new IllegalArgumentException("revokeToken must not be blank");
        }
        revokeToken = revokeToken.trim();
    }
}
