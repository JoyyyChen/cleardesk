package com.cleardesk.context;

import com.cleardesk.model.domain.User;

/**
 * 当前请求登录用户
 */
public final class UserHolder {

    private static final ThreadLocal<User> LOCAL = new ThreadLocal<>();

    private UserHolder() {
    }

    public static void set(User user) {
        LOCAL.set(user);
    }

    public static User get() {
        return LOCAL.get();
    }

    public static void remove() {
        LOCAL.remove();
    }
}
