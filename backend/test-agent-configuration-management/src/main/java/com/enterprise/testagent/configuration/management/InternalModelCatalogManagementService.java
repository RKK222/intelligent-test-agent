package com.enterprise.testagent.configuration.management;

import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import java.util.List;

/** 模型目录管理 API 依赖的应用边界。 */
public interface InternalModelCatalogManagementService {

    List<InternalModelProviderModel> current(String providerId);

    List<InternalModelProviderModel> save(
            String providerId,
            InternalModelCatalogManagementApplicationService.UpdateCatalogCommand command,
            String traceId);
}
