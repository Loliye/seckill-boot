package com.seckill.user.dao;

import com.seckill.user.domain.SeckillUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillUserMapper
{
    /**
     * 通过 phone 查询用户信息
     *
     * @param phone
     * @return
     */
    com.seckill.common.domain.SeckillUser getUserByPhone(@Param("phone") Long phone);

    /**
     * 更新用户信息
     *
     * @param updatedUser
     */
    @Update("UPDATE seckill_user SET password=#{password} WHERE id=#{id}")
    void updatePassword(SeckillUser updatedUser);


    /**
     * 插入一条用户信息到数据库中
     *
     * @param seckillUser
     * @return
     */
    long insertUser(com.seckill.common.domain.SeckillUser seckillUser);

    /**
     * 查询电话号码
     *
     * @param phone
     * @return
     */
    long findPhone(long phone);


}
