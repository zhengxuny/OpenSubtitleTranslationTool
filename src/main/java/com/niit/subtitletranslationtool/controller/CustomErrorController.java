// src/main/java/com/niit/subtitletranslationtool/controller/CustomErrorController.java
package com.niit.subtitletranslationtool.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {
    @RequestMapping("/error")
    public String handleError() {
        return "404"; // 返回自定义的404错误页面
    }

    public String getErrorPath() {
        return "/404.html";
    }
}