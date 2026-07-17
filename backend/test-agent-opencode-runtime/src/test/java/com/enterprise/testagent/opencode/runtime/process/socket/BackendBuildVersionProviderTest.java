package com.enterprise.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

class BackendBuildVersionProviderTest {

    @Test
    void formatsUtcBuildTimeInAsiaShanghai() {
        Properties properties = new Properties();
        properties.setProperty("time", "2026-07-15T01:02:03Z");

        BackendBuildVersionProvider provider = new BackendBuildVersionProvider(new BuildProperties(properties));

        assertThat(provider.buildVersion()).isEqualTo("V20260715.090203");
    }

    @Test
    void returnsNullWhenBuildMetadataIsUnavailable() {
        BackendBuildVersionProvider provider = new BackendBuildVersionProvider((BuildProperties) null);

        assertThat(provider.buildVersion()).isNull();
    }
}
