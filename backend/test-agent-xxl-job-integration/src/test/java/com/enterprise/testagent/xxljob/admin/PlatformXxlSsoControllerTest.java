package com.enterprise.testagent.xxljob.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.xxljob.XxlJobAdminBridge;
import com.enterprise.testagent.xxljob.XxlJobSsoIdentity;
import com.xxl.sso.core.helper.XxlSsoHelper;
import com.xxl.sso.core.store.LoginStore;
import com.xxl.tool.response.Response;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

class PlatformXxlSsoControllerTest {

    @Test
    void returnsReadyHandshakePageOnlyAfterTicketProvisioningAndXxlLoginSucceed() {
        XxlJobAdminBridge bridge = mock(XxlJobAdminBridge.class);
        PlatformXxlJobUserProvisioner provisioner = mock(PlatformXxlJobUserProvisioner.class);
        LoginStore loginStore = mock(LoginStore.class);
        XxlSsoHelper.init(loginStore, PlatformXxlSsoConfiguration.TOKEN_KEY, 86_400_000L);

        XxlJobSsoIdentity identity = new XxlJobSsoIdentity(
                "usr_admin",
                "admin",
                "a".repeat(64),
                Instant.parse("2099-07-20T08:00:00Z"));
        PlatformXxlJobUser user = new PlatformXxlJobUser(
                7,
                "usr_admin",
                "admin",
                null,
                1,
                "",
                identity.sessionDigest(),
                identity.sessionExpiresAt());
        when(bridge.consumeTicket("ticket-one")).thenReturn(Optional.of(identity));
        when(provisioner.provision(identity)).thenReturn(user);
        when(loginStore.set(any())).thenReturn(Response.ofSuccess());

        PlatformXxlSsoController controller = new PlatformXxlSsoController(bridge, provisioner);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/xxl-job-admin/platform-sso/login");
        request.setContextPath("/xxl-job-admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = controller.login("ticket-one", request, response);

        assertThat(result.getViewName()).isEqualTo("platform/xxl-sso-complete");
        assertThat(result.getModel()).containsEntry("redirectPath", "/xxl-job-admin/");
        assertThat(response.getCookie(PlatformXxlSsoConfiguration.TOKEN_KEY)).isNotNull();
    }

    @Test
    void rendersPlatformStatusPageWhenRedisTicketConsumptionFails() {
        XxlJobAdminBridge bridge = mock(XxlJobAdminBridge.class);
        PlatformXxlJobUserProvisioner provisioner = mock(PlatformXxlJobUserProvisioner.class);
        when(bridge.consumeTicket("ticket-one"))
                .thenThrow(new org.springframework.data.redis.RedisSystemException(
                        "Error in execution",
                        new IllegalStateException("unknown command GETDEL")));
        PlatformXxlSsoController controller = new PlatformXxlSsoController(bridge, provisioner);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/xxl-job-admin/platform-sso/login");
        request.setContextPath("/xxl-job-admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = controller.login("ticket-one", request, response);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(result.getViewName()).isEqualTo("platform/xxl-sso-status");
        assertThat(result.getModel())
                .containsEntry("state", "unavailable")
                .containsEntry("message", "XXL-JOB 管理服务暂不可用");
    }

    @Test
    void rendersPlatformStatusPageWhenXxlLoginStoreFails() {
        XxlJobAdminBridge bridge = mock(XxlJobAdminBridge.class);
        PlatformXxlJobUserProvisioner provisioner = mock(PlatformXxlJobUserProvisioner.class);
        LoginStore loginStore = mock(LoginStore.class);
        XxlSsoHelper.init(loginStore, PlatformXxlSsoConfiguration.TOKEN_KEY, 86_400_000L);
        XxlJobSsoIdentity identity = new XxlJobSsoIdentity(
                "usr_admin",
                "admin",
                "a".repeat(64),
                Instant.parse("2099-07-20T08:00:00Z"));
        PlatformXxlJobUser user = new PlatformXxlJobUser(
                7,
                "usr_admin",
                "admin",
                null,
                1,
                "",
                identity.sessionDigest(),
                identity.sessionExpiresAt());
        when(bridge.consumeTicket("ticket-one")).thenReturn(Optional.of(identity));
        when(provisioner.provision(identity)).thenReturn(user);
        when(loginStore.set(any())).thenThrow(new IllegalStateException("login store unavailable"));
        PlatformXxlSsoController controller = new PlatformXxlSsoController(bridge, provisioner);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/xxl-job-admin/platform-sso/login");
        request.setContextPath("/xxl-job-admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = controller.login("ticket-one", request, response);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(result.getViewName()).isEqualTo("platform/xxl-sso-status");
        assertThat(result.getModel()).containsEntry("state", "unavailable");
    }
}
