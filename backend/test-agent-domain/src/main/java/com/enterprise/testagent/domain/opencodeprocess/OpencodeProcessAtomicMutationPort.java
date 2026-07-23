package com.enterprise.testagent.domain.opencodeprocess;

/**
 * 用户进程权威分配的原子变更端口。
 *
 * <p>分配迁移必须同时比较并更新 process 与 binding；process 条件除 assignment 坐标外还要匹配旧
 * status/PID/traceId 生命周期代次。运行态回写只能修改状态字段，并使用同一代次条件，防止迟到快照
 * 覆盖同坐标的新实例或新分配。
 */
public interface OpencodeProcessAtomicMutationPort {

    void compareAndSetAssignment(
            OpencodeServerProcess expectedProcess,
            UserOpencodeProcessBinding expectedBinding,
            OpencodeServerProcess replacementProcess,
            UserOpencodeProcessBinding replacementBinding);

    boolean compareAndSetRuntimeState(
            OpencodeServerProcess expectedAssignment,
            OpencodeServerProcess replacementState);
}
