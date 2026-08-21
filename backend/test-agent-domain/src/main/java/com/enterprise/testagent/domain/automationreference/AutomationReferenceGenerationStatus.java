package com.enterprise.testagent.domain.automationreference;

/** 自动化引用不可变配置代次的生命周期状态。 */
public enum AutomationReferenceGenerationStatus {
    SYNCHRONIZING,
    READY,
    FAILED,
    RETIRED
}
