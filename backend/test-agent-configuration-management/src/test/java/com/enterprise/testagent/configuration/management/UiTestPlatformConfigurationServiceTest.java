package com.enterprise.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UiTestPlatformConfigurationServiceTest {

    @Test
    void missingOrSentinelValueIsReportedAsUnconfigured() {
        CommonParameterValues values = mock(CommonParameterValues.class);
        when(values.resolvedValue(
                        UiTestPlatformConfigurationService.PARAMETER_ENGLISH_NAME,
                        ParameterPlatform.ALL))
                .thenReturn(Optional.empty(), Optional.of(" unconfigured "));
        UiTestPlatformConfigurationService service = new UiTestPlatformConfigurationService(values);

        assertThat(service.current().configured()).isFalse();
        assertThat(service.current().configured()).isFalse();
        assertThat(service.current().baseUrl()).isNull();
    }

    @Test
    void currentNormalizesConfiguredHttpAddress() {
        CommonParameterValues values = mock(CommonParameterValues.class);
        when(values.resolvedValue(
                        UiTestPlatformConfigurationService.PARAMETER_ENGLISH_NAME,
                        ParameterPlatform.ALL))
                .thenReturn(Optional.of(" https://ui.example.test:7788/ "));
        UiTestPlatformConfigurationService service = new UiTestPlatformConfigurationService(values);

        var configuration = service.current();

        assertThat(configuration.configured()).isTrue();
        assertThat(configuration.baseUrl()).isEqualTo("https://ui.example.test:7788");
    }

    @Test
    void managedValueAcceptsSentinelAndRejectsUnsafeOrAmbiguousAddresses() {
        assertThat(UiTestPlatformConfigurationService.normalizeManagedValue(" unconfigured "))
                .isEqualTo(UiTestPlatformConfigurationService.UNCONFIGURED);
        assertThat(UiTestPlatformConfigurationService.normalizeManagedValue("http://ui.example.test:7788///"))
                .isEqualTo("http://ui.example.test:7788");

        for (String invalid : new String[] {
                "", "ftp://ui.example.test", "http://user:secret@ui.example.test",
                "http://ui.example.test?target=x", "http://ui.example.test#fragment", "ui.example.test:7788"
        }) {
            assertThatThrownBy(() -> UiTestPlatformConfigurationService.normalizeManagedValue(invalid))
                    .isInstanceOfSatisfying(PlatformException.class, exception -> {
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                        assertThat(exception.getMessage()).isEqualTo(
                                "UI 测试执行平台地址必须是无用户信息、查询参数和片段的 HTTP/HTTPS 地址");
                    });
        }
    }

    @Test
    void invalidPersistedValueReturnsSafeUnavailableError() {
        CommonParameterValues values = mock(CommonParameterValues.class);
        String unsafeValue = "http://user:secret@ui.example.test";
        when(values.resolvedValue(
                        UiTestPlatformConfigurationService.PARAMETER_ENGLISH_NAME,
                        ParameterPlatform.ALL))
                .thenReturn(Optional.of(unsafeValue));
        UiTestPlatformConfigurationService service = new UiTestPlatformConfigurationService(values);

        assertThatThrownBy(service::current)
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
                    assertThat(exception.getMessage()).doesNotContain(unsafeValue).doesNotContain("secret");
                });
    }
}
