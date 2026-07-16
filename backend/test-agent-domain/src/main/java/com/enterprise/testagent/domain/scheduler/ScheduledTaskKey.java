package com.enterprise.testagent.domain.scheduler;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 定时任务业务标识，作为代码注册、数据库定义、Redis 锁和运行记录的稳定关联键。
 */
public record ScheduledTaskKey(String value) {

    private static final Pattern SAFE_KEY = Pattern.compile("[a-z0-9][a-z0-9._-]{2,127}");

    /**
     * 规范化任务 key，禁止路径分隔符和空白值进入锁 key 或 SQL 查询条件。
     */
    public ScheduledTaskKey {
        value = Objects.requireNonNull(value, "taskKey must not be null").trim().toLowerCase(Locale.ROOT);
        if (!SAFE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("taskKey must match [a-z0-9][a-z0-9._-]{2,127}");
        }
    }
}
