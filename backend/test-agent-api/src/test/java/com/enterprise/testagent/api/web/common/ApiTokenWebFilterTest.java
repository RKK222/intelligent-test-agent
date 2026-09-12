package com.enterprise.testagent.api.web.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.observability.TraceConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class ApiTokenWebFilterTest {

    @Test
    void filterExemptsOnlyExactAnonymousLoginPaths() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter("secret-token");
        for (String path : java.util.List.of("/api/auth/login", "/api/auth/login-by-unified-auth")) {
            MockServerWebExchange exact = MockServerWebExchange.from(MockServerHttpRequest.post(path));
            final boolean[] called = {false};
            filter.filter(exact, currentExchange -> {
                called[0] = true;
                return Mono.empty();
            }).block();
            assertThat(called[0]).as(path).isTrue();

            MockServerWebExchange child = MockServerWebExchange.from(MockServerHttpRequest.post(path + "/extra"));
            filter.filter(child, currentExchange -> Mono.empty()).block();
            assertThat(child.getResponse().getStatusCode().value()).as(path + "/extra").isEqualTo(401);
        }
    }

    @Test
    void filterAllowsRequestsWhenTokenIsNotConfigured() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter(null);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/internal/platform/workspace-management/workspaces"));
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, "trace_1234567890abcdef");
        final boolean[] called = {false};
        WebFilterChain chain = currentExchange -> {
            called[0] = true;
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(called[0]).isTrue();
    }

    @Test
    void filterRejectsRequestsWhenBearerTokenDoesNotMatch() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter("secret-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/internal/platform/workspace-management/workspaces"));
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, "trace_1234567890abcdef");

        filter.filter(exchange, currentExchange -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void filterExemptsOnlyNightExecutionInternalDispatchPath() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter("secret-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(
                "/api/internal/platform/opencode-runtime/night-execution/internal-dispatch"));
        final boolean[] called = {false};

        filter.filter(exchange, currentExchange -> {
            called[0] = true;
            return Mono.empty();
        }).block();

        assertThat(called[0]).isTrue();
    }

    @Test
    void filterExemptsOnlyExactPersonalWorkspaceRelocationInternalPaths() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter("secret-token");
        for (String path : java.util.List.of(
                "/api/internal/platform/workspace-management/personal-workspace-relocations/transfer-tickets",
                "/api/internal/platform/workspace-management/personal-workspace-relocations/transfer/ws")) {
            MockServerWebExchange exact = MockServerWebExchange.from(MockServerHttpRequest.post(path));
            final boolean[] called = {false};
            filter.filter(exact, currentExchange -> {
                called[0] = true;
                return Mono.empty();
            }).block();
            assertThat(called[0]).as(path).isTrue();

            MockServerWebExchange child = MockServerWebExchange.from(MockServerHttpRequest.post(path + "/extra"));
            filter.filter(child, currentExchange -> Mono.empty()).block();
            assertThat(child.getResponse().getStatusCode().value()).as(path + "/extra").isEqualTo(401);
        }
    }

    @Test
    void filterLeavesWorkspaceGitToolAuthenticationToDedicatedController() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter("secret-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(
                "/api/internal/agent/opencode/workspace-git-tool"));
        final boolean[] called = {false};

        filter.filter(exchange, currentExchange -> {
            called[0] = true;
            return Mono.empty();
        }).block();

        assertThat(called[0]).isTrue();
    }

    @Test
    void filterLeavesOnlyExactCodeKnowledgeToolPathsToDedicatedController() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter("secret-token");
        for (String path : java.util.List.of(
                "/api/internal/agent/opencode/code-knowledge-tool",
                "/api/internal/agent/opencode/code-source-tool")) {
            MockServerWebExchange exact = MockServerWebExchange.from(MockServerHttpRequest.post(path));
            final boolean[] called = {false};
            filter.filter(exact, currentExchange -> {
                called[0] = true;
                return Mono.empty();
            }).block();
            assertThat(called[0]).as(path).isTrue();

            MockServerWebExchange child = MockServerWebExchange.from(MockServerHttpRequest.post(path + "/extra"));
            filter.filter(child, currentExchange -> Mono.empty()).block();
            assertThat(child.getResponse().getStatusCode().value()).as(path + "/extra").isEqualTo(401);
        }
    }

    @Test
    void filterExemptsOnlyExactUiTestToolConfigPath() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter("secret-token");
        MockServerWebExchange exact = MockServerWebExchange.from(MockServerHttpRequest.get(
                "/api/internal/agent/opencode/ui-test-tool/config"));
        final boolean[] exactCalled = {false};

        filter.filter(exact, currentExchange -> {
            exactCalled[0] = true;
            return Mono.empty();
        }).block();

        MockServerWebExchange child = MockServerWebExchange.from(MockServerHttpRequest.get(
                "/api/internal/agent/opencode/ui-test-tool/config/extra"));
        filter.filter(child, currentExchange -> Mono.empty()).block();

        assertThat(exactCalled[0]).isTrue();
        assertThat(child.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void filterLeavesLobehubRedeemAndRevokeToHmacAuthentication() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter("secret-token");

        for (String path : java.util.List.of(
                "/api/internal/platform/lobehub-sso/tickets/redeem",
                "/api/internal/platform/lobehub-sso/grants/revoke")) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path));
            final boolean[] called = {false};

            filter.filter(exchange, currentExchange -> {
                called[0] = true;
                return Mono.empty();
            }).block();

            assertThat(called[0]).as(path).isTrue();
        }
    }

    @Test
    void filterLeavesModelGatewayToDelegatedGrantAuthentication() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter("secret-token");

        for (String path : java.util.List.of(
                "/api/internal/platform/model-gateway/v1",
                "/api/internal/platform/model-gateway/v1/models",
                "/api/internal/platform/model-gateway/v1/chat/completions")) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path));
            final boolean[] called = {false};

            filter.filter(exchange, currentExchange -> {
                called[0] = true;
                return Mono.empty();
            }).block();

            assertThat(called[0]).as(path).isTrue();
        }
    }

    @Test
    void filterLeavesOnlyExactExternalNamespaceToDedicatedAuthentication() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter("secret-token");
        for (String path : java.util.List.of(
                "/api/external/v1",
                "/api/external/v1/users/u001/ssh-key")) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
            final boolean[] called = {false};
            filter.filter(exchange, currentExchange -> {
                called[0] = true;
                return Mono.empty();
            }).block();
            assertThat(called[0]).as(path).isTrue();
        }

        MockServerWebExchange adjacent = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/external/v10/users/u001/ssh-key"));
        filter.filter(adjacent, currentExchange -> Mono.empty()).block();
        assertThat(adjacent.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void filterExemptsOnlyExactProtectedAgentMcpPath() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter("secret-token");
        String path = "/api/internal/platform/protected-agent/mcp";
        MockServerWebExchange exact = MockServerWebExchange.from(MockServerHttpRequest.post(path));
        final boolean[] called = {false};

        filter.filter(exact, currentExchange -> {
            called[0] = true;
            return Mono.empty();
        }).block();

        MockServerWebExchange child = MockServerWebExchange.from(MockServerHttpRequest.post(path + "/extra"));
        filter.filter(child, currentExchange -> Mono.empty()).block();
        assertThat(called[0]).isTrue();
        assertThat(child.getResponse().getStatusCode().value()).isEqualTo(401);
    }
}
