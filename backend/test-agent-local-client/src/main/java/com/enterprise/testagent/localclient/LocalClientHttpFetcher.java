package com.enterprise.testagent.localclient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Locale;

/** 使用无重定向、有限读取的 HTTP 客户端下载已签名发布内容。 */
final class LocalClientHttpFetcher implements LocalClientReleaseDownloader.Fetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration METADATA_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration ARTIFACT_TIMEOUT = Duration.ofMinutes(30);
    private static final int BUFFER_BYTES = 64 * 1024;

    private final HttpClient httpClient;

    LocalClientHttpFetcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    LocalClientHttpFetcher(HttpClient httpClient) {
        this.httpClient = java.util.Objects.requireNonNull(httpClient);
    }

    @Override
    public byte[] fetchBytes(URI uri, int maxBytes) throws Exception {
        validateUri(uri);
        if (maxBytes < 1) {
            throw new IllegalArgumentException("download byte limit must be positive");
        }
        HttpResponse<InputStream> response = send(uri, METADATA_TIMEOUT);
        try (InputStream input = response.body()) {
            validateResponse(uri, response, maxBytes, false);
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, BUFFER_BYTES));
            byte[] buffer = new byte[BUFFER_BYTES];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                if (read > maxBytes - total) {
                    throw new SecurityException("download exceeds signed metadata size limit");
                }
                output.write(buffer, 0, read);
                total += read;
            }
            return output.toByteArray();
        }
    }

    @Override
    public void fetchFile(URI uri, Path target, long expectedSize) throws Exception {
        validateUri(uri);
        if (expectedSize < 1) {
            throw new IllegalArgumentException("signed artifact size must be positive");
        }
        Path targetPath = target.toAbsolutePath().normalize();
        Path parent = targetPath.getParent();
        if (parent == null
                || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(parent)
                || Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new SecurityException("download target is not a new regular file");
        }

        boolean complete = false;
        try {
            HttpResponse<InputStream> response = send(uri, ARTIFACT_TIMEOUT);
            try (InputStream input = response.body()) {
                validateResponse(uri, response, expectedSize, true);
                try (OutputStream output = Files.newOutputStream(
                        targetPath,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS)) {
                    setPrivateFilePermissions(targetPath);
                    copyExact(input, output, expectedSize);
                }
            }
            complete = true;
        } finally {
            if (!complete) {
                Files.deleteIfExists(targetPath);
            }
        }
    }

    private HttpResponse<InputStream> send(URI uri, Duration timeout) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "test-agent-local-client-updater/1")
                .GET()
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("release download was interrupted", exception);
        }
    }

    private static void validateResponse(
            URI requestedUri,
            HttpResponse<?> response,
            long sizeLimit,
            boolean requireExactLength) {
        if (!requestedUri.equals(response.uri())) {
            throw new SecurityException("release download redirect is not allowed");
        }
        int status = response.statusCode();
        if (status >= 300 && status < 400) {
            throw new SecurityException("release download redirect is not allowed");
        }
        if (status != 200) {
            throw new IllegalStateException("release download returned HTTP " + status);
        }
        response.headers().firstValue("Content-Encoding").ifPresent(encoding -> {
            if (!"identity".equals(encoding.trim().toLowerCase(Locale.ROOT))) {
                throw new SecurityException("encoded release responses are not allowed");
            }
        });
        response.headers().firstValueAsLong("Content-Length").ifPresent(length -> {
            if (length < 0 || length > sizeLimit || (requireExactLength && length != sizeLimit)) {
                throw new SecurityException("release response length does not match signed metadata");
            }
        });
    }

    private static void copyExact(InputStream input, OutputStream output, long expectedSize) throws IOException {
        byte[] buffer = new byte[BUFFER_BYTES];
        long remaining = expectedSize;
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new SecurityException("release response is shorter than signed metadata");
            }
            if (read == 0) {
                continue;
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
        if (input.read() >= 0) {
            throw new SecurityException("release response is longer than signed metadata");
        }
    }

    private static void validateUri(URI uri) {
        if (uri == null
                || uri.getHost() == null
                || uri.getRawAuthority() == null
                || uri.getUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getRawPath() == null
                || uri.getRawPath().isBlank()
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("release download URI is invalid");
        }
    }

    private static void setPrivateFilePermissions(Path target) throws IOException {
        try {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // 麒麟使用 POSIX；其它开发平台依赖用户私有安装目录 ACL。
        }
    }
}
