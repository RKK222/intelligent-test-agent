package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManagedWorkspaceGitPathPolicyTest {

    @Test
    void normalUserCannotWriteNormalizedApplicationConfigPath() {
        assertThatThrownBy(() -> ManagedWorkspaceGitPathPolicy.requireWriteAccess(
                List.of("docs/../.opencode/tools/workspace-git.ts"),
                List.of(Dictionary.ROLE_USER)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void superAdminInheritsApplicationAdminAccess() {
        ManagedWorkspaceGitPathPolicy.requireWriteAccess(
                List.of(".opencode/agents/review.md"),
                List.of(Dictionary.ROLE_SUPER_ADMIN));
    }
}
