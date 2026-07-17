package com.enterprise.testagent.domain.configuration;

import java.util.List;

/**
 * 通用参数修改日志持久化端口；业务模块通过此端口记录和查询修改历史。
 */
public interface CommonParameterChangeLogRepository {

    /**
     * 保存修改日志。
     */
    void save(CommonParameterChangeLog log);

    /**
     * 按参数业务 ID 查询修改日志，按修改时间倒序排列。
     *
     * @param parameterId 参数业务 ID
     * @param limit 最大返回条数
     * @return 修改日志列表
     */
    List<CommonParameterChangeLog> findByParameterId(String parameterId, int limit);
}
