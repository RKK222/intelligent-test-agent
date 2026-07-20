package com.enterprise.testagent.xxljob;

import com.enterprise.testagent.common.error.PlatformException;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 所有 SQL 注册的平台周期任务共用的 XXL 入口。 */
@Component
public class TestAgentScheduledTaskXxlHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAgentScheduledTaskXxlHandler.class);

    private final XxlJobScheduledTaskAdapter adapter;

    public TestAgentScheduledTaskXxlHandler(XxlJobScheduledTaskAdapter adapter) {
        this.adapter = Objects.requireNonNull(adapter, "adapter must not be null");
    }

    @XxlJob("testAgentScheduledTaskHandler")
    public void execute() {
        try {
            XxlJobTaskExecutionOutcome outcome = adapter.execute(XxlJobHelper.getJobParam());
            XxlJobHelper.handleSuccess(outcome.status().name());
        } catch (PlatformException exception) {
            // XXL 日志只写稳定错误码和安全消息，不写任务参数、凭据或第三方异常栈。
            XxlJobHelper.handleFail(exception.errorCode().name() + ": " + exception.getMessage());
            LOGGER.warn("XXL-JOB 平台任务执行失败，errorCode={}", exception.errorCode());
        } catch (RuntimeException exception) {
            XxlJobHelper.handleFail("INTERNAL_ERROR: 定时任务执行失败");
            LOGGER.warn("XXL-JOB 平台任务执行失败，errorType={}", exception.getClass().getSimpleName());
        }
    }
}
