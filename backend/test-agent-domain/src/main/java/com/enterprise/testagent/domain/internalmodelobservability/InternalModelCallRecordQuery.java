package com.enterprise.testagent.domain.internalmodelobservability;

import com.enterprise.testagent.common.pagination.PageRequest;
import java.time.Instant;
import java.util.Objects;

/** 明细查询条件；空值表示不限制。from/to 均已归一化，避免非法时间范围压垮索引扫描。 */
public record InternalModelCallRecordQuery(
        String providerId,
        InternalModelCallOutcome outcome,
        InternalModelCallSource source,
        Instant from,
        Instant to,
        PageRequest page) {

    public InternalModelCallRecordQuery {
        providerId = normalize(providerId);
        Objects.requireNonNull(page, "page must not be null");
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
