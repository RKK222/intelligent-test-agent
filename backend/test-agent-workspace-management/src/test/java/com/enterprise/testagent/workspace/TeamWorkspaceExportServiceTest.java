package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceStatus;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.enterprise.testagent.domain.team.TeamMembershipState;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import com.enterprise.testagent.domain.team.TeamWorkspaceExportItem;
import com.enterprise.testagent.domain.team.TeamWorkspaceExportJob;
import com.enterprise.testagent.domain.team.TeamWorkspaceExportRepository;
import com.enterprise.testagent.domain.team.TeamWorkspaceExportStatus;
import com.enterprise.testagent.domain.team.TeamWorkspaceQueryRepository;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.MemberContributionView;
import com.enterprise.testagent.domain.team.TeamWorkspaceViews.PersonalWorkspaceView;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 团队整组导出敏感文件过滤契约。 */
class TeamWorkspaceExportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void excludesSecretsButKeepsExplicitExamples() {
        assertThat(TeamWorkspaceExportService.sensitiveReason(".env")).isEqualTo("ENV_SECRET");
        assertThat(TeamWorkspaceExportService.sensitiveReason("config/.env.production"))
                .isEqualTo("ENV_SECRET");
        assertThat(TeamWorkspaceExportService.sensitiveReason(".env.example")).isNull();
        assertThat(TeamWorkspaceExportService.sensitiveReason(".env.local.sample")).isNull();
        assertThat(TeamWorkspaceExportService.sensitiveReason(".npmrc")).isEqualTo("CREDENTIAL_FILE");
        assertThat(TeamWorkspaceExportService.sensitiveReason("credentials.json"))
                .isEqualTo("CREDENTIAL_FILE");
        assertThat(TeamWorkspaceExportService.sensitiveReason("service-account-prod.json"))
                .isEqualTo("CREDENTIAL_FILE");
        assertThat(TeamWorkspaceExportService.sensitiveReason("keys/id_ed25519"))
                .isEqualTo("CREDENTIAL_FILE");
        assertThat(TeamWorkspaceExportService.sensitiveReason("keys/client.p12"))
                .isEqualTo("PRIVATE_KEY_OR_KEYSTORE");
        assertThat(TeamWorkspaceExportService.sensitiveReason(".opencode/agents/review.md")).isNull();
    }

    @Test
    void createsOneFilteredZipWithManifestAndOpencodeFiles() throws Exception {
        Path worktree = Files.createDirectories(tempDir.resolve("worktree"));
        Files.writeString(worktree.resolve("visible.txt"), "visible", StandardCharsets.UTF_8);
        Files.writeString(worktree.resolve(".env"), "SECRET=hidden", StandardCharsets.UTF_8);
        Path opencode = Files.createDirectories(worktree.resolve(".opencode"));
        Files.writeString(opencode.resolve("agent.md"), "agent", StandardCharsets.UTF_8);

        TeamWorkspaceQueryRepository queries = mock(TeamWorkspaceQueryRepository.class);
        TeamWorkspaceExportShardGateway gateway = mock(TeamWorkspaceExportShardGateway.class);
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        InMemoryExports exports = new InMemoryExports();
        UserId memberId = new UserId("member-zip");
        PersonalWorkspace personal = personal(worktree, memberId);
        User member = new User(
                memberId, "auth-member", "member", "hash", null, null, null,
                UserStatus.ACTIVE, now(), now());
        when(queries.findContributions(false, memberId, "version-zip")).thenReturn(List.of(
                new MemberContributionView(
                        member, TeamMembershipState.CURRENT,
                        List.of(new PersonalWorkspaceView(personal, "server-a")))));
        TeamWorkspaceExportService service = new TeamWorkspaceExportService(
                queries, exports, parameters, new WorkspaceServerIdentity("server-a"),
                new ObjectMapper(), Clock.fixed(now(), ZoneOffset.UTC), gateway);
        try {
            var created = service.create(
                    TeamScopeMode.MY_TEAM, memberId, memberId, false, "version-zip");
            TeamWorkspaceExportJob completed = waitUntilComplete(exports, created.exportId());
            assertThat(completed.status())
                    .as("errorCode=%s, errorMessage=%s, items=%s",
                            completed.errorCode(), completed.errorMessage(), exports.findItems(created.exportId()))
                    .isEqualTo(TeamWorkspaceExportStatus.READY);

            Path archive = service.artifact(created.exportId(), memberId.value());
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                List<String> names = zip.stream().map(entry -> entry.getName()).toList();
                assertThat(names).anyMatch(name -> name.endsWith("/visible.txt"));
                assertThat(names).anyMatch(name -> name.endsWith("/.opencode/agent.md"));
                assertThat(names).noneMatch(name -> name.endsWith("/.env"));
                String manifest = new String(
                        zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(), StandardCharsets.UTF_8);
                assertThat(manifest).contains(".env", "ENV_SECRET", "SUCCEEDED").doesNotContain("SECRET=hidden");
            }
            service.invalidateTeamMember(memberId.value(), memberId.value());
            assertThat(exports.findJob(created.exportId()).orElseThrow().status())
                    .isEqualTo(TeamWorkspaceExportStatus.CANCELLED);
            assertThat(Files.exists(archive)).isFalse();
        } finally {
            service.close();
        }
    }

    @Test
    void rejectsSparseWorktreeAboveTwoGiBBeforeCreatingJob() throws Exception {
        Path worktree = Files.createDirectories(tempDir.resolve("oversized-worktree"));
        try (RandomAccessFile sparse = new RandomAccessFile(worktree.resolve("oversized.bin").toFile(), "rw")) {
            sparse.setLength(TeamWorkspaceExportService.MAX_UNCOMPRESSED_BYTES + 1L);
        }

        TeamWorkspaceQueryRepository queries = mock(TeamWorkspaceQueryRepository.class);
        TeamWorkspaceExportShardGateway gateway = mock(TeamWorkspaceExportShardGateway.class);
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        InMemoryExports exports = new InMemoryExports();
        UserId memberId = new UserId("member-limit");
        PersonalWorkspace personal = personal(worktree, memberId);
        User member = new User(
                memberId, "auth-limit", "member-limit", "hash", null, null, null,
                UserStatus.ACTIVE, now(), now());
        when(queries.findContributions(false, memberId, "version-zip")).thenReturn(List.of(
                new MemberContributionView(
                        member, TeamMembershipState.CURRENT,
                        List.of(new PersonalWorkspaceView(personal, "server-a")))));
        TeamWorkspaceExportService service = new TeamWorkspaceExportService(
                queries, exports, parameters, new WorkspaceServerIdentity("server-a"),
                new ObjectMapper(), Clock.fixed(now(), ZoneOffset.UTC), gateway);
        try {
            assertThatThrownBy(() -> service.create(
                    TeamScopeMode.MY_TEAM, memberId, memberId, false, "version-zip"))
                    .isInstanceOf(PlatformException.class)
                    .hasMessageContaining("2 GiB");
            assertThat(exports.jobs).isEmpty();
        } finally {
            service.close();
        }
    }

    private PersonalWorkspace personal(Path root, UserId memberId) {
        return new PersonalWorkspace(
                new PersonalWorkspaceId("pw-zip"), new ApplicationWorkspaceVersionId("version-zip"),
                new ApplicationId("app-zip"), new ApplicationWorkspaceId("awp_zip"), memberId,
                "default", "feature", root.toString(), root.toString(), new WorkspaceId("wrk_runtime_zip"),
                "a".repeat(40), ManagedWorkspaceStatus.ACTIVE, now(), now());
    }

    private TeamWorkspaceExportJob waitUntilComplete(InMemoryExports exports, String exportId) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            TeamWorkspaceExportJob job = exports.findJob(exportId).orElseThrow();
            if (job.status() != TeamWorkspaceExportStatus.QUEUED
                    && job.status() != TeamWorkspaceExportStatus.RUNNING) return job;
            Thread.sleep(20L);
        }
        throw new AssertionError("团队导出未在测试时限内完成");
    }

    private Instant now() {
        return Instant.parse("2026-09-21T08:00:00Z");
    }

    /** 测试只需模拟同一协调节点共享关系库的原子领取和状态写回。 */
    private static final class InMemoryExports implements TeamWorkspaceExportRepository {
        private final Map<String, TeamWorkspaceExportJob> jobs = new LinkedHashMap<>();
        private final Map<String, TeamWorkspaceExportItem> items = new LinkedHashMap<>();

        @Override public synchronized void insertJob(TeamWorkspaceExportJob job) { jobs.put(job.exportId(), job); }
        @Override public synchronized void insertItem(TeamWorkspaceExportItem item) {
            items.put(item.exportItemId(), item);
        }
        @Override public synchronized Optional<TeamWorkspaceExportJob> findJob(String exportId) {
            return Optional.ofNullable(jobs.get(exportId));
        }
        @Override public synchronized List<TeamWorkspaceExportItem> findItems(String exportId) {
            return items.values().stream().filter(item -> item.exportId().equals(exportId)).toList();
        }
        @Override public synchronized void updateJob(TeamWorkspaceExportJob job) { jobs.put(job.exportId(), job); }
        @Override public synchronized void updateItem(TeamWorkspaceExportItem item) {
            items.put(item.exportItemId(), item);
        }
        @Override public synchronized boolean claimItem(
                String exportItemId, String leaseOwner, String leaseToken, Instant now, Instant leaseExpiresAt) {
            TeamWorkspaceExportItem item = items.get(exportItemId);
            return item != null && "QUEUED".equals(item.status());
        }
        @Override public synchronized boolean cancel(String exportId, String actorUserId, Instant now) { return true; }
        @Override public synchronized List<TeamWorkspaceExportJob> findActiveByTeamMember(
                String ownerUserId, String memberUserId) {
            return jobs.values().stream()
                    .filter(job -> ownerUserId.equals(job.ownerUserId()))
                    .filter(job -> List.of(
                            TeamWorkspaceExportStatus.QUEUED,
                            TeamWorkspaceExportStatus.RUNNING,
                            TeamWorkspaceExportStatus.READY,
                            TeamWorkspaceExportStatus.PARTIAL_READY).contains(job.status()))
                    .filter(job -> items.values().stream().anyMatch(item ->
                            item.exportId().equals(job.exportId()) && item.userId().equals(memberUserId)))
                    .toList();
        }
        @Override public synchronized List<TeamWorkspaceExportJob> findExpiredReady(
                String coordinatorLinuxServerId, Instant now, int limit) {
            return List.of();
        }
    }
}
