package com.icbc.testagent.common.git;

/**
 * Git 命令失败的安全归因结果，message/hint 可直接返回给前端，stderr 仍只放在异常 details 中辅助排查。
 */
record GitCommandFailure(String type, String message, String hint) {
}
