package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import java.util.Objects;

/**
 * Run 的双身份快照：executionOwner 用于 OpenCode，actualSender 只用于平台归因和授权。
 */
public record RunActorAttribution(
        UserId executionOwnerUserId,
        UserId actualSenderUserId,
        String actualSenderUnifiedAuthId,
        boolean sentBySharedUser) {

    public RunActorAttribution {
        if (sentBySharedUser) {
            Objects.requireNonNull(executionOwnerUserId, "executionOwnerUserId must not be null");
            Objects.requireNonNull(actualSenderUserId, "actualSenderUserId must not be null");
            if (actualSenderUnifiedAuthId == null || actualSenderUnifiedAuthId.isBlank()) {
                throw new IllegalArgumentException("actualSenderUnifiedAuthId must not be blank");
            }
        }
    }

    /** 普通入口下实际发送人和执行所属人为同一用户。 */
    public static RunActorAttribution direct(UserId userId) {
        return new RunActorAttribution(userId, userId, null, false);
    }

    /** 分享入口保留真实 actor，同时显式使用所属人执行。 */
    public static RunActorAttribution from(DelegatedOperationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return new RunActorAttribution(
                context.executionOwnerUserId(),
                context.actorUserId(),
                context.actorUnifiedAuthId(),
                context.delegated());
    }
}
