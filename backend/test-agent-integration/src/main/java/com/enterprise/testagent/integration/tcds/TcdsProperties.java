package com.enterprise.testagent.integration.tcds;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** TCDS 部署配置；地址必须由部署环境显式注入。 */
@ConfigurationProperties(prefix = "test-agent.third-party-api")
public class TcdsProperties {

    private String baseUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
