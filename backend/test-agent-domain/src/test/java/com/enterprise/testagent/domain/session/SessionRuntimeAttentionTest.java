package com.enterprise.testagent.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 验证会话运行态能区分问题与权限两种待关注状态。
 */
class SessionRuntimeAttentionTest {

    @Test
    void supportsPermissionAttention() {
        assertThat(Arrays.stream(SessionRuntimeAttention.values()).map(Enum::name))
                .contains("PERMISSION");
    }

    @Test
    void runtimeStateSummaryExposesPermissionSessionCount() {
        assertThat(Arrays.stream(SessionRuntimeStateSummary.class.getRecordComponents())
                .map(RecordComponent::getName))
                .contains("permissionCount");
    }
}
