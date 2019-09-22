package com.seckill.mq.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class MQConfig
{
    public static final String SECKILL_QUEUE="seckill.queue";

    public static final String SK_ROUTING_KEY="routing.sk";

    @Bean
    public Queue seckillQueue()
    {
        return new Queue(SECKILL_QUEUE,true);
    }

    @Bean
    @Scope("prototype")
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory)
    {
        RabbitTemplate template=new RabbitTemplate(connectionFactory);
        return template;
    }
}
