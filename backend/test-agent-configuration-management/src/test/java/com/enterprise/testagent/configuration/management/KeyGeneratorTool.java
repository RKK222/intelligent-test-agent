package com.enterprise.testagent.configuration.management;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 一次性工具：生成 AES-256 密钥，输出到 stdout。
 * 仅在本地开发环境使用，不作为正式代码交付。
 */
public final class KeyGeneratorTool {

    public static void main(String[] args) {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[32]; // AES-256
        random.nextBytes(key);
        String encoded = Base64.getEncoder().encodeToString(key);
        System.out.println("=== GENERATED AES-256 KEY ===");
        System.out.println(encoded);
        System.out.println("=== END ===");
    }

    private KeyGeneratorTool() {
    }
}
