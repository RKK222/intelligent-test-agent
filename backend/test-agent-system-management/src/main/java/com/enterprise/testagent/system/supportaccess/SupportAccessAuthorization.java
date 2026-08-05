package com.enterprise.testagent.system.supportaccess;

import com.enterprise.testagent.domain.supportaccess.SupportAccessGrant;
import com.enterprise.testagent.domain.supportaccess.SupportAccessGrantSession;
import com.enterprise.testagent.domain.user.User;

/** 已实时校验的排查访问上下文，actor 始终是当前超级管理员，不发生身份切换。 */
public record SupportAccessAuthorization(
        SupportAccessGrant grant,
        SupportAccessGrantSession session,
        User actor,
        User target) {
}
