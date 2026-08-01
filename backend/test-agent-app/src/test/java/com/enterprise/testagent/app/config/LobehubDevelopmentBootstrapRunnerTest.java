package com.enterprise.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.configuration.management.CommonParameterManagementApplicationService;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterChangeLog;
import com.enterprise.testagent.domain.configuration.CommonParameterChangeLogRepository;
import com.enterprise.testagent.domain.configuration.CommonParameterRepository;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.ResolvedParameter;
import com.enterprise.testagent.integration.lobehub.LobehubDevelopmentOwnerResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

/** 验证显式 LobeHub 开发模式会通过既有审计服务安全初始化公共参数。 */
class LobehubDevelopmentBootstrapRunnerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-31T12:00:00Z");

    @Test
    void replacesSafePlaceholdersAndEnablesLobehubLastForLoopbackDatabase() throws Exception {
        ParameterStore store = new ParameterStore();
        store.add("param_lobehub_enabled_all", "LOBEHUB_ENABLED", "false");
        store.add("param_lobehub_base_url_all", "LOBEHUB_BASE_URL", "http://127.0.0.1:3210");
        store.add("param_lobehub_email_domain_all", "LOBEHUB_SSO_EMAIL_DOMAIN", "disabled.invalid");
        store.add("param_lobehub_owner_auth_all", "LOBEHUB_INITIAL_OWNER_UNIFIED_AUTH_ID", "NOT_CONFIGURED");
        List<CommonParameterChangeLog> changeLogs = new ArrayList<>();
        CommonParameterManagementApplicationService managementService = new CommonParameterManagementApplicationService(
                store,
                changeLogRepository(changeLogs),
                ignored -> { });
        LobehubDevelopmentOwnerResolver ownerResolver = mock(LobehubDevelopmentOwnerResolver.class);
        when(ownerResolver.resolve("", "NOT_CONFIGURED")).thenReturn("AUTH_OWNER");
        LobehubDevelopmentBootstrapRunner runner = new LobehubDevelopmentBootstrapRunner(
                store,
                managementService,
                ownerResolver,
                "jdbc:postgresql://127.0.0.1:5432/test_agent",
                "",
                "http://127.0.0.1:3210",
                "lobehub.local",
                true);

        runner.run(new DefaultApplicationArguments());

        assertThat(store.value("LOBEHUB_BASE_URL")).isEqualTo("http://127.0.0.1:3210");
        assertThat(store.value("LOBEHUB_SSO_EMAIL_DOMAIN")).isEqualTo("lobehub.local");
        assertThat(store.value("LOBEHUB_INITIAL_OWNER_UNIFIED_AUTH_ID")).isEqualTo("AUTH_OWNER");
        assertThat(store.value("LOBEHUB_ENABLED")).isEqualTo("true");
        assertThat(changeLogs).extracting(CommonParameterChangeLog::parameterId)
                .containsExactly(
                        "param_lobehub_email_domain_all",
                        "param_lobehub_owner_auth_all",
                        "param_lobehub_enabled_all");
    }

    @Test
    void refusesToMutateParametersForANonLoopbackDatabase() {
        ParameterStore store = new ParameterStore();
        store.add("param_lobehub_enabled_all", "LOBEHUB_ENABLED", "false");
        store.add("param_lobehub_base_url_all", "LOBEHUB_BASE_URL", "http://127.0.0.1:3210");
        store.add("param_lobehub_email_domain_all", "LOBEHUB_SSO_EMAIL_DOMAIN", "disabled.invalid");
        store.add("param_lobehub_owner_auth_all", "LOBEHUB_INITIAL_OWNER_UNIFIED_AUTH_ID", "NOT_CONFIGURED");
        List<CommonParameterChangeLog> changeLogs = new ArrayList<>();
        LobehubDevelopmentBootstrapRunner runner = new LobehubDevelopmentBootstrapRunner(
                store,
                new CommonParameterManagementApplicationService(store, changeLogRepository(changeLogs), ignored -> { }),
                mock(LobehubDevelopmentOwnerResolver.class),
                "jdbc:postgresql://db.shared.internal:5432/test_agent",
                "",
                "http://127.0.0.1:3210",
                "lobehub.local",
                true);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("回环地址");
        assertThat(store.value("LOBEHUB_ENABLED")).isEqualTo("false");
        assertThat(store.value("LOBEHUB_SSO_EMAIL_DOMAIN")).isEqualTo("disabled.invalid");
        assertThat(changeLogs).isEmpty();
    }

    @Test
    void acceptsIpv6LoopbackForLocalDatabaseAndChatOrigin() throws Exception {
        ParameterStore store = new ParameterStore();
        store.add("param_lobehub_enabled_all", "LOBEHUB_ENABLED", "false");
        store.add("param_lobehub_base_url_all", "LOBEHUB_BASE_URL", "http://[::1]:3210");
        store.add("param_lobehub_email_domain_all", "LOBEHUB_SSO_EMAIL_DOMAIN", "disabled.invalid");
        store.add("param_lobehub_owner_auth_all", "LOBEHUB_INITIAL_OWNER_UNIFIED_AUTH_ID", "NOT_CONFIGURED");
        LobehubDevelopmentOwnerResolver ownerResolver = mock(LobehubDevelopmentOwnerResolver.class);
        when(ownerResolver.resolve("", "NOT_CONFIGURED")).thenReturn("AUTH_OWNER");
        LobehubDevelopmentBootstrapRunner runner = new LobehubDevelopmentBootstrapRunner(
                store,
                new CommonParameterManagementApplicationService(
                        store, changeLogRepository(new ArrayList<>()), ignored -> { }),
                ownerResolver,
                "jdbc:postgresql://[::1]:5432/test_agent",
                "",
                "http://[::1]:3210",
                "lobehub.local",
                true);

        runner.run(new DefaultApplicationArguments());

        assertThat(store.value("LOBEHUB_ENABLED")).isEqualTo("true");
    }

    @Test
    void disablesAnExistingEntryBeforeOwnerResolutionCanFail() {
        ParameterStore store = configuredStore("true");
        List<CommonParameterChangeLog> changeLogs = new ArrayList<>();
        LobehubDevelopmentOwnerResolver ownerResolver = mock(LobehubDevelopmentOwnerResolver.class);
        when(ownerResolver.resolve("", "AUTH_OWNER"))
                .thenThrow(new PlatformException(ErrorCode.INTERNAL_ERROR, "owner 不唯一"));
        LobehubDevelopmentBootstrapRunner runner = new LobehubDevelopmentBootstrapRunner(
                store,
                new CommonParameterManagementApplicationService(store, changeLogRepository(changeLogs), ignored -> { }),
                ownerResolver,
                "jdbc:postgresql://127.0.0.1:5432/test_agent",
                "",
                "http://127.0.0.1:3210",
                "lobehub.local",
                true);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("owner 不唯一");
        assertThat(store.value("LOBEHUB_ENABLED")).isEqualTo("false");
        assertThat(changeLogs).extracting(CommonParameterChangeLog::newValue).containsExactly("false");
    }

    @Test
    void compensatesToDisabledWhenFinalEnableAuditFails() {
        ParameterStore store = configuredStore("false");
        CommonParameterChangeLogRepository failingAudit = new CommonParameterChangeLogRepository() {
            @Override
            public void save(CommonParameterChangeLog log) {
                if ("param_lobehub_enabled_all".equals(log.parameterId()) && "true".equals(log.newValue())) {
                    throw new IllegalStateException("audit unavailable");
                }
            }

            @Override
            public List<CommonParameterChangeLog> findByParameterId(String parameterId, int limit) {
                return List.of();
            }
        };
        LobehubDevelopmentOwnerResolver ownerResolver = mock(LobehubDevelopmentOwnerResolver.class);
        when(ownerResolver.resolve("", "AUTH_OWNER")).thenReturn("AUTH_OWNER");
        LobehubDevelopmentBootstrapRunner runner = new LobehubDevelopmentBootstrapRunner(
                store,
                new CommonParameterManagementApplicationService(store, failingAudit, ignored -> { }),
                ownerResolver,
                "jdbc:postgresql://127.0.0.1:5432/test_agent",
                "",
                "http://127.0.0.1:3210",
                "lobehub.local",
                true);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audit unavailable");
        assertThat(store.value("LOBEHUB_ENABLED")).isEqualTo("false");
    }

    @Test
    void compensationModeOnlyDisablesEntryAndDoesNotResolveOwner() throws Exception {
        ParameterStore store = configuredStore("true");
        List<CommonParameterChangeLog> changeLogs = new ArrayList<>();
        LobehubDevelopmentOwnerResolver ownerResolver = mock(LobehubDevelopmentOwnerResolver.class);
        LobehubDevelopmentBootstrapRunner runner = new LobehubDevelopmentBootstrapRunner(
                store,
                new CommonParameterManagementApplicationService(store, changeLogRepository(changeLogs), ignored -> { }),
                ownerResolver,
                "jdbc:postgresql://127.0.0.1:5432/test_agent",
                "",
                "http://127.0.0.1:3210",
                "lobehub.local",
                false);

        runner.run(new DefaultApplicationArguments());

        assertThat(store.value("LOBEHUB_ENABLED")).isEqualTo("false");
        assertThat(changeLogs).extracting(CommonParameterChangeLog::newValue).containsExactly("false");
        org.mockito.Mockito.verifyNoInteractions(ownerResolver);
    }

    private static ParameterStore configuredStore(String enabled) {
        ParameterStore store = new ParameterStore();
        store.add("param_lobehub_enabled_all", "LOBEHUB_ENABLED", enabled);
        store.add("param_lobehub_base_url_all", "LOBEHUB_BASE_URL", "http://127.0.0.1:3210");
        store.add("param_lobehub_email_domain_all", "LOBEHUB_SSO_EMAIL_DOMAIN", "lobehub.local");
        store.add("param_lobehub_owner_auth_all", "LOBEHUB_INITIAL_OWNER_UNIFIED_AUTH_ID", "AUTH_OWNER");
        return store;
    }

    private static CommonParameterChangeLogRepository changeLogRepository(List<CommonParameterChangeLog> logs) {
        return new CommonParameterChangeLogRepository() {
            @Override
            public void save(CommonParameterChangeLog log) {
                logs.add(log);
            }

            @Override
            public List<CommonParameterChangeLog> findByParameterId(String parameterId, int limit) {
                return logs.stream()
                        .filter(log -> parameterId.equals(log.parameterId()))
                        .limit(limit)
                        .toList();
            }
        };
    }

    /** 同时实现读写端口，使测试验证真实参数状态而不是验证 mock 调用。 */
    private static final class ParameterStore implements CommonParameterRepository, CommonParameterValues {

        private final Map<String, CommonParameter> byId = new LinkedHashMap<>();

        void add(String parameterId, String englishName, String value) {
            byId.put(parameterId, new CommonParameter(
                    parameterId,
                    englishName,
                    englishName,
                    value,
                    ParameterPlatform.ALL,
                    true,
                    CREATED_AT,
                    CREATED_AT));
        }

        String value(String englishName) {
            return findByEnglishNameAndPlatform(englishName, ParameterPlatform.ALL)
                    .orElseThrow()
                    .parameterValue();
        }

        @Override
        public Optional<CommonParameter> findByEnglishNameAndPlatform(
                String englishName,
                ParameterPlatform platform) {
            return byId.values().stream()
                    .filter(parameter -> parameter.englishName().equals(englishName))
                    .filter(parameter -> parameter.platform() == platform)
                    .findFirst();
        }

        @Override
        public List<CommonParameter> findAll() {
            return byId.values().stream()
                    .sorted(Comparator.comparing(CommonParameter::englishName))
                    .toList();
        }

        @Override
        public Optional<CommonParameter> findByParameterId(String parameterId) {
            return Optional.ofNullable(byId.get(parameterId));
        }

        @Override
        public int updateValue(String parameterId, String newValue, Instant updatedAt) {
            CommonParameter current = byId.get(parameterId);
            if (current == null) {
                return 0;
            }
            byId.put(parameterId, current.withValue(newValue, updatedAt));
            return 1;
        }

        @Override
        public Optional<String> resolvedValue(String englishName) {
            return resolvedValue(englishName, ParameterPlatform.ALL);
        }

        @Override
        public Optional<String> resolvedValue(String englishName, ParameterPlatform platform) {
            return raw(englishName, platform).map(CommonParameter::parameterValue);
        }

        @Override
        public Optional<CommonParameter> raw(String englishName, ParameterPlatform platform) {
            return findByEnglishNameAndPlatform(englishName, platform);
        }

        @Override
        public List<ResolvedParameter> resolvedAll() {
            return List.of();
        }
    }
}
