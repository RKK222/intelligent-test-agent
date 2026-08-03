package com.enterprise.testagent.integration.workflow;

import java.security.SecureRandom;
import java.util.Base64;

final class SecureWorkflowCapabilityTokenFactory implements WorkflowCapabilityTokenFactory {
    private final SecureRandom random = new SecureRandom();

    @Override
    public String token(String prefix) {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
