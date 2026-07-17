package com.enterprise.testagent.domain.managedworkspace;

import com.enterprise.testagent.domain.support.DomainValidation;

/**
 * 工作区同步记录业务 ID。
 */
public record WorkspaceSyncRecordId(String value) {
    public WorkspaceSyncRecordId {
        value = DomainValidation.requireText(value, "workspaceSyncRecordId");
    }
}
