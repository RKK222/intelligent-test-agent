package com.enterprise.testagent.integration.toolbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 锁定首版离线工具目录，避免升级或生成脚本静默引入不可用入口。 */
class ToolboxCatalogContractTest {

    private static final Pattern HAN_PATTERN = Pattern.compile("\\p{IsHan}");

    @Test
    void defaultCatalogContainsExactly193OfflineTools() {
        ToolboxCatalog catalog = ToolboxCatalogLoader.loadDefault();

        assertThat(catalog.catalogVersion()).isEqualTo("2026-07-27.it-tools-2024.10.22-7ca5933.omni-tools-0.6.0");
        assertThat(catalog.tools()).hasSize(193);
        assertThat(catalog.tools())
                .allSatisfy(tool -> {
                    assertThat(tool.toolId()).isNotBlank();
                    assertThat(tool.nameZh()).isNotBlank().containsPattern(HAN_PATTERN);
                    assertThat(tool.nameEn()).isNotBlank();
                    assertThat(tool.descriptionZh()).isNotBlank().containsPattern(HAN_PATTERN);
                    assertThat(tool.category()).isNotNull();
                    assertThat(tool.keywords()).isNotEmpty();
                    assertThat(tool.launchPath()).startsWith("/toolbox/apps/");
                });
        assertThat(catalog.tools().stream().map(ToolboxToolDefinition::toolId))
                .doesNotHaveDuplicates();
        assertThat(catalog.tools().stream().map(ToolboxToolDefinition::launchPath))
                .doesNotHaveDuplicates();
        assertThat(new HashSet<>(catalog.tools().stream().map(ToolboxToolDefinition::category).toList()))
                .allMatch(ToolboxCategory::isSupported);
        assertThat(catalog.tools())
                .noneMatch(tool -> tool.toolId().contains("camera-recorder")
                        || tool.toolId().equals("omni-tools.pdf.editor")
                        || "/".equals(tool.launchPath())
                        || tool.launchPath().endsWith("/categories"));
    }

    @Test
    void stableIdsAndLaunchPathsKeepTheirSourcePrefix() {
        ToolboxCatalog catalog = ToolboxCatalogLoader.loadDefault();

        assertThat(catalog.tools()).allSatisfy(tool -> {
            if (tool.source() == ToolboxSource.IT_TOOLS) {
                assertThat(tool.toolId()).startsWith("it-tools.");
                assertThat(tool.launchPath()).startsWith("/toolbox/apps/it-tools/");
            } else {
                assertThat(tool.toolId()).startsWith("omni-tools.");
                assertThat(tool.launchPath()).startsWith("/toolbox/apps/omni-tools/");
            }
        });
    }
}
