package com.enterprise.testagent.domain.dictionary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoleCapabilitiesTest {

    @Test
    void followsConfiguredRoleHierarchyWithoutMutatingActualRoles() {
        assertThat(RoleCapabilities.hasCapability(
                List.of(Dictionary.ROLE_SYSTEM_ADMIN), Dictionary.ROLE_APP_ADMIN)).isTrue();
        assertThat(RoleCapabilities.hasCapability(
                List.of(Dictionary.ROLE_SUPER_ADMIN), Dictionary.ROLE_SYSTEM_ADMIN)).isTrue();
        assertThat(RoleCapabilities.hasCapability(
                List.of(Dictionary.ROLE_APP_ADMIN), Dictionary.ROLE_SYSTEM_ADMIN)).isFalse();
        assertThat(RoleCapabilities.hasCapability(
                List.of(Dictionary.ROLE_USER), Dictionary.ROLE_APP_ADMIN)).isFalse();
    }

    @Test
    void unknownRoleRequiresExactMatch() {
        assertThat(RoleCapabilities.hasCapability(List.of("CUSTOM"), "CUSTOM")).isTrue();
        assertThat(RoleCapabilities.hasCapability(List.of(Dictionary.ROLE_SUPER_ADMIN), "CUSTOM")).isFalse();
    }
}
