package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 为用户 OpenCode 进程签发仅供工作区 Git Tool 使用的短期凭据。
 *
 * <p>凭据不会进入通用用户登录过滤器，也不能调用其它平台 API；签名密钥复用只驻留 Java
 * 进程的 manager 控制密钥，Tool 进程只获得带用户和过期时间的签名结果。</p>
 */
@Service
public class WorkspaceGitToolTokenService {

    public static final String TOKEN_ENV_NAME = "TEST_AGENT_WORKSPACE_GIT_TOOL_TOKEN";
    public static final String BASE_URL_ENV_NAME = "TEST_AGENT_PLATFORM_BASE_URL";
    public static final String ENDPOINT_PATH =
            "/api/internal/agent/opencode/workspace-git-tool";
    private static final Duration TOKEN_TTL = Duration.ofDays(7);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ManagerControlSettings managerControlSettings;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final DictionaryRepository dictionaryRepository;
    private final Clock clock;

    @Autowired
    public WorkspaceGitToolTokenService(
            ManagerControlSettings managerControlSettings,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository) {
        this(managerControlSettings, userRepository, userRoleRepository, dictionaryRepository, Clock.systemUTC());
    }

    WorkspaceGitToolTokenService(
            ManagerControlSettings managerControlSettings,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository,
            Clock clock) {
        this.managerControlSettings = managerControlSettings;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.clock = clock;
    }

    /** 为指定用户生成七天有效、仅由专用 Controller 接受的签名凭据。 */
    public String issue(UserId userId) {
        requireSigningKey();
        long expiresAt = clock.instant().plus(TOKEN_TTL).getEpochSecond();
        String payload = userId.value() + "\n" + expiresAt;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    /** 校验 Bearer 凭据并实时读取用户状态和角色，避免长期缓存权限。 */
    public WorkspaceGitToolPrincipal authenticate(String authorization) {
        String token = bearerToken(authorization);
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw unauthenticated();
        }
        byte[] expected = sign(parts[0]).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = parts[1].getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw unauthenticated();
        }
        String[] payload = decodePayload(parts[0]);
        UserId userId = new UserId(payload[0]);
        long expiresAt;
        try {
            expiresAt = Long.parseLong(payload[1]);
        } catch (NumberFormatException exception) {
            throw unauthenticated();
        }
        if (!clock.instant().isBefore(Instant.ofEpochSecond(expiresAt))) {
            throw unauthenticated();
        }
        return currentPrincipal(userId);
    }

    /** 为同一进程中的其它只读专用 Tool 签发带 audience 的独立凭据。 */
    String issueForAudience(UserId userId, String audience) {
        requireSigningKey();
        String normalizedAudience = requireAudience(audience);
        long expiresAt = clock.instant().plus(TOKEN_TTL).getEpochSecond();
        String payload = normalizedAudience + "\n" + userId.value() + "\n" + expiresAt;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    /** 校验 audience 后实时恢复用户和角色，禁止不同专用 Tool 之间复用凭据。 */
    WorkspaceGitToolPrincipal authenticateForAudience(String authorization, String audience) {
        String token = bearerToken(authorization);
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw unauthenticated();
        }
        byte[] expected = sign(parts[0]).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = parts[1].getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw unauthenticated();
        }
        String[] payload = decodeAudiencePayload(parts[0]);
        if (!requireAudience(audience).equals(payload[0])) {
            throw unauthenticated();
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(payload[2]);
        } catch (NumberFormatException exception) {
            throw unauthenticated();
        }
        if (!clock.instant().isBefore(Instant.ofEpochSecond(expiresAt))) {
            throw unauthenticated();
        }
        return currentPrincipal(new UserId(payload[1]));
    }

    private WorkspaceGitToolPrincipal currentPrincipal(UserId userId) {
        User user = userRepository.findByUserId(userId)
                .filter(User::canLogin)
                .orElseThrow(this::unauthenticated);
        List<String> roles = userRoleRepository.findByUserId(user.userId()).stream()
                .map(role -> dictionaryRepository.findByDictId(role.dictId()))
                .flatMap(java.util.Optional::stream)
                .filter(dictionary -> Dictionary.DICT_KEY_ROLE.equals(dictionary.dictKey()))
                .map(Dictionary::dictValue)
                .sorted()
                .toList();
        return new WorkspaceGitToolPrincipal(user.userId(), roles);
    }

    private String[] decodeAudiencePayload(String encodedPayload) {
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            String[] values = decoded.split("\\n", -1);
            if (values.length != 3 || values[0].isBlank() || values[1].isBlank() || values[2].isBlank()) {
                throw unauthenticated();
            }
            return values;
        } catch (IllegalArgumentException exception) {
            throw unauthenticated();
        }
    }

    private String requireAudience(String audience) {
        if (audience == null || !audience.matches("[a-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("tool audience is invalid");
        }
        return audience;
    }

    private String[] decodePayload(String encodedPayload) {
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            String[] values = decoded.split("\\n", -1);
            if (values.length != 2 || values[0].isBlank() || values[1].isBlank()) {
                throw unauthenticated();
            }
            return values;
        } catch (IllegalArgumentException exception) {
            throw unauthenticated();
        }
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw unauthenticated();
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw unauthenticated();
        }
        return token;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(requireSigningKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "工作区 Git Tool 凭据签发失败");
        }
    }

    private String requireSigningKey() {
        String value = managerControlSettings == null ? null : managerControlSettings.token();
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "工作区 Git Tool 尚未配置");
        }
        return value;
    }

    private PlatformException unauthenticated() {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, "工作区 Git Tool 凭据无效或已过期");
    }

    /** 专用凭据恢复出的最小身份；不承载通用平台登录 Token。 */
    public record WorkspaceGitToolPrincipal(UserId userId, List<String> roles) {
        public WorkspaceGitToolPrincipal {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }
}
