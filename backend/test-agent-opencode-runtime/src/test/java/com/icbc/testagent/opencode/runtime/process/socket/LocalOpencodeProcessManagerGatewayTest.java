package com.icbc.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessHealthCommand;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessStartCommand;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalOpencodeProcessManagerGatewayTest {

    private HttpServer server;
    private LocalOpencodeProcessManagerGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        gateway = new LocalOpencodeProcessManagerGateway(Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void checkHealthReturnsHealthyWhenLocalServerReturns2xx() {
        server.createContext("/", new FixedStatusHandler(200));
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";

        var result = gateway.checkHealth(new OpencodeProcessHealthCommand(
                new OpencodeProcessId("ocp_local_user_dev"),
                baseUrl,
                "trace_1234567890abcdef"));

        assertThat(result.healthy()).isTrue();
        assertThat(result.message()).contains("local-direct-http:200");
    }

    @Test
    void checkHealthReturnsHealthyWhenLocalServerReturns3xx() {
        server.createContext("/", new FixedStatusHandler(302));
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";

        var result = gateway.checkHealth(new OpencodeProcessHealthCommand(
                new OpencodeProcessId("ocp_local_user_dev"),
                baseUrl,
                "trace_1234567890abcdef"));

        assertThat(result.healthy()).isTrue();
        assertThat(result.message()).contains("local-direct-http:302");
    }

    @Test
    void checkHealthReturnsUnhealthyWhenLocalServerReturns5xx() {
        server.createContext("/", new FixedStatusHandler(500));
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";

        var result = gateway.checkHealth(new OpencodeProcessHealthCommand(
                new OpencodeProcessId("ocp_local_user_dev"),
                baseUrl,
                "trace_1234567890abcdef"));

        assertThat(result.healthy()).isFalse();
        assertThat(result.message()).contains("500");
    }

    @Test
    void checkHealthReturnsUnhealthyWhenConnectionFails() {
        var result = gateway.checkHealth(new OpencodeProcessHealthCommand(
                new OpencodeProcessId("ocp_local_user_dev"),
                "http://127.0.0.1:1/should-not-listen",
                "trace_1234567890abcdef"));

        assertThat(result.healthy()).isFalse();
        assertThat(result.message()).contains("127.0.0.1:1");
    }

    @Test
    void startProcessReturnsPlaceholderSuccessWithoutTouchingNetwork() {
        var result = gateway.startProcess(new OpencodeProcessStartCommand(
                new UserId("usr_test_dev"),
                new LinuxServerId("127.0.0.1"),
                new OpencodeContainerId("ctr_local_4096"),
                4096,
                "http://127.0.0.1:4096",
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                "trace_1234567890abcdef"));

        assertThat(result.pid()).isZero();
        assertThat(result.message()).isEqualTo("local-skip");
    }

    private static final class FixedStatusHandler implements HttpHandler {
        private final int status;

        private FixedStatusHandler(int status) {
            this.status = status;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        }
    }
}
