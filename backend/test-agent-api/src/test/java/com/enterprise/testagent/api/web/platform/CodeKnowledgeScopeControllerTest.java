package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.integration.codeknowledge.TraceWeaveCodeKnowledgeSettings;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class CodeKnowledgeScopeControllerTest {

    @Test
    void returnsOnlyMimoRepositoryIdsForTheAuthenticatedPilot() {
        UserId userId = new UserId("usr_pilot");
        TraceWeaveCodeKnowledgeSettings settings = mock(TraceWeaveCodeKnowledgeSettings.class);
        when(settings.selectionScope(userId)).thenReturn(new TraceWeaveCodeKnowledgeSettings.SelectionScope(
                true, null, "DEV", List.of("repo_orders", "repo_payments")));
        CodeKnowledgeScopeController controller = new CodeKnowledgeScopeController(settings);

        var response = controller.scope(exchange(userId)).block();

        assertThat(response.data().available()).isTrue();
        assertThat(response.data().repositoryIds()).containsExactly("repo_orders", "repo_payments");
        verify(settings).selectionScope(userId);
    }

    private MockServerWebExchange exchange(UserId userId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(CodeKnowledgeScopeController.SCOPE_ENDPOINT_PATH));
        exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, new AuthPrincipal(
                "jwt", userId, "pilot", "10000001", List.of("USER"),
                Instant.parse("2026-09-12T00:00:00Z"), Instant.parse("2026-09-13T00:00:00Z")));
        return exchange;
    }
}
