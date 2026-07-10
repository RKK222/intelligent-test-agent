package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.configuration.CommonParameterReloadedEvent;
import com.icbc.testagent.domain.run.ConversationContextStore;
import com.icbc.testagent.domain.workspace.ManagedWorkspacePathResolver;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 跨 Java 监听可信工作区路径参数变化，以 O(1) 全局代次使旧运行上下文失效。
 */
@Service
public class ConversationContextInvalidationListener {

    private static final Set<String> TRUSTED_PATH_PARAMETERS = Set.of(
            "SYS_DATA_ROOT_DIR",
            ManagedWorkspacePathResolver.PARAM_OPENCODE_APP_WORKSPACE_ROOT,
            ManagedWorkspacePathResolver.PARAM_OPENCODE_PERSONAL_WORKTREE_ROOT);

    private final ConversationContextStore contextStore;

    public ConversationContextInvalidationListener(ConversationContextStore contextStore) {
        this.contextStore = Objects.requireNonNull(contextStore, "contextStore must not be null");
    }

    /** 参数广播已覆盖本地与远端节点；非可信路径参数不会制造无谓 token 失效。 */
    @EventListener
    public void onCommonParameterReloaded(CommonParameterReloadedEvent event) {
        if (event != null && TRUSTED_PATH_PARAMETERS.contains(event.englishName())) {
            contextStore.invalidateAll();
        }
    }
}
