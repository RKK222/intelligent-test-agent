package com.enterprise.testagent.xxljob;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.observability.TraceIdSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final int MAX_SUMMARY_LENGTH = 12_000;

    private final XxlJobScheduledTaskAdapter adapter;
    private final ObjectMapper objectMapper;

    public TestAgentScheduledTaskXxlHandler(XxlJobScheduledTaskAdapter adapter, ObjectMapper objectMapper) {
        this.adapter = Objects.requireNonNull(adapter, "adapter must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @XxlJob("testAgentScheduledTaskHandler")
    public void execute() {
        String traceId = TraceIdSupport.generate();
        long startedNanos = System.nanoTime();
        XxlJobHelper.log(
                "【任务开始】jobId={}，logId={}，分片={}/{}，traceId={}",
                XxlJobHelper.getJobId(),
                XxlJobHelper.getLogId(),
                XxlJobHelper.getShardIndex() + 1,
                XxlJobHelper.getShardTotal(),
                traceId);
        try {
            XxlJobTaskExecutionOutcome outcome =
                    adapter.execute(XxlJobHelper.getJobParam(), traceId, XxlJobHelper::log);
            String summary = serialize(outcome);
            XxlJobHelper.log("【处理结果】{}", summary);
            XxlJobHelper.handleSuccess(successHandleMessage(outcome, summary));
            LOGGER.info(
                    "XXL-JOB 平台任务执行结束，traceId={} taskKey={} taskRunId={} status={} processed={} durationMillis={}",
                    outcome.traceId(),
                    outcome.taskKey(),
                    outcome.taskRunId(),
                    outcome.status(),
                    outcome.processed(),
                    outcome.durationMillis());
        } catch (PlatformException exception) {
            // XXL 日志只写稳定错误码和安全消息，不写任务参数、凭据或第三方异常栈。
            long durationMillis = elapsedMillis(startedNanos);
            String failure = exception.errorCode().name() + ": " + exception.getMessage();
            XxlJobHelper.log(
                    "【执行结束】status=FAILED，traceId={}，errorCode={}，message={}，durationMillis={}",
                    traceId,
                    exception.errorCode().name(),
                    exception.getMessage(),
                    durationMillis);
            XxlJobHelper.handleFail(htmlEscape(abbreviate(failure, MAX_SUMMARY_LENGTH)));
            LOGGER.warn(
                    "XXL-JOB 平台任务执行失败，traceId={} errorCode={} durationMillis={}",
                    traceId,
                    exception.errorCode(),
                    durationMillis);
        } catch (RuntimeException exception) {
            long durationMillis = elapsedMillis(startedNanos);
            XxlJobHelper.log(
                    "【执行结束】status=FAILED，traceId={}，errorCode=INTERNAL_ERROR，message=定时任务执行失败，durationMillis={}",
                    traceId,
                    durationMillis);
            XxlJobHelper.handleFail("INTERNAL_ERROR: 定时任务执行失败");
            LOGGER.warn(
                    "XXL-JOB 平台任务执行失败，traceId={} errorType={} durationMillis={}",
                    traceId,
                    exception.getClass().getSimpleName(),
                    durationMillis);
        }
    }

    /** 将低敏结果压缩成单行 JSON，避免 XXL 完成备注超过上游 15,000 字符限制。 */
    private String serialize(XxlJobTaskExecutionOutcome outcome) {
        try {
            return abbreviate(objectMapper.writeValueAsString(outcome.toLogFields()), MAX_SUMMARY_LENGTH);
        } catch (JsonProcessingException exception) {
            return "{\"status\":\"" + outcome.status().name()
                    + "\",\"processed\":" + outcome.processed()
                    + ",\"taskKey\":\"" + outcome.taskKey()
                    + "\",\"traceId\":\"" + outcome.traceId()
                    + "\",\"result\":\"SERIALIZATION_FAILED\"}";
        }
    }

    /** 完成备注同时写明是否真正处理；互斥跳过不能伪装成业务成功。 */
    private String successHandleMessage(XxlJobTaskExecutionOutcome outcome, String summary) {
        String conclusion = outcome.processed() ? "执行成功" : "未执行（全局锁被其他节点持有）";
        return abbreviate(htmlEscape(conclusion + "：" + summary), MAX_SUMMARY_LENGTH);
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 15) + "...(已截断)";
    }

    /** XXL 列表页把完成备注拼入 HTML，需在写入前转义所有可变字段。 */
    private static String htmlEscape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
