package com.enterprise.testagent.opencode.runtime.share;

import com.enterprise.testagent.domain.user.UserId;
import java.util.Objects;

/** 分享管理全量更新中的单个成员命令。 */
public record SessionShareMemberCommand(UserId userId, boolean canChat) {

    public SessionShareMemberCommand {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
