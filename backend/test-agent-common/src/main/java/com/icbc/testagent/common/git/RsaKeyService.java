package com.icbc.testagent.common.git;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * 非对称密钥服务：加载 RSA 私钥文件（classpath:rsa-private.key），推导出公钥，
 * 提供公钥导出和私钥解密能力。
 *
 * <p>私钥文件不存在时自动生成临时密钥对（开发环境兜底），
 * 生产环境务必部署 PEM 格式的 PKCS8 RSA 私钥文件。</p>
 *
 * <p>使用方需通过 {@code @Bean} 或 {@code new RsaKeyService()} 实例化。</p>
 */
public class RsaKeyService {

    private static final Logger LOGGER = Logger.getLogger(RsaKeyService.class.getName());
    private static final String RSA_ALGORITHM = "RSA";
    private static final String CIPHER_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final OAEPParameterSpec WEB_CRYPTO_OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String publicKeyBase64;

    public RsaKeyService() {
        try {
            byte[] pemBytes = readResourceBytes("rsa-private.key");
            if (pemBytes != null) {
                String pem = new String(pemBytes, StandardCharsets.UTF_8);
                this.privateKey = parsePkcs8PrivateKey(pem);
                this.publicKey = derivePublicKey(privateKey);
                LOGGER.info("RSA private key loaded from classpath:rsa-private.key");
            } else {
                LOGGER.warning("classpath:rsa-private.key not found; auto-generating ephemeral RSA key pair. "
                        + "For production, place a PEM-encoded PKCS8 private key at classpath:rsa-private.key");
                java.security.KeyPairGenerator gen = java.security.KeyPairGenerator.getInstance(RSA_ALGORITHM);
                gen.initialize(2048, new java.security.SecureRandom());
                java.security.KeyPair pair = gen.generateKeyPair();
                this.privateKey = pair.getPrivate();
                this.publicKey = pair.getPublic();
                LOGGER.warning("Auto-generated RSA key pair is ephemeral - keys will change on restart");
            }
            this.publicKeyBase64 = Base64.getEncoder().encodeToString(this.publicKey.getEncoded());
            LOGGER.info("RsaKeyService initialized, public key (SPKI Base64): " + this.publicKeyBase64);
        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "RSA key pair initialization failed", Map.of(), e);
        }
    }

    /** 返回 RSA 公钥的 SPKI 编码（X.509 SubjectPublicKeyInfo）的 Base64 字符串。 */
    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    /** 使用 RSA 私钥解密数据。 */
    public byte[] decrypt(byte[] encryptedData) {
        try {
            return decrypt(encryptedData, WEB_CRYPTO_OAEP_SHA256);
        } catch (PlatformException e) {
            // 兼容历史 Java 夹具或旧密文：SunJCE transformation 默认 MGF1 参数可能不是 SHA-256。
            return decrypt(encryptedData, null);
        } catch (Exception e) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "RSA decryption failed", Map.of(), e);
        }
    }

    /** 返回公钥对象，供测试中模拟前端加密。 */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    private byte[] decrypt(byte[] encryptedData, AlgorithmParameterSpec parameterSpec) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            if (parameterSpec == null) {
                cipher.init(Cipher.DECRYPT_MODE, privateKey);
            } else {
                cipher.init(Cipher.DECRYPT_MODE, privateKey, parameterSpec);
            }
            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "RSA decryption failed", Map.of(), e);
        }
    }

    private static PrivateKey parsePkcs8PrivateKey(String pem) {
        try {
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Failed to parse RSA private key PEM", Map.of(), e);
        }
    }

    private static PublicKey derivePublicKey(PrivateKey privateKey) {
        try {
            RSAPrivateCrtKey rsaPrivate = (RSAPrivateCrtKey) privateKey;
            RSAPublicKeySpec publicKeySpec =
                    new RSAPublicKeySpec(rsaPrivate.getModulus(), rsaPrivate.getPublicExponent());
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePublic(publicKeySpec);
        } catch (Exception e) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR,
                    "Failed to derive RSA public key from private key", Map.of(), e);
        }
    }

    private static byte[] readResourceBytes(String resourcePath) {
        try (InputStream is = openResourceStream(resourcePath)) {
            if (is == null) return null;
            return is.readAllBytes();
        } catch (Exception e) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR,
                    "Failed to read resource: " + resourcePath, Map.of("path", resourcePath), e);
        }
    }

    private static InputStream openResourceStream(String resourcePath) {
        InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath);
        if (is == null) {
            is = RsaKeyService.class.getClassLoader()
                    .getResourceAsStream(resourcePath);
        }
        return is;
    }
}
