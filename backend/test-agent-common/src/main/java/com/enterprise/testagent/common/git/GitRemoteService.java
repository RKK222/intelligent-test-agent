package com.enterprise.testagent.common.git;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Git 远端只读查询服务，仅使用 ls-remote 和 archive，不 clone、不 fetch。
 */
public class GitRemoteService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    public static final String NODE_TYPE_DIRECTORY = "directory";
    public static final String NODE_TYPE_FILE = "file";

    private final GitCommandExecutor executor;

    /**
     * 远端仓库树节点。type 只输出 directory/file，children 对文件恒为空列表。
     */
    public record RemoteTreeNode(String name, String path, String type, List<RemoteTreeNode> children) {
        public RemoteTreeNode {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

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
     * 使用 git archive --remote 读取指定分支文件列表，并返回完整目录/文件树。
     */
    public List<RemoteTreeNode> listTree(String gitUrl, String branch, String privateKey) {
        GitCommandResult result = executor.execute(
                List.of("git", "archive", "--format=tar", "--remote=" + gitUrl, branch),
                privateKey,
                DEFAULT_TIMEOUT);
        return parseTarTree(result.stdoutBytes());
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

    /**
     * 解析 tar header 中的 entry name 和 typeflag，组装稳定排序的目录/文件树。
     */
    public List<RemoteTreeNode> parseTarTree(byte[] tarBytes) {
        MutableTreeNode root = new MutableTreeNode("", "", NODE_TYPE_DIRECTORY);
        int offset = 0;
        while (offset + 512 <= tarBytes.length) {
            String name = normalizeTarEntryName(tarName(tarBytes, offset));
            if (name.isBlank()) {
                if (!hasNonZeroAfter(tarBytes, offset + 512)) {
                    break;
                }
                offset += 512;
                continue;
            }
            char typeFlag = offset + 156 < tarBytes.length ? (char) tarBytes[offset + 156] : '0';
            boolean directory = typeFlag == '5' || name.endsWith("/");
            if (typeFlag == 'x' || typeFlag == 'g') {
                long size = tarSize(tarBytes, offset + 124);
                long blocks = (size + 511) / 512;
                offset += 512 + (int) blocks * 512;
                continue;
            }
            addTreeEntry(root, name, directory ? NODE_TYPE_DIRECTORY : NODE_TYPE_FILE);
            long size = tarSize(tarBytes, offset + 124);
            long blocks = (size + 511) / 512;
            offset += 512 + (int) blocks * 512;
        }
        return root.children.values().stream()
                .sorted(treeNodeComparator())
                .map(MutableTreeNode::toImmutable)
                .toList();
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

    private static void addTreeEntry(MutableTreeNode root, String name, String type) {
        String normalized = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        if (normalized.isBlank()) {
            return;
        }
        String[] segments = normalized.split("/");
        MutableTreeNode current = root;
        StringBuilder path = new StringBuilder();
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (segment.isBlank()) {
                return;
            }
            if (path.length() > 0) {
                path.append('/');
            }
            path.append(segment);
            boolean leaf = index == segments.length - 1;
            String nodeType = leaf ? type : NODE_TYPE_DIRECTORY;
            current = current.children.computeIfAbsent(segment, key -> new MutableTreeNode(segment, path.toString(), nodeType));
            if (leaf) {
                current.type = nodeType;
            }
        }
    }

    private static String tarName(byte[] bytes, int offset) {
        String name = tarString(bytes, offset, 100);
        String prefix = tarString(bytes, offset + 345, 155);
        if (!prefix.isBlank()) {
            return prefix + "/" + name;
        }
        return name;
    }

    private static String tarString(byte[] bytes, int offset, int length) {
        int end = offset;
        int max = Math.min(offset + length, bytes.length);
        while (end < max && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    private static String normalizeTarEntryName(String name) {
        String value = name == null ? "" : name.replace('\\', '/').trim();
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
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

    private static Comparator<MutableTreeNode> treeNodeComparator() {
        return Comparator
                .comparing((MutableTreeNode node) -> NODE_TYPE_FILE.equals(node.type))
                .thenComparing(node -> node.name);
    }

    private static final class MutableTreeNode {
        private final String name;
        private final String path;
        private String type;
        private final Map<String, MutableTreeNode> children = new LinkedHashMap<>();

        private MutableTreeNode(String name, String path, String type) {
            this.name = name;
            this.path = path;
            this.type = type;
        }

        private RemoteTreeNode toImmutable() {
            return new RemoteTreeNode(
                    name,
                    path,
                    type,
                    children.values().stream()
                            .sorted(treeNodeComparator())
                            .map(MutableTreeNode::toImmutable)
                            .toList());
        }
    }
}
