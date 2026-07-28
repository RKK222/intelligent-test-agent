package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceGitToolTokenService;
import com.enterprise.testagent.workspace.ConversationWorkspaceGitApplicationService;
import com.enterprise.testagent.workspace.ConversationWorkspaceGitApplicationService.ConversationWorkspaceGitResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class WorkspaceGitToolControllerTest {

    @Test
    void dedicatedTokenIdentityAndRemoteSessionAreForwardedToBusinessService() {
        WorkspaceGitToolTokenService tokenService = mock(WorkspaceGitToolTokenService.class);
        ConversationWorkspaceGitApplicationService gitService = mock(ConversationWorkspaceGitApplicationService.class);
        UserId userId = new UserId("usr_1234567890abcdef");
        String traceId = "trace_1234567890abcdef";
        when(tokenService.authenticate("Bearer signed-tool-token"))
                .thenReturn(new WorkspaceGitToolTokenService.WorkspaceGitToolPrincipal(
                        userId, List.of("USER")));
        when(gitService.execute(
                "ses_remote",
                "status",
                null,
                null,
                null,
                null,
                null,
                null,
                userId,
                List.of("USER"),
                traceId))
                .thenReturn(new ConversationWorkspaceGitResult(
                        "STATUS", "wrk_123", "pws_123", Map.of("clean", true)));
        WorkspaceGitToolController controller = new WorkspaceGitToolController(tokenService, gitService);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(
                        WorkspaceGitToolTokenService.ENDPOINT_PATH)
                .header("Authorization", "Bearer signed-tool-token"));
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, traceId);

        var response = controller.execute(
                new WorkspaceGitToolController.WorkspaceGitToolRequest(
                        "ses_remote", "status", null, null, null, null, null, null),
                exchange);

        assertThat(response.data()).isInstanceOf(ConversationWorkspaceGitResult.class);
        verify(tokenService).authenticate("Bearer signed-tool-token");
    }
}
