package com.enterprise.testagent.domain.toolbox;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 工具点击持久化端口，具体幂等和并发竞争由关系型数据库适配器保证。 */
public interface ToolboxClickRepository {

    /**
     * 在单个事务中保存明细、竞争用户工具计数窗口并按需更新累计数。
     *
     * @param event 服务端生成时间的点击明细
     * @param countingWindow 同一用户同一工具的去重计数窗口
     * @return 事件是否新写入、是否增加累计以及最新累计数
     */
    ToolboxClickWriteResult record(ToolboxClickEvent event, Duration countingWindow);

    /** 批量读取目录中已有累计投影，缺失工具按零次处理。 */
    Map<String, ToolboxClickTotal> findTotals(List<String> toolIds);
}
