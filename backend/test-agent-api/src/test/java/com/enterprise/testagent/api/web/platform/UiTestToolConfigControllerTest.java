package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.configuration.management.UiTestPlatformConfigurationService;
import com.enterprise.testagent.configuration.management.UiTestPlatformConfigurationService.UiTestPlatformConfiguration;
import com.enterprise.testagent.observability.TraceConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class UiTestToolConfigControllerTest {

    @Test
    void returnsOnlyCurrentReadOnlyConfigurationWithoutCredential() {
        UiTestPlatformConfigurationService configurationService = mock(UiTestPlatformConfigurationService.class);
        when(configurationService.current())
                .thenReturn(new UiTestPlatformConfiguration(true, "http://ui.example.test:7788"));
        UiTestToolConfigController controller = new UiTestToolConfigController(configurationService);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(UiTestToolConfigController.ENDPOINT_PATH));
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, "trace_1234567890abcdef");

        var response = controller.current(exchange).block();

        assertThat(response).isNotNull();
        assertThat(response.traceId()).isEqualTo("trace_1234567890abcdef");
        assertThat(response.data().configured()).isTrue();
        assertThat(response.data().baseUrl()).isEqualTo("http://ui.example.test:7788");
        verify(configurationService).current();
    }
}
