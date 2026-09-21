package com.enterprise.testagent.workspace;

import java.nio.file.Path;
import java.util.List;

/**
 * 团队导出跨 Java 节点 shard 传输端口。
 *
 * <p>控制请求由公共 Java 路由和 HTTP 转发器发送，ZIP 内容只能经一次性内部
 * WebSocket 流式回传协调节点，禁止退化为后端间 HTTP 文件代理。</p>
 */
public interface TeamWorkspaceExportShardGateway {

    Preflight inspect(Request request);

    TransferredShard fetch(Request request, Path target);

    /** 团队关系撤销后通知产物协调节点立即删除未下载 ZIP；失败时由协调节点定时清理兜底。 */
    void deleteRevokedArtifact(String coordinatorLinuxServerId, String exportId, String traceId);

    record Preflight(int fileCount, long uncompressedBytes) {
    }

    record Request(
            String exportId,
            String exportItemId,
            String sourceLinuxServerId,
            String userId,
            String personalWorkspaceId,
            String versionId,
            long maxUncompressedBytes,
            int maxFileCount,
            String traceId) {
    }

    record TransferredShard(
            int fileCount,
            long uncompressedBytes,
            long archiveBytes,
            String sha256,
            List<Excluded> excluded) {
    }

    record Excluded(String path, String reason) {
    }
}
