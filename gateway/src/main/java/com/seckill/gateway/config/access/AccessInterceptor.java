package com.seckill.gateway.config.access;

import com.alibaba.fastjson.JSON;
import com.seckill.common.api.cache.RedisServiceApi;
import com.seckill.common.api.cache.vo.AccessKeyPrefix;
import com.seckill.common.api.cache.vo.SkUserKeyPrefix;
import com.seckill.common.api.user.UserServiceApi;
import com.seckill.common.api.user.vo.UserVo;
import com.seckill.common.result.CodeMsg;
import com.seckill.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

@Service
@Slf4j
public class AccessInterceptor extends HandlerInterceptorAdapter
{


    @Reference(interfaceClass = RedisServiceApi.class)
    RedisServiceApi redisService;

    @Reference(interfaceClass = UserServiceApi.class)
    UserServiceApi userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception
    {
        log.info(request.getRequestURL() + " 拦截请求");

        //拦截方法
        if (handler instanceof HandlerMethod)
        {
            log.info("HandlerMethod:{}", ((HandlerMethod) handler).getMethod().getName());
            //获取用户对象
            UserVo user = this.getUser(request, response);
            //保存用户
            UserContext.setUser(user);

            //获取有注解@Accesslimit的方法  没有直接返回true
            HandlerMethod method= (HandlerMethod) handler;
            AccessLimit accessLimit=method.getMethodAnnotation(AccessLimit.class);

            if(accessLimit==null)
                return true;

            int seconds = accessLimit.seconds();
            int maxCount = accessLimit.maxAccessCount();
            boolean needLogin = accessLimit.needLogin();
            String key=request.getRequestURI();

            if(needLogin)
            {
                if(user==null)
                {
                    this.render(response, CodeMsg.SESSION_ERROR);
                    return false;
                }
                key="_"+user.getPhone();

            }else{

            }
            //设置缓存过期时间
            AccessKeyPrefix accessKeyPrefix=AccessKeyPrefix.withExpire(seconds);
            Integer count=redisService.get(accessKeyPrefix,key,Integer.class);

            if(count ==null)
                redisService.set(accessKeyPrefix,key,1);
            else if(count<maxCount)
                redisService.incr(accessKeyPrefix,key);
            else {
                this.render(response,CodeMsg.ACCESS_LIMIT_REACHED);
                return false;
            }


        }
        return true;
    }

    private void render(HttpServletResponse response, CodeMsg codeMsg) throws IOException
    {
        response.setContentType("application/json charset=UTF-8");
        OutputStream out=response.getOutputStream();
        String string= JSON.toJSONString(Result.error(codeMsg));

        out.write(string.getBytes("UTF-8"));
        out.flush();
        out.close();
    }

    private UserVo getUser(HttpServletRequest request, HttpServletResponse response)
    {
        log.info(request.getRequestURL() + " 获取UserVo对象");

        String paramToken = request.getParameter(UserServiceApi.COOKIE_NAME_TOKEN);
        String cookieToken = this.getCookieValue(request, UserServiceApi.COOKIE_NAME_TOKEN);

        if (StringUtils.isEmpty(paramToken) && StringUtils.isEmpty(cookieToken))
            return null;

        String token = StringUtils.isEmpty(paramToken) ? cookieToken : paramToken;

        if (StringUtils.isEmpty(token))
            return null;

        UserVo userVo = redisService.get(SkUserKeyPrefix.TOKEN,token,UserVo.class);

        if(userVo!=null)
            addCookie(response,token,userVo);

        return userVo;


    }

    private String getCookieValue(HttpServletRequest request, String cookieName)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0)
            return null;

        for (Cookie cookie : cookies)
        {
            if (cookie.getName().equals(cookieName))
                return cookie.getValue();
        }
        return null;
    }

    private void addCookie(HttpServletResponse response, String token, UserVo user) {

        redisService.set(SkUserKeyPrefix.TOKEN, token, user);

        Cookie cookie = new Cookie(UserServiceApi.COOKIE_NAME_TOKEN, token);
        // 客户端cookie的有限期和缓存中的cookie有效期一致
        cookie.setMaxAge(SkUserKeyPrefix.TOKEN.expireSeconds());
        cookie.setPath("/");
        response.addCookie(cookie);
    }
}
