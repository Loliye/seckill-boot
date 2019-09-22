package com.seckill.gateway.controller;

import com.seckill.common.api.cache.vo.SkUserKeyPrefix;
import com.seckill.common.api.user.UserServiceApi;
import com.seckill.common.api.user.vo.LoginVo;
import com.seckill.common.api.user.vo.RegisterVo;
import com.seckill.common.result.CodeMsg;
import com.seckill.common.result.Result;
import com.seckill.gateway.exception.GlobalException;
import com.sun.org.apache.xpath.internal.operations.Bool;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

@Controller
@RequestMapping("/user")
@Slf4j
public class UserController
{
    @Reference(interfaceClass = UserServiceApi.class)
    UserServiceApi userService;

    @RequestMapping(value = "/index", method = RequestMethod.GET)
    public String index()
    {
        log.info("首页接口");
        return "login";// login页面
    }

    @PostMapping("/login")
    @ResponseBody
    public Result<Boolean> login(HttpServletResponse response, @Valid LoginVo loginVo)
    {
        String token=userService.login(loginVo);
        log.info("token:{}",token);

        Cookie cookie=new Cookie(UserServiceApi.COOKIE_NAME_TOKEN,token);
        cookie.setMaxAge(SkUserKeyPrefix.TOKEN.expireSeconds());
        cookie.setPath("/");
        response.addCookie(cookie);

        return Result.success(true);
    }

    @GetMapping("/doRegister")
    public String doRegister()
    {
        log.info("doRegister()");
        return "register";
    }

    @PostMapping("/register")
    public Result<Boolean> register(RegisterVo registerVo)
    {
        log.info("registerVo:{}",registerVo);
        if(registerVo==null)
            throw new GlobalException(CodeMsg.FILL_REGISTER_INFO);

        CodeMsg codeMsg=userService.register(registerVo);

        return Result.info(codeMsg);
    }

}
