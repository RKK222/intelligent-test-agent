package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 为用户 OpenCode 进程签发仅供一次 UI 测试执行 Tool 使用的短期凭据。 */
@Service
public class UiTestExecutionToolTokenService {

    public static final String TOKEN_ENV_NAME = "TEST_AGENT_UI_TEST_TOOL_TOKEN";
    public static final String ENDPOINT_PATH = "/api/internal/agent/opencode/ui-test-executions";

    private static final String AUDIENCE = "ui-test-execution";
    private static final Duration TOKEN_TTL = Duration.ofDays(7);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ManagerControlSettings managerControlSettings;
    private final UserRepository userRepository;
    private final Clock clock;

    @Autowired
    public UiTestExecutionToolTokenService(
            ManagerControlSettings managerControlSettings,
            UserRepository userRepository) {
        this(managerControlSettings, userRepository, Clock.systemUTC());
    }

    UiTestExecutionToolTokenService(
            ManagerControlSettings managerControlSettings,
            UserRepository userRepository,
            Clock clock) {
        this.managerControlSettings = managerControlSettings;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** 签发含固定 audience、用户 ID 和到期时间的凭据。 */
    public String issue(UserId userId) {
        requireSigningKey();
        long expiresAt = clock.instant().plus(TOKEN_TTL).getEpochSecond();
        String payload = AUDIENCE + "\n" + userId.value() + "\n" + expiresAt;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    /** 校验专用 Bearer 凭据并实时确认用户仍可登录。 */
    public UserId authenticate(String authorization) {
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
        if (!AUDIENCE.equals(payload[0])) {
            throw unauthenticated();
        }
        UserId userId = new UserId(payload[1]);
        long expiresAt;
        try {
            expiresAt = Long.parseLong(payload[2]);
        } catch (NumberFormatException exception) {
            throw unauthenticated();
        }
        if (!clock.instant().isBefore(Instant.ofEpochSecond(expiresAt))) {
            throw unauthenticated();
        }
        return userRepository.findByUserId(userId)
                .filter(User::canLogin)
                .map(User::userId)
                .orElseThrow(this::unauthenticated);
    }

    private String[] decodePayload(String encodedPayload) {
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
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "UI 测试 Tool 凭据签发失败");
        }
    }

    private String requireSigningKey() {
        String value = managerControlSettings == null ? null : managerControlSettings.token();
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "UI 测试 Tool 尚未配置");
        }
        return value;
    }

    private PlatformException unauthenticated() {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, "UI 测试 Tool 凭据无效或已过期");
    }
}
