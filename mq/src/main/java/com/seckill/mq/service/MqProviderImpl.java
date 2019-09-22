package com.seckill.mq.service;

import com.seckill.common.api.mq.MqProviderApi;
import com.seckill.common.api.mq.vo.SkMessage;
import com.seckill.mq.config.MQConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Service(interfaceClass = MqProviderApi.class)
@Slf4j
public class MqProviderImpl implements MqProviderApi, RabbitTemplate.ConfirmCallback
{

    @Autowired
    private RabbitTemplate rabbitTemplate;
    public MqProviderImpl(RabbitTemplate rabbitTemplate)
    {
        this.rabbitTemplate=rabbitTemplate;
        rabbitTemplate.setConfirmCallback(this);
    }

    /**
     * ack机制
     * @param correlationData
     * @param ack
     * @param cause
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause)
    {
        log.info("SkMessage UUID:{}",correlationData.getId());
        if(ack)
            log.info("消息消费成功");
        else log.debug("消息消费失败");

        if(cause!=null)
            log.info("CallBackConfig Cause:{}",cause);
    }

    @Override
    public void sendSkMessage(SkMessage message)
    {
        log.info("MQ send message:{}"+message);
        CorrelationData correlationData=new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(MQConfig.SECKILL_QUEUE,message,correlationData);
    }
}
