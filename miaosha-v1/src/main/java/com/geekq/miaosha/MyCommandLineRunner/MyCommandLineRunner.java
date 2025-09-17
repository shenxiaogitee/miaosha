package com.geekq.miaosha.MyCommandLineRunner;

import com.geekq.miaosha.rabbitmq.MQConfig;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class MyCommandLineRunner implements CommandLineRunner {

    @Autowired
    AmqpTemplate amqpTemplate;

    @Override
    public void run(String... args) throws Exception {

        ExecutorService executorService = Executors.newFixedThreadPool(8);
        String msg = "{\"goodsId\":2,\"user\":{\"id\":18912341245,\"loginCount\":0,\"nickname\":\"17352437166\",\"password\":\"b7797cce01b4b131b433b6acf4add449\",\"registerDate\":1547185469000,\"salt\":\"1a2b3c4d\"}}";
        for (int i = 0; i < 10000; i++) {
            executorService.submit(() -> {
                amqpTemplate.convertAndSend(MQConfig.MIAOSHA_QUEUE, msg);
            });
        }

    }
}
