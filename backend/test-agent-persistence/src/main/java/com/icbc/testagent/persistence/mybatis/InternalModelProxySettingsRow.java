package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * internal_model_proxy_settings 表的 MyBatis 行模型。
 */
public record InternalModelProxySettingsRow(
        String settingId,
        String icbcOpenaiAuthToken,
        Instant createdAt,
        Instant updatedAt) {
}
