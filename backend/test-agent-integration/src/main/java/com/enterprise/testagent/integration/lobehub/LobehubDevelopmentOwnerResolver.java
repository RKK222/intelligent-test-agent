package com.enterprise.testagent.integration.lobehub;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.dictionary.DictId;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 为显式本地开发模式解析唯一 LobeHub owner。 */
@Service
public class LobehubDevelopmentOwnerResolver {

    private static final String OWNER_PLACEHOLDER = "NOT_CONFIGURED";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final DictionaryRepository dictionaryRepository;

    public LobehubDevelopmentOwnerResolver(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null");
        this.dictionaryRepository = Objects.requireNonNull(
                dictionaryRepository, "dictionaryRepository must not be null");
    }

    /**
     * 返回开发环境 owner 的统一认证号。显式指定值优先，其次保留已配置 owner；
     * 仅在仍为占位值时，
     * 才从状态正常、部门非空的超级管理员中自动选择，并要求候选唯一。
     */
    public String resolve(String requestedOwner, String configuredOwner) {
        String requested = normalize(requestedOwner);
        if (requested != null) {
            return requireEligibleUser(requested).unifiedAuthId();
        }
        String configured = normalize(configuredOwner);
        if (configured != null && !OWNER_PLACEHOLDER.equalsIgnoreCase(configured)) {
            return requireEligibleUser(configured).unifiedAuthId();
        }

        DictId superAdminRole = dictionaryRepository
                .findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, Dictionary.ROLE_SUPER_ADMIN)
                .map(Dictionary::dictId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "LobeHub 本地开发 owner 无法解析：超级管理员角色不存在"));
        List<User> candidates = findAllUsers().stream()
                .filter(User::canLogin)
                .filter(this::hasDepartment)
                .filter(user -> userRoleRepository.findByUserId(user.userId()).stream()
                        .anyMatch(role -> superAdminRole.equals(role.dictId())))
                .toList();
        if (candidates.size() != 1) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "LobeHub 本地开发 owner 无法唯一确定，"
                            + "请设置 TEST_AGENT_LOBEHUB_DEV_OWNER_UNIFIED_AUTH_ID");
        }
        return candidates.getFirst().unifiedAuthId();
    }

    private User requireEligibleUser(String unifiedAuthId) {
        User user = userRepository.findByUnifiedAuthId(unifiedAuthId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "LobeHub 本地开发 owner 对应的平台用户不存在"));
        if (!user.canLogin() || !hasDepartment(user)) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "LobeHub 本地开发 owner 必须是状态正常且部门非空的平台用户");
        }
        return user;
    }

    private List<User> findAllUsers() {
        List<User> users = new ArrayList<>();
        int page = 1;
        while (true) {
            PageResponse<User> response = userRepository.findPage("", new PageRequest(page, PageRequest.MAX_SIZE));
            users.addAll(response.items());
            if (page >= response.totalPages()) {
                return List.copyOf(users);
            }
            page++;
        }
    }

    private boolean hasDepartment(User user) {
        try {
            LobehubSsoApplicationService.normalizeDepartment(user.department());
            return true;
        } catch (PlatformException ignored) {
            return false;
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
