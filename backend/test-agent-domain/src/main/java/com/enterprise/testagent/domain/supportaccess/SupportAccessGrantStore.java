package com.enterprise.testagent.domain.supportaccess;

import java.time.Duration;
import java.util.Optional;

/**
 * 排查授权短期状态端口；同一登录会话只允许最后一次签发的授权继续生效。
 */
public interface SupportAccessGrantStore {

    /** 原子语义上把会话当前授权切换为 payload，并返回被替换的旧授权。 */
    Optional<SupportAccessGrantSession> rotate(SupportAccessGrantSession payload, Duration ttl);

    /** 按授权 Token 摘要查询，并再次核对它仍是登录会话的当前授权。 */
    Optional<SupportAccessGrantSession> findByTokenDigest(String grantTokenDigest);

    /** 仅当会话当前授权仍匹配时撤销，避免旧请求删除后签发的新授权。 */
    void revoke(SupportAccessGrantSession payload);
}
