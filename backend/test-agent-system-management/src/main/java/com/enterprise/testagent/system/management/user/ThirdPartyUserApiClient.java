package com.enterprise.testagent.system.management.user;

import com.enterprise.testagent.system.management.config.ThirdPartyApiProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class ThirdPartyUserApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThirdPartyUserApiClient.class);
    private static final String HEADER_TOOL_ID = "toolId";
    private static final String TOOL_ID_VALUE = "66f36bfa5c1c6105572b0118880261d6";

    private final ThirdPartyApiProperties properties;
    private final RestTemplate restTemplate;

    public ThirdPartyUserApiClient(ThirdPartyApiProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /**
     * 调用第三方用户信息接口。
     *
     * <p>超时 30 秒，请求失败时返回空 Optional，由调用方决定是否降级到原逻辑。
     */
    public Optional<UserManagementResponses.ThirdPartyUserInfoResponse> getUserByLoginName(String userId) {
        try {
//            /user/getUserByLoginName
            String url = properties.getBaseUrl() + "/user/getUserByLoginName?userId=" + userId;
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_TOOL_ID, TOOL_ID_VALUE);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<UserManagementResponses.ThirdPartyUserInfoResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, UserManagementResponses.ThirdPartyUserInfoResponse.class);
            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            LOGGER.error("Failed to fetch user info from third party API for userId: {}", userId, e);
            return Optional.empty();
        }
    }
}
