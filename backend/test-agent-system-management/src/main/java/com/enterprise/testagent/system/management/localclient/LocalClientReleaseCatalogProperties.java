package com.enterprise.testagent.system.management.localclient;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 平台从前置 Nginx 同步客户端版本所需的非敏感可信根和公钥配置。 */
@ConfigurationProperties(prefix = "test-agent.local-client.version-management")
public class LocalClientReleaseCatalogProperties {

    private URI downloadBaseUrl;
    private String signingPublicKeyBase64;
    private String catalogPath = "catalog.json";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(30);

    public URI getDownloadBaseUrl() {
        return downloadBaseUrl;
    }

    public void setDownloadBaseUrl(URI downloadBaseUrl) {
        this.downloadBaseUrl = downloadBaseUrl;
    }

    public String getSigningPublicKeyBase64() {
        return signingPublicKeyBase64;
    }

    public void setSigningPublicKeyBase64(String signingPublicKeyBase64) {
        this.signingPublicKeyBase64 = signingPublicKeyBase64;
    }

    public String getCatalogPath() {
        return catalogPath;
    }

    public void setCatalogPath(String catalogPath) {
        this.catalogPath = catalogPath;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
