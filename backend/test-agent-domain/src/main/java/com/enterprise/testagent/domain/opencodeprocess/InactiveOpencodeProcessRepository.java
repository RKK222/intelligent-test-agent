package com.enterprise.testagent.domain.opencodeprocess;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 用户 OpenCode 进程闲置候选查询端口；关系型查询实现必须使用 MyBatis XML。
 */
public interface InactiveOpencodeProcessRepository {

    /**
     * 查询指定 Linux 服务器上严格早于截止时间的运行中候选，结果按最久未使用优先返回。
     */
    List<InactiveOpencodeProcessCandidate> findCandidates(
            LinuxServerId linuxServerId,
            Instant activityBefore,
            int limit);

    /**
     * 在用户空闲闸门内重新读取精确进程，避免扫描后新 Run 或进程重启造成误关。
     */
    Optional<InactiveOpencodeProcessCandidate> findCurrentCandidate(
            OpencodeProcessId processId,
            Instant activityBefore);
}
