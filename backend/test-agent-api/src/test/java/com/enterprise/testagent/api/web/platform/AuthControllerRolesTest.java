package com.enterprise.testagent.api.web.platform;

import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.DictId;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.system.management.auth.AuthApplicationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class AuthControllerRolesTest {

    private static Dictionary dictionaryFor(String value, String label) {
        return new Dictionary(
                new DictId("dict_role_" + value.toLowerCase()),
                "应用角色",
                Dictionary.DICT_KEY_ROLE,
                value,
                label,
                1,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-23T00:00:00Z"));
    }

    @Test
    void meReturnsRolesAndChineseRoleLabelsFromDictionary() {
        AuthApplicationService service = org.mockito.Mockito.mock(AuthApplicationService.class);
        DictionaryRepository dictionaryRepository = org.mockito.Mockito.mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, Dictionary.ROLE_APP_ADMIN))
                .thenReturn(Optional.of(dictionaryFor(Dictionary.ROLE_APP_ADMIN, "应用管理员")));
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                new UserId("usr_1234567890abcdef"),
                "admin",
                "AUTH_1",
                List.of(Dictionary.ROLE_APP_ADMIN),
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"));
        WebTestClient client = WebTestClient.bindToController(new AuthController(service, dictionaryRepository))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .build();

        client.get()
                .uri("/api/auth/me")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.roles[0]").isEqualTo("APP_ADMIN")
                .jsonPath("$.data.roleLabels[0]").isEqualTo("应用管理员");
    }

    @Test
    void meFallsBackToRoleCodeWhenDictionaryEntryIsMissing() {
        AuthApplicationService service = org.mockito.Mockito.mock(AuthApplicationService.class);
        DictionaryRepository dictionaryRepository = org.mockito.Mockito.mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, "UNKNOWN_ROLE"))
                .thenReturn(Optional.empty());
        AuthPrincipal principal = new AuthPrincipal(
                "token",
                new UserId("usr_1234567890abcdef"),
                "admin",
                "AUTH_1",
                List.of("UNKNOWN_ROLE"),
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"));
        WebTestClient client = WebTestClient.bindToController(new AuthController(service, dictionaryRepository))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .build();

        client.get()
                .uri("/api/auth/me")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.roleLabels[0]").isEqualTo("UNKNOWN_ROLE");
    }

    @Test
    void loginReturnsRolesLoadedByAuthService() {
        AuthApplicationService service = org.mockito.Mockito.mock(AuthApplicationService.class);
        DictionaryRepository dictionaryRepository = org.mockito.Mockito.mock(DictionaryRepository.class);
        when(service.login("admin", "secret", "unknown", null))
                .thenReturn(new AuthPrincipal(
                        "token",
                        new UserId("usr_1234567890abcdef"),
                        "admin",
                        "AUTH_1",
                        List.of(Dictionary.ROLE_APP_ADMIN),
                        Instant.parse("2026-06-23T00:00:00Z"),
                        Instant.parse("2026-06-24T00:00:00Z")));
        WebTestClient client = WebTestClient.bindToController(new AuthController(service, dictionaryRepository))
                .webFilter(new TraceIdWebFilter())
                .build();

        client.post()
                .uri("/api/auth/login")
                .header("X-Trace-Id", "trace_1234567890abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"admin","password":"secret"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.roles[0]").isEqualTo("APP_ADMIN");
    }
}
