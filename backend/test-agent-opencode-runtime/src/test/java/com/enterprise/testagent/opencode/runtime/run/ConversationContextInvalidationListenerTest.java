package com.enterprise.testagent.opencode.runtime.run;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.enterprise.testagent.domain.configuration.CommonParameterReloadedEvent;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import org.junit.jupiter.api.Test;

class ConversationContextInvalidationListenerTest {

    @Test
    void invalidatesAllOnlyForTrustedWorkspacePathParameters() {
        ConversationContextStore store = mock(ConversationContextStore.class);
        ConversationContextInvalidationListener listener = new ConversationContextInvalidationListener(store);

        listener.onCommonParameterReloaded(event("SYS_DATA_ROOT_DIR"));
        listener.onCommonParameterReloaded(event("OPENCODE_APP_WORKSPACE_ROOT"));
        listener.onCommonParameterReloaded(event("OPENCODE_PERSONAL_WORKTREE_ROOT"));
        listener.onCommonParameterReloaded(event("OPENCODE_MANAGER_MAX_PROCESSES"));

        verify(store, times(3)).invalidateAll();
        verifyNoMoreInteractions(store);
    }

    private static CommonParameterReloadedEvent event(String englishName) {
        return new CommonParameterReloadedEvent(
                englishName,
                ParameterPlatform.ALL,
                "param_test",
                "trace_test",
                "instance-test");
    }
}
