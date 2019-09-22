package com.seckill.order.service;

import com.seckill.common.api.cache.RedisServiceApi;
import com.seckill.common.api.cache.vo.OrderKeyPrefix;
import com.seckill.common.api.goods.vo.GoodsVo;
import com.seckill.common.api.order.OrderServiceApi;
import com.seckill.common.api.user.vo.UserVo;
import com.seckill.common.domain.OrderInfo;
import com.seckill.common.domain.SeckillOrder;
import com.seckill.order.dao.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service(interfaceClass = OrderServiceApi.class)
@Slf4j
public class OrderServiceImpl implements OrderServiceApi
{
    @Autowired
    OrderMapper orderMapper;

    @Reference(interfaceClass = RedisServiceApi.class)
    RedisServiceApi redisService;

    @Override
    public OrderInfo getOrderById(long orderId)
    {
        return orderMapper.getOrderById(orderId);
    }

    @Override
    public SeckillOrder getSeckillOrderByUserIdAndGoodsId(long userId, long goodsId)
    {
        return orderMapper.getSeckillOrderByUserIdAndGoodsId(userId,goodsId);
    }

    @Transactional
    @Override
    public OrderInfo createOrder(UserVo user, GoodsVo goods)
    {
        //订单信息
        OrderInfo orderInfo = new OrderInfo();
        //秒杀订单信息
        SeckillOrder seckillOrder = new SeckillOrder();

        orderInfo.setCreateDate(new Date());
        orderInfo.setDeliveryAddrId(0L);
        orderInfo.setGoodsCount(1);// 订单中商品的数量
        orderInfo.setGoodsId(goods.getId());
        orderInfo.setGoodsName(goods.getGoodsName());
        orderInfo.setGoodsPrice(goods.getSeckillPrice());// 秒杀价格
        orderInfo.setOrderChannel(1);
        orderInfo.setStatus(0);
        orderInfo.setUserId(user.getUuid());


        long orderId=orderMapper.insert(orderInfo);
        log.debug("订单信息写入order_info表中：{}",orderId);

        seckillOrder.setGoodsId(goods.getId());
        seckillOrder.setOrderId(orderInfo.getId());
        seckillOrder.setUserId(user.getUuid());
        orderMapper.insertSeckillOrder(seckillOrder);
        log.debug("将秒杀订单写入seckill_order表中");

        //秒杀订单写入redis中
        redisService.set(OrderKeyPrefix.SK_ORDER,":"+user.getUuid()+"_"+goods.getId(),seckillOrder);

        return orderInfo;

    }
}
