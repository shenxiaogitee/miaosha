package com.geekq.miaosha.controller;

import com.geekq.miaosha.redis.KeyPrefix;
import com.geekq.miaosha.redis.RedisService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring5.view.ThymeleafViewResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;

@Controller
public class BaseController {


    @Autowired
    ThymeleafViewResolver thymeleafViewResolver;
    @Autowired
    RedisService redisService;
    //加一个配置项
    @Value("#{'${pageCache.enbale}'}")
    private boolean pageCacheEnable;

    public static void out(HttpServletResponse res, String html) {
        res.setContentType("text/html");
        res.setCharacterEncoding("UTF-8");
        try {
            OutputStream out = res.getOutputStream();
            out.write(html.getBytes("UTF-8"));
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从提供的代码中可以看出， BaseController 是一个基础控制器类，主要提供了以下功能：
     * 1. 1.
     *    页面缓存处理 ：
     *    - 通过Redis实现页面级缓存，提高页面加载速度
     *    - 使用配置项 pageCache.enbale 控制是否启用页面缓存功能
     *    - 当缓存启用时，先尝试从Redis获取已缓存的页面内容，如果存在则直接返回
     *    - 如果缓存不存在，则手动渲染页面并将结果存入Redis缓存
     * 2. 2.
     *    页面渲染 ：
     *    - 提供 render 方法，用于渲染Thymeleaf模板
     *    - 支持手动渲染页面内容，并将渲染结果写入HTTP响应
     * 3. 3.
     *    HTTP响应输出 ：
     *    - 提供 out 方法，用于将HTML内容写入HTTP响应
     *    - 设置正确的内容类型和字符编码
     * @param request
     * @param response
     * @param model
     * @param tplName
     * @param prefix
     * @param key
     * @return
     */
    public String render(HttpServletRequest request, HttpServletResponse response, Model model, String tplName, KeyPrefix prefix, String key) {
        if (!pageCacheEnable) {
            return tplName;
        }
        //取缓存
        String html = redisService.get(prefix, key, String.class);
        if (!StringUtils.isEmpty(html)) {
            out(response, html);
            return null;
        }
        //手动渲染
        WebContext ctx = new WebContext(request, response,
                request.getServletContext(), request.getLocale(), model.asMap());
        html = thymeleafViewResolver.getTemplateEngine().process(tplName, ctx);
        if (!StringUtils.isEmpty(html)) {
            redisService.set(prefix, key, html);
        }
        out(response, html);
        return null;
    }
}
