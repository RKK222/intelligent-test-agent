package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.user.UserId;

/** 密钥变更或新实例接管时跨后台节点立即关闭用户客户端连接的端口。 */
public interface LocalClientConnectionRevoker {

    void revokeAll(UserId userId, String reason, String traceId);

    /** 保留刚完成认证的当前 generation，并撤销该用户其它全部实时连接。 */
    void revokeAllExcept(
            UserId userId,
            LocalClientInstanceId retainedClientInstanceId,
            long retainedGeneration,
            String reason,
            String traceId);
}
