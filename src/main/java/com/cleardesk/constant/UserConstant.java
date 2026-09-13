package com.cleardesk.constant;

/**
 * 用户常量
 */
public interface UserConstant {

    /**
     * Session 中保存的登录用户 id
     */
    String USER_LOGIN_STATE = "userLoginState";

    /**
     * 角色 0-普通用户
     */
    int DEFAULT_ROLE = 0;

    /**
     * 角色 1-管理员
     */
    int ADMIN_ROLE = 1;

    /**
     * 状态 0-正常
     */
    int USER_STATUS_NORMAL = 0;

    /**
     * 状态 1-禁用
     */
    int USER_STATUS_DISABLED = 1;

    /**
     * 默认页码
     */
    long DEFAULT_PAGE_NUM = 1;

    /**
     * 默认每页条数
     */
    long DEFAULT_PAGE_SIZE = 10;

    /**
     * 每页条数上限
     */
    long MAX_PAGE_SIZE = 20;
}
