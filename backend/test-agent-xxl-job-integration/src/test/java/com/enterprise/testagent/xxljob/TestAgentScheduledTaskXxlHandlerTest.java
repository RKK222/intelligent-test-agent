package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.context.XxlJobContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestAgentScheduledTaskXxlHandlerTest {

    @TempDir
    private Path temporaryDirectory;

    @AfterEach
    void clearXxlContext() {
        XxlJobContext.setXxlJobContext(null);
    }

    @Test
    void writesTaskIdentityBusinessResultAndSuccessConclusionToXxlLog() throws Exception {
        XxlJobScheduledTaskAdapter adapter = org.mockito.Mockito.mock(XxlJobScheduledTaskAdapter.class);
        Instant startedAt = Instant.parse("2026-08-27T01:00:00Z");
        XxlJobTaskExecutionOutcome outcome = new XxlJobTaskExecutionOutcome(
                XxlJobTaskExecutionStatus.SUCCEEDED,
                "opencode-runtime.analytics-rollup",
                "TestAgent 运营分析汇总",
                "str_test_run",
                "trace_xxl_success",
                XxlJobConcurrencyPolicy.GLOBAL_MUTEX,
                startedAt,
                startedAt.plusMillis(321),
                Map.of("updated", 3, "note", "<script>不可执行</script>"));
        when(adapter.execute(
                        eq("safe-parameter"),
                        anyString(),
                        org.mockito.ArgumentMatchers.any(XxlJobScheduledTaskAdapter.ExecutionLog.class)))
                .thenReturn(outcome);
        Path logFile = installXxlContext("safe-parameter");

        new TestAgentScheduledTaskXxlHandler(
                        adapter,
                        new ObjectMapper().findAndRegisterModules())
                .execute();

        String logContent = Files.readString(logFile);
        assertThat(logContent)
                .contains("【任务开始】")
                .contains("【处理结果】")
                .contains("\"taskKey\":\"opencode-runtime.analytics-rollup\"")
                .contains("\"processed\":true")
                .contains("\"updated\":3")
                .contains("\"durationMillis\":321");
        XxlJobContext context = XxlJobContext.getXxlJobContext();
        assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
        assertThat(context.getHandleMsg())
                .startsWith("执行成功：")
                .contains("opencode-runtime.analytics-rollup")
                .contains("&lt;script&gt;不可执行&lt;/script&gt;")
                .doesNotContain("<script>");
        verify(adapter).execute(
                eq("safe-parameter"),
                anyString(),
                org.mockito.ArgumentMatchers.any(XxlJobScheduledTaskAdapter.ExecutionLog.class));
    }

    @Test
    void marksLockContentionAsNotExecutedInsteadOfBusinessSuccess() throws Exception {
        XxlJobScheduledTaskAdapter adapter = org.mockito.Mockito.mock(XxlJobScheduledTaskAdapter.class);
        Instant now = Instant.parse("2026-08-27T01:00:00Z");
        XxlJobTaskExecutionOutcome outcome = XxlJobTaskExecutionOutcome.skippedLockHeld(
                "opencode-runtime.analytics-rollup",
                "TestAgent 运营分析汇总",
                "str_skipped_run",
                "trace_xxl_skipped",
                XxlJobConcurrencyPolicy.GLOBAL_MUTEX,
                now,
                now.plusMillis(8));
        when(adapter.execute(
                        eq("lock-parameter"),
                        anyString(),
                        org.mockito.ArgumentMatchers.any(XxlJobScheduledTaskAdapter.ExecutionLog.class)))
                .thenReturn(outcome);
        Path logFile = installXxlContext("lock-parameter");

        new TestAgentScheduledTaskXxlHandler(
                        adapter,
                        new ObjectMapper().findAndRegisterModules())
                .execute();

        assertThat(Files.readString(logFile))
                .contains("\"status\":\"SKIPPED_LOCK_HELD\"")
                .contains("\"processed\":false")
                .contains("GLOBAL_MUTEX_LOCK_HELD");
        assertThat(XxlJobContext.getXxlJobContext().getHandleMsg())
                .startsWith("未执行（全局锁被其他节点持有）：");
    }

    @Test
    void recordsSafeFailureWithoutWritingRawParameter() throws Exception {
        XxlJobScheduledTaskAdapter adapter = org.mockito.Mockito.mock(XxlJobScheduledTaskAdapter.class);
        when(adapter.execute(
                        eq("accessToken=do-not-log"),
                        anyString(),
                        org.mockito.ArgumentMatchers.any(XxlJobScheduledTaskAdapter.ExecutionLog.class)))
                .thenThrow(new PlatformException(ErrorCode.CONFLICT, "定时任务 Redis 锁续租失败"));
        Path logFile = installXxlContext("accessToken=do-not-log");

        new TestAgentScheduledTaskXxlHandler(
                        adapter,
                        new ObjectMapper().findAndRegisterModules())
                .execute();

        String logContent = Files.readString(logFile);
        assertThat(logContent)
                .contains("【执行结束】status=FAILED")
                .contains("errorCode=CONFLICT")
                .doesNotContain("accessToken")
                .doesNotContain("do-not-log");
        assertThat(XxlJobContext.getXxlJobContext().getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
        assertThat(XxlJobContext.getXxlJobContext().getHandleMsg())
                .isEqualTo("CONFLICT: 定时任务 Redis 锁续租失败");
    }

    /** 使用 XXL Core 的真实线程上下文和文件追加器验证管理页能够读取的日志内容。 */
    private Path installXxlContext(String jobParameter) {
        Path logFile = temporaryDirectory.resolve("xxl-job.log");
        XxlJobContext.setXxlJobContext(new XxlJobContext(
                101L,
                jobParameter,
                202L,
                System.currentTimeMillis(),
                logFile.toString(),
                0,
                1));
        return logFile;
    }
}
