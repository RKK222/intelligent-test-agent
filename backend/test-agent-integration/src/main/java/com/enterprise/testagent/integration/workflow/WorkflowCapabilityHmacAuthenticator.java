package com.enterprise.testagent.integration.workflow;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workflowcapability.WorkflowCapabilityStore;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 验证Python workflow请求的固定client、请求体、时钟、nonce、HMAC和平台session marker。 */
@Component
public class WorkflowCapabilityHmacAuthenticator {

    public static final String CLIENT_ID = "workflow";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private final WorkflowCapabilityStore store;
    private final TokenSessionMarkerStore markers;
    private final WorkflowCapabilityProperties properties;
    private final Clock clock;

    @Autowired
    public WorkflowCapabilityHmacAuthenticator(
            WorkflowCapabilityStore store,
            TokenSessionMarkerStore markers,
            WorkflowCapabilityProperties properties) {
        this(store, markers, properties, Clock.systemUTC());
    }

    WorkflowCapabilityHmacAuthenticator(
            WorkflowCapabilityStore store,
            TokenSessionMarkerStore markers,
            WorkflowCapabilityProperties properties,
            Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.markers = Objects.requireNonNull(markers);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    public WorkflowCapabilityCaller authenticateWorkflow(
            String method,
            String path,
            byte[] body,
            String clientId,
            String userId,
            String sessionDigest,
            String timestamp,
            String nonce,
            String claimedBodyDigest,
            String signature) {
        if (!CLIENT_ID.equals(clientId)
                || userId == null
                || !userId.matches("[A-Za-z0-9_-]{8,128}")
                || sessionDigest == null
                || !sessionDigest.matches("[a-f0-9]{64}")
                || nonce == null
                || !nonce.matches("[A-Za-z0-9._~-]{16,128}")) {
            throw unauthenticated();
        }
        long epoch = parseTimestamp(timestamp);
        if (Math.abs(clock.instant().getEpochSecond() - epoch) > properties.getHmacClockSkew().toSeconds()) {
            throw unauthenticated();
        }
        String actualBodyDigest = sha256(body);
        if (!constantEquals(actualBodyDigest, claimedBodyDigest)) {
            throw unauthenticated();
        }
        String canonical = String.join("\n",
                method.toUpperCase(),
                path,
                actualBodyDigest,
                userId,
                sessionDigest,
                timestamp,
                nonce,
                CLIENT_ID);
        if (!constantEquals(hmac(requiredSecret(properties.getHmacSecret()), canonical), signature)) {
            throw unauthenticated();
        }
        if (!store.reserveNonce(sha256(nonce.getBytes(StandardCharsets.UTF_8)), properties.getNonceTtl())) {
            throw unauthenticated();
        }
        UserId parsedUserId = new UserId(userId);
        if (!markers.isActiveForUser(sessionDigest, parsedUserId)) {
            throw unauthenticated();
        }
        return new WorkflowCapabilityCaller(parsedUserId, sessionDigest);
    }

    /** Runner兑换ticket时使用独立密钥和固定runnerId，不接受Python用户头代替。 */
    public void authenticateRunner(
            String method,
            String path,
            byte[] body,
            String runnerId,
            String timestamp,
            String nonce,
            String claimedBodyDigest,
            String signature) {
        if (!Objects.equals(properties.getRunnerId(), runnerId)
                || nonce == null
                || !nonce.matches("[A-Za-z0-9._~-]{16,128}")) {
            throw unauthenticated();
        }
        long epoch = parseTimestamp(timestamp);
        if (Math.abs(clock.instant().getEpochSecond() - epoch) > properties.getHmacClockSkew().toSeconds()) {
            throw unauthenticated();
        }
        String actualBodyDigest = sha256(body);
        if (!constantEquals(actualBodyDigest, claimedBodyDigest)) {
            throw unauthenticated();
        }
        String canonical = String.join("\n",
                method.toUpperCase(),
                path,
                actualBodyDigest,
                runnerId,
                timestamp,
                nonce);
        if (!constantEquals(hmac(requiredSecret(properties.getRunnerHmacSecret()), canonical), signature)
                || !store.reserveNonce(
                        sha256(("runner:" + nonce).getBytes(StandardCharsets.UTF_8)),
                        properties.getNonceTtl())) {
            throw unauthenticated();
        }
    }

    private byte[] requiredSecret(String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("workflow capability HMAC secret is not configured securely");
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private long parseTimestamp(String timestamp) {
        try {
            return Long.parseLong(timestamp);
        } catch (RuntimeException exception) {
            throw unauthenticated();
        }
    }

    private static String hmac(byte[] secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 unavailable", exception);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static boolean constantEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private PlatformException unauthenticated() {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, "workflow服务认证失败");
    }
}
