package com.enterprise.testagent.domain.opencodeprocess;

/** 权威进程或 binding 已被并发修改，当前原子变更必须整体放弃。 */
public final class OpencodeProcessAssignmentConflictException extends RuntimeException {

    public OpencodeProcessAssignmentConflictException(String message) {
        super(message);
    }
}
