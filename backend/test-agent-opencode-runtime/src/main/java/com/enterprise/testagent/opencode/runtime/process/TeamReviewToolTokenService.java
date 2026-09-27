package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.user.UserId;
import org.springframework.stereotype.Service;

/** 审阅 Tool 独立 audience；不能替代登录 Token 或其它工具凭据。 */
@Service
public class TeamReviewToolTokenService {
    public static final String TOKEN_ENV_NAME = "TEST_AGENT_TEAM_REVIEW_TOOL_TOKEN";
    public static final String TICKET_PATH = "/api/internal/agent/opencode/team-review-tool/ticket";
    private static final String AUDIENCE = "team-review-read";
    private final WorkspaceGitToolTokenService tokens;
    public TeamReviewToolTokenService(WorkspaceGitToolTokenService tokens) { this.tokens = tokens; }
    public String issue(UserId userId) { return tokens.issueForAudience(userId, AUDIENCE); }
    public UserId authenticate(String authorization) {
        return tokens.authenticateForAudience(authorization, AUDIENCE).userId();
    }
}
