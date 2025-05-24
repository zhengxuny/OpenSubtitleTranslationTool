package com.niit.subtitletranslationtool.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 该类是一个Spring Web MVC控制器，主要负责处理用户页面请求的逻辑。
 * 它映射了与用户登录、注册以及主页相关的URL路径，并返回相应的HTML模板页面。
 */
@Controller
public class ViewController {

    /**
     * 用户访问"/login"路径时调用此方法。
     * 该方法返回一个表示登录页面的字符串，将会被Spring解析为"login.html"模板文件。
     *
     * @return 字符串"login"，表示返回login.html视图
     */
    @GetMapping("/login")
    public String showLoginPage() {
        return "login"; // 返回 login.html 模板
    }

    /**
     * 用户访问"/register"路径时调用此方法。
     * 该方法返回一个表示注册页面的字符串，将会被Spring解析为"register.html"模板文件。
     *
     * @return 字符串"register"，表示返回register.html视图
     */
    @GetMapping("/register")
    public String showRegisterPage() {
        return "register"; // 返回 register.html 模板
    }

    /**
     * 当用户访问根路径"/"或者"/index"路径时，会调用此方法。
     * 该方法返回一个代表主页的字符串，将会被Spring解析为"index.html"模板文件。
     * 这意味着不论是直接访问网站根路径还是明确指定了/index路径，都将会导向同一主页。
     *
     * @return 字符串"index"，表示返回index.html视图
     */
    @GetMapping({"/", "/index"}) // 根路径和/index都导向主页
    public String showIndexPage() {
        return "index"; // 返回 index.html 模板
    }
}
