package com.enterprise.testagent.domain.hub;

import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.List;
import java.util.Map;

/**
 * 受保护 Agent/Skill 的只读解析端口；运行模块只依赖不可变修订，不感知 Hub 存储实现。
 */
public interface ProtectedAgentDefinitionResolver {

    /** 返回当前用户可选择的已发布 Agent，不携带任何正文。 */
    List<CatalogItem> listCatalog(UserId userId, WorkspaceId workspaceId);

    /** 解析一个已发布不可变修订及其冻结 Skill 依赖；未发布修订必须失败关闭。 */
    Definition resolve(UserId userId, WorkspaceId workspaceId, String revisionId);

    record CatalogItem(
            String selectionId,
            String revisionId,
            String displayName,
            String description,
            String contentSha256,
            CatalogSource source) {

        public CatalogItem(
                String selectionId,
                String revisionId,
                String displayName,
                String description,
                String contentSha256) {
            this(selectionId, revisionId, displayName, description, contentSha256, CatalogSource.APPLICATION_HUB);
        }
    }

    enum CatalogSource {
        PUBLIC_GIT,
        APPLICATION_HUB
    }

    record Definition(
            String assetId,
            String technicalId,
            String revisionId,
            String artifactSha256,
            String contentSha256,
            String displayName,
            Map<String, String> agentFiles,
            List<SkillDefinition> skills) {

        public Definition {
            agentFiles = agentFiles == null ? Map.of() : Map.copyOf(agentFiles);
            skills = skills == null ? List.of() : List.copyOf(skills);
        }
    }

    record SkillDefinition(
            String assetId,
            String technicalId,
            String revisionId,
            String artifactSha256,
            String contentSha256,
            String displayName,
            Map<String, String> files) {

        public SkillDefinition {
            files = files == null ? Map.of() : Map.copyOf(files);
        }
    }
}
