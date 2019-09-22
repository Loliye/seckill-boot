package com.seckill.cache.service;

import com.alibaba.fastjson.JSON;
import com.seckill.common.api.cache.RedisServiceApi;
import com.seckill.common.api.cache.vo.KeyPrefix;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Service(interfaceClass = RedisServiceApi.class)
@Slf4j
public class RedisServiceImpl implements RedisServiceApi
{

    @Autowired
    JedisPool jedisPool;

    @Override
    public <T> T get(KeyPrefix prefix, String key, Class<T> clazz)
    {
        Jedis jedis=null;
        try{
            jedis=jedisPool.getResource();
            String realKey=prefix.getPrefix()+key;
            String strValue=jedis.get(realKey);
            T objValue=stringToBean(strValue,clazz);
            log.info("redis get:{}",objValue);
            return objValue;
        }finally
        {
            retrunToPool(jedis);
        }
    }

    @Override
    public <T> boolean set(KeyPrefix prefix, String key, T value)
    {
        Jedis jedis=null;
        try{
            jedis=jedisPool.getResource();

            String strValue=beanToString(value);
            String realKey=prefix.getPrefix()+key;
            int expires=prefix.expireSeconds();

            if(expires<=0)
                jedis.set(realKey,strValue);
            else jedis.setex(realKey,expires,strValue);

            log.info("redis set: key-{},value-{}",realKey,strValue);
            return true;
        }finally
        {
            retrunToPool(jedis);
        }
    }

    @Override
    public boolean exists(KeyPrefix keyPrefix, String key)
    {
        Jedis jedis=null;
        try{
            jedis=jedisPool.getResource();
            String realKey=keyPrefix.getPrefix()+key;
            log.info("redis exist:key-{}",realKey);
            return jedis.exists(realKey);
        }finally
        {
            retrunToPool(jedis);
        }
    }

    @Override
    public long incr(KeyPrefix keyPrefix, String key)
    {
        Jedis jedis=null;
        try{
            jedis=jedisPool.getResource();
            String realKey=keyPrefix.getPrefix()+key;
            long result=jedis.incr(realKey);
            log.info("redis incr:key-{}",realKey);

            return result;
        }finally
        {
            retrunToPool(jedis);
        }
    }

    @Override
    public long decr(KeyPrefix keyPrefix, String key)
    {
        Jedis jedis=null;
        try{
            jedis= jedisPool.getResource();
            String realKey=keyPrefix.getPrefix()+key;
            long result=jedis.decr(realKey);
            log.info("redis decr:key-{}",realKey);
            return result;
        }finally
        {
            retrunToPool(jedis);
        }
    }

    @Override
    public boolean delete(KeyPrefix prefix, String key)
    {
        Jedis jedis=null;
        try{
            jedis=jedisPool.getResource();
            String realKey=prefix.getPrefix()+key;
            Long result=jedis.del(realKey);
            log.info("redis delete:{}",realKey);

            return result>0;
        }finally
        {
            retrunToPool(jedis);
        }
    }


    private static void retrunToPool(Jedis jedis)
    {
        if(jedis!=null)
            jedis.close();
    }

    public static <T> T stringToBean(String strValue,Class<T> tClass)
    {
        if(strValue==null||strValue.length()<=0||tClass==null)
            return null;

        if(tClass==int.class||tClass==Integer.class)
            return (T) Integer.valueOf(strValue);
        else if(tClass==long.class||tClass==Long.class)
            return (T) Long.valueOf(strValue);
        else if(tClass==String.class)
            return (T) strValue;
        else return JSON.toJavaObject(JSON.parseObject(strValue),tClass);
    }

    public static <T> String beanToString(T value)
    {
        if(value==null)
            return null;

        Class<?> tclass=value.getClass();
        if(tclass==int.class||tclass==Integer.class)
            return ""+value;
        else if(tclass==Long.class||tclass==long.class)
            return ""+value;
        else if(tclass==String.class)
            return (String) value;
        else return JSON.toJSONString(value);
    }

}
