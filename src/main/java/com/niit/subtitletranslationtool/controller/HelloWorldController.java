package com.niit.subtitletranslationtool.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

//写一个简单的HelloWorldController，用于测试Spring Boot应用是否正常运行
@RestController
public class HelloWorldController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello World";
    }
}
