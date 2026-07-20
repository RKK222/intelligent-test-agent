package com.enterprise.testagent.xxljob.admin;

import com.xxl.job.admin.framework.constant.Consts;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.sso.core.store.LoginStore;
import com.xxl.tool.response.Response;
import com.enterprise.testagent.xxljob.XxlJobAdminBridge;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/** XXL SSO store：每次请求查询用户并复核平台 Redis session digest marker。 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformXxlLoginStore implements LoginStore {

    private final PlatformXxlJobUserMapper mapper;
    private final XxlJobAdminBridge bridge;
    private final Clock clock;

    @Autowired
    public PlatformXxlLoginStore(PlatformXxlJobUserMapper mapper, XxlJobAdminBridge bridge) {
        this(mapper, bridge, Clock.systemUTC());
    }

    PlatformXxlLoginStore(PlatformXxlJobUserMapper mapper, XxlJobAdminBridge bridge, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.bridge = Objects.requireNonNull(bridge, "bridge must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Response<String> set(LoginInfo loginInfo) {
        int id = parseId(loginInfo == null ? null : loginInfo.getUserId());
        if (id < 1 || loginInfo.getSignature() == null || loginInfo.getSignature().isBlank()) {
            return Response.ofFail("login info invalid");
        }
        return mapper.updateToken(id, loginInfo.getSignature()) > 0
                ? Response.ofSuccess()
                : Response.ofFail("token set failed");
    }

    @Override
    public Response<String> update(LoginInfo loginInfo) {
        return Response.ofFail("not supported");
    }

    @Override
    public Response<String> remove(String userId) {
        int id = parseId(userId);
        return id > 0 && mapper.updateToken(id, "") > 0
                ? Response.ofSuccess()
                : Response.ofFail("token remove failed");
    }

    @Override
    public Response<LoginInfo> get(String userId) {
        int id = parseId(userId);
        PlatformXxlJobUser user = id > 0 ? mapper.findById(id) : null;
        if (user == null
                || user.token() == null
                || user.token().isBlank()
                || user.sessionDigest() == null
                || user.sessionExpiresAt() == null
                || !user.sessionExpiresAt().isAfter(clock.instant())
                || !bridge.isPlatformSessionActive(user.sessionDigest())) {
            return Response.ofFail("platform session invalid");
        }
        LoginInfo loginInfo = new LoginInfo(String.valueOf(user.id()), user.token());
        loginInfo.setUserName(user.username());
        loginInfo.setRoleList(user.role() == 1 ? List.of(Consts.ADMIN_ROLE) : List.of());
        loginInfo.setExtraInfo(Map.of("jobGroups", user.permission() == null ? "" : user.permission()));
        return Response.ofSuccess(loginInfo);
    }

    private int parseId(String userId) {
        try {
            return Integer.parseInt(userId);
        } catch (NumberFormatException | NullPointerException exception) {
            return -1;
        }
    }
}
