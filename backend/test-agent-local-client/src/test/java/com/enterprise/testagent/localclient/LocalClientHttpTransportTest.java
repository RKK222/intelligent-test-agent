package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

/** 本地 OpenCode 环回传输必须禁用 h2c upgrade，避免 POST 响应永久等待。 */
class LocalClientHttpTransportTest {

    @Test
    void loopbackTransportUsesHttp11() {
        HttpClient client = LocalClientConnection.loopbackHttpClient();
        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }
}
