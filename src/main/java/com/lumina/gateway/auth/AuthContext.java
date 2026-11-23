package com.lumina.gateway.auth;

/**
 * 鉴权通过后写入请求上下文的属性键。
 */
public final class AuthContext {

    public static final String ATTR_APP_KEY = "gateway.auth.appKey";
    public static final String ATTR_TENANT = "gateway.auth.tenant";

    private AuthContext() {
    }
}
