package com.enterprise.testagent.integration.toolbox;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.toolbox.ToolboxClickEvent;
import com.enterprise.testagent.domain.toolbox.ToolboxClickRepository;
import com.enterprise.testagent.domain.toolbox.ToolboxClickTotal;
import com.enterprise.testagent.domain.toolbox.ToolboxClickWriteResult;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 工具目录查询与点击计数业务服务。 */
@Service
public class ToolboxCatalogService {

    private static final Duration DEFAULT_COUNTING_WINDOW = Duration.ofSeconds(30);
    private static final int DEFAULT_HOT_LIMIT = 10;

    private final ToolboxCatalog catalog;
    private final Map<String, ToolboxToolDefinition> toolsById;
    private final ToolboxClickRepository clickRepository;
    private final Clock clock;
    private final Duration countingWindow;
    private final int hotLimit;

    /** 生产构造器固定使用版本化 classpath 目录、UTC 服务端时间和 30 秒窗口。 */
    @Autowired
    public ToolboxCatalogService(ToolboxClickRepository clickRepository) {
        this(
                ToolboxCatalogLoader.loadDefault(),
                clickRepository,
                Clock.systemUTC(),
                DEFAULT_COUNTING_WINDOW,
                DEFAULT_HOT_LIMIT);
    }

    /** 测试构造器允许固定时钟和小目录验证边界，不对外暴露可变运行配置。 */
    ToolboxCatalogService(
            ToolboxCatalog catalog,
            ToolboxClickRepository clickRepository,
            Clock clock,
            Duration countingWindow,
            int hotLimit) {
        this.catalog = catalog;
        this.clickRepository = clickRepository;
        this.clock = clock;
        this.countingWindow = countingWindow;
        this.hotLimit = hotLimit;
        this.toolsById = catalog.tools().stream()
                .collect(Collectors.toUnmodifiableMap(ToolboxToolDefinition::toolId, Function.identity()));
    }

    /** 返回完整目录，并从累计投影计算正点击 Top 10。 */
    public ToolboxCatalogResponse catalog() {
        List<String> toolIds = catalog.tools().stream().map(ToolboxToolDefinition::toolId).toList();
        Map<String, ToolboxClickTotal> totals = clickRepository.findTotals(toolIds);
        Map<String, Integer> ranks = hotRanks(totals);
        List<ToolboxToolView> views = catalog.tools().stream()
                .map(tool -> toView(tool, totals.get(tool.toolId()), ranks.get(tool.toolId())))
                .toList();
        return new ToolboxCatalogResponse(catalog.catalogVersion(), views.size(), hotLimit, views);
    }

    /** 记录一次点击；无效或离线剔除工具统一按资源不存在处理。 */
    public ToolboxClickResult recordClick(String toolId, String eventId, UserId userId, String traceId) {
        ToolboxToolDefinition tool = toolsById.get(toolId);
        if (tool == null) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "工具不存在或当前离线版本不可用");
        }
        Instant clickedAt = clock.instant();
        ToolboxClickWriteResult result = clickRepository.record(
                new ToolboxClickEvent(
                        eventId,
                        tool.toolId(),
                        tool.source().name(),
                        userId,
                        traceId,
                        clickedAt,
                        false),
                countingWindow);
        return new ToolboxClickResult(tool.toolId(), result.clickCount(), result.recorded(), result.incremented());
    }

    private Map<String, Integer> hotRanks(Map<String, ToolboxClickTotal> totals) {
        Comparator<ToolboxClickTotal> comparator = Comparator
                .comparingLong(ToolboxClickTotal::clickCount).reversed()
                .thenComparing(ToolboxClickTotal::lastCountedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingInt(total -> toolsById.get(total.toolId()).catalogOrder());
        List<ToolboxClickTotal> hot = totals.values().stream()
                .filter(total -> total.clickCount() > 0 && toolsById.containsKey(total.toolId()))
                .sorted(comparator)
                .limit(hotLimit)
                .toList();
        Map<String, Integer> ranks = new HashMap<>();
        for (int index = 0; index < hot.size(); index++) {
            ranks.put(hot.get(index).toolId(), index + 1);
        }
        return ranks;
    }

    private ToolboxToolView toView(ToolboxToolDefinition tool, ToolboxClickTotal total, Integer hotRank) {
        long clickCount = total == null ? 0 : total.clickCount();
        return new ToolboxToolView(
                tool.toolId(),
                tool.source(),
                tool.source().displayName(),
                tool.sourceVersion(),
                tool.nameZh(),
                tool.nameEn(),
                tool.descriptionZh(),
                tool.category(),
                tool.category().labelZh(),
                tool.keywords(),
                tool.launchPath(),
                clickCount,
                hotRank);
    }
}
