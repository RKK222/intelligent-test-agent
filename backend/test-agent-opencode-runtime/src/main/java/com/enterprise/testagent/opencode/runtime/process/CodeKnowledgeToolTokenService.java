package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.user.UserId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 为代码知识与源码只读 Tool 签发独立 audience 的用户凭据。 */
@Service
public class CodeKnowledgeToolTokenService {

    public static final String TOKEN_ENV_NAME = "TEST_AGENT_CODE_KNOWLEDGE_TOOL_TOKEN";
    public static final String CODE_KNOWLEDGE_ENDPOINT_PATH =
            "/api/internal/agent/opencode/code-knowledge-tool";
    public static final String CODE_SOURCE_ENDPOINT_PATH =
            "/api/internal/agent/opencode/code-source-tool";
    private static final String AUDIENCE = "code-knowledge-read";

    private final WorkspaceGitToolTokenService tokens;

    public CodeKnowledgeToolTokenService(WorkspaceGitToolTokenService tokens) {
        this.tokens = Objects.requireNonNull(tokens);
    }

    public String issue(UserId userId) {
        try {
            return tokens.issueForAudience(userId, AUDIENCE);
        } catch (PlatformException exception) {
            if (exception.errorCode() == ErrorCode.OPENCODE_UNAVAILABLE) {
                throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "代码知识只读 Tool 尚未配置");
            }
            throw exception;
        }
    }

    public Principal authenticate(String authorization) {
        try {
            var principal = tokens.authenticateForAudience(authorization, AUDIENCE);
            return new Principal(principal.userId(), principal.roles());
        } catch (PlatformException exception) {
            if (exception.errorCode() == ErrorCode.UNAUTHENTICATED) {
                throw new PlatformException(ErrorCode.UNAUTHENTICATED, "代码知识只读 Tool 凭据无效或已过期");
            }
            throw exception;
        }
    }

    /** 专用凭据恢复出的只读 Tool 身份。 */
    public record Principal(UserId userId, List<String> roles) {
        public Principal {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }
}
