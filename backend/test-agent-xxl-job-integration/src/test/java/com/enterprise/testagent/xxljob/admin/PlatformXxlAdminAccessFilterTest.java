package com.enterprise.testagent.xxljob.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.xxljob.XxlJobProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PlatformXxlAdminAccessFilterTest {

    @Test
    void blocksNativeLoginPasswordAndUserMutationsButKeepsUserListReadable() throws Exception {
        PlatformXxlAdminAccessFilter filter = new PlatformXxlAdminAccessFilter();

        assertBlocked(filter, "POST", "/auth/doLogin");
        assertBlocked(filter, "POST", "/auth/updatePwd");
        assertBlocked(filter, "POST", "/user/insert");
        assertBlocked(filter, "POST", "/user/update");
        assertBlocked(filter, "POST", "/user/delete");

        AtomicBoolean invoked = new AtomicBoolean();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/pageList");
        filter.doFilter(request, new MockHttpServletResponse(),
                (req, response) -> invoked.set(true));
        assertThat(invoked).isTrue();
    }

    @Test
    void addsSameOriginFramePolicyAndHardensXxlCookie() throws Exception {
        XxlJobProperties properties = new XxlJobProperties();
        PlatformXxlAdminSecurityHeadersFilter filter = new PlatformXxlAdminSecurityHeadersFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setContextPath("/xxl-job-admin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, rawResponse) -> ((jakarta.servlet.http.HttpServletResponse) rawResponse)
                .addCookie(new Cookie("test_agent_xxl_login", "secret"));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("frame-ancestors 'self'");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        Cookie cookie = response.getCookie("test_agent_xxl_login");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/xxl-job-admin/");
    }

    @Test
    void permitsExplicitHttpOnlyDeploymentWithoutSecureCookie() throws Exception {
        XxlJobProperties properties = new XxlJobProperties();
        properties.getAdmin().setCookieSecure(false);
        PlatformXxlAdminSecurityHeadersFilter filter = new PlatformXxlAdminSecurityHeadersFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setContextPath("/xxl-job-admin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, rawResponse) -> ((jakarta.servlet.http.HttpServletResponse) rawResponse)
                .addCookie(new Cookie("test_agent_xxl_login", "secret"));

        filter.doFilter(request, response, chain);

        Cookie cookie = response.getCookie("test_agent_xxl_login");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    private static void assertBlocked(PlatformXxlAdminAccessFilter filter, String method, String path)
            throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, rawResponse) -> invoked.set(true));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("NATIVE_LOGIN_DISABLED");
        assertThat(invoked).isFalse();
    }
}
