package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 生成并按数据库权威 SHA 校验/修复平台隐藏源码索引。 */
@Component
public class AppSourceIndexManager {

    private final ObjectMapper objectMapper;

    public AppSourceIndexManager() {
        this(new ObjectMapper());
    }

    AppSourceIndexManager(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    /** 打开前校验索引；缺失或损坏时只用数据库快照重建，不信任磁盘内容。 */
    public void ensureAuthoritativeIndex(Path root, AppSourceSnapshot snapshot) {
        if (snapshot.indexSha256() == null) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用源码索引尚未就绪");
        }
        byte[] expected = canonicalBytes(snapshot);
        String expectedSha = sha256(expected);
        if (!expectedSha.equalsIgnoreCase(snapshot.indexSha256())) {
            throw new PlatformException(ErrorCode.CONFLICT, "应用源码数据库索引摘要不一致");
        }
        Path safeRoot = AppSourcePathGuard.requireSafe(root);
        Path index = AppSourcePathGuard.requireSafe(
                safeRoot.resolve(AppSourceApplicationService.INDEX_FILE_NAME));
        try {
            if (Files.isRegularFile(index) && expectedSha.equalsIgnoreCase(sha256(Files.readAllBytes(index)))) {
                return;
            }
            Files.createDirectories(safeRoot);
            AppSourcePathGuard.requireSafe(safeRoot);
            Path temporary = AppSourcePathGuard.requireSafe(safeRoot.resolve(
                    "." + AppSourceApplicationService.INDEX_FILE_NAME + "." + UUID.randomUUID() + ".repair"));
            try {
                Files.write(temporary, expected);
                Files.move(temporary, index, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "应用源码索引修复失败", Map.of(), exception);
        }
    }

    public String canonicalSha256(AppSourceSnapshot snapshot) {
        return sha256(canonicalBytes(snapshot));
    }

    byte[] canonicalBytes(AppSourceSnapshot snapshot) {
        return canonicalBytes(
                snapshot.generation(), snapshot.branch(), snapshot.targetCommit(),
                snapshot.expiresAt(), snapshot.selectedPaths());
    }

    byte[] canonicalBytes(
            long generation,
            String branch,
            String targetCommit,
            Instant expiresAt,
            List<AppSourceSelectedPath> selectedPaths) {
        try {
            Map<String, Object> index = new LinkedHashMap<>();
            index.put("schemaVersion", 1);
            index.put("generation", generation);
            index.put("branch", branch);
            index.put("commit", targetCommit);
            index.put("expiresAt", expiresAt.toString());
            index.put("selectedPaths", selectedPaths.stream().map(path -> {
                Map<String, Object> selected = new LinkedHashMap<>();
                selected.put("path", path.path());
                selected.put("type", path.pathType().name());
                return selected;
            }).toList());
            return objectMapper.writeValueAsBytes(index);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "生成应用源码索引失败", Map.of(), exception);
        }
    }

    String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
