package com.enterprise.testagent.integration.skillhub;

/** SkillHub 下载来源枚举；平台调用固定使用 channel=3。 */
public enum SkillHubDownloadChannel {
    PLATFORM(3);

    private final int code;

    SkillHubDownloadChannel(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
