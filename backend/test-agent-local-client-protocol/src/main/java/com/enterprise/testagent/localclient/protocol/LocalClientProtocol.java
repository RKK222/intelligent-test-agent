package com.enterprise.testagent.localclient.protocol;

import java.time.Duration;

/** 本地 OpenCode 客户端 v1 协议的稳定常量。 */
public final class LocalClientProtocol {

    public static final String VERSION = "local-opencode-client.v1";
    public static final int BINARY_CHUNK_BYTES = 256 * 1024;
    public static final int MAX_FRAME_CHARS = 2 * 1024 * 1024;
    public static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(5);
    public static final Duration CONNECTION_TTL = Duration.ofSeconds(15);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration LONG_REQUEST_TIMEOUT = Duration.ofHours(24);

    private LocalClientProtocol() {
    }
}
