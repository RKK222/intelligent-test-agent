package com.enterprise.testagent.domain.automationreference;

import java.util.List;

/**
 * Run 派发前已经完成工作树对账的自动化引用快照。
 *
 * <p>这里只保存应用级逻辑身份和固定代次，不携带物理目录，也不参与提示词拼装。
 */
public record AutomationReferenceRunPreparation(
        List<ApplicationAutomationReferenceRunLease> leases,
        List<String> warnings,
        boolean configurationChanged) {

    public AutomationReferenceRunPreparation {
        leases = leases == null ? List.of() : List.copyOf(leases);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static AutomationReferenceRunPreparation empty() {
        return new AutomationReferenceRunPreparation(List.of(), List.of(), false);
    }
}
