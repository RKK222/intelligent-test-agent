package com.enterprise.testagent.api.config;

import com.enterprise.testagent.api.web.platform.LocalClientConnectionWebSocketHandler;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

/** 注册本地 OpenCode 客户端主动连接的反向 WebSocket URL。 */
@Configuration
public class LocalClientWebSocketConfig {

    @Bean
    HandlerMapping localClientWebSocketHandlerMapping(LocalClientConnectionWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of(LocalClientConnectionWebSocketHandler.PATH, handler));
        mapping.setOrder(-3);
        return mapping;
    }
}
