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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

import java.io.IOException;

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

    // 接收秒杀消息
    @RabbitListener(queues = MQConfig.MIAOSHA_QUEUE)
    public void receive(String message, Channel channel, Message amqpMsg) throws IOException {
        long tag = amqpMsg.getMessageProperties().getDeliveryTag();
        try {
            log.info("receive message:" + message);
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
            log.warn("Duplicate order, treat as success: {}", message);
            channel.basicAck(tag, false);          // 幂等冲突 → ACK 丢弃
        } catch (TransientDataAccessException e) {
            channel.basicNack(tag, false, true);   // 临时性异常 → 允许重试
        } catch (Exception e) {
            channel.basicNack(tag, false, false);  // 不可恢复异常 → 直接丢/DLQ（建议配 DLX）
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
