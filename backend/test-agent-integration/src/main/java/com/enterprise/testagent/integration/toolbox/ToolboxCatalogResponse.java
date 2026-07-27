package com.enterprise.testagent.integration.toolbox;

import java.util.List;

/** 工具盒子目录业务响应，固定携带热门榜上限。 */
public record ToolboxCatalogResponse(String catalogVersion, int total, int hotLimit, List<ToolboxToolView> tools) {
}
