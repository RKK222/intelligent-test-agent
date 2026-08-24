package com.enterprise.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.git.GitRemoteService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitCloneCacheServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void parseLsTreeOutputToDirectoryAndFileTree() {
        String lsTreeOutput = """
                040000 tree d8329fc1cc938780ffdd9f94e0d364e0ea74f579\tsrc
                040000 tree e9329fc1cc938780ffdd9f94e0d364e0ea74f579\tsrc/main
                100644 blob e69de29bb2d1d6434b8b29ae775ad8c2e48c5391\tsrc/main/App.java
                100644 blob aabbccddeeff00112233445566778899aabbccdd\tREADME.md
                """;

        List<GitRemoteService.RemoteTreeNode> tree = GitCloneCacheService.parseLsTreeOutput(lsTreeOutput);

        assertThat(tree).usingRecursiveComparison().isEqualTo(List.of(
                new GitRemoteService.RemoteTreeNode(
                        "src",
                        "src",
                        "directory",
                        List.of(new GitRemoteService.RemoteTreeNode(
                                "main",
                                "src/main",
                                "directory",
                                List.of(new GitRemoteService.RemoteTreeNode(
                                        "App.java",
                                        "src/main/App.java",
                                        "file",
                                        List.of()))))),
                new GitRemoteService.RemoteTreeNode(
                        "README.md",
                        "README.md",
                        "file",
                        List.of())));
    }

    @Test
    void refreshTreeFetchesRemoteEvenWhenCachedTreeHasNotExpired() throws Exception {
        Path repository = tempDir.resolve("repository");
        Files.createDirectories(repository);
        executeGit(repository, "init");
        executeGit(repository, "checkout", "-b", "main");
        executeGit(repository, "config", "user.name", "Test Agent");
        executeGit(repository, "config", "user.email", "test-agent@example.com");
        Files.createDirectories(repository.resolve("old-directory"));
        Files.writeString(repository.resolve("old-directory/README.md"), "old", StandardCharsets.UTF_8);
        executeGit(repository, "add", ".");
        executeGit(repository, "commit", "-m", "initial tree");

        GitCloneCacheService service = new GitCloneCacheService(
                tempDir.resolve("cache"), Duration.ofHours(1), Duration.ofSeconds(10));
        List<GitRemoteService.RemoteTreeNode> initialTree = service.listTree(
                repository.toUri().toString(), "main", null);

        Files.createDirectories(repository.resolve("latest-directory"));
        Files.writeString(repository.resolve("latest-directory/README.md"), "latest", StandardCharsets.UTF_8);
        executeGit(repository, "add", ".");
        executeGit(repository, "commit", "-m", "latest tree");

        assertThat(treePaths(initialTree)).doesNotContain("latest-directory");
        assertThat(treePaths(service.listTree(repository.toUri().toString(), "main", null)))
                .doesNotContain("latest-directory");
        assertThat(treePaths(service.refreshTree(repository.toUri().toString(), "main", null)))
                .contains("latest-directory", "latest-directory/README.md");
    }

    /** 执行测试仓库 Git 命令，失败时保留完整输出帮助定位环境问题。 */
    private static void executeGit(Path repository, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", repository.toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(finished).as("Git command timed out: %s", String.join(" ", command)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
    }

    private static List<String> treePaths(List<GitRemoteService.RemoteTreeNode> nodes) {
        List<String> paths = new ArrayList<>();
        collectTreePaths(nodes, paths);
        return paths;
    }

    private static void collectTreePaths(List<GitRemoteService.RemoteTreeNode> nodes, List<String> paths) {
        for (GitRemoteService.RemoteTreeNode node : nodes) {
            paths.add(node.path());
            collectTreePaths(node.children(), paths);
        }
    }
}
