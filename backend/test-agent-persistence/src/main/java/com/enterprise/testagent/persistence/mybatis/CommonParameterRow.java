package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * common_parameters 表的 MyBatis 行模型，只在 persistence 模块内部使用。
 */
public record CommonParameterRow(
        String parameterId,
        String parameterEnglish,
        String parameterChinese,
        String parameterValue,
        String platform,
        boolean editable,
        Instant createdAt,
        Instant updatedAt) {
}
