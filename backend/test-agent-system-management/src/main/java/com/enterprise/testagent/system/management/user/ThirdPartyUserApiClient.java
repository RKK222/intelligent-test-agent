package com.enterprise.testagent.system.management.user;

import com.enterprise.testagent.domain.tcds.TcdsGateway;
import java.util.Optional;

/**
 * 旧用户管理调用面的兼容门面。HTTP 已统一收敛到 integration 的 {@link TcdsGateway} 实现。
 */
public class ThirdPartyUserApiClient {

    private final TcdsGateway tcdsGateway;

    public ThirdPartyUserApiClient(TcdsGateway tcdsGateway) {
        this.tcdsGateway = tcdsGateway;
    }

    /**
     * 调用第三方用户信息接口。
     *
     * <p>请求失败时返回空 Optional 的语义由共享 TCDS 网关保持，调用方继续决定是否降级。
     */
    public Optional<UserManagementResponses.ThirdPartyUserInfoResponse> getUserByLoginName(String userId) {
        return tcdsGateway.findUser(userId).map(profile ->
                new UserManagementResponses.ThirdPartyUserInfoResponse(
                        profile.fullName(),
                        profile.loginName(),
                        profile.rdDepartment(),
                        profile.department()));
    }
}
