package com.enterprise.testagent.domain.appsource;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;

/** 配置管理只读使用的仓库磁盘身份守卫端口。 */
@FunctionalInterface
public interface AppSourceRepositoryHistory {

    /** slot、snapshot、operation 或 cleanup 任一历史存在时均返回 true。 */
    boolean hasRepositoryHistory(CodeRepositoryId repositoryId);
}
