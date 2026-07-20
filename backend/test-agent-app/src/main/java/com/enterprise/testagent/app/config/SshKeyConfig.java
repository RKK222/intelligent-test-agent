package com.enterprise.testagent.app.config;

import com.enterprise.testagent.common.git.RsaKeyService;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SSH 加密相关 Bean 装配。
 *
 * <p>{@link RsaKeyService} 和 {@link SshKeyEncryptionService} 作为纯 Java 类
 * 在 {@code test-agent-common} 中定义，通过本配置注入 Spring 容器。</p>
 */
@Configuration
public class SshKeyConfig {

    @Bean
    public RsaKeyService rsaKeyService() {
        return new RsaKeyService();
    }

    @Bean
    public SshKeyEncryptionService sshKeyEncryptionService(RsaKeyService rsaKeyService) {
        return new SshKeyEncryptionService(rsaKeyService);
    }
}
