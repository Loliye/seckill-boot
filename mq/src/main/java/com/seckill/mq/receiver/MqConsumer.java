package com.seckill.mq.receiver;

import com.seckill.common.api.cache.RedisServiceApi;
import com.seckill.common.api.cache.vo.OrderKeyPrefix;
import com.seckill.common.api.goods.GoodsServiceApi;
import com.seckill.common.api.goods.vo.GoodsVo;
import com.seckill.common.api.mq.vo.SkMessage;
import com.seckill.common.api.order.OrderServiceApi;
import com.seckill.common.api.seckill.SeckillServiceApi;

import com.seckill.common.api.user.vo.UserVo;
import com.seckill.common.domain.SeckillOrder;
import com.seckill.mq.config.MQConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * MQ 消费者
 */
@Service
@Slf4j
public class MqConsumer
{
    @Reference(interfaceClass = GoodsServiceApi.class)
    GoodsServiceApi goodsService;

    @Reference(interfaceClass = OrderServiceApi.class)
    OrderServiceApi orderService;

    @Reference(interfaceClass = RedisServiceApi.class)
    RedisServiceApi redisService;

    @Reference(interfaceClass = SeckillServiceApi.class)
    SeckillServiceApi seckillService;

    /**
     * 处理秒杀业务
     * @param message
     */
    @RabbitListener(queues = MQConfig.SECKILL_QUEUE)
    public void receiverSkInfo(SkMessage message)
    {
        log.info("MQ receiver message:{}",message);
        //获取秒杀用户  商品id
        UserVo user = message.getUser();
        long goodsId = message.getGoodsId();

        //获取商品以及信息
        GoodsVo goodsVo = goodsService.getGoodsVoByGoodsId(goodsId);
        Integer stockCount=goodsVo.getStockCount();

        if(stockCount<=0)
            return;

        //是否进行过秒杀
        SeckillOrder order=this.getSkOrderByUserIdAndGoodsId(user.getUuid(),goodsId);
        if(order!=null)
            return;

        //进行秒杀  1减库存 2写入订单 3 写入秒杀订单
        seckillService.seckill(user,goodsVo);

    }

    /**
     * 获取用户与商品的秒杀订单
     * 秒杀成功后  写入redis 以后直接从redis从读取
     * @param userId
     * @param goodsId
     * @return
     */
    private SeckillOrder getSkOrderByUserIdAndGoodsId(Long userId,Long goodsId)
    {
        SeckillOrder order=redisService.get(OrderKeyPrefix.SK_ORDER,":"+userId+"_"+goodsId,SeckillOrder.class);

        if(order!=null)
            return order;

        return orderService.getSeckillOrderByUserIdAndGoodsId(userId,goodsId);
    }
}
