package com.enterprise.testagent.system.management.auth;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.RoleCapabilities;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 高权限入口使用的实时账号与角色复核服务。 */
@Service
public class LiveRoleCapabilityService {

    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final DictionaryRepository dictionaries;

    public LiveRoleCapabilityService(
            UserRepository users,
            UserRoleRepository userRoles,
            DictionaryRepository dictionaries) {
        this.users = Objects.requireNonNull(users, "users must not be null");
        this.userRoles = Objects.requireNonNull(userRoles, "userRoles must not be null");
        this.dictionaries = Objects.requireNonNull(dictionaries, "dictionaries must not be null");
    }

    /** 复核登录主体仍有效并具备指定层级能力。 */
    public LiveActor require(AuthPrincipal principal, String requiredRole) {
        if (principal == null || principal.isExpired()) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "平台登录会话已失效");
        }
        LiveActor actor = require(principal.userId(), requiredRole);
        if (!Objects.equals(actor.user().unifiedAuthId(), principal.unifiedAuthId())) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "平台登录会话已失效");
        }
        return actor;
    }

    /** 按用户 ID 复核账号和数据库实时角色，供长连接逐次鉴权使用。 */
    public LiveActor require(UserId userId, String requiredRole) {
        User user = users.findByUserId(userId)
                .filter(User::canLogin)
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHENTICATED, "平台用户已失效"));
        List<String> roles = roleCodes(userId);
        if (!RoleCapabilities.hasCapability(roles, requiredRole)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无权限");
        }
        return new LiveActor(user, roles);
    }

    /** 返回用户当前数据库角色 code，并保持字典顺序和去重。 */
    public List<String> roleCodes(UserId userId) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        userRoles.findByUserId(userId).forEach(role -> dictionaries.findByDictId(role.dictId())
                .filter(dictionary -> Dictionary.DICT_KEY_ROLE.equals(dictionary.dictKey()))
                .map(Dictionary::dictValue)
                .ifPresent(codes::add));
        return List.copyOf(codes);
    }

    public record LiveActor(User user, List<String> roles) {
    }
}
