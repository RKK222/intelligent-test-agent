package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HttpXxlJobAdminReadinessProbeTest {

    @Test
    void reportsReadyWhenAnyConfiguredAdminReturnsHttp200() throws Exception {
        AtomicInteger unavailableRequests = new AtomicInteger();
        AtomicInteger readyRequests = new AtomicInteger();
        HttpServer unavailable = server(503, unavailableRequests);
        HttpServer ready = server(200, readyRequests);
        try {
            HttpXxlJobAdminReadinessProbe probe =
                    new HttpXxlJobAdminReadinessProbe(Duration.ofMillis(500));
            String addresses = adminAddress(unavailable) + ", " + adminAddress(ready) + "/";

            assertThat(probe.isAnyReady(addresses)).isTrue();
            assertThat(unavailableRequests).hasValue(1);
            assertThat(readyRequests).hasValue(1);
        } finally {
            unavailable.stop(0);
            ready.stop(0);
        }
    }

    @Test
    void rejectsInvalidAddressesAndNon200Responses() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer unavailable = server(302, requests);
        try {
            HttpXxlJobAdminReadinessProbe probe =
                    new HttpXxlJobAdminReadinessProbe(Duration.ofMillis(500));

            assertThat(probe.isAnyReady("not-a-http-address," + adminAddress(unavailable))).isFalse();
            assertThat(requests).hasValue(1);
        } finally {
            unavailable.stop(0);
        }
    }

    private static HttpServer server(int status, AtomicInteger requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/xxl-job-admin/actuator/health/readiness", exchange -> {
            requests.incrementAndGet();
            respond(exchange, status);
        });
        server.start();
        return server;
    }

    private static void respond(HttpExchange exchange, int status) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(status, -1);
        }
    }

    private static String adminAddress(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/xxl-job-admin";
    }
}
