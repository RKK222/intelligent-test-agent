package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadProgress;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.SkillHubUploadRequest;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.workspace.AgentSkillHubApplicationService;
import com.enterprise.testagent.workspace.AgentSkillHubResponses;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Skill Hub 分类入口必须在 HTTP 边界强校验超级管理员身份。 */
class AgentSkillHubControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");

    @Test
    void superAdminCanClassifyUserPushedSkill() {
        AgentSkillHubApplicationService service = mock(AgentSkillHubApplicationService.class);
        when(service.classifySkill("hub_asset_1", "TEST", "TEST_DESIGN", new UserId("usr_admin")))
                .thenReturn(new AgentSkillHubResponses.ClassificationResponse(
                        "hub_asset_1", "TEST", "TEST_DESIGN", "usr_admin", NOW));

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).put()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/assets/hub_asset_1/classification")
                .header("X-Trace-Id", "trace_hub_classification")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"category":"TEST","subcategory":"TEST_DESIGN"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.category").isEqualTo("TEST")
                .jsonPath("$.data.subcategory").isEqualTo("TEST_DESIGN")
                .jsonPath("$.data.classifiedByUserId").isEqualTo("usr_admin");

        verify(service).classifySkill("hub_asset_1", "TEST", "TEST_DESIGN", new UserId("usr_admin"));
    }

    @Test
    void appAdminAndAnonymousCannotClassifySkill() {
        AgentSkillHubApplicationService service = mock(AgentSkillHubApplicationService.class);

        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).put()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/assets/hub_asset_1/classification")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"category\":\"OTHER\"}")
                .exchange()
                .expectStatus().isForbidden();

        WebTestClient.bindToController(new AgentSkillHubController(service))
                .webFilter(new TraceIdWebFilter())
                .controllerAdvice(new GlobalExceptionHandler())
                .build()
                .put()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/assets/hub_asset_1/classification")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"category\":\"OTHER\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void listForwardsExplicitSourceAndExternalSyncRequiresSuperAdmin() {
        AgentSkillHubApplicationService service = mock(AgentSkillHubApplicationService.class);
        when(service.listAssets(
                "SKILL", null, null, "ALL", null, false, 1, 30, null, new UserId("usr_admin")))
                .thenReturn(new AgentSkillHubResponses.PageResponse<>(List.of(), 0, 1, 30));
        when(service.syncExternalSkillHubCatalog())
                .thenReturn(new AgentSkillHubResponses.ExternalSyncResponse(2, NOW));

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).get()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/assets?type=SKILL&source=ALL")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.data.total").isEqualTo(0);
        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).post()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/external/sync")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.data.assetCount").isEqualTo(2);
        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).post()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/external/sync")
                .exchange().expectStatus().isForbidden();

        verify(service).listAssets(
                "SKILL", null, null, "ALL", null, false, 1, 30, null, new UserId("usr_admin"));
        verify(service).syncExternalSkillHubCatalog();
    }

    @Test
    void uploadAndProgressExposeDocumentedResultWithoutInventedFields() throws Exception {
        AgentSkillHubApplicationService service = mock(AgentSkillHubApplicationService.class);
        when(service.uploadExternalSkillHub(any())).thenReturn("user001_1723526400000");
        when(service.externalSkillHubUploadProgress("user001_1723526400000"))
                .thenReturn(new SkillHubUploadProgress(50, "数据保存 - 开始"));

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).post()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/external/upload")
                .body(BodyInserters.fromMultipartData(uploadBody()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isEqualTo("user001_1723526400000");
        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).get()
                .uri(uri -> uri.path("/api/internal/platform/workspace-management/agent-skill-hub/external/upload/progress")
                        .queryParam("taskId", "user001_1723526400000").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.progress").isEqualTo(50)
                .jsonPath("$.data.message").isEqualTo("数据保存 - 开始")
                .jsonPath("$.data.taskId").doesNotExist();
        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).post()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/external/upload")
                .body(BodyInserters.fromMultipartData(uploadBody()))
                .exchange()
                .expectStatus().isForbidden();

        ArgumentCaptor<SkillHubUploadRequest> request = ArgumentCaptor.forClass(SkillHubUploadRequest.class);
        verify(service).uploadExternalSkillHub(request.capture());
        assertThat(request.getValue().source()).isEqualTo("研发团队");
        assertThat(request.getValue().phase()).isEqualTo("04");
        assertThat(request.getValue().userId()).isEqualTo("AUTH_HUB");
        assertThat(request.getValue().skillPackage().filename()).isEqualTo("api-check.zip");
        verify(service).externalSkillHubUploadProgress("user001_1723526400000");
    }

    @Test
    void materializeUsesAuthenticatedUnifiedAuthIdForSkillHubDownload() {
        AgentSkillHubApplicationService service = mock(AgentSkillHubApplicationService.class);

        client(service, List.of(Dictionary.ROLE_APP_ADMIN)).post()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/assets/hub_external/materialize")
                .exchange()
                .expectStatus().isOk();

        verify(service).materializeExternalAsset(
                "hub_external", null, new UserId("usr_admin"), "AUTH_HUB");
    }

    @Test
    void uploadRejectsClientSuppliedUserId() throws Exception {
        AgentSkillHubApplicationService service = mock(AgentSkillHubApplicationService.class);
        var body = uploadBody();
        body.add("userId", new HttpEntity<>("FORGED_AUTH_ID"));

        client(service, List.of(Dictionary.ROLE_SUPER_ADMIN)).post()
                .uri("/api/internal/platform/workspace-management/agent-skill-hub/external/upload")
                .body(BodyInserters.fromMultipartData(body))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");

        verify(service, never()).uploadExternalSkillHub(any());
    }

    private static org.springframework.util.MultiValueMap<String, org.springframework.http.HttpEntity<?>> uploadBody()
            throws Exception {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("source", "研发团队");
        body.part("phase", "04");
        body.part("file", resource("api-check.zip", zip())).contentType(MediaType.APPLICATION_OCTET_STREAM);
        body.part("safetyReportPic", resource("safety.png", new byte[]{1})).contentType(MediaType.IMAGE_PNG);
        body.part("directoryStructurePic", resource("directory.png", new byte[]{2})).contentType(MediaType.IMAGE_PNG);
        body.part("runningEffectPic", resource("running.png", new byte[]{3})).contentType(MediaType.IMAGE_PNG);
        return body.build();
    }

    private static ByteArrayResource resource(String filename, byte[] content) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private static byte[] zip() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(output)) {
            archive.putNextEntry(new ZipEntry("SKILL.md"));
            archive.write("name: api-check".getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
        }
        return output.toByteArray();
    }

    private static WebTestClient client(AgentSkillHubApplicationService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "platform-token", new UserId("usr_admin"), "平台管理员", "AUTH_HUB",
                roles, NOW, NOW.plusSeconds(3600));
        return WebTestClient.bindToController(new AgentSkillHubController(service))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
