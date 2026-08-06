package com.enterprise.testagent.api.web.aop;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;

/**
 * 平台异常日志分级策略，只识别不会影响服务健康的精确用户前置条件。
 */
final class PlatformErrorLogPolicy {

    private static final String PROCESS_INITIALIZATION_REQUIRED = "请先初始化 TestAgent 进程";

    private PlatformErrorLogPolicy() {
    }

    /**
     * 未初始化用户进程是可预期的页面状态；同错误码下的其它不可用故障仍必须保留 ERROR 堆栈。
     */
    static boolean isExpectedProcessInitializationPrecondition(Throwable error) {
        return error instanceof PlatformException platformException
                && platformException.errorCode() == ErrorCode.OPENCODE_UNAVAILABLE
                && PROCESS_INITIALIZATION_REQUIRED.equals(platformException.getMessage());
    }
}
