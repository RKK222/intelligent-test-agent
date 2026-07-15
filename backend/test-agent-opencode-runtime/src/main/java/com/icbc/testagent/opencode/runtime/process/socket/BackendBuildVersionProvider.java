package com.icbc.testagent.opencode.runtime.process.socket;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/**
 * 从 Spring Boot 构建元数据读取 Java 产物版本；元数据缺失时不以启动时间伪造版本。
 */
@Component
public class BackendBuildVersionProvider {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("'V'yyyyMMdd.HHmmss", Locale.ROOT)
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final BuildProperties buildProperties;

    /**
     * 生产环境由 Spring 按需提供 build-info；开发期直接从 IDE 启动时允许该 Bean 缺失。
     */
    @Autowired
    public BackendBuildVersionProvider(ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this(buildPropertiesProvider.getIfAvailable());
    }

    BackendBuildVersionProvider(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    /**
     * 返回北京时间构建版本，缺少构建元数据时返回 {@code null}。
     */
    public String buildVersion() {
        if (buildProperties == null || buildProperties.getTime() == null) {
            return null;
        }
        return FORMATTER.format(buildProperties.getTime());
    }
}
