package com.enterprise.testagent.domain.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalClientDomainTest {

    @Test
    void shouldRequireStablePrefixesAndPositiveGeneration() {
        assertThat(new LocalClientInstanceId("lci_device").value()).isEqualTo("lci_device");
        assertThatThrownBy(() -> new LocalClientInstanceId("device"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalClientConnectionRoute(
                new LocalClientInstanceId("lci_device"),
                new UserId("usr_owner"),
                new BackendProcessId("bjp_backend"),
                0,
                null,
                List.of(),
                null,
                LocalClientProcessStatus.STOPPED,
                null,
                null,
                false,
                Instant.now(),
                Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectionGeneration");
    }

    @Test
    void shouldNotAllowActiveCredentialWithRevokedTimestamp() {
        Instant now = Instant.parse("2026-08-11T12:00:00Z");

        assertThatThrownBy(() -> new LocalClientCredential(
                new UserId("usr_owner"),
                "encrypted",
                "fingerprint",
                "tack_v1_...abcd",
                1,
                LocalClientCredentialStatus.ACTIVE,
                now,
                now,
                null,
                now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active credential");
    }
}
