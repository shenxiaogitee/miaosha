# Miaosha-v1 秒杀系统架构文档

## 1. 项目概述

### 1.1 项目简介
Miaosha-v1是一个基于Spring Boot的高并发秒杀系统，采用Redis缓存、RabbitMQ消息队列等技术栈，实现了完整的秒杀业务流程。

### 1.2 技术栈
- **后端框架**: Spring Boot 2.x
- **数据库**: MySQL 5.7+
- **缓存**: Redis 6.x
- **消息队列**: RabbitMQ 3.x
- **模板引擎**: Thymeleaf
- **连接池**: Druid
- **ORM框架**: MyBatis

## 2. 系统架构设计

### 2.1 整体架构
```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   用户端    │───▶│  Web服务层  │───▶│  业务服务层  │
└─────────────┘    └─────────────┘    └─────────────┘
                           │                   │
                           ▼                   ▼
                   ┌─────────────┐    ┌─────────────┐
                   │ Redis缓存层 │    │ RabbitMQ队列│
                   └─────────────┘    └─────────────┘
                           │                   │
                           ▼                   ▼
                   ┌─────────────────────────────────┐
                   │        MySQL数据库层           │
                   └─────────────────────────────────┘
```

### 2.2 核心模块
- **控制器层**: 处理HTTP请求，参数验证
- **服务层**: 业务逻辑实现，事务管理
- **缓存层**: Redis数据缓存，提升性能
- **消息队列**: 异步处理，削峰填谷
- **数据访问层**: MySQL数据持久化

## 3. 核心业务流程

### 3.1 完整秒杀流程
```
用户登录 → 商品展示 → 获取秒杀路径 → 提交秒杀请求 → 轮询结果 → 订单生成
```

### 3.2 详细流程说明

#### 3.2.1 用户认证流程
1. 用户提交登录信息（手机号+密码）
2. 服务端验证用户凭据
3. 生成用户会话Token
4. Redis缓存用户登录状态

#### 3.2.2 秒杀请求流程
1. **前置验证**
   - 用户登录状态检查
   - 动态路径验证
   - 重复秒杀检查
   - 接口访问频率限制

2. **库存预扣**
   - 检查本地内存标记
   - Redis原子递减操作
   - 库存不足时快速失败

3. **异步处理**
   - 构造秒杀消息对象
   - 发送RabbitMQ消息
   - 立即返回排队状态

#### 3.2.3 订单处理流程
1. **消息消费**
   - MQ消费者接收秒杀消息
   - 再次验证库存和重复下单
   - 执行数据库事务操作

2. **订单生成**
   - 扣减商品库存
   - 创建订单信息
   - 生成秒杀订单记录

## 4. 关键技术实现

### 4.1 高并发处理

#### 4.1.1 多级缓存策略
```java
// 本地内存标记 -> Redis缓存 -> 数据库
boolean over = localOverMap.get(goodsId);  // 本地内存
Long stock = redisService.decr(GoodsKey.getMiaoshaGoodsStock, "" + goodsId);  // Redis
```

#### 4.1.2 异步消息处理
```java
// 发送异步消息
MiaoshaMessage mm = new MiaoshaMessage();
mm.setGoodsId(goodsId);
mm.setUser(user);
mqSender.sendMiaoshaMessage(mm);
```

### 4.2 防刷限流机制

#### 4.2.1 接口访问限制
```java
@AccessLimit(seconds = 5, maxCount = 5, needLogin = true)
public ResultGeekQ<Integer> miaosha(...) {
    // 5秒内最多访问5次
}
```

#### 4.2.2 动态路径防护
```java
// 生成随机MD5路径
String str = MD5Utils.md5(UUIDUtil.uuid() + "123456");
redisService.set(MiaoshaKey.getMiaoshaPath, "" + user.getNickname() + "_" + goodsId, str);
```

#### 4.2.3 图形验证码
```java
// 数学运算验证码
String verifyCode = generateVerifyCode(rdm);  // 如: "3+2*4"
int rnd = calc(verifyCode);  // 计算结果: 11
redisService.set(MiaoshaKey.getMiaoshaVerifyCode, user.getNickname() + "," + goodsId, rnd);
```

### 4.3 数据一致性保障

#### 4.3.1 事务管理
```java
@Transactional
public OrderInfo miaosha(MiaoshaUser user, GoodsVo goods) {
    // 减库存和下订单在同一事务中
    boolean success = goodsService.reduceStock(goods);
    if (success) {
        return orderService.createOrder(user, goods);
    }
    return null;
}
```

#### 4.3.2 Redis原子操作
```java
// 使用Redis DECR命令保证原子性
Long stock = redisService.decr(GoodsKey.getMiaoshaGoodsStock, "" + goodsId);
if (stock < 0) {
    // 库存不足，标记商品售罄
    localOverMap.put(goodsId, true);
}
```

## 5. 数据库设计

### 5.1 核心表结构

#### 5.1.1 用户表 (miaosha_user)
```sql
CREATE TABLE miaosha_user (
    id BIGINT PRIMARY KEY,
    nickname VARCHAR(255),
    password VARCHAR(32),
    salt VARCHAR(10),
    head VARCHAR(128),
    register_date DATETIME,
    last_login_date DATETIME,
    login_count INT
);
```

#### 5.1.2 商品表 (goods)
```sql
CREATE TABLE goods (
    id BIGINT PRIMARY KEY,
    goods_name VARCHAR(16),
    goods_title VARCHAR(64),
    goods_img VARCHAR(64),
    goods_detail LONGTEXT,
    goods_price DECIMAL(10,2),
    goods_stock INT
);
```

#### 5.1.3 秒杀商品表 (miaosha_goods)
```sql
CREATE TABLE miaosha_goods (
    id BIGINT PRIMARY KEY,
    goods_id BIGINT,
    miaosha_price DECIMAL(10,2),
    stock_count INT,
    start_date DATETIME,
    end_date DATETIME
);
```

#### 5.1.4 订单表 (order_info)
```sql
CREATE TABLE order_info (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    goods_id BIGINT,
    delivery_addr_id BIGINT,
    goods_name VARCHAR(16),
    goods_count INT,
    goods_price DECIMAL(10,2),
    order_channel TINYINT,
    status TINYINT,
    create_date DATETIME,
    pay_date DATETIME
);
```

#### 5.1.5 秒杀订单表 (miaosha_order)
```sql
CREATE TABLE miaosha_order (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    order_id BIGINT,
    goods_id BIGINT,
    UNIQUE KEY u_uid_gid (user_id, goods_id)
);
```

## 6. Redis缓存策略

### 6.1 缓存Key设计
```java
// 商品库存缓存
GoodsKey.getMiaoshaGoodsStock + goodsId

// 用户会话缓存
MiaoshaUserKey.token + token

// 秒杀路径缓存
MiaoshaKey.getMiaoshaPath + userId + "_" + goodsId

// 验证码缓存
MiaoshaKey.getMiaoshaVerifyCode + userId + "," + goodsId

// 商品售罄标记
MiaoshaKey.isGoodsOver + goodsId
```

### 6.2 缓存更新策略
- **商品信息**: 启动时预热，定时刷新
- **库存数据**: 实时更新，原子操作
- **用户会话**: 登录时写入，过期自动清理
- **临时数据**: 设置TTL，自动过期

## 7. RabbitMQ消息队列

### 7.1 队列配置
```java
@Configuration
public class MQConfig {
    public static final String MIAOSHA_QUEUE = "miaosha.queue";
    public static final String MIAOSHATEST = "miaoshatest";
    
    @Bean
    public Queue miaoshaQueue() {
        return new Queue(MIAOSHA_QUEUE, true);  // 持久化队列
    }
}
```

### 7.2 消息生产者
```java
@Service
public class MQSender {
    @Autowired
    AmqpTemplate amqpTemplate;
    
    public void sendMiaoshaMessage(MiaoshaMessage mm) {
        String msg = RedisService.beanToString(mm);
        amqpTemplate.convertAndSend(MQConfig.MIAOSHA_QUEUE, msg);
    }
}
```

### 7.3 消息消费者
```java
@RabbitListener(queues = MQConfig.MIAOSHA_QUEUE)
public void receive(String message) {
    MiaoshaMessage mm = RedisService.stringToBean(message, MiaoshaMessage.class);
    // 处理秒杀逻辑
    miaoshaService.miaosha(mm.getUser(), goods);
}
```

## 8. 性能优化策略

### 8.1 系统优化
- **连接池优化**: Druid连接池配置
- **JVM调优**: 堆内存、GC参数优化
- **数据库优化**: 索引设计、SQL优化
- **缓存预热**: 系统启动时预加载热点数据

### 8.2 业务优化
- **库存预扣**: Redis原子操作避免超卖
- **本地标记**: 减少无效Redis访问
- **异步处理**: 提升用户体验
- **快速失败**: 库存不足时立即返回

## 9. 监控与运维

### 9.1 关键指标监控
- **QPS/TPS**: 系统吞吐量
- **响应时间**: 接口响应延迟
- **错误率**: 系统异常比例
- **资源使用**: CPU、内存、网络

### 9.2 日志管理
- **业务日志**: 秒杀流程关键节点
- **错误日志**: 异常信息记录
- **性能日志**: 慢查询、慢接口
- **访问日志**: 用户行为分析

## 10. 部署说明

### 10.1 环境要求
- **JDK**: 1.8+
- **MySQL**: 5.7+
- **Redis**: 6.0+
- **RabbitMQ**: 3.8+

### 10.2 配置文件
```properties
# 数据库配置
spring.datasource.url=jdbc:mysql://localhost:3306/miaosha
spring.datasource.username=root
spring.datasource.password=root

# Redis配置
redis.host=127.0.0.1
redis.port=6379
redis.timeout=10
redis.poolMaxTotal=1000

# RabbitMQ配置
spring.rabbitmq.host=127.0.0.1
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

### 10.3 启动步骤
1. 启动MySQL数据库，导入SQL脚本
2. 启动Redis服务
3. 启动RabbitMQ服务
4. 配置application.properties
5. 运行Spring Boot应用

## 11. 总结

Miaosha-v1秒杀系统通过合理的架构设计和技术选型，实现了高并发、高可用的秒杀业务。系统采用多级缓存、异步消息、事务保障等技术手段，有效解决了秒杀场景下的超卖、性能、稳定性等核心问题。

### 11.1 技术亮点
- Redis缓存 + 本地内存的多级缓存策略
- RabbitMQ异步消息队列削峰填谷
- 动态路径 + 验证码 + 限流的防刷机制
- 数据库事务保证数据一致性

### 11.2 适用场景
- 电商秒杀活动
- 限量商品抢购
- 票务系统抢票
- 其他高并发场景

---

**文档版本**: v1.0  
**更新时间**: 2024年  
**维护人员**: 开发团队