package com.enterprise.testagent.integration.lobehub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.ResolvedParameter;
import com.enterprise.testagent.domain.dictionary.DictId;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRole;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.lobehub.LobehubGrantPayload;
import com.enterprise.testagent.domain.lobehub.LobehubSsoStore;
import com.enterprise.testagent.domain.lobehub.LobehubTicketPayload;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LobehubSsoApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-29T02:00:00Z");

    @Test
    void issuesDigestOnlyTicketAndRedeemsDepartmentIdentityWithScopedGrant() {
        Fixture fixture = fixture(activeUser("  Test\u3000  Dept  "));
        AuthPrincipal principal = principal(List.of(Dictionary.ROLE_USER));

        LobehubSsoTicketIssue issue = fixture.service().issue(principal);

        assertThat(issue.ticket()).matches("[A-Za-z0-9_-]{43}");
        assertThat(issue.expiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(issue.consumeUrl()).isEqualTo("http://lobehub.internal/api/auth/platform/consume");
        assertThat(fixture.store().tickets).containsOnlyKeys(LobehubDigest.sha256(issue.ticket()));
        assertThat(fixture.store().tickets.keySet()).noneMatch(key -> key.contains(issue.ticket()));

        LobehubSsoRedeemResult redeemed = fixture.service().redeem(issue.ticket());

        assertThat(redeemed.userId()).isEqualTo("usr_lobehub");
        assertThat(redeemed.email()).isEqualTo("AUTH_LOBEHUB@example.internal");
        assertThat(redeemed.department()).isEqualTo("Test Dept");
        assertThat(redeemed.departmentKey())
                .isEqualTo(LobehubDigest.sha256("test dept"));
        assertThat(redeemed.instanceRole()).isEqualTo("member");
        assertThat(redeemed.modelGrant()).matches("[A-Za-z0-9_-]{43}");
        assertThat(redeemed.grantExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(fixture.store().grants)
                .containsOnlyKeys(LobehubDigest.sha256(redeemed.modelGrant()));
        assertThat(fixture.store().tickets).isEmpty();

        LobehubModelIdentity identity = fixture.service().authenticateModelGrant(redeemed.modelGrant());
        assertThat(identity.userId()).isEqualTo("usr_lobehub");
        assertThat(identity.unifiedAuthId()).isEqualTo("AUTH_LOBEHUB");
        assertThat(identity.scope()).isEqualTo("model-gateway");
    }

    @Test
    void mapsConfiguredOwnerAndOtherSuperAdminsWithoutChangingDepartmentKey() {
        Fixture ownerFixture = fixture(activeUser("研发一部"), "AUTH_LOBEHUB");
        LobehubSsoRedeemResult owner = ownerFixture.service().redeem(
                ownerFixture.service().issue(principal(List.of(Dictionary.ROLE_SUPER_ADMIN))).ticket());
        assertThat(owner.instanceRole()).isEqualTo("owner");

        Fixture adminFixture = fixture(
                activeUser("研发一部"),
                "SOMEONE_ELSE",
                Dictionary.ROLE_SUPER_ADMIN);
        LobehubSsoRedeemResult admin = adminFixture.service().redeem(
                adminFixture.service().issue(principal(List.of(Dictionary.ROLE_SUPER_ADMIN))).ticket());
        assertThat(admin.instanceRole()).isEqualTo("admin");
        assertThat(admin.departmentKey()).isEqualTo(owner.departmentKey());
    }

    @Test
    void rejectsBlankDepartmentAndDisabledUsersBeforeIssuingTicket() {
        Fixture blankDepartment = fixture(activeUser("\u3000 \t"));

        assertThatThrownBy(() -> blankDepartment.service().issue(principal(List.of(Dictionary.ROLE_USER))))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(blankDepartment.store().tickets).isEmpty();

        Fixture disabled = fixture(user("测试部", UserStatus.INACTIVE));
        assertThatThrownBy(() -> disabled.service().issue(principal(List.of(Dictionary.ROLE_USER))))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void rotatesGrantOnNextLoginAndRejectsGrantAfterUserIsDisabled() {
        MutableUserRepository users = new MutableUserRepository();
        User active = activeUser("测试部");
        users.setUser(active);
        Fixture fixture = fixture(active, "AUTH_LOBEHUB", users);

        String first = fixture.service().redeem(
                fixture.service().issue(principal(List.of(Dictionary.ROLE_USER))).ticket()).modelGrant();
        String second = fixture.service().redeem(
                fixture.service().issue(principal(List.of(Dictionary.ROLE_USER))).ticket()).modelGrant();

        assertThat(first).isNotEqualTo(second);
        assertThatThrownBy(() -> fixture.service().authenticateModelGrant(first))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));

        users.setUser(user("测试部", UserStatus.INACTIVE));
        assertThatThrownBy(() -> fixture.service().authenticateModelGrant(second))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void collapsesAllUnicodeWhitespaceInDepartmentName() {
        assertThat(LobehubSsoApplicationService.normalizeDepartment(" 研发\u2028\u2007\t一部 "))
                .isEqualTo("研发 一部");
        assertThat(LobehubSsoApplicationService.departmentKeyInput("Test Α部"))
                .isEqualTo("test Α部");
    }

    @Test
    void rejectsLobehubBaseUrlWithAPathInsteadOfProducingANonFixedConsumePath() {
        Fixture fixture = fixture(activeUser("测试部"));
        fixture.parameters().values().put("LOBEHUB_BASE_URL", "http://lobehub.internal/tenant");

        assertThatThrownBy(() -> fixture.service().issue(principal(List.of(Dictionary.ROLE_USER))))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void enabledFeatureRejectsPlaceholderOwnerAndEmailDomainBeforePersistingATicket() {
        Fixture placeholderOwner = fixture(activeUser("测试部"));
        placeholderOwner.parameters().values()
                .put("LOBEHUB_INITIAL_OWNER_UNIFIED_AUTH_ID", "NOT_CONFIGURED");

        assertThatThrownBy(() -> placeholderOwner.service().issue(principal(List.of(Dictionary.ROLE_USER))))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
        assertThat(placeholderOwner.store().tickets).isEmpty();

        Fixture placeholderEmail = fixture(activeUser("测试部"));
        placeholderEmail.parameters().values().put("LOBEHUB_SSO_EMAIL_DOMAIN", "disabled.invalid");

        assertThatThrownBy(() -> placeholderEmail.service().issue(principal(List.of(Dictionary.ROLE_USER))))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
        assertThat(placeholderEmail.store().tickets).isEmpty();
    }

    @Test
    void featureKillSwitchRejectsAnOtherwiseValidModelGrant() {
        Fixture fixture = fixture(activeUser("测试部"));
        String grant = fixture.service().redeem(
                fixture.service().issue(principal(List.of(Dictionary.ROLE_USER))).ticket()).modelGrant();
        fixture.parameters().values().put("LOBEHUB_ENABLED", "false");

        assertThatThrownBy(() -> fixture.service().authenticateModelGrant(grant))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private static Fixture fixture(User user) {
        return fixture(user, "SOMEONE_ELSE");
    }

    private static Fixture fixture(User user, String ownerUnifiedAuthId) {
        return fixture(user, ownerUnifiedAuthId, Dictionary.ROLE_USER);
    }

    private static Fixture fixture(User user, String ownerUnifiedAuthId, String persistedRole) {
        MutableUserRepository users = new MutableUserRepository();
        users.setUser(user);
        return fixture(user, ownerUnifiedAuthId, persistedRole, users);
    }

    private static Fixture fixture(User user, String ownerUnifiedAuthId, MutableUserRepository users) {
        return fixture(user, ownerUnifiedAuthId, Dictionary.ROLE_USER, users);
    }

    private static Fixture fixture(
            User user,
            String ownerUnifiedAuthId,
            String persistedRole,
            MutableUserRepository users) {
        FixedCommonParameterValues parameters = new FixedCommonParameterValues(new HashMap<>(Map.of(
                "LOBEHUB_ENABLED", "true",
                "LOBEHUB_BASE_URL", "http://lobehub.internal/",
                "LOBEHUB_SSO_EMAIL_DOMAIN", "example.internal",
                "LOBEHUB_INITIAL_OWNER_UNIFIED_AUTH_ID", ownerUnifiedAuthId)));

        DictId roleId = new DictId("dict_lobehub_role");
        UserRoleRepository roles = new FixedUserRoleRepository(
                List.of(new UserRole(new UserId("usr_lobehub"), roleId, NOW)));
        DictionaryRepository dictionaries = new FixedDictionaryRepository(dictionary(persistedRole));

        InMemoryStore store = new InMemoryStore();
        LobehubIntegrationProperties properties = new LobehubIntegrationProperties();
        LobehubSsoApplicationService service = new LobehubSsoApplicationService(
                users,
                roles,
                dictionaries,
                parameters,
                store,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SequenceTokenFactory());
        return new Fixture(service, store, parameters);
    }

    private static User activeUser(String department) {
        return user(department, UserStatus.ACTIVE);
    }

    private static User user(String department, UserStatus status) {
        return new User(
                new UserId("usr_lobehub"),
                "AUTH_LOBEHUB",
                "平台用户",
                "password-hash",
                "总行",
                "研发中心",
                department,
                status,
                NOW.minusSeconds(100),
                NOW);
    }

    private static AuthPrincipal principal(List<String> roles) {
        return new AuthPrincipal(
                "raw-platform-token",
                new UserId("usr_lobehub"),
                "平台用户",
                "AUTH_LOBEHUB",
                roles,
                NOW.minusSeconds(10),
                NOW.plusSeconds(3600));
    }

    private static Dictionary dictionary(String role) {
        return new Dictionary(
                new DictId("dict_lobehub_role"),
                "角色",
                Dictionary.DICT_KEY_ROLE,
                role,
                role,
                0,
                NOW,
                NOW);
    }

    private record Fixture(
            LobehubSsoApplicationService service,
            InMemoryStore store,
            FixedCommonParameterValues parameters) {
    }

    private static final class SequenceTokenFactory implements LobehubTokenFactory {
        private int value;

        @Override
        public String newToken() {
            value++;
            byte[] bytes = new byte[32];
            bytes[31] = (byte) value;
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
    }

    private static final class InMemoryStore implements LobehubSsoStore {
        private final Map<String, LobehubTicketPayload> tickets = new HashMap<>();
        private final Map<String, LobehubGrantPayload> grants = new HashMap<>();
        private final Map<String, String> grantsByUser = new HashMap<>();
        private final Map<String, Boolean> nonces = new HashMap<>();

        @Override
        public void saveTicket(String ticketDigest, LobehubTicketPayload payload, Duration ttl) {
            tickets.put(ticketDigest, payload);
        }

        @Override
        public Optional<LobehubTicketPayload> consumeTicket(String ticketDigest) {
            return Optional.ofNullable(tickets.remove(ticketDigest));
        }

        @Override
        public boolean reserveNonce(String nonceDigest, Duration ttl) {
            return nonces.putIfAbsent(nonceDigest, Boolean.TRUE) == null;
        }

        @Override
        public void rotateGrant(String userId, String grantDigest, LobehubGrantPayload payload, Duration ttl) {
            String old = grantsByUser.put(userId, grantDigest);
            if (old != null) {
                grants.remove(old);
            }
            grants.put(grantDigest, payload);
        }

        @Override
        public Optional<LobehubGrantPayload> findGrant(String grantDigest) {
            return Optional.ofNullable(grants.get(grantDigest));
        }

        @Override
        public void revokeGrant(String grantDigest) {
            LobehubGrantPayload removed = grants.remove(grantDigest);
            if (removed != null) {
                grantsByUser.remove(removed.userId(), grantDigest);
            }
        }
    }

    private static final class MutableUserRepository implements UserRepository {
        private User user;

        private void setUser(User user) {
            this.user = user;
        }

        @Override
        public void save(User user) {
            this.user = user;
        }

        @Override
        public Optional<User> findByUserId(UserId userId) {
            return user != null && user.userId().equals(userId) ? Optional.of(user) : Optional.empty();
        }

        @Override
        public Optional<User> findByUnifiedAuthId(String unifiedAuthId) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.empty();
        }

        @Override
        public PageResponse<User> findPage(String keyword, PageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByUsername(String username) {
            return false;
        }

        @Override
        public boolean existsByUnifiedAuthId(String unifiedAuthId) {
            return false;
        }
    }

    private record FixedCommonParameterValues(Map<String, String> values) implements CommonParameterValues {
        @Override
        public Optional<String> resolvedValue(String englishName) {
            return Optional.ofNullable(values.get(englishName));
        }

        @Override
        public Optional<String> resolvedValue(String englishName, ParameterPlatform platform) {
            return resolvedValue(englishName);
        }

        @Override
        public Optional<CommonParameter> raw(String englishName, ParameterPlatform platform) {
            return Optional.empty();
        }

        @Override
        public List<CommonParameter> findAll() {
            return List.of();
        }

        @Override
        public List<ResolvedParameter> resolvedAll() {
            return List.of();
        }
    }

    private record FixedUserRoleRepository(List<UserRole> roles) implements UserRoleRepository {
        @Override
        public void save(UserRole userRole) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<UserRole> findByUserId(UserId userId) {
            return roles;
        }

        @Override
        public void delete(UserRole userRole) {
            throw new UnsupportedOperationException();
        }
    }

    private record FixedDictionaryRepository(Dictionary dictionary) implements DictionaryRepository {
        @Override
        public void save(Dictionary dictionary) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Dictionary> findByDictId(DictId dictId) {
            return dictionary.dictId().equals(dictId) ? Optional.of(dictionary) : Optional.empty();
        }

        @Override
        public List<Dictionary> findByDictKey(String dictKey) {
            return List.of();
        }

        @Override
        public Optional<Dictionary> findByDictKeyAndValue(String dictKey, String dictValue) {
            return Optional.empty();
        }

        @Override
        public void delete(DictId dictId) {
            throw new UnsupportedOperationException();
        }
    }
}
