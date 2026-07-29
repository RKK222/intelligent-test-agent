package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.api.config.AppSourceWebSocketConfig;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.workspace.AppSourceApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.HandlerMapping;

class AppSourceApiContextTest {

    @Test
    void appSourceHttpTicketAndWebSocketBeansFormOneRunnableGraph() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfiguration.class, AppSourceWebSocketConfig.class)
                .withPropertyValues("test-agent.security.cors-allowed-origins=*")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AppSourceController.class);
                    assertThat(context).hasSingleBean(AppSourceOperationController.class);
                    assertThat(context).hasSingleBean(AppSourceOperationTicketService.class);
                    assertThat(context).hasSingleBean(AppSourceOperationWebSocketHandler.class);
                    assertThat(context.getBeansOfType(HandlerMapping.class))
                            .containsKey("appSourceWebSocketHandlerMapping");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(AppSourceOperationWebSocketHandler.class)
    static class TestConfiguration {

        @Bean
        AppSourceApplicationService appSourceApplicationService() {
            return mock(AppSourceApplicationService.class);
        }

        @Bean
        UserOpencodeProcessAssignmentService userOpencodeProcessAssignmentService() {
            return mock(UserOpencodeProcessAssignmentService.class);
        }

        @Bean
        BackendInstanceIdentity backendInstanceIdentity() {
            return new BackendInstanceIdentity() {
                @Override
                public String instanceId() {
                    return "instance-a";
                }

                @Override
                public String linuxServerId() {
                    return "server-a";
                }

                @Override
                public String backendProcessId() {
                    return "bjp_server_a";
                }

                @Override
                public String listenUrl() {
                    return "http://server-a:8080";
                }
            };
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        CurrentBackendWebSocketUrlFactory currentBackendWebSocketUrlFactory(
                BackendInstanceIdentity identity) {
            return new CurrentBackendWebSocketUrlFactory(identity);
        }

        @Bean
        AppSourceOperationTicketStore appSourceOperationTicketStore() {
            return new AppSourceOperationTicketStore();
        }

        @Bean
        AppSourceOperationTicketService appSourceOperationTicketService(
                AppSourceOperationTicketStore store,
                AppSourceApplicationService appSources,
                CurrentBackendWebSocketUrlFactory urls,
                BackendInstanceIdentity identity) {
            return new AppSourceOperationTicketService(store, appSources, urls, identity);
        }

        @Bean
        AppSourceController appSourceController(
                AppSourceApplicationService appSources,
                UserOpencodeProcessAssignmentService assignments) {
            return new AppSourceController(appSources, assignments);
        }

        @Bean
        AppSourceOperationController appSourceOperationController(
                AppSourceApplicationService appSources,
                AppSourceOperationTicketService tickets) {
            return new AppSourceOperationController(appSources, tickets);
        }
    }
}
