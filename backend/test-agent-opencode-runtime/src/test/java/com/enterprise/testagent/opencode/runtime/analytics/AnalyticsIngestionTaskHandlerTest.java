package com.enterprise.testagent.opencode.runtime.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AnalyticsIngestionTaskHandlerTest {

    @Test
    void exposesOneMinuteGlobalTaskMetadata() {
        AnalyticsIngestionTaskHandler handler = new AnalyticsIngestionTaskHandler(mock(AnalyticsIngestionService.class));

        assertThat(handler.taskKey().value()).isEqualTo("opencode-runtime.analytics-ingestion");
        assertThat(handler.cronExpression()).isEqualTo("0 * * * * *");
        assertThat(handler.lockTtl()).isEqualTo(Duration.ofMinutes(2));
    }
}
