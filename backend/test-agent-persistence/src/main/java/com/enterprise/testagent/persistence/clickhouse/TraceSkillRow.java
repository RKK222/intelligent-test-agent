package com.enterprise.testagent.persistence.clickhouse;

/** Trace 与已观测 Skill 名称的去重查询行；不承载调用参数、结果或其它正文。 */
public record TraceSkillRow(String traceId, String skillName) {
}
