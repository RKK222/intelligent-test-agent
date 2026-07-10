package com.icbc.testagent.domain.run;

import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.util.Objects;

/**
 * Workspace 可信绑定变更窗口的 Redis gate 凭证；完成或回滚时只能释放当前操作写入的成员。
 */
public record ConversationContextWorkspaceMutation(WorkspaceId workspaceId, String mutationToken) {

    public ConversationContextWorkspaceMutation {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        if (mutationToken == null || mutationToken.isBlank()) {
            throw new IllegalArgumentException("mutationToken must not be blank");
        }
        mutationToken = mutationToken.trim();
    }
}
