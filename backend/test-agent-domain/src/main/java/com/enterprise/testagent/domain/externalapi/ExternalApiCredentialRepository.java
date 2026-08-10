package com.enterprise.testagent.domain.externalapi;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import java.util.List;
import java.util.Optional;

/** 外部 API 凭据持久化端口；关系型实现必须使用 MyBatis XML。 */
public interface ExternalApiCredentialRepository {
    List<ExternalApiCredential> findAll();

    PageResponse<ExternalApiCredential> findPage(String keyword, Boolean enabled, PageRequest pageRequest);

    Optional<ExternalApiCredential> findById(ExternalApiCredentialId credentialId);

    boolean existsByToolCode(String toolCode);

    void insert(ExternalApiCredential credential);

    void updateDetails(ExternalApiCredential credential);

    void updateKey(ExternalApiCredential credential);

    boolean delete(ExternalApiCredentialId credentialId);
}
