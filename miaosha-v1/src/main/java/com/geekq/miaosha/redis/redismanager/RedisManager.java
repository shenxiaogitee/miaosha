package com.geekq.miaosha.redis.redismanager;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisManager {

    private static JedisPool jedisPool;

    static {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        // 增加最大等待时间，从20毫秒改为2000毫秒
        jedisPoolConfig.setMaxWaitMillis(2000);
        // 增加最大连接数
        jedisPoolConfig.setMaxTotal(1000);
        // 增加最大空闲连接数
        jedisPoolConfig.setMaxIdle(500);
        // 设置最小空闲连接数
        jedisPoolConfig.setMinIdle(50);
        // 设置测试连接
        jedisPoolConfig.setTestOnBorrow(true);
        jedisPool = new JedisPool(jedisPoolConfig, "117.72.214.81" , 16379);
    }

    public static Jedis getJedis() throws Exception {
        if (null != jedisPool) {
            return jedisPool.getResource();
        }
        throw new Exception("Jedispool was not init !!!");
    }


}
