package com.enterprise.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitRemoteServiceTest {

    @Test
    void parseLsRemoteHeadsOutputToBranchNames() {
        GitRemoteService service = new GitRemoteService(new FakeExecutor(List.of(), new byte[0]));

        assertThat(service.parseBranches("""
                abcdef1234567890\trefs/heads/main
                abcdef1234567891\trefs/heads/feature/app-config
                abcdef1234567892\trefs/tags/v1
                """))
                .containsExactly("main", "feature/app-config");
    }

    @Test
    void listBranchesAllowsSlowEnterpriseGitServers() {
        RecordingTimeoutExecutor executor = new RecordingTimeoutExecutor("""
                abcdef1234567890\trefs/heads/master
                """);
        GitRemoteService service = new GitRemoteService(executor);

        assertThat(service.listBranches("ssh://AUTH_ADMIN@scm.example.com:29418/team/agent-config.git", "PRIVATE KEY"))
                .containsExactly("master");
        assertThat(executor.timeouts).containsExactly(Duration.ofSeconds(60));
    }

    @Test
    void parseArchiveTarOutputToTopLevelAndNestedDirectories() {
        GitRemoteService service = new GitRemoteService(new FakeExecutor(List.of(), tarWith("src/main/App.java", "docs/README.md")));

        assertThat(service.listDirectories("git@example.com:demo/repo.git", "main", "PRIVATE KEY"))
                .containsExactly("docs", "src", "src/main");
    }

    @Test
    void parseArchiveTarOutputToDirectoryAndFileTree() {
        GitRemoteService service = new GitRemoteService(new FakeExecutor(List.of(), tarWith(
                "F-COSS/W1/F1/case.md",
                "F-COSS/W1/readme.md",
                "F-COSS/W2/",
                "README.md")));

        assertThat(service.listTree("git@example.com:demo/repo.git", "main", "PRIVATE KEY"))
                .usingRecursiveComparison()
                .isEqualTo(List.of(
                        new GitRemoteService.RemoteTreeNode(
                                "F-COSS",
                                "F-COSS",
                                "directory",
                                List.of(
                                        new GitRemoteService.RemoteTreeNode(
                                                "W1",
                                                "F-COSS/W1",
                                                "directory",
                                                List.of(
                                                        new GitRemoteService.RemoteTreeNode(
                                                                "F1",
                                                                "F-COSS/W1/F1",
                                                                "directory",
                                                                List.of(new GitRemoteService.RemoteTreeNode(
                                                                        "case.md",
                                                                        "F-COSS/W1/F1/case.md",
                                                                        "file",
                                                                        List.of()))),
                                                        new GitRemoteService.RemoteTreeNode(
                                                                "readme.md",
                                                                "F-COSS/W1/readme.md",
                                                                "file",
                                                                List.of()))),
                                        new GitRemoteService.RemoteTreeNode(
                                                "W2",
                                                "F-COSS/W2",
                                                "directory",
                                                List.of()))),
                        new GitRemoteService.RemoteTreeNode(
                                "README.md",
                                "README.md",
                                "file",
                                List.of())));
    }

    @Test
    void mapGitTimeoutToPlatformError() {
        GitRemoteService service = new GitRemoteService((command, privateKey, timeout) -> {
            throw new PlatformException(ErrorCode.GIT_TIMEOUT, "Git 操作超时");
        });

        try {
            service.listBranches("git@example.com:demo/repo.git", "PRIVATE KEY");
        } catch (PlatformException exception) {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.GIT_TIMEOUT);
        }
    }

    private static byte[] tarWith(String... names) {
        byte[] tar = new byte[1024 * names.length + 1024];
        int offset = 0;
        for (String name : names) {
            byte[] header = new byte[512];
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
            header[100] = '0';
            System.arraycopy("0000644\0".getBytes(StandardCharsets.US_ASCII), 0, header, 100, 8);
            System.arraycopy("0000000\0".getBytes(StandardCharsets.US_ASCII), 0, header, 108, 8);
            System.arraycopy("0000000\0".getBytes(StandardCharsets.US_ASCII), 0, header, 116, 8);
            System.arraycopy("00000000000\0".getBytes(StandardCharsets.US_ASCII), 0, header, 124, 12);
            System.arraycopy("00000000000\0".getBytes(StandardCharsets.US_ASCII), 0, header, 136, 12);
            for (int i = 148; i < 156; i++) {
                header[i] = ' ';
            }
            header[156] = '0';
            int checksum = 0;
            for (byte b : header) {
                checksum += Byte.toUnsignedInt(b);
            }
            String checksumText = String.format("%06o\0 ", checksum);
            System.arraycopy(checksumText.getBytes(StandardCharsets.US_ASCII), 0, header, 148, 8);
            System.arraycopy(header, 0, tar, offset, 512);
            offset += 512;
            offset += 512;
        }
        return tar;
    }

    private record FakeExecutor(List<String> stdoutLines, byte[] stdoutBytes) implements GitCommandExecutor {
        @Override
        public GitCommandResult execute(List<String> command, String privateKey, Duration timeout) {
            return new GitCommandResult(0, String.join("\n", stdoutLines), stdoutBytes);
        }
    }

    private static final class RecordingTimeoutExecutor implements GitCommandExecutor {
        private final String stdout;
        private final List<Duration> timeouts = new ArrayList<>();

        private RecordingTimeoutExecutor(String stdout) {
            this.stdout = stdout;
        }

        @Override
        public GitCommandResult execute(List<String> command, String privateKey, Duration timeout) {
            timeouts.add(timeout);
            return new GitCommandResult(0, stdout, stdout.getBytes(StandardCharsets.UTF_8));
        }
    }
}
