package com.seckill.gateway.config;

import com.seckill.gateway.config.access.AccessInterceptor;
import com.seckill.gateway.config.resolver.UserArgumentResovler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * web配置
 */
@Configuration
@Slf4j
public class WebConfig implements WebMvcConfigurer
{

    @Autowired
    UserArgumentResovler userArgumentResovler;

    @Autowired
    AccessInterceptor accessInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        log.info("添加请求拦截器");
        registry.addInterceptor(accessInterceptor);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers)
    {
        log.info("添加自定义参数解析器");
        resolvers.add(userArgumentResovler);
    }
}
