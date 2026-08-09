package com.enterprise.testagent.app.config;

import com.enterprise.testagent.system.management.externalapi.ExternalApiCredentialRegistry;
import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Flyway 完成后、实例就绪前严格加载全部外部 API 凭据。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ExternalApiCredentialStartupRunner implements ApplicationRunner {

    private final ExternalApiCredentialRegistry registry;

    public ExternalApiCredentialStartupRunner(ExternalApiCredentialRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void run(ApplicationArguments args) {
        registry.loadOnStartup();
    }
}
