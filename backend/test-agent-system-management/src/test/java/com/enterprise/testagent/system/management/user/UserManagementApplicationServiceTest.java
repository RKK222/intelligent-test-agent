package com.enterprise.testagent.system.management.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.TokenStore;
import com.enterprise.testagent.domain.dictionary.DictId;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRole;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserDeletionRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserManagementQuery;
import com.enterprise.testagent.domain.user.UserManagementQueryRepository;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextUserMutation;
import com.enterprise.testagent.system.management.user.UserManagementResponses.CreateUserCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.DeleteUsersCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.RoleOption;
import com.enterprise.testagent.system.management.user.UserManagementResponses.SyncUsersFromTcdsCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.UpdateUsernameCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.UpdateUserRoleCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.UpdateUserRolesCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.UserRoleAssignment;
import com.enterprise.testagent.system.management.user.UserManagementResponses.UserResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
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
        UserManagementQueryRepository queryRepository = mock(UserManagementQueryRepository.class);
        User user = User.createNew(
                "usr_1234567890abcdef", "AUTH_1", "alice",
                "$2a$10$hashedvalue", "企业", "研发部", "测试部");
        UserManagementQuery query = new UserManagementQuery("ali", "APP_ADMIN", "企业", "研发", "测试");
        when(queryRepository.findPage(eq(query), any(PageRequest.class)))
                .thenReturn(new PageResponse<>(List.of(user), 1, 50, 1));

        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        when(userRoleRepository.findByUserId(user.userId()))
                .thenReturn(List.of(UserRole.create(user.userId(), APP_ADMIN_DICT_ID)));

        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictId(APP_ADMIN_DICT_ID))
                .thenReturn(Optional.of(roleDictionary(APP_ADMIN_DICT_ID, "APP_ADMIN", "应用管理员", 3)));

        UserManagementApplicationService service = service(
                userDomainService(userRepository),
                userRepository,
                queryRepository,
                mock(UserDeletionRepository.class),
                userRoleRepository,
                dictionaryRepository,
                mock(TokenStore.class),
                mock(ThirdPartyUserApiClient.class));
        PageResponse<UserResponse> page = service.listUsers(query, new PageRequest(1, 50));

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

        UserDomainService userDomainService = userDomainService(userRepository);
        UserManagementApplicationService service = service(
                userDomainService,
                userRepository,
                mock(UserManagementQueryRepository.class),
                mock(UserDeletionRepository.class),
                userRoleRepository,
                dictionaryRepository,
                mock(TokenStore.class),
                mock(ThirdPartyUserApiClient.class));

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

        UserManagementApplicationService service = service(
                userRepository, mock(UserRoleRepository.class), dictionaryRepository);

        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
                "AUTH_3", "casper", null, null, null, "GHOST")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void updateUsernamePreservesIdentityAndAuthorizationWithoutRevokingTokens() {
        UserRepository userRepository = mock(UserRepository.class);
        User current = User.createNew(
                "usr_1234567890abcdef", "AUTH_1", "alice",
                "$2a$10$hashedvalue", "企业", "研发部", "测试部");
        when(userRepository.findByUserId(current.userId())).thenReturn(Optional.of(current));
        when(userRepository.existsByUsername("Alice Updated")).thenReturn(false);
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        when(userRoleRepository.findByUserId(current.userId()))
                .thenReturn(List.of(UserRole.create(current.userId(), USER_DICT_ID)));
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictId(USER_DICT_ID))
                .thenReturn(Optional.of(roleDictionary(USER_DICT_ID, "USER", "普通用户", 4)));
        TokenStore tokenStore = mock(TokenStore.class);
        UserManagementApplicationService service = service(
                userDomainService(userRepository),
                userRepository,
                mock(UserManagementQueryRepository.class),
                mock(UserDeletionRepository.class),
                userRoleRepository,
                dictionaryRepository,
                tokenStore,
                mock(ThirdPartyUserApiClient.class));

        UserResponse response = service.updateUsername(
                new UpdateUsernameCommand(current.userId().value(), "  Alice Updated  "));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue())
                .returns(current.userId(), User::userId)
                .returns(current.unifiedAuthId(), User::unifiedAuthId)
                .returns("Alice Updated", User::username)
                .returns(current.organization(), User::organization)
                .returns(current.rdDepartment(), User::rdDepartment)
                .returns(current.department(), User::department)
                .returns(current.passwordHash(), User::passwordHash)
                .returns(current.status(), User::status);
        assertThat(response.username()).isEqualTo("Alice Updated");
        assertThat(response.unifiedAuthId()).isEqualTo("AUTH_1");
        assertThat(response.roles()).containsExactly("USER");
        verify(tokenStore, never()).deleteByUserIds(any());
    }

    @Test
    void updateUsernameRejectsDuplicateNameWithoutWriting() {
        UserRepository userRepository = mock(UserRepository.class);
        User current = User.createNew(
                "usr_1234567890abcdef", "AUTH_1", "alice", "hash", null, null, null);
        when(userRepository.findByUserId(current.userId())).thenReturn(Optional.of(current));
        when(userRepository.existsByUsername("bob")).thenReturn(true);
        UserManagementApplicationService service = service(
                userRepository, mock(UserRoleRepository.class), mock(DictionaryRepository.class));

        assertThatThrownBy(() -> service.updateUsername(
                new UpdateUsernameCommand(current.userId().value(), "bob")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(userRepository, never()).save(any());
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

        TokenStore tokenStore = mock(TokenStore.class);
        UserManagementApplicationService service = service(
                userDomainService(userRepository, mock(ThirdPartyUserApiClient.class)),
                userRepository,
                mock(UserManagementQueryRepository.class),
                mock(UserDeletionRepository.class),
                userRoleRepository,
                dictionaryRepository,
                tokenStore,
                mock(ThirdPartyUserApiClient.class));
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
        verify(tokenStore, times(2)).deleteByUserIds(List.of(user.userId()));
    }

    @Test
    void updateUserRoleRejectsMissingUser() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUserId(new UserId("usr_missing"))).thenReturn(Optional.empty());
        UserManagementApplicationService service = service(
                userRepository, mock(UserRoleRepository.class), mock(DictionaryRepository.class));

        assertThatThrownBy(() -> service.updateUserRole(new UpdateUserRoleCommand("usr_missing", "USER")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void batchRoleUpdateUsesOneTokenRevocationBatchForDifferentTargetRoles() {
        UserRepository userRepository = mock(UserRepository.class);
        User alice = User.createNew(
                "usr_alice", "AUTH_A", "alice", "hash", "企业", "研发部", "测试部");
        User bob = User.createNew(
                "usr_bob", "AUTH_B", "bob", "hash", "企业", "研发部", "测试部");
        when(userRepository.findByUserId(alice.userId())).thenReturn(Optional.of(alice));
        when(userRepository.findByUserId(bob.userId())).thenReturn(Optional.of(bob));

        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        when(userRoleRepository.findByUserId(alice.userId()))
                .thenReturn(List.of(UserRole.create(alice.userId(), APP_ADMIN_DICT_ID)));
        when(userRoleRepository.findByUserId(bob.userId()))
                .thenReturn(List.of(UserRole.create(bob.userId(), USER_DICT_ID)));
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, "USER"))
                .thenReturn(Optional.of(roleDictionary(USER_DICT_ID, "USER", "普通用户", 4)));
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, "APP_ADMIN"))
                .thenReturn(Optional.of(roleDictionary(APP_ADMIN_DICT_ID, "APP_ADMIN", "应用管理员", 3)));
        TokenStore tokenStore = mock(TokenStore.class);
        ConversationContextStore contextStore = mock(ConversationContextStore.class);
        when(contextStore.beginUserMutation(any(UserId.class))).thenAnswer(invocation -> {
            UserId userId = invocation.getArgument(0);
            return new ConversationContextUserMutation(userId, "role-" + userId.value());
        });
        UserManagementApplicationService service = service(
                userDomainService(userRepository),
                userRepository,
                mock(UserManagementQueryRepository.class),
                mock(UserDeletionRepository.class),
                userRoleRepository,
                dictionaryRepository,
                tokenStore,
                mock(ThirdPartyUserApiClient.class));
        service.setConversationContextStore(contextStore);

        var response = service.updateUserRoles(new UpdateUserRolesCommand(
                "usr_admin",
                List.of(
                        new UserRoleAssignment(alice.userId().value(), "USER"),
                        new UserRoleAssignment(bob.userId().value(), "APP_ADMIN")),
                false,
                null,
                null));

        List<UserId> targets = List.of(alice.userId(), bob.userId());
        assertThat(response.updatedCount()).isEqualTo(2);
        verify(tokenStore, times(2)).deleteByUserIds(targets);
        verify(contextStore).completeUserMutation(
                new ConversationContextUserMutation(alice.userId(), "role-usr_alice"));
        verify(contextStore).completeUserMutation(
                new ConversationContextUserMutation(bob.userId(), "role-usr_bob"));
        verify(userRoleRepository).save(argThat(role ->
                role.userId().equals(alice.userId()) && role.dictId().equals(USER_DICT_ID)));
        verify(userRoleRepository).save(argThat(role ->
                role.userId().equals(bob.userId()) && role.dictId().equals(APP_ADMIN_DICT_ID)));
    }

    @Test
    void batchRoleUpdateResolvesAllFilteredUsersAndExcludesOperator() {
        UserRepository userRepository = mock(UserRepository.class);
        User target = User.createNew(
                "usr_target", "AUTH_T", "target", "hash", "企业", "研发部", "测试部");
        when(userRepository.findByUserId(target.userId())).thenReturn(Optional.of(target));
        UserManagementQuery query = new UserManagementQuery(
                null, UserManagementQuery.ROLE_UNASSIGNED, "企业", null, null);
        UserManagementQueryRepository queryRepository = mock(UserManagementQueryRepository.class);
        UserId operator = new UserId("usr_admin");
        when(queryRepository.findUserIds(
                query,
                operator,
                UserManagementApplicationService.MAX_ROLE_BATCH_SIZE + 1))
                .thenReturn(List.of(target.userId()));
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, "USER"))
                .thenReturn(Optional.of(roleDictionary(USER_DICT_ID, "USER", "普通用户", 4)));
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        UserManagementApplicationService service = service(
                userDomainService(userRepository),
                userRepository,
                queryRepository,
                mock(UserDeletionRepository.class),
                userRoleRepository,
                dictionaryRepository,
                mock(TokenStore.class),
                mock(ThirdPartyUserApiClient.class));

        var response = service.updateUserRoles(new UpdateUserRolesCommand(
                operator.value(), List.of(), true, "USER", query));

        assertThat(response.updatedCount()).isEqualTo(1);
        verify(queryRepository).findUserIds(
                query,
                operator,
                UserManagementApplicationService.MAX_ROLE_BATCH_SIZE + 1);
        verify(userRoleRepository).save(argThat(role -> role.userId().equals(target.userId())));
    }

    @Test
    void batchRoleUpdateRejectsExplicitOperatorTarget() {
        TokenStore tokenStore = mock(TokenStore.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserManagementApplicationService service = service(
                userDomainService(userRepository),
                userRepository,
                mock(UserManagementQueryRepository.class),
                mock(UserDeletionRepository.class),
                mock(UserRoleRepository.class),
                mock(DictionaryRepository.class),
                tokenStore,
                mock(ThirdPartyUserApiClient.class));

        assertThatThrownBy(() -> service.updateUserRoles(new UpdateUserRolesCommand(
                "usr_admin",
                List.of(new UserRoleAssignment("usr_admin", "USER")),
                false,
                null,
                null)))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(tokenStore, never()).deleteByUserIds(any());
    }

    @Test
    void batchRoleUpdateRejectsMoreThanMaximumFilteredUsers() {
        UserManagementQuery query = new UserManagementQuery(null, null, null, null, null);
        UserManagementQueryRepository queryRepository = mock(UserManagementQueryRepository.class);
        List<UserId> tooManyUserIds = IntStream
                .rangeClosed(1, UserManagementApplicationService.MAX_ROLE_BATCH_SIZE + 1)
                .mapToObj(index -> new UserId("usr_" + index))
                .toList();
        when(queryRepository.findUserIds(
                query,
                new UserId("usr_admin"),
                UserManagementApplicationService.MAX_ROLE_BATCH_SIZE + 1))
                .thenReturn(tooManyUserIds);
        TokenStore tokenStore = mock(TokenStore.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserManagementApplicationService service = service(
                userDomainService(userRepository),
                userRepository,
                queryRepository,
                mock(UserDeletionRepository.class),
                mock(UserRoleRepository.class),
                mock(DictionaryRepository.class),
                tokenStore,
                mock(ThirdPartyUserApiClient.class));

        assertThatThrownBy(() -> service.updateUserRoles(new UpdateUserRolesCommand(
                "usr_admin", List.of(), true, "USER", query)))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(tokenStore, never()).deleteByUserIds(any());
    }

    @Test
    void createUserPropagatesConflictOnDuplicateUsername() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, "USER"))
                .thenReturn(Optional.of(roleDictionary(USER_DICT_ID, "USER", "普通用户", 4)));

        UserManagementApplicationService service = service(
                userRepository, mock(UserRoleRepository.class), dictionaryRepository);

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
    void deleteUsersCleansDistinctTargetsAndRevokesRuntimeState() {
        UserRepository userRepository = mock(UserRepository.class);
        UserDeletionRepository deletionRepository = mock(UserDeletionRepository.class);
        TokenStore tokenStore = mock(TokenStore.class);
        ThirdPartyUserApiClient thirdPartyUserApiClient = mock(ThirdPartyUserApiClient.class);
        List<UserId> targets = List.of(new UserId("usr_a"), new UserId("usr_b"));
        when(deletionRepository.lockExistingUserIds(targets)).thenReturn(targets);
        when(deletionRepository.findDeletionBlockedUserIds(targets)).thenReturn(List.of());
        when(deletionRepository.deleteUsers(targets)).thenReturn(2);

        ConversationContextStore contextStore = mock(ConversationContextStore.class);
        when(contextStore.beginUserMutation(any(UserId.class))).thenAnswer(invocation -> {
            UserId userId = invocation.getArgument(0);
            return new ConversationContextUserMutation(userId, "delete-" + userId.value());
        });
        UserManagementApplicationService service = service(
                userDomainService(userRepository, thirdPartyUserApiClient),
                userRepository,
                mock(UserManagementQueryRepository.class),
                deletionRepository,
                mock(UserRoleRepository.class),
                mock(DictionaryRepository.class),
                tokenStore,
                thirdPartyUserApiClient);
        service.setConversationContextStore(contextStore);

        var response = service.deleteUsers(new DeleteUsersCommand(
                "usr_admin",
                List.of("usr_a", "usr_a", " usr_b ")));

        assertThat(response.deletedUserIds()).containsExactly("usr_a", "usr_b");
        assertThat(response.deletedCount()).isEqualTo(2);
        verify(deletionRepository).deleteUsers(targets);
        verify(tokenStore, times(2)).deleteByUserIds(targets);
        verify(contextStore).completeUserMutation(new ConversationContextUserMutation(targets.get(0), "delete-usr_a"));
        verify(contextStore).completeUserMutation(new ConversationContextUserMutation(targets.get(1), "delete-usr_b"));
    }

    @Test
    void deleteUsersRejectsCurrentLoginUser() {
        UserDeletionRepository deletionRepository = mock(UserDeletionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ThirdPartyUserApiClient thirdPartyUserApiClient = mock(ThirdPartyUserApiClient.class);
        UserManagementApplicationService service = service(
                userDomainService(userRepository, thirdPartyUserApiClient),
                userRepository,
                mock(UserManagementQueryRepository.class),
                deletionRepository,
                mock(UserRoleRepository.class),
                mock(DictionaryRepository.class),
                mock(TokenStore.class),
                thirdPartyUserApiClient);

        assertThatThrownBy(() -> service.deleteUsers(new DeleteUsersCommand(
                "usr_admin", List.of("usr_target", "usr_admin"))))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(deletionRepository, never()).deleteUsers(any());
    }

    @Test
    void deleteUsersRejectsWholeBatchWhenBusinessReferencesExist() {
        UserDeletionRepository deletionRepository = mock(UserDeletionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ThirdPartyUserApiClient thirdPartyUserApiClient = mock(ThirdPartyUserApiClient.class);
        List<UserId> targets = List.of(new UserId("usr_a"), new UserId("usr_b"));
        when(deletionRepository.lockExistingUserIds(targets)).thenReturn(targets);
        when(deletionRepository.findDeletionBlockedUserIds(targets)).thenReturn(List.of(targets.get(1)));
        UserManagementApplicationService service = service(
                userDomainService(userRepository, thirdPartyUserApiClient),
                userRepository,
                mock(UserManagementQueryRepository.class),
                deletionRepository,
                mock(UserRoleRepository.class),
                mock(DictionaryRepository.class),
                mock(TokenStore.class),
                thirdPartyUserApiClient);

        assertThatThrownBy(() -> service.deleteUsers(new DeleteUsersCommand(
                "usr_admin", List.of("usr_a", "usr_b"))))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.details()).containsEntry("userIds", List.of("usr_b"));
                });
        verify(deletionRepository, never()).deleteUsers(any());
    }

    @Test
    void deleteUsersHasTransactionBoundaryForBatchAtomicity() throws NoSuchMethodException {
        Transactional transactional = UserManagementApplicationService.class
                .getMethod("deleteUsers", DeleteUsersCommand.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional)
                .as("账号附属数据和 users 主记录必须整批提交或回滚")
                .isNotNull();
    }

    @Test
    void syncUsersFromTcdsUpdatesProfilesWithoutChangingUserIdsOrOrganization() {
        UserRepository userRepository = mock(UserRepository.class);
        User first = User.createNew(
                "usr_a", "AUTH_A", "old-a", "$2a$10$hashedvalue", "原组织", "旧研发", "旧部门");
        User second = User.createNew(
                "usr_b", "AUTH_B", "old-b", "$2a$10$hashedvalue", "原组织B", "旧研发B", "旧部门B");
        when(userRepository.findByUserId(first.userId())).thenReturn(Optional.of(first));
        when(userRepository.findByUserId(second.userId())).thenReturn(Optional.of(second));

        ThirdPartyUserApiClient thirdPartyUserApiClient = mock(ThirdPartyUserApiClient.class);
        when(thirdPartyUserApiClient.getUserByLoginName("AUTH_A")).thenReturn(Optional.of(
                new UserManagementResponses.ThirdPartyUserInfoResponse("张三", "AUTH_A", "研发中心", "测试部")));
        when(thirdPartyUserApiClient.getUserByLoginName("AUTH_B")).thenReturn(Optional.of(
                new UserManagementResponses.ThirdPartyUserInfoResponse("李四", "AUTH_B", "科技部", "平台部")));
        UserManagementApplicationService service = service(
                userDomainService(userRepository, thirdPartyUserApiClient),
                userRepository,
                mock(UserManagementQueryRepository.class),
                mock(UserDeletionRepository.class),
                mock(UserRoleRepository.class),
                mock(DictionaryRepository.class),
                mock(TokenStore.class),
                thirdPartyUserApiClient);

        var response = service.syncUsersFromTcds(new SyncUsersFromTcdsCommand(List.of("usr_a", "usr_b")));

        assertThat(response.syncedUserIds()).containsExactly("usr_a", "usr_b");
        assertThat(response.syncedCount()).isEqualTo(2);
        ArgumentCaptor<User> savedUsers = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(savedUsers.capture());
        assertThat(savedUsers.getAllValues())
                .extracting(User::userId, User::username, User::organization, User::rdDepartment, User::department)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first.userId(), "张三", "原组织", "研发中心", "测试部"),
                        org.assertj.core.groups.Tuple.tuple(second.userId(), "李四", "原组织B", "科技部", "平台部"));
    }

    @Test
    void syncUsersFromTcdsDoesNotWriteWhenAnyExternalLookupFails() {
        UserRepository userRepository = mock(UserRepository.class);
        User first = User.createNew(
                "usr_a", "AUTH_A", "old-a", "$2a$10$hashedvalue", null, null, null);
        User second = User.createNew(
                "usr_b", "AUTH_B", "old-b", "$2a$10$hashedvalue", null, null, null);
        when(userRepository.findByUserId(first.userId())).thenReturn(Optional.of(first));
        when(userRepository.findByUserId(second.userId())).thenReturn(Optional.of(second));
        ThirdPartyUserApiClient thirdPartyUserApiClient = mock(ThirdPartyUserApiClient.class);
        when(thirdPartyUserApiClient.getUserByLoginName("AUTH_A")).thenReturn(Optional.of(
                new UserManagementResponses.ThirdPartyUserInfoResponse("张三", "AUTH_A", "研发中心", "测试部")));
        when(thirdPartyUserApiClient.getUserByLoginName("AUTH_B")).thenReturn(Optional.empty());
        UserManagementApplicationService service = service(
                userDomainService(userRepository, thirdPartyUserApiClient),
                userRepository,
                mock(UserManagementQueryRepository.class),
                mock(UserDeletionRepository.class),
                mock(UserRoleRepository.class),
                mock(DictionaryRepository.class),
                mock(TokenStore.class),
                thirdPartyUserApiClient);

        assertThatThrownBy(() -> service.syncUsersFromTcds(
                new SyncUsersFromTcdsCommand(List.of("usr_a", "usr_b"))))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
        verify(userRepository, never()).save(any());
    }

    @Test
    void listRolesReturnsRoleOptionsSortedBySortOrder() {
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKey(Dictionary.DICT_KEY_ROLE)).thenReturn(List.of(
                roleDictionary(USER_DICT_ID, "USER", "普通用户", 4),
                roleDictionary(new DictId("dict_role_super_admin"), "SUPER_ADMIN", "超级管理员", 1),
                roleDictionary(APP_ADMIN_DICT_ID, "APP_ADMIN", "应用管理员", 3),
                roleDictionary(new DictId("dict_role_system_admin"), "SYSTEM_ADMIN", "系统管理员", 2)));

        UserManagementApplicationService service = service(
                mock(UserRepository.class), mock(UserRoleRepository.class), dictionaryRepository);

        List<RoleOption> roles = service.listRoles();

        assertThat(roles).extracting(RoleOption::roleCode)
                .containsExactly("SUPER_ADMIN", "SYSTEM_ADMIN", "APP_ADMIN", "USER");
        assertThat(roles).extracting(RoleOption::roleLabel)
                .containsExactly("超级管理员", "系统管理员", "应用管理员", "普通用户");
    }

    private Dictionary roleDictionary(DictId dictId, String value, String label, int sortOrder) {
        return new Dictionary(dictId, "角色", Dictionary.DICT_KEY_ROLE, value, label, sortOrder, NOW, NOW);
    }

    private UserManagementApplicationService service(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository) {
        ThirdPartyUserApiClient thirdPartyUserApiClient = mock(ThirdPartyUserApiClient.class);
        return service(
                userDomainService(userRepository, thirdPartyUserApiClient),
                userRepository,
                mock(UserManagementQueryRepository.class),
                mock(UserDeletionRepository.class),
                userRoleRepository,
                dictionaryRepository,
                mock(TokenStore.class),
                thirdPartyUserApiClient);
    }

    private UserManagementApplicationService service(
            UserDomainService userDomainService,
            UserRepository userRepository,
            UserManagementQueryRepository userManagementQueryRepository,
            UserDeletionRepository userDeletionRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository,
            TokenStore tokenStore,
            ThirdPartyUserApiClient thirdPartyUserApiClient) {
        return new UserManagementApplicationService(
                userDomainService,
                userRepository,
                userManagementQueryRepository,
                userDeletionRepository,
                userRoleRepository,
                dictionaryRepository,
                tokenStore,
                thirdPartyUserApiClient);
    }

    private UserDomainService userDomainService(UserRepository userRepository) {
        return userDomainService(userRepository, mock(ThirdPartyUserApiClient.class));
    }

    private UserDomainService userDomainService(
            UserRepository userRepository,
            ThirdPartyUserApiClient thirdPartyUserApiClient) {
        return new UserDomainService(
                userRepository,
                thirdPartyUserApiClient,
                mock(UserRoleRepository.class),
                mock(DictionaryRepository.class));
    }
}
