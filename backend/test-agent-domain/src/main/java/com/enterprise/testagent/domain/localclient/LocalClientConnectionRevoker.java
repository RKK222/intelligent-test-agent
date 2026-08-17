package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.user.UserId;

/** 密钥轮换或撤销时跨后台节点立即关闭用户全部客户端连接的端口。 */
public interface LocalClientConnectionRevoker {

    void revokeAll(UserId userId, String reason, String traceId);
}
