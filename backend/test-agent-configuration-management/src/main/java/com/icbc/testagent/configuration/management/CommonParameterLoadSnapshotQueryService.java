package com.icbc.testagent.configuration.management;

import com.icbc.testagent.domain.configuration.CommonParameterLoadSnapshot;
import com.icbc.testagent.domain.configuration.CommonParameterLoadSnapshotStore;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 通用参数每进程加载值聚合查询服务；从共享存储读取所有存活后端实例的加载快照，供管理端展示。
 */
@Service
public class CommonParameterLoadSnapshotQueryService {

    private final CommonParameterLoadSnapshotStore store;

    public CommonParameterLoadSnapshotQueryService(CommonParameterLoadSnapshotStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    /**
     * 返回所有存活后端进程的加载快照；结果由存储端按后端进程 ID 稳定排序。
     */
    public List<CommonParameterLoadSnapshot> list() {
        return store.liveSnapshots();
    }
}
