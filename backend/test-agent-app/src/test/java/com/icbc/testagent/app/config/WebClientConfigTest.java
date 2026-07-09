package com.icbc.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class WebClientConfigTest {

    @Test
    void providesWebClientBuilder() {
        WebClient.Builder builder = new WebClientConfig().webClientBuilder();

        assertThat(builder).isNotNull();
        assertThat(builder.build()).isNotNull();
    }
}
