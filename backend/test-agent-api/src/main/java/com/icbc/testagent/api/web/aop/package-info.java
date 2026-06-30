/**
 * API 横切关注点包，包含日志切面、脱敏工具等。
 *
 * <p>本包提供：
 * <ul>
 *   <li>{@link com.icbc.testagent.api.web.aop.ApiLoggingAspect}：API 日志切面，统一记录所有 Controller 方法的入口和出口日志</li>
 *   <li>{@link com.icbc.testagent.api.web.aop.SensitiveDataMasker}：敏感数据脱敏工具</li>
 * </ul>
 */
package com.icbc.testagent.api.web.aop;
