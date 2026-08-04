package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.workspace.PersonalWorkspaceRelocationReceiveService;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 验证内部票据必须经过 XXL token、权威状态校验及一次性 Origin/源服务器绑定。 */
class PersonalWorkspaceRelocationTransferControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-04T04:00:00Z");
    private static final String SHA = "a".repeat(64);

    @Test
    void issuesTargetBoundTicketWithValidXxlToken() {
        PersonalWorkspaceRelocationReceiveService receiveService = mock(PersonalWorkspaceRelocationReceiveService.class);
        PersonalWorkspaceRelocationTransferTicketStore store =
                new PersonalWorkspaceRelocationTransferTicketStore(Clock.fixed(NOW, ZoneOffset.UTC));
        PersonalWorkspaceRelocationTransferTicketService ticketService =
                new PersonalWorkspaceRelocationTransferTicketService(receiveService, store);
        WebTestClient client = client(ticketService, "xxl-secret");

        client.post().uri(PersonalWorkspaceRelocationTransferController.TICKET_PATH)
                .header("XXL-JOB-ACCESS-TOKEN", "xxl-secret")
                .header("X-Trace-Id", "trace_relocation_ticket")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestJson())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ticket").value(value -> org.assertj.core.api.Assertions.assertThat((String) value)
                        .startsWith("pwrt_"))
                .jsonPath("$.data.webSocketPath")
                .isEqualTo(PersonalWorkspaceRelocationTransferController.WEB_SOCKET_PATH);

        verify(receiveService).authorize(
                "pwr_ticket_12345678", "server-a", "server-b", SHA, 123L);
    }

    @Test
    void rejectsWrongTokenBeforeAuthorizingRelocation() {
        PersonalWorkspaceRelocationReceiveService receiveService = mock(PersonalWorkspaceRelocationReceiveService.class);
        PersonalWorkspaceRelocationTransferTicketService ticketService =
                new PersonalWorkspaceRelocationTransferTicketService(
                        receiveService,
                        new PersonalWorkspaceRelocationTransferTicketStore(Clock.fixed(NOW, ZoneOffset.UTC)));

        client(ticketService, "xxl-secret").post()
                .uri(PersonalWorkspaceRelocationTransferController.TICKET_PATH)
                .header("XXL-JOB-ACCESS-TOKEN", "wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestJson())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED");

        verifyNoInteractions(receiveService);
    }

    @Test
    void ticketIsSingleUseAndRequiresExactInternalOriginAndSourceServer() {
        PersonalWorkspaceRelocationTransferTicketStore store =
                new PersonalWorkspaceRelocationTransferTicketStore(Clock.fixed(NOW, ZoneOffset.UTC));
        PersonalWorkspaceRelocationTransferDtos.TicketRequest request =
                new PersonalWorkspaceRelocationTransferDtos.TicketRequest(
                        "pwr_ticket_12345678", "server-a", "server-b", SHA, 123L);
        PersonalWorkspaceRelocationTransferTicket ticket = store.issue(request, "trace_ticket");

        store.consume(
                ticket.ticket(),
                PersonalWorkspaceRelocationTransferController.INTERNAL_ORIGIN,
                "server-a");
        assertThatThrownBy(() -> store.consume(
                ticket.ticket(),
                PersonalWorkspaceRelocationTransferController.INTERNAL_ORIGIN,
                "server-a"))
                .isInstanceOf(PlatformException.class);

        PersonalWorkspaceRelocationTransferTicket wrongOrigin = store.issue(request, "trace_ticket");
        assertThatThrownBy(() -> store.consume(wrongOrigin.ticket(), "https://browser.example", "server-a"))
                .isInstanceOf(PlatformException.class);
        PersonalWorkspaceRelocationTransferTicket wrongSource = store.issue(request, "trace_ticket");
        assertThatThrownBy(() -> store.consume(
                wrongSource.ticket(),
                PersonalWorkspaceRelocationTransferController.INTERNAL_ORIGIN,
                "server-c"))
                .isInstanceOf(PlatformException.class);
    }

    @Test
    void missingTicketIsRejectedAsForbiddenInsteadOfInternalError() {
        PersonalWorkspaceRelocationTransferTicketStore store =
                new PersonalWorkspaceRelocationTransferTicketStore(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> store.consume(
                        null,
                        PersonalWorkspaceRelocationTransferController.INTERNAL_ORIGIN,
                        "server-a"))
                .isInstanceOf(PlatformException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                                ((PlatformException) exception).errorCode())
                        .isEqualTo(com.enterprise.testagent.common.error.ErrorCode.FORBIDDEN));
    }

    private WebTestClient client(
            PersonalWorkspaceRelocationTransferTicketService ticketService, String token) {
        XxlJobProperties properties = new XxlJobProperties();
        properties.setAccessToken(token);
        return WebTestClient.bindToController(
                        new PersonalWorkspaceRelocationTransferController(ticketService, properties))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String requestJson() {
        return """
                {
                  "relocationId":"pwr_ticket_12345678",
                  "sourceLinuxServerId":"server-a",
                  "targetLinuxServerId":"server-b",
                  "snapshotSha256":"%s",
                  "archiveSizeBytes":123
                }
                """.formatted(SHA);
    }
}
