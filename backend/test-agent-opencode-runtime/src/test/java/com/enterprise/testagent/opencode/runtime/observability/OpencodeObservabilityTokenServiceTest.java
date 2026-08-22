package com.enterprise.testagent.opencode.runtime.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeObservabilityGenerationRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartupRequest;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OpencodeObservabilityTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T08:00:00Z");
    private static final UserId USER_ID = new UserId("usr_observability");
    private static final OpencodeProcessId PROCESS_ID = new OpencodeProcessId("ocp_observability");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("linux-observability");
    private static final OpencodeContainerId CONTAINER_ID = new OpencodeContainerId("container-observability");
    private static final String GENERATION = "generation-20260822";

    @Test
    void issuedTokenAuthenticatesOnlyTheBoundRunningProcessGeneration() {
        Fixture fixture = fixture(NOW, process(GENERATION));

        String token = fixture.service().issue(request(GENERATION));
        OpencodeObservabilityTokenService.Principal principal =
                fixture.service().authenticate("Bearer " + token, GENERATION);

        assertThat(principal.user().userId()).isEqualTo(USER_ID);
        assertThat(principal.process().processId()).isEqualTo(PROCESS_ID);
        assertThat(principal.generation()).isEqualTo(GENERATION);
        assertThat(token).doesNotContain("manager-observability-secret");
    }

    @Test
    void rejectsTamperingWrongGenerationAndExpiredCredentials() {
        Fixture issuer = fixture(NOW, process(GENERATION));
        String token = issuer.service().issue(request(GENERATION));

        assertUnauthenticated(() -> issuer.service().authenticate("Bearer " + token + "x", GENERATION));
        assertUnauthenticated(() -> issuer.service().authenticate("Bearer " + token, "old-generation"));

        Fixture expired = fixture(NOW.plus(Duration.ofDays(8)), process(GENERATION));
        assertUnauthenticated(() -> expired.service().authenticate("Bearer " + token, GENERATION));
    }

    @Test
    void configurableShortTtlExpiresWithoutWaitingForTheProcessGenerationToChange() {
        Fixture issuer = fixture(NOW, process(GENERATION), Duration.ofMinutes(2));
        String token = issuer.service().issue(request(GENERATION));
        Fixture expired = fixture(NOW.plus(Duration.ofMinutes(3)), process(GENERATION), Duration.ofMinutes(2));

        assertUnauthenticated(() -> expired.service().authenticate("Bearer " + token, GENERATION));
    }

    @Test
    void rejectsAValidSignatureAfterTheDatabaseProcessGenerationChanges() {
        Fixture issuer = fixture(NOW, process(GENERATION));
        String token = issuer.service().issue(request(GENERATION));
        Fixture restarted = fixture(NOW.plusSeconds(1), process("new-generation"));

        assertUnauthenticated(() -> restarted.service().authenticate("Bearer " + token, GENERATION));
    }

    private Fixture fixture(Instant now, OpencodeServerProcess process) {
        return fixture(now, process, Duration.ofDays(7));
    }

    private Fixture fixture(Instant now, OpencodeServerProcess process, Duration tokenTtl) {
        OpencodeProcessManagementRepository processRepository = mock(OpencodeProcessManagementRepository.class);
        OpencodeObservabilityGenerationRepository generationRepository =
                mock(OpencodeObservabilityGenerationRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        User user = mock(User.class);
        when(user.userId()).thenReturn(USER_ID);
        when(user.canLogin()).thenReturn(true);
        when(userRepository.findByUserId(USER_ID)).thenReturn(Optional.of(user));
        when(processRepository.findOpencodeServerProcessById(PROCESS_ID)).thenReturn(Optional.of(process));
        when(generationRepository.findByProcessId(PROCESS_ID)).thenReturn(Optional.of(process.traceId()));
        ManagerControlSettings settings = new ManagerControlSettings(
                "manager-observability-secret",
                "http://127.0.0.1:8080",
                SERVER_ID,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                100);
        return new Fixture(new OpencodeObservabilityTokenService(
                settings,
                processRepository,
                generationRepository,
                userRepository,
                Clock.fixed(now, ZoneOffset.UTC),
                tokenTtl));
    }

    private OpencodeProcessStartupRequest request(String generation) {
        return new OpencodeProcessStartupRequest(
                USER_ID,
                PROCESS_ID,
                NOW,
                NOW,
                SERVER_ID,
                CONTAINER_ID,
                43121,
                "http://127.0.0.1:43121",
                "/data/sessions",
                "/data/config",
                Map.of(),
                generation);
    }

    private OpencodeServerProcess process(String generation) {
        return new OpencodeServerProcess(
                PROCESS_ID,
                USER_ID,
                SERVER_ID,
                CONTAINER_ID,
                43121,
                8123L,
                "http://127.0.0.1:43121",
                OpencodeServerProcessStatus.RUNNING,
                "/data/sessions",
                "/data/config",
                NOW,
                NOW,
                "ok",
                NOW,
                NOW,
                generation);
    }

    private void assertUnauthenticated(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    private record Fixture(OpencodeObservabilityTokenService service) {
    }
}
