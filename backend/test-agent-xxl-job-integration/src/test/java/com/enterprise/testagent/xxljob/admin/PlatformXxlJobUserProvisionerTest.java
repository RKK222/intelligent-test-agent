package com.enterprise.testagent.xxljob.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.xxljob.XxlJobSsoIdentity;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PlatformXxlJobUserProvisionerTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-07-20T01:00:00Z");

    @Test
    void jitProvisionIsIdempotentAndTracksPlatformRename() {
        PlatformXxlJobUserMapper mapper = org.mockito.Mockito.mock(PlatformXxlJobUserMapper.class);
        PlatformXxlJobUser existing = user(7, "usr_admin", "旧名称");
        when(mapper.findByPlatformUserId("usr_admin"))
                .thenReturn(existing, user(7, "usr_admin", "新名称"));
        PlatformXxlJobUserProvisioner provisioner = provisioner(mapper);

        PlatformXxlJobUser result = provisioner.provision(identity("usr_admin", "新名称"));

        verify(mapper).upsert(argThat(command -> command.platformUserId().equals("usr_admin")
                && command.username().equals("新名称")
                && command.role() == 1));
        assertThat(result.username()).isEqualTo("新名称");
    }

    @Test
    void usernameCollisionAppendsStablePlatformUserHash() {
        PlatformXxlJobUserMapper mapper = org.mockito.Mockito.mock(PlatformXxlJobUserMapper.class);
        when(mapper.findByPlatformUserId("usr_second"))
                .thenReturn(null, user(8, "usr_second", "同名用户-ec12270b"));
        when(mapper.findByUsername("同名用户"))
                .thenReturn(user(1, "usr_first", "同名用户"));
        PlatformXxlJobUserProvisioner provisioner = provisioner(mapper);

        PlatformXxlJobUser result = provisioner.provision(identity("usr_second", "同名用户"));

        verify(mapper).upsert(argThat(command -> command.username().matches("同名用户-[a-f0-9]{8}")
                && !command.username().equals("同名用户")));
        assertThat(result.platformUserId()).isEqualTo("usr_second");
    }

    private static PlatformXxlJobUserProvisioner provisioner(PlatformXxlJobUserMapper mapper) {
        return new PlatformXxlJobUserProvisioner(
                mapper,
                Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC),
                new SecureRandom(new byte[]{9, 8, 7}));
    }

    private static XxlJobSsoIdentity identity(String id, String name) {
        return new XxlJobSsoIdentity(
                id,
                name,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                EXPIRES_AT);
    }

    private static PlatformXxlJobUser user(int id, String platformUserId, String username) {
        return new PlatformXxlJobUser(
                id,
                platformUserId,
                username,
                "signature",
                1,
                "",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                EXPIRES_AT);
    }
}
