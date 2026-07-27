package com.enterprise.testagent.integration.toolbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 工具目录和点击业务规则单元测试。 */
class ToolboxCatalogServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");
    private static final UserId USER_ID = new UserId("usr_toolbox123456");

    @Test
    void firstClickIncrementsAndRepeatWithinThirtySecondsOnlyKeepsEvent() {
        InMemoryRepository repository = new InMemoryRepository();
        ToolboxCatalogService service = service(repository, NOW);

        ToolboxClickResult first = service.recordClick("it-tools.hash-text", "evt_first", USER_ID, "trace_first");
        ToolboxClickResult repeated = service.recordClick("it-tools.hash-text", "evt_repeat", USER_ID, "trace_repeat");

        assertThat(first).isEqualTo(new ToolboxClickResult("it-tools.hash-text", 1, true, true));
        assertThat(repeated).isEqualTo(new ToolboxClickResult("it-tools.hash-text", 1, true, false));
        assertThat(repository.events).hasSize(2);
        assertThat(repository.events.get(1).counted()).isFalse();
    }

    @Test
    void duplicateEventIdIsIdempotentAndDoesNotCreateAnotherDetail() {
        InMemoryRepository repository = new InMemoryRepository();
        ToolboxCatalogService service = service(repository, NOW);

        service.recordClick("it-tools.hash-text", "evt_same", USER_ID, "trace_first");
        ToolboxClickResult duplicate = service.recordClick(
                "it-tools.hash-text", "evt_same", USER_ID, "trace_duplicate");

        assertThat(duplicate).isEqualTo(new ToolboxClickResult("it-tools.hash-text", 1, false, false));
        assertThat(repository.events).hasSize(1);
    }

    @Test
    void clickAfterWindowExpiresIncrementsAgain() {
        InMemoryRepository repository = new InMemoryRepository();
        service(repository, NOW).recordClick("it-tools.hash-text", "evt_first", USER_ID, "trace_first");

        ToolboxClickResult later = service(repository, NOW.plusSeconds(30))
                .recordClick("it-tools.hash-text", "evt_later", USER_ID, "trace_later");

        assertThat(later.clickCount()).isEqualTo(2);
        assertThat(later.incremented()).isTrue();
    }

    @Test
    void catalogRanksOnlyPositiveTopTenByCountTimeAndCatalogOrder() {
        InMemoryRepository repository = new InMemoryRepository();
        ToolboxCatalog catalog = catalog(12);
        for (int index = 0; index < 12; index++) {
            String toolId = catalog.tools().get(index).toolId();
            repository.totals.put(toolId, new ToolboxClickTotal(
                    toolId,
                    index == 11 ? 0 : index + 1,
                    NOW.plusSeconds(index)));
        }

        ToolboxCatalogResponse response = new ToolboxCatalogService(
                catalog,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30),
                10).catalog();

        assertThat(response.hotLimit()).isEqualTo(10);
        assertThat(response.tools().stream().filter(tool -> tool.hotRank() != null)).hasSize(10);
        assertThat(response.tools().stream()
                        .filter(tool -> tool.hotRank() != null)
                        .sorted(java.util.Comparator.comparing(ToolboxToolView::hotRank))
                        .map(ToolboxToolView::toolId))
                .containsExactly(
                        "it-tools.tool-10", "it-tools.tool-9", "it-tools.tool-8", "it-tools.tool-7",
                        "it-tools.tool-6", "it-tools.tool-5", "it-tools.tool-4", "it-tools.tool-3",
                        "it-tools.tool-2", "it-tools.tool-1");
    }

    @Test
    void catalogWithoutClicksKeepsZeroCountsAndEmptyHotRanking() {
        ToolboxCatalogResponse response = service(new InMemoryRepository(), NOW).catalog();

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.tools()).allSatisfy(tool -> {
            assertThat(tool.clickCount()).isZero();
            assertThat(tool.hotRank()).isNull();
        });
    }

    @Test
    void invalidOrOfflineToolUsesUnifiedNotFoundError() {
        ToolboxCatalogService service = service(new InMemoryRepository(), NOW);

        assertThatThrownBy(() -> service.recordClick("it-tools.camera-recorder", "evt_bad", USER_ID, "trace_bad"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private static ToolboxCatalogService service(InMemoryRepository repository, Instant now) {
        return new ToolboxCatalogService(
                catalog(2),
                repository,
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofSeconds(30),
                10);
    }

    private static ToolboxCatalog catalog(int size) {
        List<ToolboxToolDefinition> tools = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            String slug = index == 0 ? "hash-text" : "tool-" + index;
            tools.add(new ToolboxToolDefinition(
                    "it-tools." + slug,
                    ToolboxSource.IT_TOOLS,
                    "2024.10.22-7ca5933",
                    "工具 " + index,
                    "Tool " + index,
                    "用于测试的工具",
                    ToolboxCategory.SECURITY,
                    List.of("test", slug),
                    "/toolbox/apps/it-tools/" + slug,
                    index));
        }
        return new ToolboxCatalog("test-catalog", tools);
    }

    /** 以内存语义模拟数据库幂等键和 30 秒用户/工具计数窗口。 */
    private static final class InMemoryRepository implements ToolboxClickRepository {

        private final Map<String, ToolboxClickEvent> byEventId = new LinkedHashMap<>();
        private final Map<String, Instant> lastCounted = new HashMap<>();
        private final Map<String, ToolboxClickTotal> totals = new HashMap<>();
        private final List<ToolboxClickEvent> events = new ArrayList<>();

        @Override
        public ToolboxClickWriteResult record(ToolboxClickEvent event, Duration countingWindow) {
            if (byEventId.containsKey(event.eventId())) {
                return new ToolboxClickWriteResult(currentCount(event.toolId()), false, false);
            }
            String stateKey = event.userId().value() + "\n" + event.toolId();
            Instant previous = lastCounted.get(stateKey);
            boolean incremented = previous == null
                    || !event.clickedAt().isBefore(previous.plus(countingWindow));
            ToolboxClickEvent stored = event.withCounted(incremented);
            byEventId.put(event.eventId(), stored);
            events.add(stored);
            if (incremented) {
                lastCounted.put(stateKey, event.clickedAt());
                long next = currentCount(event.toolId()) + 1;
                totals.put(event.toolId(), new ToolboxClickTotal(event.toolId(), next, event.clickedAt()));
            }
            return new ToolboxClickWriteResult(currentCount(event.toolId()), true, incremented);
        }

        @Override
        public Map<String, ToolboxClickTotal> findTotals(List<String> toolIds) {
            Map<String, ToolboxClickTotal> result = new HashMap<>();
            toolIds.forEach(toolId -> Optional.ofNullable(totals.get(toolId))
                    .ifPresent(total -> result.put(toolId, total)));
            return result;
        }

        private long currentCount(String toolId) {
            return Optional.ofNullable(totals.get(toolId)).map(ToolboxClickTotal::clickCount).orElse(0L);
        }
    }
}
