package com.enterprise.testagent.domain.run;

/** 单次 Run 的 Diff 终态计数；只保存数量，不保存补丁原文。 */
public record RunDiffCounts(int proposed, int accepted, int rejected) {

    public RunDiffCounts {
        if (proposed < 0 || accepted < 0 || rejected < 0) {
            throw new IllegalArgumentException("diff counts must not be negative");
        }
    }

    /** 返回没有 Diff 动作的计数快照。 */
    public static RunDiffCounts empty() {
        return new RunDiffCounts(0, 0, 0);
    }
}
