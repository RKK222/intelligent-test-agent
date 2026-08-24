package com.enterprise.testagent.workspace;

import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityModels;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * 从固定公共 Git 文件树生成确定性、不可变的本地客户端能力包。
 *
 * <p>该构建器不执行 npm，也不信任公共仓库自带 node_modules；依赖版本只能来自平台锁文件，
 * 实际字节只能来自已离线准备好的运行时目录。</p>
 */
final class PublicClientCapabilityPackageBuilder {

    static final int SCHEMA_VERSION = 1;
    static final String PROTOCOL_CAPABILITY = "PUBLIC_CAPABILITY_SYNC_V1";
    static final int RUNTIME_LAYOUT_VERSION = 1;
    static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024;
    static final int MAX_FILES = 100_000;
    private static final Set<String> ROOTS = Set.of("agents", "skills", "tools");
    private static final String OPENCODE_PLUGIN = "@opencode-ai/plugin";
    private static final byte[] RUNTIME_GITIGNORE =
            "node_modules\npackage.json\npackage-lock.json\nbun.lock\n.gitignore".getBytes(StandardCharsets.UTF_8);
    private static final Set<String> BUILTINS = Set.of(
            "assert", "buffer", "child_process", "crypto", "events", "fs", "http", "https", "module",
            "net", "os", "path", "process", "querystring", "stream", "string_decoder", "timers", "tls",
            "tty", "url", "util", "v8", "vm", "worker_threads", "zlib");
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "(?:from\\s*|import\\s*(?:\\(\\s*)?|require\\s*\\(\\s*)[\"']([^\"']+)[\"']");
    private static final Pattern PRIVATE_ADDRESS = Pattern.compile(
            "https?://(?:10(?:\\.\\d{1,3}){3}|192\\.168(?:\\.\\d{1,3}){2}|"
                    + "172\\.(?:1[6-9]|2\\d|3[01])(?:\\.\\d{1,3}){2})(?::\\d+)?(?:[/\"'\\s]|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIVATE_ADDRESS_CODE_USE = Pattern.compile(
            "(?i)(?:fetch\\s*\\(|base[_-]?url\\s*[:=]|server[_-]?url\\s*[:=]|endpoint\\s*[:=])"
                    + "[^\\r\\n]{0,160}https?://(?:10(?:\\.\\d{1,3}){3}|192\\.168(?:\\.\\d{1,3}){2}|"
                    + "172\\.(?:1[6-9]|2\\d|3[01])(?:\\.\\d{1,3}){2})");
    private static final Pattern SECRET_CONTENT = Pattern.compile(
            "-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|(?i)(?:api[_-]?key|access[_-]?token|client[_-]?secret)\\s*[:=]\\s*[\"'][^\"']{8,}[\"']");

    private final ObjectMapper objectMapper;

    PublicClientCapabilityPackageBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    BuildResult build(
            Path publicConfigRoot,
            Path portableRuntimeLock,
            Path portableRuntimeNodeModules,
            String sourceCommit,
            LocalClientPublicCapabilityModels.Release previous,
            Instant createdAt) {
        try {
            Map<String, byte[]> files = collectPublicFiles(publicConfigRoot);
            DependencyResult dependencies = collectDependencies(
                    files, files.get("tools/package.json"), portableRuntimeLock, portableRuntimeNodeModules);
            files.putAll(dependencies.files());
            if (files.size() > MAX_FILES) {
                throw incompatible("TOO_MANY_FILES", "能力包文件数量超过上限");
            }
            long uncompressedSize = files.values().stream().mapToLong(bytes -> bytes.length).sum();
            if (uncompressedSize > MAX_UNCOMPRESSED_BYTES) {
                throw incompatible("PACKAGE_TOO_LARGE", "能力包解压后大小超过上限");
            }
            String fileContentDigest = contentDigest(files);
            // 完整能力版本同时绑定公共 Git commit；相同能力文件来自新 commit 时仍能形成可追溯的新版本，
            // 避免 bundle_digest 唯一约束把服务器已发布 commit 留在“未生成”状态。
            String bundleDigest = bundleIdentityDigest(sourceCommit, fileContentDigest);
            LocalClientPublicCapabilityModels.Counts counts = counts(files.keySet());
            ObjectNode changeSummary = changeSummary(previous, files, bundleDigest, dependencies.digest());
            boolean requiresRestart = previous == null
                    || previous.compatibility() != LocalClientPublicCapabilityModels.Compatibility.AVAILABLE
                    || changeSummary.path("toolsChanged").asBoolean()
                    || changeSummary.path("dependenciesChanged").asBoolean();
            ObjectNode manifest = manifest(
                    sourceCommit, bundleDigest, fileContentDigest, counts, dependencies, files,
                    changeSummary, requiresRestart, createdAt);
            byte[] manifestBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
            Map<String, byte[]> archiveFiles = new TreeMap<>();
            archiveFiles.put("manifest.json", manifestBytes);
            archiveFiles.putAll(files);
            byte[] artifact = tarGz(archiveFiles);
            String artifactSha256 = sha256(artifact);
            return new BuildResult(
                    sourceCommit,
                    bundleDigest,
                    artifactSha256,
                    objectMapper.writeValueAsString(manifest),
                    objectMapper.writeValueAsString(changeSummary),
                    counts,
                    requiresRestart,
                    artifact,
                    uncompressedSize + manifestBytes.length,
                    archiveFiles.size());
        } catch (CompatibilityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw incompatible("PACKAGE_BUILD_FAILED", "能力包生成失败", exception);
        }
    }

    private Map<String, byte[]> collectPublicFiles(Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Map<String, byte[]> files = new TreeMap<>();
        for (String allowedRoot : ROOTS.stream().sorted().toList()) {
            Path start = normalizedRoot.resolve(allowedRoot);
            if (!Files.exists(start, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            Files.walkFileTree(start, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName().toString();
                    if (Files.isSymbolicLink(dir)) {
                        throw incompatible("SYMLINK_REJECTED", "能力目录不允许符号链接");
                    }
                    if (!dir.equals(start) && (name.equals("node_modules") || name.equals(".git")
                            || name.equals(".cache") || name.equals("cache") || name.equals("__pycache__"))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile() || Files.isSymbolicLink(file)) {
                        throw incompatible("UNSUPPORTED_FILE_TYPE", "能力包仅允许普通文件");
                    }
                    String name = file.getFileName().toString();
                    if (excludedName(name)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (attrs.size() > MAX_FILE_BYTES) {
                        throw incompatible("FILE_TOO_LARGE", "能力文件超过单文件上限");
                    }
                    byte[] bytes = Files.readAllBytes(file);
                    rejectSensitiveContent(file, bytes);
                    String relative = normalizedRoot.relativize(file.toAbsolutePath().normalize())
                            .toString().replace('\\', '/');
                    files.put(relative, bytes);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return files;
    }

    private DependencyResult collectDependencies(
            Map<String, byte[]> publicFiles,
            byte[] toolPackageBytes,
            Path portableLockPath,
            Path nodeModulesRoot) throws IOException {
        Map<String, String> declared = toolPackageBytes == null
                ? Map.of() : stringMap(objectMapper.readTree(toolPackageBytes).path("dependencies"));
        Set<String> imported = toolImports(publicFiles);
        if (!declared.keySet().containsAll(imported)) {
            Set<String> missing = new LinkedHashSet<>(imported);
            missing.removeAll(declared.keySet());
            throw incompatible("TOOL_DEPENDENCY_UNDECLARED", "Tool 存在未声明依赖: " + String.join(",", missing));
        }
        if (!Files.isRegularFile(portableLockPath, LinkOption.NOFOLLOW_LINKS)) {
            throw incompatible("PORTABLE_LOCK_MISSING", "平台可移植依赖锁文件不存在");
        }
        JsonNode packages = objectMapper.readTree(portableLockPath.toFile()).path("packages");
        JsonNode pluginLock = packages.path("node_modules/" + OPENCODE_PLUGIN);
        String pluginVersion = pluginLock.path("version").asText(null);
        if (pluginVersion == null) {
            throw incompatible("PORTABLE_PLUGIN_NOT_LOCKED", "平台可移植运行时未锁定 OpenCode Plugin");
        }
        Map<String, String> directDependencies = new TreeMap<>(declared);
        String declaredPluginVersion = directDependencies.putIfAbsent(OPENCODE_PLUGIN, pluginVersion);
        if (declaredPluginVersion != null && !declaredPluginVersion.equals(pluginVersion)) {
            throw incompatible("TOOL_DEPENDENCY_NOT_LOCKED", "Tool 依赖未按平台版本锁定: " + OPENCODE_PLUGIN);
        }
        Map<String, byte[]> output = new TreeMap<>();
        Map<String, String> versions = new TreeMap<>();
        Map<String, JsonNode> lockedPackages = new TreeMap<>();
        Path normalizedModules = nodeModulesRoot.toAbsolutePath().normalize();
        ArrayDeque<String> queue = new ArrayDeque<>(directDependencies.keySet());
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String dependency = queue.removeFirst();
            if (!visited.add(dependency)) {
                continue;
            }
            JsonNode locked = packages.path("node_modules/" + dependency);
            String lockedVersion = locked.path("version").asText(null);
            if (lockedVersion == null || (declared.containsKey(dependency)
                    && !declared.get(dependency).equals(lockedVersion))) {
                throw incompatible("TOOL_DEPENDENCY_NOT_LOCKED", "Tool 依赖未按平台版本锁定: " + dependency);
            }
            if (locked.path("hasInstallScript").asBoolean(false)
                    || locked.has("os") || locked.has("cpu")) {
                throw incompatible("TOOL_DEPENDENCY_NOT_PORTABLE", "Tool 依赖包含安装脚本或平台约束: " + dependency);
            }
            Path packageRoot = normalizedModules.resolve(dependency).normalize();
            if (!packageRoot.startsWith(normalizedModules)
                    || !Files.isDirectory(packageRoot, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(packageRoot)) {
                throw incompatible("PORTABLE_DEPENDENCY_MISSING", "可移植依赖字节不存在: " + dependency);
            }
            JsonNode installedManifest = objectMapper.readTree(packageRoot.resolve("package.json").toFile());
            if (!lockedVersion.equals(installedManifest.path("version").asText())) {
                throw incompatible("PORTABLE_DEPENDENCY_VERSION_MISMATCH", "可移植依赖版本不匹配: " + dependency);
            }
            JsonNode scripts = installedManifest.path("scripts");
            if (scripts.has("preinstall") || scripts.has("install") || scripts.has("postinstall")) {
                throw incompatible("TOOL_DEPENDENCY_INSTALL_SCRIPT", "依赖声明了安装脚本: " + dependency);
            }
            collectDependencyFiles(normalizedModules, packageRoot, output);
            versions.put(dependency, lockedVersion);
            lockedPackages.put("node_modules/" + dependency, locked.deepCopy());
            stringMap(locked.path("dependencies")).keySet().forEach(queue::addLast);
        }
        Map<String, byte[]> runtimeFiles = new TreeMap<>();
        output.forEach((path, bytes) -> runtimeFiles.put("node_modules/" + path, bytes));
        runtimeFiles.put("package.json", runtimePackageJson(directDependencies));
        runtimeFiles.put("package-lock.json", runtimePackageLock(directDependencies, lockedPackages));
        // OpenCode 会自行补写同名文件；随包提供完全相同的内容可保持版本目录不被改写。
        runtimeFiles.put(".gitignore", RUNTIME_GITIGNORE);
        return new DependencyResult(Map.copyOf(runtimeFiles), Map.copyOf(versions), contentDigest(runtimeFiles));
    }

    private byte[] runtimePackageJson(Map<String, String> dependencies) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("name", "test-agent-public-capability-runtime");
        root.put("version", "1.0.0");
        root.put("private", true);
        root.put("type", "module");
        ObjectNode dependencyNode = root.putObject("dependencies");
        dependencies.forEach(dependencyNode::put);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
    }

    private byte[] runtimePackageLock(
            Map<String, String> dependencies,
            Map<String, JsonNode> lockedPackages) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("name", "test-agent-public-capability-runtime");
        root.put("version", "1.0.0");
        root.put("lockfileVersion", 3);
        root.put("requires", true);
        ObjectNode packages = root.putObject("packages");
        ObjectNode packageRoot = packages.putObject("");
        packageRoot.put("name", "test-agent-public-capability-runtime");
        packageRoot.put("version", "1.0.0");
        ObjectNode dependencyNode = packageRoot.putObject("dependencies");
        dependencies.forEach(dependencyNode::put);
        lockedPackages.forEach(packages::set);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
    }

    /** Tool 的裸模块 import 必须同时出现在 tools/package.json 与平台 lock 中。 */
    private Set<String> toolImports(Map<String, byte[]> files) {
        Set<String> imported = new LinkedHashSet<>();
        files.forEach((path, bytes) -> {
            if (!path.startsWith("tools/")
                    || !(path.endsWith(".ts") || path.endsWith(".js") || path.endsWith(".mjs"))) {
                return;
            }
            String source = new String(bytes, StandardCharsets.UTF_8);
            Matcher matcher = IMPORT_PATTERN.matcher(source);
            while (matcher.find()) {
                String specifier = matcher.group(1);
                if (specifier.startsWith(".") || specifier.startsWith("/") || specifier.startsWith("node:")) {
                    continue;
                }
                String topLevel = topLevelPackage(specifier);
                if (!BUILTINS.contains(topLevel)) {
                    imported.add(topLevel);
                }
            }
            if (source.matches("(?s).*import\\s*\\((?!\\s*[\"']).*")) {
                throw incompatible("TOOL_DYNAMIC_IMPORT_REJECTED", "Tool 使用了无法静态解析的动态 import: " + path);
            }
            if (source.matches("(?s).*require\\s*\\((?!\\s*[\"']).*")) {
                throw incompatible("TOOL_DYNAMIC_REQUIRE_REJECTED", "Tool 使用了无法静态解析的动态 require: " + path);
            }
        });
        return Set.copyOf(imported);
    }

    private static String topLevelPackage(String specifier) {
        if (!specifier.startsWith("@")) {
            int slash = specifier.indexOf('/');
            return slash < 0 ? specifier : specifier.substring(0, slash);
        }
        int first = specifier.indexOf('/');
        int second = first < 0 ? -1 : specifier.indexOf('/', first + 1);
        return second < 0 ? specifier : specifier.substring(0, second);
    }

    private void collectDependencyFiles(Path nodeModulesRoot, Path packageRoot, Map<String, byte[]> output)
            throws IOException {
        Path normalizedModules = nodeModulesRoot.toAbsolutePath().normalize();
        Files.walkFileTree(packageRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (Files.isSymbolicLink(dir)) {
                    throw incompatible("TOOL_DEPENDENCY_SYMLINK", "可移植依赖不允许符号链接");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || Files.isSymbolicLink(file)) {
                    throw incompatible("TOOL_DEPENDENCY_FILE_TYPE", "可移植依赖包含非普通文件");
                }
                String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".node") || lower.endsWith(".so") || lower.endsWith(".dylib")
                        || lower.endsWith(".dll") || lower.endsWith(".exe")) {
                    throw incompatible("TOOL_DEPENDENCY_NATIVE_BINARY", "可移植依赖包含原生扩展");
                }
                if (attrs.size() > MAX_FILE_BYTES) {
                    throw incompatible("TOOL_DEPENDENCY_FILE_TOO_LARGE", "依赖文件超过上限");
                }
                String relative = normalizedModules.relativize(file.toAbsolutePath().normalize())
                        .toString().replace('\\', '/');
                output.put(relative, Files.readAllBytes(file));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private ObjectNode manifest(
            String sourceCommit,
            String bundleDigest,
            String contentDigest,
            LocalClientPublicCapabilityModels.Counts counts,
            DependencyResult dependencies,
            Map<String, byte[]> files,
            ObjectNode changeSummary,
            boolean requiresRestart,
            Instant createdAt) {
        ObjectNode manifest = objectMapper.createObjectNode();
        manifest.put("schemaVersion", SCHEMA_VERSION);
        manifest.put("sourceCommit", sourceCommit);
        manifest.put("bundleDigest", bundleDigest);
        manifest.put("contentDigest", contentDigest);
        manifest.put("protocolCapability", PROTOCOL_CAPABILITY);
        manifest.put("runtimeLayoutVersion", RUNTIME_LAYOUT_VERSION);
        manifest.put("opencodeVersionMin", "1.18.4");
        manifest.put("opencodeVersionMax", "1.18.x");
        manifest.put("requiresRestart", requiresRestart);
        manifest.put("createdAt", createdAt.toString());
        ObjectNode countNode = manifest.putObject("counts");
        countNode.put("agents", counts.agents()); countNode.put("skills", counts.skills()); countNode.put("tools", counts.tools());
        manifest.set("changeSummary", changeSummary);
        ObjectNode dependencyNode = manifest.putObject("dependencies");
        dependencyNode.put("digest", dependencies.digest());
        dependencies.versions().forEach(dependencyNode.putObject("versions")::put);
        ArrayNode fileList = manifest.putArray("files");
        files.forEach((path, bytes) -> {
            ObjectNode file = fileList.addObject();
            file.put("path", path); file.put("size", bytes.length); file.put("sha256", sha256(bytes));
        });
        return manifest;
    }

    private ObjectNode changeSummary(
            LocalClientPublicCapabilityModels.Release previous,
            Map<String, byte[]> files,
            String bundleDigest,
            String dependencyDigest) {
        ObjectNode summary = objectMapper.createObjectNode();
        boolean initial = previous == null
                || previous.compatibility() != LocalClientPublicCapabilityModels.Compatibility.AVAILABLE;
        summary.put("initial", initial);
        summary.put("previousDigest", previous == null ? null : previous.bundleDigest());
        summary.put("targetDigest", bundleDigest);
        Map<String, String> previousFiles = initial ? Map.of() : manifestFiles(previous.manifestJson());
        summary.put("agentsChanged", initial || categoryChanged(previousFiles, files, "agents/"));
        summary.put("skillsChanged", initial || categoryChanged(previousFiles, files, "skills/"));
        summary.put("toolsChanged", initial || categoryChanged(previousFiles, files, "tools/"));
        String previousDependencies = initial ? null : manifestDependencyDigest(previous.manifestJson());
        summary.put("dependenciesChanged", initial || !dependencyDigest.equals(previousDependencies));
        return summary;
    }

    private Map<String, String> manifestFiles(String manifestJson) {
        try {
            Map<String, String> files = new TreeMap<>();
            for (JsonNode item : objectMapper.readTree(manifestJson).path("files")) {
                files.put(item.path("path").asText(), item.path("sha256").asText());
            }
            return Map.copyOf(files);
        } catch (IOException exception) {
            throw incompatible("PREVIOUS_MANIFEST_INVALID", "上一客户端能力包 manifest 无法读取", exception);
        }
    }

    private String manifestDependencyDigest(String manifestJson) {
        try {
            return objectMapper.readTree(manifestJson).path("dependencies").path("digest").asText(null);
        } catch (IOException exception) {
            throw incompatible("PREVIOUS_MANIFEST_INVALID", "上一客户端能力包依赖摘要无法读取", exception);
        }
    }

    private static boolean categoryChanged(
            Map<String, String> previous,
            Map<String, byte[]> current,
            String prefix) {
        Map<String, String> currentDigests = new TreeMap<>();
        current.forEach((path, bytes) -> {
            if (path.startsWith(prefix)) {
                currentDigests.put(path, sha256(bytes));
            }
        });
        Map<String, String> previousDigests = new TreeMap<>();
        previous.forEach((path, digest) -> {
            if (path.startsWith(prefix)) {
                previousDigests.put(path, digest);
            }
        });
        return !currentDigests.equals(previousDigests);
    }

    private static LocalClientPublicCapabilityModels.Counts counts(Set<String> paths) {
        int agents = (int) paths.stream().filter(path -> path.startsWith("agents/") && path.endsWith(".md")).count();
        int skills = (int) paths.stream().filter(path -> path.startsWith("skills/") && path.endsWith("/SKILL.md")).count();
        int tools = (int) paths.stream().filter(path -> path.startsWith("tools/")
                && (path.endsWith(".ts") || path.endsWith(".js") || path.endsWith(".mjs"))).count();
        return new LocalClientPublicCapabilityModels.Counts(agents, skills, tools);
    }

    private void rejectSensitiveContent(Path file, byte[] bytes) {
        if (!isText(file.getFileName().toString())) {
            return;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        boolean codeSource = file.getFileName().toString().matches("(?i).+\\.(?:ts|js|mjs)$");
        boolean privateAddress = PRIVATE_ADDRESS.matcher(text).find()
                && (!codeSource || PRIVATE_ADDRESS_CODE_USE.matcher(text).find());
        if (SECRET_CONTENT.matcher(text).find() || privateAddress) {
            throw incompatible("SENSITIVE_CONTENT", "能力文件包含密钥或固定内网服务器地址: " + file.getFileName());
        }
    }

    private static boolean excludedName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return name.equals("AGENTS.md") || name.equals("opencode.jsonc") || lower.equals(".env")
                || lower.startsWith(".env.") || lower.endsWith(".pem") || lower.endsWith(".key")
                || lower.equals("package-lock.json") || lower.equals("pnpm-lock.yaml")
                || lower.equals("yarn.lock") || lower.equals("bun.lock") || lower.equals("bun.lockb")
                || lower.equals("credentials.properties") || lower.equals("client.properties")
                || lower.equals(".ds_store");
    }

    private static boolean isText(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".json") || lower.endsWith(".jsonc")
                || lower.endsWith(".ts") || lower.endsWith(".js") || lower.endsWith(".mjs")
                || lower.endsWith(".py")
                || lower.endsWith(".txt") || lower.endsWith(".yaml") || lower.endsWith(".yml")
                || lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".sh");
    }

    private static Map<String, String> stringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asText()));
        return Map.copyOf(values);
    }

    private static String contentDigest(Map<String, byte[]> files) {
        MessageDigest digest = messageDigest();
        new TreeMap<>(files).forEach((path, bytes) -> {
            digest.update(path.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(sha256(bytes).getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) '\n');
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String bundleIdentityDigest(String sourceCommit, String contentDigest) {
        return sha256((sourceCommit.toLowerCase(Locale.ROOT) + "\n" + contentDigest)
                .getBytes(StandardCharsets.US_ASCII));
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(messageDigest().digest(bytes));
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static byte[] tarGz(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : new TreeMap<>(files).entrySet()) {
                writeTarEntry(gzip, "public-capabilities/" + entry.getKey(), entry.getValue());
            }
            gzip.write(new byte[1024]);
        }
        return bytes.toByteArray();
    }

    private static void writeTarEntry(GZIPOutputStream output, String path, byte[] content) throws IOException {
        byte[] name = path.getBytes(StandardCharsets.UTF_8);
        byte[] prefix = new byte[0];
        if (name.length > 100) {
            int split = path.lastIndexOf('/');
            while (split > 0) {
                byte[] candidatePrefix = path.substring(0, split).getBytes(StandardCharsets.UTF_8);
                byte[] candidateName = path.substring(split + 1).getBytes(StandardCharsets.UTF_8);
                if (candidatePrefix.length <= 155 && candidateName.length <= 100) {
                    prefix = candidatePrefix;
                    name = candidateName;
                    break;
                }
                split = path.lastIndexOf('/', split - 1);
            }
            if (name.length > 100 || prefix.length == 0) {
                throw incompatible("TAR_PATH_TOO_LONG", "能力包路径超过 USTAR 上限: " + path);
            }
        }
        byte[] header = new byte[512];
        System.arraycopy(name, 0, header, 0, name.length);
        octal(header, 100, 8, 0644); octal(header, 108, 8, 0); octal(header, 116, 8, 0);
        octal(header, 124, 12, content.length); octal(header, 136, 12, 0);
        java.util.Arrays.fill(header, 148, 156, (byte) ' ');
        header[156] = '0';
        byte[] magic = "ustar\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, header, 257, magic.length);
        byte[] version = "00".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(version, 0, header, 263, version.length);
        System.arraycopy(prefix, 0, header, 345, prefix.length);
        long checksum = 0;
        for (byte value : header) { checksum += Byte.toUnsignedInt(value); }
        octal(header, 148, 8, checksum);
        output.write(header); output.write(content);
        int padding = (512 - (content.length % 512)) % 512;
        if (padding > 0) { output.write(new byte[padding]); }
    }

    private static void octal(byte[] target, int offset, int length, long value) {
        String encoded = Long.toOctalString(value);
        if (encoded.length() > length - 1) {
            throw incompatible("TAR_VALUE_TOO_LARGE", "能力包 TAR 字段超过上限");
        }
        int start = offset + length - 1 - encoded.length();
        java.util.Arrays.fill(target, offset, start, (byte) '0');
        byte[] ascii = encoded.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(ascii, 0, target, start, ascii.length);
        target[offset + length - 1] = 0;
    }

    static CompatibilityException incompatible(String code, String message) {
        return new CompatibilityException(code, message, null);
    }

    static CompatibilityException incompatible(String code, String message, Throwable cause) {
        return new CompatibilityException(code, message, cause);
    }

    record DependencyResult(Map<String, byte[]> files, Map<String, String> versions, String digest) {
    }

    record BuildResult(
            String sourceCommit,
            String bundleDigest,
            String artifactSha256,
            String manifestJson,
            String changeSummaryJson,
            LocalClientPublicCapabilityModels.Counts counts,
            boolean requiresRestart,
            byte[] artifact,
            long uncompressedSize,
            int fileCount) {
    }

    static final class CompatibilityException extends RuntimeException {
        private final String errorCode;

        CompatibilityException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        String errorCode() {
            return errorCode;
        }
    }
}
