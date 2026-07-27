package com.enterprise.testagent.integration.toolbox;

import java.util.List;

/** 叠加累计点击和热门排名后的目录展示模型。 */
public record ToolboxToolView(
        String toolId,
        ToolboxSource source,
        String sourceName,
        String sourceVersion,
        String nameZh,
        String nameEn,
        String descriptionZh,
        ToolboxCategory category,
        String categoryLabel,
        List<String> keywords,
        String launchPath,
        long clickCount,
        Integer hotRank) {
}
