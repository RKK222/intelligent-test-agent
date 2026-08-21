package com.enterprise.testagent.common.localclient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/** 客户端和平台共用的可信下载根、路径、摘要及清单签名校验。 */
public final class LocalClientDownloadTrust {

    private final URI trustedRoot;
    private final PublicKey publicKey;

    public LocalClientDownloadTrust(URI trustedRoot, PublicKey publicKey) {
        this.trustedRoot = validateRoot(trustedRoot);
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null");
    }

    public URI trustedRoot() {
        return trustedRoot;
    }

    /** 只允许清单给出可信根下的规范相对路径，拒绝绝对 URL、编码路径和目录穿越。 */
    public URI resolve(String relativePath) {
        if (relativePath == null
                || relativePath.isBlank()
                || relativePath.startsWith("/")
                || relativePath.contains("%")
                || relativePath.contains("\\")
                || relativePath.contains("//")
                || !relativePath.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")) {
            throw new IllegalArgumentException("download path is invalid");
        }
        for (String segment : relativePath.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("download path contains an unsafe segment");
            }
        }
        URI resolved = trustedRoot.resolve(relativePath).normalize();
        if (!sameOrigin(trustedRoot, resolved)
                || resolved.getRawPath() == null
                || !resolved.getRawPath().startsWith(trustedRoot.getRawPath())
                || resolved.getRawQuery() != null
                || resolved.getRawFragment() != null
                || resolved.getUserInfo() != null) {
            throw new IllegalArgumentException("download path escapes trusted root");
        }
        return resolved;
    }

    public void verifySignature(byte[] content, byte[] signatureBytes) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(signatureBytes, "signatureBytes must not be null");
        try {
            String algorithm = switch (publicKey.getAlgorithm().toUpperCase(Locale.ROOT)) {
                case "RSA" -> "SHA256withRSA";
                case "EC", "ECDSA" -> "SHA256withECDSA";
                default -> throw new SecurityException("unsupported signing public key algorithm");
            };
            Signature verifier = Signature.getInstance(algorithm);
            verifier.initVerify(publicKey);
            verifier.update(content);
            if (!verifier.verify(signatureBytes)) {
                throw new SecurityException("release signature verification failed");
            }
        } catch (GeneralSecurityException exception) {
            throw new SecurityException("release signature verification failed", exception);
        }
    }

    /** 大制品使用流式签名校验，避免把 JDK/OpenCode 归档整体读入堆内存。 */
    public void verifySignature(Path content, byte[] signatureBytes) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(signatureBytes, "signatureBytes must not be null");
        try (InputStream input = Files.newInputStream(content)) {
            Signature verifier = newSignatureVerifier();
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    verifier.update(buffer, 0, read);
                }
            }
            if (!verifier.verify(signatureBytes)) {
                throw new SecurityException("release signature verification failed");
            }
        } catch (IOException | GeneralSecurityException exception) {
            throw new SecurityException("release signature verification failed", exception);
        }
    }

    public static PublicKey parsePublicKeyPem(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("signing public key is required");
        }
        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] encoded = Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.US_ASCII));
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            for (String algorithm : new String[] {"RSA", "EC"}) {
                try {
                    return KeyFactory.getInstance(algorithm).generatePublic(keySpec);
                } catch (GeneralSecurityException ignored) {
                    // 继续尝试另一种平台允许的公钥算法。
                }
            }
            throw new IllegalArgumentException("signing public key algorithm is unsupported");
        } catch (IllegalArgumentException exception) {
            throw exception;
        }
    }

    public static String sha256(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static String sha256(Path content) {
        Objects.requireNonNull(content, "content must not be null");
        try (InputStream input = Files.newInputStream(content)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("failed to calculate release SHA-256", exception);
        }
    }

    public static void requireSha256(byte[] content, String expected) {
        if (expected == null || !expected.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expected SHA-256 is invalid");
        }
        byte[] actualBytes = sha256(content).getBytes(StandardCharsets.US_ASCII);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(actualBytes, expectedBytes)) {
            throw new SecurityException("release SHA-256 verification failed");
        }
    }

    public static void requireSha256(Path content, String expected) {
        requireExpectedSha256(expected);
        byte[] actualBytes = sha256(content).getBytes(StandardCharsets.US_ASCII);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(actualBytes, expectedBytes)) {
            throw new SecurityException("release SHA-256 verification failed");
        }
    }

    private Signature newSignatureVerifier() throws GeneralSecurityException {
        String algorithm = switch (publicKey.getAlgorithm().toUpperCase(Locale.ROOT)) {
            case "RSA" -> "SHA256withRSA";
            case "EC", "ECDSA" -> "SHA256withECDSA";
            default -> throw new GeneralSecurityException("unsupported signing public key algorithm");
        };
        Signature verifier = Signature.getInstance(algorithm);
        verifier.initVerify(publicKey);
        return verifier;
    }

    private static void requireExpectedSha256(String expected) {
        if (expected == null || !expected.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expected SHA-256 is invalid");
        }
    }

    private static URI validateRoot(URI root) {
        Objects.requireNonNull(root, "trustedRoot must not be null");
        String scheme = root.getScheme();
        if (root.getHost() == null
                || root.getRawAuthority() == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || root.getUserInfo() != null
                || root.getRawQuery() != null
                || root.getRawFragment() != null
                || root.getRawPath() == null) {
            throw new IllegalArgumentException("trusted download root is invalid");
        }
        String path = root.getRawPath().endsWith("/") ? root.getRawPath() : root.getRawPath() + "/";
        URI normalized = URI.create(scheme.toLowerCase(Locale.ROOT)
                + "://"
                + root.getRawAuthority()
                + path).normalize();
        if (!normalized.getRawPath().equals(path)) {
            throw new IllegalArgumentException("trusted download root must be canonical");
        }
        return normalized;
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
