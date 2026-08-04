package com.enterprise.testagent.workspace;

import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocation;
import java.nio.file.Path;

/** 源 Java 到目标 Java 的工作区快照传输端口；实现必须使用平台文件 WebSocket。 */
public interface PersonalWorkspaceRelocationTransferGateway {

    void transfer(
            PersonalWorkspaceRelocation relocation,
            Path archive,
            String snapshotSha256,
            long archiveSizeBytes,
            String traceId);
}
