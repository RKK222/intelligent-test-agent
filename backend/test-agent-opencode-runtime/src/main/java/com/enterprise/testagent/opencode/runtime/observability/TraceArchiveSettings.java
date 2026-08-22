package com.enterprise.testagent.opencode.runtime.observability;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Trace 归档参数；默认复用现有 SYS_DATA_ROOT_DIR 持久化卷，不引入新部署节点。 */
@Component
public class TraceArchiveSettings {

    private final Path archiveRoot;
    private final long warningFreeBytes;

    public TraceArchiveSettings(
            CommonParameterValues commonParameters,
            @Value("${test-agent.observability.trace.archive-root:}") String configuredRoot,
            @Value("${test-agent.observability.trace.warning-free-bytes:1073741824}") long warningFreeBytes) {
        String root = configuredRoot == null ? "" : configuredRoot.trim();
        if (root.isBlank()) {
            root = commonParameters.resolvedValue("SYS_DATA_ROOT_DIR", ParameterPlatform.current())
                    .map(value -> Path.of(value).resolve("agent-observability/traces").toString())
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.TRACE_CONTENT_UNAVAILABLE,
                            "Trace 归档根目录未配置"));
        }
        this.archiveRoot = Path.of(root).toAbsolutePath().normalize();
        this.warningFreeBytes = Math.max(64L * 1024 * 1024, warningFreeBytes);
    }

    public Path archiveRoot() {
        return archiveRoot;
    }

    public long warningFreeBytes() {
        return warningFreeBytes;
    }
}
