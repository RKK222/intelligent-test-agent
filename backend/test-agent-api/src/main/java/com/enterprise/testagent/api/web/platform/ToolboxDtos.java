package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.integration.toolbox.ToolboxCatalogResponse;
import com.enterprise.testagent.integration.toolbox.ToolboxClickResult;
import com.enterprise.testagent.integration.toolbox.ToolboxToolView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 工具盒子 HTTP 协议 DTO，避免 API 层直接暴露业务实现类型。 */
final class ToolboxDtos {

    private ToolboxDtos() {
    }

    record CatalogResponse(String catalogVersion, int total, int hotLimit, List<ToolResponse> tools) {

        static CatalogResponse from(ToolboxCatalogResponse response) {
            return new CatalogResponse(
                    response.catalogVersion(),
                    response.total(),
                    response.hotLimit(),
                    response.tools().stream().map(ToolResponse::from).toList());
        }
    }

    record ToolResponse(
            String toolId,
            String source,
            String sourceName,
            String sourceVersion,
            String nameZh,
            String nameEn,
            String descriptionZh,
            String category,
            String categoryLabel,
            List<String> keywords,
            String launchPath,
            long clickCount,
            Integer hotRank) {

        static ToolResponse from(ToolboxToolView tool) {
            return new ToolResponse(
                    tool.toolId(),
                    tool.source().name(),
                    tool.sourceName(),
                    tool.sourceVersion(),
                    tool.nameZh(),
                    tool.nameEn(),
                    tool.descriptionZh(),
                    tool.category().name(),
                    tool.categoryLabel(),
                    tool.keywords(),
                    tool.launchPath(),
                    tool.clickCount(),
                    tool.hotRank());
        }
    }

    record ClickRequest(
            @NotBlank(message = "eventId 不能为空")
            @Size(max = 128, message = "eventId 长度不能超过 128")
            String eventId) {
    }

    record ClickResponse(String toolId, long clickCount, boolean recorded, boolean incremented) {

        static ClickResponse from(ToolboxClickResult result) {
            return new ClickResponse(
                    result.toolId(), result.clickCount(), result.recorded(), result.incremented());
        }
    }
}
