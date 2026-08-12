package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRuntimeTargetRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 首版本地运行时能力守卫。前端 capability 只负责展示，敏感入口仍必须在服务端按冻结目标拒绝。
 */
@Service
public class LocalRuntimeCapabilityGuard {

    private final LocalClientWorkspaceRepository workspaceRepository;
    private final SessionRuntimeTargetRepository sessionTargetRepository;

    public LocalRuntimeCapabilityGuard(
            LocalClientWorkspaceRepository workspaceRepository,
            SessionRuntimeTargetRepository sessionTargetRepository) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository);
        this.sessionTargetRepository = Objects.requireNonNull(sessionTargetRepository);
    }

    /** 本地工作区首版不允许通过浏览器入口管理指定能力。 */
    public void requireWorkspaceSupported(WorkspaceId workspaceId, String capability, String message) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        if (workspaceRepository.findByWorkspaceId(workspaceId).isPresent()) {
            throw unsupported(capability, message, Map.of("workspaceId", workspaceId.value()));
        }
    }

    /** 会话目标一旦冻结为本地客户端，即使客户端离线也禁止退回服务端能力。 */
    public void requireSessionSupported(SessionId sessionId, String capability, String message) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        boolean local = sessionTargetRepository.findBySessionId(sessionId)
                .map(target -> target.runtimeKind() == RuntimeKind.LOCAL_CLIENT)
                .orElse(false);
        if (local) {
            throw unsupported(capability, message, Map.of("sessionId", sessionId.value()));
        }
    }

    private PlatformException unsupported(String capability, String message, Map<String, Object> details) {
        return new PlatformException(
                ErrorCode.FORBIDDEN,
                message,
                Map.of(
                        "runtimeKind", RuntimeKind.LOCAL_CLIENT.name(),
                        "capability", capability,
                        "target", details));
    }
}
