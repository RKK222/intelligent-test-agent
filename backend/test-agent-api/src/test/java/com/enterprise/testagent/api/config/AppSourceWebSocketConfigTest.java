package com.enterprise.testagent.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.enterprise.testagent.api.web.platform.AppSourceOperationWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

class AppSourceWebSocketConfigTest {

    @Test
    void mapsOnlyTheTicketProtectedAppSourceOperationPath() {
        AppSourceOperationWebSocketHandler handler = mock(AppSourceOperationWebSocketHandler.class);

        SimpleUrlHandlerMapping mapping = (SimpleUrlHandlerMapping) new AppSourceWebSocketConfig()
                .appSourceWebSocketHandlerMapping(handler);

        assertThat(mapping.getUrlMap())
                .containsOnlyKeys(
                        "/api/internal/platform/workspace-management/app-source-operations/*/ws");
        assertThat(mapping.getUrlMap().get(
                "/api/internal/platform/workspace-management/app-source-operations/*/ws"))
                .isSameAs(handler);
        assertThat(mapping.getOrder()).isEqualTo(-1);
    }
}
