package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.user.UserId;
import java.util.Objects;

/**
 * 用户权限变更窗口的 Redis gate 凭证；完成或回滚时只能释放当前操作写入的成员。
 */
public record ConversationContextUserMutation(UserId userId, String mutationToken) {

    public ConversationContextUserMutation {
        Objects.requireNonNull(userId, "userId must not be null");
        if (mutationToken == null || mutationToken.isBlank()) {
            throw new IllegalArgumentException("mutationToken must not be blank");
        }
        mutationToken = mutationToken.trim();
    }
}
