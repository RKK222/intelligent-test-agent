package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetReferenceWorkspaceJsoncReconcilerTest {
    private final AutomationReferenceWorkspaceJsoncReconciler reconciler =
            new AutomationReferenceWorkspaceJsoncReconciler(new ObjectMapper());
    private final AutomationReferenceWorkspaceJsoncReconciler.AssetPatch patch =
            new AutomationReferenceWorkspaceJsoncReconciler.AssetPatch("app-demo", "repo-assets",
                    "spec-assets", "{env:OPENCODE_REFERENCES_DIR}/assets/ai-agent/spec",
                    "spec", true, "设计资料", 1L);

    @Test
    void preservesCommentsAndUnrelatedReferencesWhileAddingExactPermission() {
        String source = """
                {
                  // keep user comment
                  "references": { "other": { "path": "/other", "merge": false } },
                  "permission": { "external_directory": { "*": "ask" } }
                }
                """;
        String result = reconciler.reconcileAssets(source, "app-demo", List.of(patch));

        assertThat(result).contains("// keep user comment", "\"other\"", "\"spec-assets\"")
                .contains("{env:OPENCODE_REFERENCES_DIR}/assets/ai-agent/spec/*")
                .doesNotContain("{env:OPENCODE_REFERENCES_DIR}/assets/ai-agent/*");
        assertThat(reconciler.reconcileAssets(result, "app-demo", List.of(patch))).isEqualTo(result);
    }

    @Test
    void takesOverMatchingLegacyAliasButRejectsDifferentPersonalPath() {
        String source = """
                {"references":{"spec-assets":{"path":"{env:OPENCODE_REFERENCES_DIR}/assets/ai-agent/spec",
                  "merge":false,"hidden":true}}}
                """;
        assertThat(reconciler.reconcileAssets(source, "app-demo", List.of(patch)))
                .contains("\"hidden\":true", "\"testagent-reference-kind\": \"asset\"");

        String conflicting = source.replace("/ai-agent/spec", "/other/spec");
        assertThatThrownBy(() -> reconciler.reconcileAssets(conflicting, "app-demo", List.of(patch)))
                .isInstanceOf(PlatformException.class);
    }

    @Test
    void readyBranchGenerationChangeTriggersPatchEvenWhenPathIsStable() {
        String first = reconciler.reconcileAssets("{}", "app-demo", List.of(patch));
        var switched = new AutomationReferenceWorkspaceJsoncReconciler.AssetPatch(
                patch.appId(), patch.repositoryId(), patch.alias(), patch.path(), patch.folder(),
                patch.merge(), patch.description(), 2L);
        String second = reconciler.reconcileAssets(first, "app-demo", List.of(switched));

        assertThat(second).isNotEqualTo(first).contains("\"testagent-asset-generation\":2");
        assertThat(reconciler.reconcileAssets(second, "app-demo", List.of(switched))).isEqualTo(second);
        var unavailable = new AutomationReferenceWorkspaceJsoncReconciler.AssetPatch(
                patch.appId(), patch.repositoryId(), patch.alias(), patch.path(), patch.folder(),
                patch.merge(), patch.description(), null);
        assertThat(reconciler.reconcileAssets(second, "app-demo", List.of(unavailable))).isEqualTo(second);
    }
}
