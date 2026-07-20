package com.enterprise.testagent.xxljob.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.xxljob.XxlJobAdminBridge;
import com.enterprise.testagent.xxljob.XxlJobSsoIdentity;
import com.xxl.sso.core.model.LoginInfo;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformXxlLoginStoreTest {

    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");

    @Test
    void everyLookupRequiresActivePlatformSessionMarker() {
        PlatformXxlJobUserMapper mapper = org.mockito.Mockito.mock(PlatformXxlJobUserMapper.class);
        XxlJobAdminBridge bridge = org.mockito.Mockito.mock(XxlJobAdminBridge.class);
        PlatformXxlJobUser user = new PlatformXxlJobUser(
                7,
                "usr_admin",
                "平台管理员",
                "xxl-signature",
                1,
                "",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                NOW.plusSeconds(60));
        when(mapper.findById(7)).thenReturn(user);
        PlatformXxlLoginStore store = new PlatformXxlLoginStore(
                mapper, bridge, Clock.fixed(NOW, ZoneOffset.UTC));

        when(bridge.isPlatformSessionActive(user.sessionDigest())).thenReturn(true, false);

        assertThat(store.get("7").isSuccess()).isTrue();
        assertThat(store.get("7").isSuccess()).isFalse();
    }

    @Test
    void loginInfoKeepsAdminRoleAndPlatformDisplayName() {
        PlatformXxlJobUserMapper mapper = org.mockito.Mockito.mock(PlatformXxlJobUserMapper.class);
        XxlJobAdminBridge bridge = new XxlJobAdminBridge() {
            @Override
            public Optional<XxlJobSsoIdentity> consumeTicket(String ticket) {
                return Optional.empty();
            }

            @Override
            public boolean isPlatformSessionActive(String sessionDigest) {
                return true;
            }
        };
        when(mapper.findById(7)).thenReturn(new PlatformXxlJobUser(
                7,
                "usr_admin",
                "平台管理员",
                "xxl-signature",
                1,
                "",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                NOW.plusSeconds(60)));
        PlatformXxlLoginStore store = new PlatformXxlLoginStore(
                mapper, bridge, Clock.fixed(NOW, ZoneOffset.UTC));

        LoginInfo loginInfo = store.get("7").getData();

        assertThat(loginInfo.getUserName()).isEqualTo("平台管理员");
        assertThat(loginInfo.getRoleList()).contains("ADMIN");
        assertThat(loginInfo.getSignature()).isEqualTo("xxl-signature");
    }
}
