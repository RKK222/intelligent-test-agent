package com.enterprise.testagent.integration.toolbox;

/** 跨两套上游应用归一后的固定工具分类。 */
public enum ToolboxCategory {
    SECURITY("安全与加密"),
    ENCODING("编码与转换"),
    TEXT("文本处理"),
    DATA("数据处理"),
    WEB("Web 工具"),
    NETWORK("网络工具"),
    DEVELOPMENT("开发辅助"),
    IMAGE("图片处理"),
    AUDIO_VIDEO("音视频"),
    PDF("PDF"),
    DATE_TIME("日期与时间"),
    MATH("数学与测量"),
    OTHER("其他");

    private final String labelZh;

    ToolboxCategory(String labelZh) {
        this.labelZh = labelZh;
    }

    /** 返回固定中文分类名。 */
    public String labelZh() {
        return labelZh;
    }

    /** 用于目录契约测试显式拒绝未知分类。 */
    public static boolean isSupported(ToolboxCategory category) {
        return category != null;
    }
}
