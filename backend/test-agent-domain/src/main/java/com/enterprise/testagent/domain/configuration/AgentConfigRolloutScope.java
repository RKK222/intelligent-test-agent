package com.enterprise.testagent.domain.configuration;

/** Agent 配置持久化排空范围；个人拉取只登记当前用户，不进入共享 Git 同步。 */
public enum AgentConfigRolloutScope {
    PUBLIC,
    APPLICATION,
    PERSONAL_APPLICATION
}
