package com.enterprise.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.enterprise.testagent.common.error.PlatformException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import java.util.EnumSet;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RSA 密钥服务测试，覆盖浏览器 Web Crypto 与 Java JCE 的 OAEP 参数兼容性。
 */
class RsaKeyServiceTest {

    @TempDir
    Path tempDir;

    private static final OAEPParameterSpec WEB_CRYPTO_RSA_OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);

    @Test
    void decryptsWebCryptoRsaOaepSha256Payload() throws Exception {
        RsaKeyService service = new RsaKeyService();
        byte[] aesKeyBytes = new byte[32];
        for (int i = 0; i < aesKeyBytes.length; i++) {
            aesKeyBytes[i] = (byte) (i + 1);
        }

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, service.getPublicKey(), WEB_CRYPTO_RSA_OAEP_SHA256);
        byte[] encrypted = cipher.doFinal(aesKeyBytes);

        assertThat(service.decrypt(encrypted)).isEqualTo(aesKeyBytes);
    }

    @Test
    void reloadsTheSameKeyPairFromConfiguredPersistentFile() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(pair.getPrivate().getEncoded());
        Path privateKeyFile = tempDir.resolve("ssh-rsa-private.key");
        Files.writeString(
                privateKeyFile,
                "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n",
                StandardCharsets.US_ASCII);
        try {
            Files.setPosixFilePermissions(
                    privateKeyFile,
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows 测试环境没有 POSIX mode，服务实现同样会跳过权限检查。
        }

        RsaKeyService first = new RsaKeyService(privateKeyFile);
        RsaKeyService restarted = new RsaKeyService(privateKeyFile);

        assertThat(restarted.getPublicKey().getEncoded()).isEqualTo(first.getPublicKey().getEncoded());
        byte[] plaintext = "persistent-key".getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, restarted.getPublicKey(), WEB_CRYPTO_RSA_OAEP_SHA256);
        assertThat(first.decrypt(cipher.doFinal(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void rejectsConfiguredPrivateKeyExposedToGroupOnPosixFileSystem() throws Exception {
        Path privateKeyFile = tempDir.resolve("exposed-rsa-private.key");
        Files.writeString(privateKeyFile, "not-read-because-permissions-fail-first", StandardCharsets.US_ASCII);
        assumeTrue(Files.getFileStore(privateKeyFile).supportsFileAttributeView("posix"));
        Files.setPosixFilePermissions(
                privateKeyFile,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.GROUP_READ));

        assertThatThrownBy(() -> new RsaKeyService(privateKeyFile))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("permissions must be 0600");
    }
}
