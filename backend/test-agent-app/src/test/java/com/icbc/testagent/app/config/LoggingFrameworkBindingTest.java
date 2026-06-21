package com.icbc.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

class LoggingFrameworkBindingTest {

    @Test
    void slf4jUsesLog4j2Binding() {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();

        assertThat(loggerFactory.getClass().getName())
                .contains("org.apache.logging.slf4j");
    }
}
