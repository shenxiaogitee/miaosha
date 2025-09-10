package com.geekq.miaosha.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MQConfig {

    /**
     * /usr/sbin/rabbitmq-plugins enable rabbitmq_management
     * mq页面
     */
    public static final String MIAOSHA_QUEUE = "miaosha.queue";

    // ==== DLX / DLQ 定义 ====
    public static final String MIAOSHA_DLX_EXCHANGE = "miaosha.dlx";     // 死信交换机（Direct）
    public static final String MIAOSHA_DLQ          = "miaosha.queue.dlq"; // 死信队列
    public static final String MIAOSHA_DLQ_KEY      = "miaosha.dlq";       // 死信路由键

    /** 延迟重试队列（固定 5s 后回流主队列） */
    public static final String MIAOSHA_RETRY_5S_QUEUE = "miaosha.queue.retry.5s";

    /** 停车场队列（重试超限/人工处理） */
    public static final String MIAOSHA_PARKING_LOT = "miaosha.queue.parking";

    public static final String EXCHANGE_TOPIC = "exchange_topic";

    public static final String MIAOSHA_MESSAGE = "miaosha_mess";

    public static final String MIAOSHATEST = "miaoshatest";

    public static final String QUEUE = "queue";
    public static final String TOPIC_QUEUE1 = "topic.queue1";
    public static final String TOPIC_QUEUE2 = "topic.queue2";
    public static final String HEADER_QUEUE = "header.queue";
    public static final String TOPIC_EXCHANGE = "topicExchage";
    public static final String FANOUT_EXCHANGE = "fanoutxchage";
    public static final String HEADERS_EXCHANGE = "headersExchage";

    /**
     * Direct模式 交换机Exchange
     */
    @Bean
    public Queue queue() {
        return new Queue(QUEUE, true);
    }

    /**
     * 秒杀队列
     */
    //@Bean
    //public Queue miaoshaQueue() {
    //    return new Queue(MIAOSHA_QUEUE, true);
    //}

    /** 主队列：挂上 DLX（如果你主队列已存在且无 DLX，请先删队列或改新名再创建） */
    @Bean
    public Queue miaoshaQueue() {
        return QueueBuilder.durable(MIAOSHA_QUEUE)
                .withArgument("x-dead-letter-exchange", MIAOSHA_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MIAOSHA_DLQ_KEY)
                // （可选）消息过期转入 DLQ：.withArgument("x-message-ttl", 60000)
                // （可选）队列满了也会死信：.withArgument("x-max-length", 100000)
                .build();
    }

    /** 死信交换机（Direct） */
    @Bean
    public DirectExchange miaoshaDlxExchange() {
        return new DirectExchange(MIAOSHA_DLX_EXCHANGE);
    }

    /** 死信队列 */
    @Bean
    public Queue miaoshaDlq() {
        return QueueBuilder.durable(MIAOSHA_DLQ).build();
    }

    /** DLQ 绑定到 DLX */
    @Bean
    public Binding miaoshaDlqBinding() {
        return BindingBuilder.bind(miaoshaDlq())
                .to(miaoshaDlxExchange())
                .with(MIAOSHA_DLQ_KEY);
    }

    /** 延迟重试队列：消息在这里等 5 秒，到期后经默认交换机 "" 回流主队列 */
    @Bean
    public Queue miaoshaRetry5sQueue() {
        return QueueBuilder.durable(MIAOSHA_RETRY_5S_QUEUE)
                .withArgument("x-message-ttl", 5000)          // 固定 5 秒
                .withArgument("x-dead-letter-exchange", "")   // 回到默认交换机
                .withArgument("x-dead-letter-routing-key", MIAOSHA_QUEUE) // 定向回主队列
                .build();
    }

    /** 停车场队列：不绑定任何交换机，仅供人工/监控消费 */
    @Bean
    public Queue miaoshaParkingLot() {
        return QueueBuilder.durable(MIAOSHA_PARKING_LOT).build();
    }

    /**
     * 秒杀测试队列
     */
    @Bean
    public Queue miaoshaTestQueue() {
        return new Queue(MIAOSHATEST, true);
    }

    /**
     * Topic模式 交换机Exchange
     */
    @Bean
    public Queue topicQueue1() {
        return new Queue(TOPIC_QUEUE1, true);
    }

    @Bean
    public Queue topicQueue2() {
        return new Queue(TOPIC_QUEUE2, true);
    }

    @Bean
    public TopicExchange topicExchage() {
        return new TopicExchange(TOPIC_EXCHANGE);
    }

    @Bean
    public Binding topicBinding1() {
        return BindingBuilder.bind(topicQueue1()).to(topicExchage()).with("topic.key1");
    }

    @Bean
    public Binding topicBinding2() {
        return BindingBuilder.bind(topicQueue2()).to(topicExchage()).with("topic.#");
    }

    /**
     * Fanout模式 交换机Exchange
     */
    @Bean
    public FanoutExchange fanoutExchage() {
        return new FanoutExchange(FANOUT_EXCHANGE);
    }

    @Bean
    public Binding FanoutBinding1() {
        return BindingBuilder.bind(topicQueue1()).to(fanoutExchage());
    }

    @Bean
    public Binding FanoutBinding2() {
        return BindingBuilder.bind(topicQueue2()).to(fanoutExchage());
    }

    /**
     * Header模式 交换机Exchange
     */
    @Bean
    public HeadersExchange headersExchage() {
        return new HeadersExchange(HEADERS_EXCHANGE);
    }

    @Bean
    public Queue headerQueue1() {
        return new Queue(HEADER_QUEUE, true);
    }

    @Bean
    public Binding headerBinding() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("header1", "value1");
        map.put("header2", "value2");
        return BindingBuilder.bind(headerQueue1()).to(headersExchage()).whereAll(map).match();
    }


}
