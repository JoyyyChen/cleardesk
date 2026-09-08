package com.cleardesk.constant;

/**
 * 用户常量
 */
public interface UserConstant {

    /**
     * Session 中保存的登录用户 id
     */
    String USER_LOGIN_STATE = "userLoginState";

    int DEFAULT_ROLE = 0;

    int ADMIN_ROLE = 1;

    int USER_STATUS_NORMAL = 0;

    int USER_STATUS_DISABLED = 1;

    long DEFAULT_PAGE_NUM = 1;

    long DEFAULT_PAGE_SIZE = 10;

    long MAX_PAGE_SIZE = 20;
}
