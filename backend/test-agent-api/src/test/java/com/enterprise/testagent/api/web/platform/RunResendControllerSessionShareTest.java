package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.run.RunApplicationService;
import com.enterprise.testagent.opencode.runtime.run.RunResendApplicationService;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/** 验证撤销重发 Controller 的生产 Spring 装配不会丢失会话分享服务。 */
class RunResendControllerSessionShareTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final String TRACE_ID = "trace_resend_share_wiring";
    private static final UserId ACTOR = new UserId("usr_resend_share_actor");
    private static final UserId OWNER = new UserId("usr_resend_share_owner");
    private static final SessionId SESSION = new SessionId("ses_resend_share_wiring");
    private static final WorkspaceId WORKSPACE = new WorkspaceId("wrk_resend_share_wiring");
    private static final SessionShareId SHARE = new SessionShareId("shr_" + "d".repeat(64));

    @Test
    void springManagedControllerUsesShareServiceForSharedResend() {
        RunResendApplicationService resendService = mock(RunResendApplicationService.class);
        RunApplicationService runService = mock(RunApplicationService.class);
        SessionCollaborationShareService shareService = mock(SessionCollaborationShareService.class);
        new ApplicationContextRunner()
                // 已注册单例不再执行业务服务自身的 Spring 方法注入，测试只覆盖 Controller 构造器选择。
                .withInitializer(context -> {
                    context.getBeanFactory().registerSingleton("runResendApplicationService", resendService);
                    context.getBeanFactory().registerSingleton("runApplicationService", runService);
                    context.getBeanFactory().registerSingleton("sessionCollaborationShareService", shareService);
                })
                .withUserConfiguration(TestConfiguration.class)
                .run(context -> {
                    RunResendController controller = context.getBean(RunResendController.class);
                    DelegatedOperationContext delegated = new DelegatedOperationContext(
                            SHARE,
                            3,
                            ACTOR,
                            "ucid_resend_share_actor",
                            "分享成员",
                            OWNER,
                            SESSION,
                            WORKSPACE,
                            true,
                            true,
                            false,
                            NOW.plusSeconds(3600));
                    when(shareService.requireAccess(ACTOR, SHARE, true, TRACE_ID)).thenReturn(delegated);
                    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                            .post("/api/internal/agent/opencode/sessions/" + SESSION.value() + "/resends")
                            .header("X-Trace-Id", TRACE_ID)
                            .header(SessionShareController.SHARE_HEADER, SHARE.value())
                            .build());
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, new AuthPrincipal(
                            "token",
                            ACTOR,
                            "分享成员",
                            "ucid_resend_share_actor",
                            List.of("USER"),
                            NOW,
                            NOW.plusSeconds(3600)));

                    assertThatCode(() -> controller.create(
                                    "opencode",
                                    SESSION.value(),
                                    new RunResendController.Request(
                                            "msg_resend_share_source",
                                            "run_resend_share_source",
                                            "ctx_resend_share",
                                            "request_resend_share",
                                            "修改后的问题"),
                                    SHARE.value(),
                                    exchange))
                            .doesNotThrowAnyException();

                    verify(shareService).requireAccess(ACTOR, SHARE, true, TRACE_ID);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(RunResendController.class)
    static class TestConfiguration {
    }
}
