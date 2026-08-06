package com.enterprise.testagent.api.web.aop;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import org.junit.jupiter.api.Test;

/**
 * 固化预期前置条件的精确分类，防止把真实 OpenCode 故障一起降级。
 */
class PlatformErrorLogPolicyTest {

    @Test
    void recognizesOnlyTheExactProcessInitializationPrecondition() {
        assertThat(PlatformErrorLogPolicy.isExpectedProcessInitializationPrecondition(
                new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "请先初始化 TestAgent 进程")))
                .isTrue();

        assertThat(PlatformErrorLogPolicy.isExpectedProcessInitializationPrecondition(
                new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "TestAgent 进程健康检查失败")))
                .isFalse();
        assertThat(PlatformErrorLogPolicy.isExpectedProcessInitializationPrecondition(
                new PlatformException(ErrorCode.INTERNAL_ERROR, "请先初始化 TestAgent 进程")))
                .isFalse();
        assertThat(PlatformErrorLogPolicy.isExpectedProcessInitializationPrecondition(
                new IllegalStateException("请先初始化 TestAgent 进程")))
                .isFalse();
    }
}
