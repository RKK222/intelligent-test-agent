package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore.ImportCandidate;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore.ImportSource;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 每个 Java 只扫描本服务器的升级前管理员个人工作区；候选和完成标记进入共享数据库。
 * 离线服务器保持 PENDING，不能由别的 Java 代读文件或提前宣布全部迁移完成。
 */
@Service
public class ApplicationAssetReferenceLegacyImporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationAssetReferenceLegacyImporter.class);
    private static final String PREFIX = "{env:OPENCODE_REFERENCES_DIR}/";
    private final ApplicationAssetReferenceStore store;
    private final ConfigurationManagementRepository configurationRepository;
    private final AgentConfigApplicationService agentConfigService;
    private final ReferenceRepositoryApplicationService referenceService;
    private final WorkspaceServerIdentity serverIdentity;
    private final ApplicationAssetReferenceImportFinalizer finalizer;
    private final Clock clock;
    private final ObjectMapper jsoncMapper = new ObjectMapper(JsonFactory.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA).build());

    public ApplicationAssetReferenceLegacyImporter(ApplicationAssetReferenceStore store,
            ConfigurationManagementRepository configurationRepository,
            AgentConfigApplicationService agentConfigService,
            ReferenceRepositoryApplicationService referenceService,
            WorkspaceServerIdentity serverIdentity,
            ApplicationAssetReferenceImportFinalizer finalizer,
            Clock clock) {
        this.store = store;
        this.configurationRepository = configurationRepository;
        this.agentConfigService = agentConfigService;
        this.referenceService = referenceService;
        this.serverIdentity = serverIdentity;
        this.finalizer = finalizer;
        this.clock = clock;
    }

    /** 有界批次避免启动或后台定时任务一次读取大量个人文件。 */
    @Scheduled(initialDelay = 15000, fixedDelay = 60000)
    public void scan() {
        try {
            var now = clock.instant();
            for (ImportSource source : store.claimLocalSources(serverIdentity.linuxServerId(),
                    now, now.plus(Duration.ofMinutes(3)), 25)) {
                scanOne(source);
            }
            for (var appId : store.readyImportApplications()) finalizer.finalizeApplication(appId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Application asset reference legacy scan failed, errorType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void scanOne(ImportSource source) {
        try {
            String content;
            try {
                content = agentConfigService.readWorkspaceAgentFile(source.workspaceId(), "opencode.jsonc", null).content();
            } catch (PlatformException exception) {
                if (exception.errorCode() != ErrorCode.NOT_FOUND) throw exception;
                content = "";
            }
            List<ImportCandidate> candidates = extract(source, content);
            store.finishSource(source, candidates, clock.instant());
        } catch (PlatformException exception) {
            if (exception.errorCode() == ErrorCode.CONFLICT) {
                store.retrySource(source, "工作区或资产副本暂不可用", clock.instant().plus(Duration.ofMinutes(2)));
            } else {
                store.recordConflict(source.appId(), source.workspaceId(), "旧个人引用无法安全解析，请管理员检查", clock.instant());
                store.finishSource(source, List.of(), clock.instant());
            }
        } catch (RuntimeException exception) {
            store.recordConflict(source.appId(), source.workspaceId(), "旧个人引用无法安全解析，请管理员检查", clock.instant());
            store.finishSource(source, List.of(), clock.instant());
        }
    }

    private List<ImportCandidate> extract(ImportSource source, String content) {
        if (content == null || content.isBlank()) return List.of();
        JsonNode root;
        try {
            root = jsoncMapper.readTree(content);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "旧个人引用 JSONC 无法解析", Map.of());
        }
        JsonNode references = root.path("references");
        if (references.isMissingNode()) return List.of();
        if (!references.isObject()) throw new PlatformException(ErrorCode.VALIDATION_ERROR, "旧个人引用结构无效");
        List<CodeRepository> repositories = configurationRepository.findRepositoriesByApplication(source.appId()).stream()
                .filter(repository -> CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value()
                        .equals(repository.repositoryType()))
                .toList();
        List<ImportCandidate> result = new ArrayList<>();
        references.fields().forEachRemaining(entry -> {
            try {
                extractOne(source, repositories, result, entry.getKey(), entry.getValue());
            } catch (PlatformException exception) {
                if (exception.errorCode() == ErrorCode.CONFLICT) {
                    throw exception;
                }
                store.recordConflict(source.appId(), entry.getKey(),
                        "旧个人资产引用字段或目录无效，未自动发布", clock.instant());
            }
        });
        return result;
    }

    /** 单条旧引用校验失败只隔离该别名，其他合法目录仍可自动发布。 */
    private void extractOne(ImportSource source, List<CodeRepository> repositories,
                            List<ImportCandidate> result, String alias, JsonNode value) {
            if (!value.isObject() || !value.path("path").isTextual()) {
                if (repositories.stream().anyMatch(repository -> alias.endsWith("-" + repository.englishName()))) {
                    throw new PlatformException(ErrorCode.VALIDATION_ERROR, "旧个人资产引用路径无效");
                }
                return;
            }
            String path = value.path("path").asText();
            for (CodeRepository repository : repositories) {
                String prefix = PREFIX + repository.englishName() + "/";
                if (!path.startsWith(prefix)) continue;
                String directory = ApplicationAssetReferenceService.requireRelativePath(path.substring(prefix.length()));
                String folder = directory.substring(directory.lastIndexOf('/') + 1);
                if (!alias.equals(folder + "-" + repository.englishName())
                        || !value.path("sdd-folder-name").asText("").equals(folder)
                        || !value.path("merge").isBoolean()
                        || !value.path("description").isTextual()
                        || value.path("description").asText().isBlank()) {
                    throw new PlatformException(ErrorCode.VALIDATION_ERROR, "旧个人资产引用字段不一致");
                }
                String parent = directory.contains("/") ? directory.substring(0, directory.lastIndexOf('/')) : "";
                boolean selectable = referenceService.tree(source.appId().value(),
                        repository.repositoryId().value(), parent).stream()
                        .anyMatch(node -> node.path().equals(directory) && node.directory() && node.selectable());
                if (!selectable) throw new PlatformException(ErrorCode.VALIDATION_ERROR, "旧个人资产目录不可配置");
                result.add(new ImportCandidate(source.workspaceId(), source.appId(), repository.repositoryId(),
                        directory, alias, value.path("merge").asBoolean(), folder,
                        value.path("description").asText().trim()));
                return;
            }
    }
}
