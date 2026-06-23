package com.icbc.testagent.domain.managedworkspace;

import com.icbc.testagent.domain.support.DomainValidation;

/**
 * 个人工作区业务 ID。
 */
public record PersonalWorkspaceId(String value) {
    public PersonalWorkspaceId {
        value = DomainValidation.requireText(value, "personalWorkspaceId");
    }
}
