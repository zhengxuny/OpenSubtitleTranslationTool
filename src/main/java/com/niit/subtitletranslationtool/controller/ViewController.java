package com.niit.subtitletranslationtool.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/login")
    public String showLoginPage() {
        return "login"; // 返回 login.html 模板
    }

    @GetMapping("/register")
    public String showRegisterPage() {
        return "register"; // 返回 register.html 模板
    }

    @GetMapping({"/", "/index"}) // 根路径和/index都导向主页
    public String showIndexPage() {
        return "index"; // 返回 index.html 模板
    }
}