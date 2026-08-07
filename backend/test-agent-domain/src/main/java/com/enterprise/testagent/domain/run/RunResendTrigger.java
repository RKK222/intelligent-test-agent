package com.enterprise.testagent.domain.run;

/** 撤销重发的触发来源。 */
public enum RunResendTrigger {
    /** 用户在对话界面主动触发。 */
    MANUAL,
    /** 定时执行在根 session.error 后按退避策略触发。 */
    AUTOMATIC
}
