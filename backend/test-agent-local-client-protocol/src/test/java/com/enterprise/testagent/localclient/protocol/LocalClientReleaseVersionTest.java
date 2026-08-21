package com.enterprise.testagent.localclient.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.localclient.LocalClientReleaseVersion;
import com.enterprise.testagent.common.localclient.LocalClientUpdateDirection;

import org.junit.jupiter.api.Test;

class LocalClientReleaseVersionTest {

    @Test
    void shouldCompareFixedWidthBeijingBuildVersionsAndResolveDirection() {
        LocalClientReleaseVersion current = LocalClientReleaseVersion.parse("20260820153045");
        LocalClientReleaseVersion newer = LocalClientReleaseVersion.parse("20260820163045");
        LocalClientReleaseVersion older = LocalClientReleaseVersion.parse("20260819163045");

        assertThat(current.directionTo(newer)).isEqualTo(LocalClientUpdateDirection.UPDATE);
        assertThat(current.directionTo(older)).isEqualTo(LocalClientUpdateDirection.ROLLBACK);
        assertThat(current.directionTo(current)).isEqualTo(LocalClientUpdateDirection.SAME);
        assertThat(newer).isGreaterThan(current);
    }

    @Test
    void shouldRejectLegacyMalformedAndImpossibleManagedVersions() {
        assertThatThrownBy(() -> LocalClientReleaseVersion.parse("0.1.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LocalClientReleaseVersion.parse("2026082015304"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LocalClientReleaseVersion.parse("20260230153045"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
