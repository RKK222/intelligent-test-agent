package com.enterprise.testagent.system.management.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.dictionary.DictId;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRole;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/**
 * 验证统一认证首次登录建号时同步授予普通用户角色，存量用户则保持已有授权不变。
 */
class UserDomainServiceTest {

    private static final DictId USER_ROLE_ID = new DictId("dict_role_user");
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void firstUnifiedAuthLoginCreatesUserAndGrantsDefaultUserRole() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUnifiedAuthId("AUTH_NEW")).thenReturn(Optional.empty());
        ThirdPartyUserApiClient thirdPartyUserApiClient = mock(ThirdPartyUserApiClient.class);
        when(thirdPartyUserApiClient.getUserByLoginName("AUTH_NEW")).thenReturn(Optional.of(
                new UserManagementResponses.ThirdPartyUserInfoResponse(
                        "张三", "usr_tcds_zhangsan", "研发中心", "测试平台部")));
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, Dictionary.ROLE_USER))
                .thenReturn(Optional.of(userRoleDictionary()));
        UserDomainService service = new UserDomainService(
                userRepository, thirdPartyUserApiClient, userRoleRepository, dictionaryRepository);

        User user = service.findOrCreateByUnifiedAuthId("AUTH_NEW");

        assertThat(user.userId().value()).isEqualTo("usr_tcds_zhangsan");
        assertThat(user.username()).isEqualTo("张三");
        assertThat(user.rdDepartment()).isEqualTo("研发中心");
        assertThat(user.department()).isEqualTo("测试平台部");
        verify(userRepository).save(user);
        verify(userRoleRepository).save(argThat((UserRole role) ->
                role.userId().equals(user.userId()) && role.dictId().equals(USER_ROLE_ID)));
    }

    @Test
    void fallbackUnifiedAuthUserStillGetsDefaultUserRole() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUnifiedAuthId("AUTH_FALLBACK")).thenReturn(Optional.empty());
        ThirdPartyUserApiClient thirdPartyUserApiClient = mock(ThirdPartyUserApiClient.class);
        when(thirdPartyUserApiClient.getUserByLoginName("AUTH_FALLBACK")).thenReturn(Optional.empty());
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, Dictionary.ROLE_USER))
                .thenReturn(Optional.of(userRoleDictionary()));
        UserDomainService service = new UserDomainService(
                userRepository, thirdPartyUserApiClient, userRoleRepository, dictionaryRepository);

        User user = service.findOrCreateByUnifiedAuthId("AUTH_FALLBACK");

        assertThat(user.userId().value()).isEqualTo("AUTH_FALLBACK");
        verify(userRoleRepository).save(argThat((UserRole role) -> role.userId().equals(user.userId())));
    }

    @Test
    void existingUnifiedAuthUserIsReturnedWithoutChangingRoles() {
        User existing = User.createNew(
                "usr_existing", "AUTH_EXISTING", "existing", "$2a$10$hashedvalue", null, null, null);
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUnifiedAuthId("AUTH_EXISTING")).thenReturn(Optional.of(existing));
        ThirdPartyUserApiClient thirdPartyUserApiClient = mock(ThirdPartyUserApiClient.class);
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        UserDomainService service = new UserDomainService(
                userRepository, thirdPartyUserApiClient, userRoleRepository, dictionaryRepository);

        assertThat(service.findOrCreateByUnifiedAuthId("AUTH_EXISTING")).isSameAs(existing);

        verify(thirdPartyUserApiClient, never()).getUserByLoginName(any());
        verify(userRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void missingDefaultRoleDoesNotCreateRolelessUser() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUnifiedAuthId("AUTH_NEW")).thenReturn(Optional.empty());
        ThirdPartyUserApiClient thirdPartyUserApiClient = mock(ThirdPartyUserApiClient.class);
        when(thirdPartyUserApiClient.getUserByLoginName("AUTH_NEW")).thenReturn(Optional.empty());
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, Dictionary.ROLE_USER))
                .thenReturn(Optional.empty());
        UserDomainService service = new UserDomainService(
                userRepository, thirdPartyUserApiClient, userRoleRepository, dictionaryRepository);

        assertThatThrownBy(() -> service.findOrCreateByUnifiedAuthId("AUTH_NEW"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(exception.getMessage()).contains("默认普通用户角色未配置");
                });
        verify(userRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void roleWriteFailureRollsBackUnifiedAuthCreationTransaction() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUnifiedAuthId("AUTH_NEW")).thenReturn(Optional.empty());
        ThirdPartyUserApiClient thirdPartyUserApiClient = mock(ThirdPartyUserApiClient.class);
        when(thirdPartyUserApiClient.getUserByLoginName("AUTH_NEW")).thenReturn(Optional.empty());
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        doThrow(new IllegalStateException("role write failed")).when(userRoleRepository).save(any());
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, Dictionary.ROLE_USER))
                .thenReturn(Optional.of(userRoleDictionary()));
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        UserDomainService service = new UserDomainService(
                userRepository, thirdPartyUserApiClient, userRoleRepository, dictionaryRepository);
        service.setPlatformTransactionManager(transactionManager);

        assertThatThrownBy(() -> service.findOrCreateByUnifiedAuthId("AUTH_NEW"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("role write failed");

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
    }

    private Dictionary userRoleDictionary() {
        return new Dictionary(
                USER_ROLE_ID,
                "应用角色",
                Dictionary.DICT_KEY_ROLE,
                Dictionary.ROLE_USER,
                "普通用户",
                4,
                NOW,
                NOW);
    }
}
