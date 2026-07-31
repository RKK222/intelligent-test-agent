package com.enterprise.testagent.domain.workflowcapability;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Python工作流共享能力的一次性票据、nonce和短期模型委托存储端口。 */
public interface WorkflowCapabilityStore {

    /** 原子占用不可逆nonce摘要；调用方不得保存原始nonce。 */
    boolean reserveNonce(String nonceDigest, Duration ttl);

    void saveCheckoutTicket(String ticketDigest, CheckoutTicketPayload payload, Duration ttl);

    Optional<CheckoutTicketPayload> consumeCheckoutTicket(String ticketDigest);

    /**
     * 仅当run未被取消时原子保存grant、grantId索引和run索引；取消墓碑存在时返回false。
     */
    boolean saveModelGrant(
            String grantId,
            String grantDigest,
            WorkflowModelGrantPayload payload,
            Duration ttl);

    Optional<WorkflowModelGrantPayload> findModelGrant(String grantDigest);

    Optional<WorkflowModelGrantPayload> findModelGrantById(String grantId);

    /** 在grantId仍存在时原子延长同一个不透明grant，避免运行中的客户端更换Bearer。 */
    boolean refreshModelGrant(
            String grantId,
            WorkflowModelGrantPayload payload,
            Duration ttl);

    List<WorkflowModelGrantPayload> findModelGrants(String taskId, String runId);

    void revokeModelGrant(String grantId);

    /** 先写run级取消墓碑，再撤销已有grant，阻止与取消并发的新委托漏网。 */
    void revokeModelGrants(String taskId, String runId, Duration tombstoneTtl);
}
