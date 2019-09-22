package com.seckill.gateway.config.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用户访问拦截的注解
 * 主要用于防止刷新功能的实现
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AccessLimit
{
    /**
     * 两次请求的最大有效时间间隔，即视两次请求为同一状态的时间间隔
     *
     * @return
     */
    int seconds();

    /**
     * 最大请求次数
     *
     * @return
     */
    int maxAccessCount();

    /**
     * 是否需要重新登录
     *
     * @return
     */
    boolean needLogin() default true;
}
