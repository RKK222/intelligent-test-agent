package com.enterprise.testagent.integration.lobehub;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.lobehub.LobehubSsoStore;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 验证 LobeHub 服务调用的时间戳、nonce、body digest 和 HMAC 签名。 */
@Component
public class LobehubHmacAuthenticator {

    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final int MINIMUM_NONCE_LENGTH = 16;
    private static final int MAXIMUM_NONCE_LENGTH = 128;

    private final LobehubSsoStore store;
    private final LobehubIntegrationProperties properties;
    private final Clock clock;

    /** 生产环境固定使用 UTC 时钟。 */
    @Autowired
    public LobehubHmacAuthenticator(LobehubSsoStore store, LobehubIntegrationProperties properties) {
        this(store, properties, Clock.systemUTC());
    }

    /** 测试构造器允许固定时间窗口。 */
    LobehubHmacAuthenticator(
            LobehubSsoStore store,
            LobehubIntegrationProperties properties,
            Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 验证完整请求后才占用 nonce，避免伪造请求污染重放窗口。 */
    public void authenticate(
            String method,
            String path,
            byte[] body,
            String timestamp,
            String nonce,
            String signature) {
        byte[] secret = requiredSecret();
        long epochSeconds = parseTimestamp(timestamp);
        long skew;
        try {
            skew = Math.abs(Math.subtractExact(clock.instant().getEpochSecond(), epochSeconds));
        } catch (ArithmeticException exception) {
            throw unauthenticated();
        }
        if (skew < 0 || skew > properties.getHmacClockSkew().toSeconds()) {
            throw unauthenticated();
        }
        if (nonce == null
                || nonce.length() < MINIMUM_NONCE_LENGTH
                || nonce.length() > MAXIMUM_NONCE_LENGTH
                || !nonce.matches("[A-Za-z0-9._~-]+")) {
            throw unauthenticated();
        }
        String expected = sign(secret, method, path, body, timestamp, nonce);
        if (signature == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            throw unauthenticated();
        }
        if (!store.reserveNonce(LobehubDigest.sha256(nonce), properties.getNonceTtl())) {
            throw unauthenticated();
        }
    }

    /** 按公开的规范化字符串生成 Base64URL HMAC-SHA256，供 fork 对齐实现。 */
    public static String sign(
            String secret,
            String method,
            String path,
            byte[] body,
            String timestamp,
            String nonce) {
        return sign(secret.getBytes(StandardCharsets.UTF_8), method, path, body, timestamp, nonce);
    }

    private static String sign(
            byte[] secret,
            String method,
            String path,
            byte[] body,
            String timestamp,
            String nonce) {
        String canonical = timestamp
                + "\n" + nonce
                + "\n" + method.toUpperCase(Locale.ROOT)
                + "\n" + path
                + "\n" + LobehubDigest.sha256(body == null ? new byte[0] : body);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("JVM does not provide HmacSHA256", exception);
        }
    }

    private byte[] requiredSecret() {
        String secret = properties.getHmacSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "LobeHub HMAC 密钥未安全配置");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private static long parseTimestamp(String timestamp) {
        try {
            return Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw unauthenticated();
        }
    }

    private static PlatformException unauthenticated() {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, "LobeHub 服务签名无效或已重放");
    }
}
