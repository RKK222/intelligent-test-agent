package com.enterprise.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.git.GitRemoteService;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitCloneCacheServiceTest {

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
}
