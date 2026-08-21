package com.enterprise.testagent.common.localclient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Objects;
import java.util.regex.Pattern;

/** 北京时间 {@code yyyyMMddHHmmss} 格式的不可变客户端发布版本。 */
public record LocalClientReleaseVersion(String value) implements Comparable<LocalClientReleaseVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d{14}");
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("uuuuMMddHHmmss")
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    public LocalClientReleaseVersion {
        Objects.requireNonNull(value, "version must not be null");
        if (!VERSION_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("客户端发布版本必须是 14 位北京时间时间戳");
        }
        try {
            LocalDateTime.parse(value, FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("客户端发布版本不是有效的北京时间时间戳", exception);
        }
    }

    public static LocalClientReleaseVersion parse(String value) {
        return new LocalClientReleaseVersion(value);
    }

    /** 固定宽度时间戳可按字符串顺序比较，避免不同节点受到时区影响。 */
    @Override
    public int compareTo(LocalClientReleaseVersion other) {
        return value.compareTo(Objects.requireNonNull(other, "other must not be null").value);
    }

    public LocalClientUpdateDirection directionTo(LocalClientReleaseVersion target) {
        int comparison = Objects.requireNonNull(target, "target must not be null").compareTo(this);
        if (comparison > 0) {
            return LocalClientUpdateDirection.UPDATE;
        }
        if (comparison < 0) {
            return LocalClientUpdateDirection.ROLLBACK;
        }
        return LocalClientUpdateDirection.SAME;
    }

    @Override
    public String toString() {
        return value;
    }
}
