package com.icbc.testagent.domain.run;

/** Redis 摘要模式允许单次低频写入 PostgreSQL 的 Diff 用户动作。 */
public enum RunDiffAction {
    ACCEPTED,
    REJECTED
}
