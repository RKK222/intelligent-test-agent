package com.icbc.testagent.domain.configuration;

import java.util.List;

/**
 * 通用参数加载快照共享存储端口；每台后端实例写入自己的加载快照，管理端聚合读取所有存活快照。
 *
 * <p>实现负责 TTL 与索引维护：进程崩溃后快照应在短期内自动失效，避免展示已离线进程的陈旧值。
 */
public interface CommonParameterLoadSnapshotStore {

    /**
     * 记录或刷新指定后端进程的加载快照。
     */
    void record(CommonParameterLoadSnapshot snapshot);

    /**
     * 返回所有未过期的加载快照；结果按后端进程 ID 稳定排序。
     */
    List<CommonParameterLoadSnapshot> liveSnapshots();
}
