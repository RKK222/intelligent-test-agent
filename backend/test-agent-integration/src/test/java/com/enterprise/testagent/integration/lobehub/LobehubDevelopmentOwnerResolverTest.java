package com.enterprise.testagent.integration.lobehub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.dictionary.DictId;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRole;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证本地开发初始化只能选出明确且可登录的 LobeHub owner。 */
class LobehubDevelopmentOwnerResolverTest {

    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");
    private static final DictId SUPER_ADMIN_ID = new DictId("dict_role_super_admin");

    @Test
    void selectsTheOnlyActiveDepartmentSuperAdminWhenOwnerIsUnconfigured() {
        User candidate = user("usr_owner", "AUTH_OWNER", "研发一部", UserStatus.ACTIVE);
        Fixture fixture = fixture(List.of(candidate));
        when(fixture.userRoleRepository().findByUserId(candidate.userId()))
                .thenReturn(List.of(new UserRole(candidate.userId(), SUPER_ADMIN_ID, NOW)));

        String owner = fixture.resolver().resolve("", "NOT_CONFIGURED");

        assertThat(owner).isEqualTo("AUTH_OWNER");
    }

    @Test
    void rejectsAutomaticSelectionWhenMoreThanOneEligibleOwnerExists() {
        User first = user("usr_first", "AUTH_FIRST", "研发一部", UserStatus.ACTIVE);
        User second = user("usr_second", "AUTH_SECOND", "研发二部", UserStatus.ACTIVE);
        Fixture fixture = fixture(List.of(first, second));
        when(fixture.userRoleRepository().findByUserId(first.userId()))
                .thenReturn(List.of(new UserRole(first.userId(), SUPER_ADMIN_ID, NOW)));
        when(fixture.userRoleRepository().findByUserId(second.userId()))
                .thenReturn(List.of(new UserRole(second.userId(), SUPER_ADMIN_ID, NOW)));

        assertThatThrownBy(() -> fixture.resolver().resolve("", "NOT_CONFIGURED"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("唯一");
    }

    @Test
    void honorsAnExplicitActiveOwnerWithDepartment() {
        User requested = user("usr_requested", "AUTH_REQUESTED", "质量部", UserStatus.ACTIVE);
        Fixture fixture = fixture(List.of());
        when(fixture.userRepository().findByUnifiedAuthId("AUTH_REQUESTED"))
                .thenReturn(Optional.of(requested));

        assertThat(fixture.resolver().resolve(" AUTH_REQUESTED ", "NOT_CONFIGURED"))
                .isEqualTo("AUTH_REQUESTED");
    }

    private static Fixture fixture(List<User> users) {
        UserRepository userRepository = mock(UserRepository.class);
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        Dictionary superAdmin = new Dictionary(
                SUPER_ADMIN_ID,
                "应用角色",
                Dictionary.DICT_KEY_ROLE,
                Dictionary.ROLE_SUPER_ADMIN,
                "超级管理员",
                1,
                NOW,
                NOW);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, Dictionary.ROLE_SUPER_ADMIN))
                .thenReturn(Optional.of(superAdmin));
        when(userRepository.findPage("", new PageRequest(1, PageRequest.MAX_SIZE)))
                .thenReturn(new PageResponse<>(users, 1, PageRequest.MAX_SIZE, users.size()));
        return new Fixture(
                new LobehubDevelopmentOwnerResolver(userRepository, userRoleRepository, dictionaryRepository),
                userRepository,
                userRoleRepository);
    }

    private static User user(String userId, String unifiedAuthId, String department, UserStatus status) {
        return new User(
                new UserId(userId),
                unifiedAuthId,
                "user-" + userId,
                "password-hash",
                "测试组织",
                "测试研发部",
                department,
                status,
                NOW,
                NOW);
    }

    private record Fixture(
            LobehubDevelopmentOwnerResolver resolver,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository) {
    }
}
