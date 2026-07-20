package com.enterprise.testagent.xxljob;

/** XXL adapter 的安全结果状态。锁已占用按成功跳过记录。 */
public enum XxlJobTaskExecutionStatus {
    SUCCEEDED,
    SKIPPED_LOCK_HELD
}
