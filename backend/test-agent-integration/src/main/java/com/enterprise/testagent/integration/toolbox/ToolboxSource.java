package com.enterprise.testagent.integration.toolbox;

/** 首版锁定的离线工具套件来源。 */
public enum ToolboxSource {
    IT_TOOLS("IT-Tools"),
    OMNI_TOOLS("OmniTools");

    private final String displayName;

    ToolboxSource(String displayName) {
        this.displayName = displayName;
    }

    /** 返回目录卡片使用的来源名称。 */
    public String displayName() {
        return displayName;
    }
}
