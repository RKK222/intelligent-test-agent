package com.example.testagent.opencode.runtime.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class TerminalTicketRateLimiterSpringTest {

    @Test
    void springContextCreatesRateLimiterWithConfiguredConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
            context.register(TerminalTicketRateLimiter.class);
            context.refresh();

            assertThat(context.getBean(TerminalTicketRateLimiter.class)).isNotNull();
        }
    }
}
