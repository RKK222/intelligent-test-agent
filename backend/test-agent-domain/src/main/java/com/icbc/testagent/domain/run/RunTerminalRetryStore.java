package com.icbc.testagent.domain.run;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Redis 终态安全投影重试端口；实现不得持久化原始输入输出或回退 PostgreSQL 队列表。 */
public interface RunTerminalRetryStore {

    /** 新增待落库安全投影，或仅以更高 generation/事件序号/重试代次覆盖同一 Run，并设置绝对 TTL。 */
    void save(RunTerminalRetry retry);

    /** 查询单个 Run 的待落库状态，供恢复和测试诊断使用。 */
    Optional<RunTerminalRetry> find(RunId runId);

    /** 按 nextAttemptAt 返回已到期可执行的重试，返回数量不得超过 limit。 */
    List<RunTerminalRetry> findDue(Instant now, int limit);

    /**
     * 事务已成功、版本已由其它执行者收敛或详情已过期时按完整记录 CAS 删除。
     *
     * @return 仅当 Redis 中仍是调用方处理的同一版记录时返回 true；晚到新投影不得被旧执行者删除
     */
    boolean delete(RunTerminalRetry expected);
}
