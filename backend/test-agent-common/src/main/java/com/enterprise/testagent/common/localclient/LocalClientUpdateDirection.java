package com.enterprise.testagent.common.localclient;

/** 客户端从当前发布版本切换到目标发布版本时的方向。 */
public enum LocalClientUpdateDirection {
    UPDATE,
    ROLLBACK,
    SAME
}
