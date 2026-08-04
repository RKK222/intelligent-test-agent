package com.enterprise.testagent.api.config;

import com.enterprise.testagent.api.web.platform.PersonalWorkspaceRelocationTransferController;
import com.enterprise.testagent.api.web.platform.PersonalWorkspaceRelocationTransferWebSocketHandler;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

/** 注册个人工作区搬迁专用的一次性内部文件 WebSocket。 */
@Configuration
public class PersonalWorkspaceRelocationWebSocketConfig {

    @Bean
    HandlerMapping personalWorkspaceRelocationWebSocketHandlerMapping(
            PersonalWorkspaceRelocationTransferWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of(PersonalWorkspaceRelocationTransferController.WEB_SOCKET_PATH, handler));
        mapping.setOrder(-1);
        return mapping;
    }
}
