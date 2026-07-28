package com.enterprise.testagent.integration.toolbox;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

/** 从 classpath 加载经测试锁定的工具目录，不在运行时扫描派生应用。 */
public final class ToolboxCatalogLoader {

    private static final String DEFAULT_RESOURCE = "/toolbox/catalog-v1.json";

    private ToolboxCatalogLoader() {
    }

    /** 加载首版目录；资源损坏属于部署构建错误，启动时立即失败。 */
    public static ToolboxCatalog loadDefault() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try (InputStream input = ToolboxCatalogLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("toolbox catalog resource is missing: " + DEFAULT_RESOURCE);
            }
            return mapper.readValue(input, ToolboxCatalog.class);
        } catch (IOException exception) {
            throw new IllegalStateException("toolbox catalog resource is invalid", exception);
        }
    }
}
