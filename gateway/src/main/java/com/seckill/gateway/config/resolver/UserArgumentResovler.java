package com.seckill.gateway.config.resolver;

import com.seckill.common.api.cache.RedisServiceApi;
import com.seckill.common.api.cache.vo.SkUserKeyPrefix;
import com.seckill.common.api.user.UserServiceApi;
import com.seckill.common.api.user.vo.UserVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.User;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * 解析请求  用户参数请求拦截
 */
@Service
@Slf4j
public class UserArgumentResovler implements HandlerMethodArgumentResolver
{

    @Reference(interfaceClass = RedisServiceApi.class)
    RedisServiceApi redisService;


    /**
     * 处理参数为UserVo的方法
     * 参数带有UserVo会调用下面的方面
     *
     * @param parameter
     * @return
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter)
    {
        log.info("supportsParameter");
        Class<?> paramerType = parameter.getParameterType();
        return paramerType == UserVo.class;
    }

    /**
     * 从session拿到UserVo
     *
     * @param parameter
     * @param mavContainer
     * @param webRequest
     * @param binderFactory
     * @return
     * @throws Exception
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception
    {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);

        log.info(request.getRequestURL() + " resolveArgument");
        String paramToken = request.getParameter(UserServiceApi.COOKIE_NAME_TOKEN);
        String cookieToken = getCookie(request,UserServiceApi.COOKIE_NAME_TOKEN);

        if(StringUtils.isEmpty(cookieToken)&&StringUtils.isEmpty(paramToken))
            return null;

        //拿到令牌
        String token=StringUtils.isEmpty(paramToken)?cookieToken:paramToken;
        if(StringUtils.isEmpty(token))
            return null;
        log.info("获取token：{}",token);

        //根据令牌返回用户
        UserVo userVo=redisService.get(SkUserKeyPrefix.TOKEN,token,UserVo.class);
        log.info("获取userVo:{}",userVo);

        if(userVo!=null)
            addCookie(response,token,userVo);

        return userVo;
    }

    /**
     * 将cookie写入redid中
     * @param response
     * @param token
     * @param user
     */
    public void addCookie(HttpServletResponse response,String token,UserVo user)
    {
        redisService.set(SkUserKeyPrefix.TOKEN,token,user);

        Cookie cookie=new Cookie(UserServiceApi.COOKIE_NAME_TOKEN,token);

        cookie.setMaxAge(SkUserKeyPrefix.TOKEN_EXPIRE);
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    public String getCookie(HttpServletRequest request, String cookieName)
    {
        log.info("resolveArgument: getCookieValue");
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0)
        {
            log.info("cookies is null");
            return null;
        }
        for(Cookie cookie:cookies)
        {
            if(cookie.getName().equals(cookieName))
                return cookie.getValue();
        }
        return null;

    }
}
