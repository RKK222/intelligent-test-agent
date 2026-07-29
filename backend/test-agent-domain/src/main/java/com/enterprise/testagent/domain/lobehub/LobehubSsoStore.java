package com.enterprise.testagent.domain.lobehub;

import java.time.Duration;
import java.util.Optional;

/** LobeHub SSO 的一次性票据、HMAC nonce 和模型委托存储端口。 */
public interface LobehubSsoStore {

    /** 保存票据摘要；实现不得持久化原始票据。 */
    void saveTicket(String ticketDigest, LobehubTicketPayload payload, Duration ttl);

    /** 原子读取并删除票据，重放必须返回 empty。 */
    Optional<LobehubTicketPayload> consumeTicket(String ticketDigest);

    /** 原子占用 nonce 摘要，已存在时返回 false。 */
    boolean reserveNonce(String nonceDigest, Duration ttl);

    /** 为用户轮换委托，保证旧委托立即失效。 */
    void rotateGrant(String userId, String grantDigest, LobehubGrantPayload payload, Duration ttl);

    /** 按摘要读取有效委托。 */
    Optional<LobehubGrantPayload> findGrant(String grantDigest);

    /** 显式撤销指定委托。 */
    void revokeGrant(String grantDigest);
}
