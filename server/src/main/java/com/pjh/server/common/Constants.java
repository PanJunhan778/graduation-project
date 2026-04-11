package com.pjh.server.common;

public final class Constants {

    private Constants() {}

    /** JWT 扩展字段 key */
    public static final String JWT_USER_ID_KEY = "userId";
    public static final String JWT_ROLE_KEY = "role";
    public static final String JWT_COMPANY_ID_KEY = "companyId";

    /** 角色枚举值 */
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_STAFF = "staff";

    /** 登录失败锁定：最大重试次数 */
    public static final int LOGIN_MAX_RETRY = 5;
    /** 登录失败锁定时长（分钟） */
    public static final int LOGIN_LOCK_MINUTES = 15;

    /** 密码正则：至少8位，含大写、小写、数字 */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).{8,}$";
}
