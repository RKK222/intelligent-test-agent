package com.enterprise.testagent.integration.lobehub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LobehubIntegrationPropertiesTest {

    @Test
    void keepsTheDocumentedSecureDefaults() {
        LobehubIntegrationProperties properties = new LobehubIntegrationProperties();

        assertThat(properties.getTicketTtl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getGrantTtl()).isEqualTo(Duration.ofDays(30));
        assertThat(properties.getHmacClockSkew()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getNonceTtl()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void rejectsNonPositiveOrOverlongSecurityWindows() {
        LobehubIntegrationProperties properties = new LobehubIntegrationProperties();

        assertThatIllegalArgumentException().isThrownBy(() -> properties.setTicketTtl(Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setTicketTtl(Duration.ofSeconds(61)));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setGrantTtl(Duration.ofDays(30).plusSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setHmacClockSkew(Duration.ofSeconds(61)));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setNonceTtl(Duration.ofSeconds(-1)));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setNonceTtl(Duration.ofSeconds(119)));
    }
}
