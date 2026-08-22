package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.trace.TraceCatalogRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientConnectionRegistry;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientRegistrationService;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientTunnelGateway;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientUpdateCoordinator;
import com.enterprise.testagent.opencode.runtime.localclient.LocalWorkspaceApplicationService;
import com.enterprise.testagent.opencode.runtime.observability.OpencodeObservabilityModels;
import com.enterprise.testagent.opencode.runtime.observability.TraceArchiveService;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartupService;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.enterprise.testagent.system.management.localclient.LocalClientCredentialApplicationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

/** 验证低优先级 Trace 帧只有在服务器原子归档完成后才得到当前连接 ACK。 */
class LocalClientConnectionWebSocketHandlerObservabilityTest {

    private static final UserId USER_ID = new UserId("usr_local_observability");
    private static final LocalClientInstanceId INSTANCE_ID = new LocalClientInstanceId("lci_local_observability");
    private static final String TRACE_ID = "trc_0123456789abcdef0123456789abcdef";
    private static final String BATCH_ID = "obs_0123456789abcdef0123456789abcdef";
    private static final String RUNTIME_GENERATION = "runtime-generation-1";
    private static final long CONNECTION_GENERATION = 7L;
    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();

    @Test
    void acknowledgesOnlyAfterArchiveAndBindsAckToCurrentConnection() throws Exception {
        TraceArchiveService archiveService = mock(TraceArchiveService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TraceCatalogRepository catalogRepository = mock(TraceCatalogRepository.class);
        User user = mock(User.class);
        when(user.canLogin()).thenReturn(true);
        when(user.userId()).thenReturn(USER_ID);
        when(userRepository.findByUserId(USER_ID)).thenReturn(Optional.of(user));
        when(catalogRepository.find(TRACE_ID)).thenReturn(Optional.empty());

        byte[] body = ("{\"traceId\":\"" + TRACE_ID + "\",\"globalSequence\":1}\n")
                .getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(body);
        CountDownLatch archiveEntered = new CountDownLatch(1);
        CountDownLatch allowArchive = new CountDownLatch(1);
        Instant archivedAt = Instant.parse("2026-08-22T08:00:00Z");
        doAnswer(invocation -> {
                    archiveEntered.countDown();
                    assertThat(allowArchive.await(3, TimeUnit.SECONDS)).isTrue();
                    return new OpencodeObservabilityModels.TraceAck(
                            TRACE_ID, 1, 1, sha256, body.length, 1, false, "ARCHIVED", archivedAt);
                })
                .when(archiveService).ingestLocalChunk(
                        any(), any(), any(), anyLong(), anyBoolean(), anyString(), anyLong(), anyLong(),
                        anyString(), any(byte[].class));

        LocalClientConnectionWebSocketHandler handler = handler(
                archiveService, userRepository, catalogRepository);
        LocalClientConnectionWebSocketHandler.ConnectionState state = state(true);
        Sinks.Many<LocalClientFrame> outbound = Sinks.many().unicast().onBackpressureBuffer();
        List<LocalClientFrame> responses = new CopyOnWriteArrayList<>();
        outbound.asFlux().take(2).subscribe(responses::add);

        StepVerifier.create(handler.handleAuthenticated(
                        frame(LocalClientFrameType.OBSERVABILITY_BATCH, declaration(body, sha256)),
                        outbound,
                        Sinks.one(),
                        state))
                .verifyComplete();

        StepVerifier.create(handler.handleAuthenticated(
                        frame(LocalClientFrameType.TRACE_CHUNK_UPLOAD, upload(body, sha256, CONNECTION_GENERATION)),
                        outbound,
                        Sinks.one(),
                        state))
                .then(() -> {
                    try {
                        assertThat(archiveEntered.await(2, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                        throw new AssertionError(exception);
                    }
                    assertThat(responses).isEmpty();
                    allowArchive.countDown();
                })
                .verifyComplete();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).type()).isEqualTo(LocalClientFrameType.TRACE_CHUNK_ACK);
        LocalClientPayloads.TraceChunkAck ack = codec.payload(
                responses.get(0), LocalClientPayloads.TraceChunkAck.class);
        assertThat(ack.batchId()).isEqualTo(BATCH_ID);
        assertThat(ack.connectionGeneration()).isEqualTo(CONNECTION_GENERATION);
        assertThat(ack.sha256()).isEqualTo(sha256);
        assertThat(responses.get(1).type()).isEqualTo(LocalClientFrameType.TRACE_UPLOAD_WATERMARK);
    }

    @Test
    void rejectsOldConnectionGenerationBeforeTouchingArchive() {
        TraceArchiveService archiveService = mock(TraceArchiveService.class);
        LocalClientConnectionWebSocketHandler handler = handler(
                archiveService, mock(UserRepository.class), mock(TraceCatalogRepository.class));
        byte[] body = "{}\n".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(body);

        LocalClientPayloads.ObservabilityBatch stale = new LocalClientPayloads.ObservabilityBatch(
                BATCH_ID, INSTANCE_ID.value(), CONNECTION_GENERATION - 1, TRACE_ID,
                RUNTIME_GENERATION, "LOCAL_CLIENT", Instant.parse("2026-08-22T00:00:00Z"),
                1, 1, sha256, body.length, 0, true, Instant.parse("2026-08-22T08:00:00Z"));
        assertThatThrownBy(() -> handler.handleAuthenticated(
                frame(LocalClientFrameType.OBSERVABILITY_BATCH, stale),
                Sinks.many().unicast().onBackpressureBuffer(),
                Sinks.one(),
                state(true)).block())
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("连接坐标");
        verifyNoInteractions(archiveService);
    }

    private LocalClientConnectionWebSocketHandler handler(
            TraceArchiveService archiveService,
            UserRepository userRepository,
            TraceCatalogRepository catalogRepository) {
        BackendJavaRouteResolver routeResolver = mock(BackendJavaRouteResolver.class);
        LocalClientConnectionWebSocketHandler handler = new LocalClientConnectionWebSocketHandler(
                mock(LocalClientCredentialApplicationService.class),
                mock(LocalClientRegistrationService.class),
                mock(LocalClientConnectionRegistry.class),
                mock(LocalClientConnectionSupersessionService.class),
                mock(LocalClientTunnelGateway.class),
                routeResolver,
                mock(OpencodeProcessStartupService.class),
                mock(LocalClientUpdateCoordinator.class),
                mock(LocalWorkspaceApplicationService.class),
                mock(LocalClientControlSecuritySettings.class),
                new LocalClientAuthenticationRateLimiter(1, Duration.ofMinutes(1)));
        handler.setObservabilityServices(
                archiveService,
                userRepository,
                catalogRepository,
                mock(BackendHttpForwarder.class),
                new ManagerControlSettings(
                        "manager-secret", "http://127.0.0.1:8080", new LinuxServerId("linux-1"),
                        Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10), 100));
        return handler;
    }

    private LocalClientConnectionWebSocketHandler.ConnectionState state(boolean supported) {
        return new LocalClientConnectionWebSocketHandler.ConnectionState(
                USER_ID,
                INSTANCE_ID,
                CONNECTION_GENERATION,
                "grant-fingerprint",
                "trace-register",
                true,
                supported,
                ConcurrentHashMap.newKeySet(),
                new ConcurrentHashMap<>());
    }

    private LocalClientPayloads.ObservabilityBatch declaration(byte[] body, String sha256) {
        return new LocalClientPayloads.ObservabilityBatch(
                BATCH_ID, INSTANCE_ID.value(), CONNECTION_GENERATION, TRACE_ID,
                RUNTIME_GENERATION, "LOCAL_CLIENT", Instant.parse("2026-08-22T00:00:00Z"),
                1, 1, sha256, body.length, 0, true, Instant.parse("2026-08-22T08:00:00Z"));
    }

    private LocalClientPayloads.TraceChunkUpload upload(byte[] body, String sha256, long generation) {
        return new LocalClientPayloads.TraceChunkUpload(
                BATCH_ID, INSTANCE_ID.value(), generation, TRACE_ID, 1, 1, sha256,
                Base64.getEncoder().encodeToString(body));
    }

    private LocalClientFrame frame(LocalClientFrameType type, Object payload) {
        return new LocalClientFrame(
                LocalClientProtocol.VERSION,
                type,
                "request-observability",
                TRACE_ID,
                CONNECTION_GENERATION,
                codec.payload(payload));
    }

    private String sha256(byte[] body) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
