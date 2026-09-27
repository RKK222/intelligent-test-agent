package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.team.TeamReviewModels.*;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import com.enterprise.testagent.workspace.TeamReviewApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 单用途通道必须失败关闭，不能通过通用 workspace RPC 或终态 Run 读取。 */
class TeamReviewProtocolServiceTest {
    @Test void sourceEnvelopePreservesAuthorizationErrorsAndRejectsMalformedReplies() throws Exception {
        var mapper = new ObjectMapper();
        var service = new TeamReviewProtocolService(null, null, null, null, null, null, null, null, null, null, null, mapper, null, null);
        assertThat(service.decodeReply(mapper.readTree("{\"id\":\"review\",\"type\":\"result\",\"data\":[]}")))
                .isEqualTo(List.of());
        for (String code : List.of("FORBIDDEN", "UNAUTHENTICATED", "NOT_FOUND")) {
            assertThatThrownBy(() -> service.decodeReply(mapper.readTree(
                    "{\"id\":\"review\",\"type\":\"error\",\"code\":\"" + code + "\",\"message\":\"/private/source-path\"}")))
                    .isInstanceOfSatisfying(PlatformException.class, error -> {
                        assertThat(error.errorCode().name()).isEqualTo(code);
                        assertThat(error.getMessage()).doesNotContain("/private/source-path");
                    });
        }
        for (String reply : List.of("{}", "{\"id\":\"other\",\"type\":\"result\",\"data\":[]}",
                "{\"id\":\"review\",\"type\":\"result\",\"data\":null}",
                "{\"id\":\"review\",\"type\":\"error\",\"code\":\"UNRECOGNIZED\"}"))
            assertThatThrownBy(() -> service.decodeReply(mapper.readTree(reply)))
                    .isInstanceOfSatisfying(PlatformException.class, error ->
                            assertThat(error.errorCode().name()).isEqualTo("INTERNAL_ERROR"));
    }

    @Test void reviewSocketRejectsOrdinaryWorkspaceOperationsAndTerminalRun() throws Exception {
        var review = mock(TeamReviewApplicationService.class);
        var runs = mock(com.enterprise.testagent.domain.run.RunRepository.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var service = new TeamReviewProtocolService(review, null, null, null, null, null, null, null, null, null, null, mapper, null, runs);
        var scope = new Scope("scope", "actor", "digest", TeamScopeMode.MY_TEAM, "actor", "version", null, List.of(), Instant.now().plusSeconds(60));
        when(review.requireScope("scope", "actor")).thenReturn(scope);
        var ticket = new WorkspaceFileSocketTicket("ticket", null, "server", null, false, false, "actor", "team-review", "scope", null, "trace", Instant.now().plusSeconds(60));
        for (String op : List.of("workspace.read", "workspace.write", "workspace.delete", "git.status", "terminal.open"))
            assertThatThrownBy(() -> service.rpc(ticket, op, mapper.readTree("{}"), "trace")).isInstanceOf(PlatformException.class).hasMessageContaining("仅允许");
        when(runs.findById(new com.enterprise.testagent.domain.run.RunId("run_1234567890abcdef"))).thenReturn(java.util.Optional.empty());
        var ended = new WorkspaceFileSocketTicket("ticket", null, "server", null, false, false, "actor", "team-review", "scope", "run_1234567890abcdef", "trace", Instant.now().plusSeconds(60));
        assertThatThrownBy(() -> service.rpc(ended, "team.review.list", mapper.readTree("{}"), "trace")).hasMessageContaining("Run 已结束");
        verify(review, never()).list(any(), anyString(), anyString());
    }
}
