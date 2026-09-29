package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.user.UserId;
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
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartupService;
import com.enterprise.testagent.system.management.localclient.LocalClientCredentialApplicationService;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

/** 验证 WSS 只接收客户端方向的更新帧，并交给版本协调器。 */
class LocalClientConnectionWebSocketHandlerUpdateTest {

    private final LocalClientFrameCodec codec = new LocalClientFrameCodec();
    private final LocalClientUpdateCoordinator updates = mock(LocalClientUpdateCoordinator.class);
    private final LocalClientTunnelGateway tunnel = mock(LocalClientTunnelGateway.class);
    private final LocalClientConnectionWebSocketHandler handler = new LocalClientConnectionWebSocketHandler(
            mock(LocalClientCredentialApplicationService.class),
            mock(LocalClientRegistrationService.class),
            mock(LocalClientConnectionRegistry.class),
            mock(LocalClientConnectionSupersessionService.class),
            tunnel,
            mock(BackendJavaRouteResolver.class),
            mock(OpencodeProcessStartupService.class),
            updates,
            mock(LocalWorkspaceApplicationService.class),
            mock(LocalClientControlSecuritySettings.class),
            new LocalClientAuthenticationRateLimiter(1, Duration.ofMinutes(1)));
    private final UserId userId = new UserId("usr_local_ws_update");
    private final LocalClientInstanceId instanceId = new LocalClientInstanceId("lci_local_ws_update");
    private final LocalClientConnectionWebSocketHandler.ConnectionState state =
            new LocalClientConnectionWebSocketHandler.ConnectionState(
                    userId, instanceId, 7, "grant-fingerprint", "trace-register", true,
                    ConcurrentHashMap.newKeySet());
    private final LocalClientConnectionWebSocketHandler.ConnectionState legacyState =
            new LocalClientConnectionWebSocketHandler.ConnectionState(
                    userId, instanceId, 7, "grant-fingerprint", "trace-register", false,
                    ConcurrentHashMap.newKeySet());

    @Test
    void versionCheckIsHandledByUpdateCoordinator() {
        LocalClientPayloads.VersionCheck payload = new LocalClientPayloads.VersionCheck(
                instanceId.value(),
                "20260820180000",
                "1",
                "2.0.18",
                List.of("SELF_UPDATE_V1"),
                Instant.parse("2026-08-20T10:00:00Z"));
        LocalClientFrame frame = frame(LocalClientFrameType.VERSION_CHECK, "req-version", payload);

        StepVerifier.create(handler.handleAuthenticated(
                        frame,
                        Sinks.many().unicast().onBackpressureBuffer(),
                        Sinks.one(),
                        state))
                .verifyComplete();

        verify(updates, timeout(1000)).handleVersionCheck(userId, instanceId, 7, payload, "trace-update");
        verifyNoInteractions(tunnel);
    }

    @Test
    void blockedVersionCheckDoesNotDelayFileOrLifecycleResponseAcceptance() throws Exception {
        LocalClientPayloads.VersionCheck payload = new LocalClientPayloads.VersionCheck(
                instanceId.value(), "20260820180000", "1", "2.0.18",
                List.of("SELF_UPDATE_V1"), Instant.parse("2026-08-20T10:00:00Z"));
        CountDownLatch versionCheckEntered = new CountDownLatch(1);
        CountDownLatch releaseVersionCheck = new CountDownLatch(1);
        doAnswer(invocation -> {
                    versionCheckEntered.countDown();
                    assertThat(releaseVersionCheck.await(3, TimeUnit.SECONDS)).isTrue();
                    return null;
                })
                .when(updates).handleVersionCheck(userId, instanceId, 7, payload, "trace-update");

        StepVerifier.create(handler.handleAuthenticated(
                        frame(LocalClientFrameType.VERSION_CHECK, "req-blocked-version", payload),
                        Sinks.many().unicast().onBackpressureBuffer(),
                        Sinks.one(),
                        state))
                .verifyComplete();
        assertThat(versionCheckEntered.await(1, TimeUnit.SECONDS)).isTrue();

        LocalClientFrame fileResponse = frame(
                LocalClientFrameType.FILE_RESPONSE,
                "req-file-response",
                new LocalClientPayloads.FileResponse(true, codec.payload(java.util.Map.of("ok", true))));
        StepVerifier.create(handler.handleAuthenticated(
                        fileResponse,
                        Sinks.many().unicast().onBackpressureBuffer(),
                        Sinks.one(),
                        state))
                .verifyComplete();

        LocalClientFrame lifecycleResult = frame(
                LocalClientFrameType.LIFECYCLE_RESULT,
                "req-lifecycle-result",
                java.util.Map.of("success", true));
        StepVerifier.create(handler.handleAuthenticated(
                        lifecycleResult,
                        Sinks.many().unicast().onBackpressureBuffer(),
                        Sinks.one(),
                        state))
                .verifyComplete();

        verify(tunnel).accept(instanceId, fileResponse);
        verify(tunnel).accept(instanceId, lifecycleResult);
        releaseVersionCheck.countDown();
    }

    @Test
    void clientCannotForgeServerUpdateCommand() {
        LocalClientFrame frame = frame(
                LocalClientFrameType.UPDATE_COMMAND,
                "cmd-forged",
                new LocalClientPayloads.UpdateCommand(
                        "cmd-forged", instanceId.value(), 7, 12, "20260820190000", "UPDATE"));

        StepVerifier.create(handler.handleAuthenticated(
                        frame,
                        Sinks.many().unicast().onBackpressureBuffer(),
                        Sinks.one(),
                        state))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(PlatformException.class))
                .verify();

        verifyNoInteractions(updates, tunnel);
    }

    @Test
    void legacyClientCannotEnterSelfUpdateProtocolOrMutateUpdateState() {
        LocalClientPayloads.VersionCheck payload = new LocalClientPayloads.VersionCheck(
                instanceId.value(), "0.1.0", null, "2.0.18", List.of(), Instant.now());

        Sinks.One<String> closeSignal = Sinks.one();
        StepVerifier.create(handler.handleAuthenticated(
                        frame(LocalClientFrameType.VERSION_CHECK, "req-legacy-version", payload),
                        Sinks.many().unicast().onBackpressureBuffer(),
                        closeSignal,
                        legacyState))
                .verifyComplete();

        StepVerifier.create(closeSignal.asMono())
                .expectNext("PROTOCOL_ERROR")
                .verifyComplete();

        verifyNoInteractions(updates, tunnel);
    }

    @Test
    void updateStatusIsAcknowledgedOnlyAfterCoordinatorReturns() {
        LocalClientPayloads.UpdateStatus payload = new LocalClientPayloads.UpdateStatus(
                "cmd-status", instanceId.value(), 7, 12, "20260820190000", "UPDATE",
                "SUCCEEDED", null, Instant.parse("2026-08-20T10:00:00Z"));
        Sinks.Many<LocalClientFrame> outbound = Sinks.many().unicast().onBackpressureBuffer();
        doAnswer(invocation -> new LocalClientPayloads.UpdateStatusAck(
                    payload.commandId(), instanceId.value(), 7, "SUCCEEDED"))
                .when(updates).handleStatus(userId, instanceId, 7, payload, "trace-update");

        StepVerifier.create(handler.handleAuthenticated(
                        frame(LocalClientFrameType.UPDATE_STATUS, "cmd-status", payload),
                        outbound,
                        Sinks.one(),
                        state))
                .verifyComplete();

        StepVerifier.create(outbound.asFlux().take(1))
                .assertNext(ackFrame -> {
                    assertThat(ackFrame.type()).isEqualTo(LocalClientFrameType.UPDATE_STATUS_ACK);
                    LocalClientPayloads.UpdateStatusAck ack = codec.payload(
                            ackFrame, LocalClientPayloads.UpdateStatusAck.class);
                    assertThat(ack.commandId()).isEqualTo("cmd-status");
                    assertThat(ack.clientInstanceId()).isEqualTo(instanceId.value());
                    assertThat(ack.connectionGeneration()).isEqualTo(7);
                    assertThat(ack.status()).isEqualTo("SUCCEEDED");
                })
                .verifyComplete();
        verify(updates).handleStatus(userId, instanceId, 7, payload, "trace-update");
    }

    @Test
    void deadlineFailureCorrectedToAutoRollbackIsAcknowledgedWithPersistedStatus() {
        LocalClientPayloads.UpdateStatus payload = new LocalClientPayloads.UpdateStatus(
                "cmd-late-rollback", instanceId.value(), 7, 12, "20260820190000", "UPDATE",
                "AUTO_ROLLED_BACK", "STARTUP_FAILED", Instant.parse("2026-08-20T10:00:00Z"));
        Sinks.Many<LocalClientFrame> outbound = Sinks.many().unicast().onBackpressureBuffer();
        doAnswer(invocation -> new LocalClientPayloads.UpdateStatusAck(
                    payload.commandId(), instanceId.value(), 7, "AUTO_ROLLED_BACK"))
                .when(updates).handleStatus(userId, instanceId, 7, payload, "trace-update");

        StepVerifier.create(handler.handleAuthenticated(
                        frame(LocalClientFrameType.UPDATE_STATUS, "cmd-late-rollback", payload),
                        outbound,
                        Sinks.one(),
                        state))
                .verifyComplete();

        StepVerifier.create(outbound.asFlux().take(1))
                .assertNext(ackFrame -> assertThat(codec.payload(
                                ackFrame, LocalClientPayloads.UpdateStatusAck.class).status())
                        .isEqualTo("AUTO_ROLLED_BACK"))
                .verifyComplete();
    }

    @Test
    void updateStatusAckUsesCoordinatorPersistedOutcomeInsteadOfEchoingClientPayload() {
        LocalClientPayloads.UpdateStatus payload = new LocalClientPayloads.UpdateStatus(
                "cmd-persisted-outcome", instanceId.value(), 7, 12, "20260820190000", "UPDATE",
                "SUCCEEDED", null, Instant.parse("2026-08-20T10:00:00Z"));
        LocalClientPayloads.UpdateStatusAck persisted = new LocalClientPayloads.UpdateStatusAck(
                payload.commandId(), instanceId.value(), 7, "AUTO_ROLLED_BACK");
        Sinks.Many<LocalClientFrame> outbound = Sinks.many().unicast().onBackpressureBuffer();
        doAnswer(invocation -> persisted)
                .when(updates).handleStatus(userId, instanceId, 7, payload, "trace-update");

        StepVerifier.create(handler.handleAuthenticated(
                        frame(LocalClientFrameType.UPDATE_STATUS, payload.commandId(), payload),
                        outbound,
                        Sinks.one(),
                        state))
                .verifyComplete();

        StepVerifier.create(outbound.asFlux().take(1))
                .assertNext(ackFrame -> assertThat(codec.payload(
                                ackFrame, LocalClientPayloads.UpdateStatusAck.class).status())
                        .isEqualTo("AUTO_ROLLED_BACK"))
                .verifyComplete();
    }

    @Test
    void conflictingTerminalStatusDoesNotEmitAck() {
        LocalClientPayloads.UpdateStatus payload = new LocalClientPayloads.UpdateStatus(
                "cmd-conflict", instanceId.value(), 7, 12, "20260820190000", "UPDATE",
                "SUCCEEDED", null, Instant.parse("2026-08-20T10:00:00Z"));
        Sinks.Many<LocalClientFrame> outbound = Sinks.many().unicast().onBackpressureBuffer();
        doAnswer(invocation -> {
                    throw new PlatformException(ErrorCode.CONFLICT, "更新终态与已持久化状态冲突");
                })
                .when(updates).handleStatus(userId, instanceId, 7, payload, "trace-update");

        Sinks.One<String> closeSignal = Sinks.one();
        StepVerifier.create(handler.handleAuthenticated(
                        frame(LocalClientFrameType.UPDATE_STATUS, "cmd-conflict", payload),
                        outbound,
                        closeSignal,
                        state))
                .verifyComplete();

        StepVerifier.create(outbound.asFlux().take(1))
                .assertNext(errorFrame -> assertThat(errorFrame.type()).isEqualTo(LocalClientFrameType.ERROR))
                .verifyComplete();
        StepVerifier.create(closeSignal.asMono())
                .expectNext("PROTOCOL_ERROR")
                .verifyComplete();
    }

    private LocalClientFrame frame(LocalClientFrameType type, String requestId, Object payload) {
        return new LocalClientFrame(
                LocalClientProtocol.VERSION,
                type,
                requestId,
                "trace-update",
                7L,
                codec.payload(payload));
    }
}
