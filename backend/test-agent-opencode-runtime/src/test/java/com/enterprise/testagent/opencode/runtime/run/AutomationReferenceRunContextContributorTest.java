package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.agent.runtime.AgentRunPromptContext;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceReferenceCatalog;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AutomationReferenceRunContextContributorTest {

    @Test
    void contributesPinnedReadonlyManifestForCommandsAndEscapesDisplayValues() {
        AutomationWorkspaceReferenceCatalog catalog = new StubCatalog(List.of(new AutomationWorkspaceReferenceCatalog.Reference(
                new ApplicationId("app_demo"),
                new ApplicationWorkspaceId("awp_auto"),
                new ApplicationWorkspaceVersionId("awv_20260819"),
                "API 自动化",
                "API <自动化>",
                "automation",
                "20260819",
                "release/&",
                "abc123",
                "/srv/automation/20260819")));
        AutomationReferenceRunContextContributor contributor = new AutomationReferenceRunContextContributor(catalog);

        String prompt = contributor.contribute(new AgentRunPromptContext(run(new UserId("usr_1")), "/test", true, "trace_test"))
                .orElseThrow();

        assertThat(prompt)
                .contains("readonly=\"true\"")
                .contains("API &lt;自动化&gt;")
                .contains("release/&amp;")
                .contains("/srv/automation/20260819")
                .contains("禁止修改、删除或提交");
    }

    @Test
    void omitsManifestWhenRunHasNoAuthenticatedUserOrNoReadyReference() {
        AutomationReferenceRunContextContributor contributor = new AutomationReferenceRunContextContributor(new StubCatalog(List.of()));

        assertThat(contributor.contribute(new AgentRunPromptContext(run(new UserId("usr_1")), "hello", false, "trace")))
                .isEmpty();
        assertThat(contributor.contribute(new AgentRunPromptContext(run(null), "hello", false, "trace")))
                .isEmpty();
    }

    private Run run(UserId userId) {
        Instant now = Instant.parse("2026-08-19T05:00:00Z");
        return new Run(
                new RunId("run_1"),
                new SessionId("ses_1"),
                new WorkspaceId("wrk_1"),
                RunStatus.PENDING,
                now,
                now,
                "trace_test",
                null,
                null,
                null,
                null,
                userId,
                null,
                null);
    }

    private record StubCatalog(List<Reference> references) implements AutomationWorkspaceReferenceCatalog {
        @Override
        public Resolution resolveActive(UserId userId, WorkspaceId hostWorkspaceId) {
            return new Resolution(new ApplicationId("app_demo"), references.size(), references, List.of());
        }

        @Override
        public Reference resolveVersion(
                UserId userId,
                WorkspaceId hostWorkspaceId,
                ApplicationWorkspaceId applicationWorkspaceId,
                ApplicationWorkspaceVersionId versionId) {
            return references.stream().filter(reference -> reference.versionId().equals(versionId)).findFirst().orElseThrow();
        }
    }
}
