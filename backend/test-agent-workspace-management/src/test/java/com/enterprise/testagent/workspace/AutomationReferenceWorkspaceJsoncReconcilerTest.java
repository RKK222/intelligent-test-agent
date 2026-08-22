package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AutomationReferenceWorkspaceJsoncReconcilerTest {

    private final AutomationReferenceWorkspaceJsoncReconciler reconciler =
            new AutomationReferenceWorkspaceJsoncReconciler(new ObjectMapper());

    @Test
    void reconcilesOneReferencePerRepositoryAndPreservesUserCommentsAndFields() {
        String oldPath = "{env:OPENCODE_REFERENCES_DIR}/automation/app/old/1/tests";
        String nextPath = "{env:OPENCODE_REFERENCES_DIR}/automation/app/repo/2/tests/e2e";
        String source = """
                {
                  // 用户自己的模型配置必须保留
                  "model": "provider/model",
                  "references": {
                    "old-branch": {
                      "path": "%s",
                      "testagent-reference-kind": "automation",
                      "testagent-automation-app-id": "app_demo",
                      "testagent-automation-repository-id": "repo_auto",
                      "testagent-automation-generation": 1
                    },
                    "user-docs": { "path": "/safe/docs", "merge": true }
                  },
                  "permission": {
                    "external_directory": {
                      "%s/*": "allow",
                      "*": "ask"
                    }
                  },
                  "future-field": { "enabled": true }
                }
                """.formatted(oldPath, oldPath);

        String output = reconciler.reconcile(source, "app_demo", List.of(new AutomationReferenceWorkspaceJsoncReconciler.Patch(
                "app_demo", "repo_auto", 2L, "automation-repo", nextPath, "e2e", "只读自动化引用")));

        assertThat(output).contains("// 用户自己的模型配置必须保留");
        assertThat(output).contains("\"future-field\": { \"enabled\": true }");
        assertThat(output).contains("\"user-docs\": { \"path\": \"/safe/docs\", \"merge\": true }");
        assertThat(output).contains("\"automation-repo\"");
        assertThat(output).contains("\"testagent-automation-generation\":2");
        assertThat(output).doesNotContain("old-branch").doesNotContain(oldPath + "/*");
        assertThat(output.indexOf("\"*\": \"ask\""))
                .isLessThan(output.indexOf("\"" + nextPath + "/*\": \"allow\""));
    }

    @Test
    void unavailableRepositoryRemovesOnlyCurrentApplicationsManagedReference() {
        String source = """
                {
                  "references": {
                    "mine": {
                      "path": "/old",
                      "testagent-reference-kind": "automation",
                      "testagent-automation-app-id": "app_demo"
                    },
                    "other-app": {
                      "path": "/other",
                      "testagent-reference-kind": "automation",
                      "testagent-automation-app-id": "app_other"
                    }
                  },
                  "permission": { "external_directory": { "/old/*": "allow", "/other/*": "allow" } }
                }
                """;

        String output = reconciler.reconcile(source, "app_demo", List.of());

        assertThat(output).doesNotContain("\"mine\"").doesNotContain("/old/*");
        assertThat(output).contains("\"other-app\"").contains("/other/*");
    }

    @Test
    void sameInputIsIdempotent() {
        var patch = new AutomationReferenceWorkspaceJsoncReconciler.Patch(
                "app_demo", "repo_auto", 3L, "automation-repo",
                "{env:OPENCODE_REFERENCES_DIR}/automation/app/repo/3", "repo", "只读自动化引用");
        String first = reconciler.reconcile("", "app_demo", List.of(patch));

        assertThat(reconciler.reconcile(first, "app_demo", List.of(patch))).isEqualTo(first);
    }
}
