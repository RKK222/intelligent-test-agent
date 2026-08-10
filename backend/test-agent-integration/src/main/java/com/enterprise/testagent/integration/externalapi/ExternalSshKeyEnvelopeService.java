package com.enterprise.testagent.integration.externalapi;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 按固定 TAEK1 协议用调用 API Key 派生密钥并封装 SSH 私钥。 */
@Service
public class ExternalSshKeyEnvelopeService {

    private static final byte[] HKDF_INFO =
            "test-agent/external-api/ssh-key/v1".getBytes(StandardCharsets.UTF_8);
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;
    private final Clock clock;

    // 存在多个构造器时必须显式标注生产构造器，否则 Spring 会回退查找无参构造器并启动失败。
    @Autowired
    public ExternalSshKeyEnvelopeService(ObjectMapper objectMapper) {
        this(objectMapper, new SecureRandom(), Clock.systemUTC());
    }

    ExternalSshKeyEnvelopeService(ObjectMapper objectMapper, SecureRandom secureRandom, Clock clock) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** 私钥明文只在本方法局部载荷和序列化字节中短暂存在。 */
    public ExternalSshKeyEnvelope encrypt(
            String toolCode,
            String apiKey,
            String unifiedAuthId,
            String sshKeyId,
            String name,
            String fingerprint,
            String privateKey,
            String traceId) {
        try {
            byte[] salt = randomBytes(16);
            byte[] nonce = randomBytes(12);
            byte[] key = hkdfSha256(apiKey.getBytes(StandardCharsets.UTF_8), salt);
            PrivateKeyPayload payload = new PrivateKeyPayload(
                    1, unifiedAuthId, sshKeyId, name, fingerprint, privateKey, Instant.now(clock).toString());
            byte[] plaintext = objectMapper.writeValueAsBytes(payload);
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            aes.updateAAD(aad(toolCode, unifiedAuthId, traceId));
            byte[] ciphertext = aes.doFinal(plaintext);
            return new ExternalSshKeyEnvelope(
                    "TAEK1", "HKDF-SHA256", "AES-256-GCM",
                    BASE64_URL.encodeToString(salt),
                    BASE64_URL.encodeToString(nonce),
                    BASE64_URL.encodeToString(ciphertext));
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR, "SSH Key 响应加密失败", Map.of(), exception);
        }
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private static byte[] hkdfSha256(byte[] inputKey, byte[] salt) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] pseudoRandomKey = mac.doFinal(inputKey);
        mac.init(new SecretKeySpec(pseudoRandomKey, "HmacSHA256"));
        mac.update(HKDF_INFO);
        mac.update((byte) 1);
        return mac.doFinal();
    }

    private static byte[] aad(String toolCode, String unifiedAuthId, String traceId) {
        return ("TAEK1\n" + toolCode + "\n" + unifiedAuthId + "\n" + traceId)
                .getBytes(StandardCharsets.UTF_8);
    }

    /** 固定字段顺序也是跨语言测试向量的一部分。 */
    private record PrivateKeyPayload(
            int schemaVersion,
            String unifiedAuthId,
            String sshKeyId,
            String name,
            String fingerprint,
            String privateKey,
            String issuedAt) {
    }
}
