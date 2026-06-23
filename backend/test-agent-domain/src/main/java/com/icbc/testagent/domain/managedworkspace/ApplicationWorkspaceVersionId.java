package com.icbc.testagent.domain.managedworkspace;

import com.icbc.testagent.domain.support.DomainValidation;

/**
 * 应用版本工作区业务 ID。
 */
public record ApplicationWorkspaceVersionId(String value) {
    public ApplicationWorkspaceVersionId {
        value = DomainValidation.requireText(value, "applicationWorkspaceVersionId");
    }
}
