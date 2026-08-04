package com.enterprise.testagent.domain.opencodeprocess;

import java.time.Instant;
import java.util.Objects;

/**
 * 超过闲置阈值的用户 OpenCode 进程候选，携带扫描时看到的完整进程代次和最近使用时间。
 */
public record InactiveOpencodeProcessCandidate(
        OpencodeServerProcess process,
        Instant lastActivityAt) {

    /** 候选必须同时具备可停止的进程快照和明确的最近活动时间。 */
    public InactiveOpencodeProcessCandidate {
        Objects.requireNonNull(process, "process must not be null");
        Objects.requireNonNull(lastActivityAt, "lastActivityAt must not be null");
    }
}
