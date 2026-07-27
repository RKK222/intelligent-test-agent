package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.toolbox.ToolboxClickEvent;
import com.enterprise.testagent.domain.toolbox.ToolboxClickRepository;
import com.enterprise.testagent.domain.toolbox.ToolboxClickTotal;
import com.enterprise.testagent.domain.toolbox.ToolboxClickWriteResult;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 工具点击领域端口的 MyBatis XML 实现。 */
@Repository
public class MyBatisToolboxClickRepository implements ToolboxClickRepository {

    private final ToolboxClickMapper mapper;

    public MyBatisToolboxClickRepository(ToolboxClickMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /**
     * 先用 eventId 幂等落明细，再原子竞争用户/工具窗口；任一步失败由 Spring 回滚整个事务。
     */
    @Override
    @Transactional
    public ToolboxClickWriteResult record(ToolboxClickEvent event, Duration countingWindow) {
        Objects.requireNonNull(event, "event must not be null");
        if (countingWindow == null || countingWindow.isZero() || countingWindow.isNegative()) {
            throw new IllegalArgumentException("countingWindow must be positive");
        }
        ToolboxClickEventRow row = new ToolboxClickEventRow(
                event.eventId(),
                event.toolId(),
                event.source(),
                event.userId().value(),
                event.traceId(),
                false,
                event.clickedAt());
        if (mapper.insertEvent(row) == 0) {
            return new ToolboxClickWriteResult(currentCount(event.toolId()), false, false);
        }

        Instant windowStart = event.clickedAt().minus(countingWindow);
        boolean incremented = mapper.claimCountingWindow(
                event.toolId(), event.userId().value(), event.clickedAt(), windowStart) == 1;
        if (incremented) {
            mapper.incrementTotal(event.toolId(), event.clickedAt());
            mapper.markEventCounted(event.eventId());
        }
        return new ToolboxClickWriteResult(currentCount(event.toolId()), true, incremented);
    }

    /** 批量读取累计投影并保持调用方传入的目录顺序。 */
    @Override
    public Map<String, ToolboxClickTotal> findTotals(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Map.of();
        }
        Map<String, ToolboxClickTotal> totals = new LinkedHashMap<>();
        mapper.findTotals(toolIds).forEach(row -> totals.put(
                row.toolId(),
                new ToolboxClickTotal(row.toolId(), row.clickCount(), row.lastCountedAt())));
        return totals;
    }

    private long currentCount(String toolId) {
        ToolboxClickTotalRow total = mapper.findTotal(toolId);
        return total == null ? 0 : total.clickCount();
    }
}
