package com.enterprise.testagent.integration.tcds;

/** TCDS 任务类型查询结果；界面展示 name，后续选择与映射使用稳定的 value。 */
public record TcdsTaskTypeOption(String name, String value) {
}
