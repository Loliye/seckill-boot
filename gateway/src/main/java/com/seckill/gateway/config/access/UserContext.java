package com.seckill.gateway.config.access;

import com.seckill.common.api.user.vo.UserVo;
import lombok.Getter;
import lombok.Setter;

/**
 * 用于保存用户 作为键值对
 */

public class UserContext
{
    private static ThreadLocal<UserVo> userHolder = new ThreadLocal<>();

    public static UserVo getUser()
    {
        return userHolder.get();
    }

    public static void setUser(UserVo user)
    {

        userHolder.set(user);
    }
}
