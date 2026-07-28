package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 解析 READY 本机副本并在返回 WorkspaceId 前修复数据库权威索引。 */
@Component
public class AppSourceWorkspaceOpener {

    private final AppSourceRepository appSources;
    private final WorkspaceRepository workspaces;
    private final ManagedWorkspacePathResolver paths;
    private final AppSourceIndexManager indexes;
    private final WorkspaceServerIdentity serverIdentity;

    public AppSourceWorkspaceOpener(
            AppSourceRepository appSources,
            WorkspaceRepository workspaces,
            ManagedWorkspacePathResolver paths,
            AppSourceIndexManager indexes,
            WorkspaceServerIdentity serverIdentity) {
        this.appSources = Objects.requireNonNull(appSources);
        this.workspaces = Objects.requireNonNull(workspaces);
        this.paths = Objects.requireNonNull(paths);
        this.indexes = Objects.requireNonNull(indexes);
        this.serverIdentity = Objects.requireNonNull(serverIdentity);
    }

    public Workspace open(AppSourceSnapshot snapshot, LinuxServerId linuxServerId) {
        if (!serverIdentity.linuxServerId().equals(linuxServerId.value())) {
            throw new PlatformException(ErrorCode.CONFLICT, "只能打开当前服务器的应用源码副本");
        }
        var replica = appSources.findReplica(snapshot.repositoryId(), snapshot.generation(), linuxServerId)
                .filter(value -> value.status() == AppSourceReplicaStatus.READY)
                .filter(value -> value.runtimeWorkspaceId() != null)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.CONFLICT, "当前服务器没有 READY 应用源码副本",
                        Map.of("linuxServerId", linuxServerId.value())));
        Workspace workspace = workspaces.findById(replica.runtimeWorkspaceId())
                .filter(value -> value.status() == WorkspaceStatus.ACTIVE)
                .filter(value -> linuxServerId.value().equals(value.linuxServerId()))
                .orElseThrow(() -> new PlatformException(ErrorCode.CONFLICT, "应用源码 Workspace 不可用"));
        Path root = paths.resolve(workspace.rootPath()).toAbsolutePath().normalize();
        indexes.ensureAuthoritativeIndex(root, snapshot);
        return workspace;
    }
}
