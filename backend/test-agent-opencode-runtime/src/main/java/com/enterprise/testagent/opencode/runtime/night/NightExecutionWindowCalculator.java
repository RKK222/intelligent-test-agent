package com.enterprise.testagent.opencode.runtime.night;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 北京时间夜间窗口与 15 分钟启动时段计算器。 */
@Component
public class NightExecutionWindowCalculator {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final Duration SLOT_DURATION = Duration.ofMinutes(15);
    private static final LocalTime WINDOW_START = LocalTime.of(21, 0);
    private static final LocalTime WINDOW_END = LocalTime.of(7, 0);

    public NightExecutionWindow nextWindow(Instant now, Map<Instant, Integer> reservations, int capacity) {
        ZonedDateTime localNow = now.atZone(ZONE);
        LocalDate startDate;
        if (localNow.toLocalTime().isBefore(WINDOW_END)) {
            startDate = localNow.toLocalDate().minusDays(1);
        } else {
            startDate = localNow.toLocalDate();
        }
        ZonedDateTime windowStart = startDate.atTime(WINDOW_START).atZone(ZONE);
        ZonedDateTime windowEnd = startDate.plusDays(1).atTime(WINDOW_END).atZone(ZONE);
        ZonedDateTime first = localNow.isAfter(windowStart) ? ceilQuarter(localNow) : windowStart;
        if (!first.isBefore(windowEnd)) {
            windowStart = localNow.toLocalDate().atTime(WINDOW_START).atZone(ZONE);
            if (!windowStart.isAfter(localNow)) windowStart = windowStart.plusDays(1);
            windowEnd = windowStart.toLocalDate().plusDays(1).atTime(WINDOW_END).atZone(ZONE);
            first = windowStart;
        }
        List<NightExecutionSlot> slots = new ArrayList<>();
        for (ZonedDateTime cursor = first; cursor.isBefore(windowEnd); cursor = cursor.plusMinutes(15)) {
            Instant slotStart = cursor.toInstant();
            int reserved = reservations == null ? 0 : reservations.getOrDefault(slotStart, 0);
            slots.add(new NightExecutionSlot(
                    slotStart, cursor.plusMinutes(15).toInstant(), reserved, capacity, reserved < capacity, false));
        }
        slots.stream().filter(NightExecutionSlot::available)
                .min(Comparator.comparingInt(NightExecutionSlot::reservedCount)
                        .thenComparing(NightExecutionSlot::slotStart))
                .ifPresent(recommended -> {
                    int index = slots.indexOf(recommended);
                    slots.set(index, recommended.withRecommended());
                });
        return new NightExecutionWindow(
                ZONE.getId(), windowStart.toInstant(), windowEnd.toInstant(), capacity, List.copyOf(slots));
    }

    public boolean belongsToWindow(Instant slotStart, NightExecutionWindow window) {
        return !slotStart.isBefore(window.windowStart())
                && slotStart.isBefore(window.windowEnd())
                && Duration.between(window.windowStart(), slotStart).toMinutes() % 15 == 0;
    }

    private ZonedDateTime ceilQuarter(ZonedDateTime value) {
        ZonedDateTime minute = value.truncatedTo(ChronoUnit.MINUTES);
        if (value.equals(minute) && minute.getMinute() % 15 == 0) return minute;
        int minutes = 15 - minute.getMinute() % 15;
        return minute.plusMinutes(minutes);
    }

    public record NightExecutionWindow(
            String timeZone,
            Instant windowStart,
            Instant windowEnd,
            int capacity,
            List<NightExecutionSlot> slots) { }

    public record NightExecutionSlot(
            Instant slotStart,
            Instant slotEnd,
            int reservedCount,
            int capacity,
            boolean available,
            boolean recommended) {
        NightExecutionSlot withRecommended() {
            return new NightExecutionSlot(slotStart, slotEnd, reservedCount, capacity, available, true);
        }
    }
}
