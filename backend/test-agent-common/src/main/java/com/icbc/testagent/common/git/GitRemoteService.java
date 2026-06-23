package com.icbc.testagent.common.git;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Git 远端只读查询服务，仅使用 ls-remote 和 archive，不 clone、不 fetch。
 */
public class GitRemoteService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    private final GitCommandExecutor executor;

    /**
     * 默认使用本机 git 命令执行器。
     */
    public GitRemoteService() {
        this(new ProcessGitCommandExecutor());
    }

    /**
     * 测试可注入 fake executor，验证解析逻辑和错误映射。
     */
    public GitRemoteService(GitCommandExecutor executor) {
        this.executor = executor;
    }

    /**
     * 列出远端分支名称。
     */
    public List<String> listBranches(String gitUrl, String privateKey) {
        GitCommandResult result = executor.execute(
                List.of("git", "ls-remote", "--heads", gitUrl),
                privateKey,
                DEFAULT_TIMEOUT);
        return parseBranches(result.stdoutText());
    }

    /**
     * 使用 git archive --remote 读取指定分支文件列表，并返回目录路径集合。
     */
    public List<String> listDirectories(String gitUrl, String branch, String privateKey) {
        GitCommandResult result = executor.execute(
                List.of("git", "archive", "--format=tar", "--remote=" + gitUrl, branch),
                privateKey,
                DEFAULT_TIMEOUT);
        return parseTarDirectories(result.stdoutBytes());
    }

    /**
     * 解析 git ls-remote --heads 输出，忽略非 heads 引用。
     */
    public List<String> parseBranches(String output) {
        return output.lines()
                .map(String::trim)
                .filter(line -> line.contains("refs/heads/"))
                .map(line -> line.substring(line.indexOf("refs/heads/") + "refs/heads/".length()))
                .filter(branch -> !branch.isBlank())
                .toList();
    }

    /**
     * 解析 tar header 中的 entry name，返回所有父目录路径。
     */
    public List<String> parseTarDirectories(byte[] tarBytes) {
        Set<String> directories = new TreeSet<>();
        int offset = 0;
        while (offset + 512 <= tarBytes.length) {
            String name = tarName(tarBytes, offset);
            if (name.isBlank()) {
                if (!hasNonZeroAfter(tarBytes, offset + 512)) {
                    break;
                }
                offset += 512;
                continue;
            }
            addParentDirectories(name, directories);
            long size = tarSize(tarBytes, offset + 124);
            long blocks = (size + 511) / 512;
            offset += 512 + (int) blocks * 512;
        }
        return new ArrayList<>(directories);
    }

    private static void addParentDirectories(String name, Set<String> directories) {
        String normalized = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        int slash = normalized.indexOf('/');
        while (slash > 0) {
            directories.add(normalized.substring(0, slash));
            slash = normalized.indexOf('/', slash + 1);
        }
        if (name.endsWith("/") && !normalized.isBlank()) {
            directories.add(normalized);
        }
    }

    private static String tarName(byte[] bytes, int offset) {
        int end = offset;
        int max = Math.min(offset + 100, bytes.length);
        while (end < max && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    private static long tarSize(byte[] bytes, int offset) {
        int end = offset;
        int max = Math.min(offset + 12, bytes.length);
        while (end < max && bytes[end] != 0 && bytes[end] != ' ') {
            end++;
        }
        String raw = new String(bytes, offset, end - offset, StandardCharsets.US_ASCII).trim();
        if (raw.isBlank()) {
            return 0;
        }
        return Long.parseLong(raw, 8);
    }

    private static boolean hasNonZeroAfter(byte[] bytes, int offset) {
        for (int index = Math.max(0, offset); index < bytes.length; index++) {
            if (bytes[index] != 0) {
                return true;
            }
        }
        return false;
    }
}
