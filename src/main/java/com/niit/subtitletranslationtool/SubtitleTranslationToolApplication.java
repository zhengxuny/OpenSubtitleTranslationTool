package com.niit.subtitletranslationtool;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

/**
 * 字幕翻译工具的Spring Boot应用主类
 * <p>
 * 负责应用的启动引导、基础配置及核心Bean的注册，包含以下功能：
 * - 启用Spring Boot自动配置和组件扫描
 * - 配置MyBatis Mapper接口扫描路径
 * - 启用异步方法执行支持
 * - 注册RestTemplate用于REST服务调用
 */
@SpringBootApplication
@MapperScan("com.niit.subtitletranslationtool.mapper")
@EnableAsync
public class SubtitleTranslationToolApplication {

    /**
     * 应用程序入口方法，启动Spring Boot上下文
     *
     * @param args 命令行参数（当前未使用）
     */
    public static void main(String[] args) {
        SpringApplication.run(SubtitleTranslationToolApplication.class, args);
    }

    /**
     * 注册RestTemplate Bean，用于应用内的REST API调用
     *
     * @return 用于HTTP客户端请求的RestTemplate实例
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}