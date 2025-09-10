package com.geekq.miaosha.rabbitmq;

import com.geekq.miaosha.domain.MiaoshaOrder;
import com.geekq.miaosha.domain.MiaoshaUser;
import com.geekq.miaosha.redis.RedisService;
import com.geekq.miaosha.service.GoodsService;
import com.geekq.miaosha.service.MiaoShaMessageService;
import com.geekq.miaosha.service.MiaoshaService;
import com.geekq.miaosha.service.OrderService;
import com.geekq.miaosha.vo.GoodsVo;
import com.geekq.miaosha.vo.MiaoShaMessageVo;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class MQReceiver {

    private static Logger log = LoggerFactory.getLogger(MQReceiver.class);

    @Autowired
    RedisService redisService;

    @Autowired
    GoodsService goodsService;

    @Autowired
    OrderService orderService;

    @Autowired
    MiaoshaService miaoshaService;

    @Autowired
    MiaoShaMessageService messageService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    /**
     * 监听死信队列，写日志/落库/报警，通常直接 ACK（AUTO 模式下方法返回即 ACK）
     */
    @RabbitListener(queues = MQConfig.MIAOSHA_DLQ)
    public void consumeDlq(String body) {
        log.error("[DLQ] 收到死信：{}", body);
        // TODO: 这里可以落库 + 告警（如钉钉/飞书/邮件），或转停车场等
    }

    // 最大重试次数
    private static final int MAX_RETRY = 3;

    // 接收秒杀消息
    @RabbitListener(queues = MQConfig.MIAOSHA_QUEUE)
    public void receive(String message, Channel channel, Message amqpMsg) throws IOException {
        long tag = amqpMsg.getMessageProperties().getDeliveryTag();
        try {
            log.info("receive message:" + message);

            // 触发条件你自定：比如消息体包含某个标记时模拟瞬时异常
            if (message.contains("simulate:transient")) {
                throw new TransientDataAccessResourceException("mock: transient glitch");
            }

            MiaoshaMessage mm = RedisService.stringToBean(message, MiaoshaMessage.class);
            MiaoshaUser user = mm.getUser();
            long goodsId = mm.getGoodsId();

            // 查询商品实际库存
            GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
            int stock = goods.getStockCount();
            if (stock <= 0) {
                channel.basicAck(tag, false);      // 没库存/商品不存在 → 当作已处理
                return;
            }
            //判断是否已经秒杀到了
            MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(Long.valueOf(user.getNickname()), goodsId);
            if (order != null) {
                channel.basicAck(tag, false);      // 已下单 → 幂等
                return;
            }
            // 秒杀到了且有库存，减库存，下订单
            // 减库存 下订单 写入秒杀订单
            miaoshaService.miaosha(user, goods);
            channel.basicAck(tag, false);          // 成功 → ACK
        } catch (DuplicateKeyException e) {
            // 幂等冲突：已下过单，当作成功
            log.warn("Duplicate order, treat as success: {}", message);
            channel.basicAck(tag, false);
            //channel.basicNack(tag, false, false);  // 幂等冲突 → DLQ（建议配 DLX）
        } catch (TransientDataAccessException e) {
            // 认为是临时性异常（可重试）
            int retry = currentRetry(amqpMsg);
            if (retry < MAX_RETRY) {
                log.warn("Transient error, retry {}/{} after delay: {}", retry + 1, MAX_RETRY, message);
                // 递增重试计数，发到延迟队列
                rabbitTemplate.convertAndSend("", MQConfig.MIAOSHA_RETRY_5S_QUEUE, message, m -> {
                    m.getMessageProperties().getHeaders().put("x-retry", retry + 1);
                    return m;
                });
                channel.basicAck(tag, false);       // ACK 掉当前这条
            } else {
                // 超限：进停车场并 ACK
                log.error("Retry exceeded ({}). Send to parking-lot: {}", MAX_RETRY, message, e);
                rabbitTemplate.convertAndSend("", MQConfig.MIAOSHA_PARKING_LOT, message);
                channel.basicAck(tag, false);
            }
        } catch (Exception e) {
            // 非可重试异常：直接死信（由 DLX 转到 DLQ）
            log.error("Non-retryable error, dead-letter: {}", message, e);
            channel.basicNack(tag, false, false);   // 不重入队 ⇒ 走 DLX → DLQ
        }
    }

    private int currentRetry(Message msg) {
        Map<String, Object> h = msg.getMessageProperties().getHeaders();
        Object v = h.get("x-retry");
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ignore) {
            return 0;
        }
    }


    @RabbitListener(queues = MQConfig.MIAOSHATEST)
    public void receiveMiaoShaMessage(Message message, Channel channel) throws IOException {
        log.info("接受到的消息为:{}", message);
        String messRegister = new String(message.getBody(), "UTF-8");
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), true);
        MiaoShaMessageVo msm = RedisService.stringToBean(messRegister, MiaoShaMessageVo.class);
        messageService.insertMs(msm);
    }
}
