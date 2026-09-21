package com.enterprise.testagent.api.web.platform;

/** 系统管理员团队管理 HTTP 请求 DTO。 */
public final class SystemAdminTeamDtos {

    private SystemAdminTeamDtos() {
    }

    public record AddMemberRequest(String memberUserId) {
        public AddMemberRequest {
            if (memberUserId == null || memberUserId.isBlank()) {
                throw new IllegalArgumentException("memberUserId 不能为空");
            }
            memberUserId = memberUserId.trim();
        }
    }
}
