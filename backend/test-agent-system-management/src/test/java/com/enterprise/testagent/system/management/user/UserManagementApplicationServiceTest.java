package com.enterprise.testagent.system.management.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
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
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextUserMutation;
import com.enterprise.testagent.system.management.user.UserManagementResponses.CreateUserCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.RoleOption;
import com.enterprise.testagent.system.management.user.UserManagementResponses.UpdateUserRoleCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.UserResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

class UserManagementApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-26T00:00:00Z");
    private static final DictId APP_ADMIN_DICT_ID = new DictId("dict_role_app_admin");
    private static final DictId USER_DICT_ID = new DictId("dict_role_user");

    @Test
    void listUsersReturnsUsersWithoutPasswordHashAndWithRoles() {
        UserRepository userRepository = mock(UserRepository.class);
        User user = User.createNew(
                "usr_1234567890abcdef", "AUTH_1", "alice",
                "$2a$10$hashedvalue", "企业", "研发部", "测试部");
        when(userRepository.findPage(eq("ali"), any(PageRequest.class)))
                .thenReturn(new PageResponse<>(List.of(user), 1, 50, 1));

        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        when(userRoleRepository.findByUserId(user.userId()))
                .thenReturn(List.of(UserRole.create(user.userId(), APP_ADMIN_DICT_ID)));

        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictId(APP_ADMIN_DICT_ID))
                .thenReturn(Optional.of(roleDictionary(APP_ADMIN_DICT_ID, "APP_ADMIN", "应用管理员", 3)));

        UserManagementApplicationService service = new UserManagementApplicationService(
                new UserDomainService(userRepository), userRepository, userRoleRepository, dictionaryRepository);
        PageResponse<UserResponse> page = service.listUsers("ali", new PageRequest(1, 50));

        assertThat(page.items()).hasSize(1);
        UserResponse response = page.items().get(0);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.roles()).containsExactly("APP_ADMIN");
        assertThat(response.roleLabels()).containsExactly("应用管理员");
        assertThat(response.status()).isEqualTo("ACTIVE");
        // UserResponse 不含密码字段，确保不会泄露 passwordHash
        assertThat(response.getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("passwordHash");
    }

    @Test
    void createUserUsesDefaultPasswordAndGrantsRole() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByUnifiedAuthId("AUTH_2")).thenReturn(false);
        // registerUser 内部 save 后，服务会按 userId 重新读取以带回角色
        when(userRepository.findByUserId(argThat(id -> id != null && id.value().startsWith("usr_"))))
                .thenAnswer(invocation -> {
                    UserId id = invocation.getArgument(0);
                    return Optional.of(User.createNew(
                            id.value(), "AUTH_2", "bob",
                            "$2a$10$hashedvalue", null, null, null));
                });

        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, "APP_ADMIN"))
                .thenReturn(Optional.of(roleDictionary(APP_ADMIN_DICT_ID, "APP_ADMIN", "应用管理员", 3)));
        when(dictionaryRepository.findByDictId(APP_ADMIN_DICT_ID))
                .thenReturn(Optional.of(roleDictionary(APP_ADMIN_DICT_ID, "APP_ADMIN", "应用管理员", 3)));
        when(userRoleRepository.findByUserId(any()))
                .thenReturn(List.of(UserRole.create(new UserId("usr_reloaded"), APP_ADMIN_DICT_ID)));

        UserDomainService userDomainService = new UserDomainService(userRepository);
        UserManagementApplicationService service = new UserManagementApplicationService(
                userDomainService, userRepository, userRoleRepository, dictionaryRepository);

        UserResponse response = service.createUser(new CreateUserCommand(
                "AUTH_2", "bob", null, null, null, "APP_ADMIN"));

        assertThat(response.username()).isEqualTo("bob");
        assertThat(response.roles()).containsExactly("APP_ADMIN");
        assertThat(response.roleLabels()).containsExactly("应用管理员");

        // 捕获保存的用户，校验密码为默认值 123456 的 BCrypt 哈希，且可被真实 BCrypt 校验通过
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(userDomainService.verifyPassword(savedUser.getValue(), UserManagementApplicationService.DEFAULT_PASSWORD))
                .isTrue();
        // 校验已授予选定角色
        verify(userRoleRepository).save(argThat((UserRole role) ->
                role.userId().equals(savedUser.getValue().userId())
                        && role.dictId().equals(APP_ADMIN_DICT_ID)));
    }

    @Test
    void createUserRejectsInvalidRole() {
        UserRepository userRepository = mock(UserRepository.class);
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, "GHOST"))
                .thenReturn(Optional.empty());

        UserManagementApplicationService service = new UserManagementApplicationService(
                new UserDomainService(userRepository), userRepository, mock(UserRoleRepository.class), dictionaryRepository);

        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
                "AUTH_3", "casper", null, null, null, "GHOST")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void updateUserRoleReplacesExistingRolesAndReturnsUpdatedUser() {
        UserRepository userRepository = mock(UserRepository.class);
        User user = User.createNew(
                "usr_1234567890abcdef", "AUTH_1", "alice",
                "$2a$10$hashedvalue", "企业", "研发部", "测试部");
        when(userRepository.findByUserId(user.userId())).thenReturn(Optional.of(user));

        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        UserRole oldRole = UserRole.create(user.userId(), APP_ADMIN_DICT_ID);
        when(userRoleRepository.findByUserId(user.userId()))
                .thenReturn(List.of(oldRole))
                .thenReturn(List.of(UserRole.create(user.userId(), USER_DICT_ID)));

        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, "USER"))
                .thenReturn(Optional.of(roleDictionary(USER_DICT_ID, "USER", "普通用户", 4)));
        when(dictionaryRepository.findByDictId(USER_DICT_ID))
                .thenReturn(Optional.of(roleDictionary(USER_DICT_ID, "USER", "普通用户", 4)));

        UserManagementApplicationService service = new UserManagementApplicationService(
                new UserDomainService(userRepository), userRepository, userRoleRepository, dictionaryRepository);
        ConversationContextStore contextStore = mock(ConversationContextStore.class);
        ConversationContextUserMutation mutation =
                new ConversationContextUserMutation(user.userId(), "mutation-role");
        when(contextStore.beginUserMutation(user.userId())).thenReturn(mutation);
        service.setConversationContextStore(contextStore);

        UserResponse response = service.updateUserRole(
                new UpdateUserRoleCommand(user.userId().value(), "USER"));

        assertThat(response.roles()).containsExactly("USER");
        assertThat(response.roleLabels()).containsExactly("普通用户");
        verify(userRoleRepository).delete(oldRole);
        verify(userRoleRepository).save(argThat((UserRole role) ->
                role.userId().equals(user.userId()) && role.dictId().equals(USER_DICT_ID)));
        verify(contextStore).beginUserMutation(user.userId());
        verify(contextStore).completeUserMutation(mutation);
    }

    @Test
    void updateUserRoleRejectsMissingUser() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUserId(new UserId("usr_missing"))).thenReturn(Optional.empty());
        UserManagementApplicationService service = new UserManagementApplicationService(
                new UserDomainService(userRepository), userRepository,
                mock(UserRoleRepository.class), mock(DictionaryRepository.class));

        assertThatThrownBy(() -> service.updateUserRole(new UpdateUserRoleCommand("usr_missing", "USER")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void createUserPropagatesConflictOnDuplicateUsername() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, "USER"))
                .thenReturn(Optional.of(roleDictionary(USER_DICT_ID, "USER", "普通用户", 4)));

        UserManagementApplicationService service = new UserManagementApplicationService(
                new UserDomainService(userRepository), userRepository, mock(UserRoleRepository.class), dictionaryRepository);

        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
                "AUTH_4", "alice", null, null, null, "USER")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void createUserHasTransactionBoundaryToAvoidOrphanUserWhenRoleGrantFails() throws NoSuchMethodException {
        Transactional transactional = UserManagementApplicationService.class
                .getMethod("createUser", CreateUserCommand.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional)
                .as("创建用户包含 users 与 user_roles 两次写入，必须同事务提交或回滚")
                .isNotNull();
    }

    @Test
    void listRolesReturnsRoleOptionsSortedBySortOrder() {
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKey(Dictionary.DICT_KEY_ROLE)).thenReturn(List.of(
                roleDictionary(USER_DICT_ID, "USER", "普通用户", 4),
                roleDictionary(new DictId("dict_role_super_admin"), "SUPER_ADMIN", "超级管理员", 1),
                roleDictionary(APP_ADMIN_DICT_ID, "APP_ADMIN", "应用管理员", 3),
                roleDictionary(new DictId("dict_role_system_admin"), "SYSTEM_ADMIN", "系统管理员", 2)));

        UserManagementApplicationService service = new UserManagementApplicationService(
                new UserDomainService(mock(UserRepository.class)), mock(UserRepository.class),
                mock(UserRoleRepository.class), dictionaryRepository);

        List<RoleOption> roles = service.listRoles();

        assertThat(roles).extracting(RoleOption::roleCode)
                .containsExactly("SUPER_ADMIN", "SYSTEM_ADMIN", "APP_ADMIN", "USER");
        assertThat(roles).extracting(RoleOption::roleLabel)
                .containsExactly("超级管理员", "系统管理员", "应用管理员", "普通用户");
    }

    private Dictionary roleDictionary(DictId dictId, String value, String label, int sortOrder) {
        return new Dictionary(dictId, "角色", Dictionary.DICT_KEY_ROLE, value, label, sortOrder, NOW, NOW);
    }
}
