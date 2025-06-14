package com.niit.subtitletranslationtool.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 自定义错误处理控制器，用于处理应用中的错误情况，例如404错误。
 * <p>
 * 实现了ErrorController接口，可以接管Spring Boot默认的错误处理流程，
 * 并返回自定义的错误页面，提升用户体验。
 */
@Controller
public class CustomErrorController implements ErrorController {

    /**
     * 处理"/error"路径的请求，当发生错误时，该方法会被调用。
     *
     * @return 返回字符串"404"，对应于/templates目录下的404.html页面。
     *         Spring Boot会自动查找该页面并返回给客户端。
     */
    @RequestMapping("/error")
    public String handleError() {
        return "404"; // 返回自定义的404错误页面
    }

}