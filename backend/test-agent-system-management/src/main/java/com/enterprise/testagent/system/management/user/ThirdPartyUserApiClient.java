package com.enterprise.testagent.system.management.user;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.system.management.config.ThirdPartyApiProperties;
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
    private static final String TOOL_ID_VALUE = "66136bfa5c1c6105572b0118880261d6";

    private final ThirdPartyApiProperties properties;
    private final RestTemplate restTemplate;

    public ThirdPartyUserApiClient(ThirdPartyApiProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    public UserManagementResponses.ThirdPartyUserInfoResponse getUserByLoginName(String userId) {
        try {
            String url = properties.getBaseUrl() + "/user/getUserByLoginName?userId=" + userId;
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_TOOL_ID, TOOL_ID_VALUE);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<UserManagementResponses.ThirdPartyUserInfoResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, UserManagementResponses.ThirdPartyUserInfoResponse.class);
            return response.getBody();
        } catch (Exception e) {
            LOGGER.error("Failed to fetch user info from third party API for userId: {}", userId, e);
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "获取用户信息失败");
        }
    }
}
