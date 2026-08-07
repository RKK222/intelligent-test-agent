package com.enterprise.testagent.domain.internalmodelobservability;

import java.util.List;

/** 内部模型 provider 探活状态的关系型持久化端口。 */
public interface InternalModelProbeStatusRepository {

    /** upsert 单 provider 探活状态；连续失败计数由 SQL 依据本次结果自动递增/归零。 */
    void upsert(InternalModelProbeStatus status);

    List<InternalModelProbeStatus> findAll();
}
