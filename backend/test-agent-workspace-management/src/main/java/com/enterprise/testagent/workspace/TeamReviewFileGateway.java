package com.enterprise.testagent.workspace;

import com.enterprise.testagent.domain.team.TeamReviewModels.File;
import com.enterprise.testagent.domain.team.TeamReviewModels.Scope;
import com.enterprise.testagent.domain.team.TeamReviewModels.Source;
import java.util.List;

/** 协调节点到成员节点的文件端口；实现必须使用既有 route/ticket/WebSocket RPC。 */
public interface TeamReviewFileGateway {
    List<File> list(Scope scope, Source source, String path, String traceId);
    Object read(Scope scope, Source source, String path, String version, long offset, String traceId);
    void authorize(Scope scope);
}
