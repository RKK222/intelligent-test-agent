package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientHttpFetcherTest {

    @TempDir
    Path temporaryDirectory;

    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/artifact", exchange -> {
            byte[] body = "signed-artifact".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/artifact");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/oversized", exchange -> {
            byte[] body = "too-large".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void shouldDownloadBoundedBytesAndExactSizedFileWithoutRedirects() throws Exception {
        LocalClientHttpFetcher fetcher = new LocalClientHttpFetcher();
        byte[] expected = "signed-artifact".getBytes(StandardCharsets.UTF_8);

        assertThat(fetcher.fetchBytes(baseUri.resolve("artifact"), expected.length))
                .isEqualTo(expected);
        Path target = temporaryDirectory.resolve("artifact.bin");
        fetcher.fetchFile(baseUri.resolve("artifact"), target, expected.length);
        assertThat(target).hasBinaryContent(expected);

        assertThatThrownBy(() -> fetcher.fetchBytes(baseUri.resolve("redirect"), 1024))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldDeletePartialFileWhenResponseExceedsSignedSize() {
        Path target = temporaryDirectory.resolve("partial.bin");

        assertThatThrownBy(() -> new LocalClientHttpFetcher()
                .fetchFile(baseUri.resolve("oversized"), target, 3))
                .isInstanceOf(SecurityException.class);
        assertThat(target).doesNotExist();
    }
}
