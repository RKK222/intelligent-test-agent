package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import org.junit.jupiter.api.Test;

class XxlJobEndpointResolverTest {

    private final XxlJobEndpointResolver resolver = new XxlJobEndpointResolver();

    @Test
    void derivesLoopbackAdminAndExecutorAddressFromPlatformListenUrl() {
        XxlJobProperties properties = new XxlJobProperties();
        properties.getAdmin().setPort(19080);
        properties.getAdmin().setContextPath("/custom-xxl/");
        properties.getExecutor().setPort(19999);

        XxlJobEndpointResolver.Endpoints endpoints =
                resolver.resolve(properties, identity("http://10.23.4.5:8080"));

        assertThat(endpoints.adminAddress()).isEqualTo("http://127.0.0.1:19080/custom-xxl");
        assertThat(endpoints.executorAddress()).isEqualTo("http://10.23.4.5:19999");
    }

    @Test
    void preservesPlatformAdvertisedHostnameButUsesExecutorHttpPort() {
        XxlJobProperties properties = new XxlJobProperties();

        XxlJobEndpointResolver.Endpoints endpoints =
                resolver.resolve(properties, identity("https://backend-a.example.internal:8443"));

        assertThat(endpoints.adminAddress()).isEqualTo("http://127.0.0.1:18080/xxl-job-admin");
        assertThat(endpoints.executorAddress()).isEqualTo("http://backend-a.example.internal:9999");
    }

    @Test
    void rejectsListenUrlWithoutHostWithoutLeakingOriginalValue() {
        String invalidListenUrl = "invalid-listen-url?token=sensitive-value";
        XxlJobProperties properties = new XxlJobProperties();

        assertThatThrownBy(() -> resolver.resolve(properties, identity(invalidListenUrl)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("无法从平台后端监听地址派生 XXL executor 地址")
                .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain("sensitive-value"));
    }

    private static BackendInstanceIdentity identity(String listenUrl) {
        return new BackendInstanceIdentity() {
            @Override
            public String instanceId() {
                return "instance-a";
            }

            @Override
            public String linuxServerId() {
                return "linux-a";
            }

            @Override
            public String backendProcessId() {
                return "bjp_a";
            }

            @Override
            public String listenUrl() {
                return listenUrl;
            }
        };
    }
}
