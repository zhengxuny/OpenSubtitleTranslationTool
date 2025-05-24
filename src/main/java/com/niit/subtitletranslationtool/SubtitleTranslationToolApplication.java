package com.niit.subtitletranslationtool;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

/**
 * @SpringBootApplication 标注这是一个 Spring Boot 应用。
 * 它整合了 @Configuration, @EnableAutoConfiguration, 和 @ComponentScan 注解。
 */
@SpringBootApplication
/**
 * @MapperScan("com.niit.subtitletranslationtool.mapper") MyBatis 的 Mapper 扫描注解。
 * 用于指定 MyBatis Mapper 接口所在的包，Spring 会自动扫描这些接口，并创建相应的 Bean。
 */
@MapperScan("com.niit.subtitletranslationtool.mapper")
/**
 * @EnableAsync 启用异步方法执行。
 * 允许在应用中使用 @Async 注解来异步执行方法。
 */
@EnableAsync
public class SubtitleTranslationToolApplication {
    /**
     * main 方法是程序的入口点。
     * @param args 接收命令行参数。
     */
    public static void main(String[] args) {
        // SpringApplication.run 启动 Spring Boot 应用。
        // 它会创建 Spring 应用上下文，并启动内嵌的 Tomcat 服务器。
        SpringApplication.run(SubtitleTranslationToolApplication.class, args);
    }

    /**
     * @Bean 注解用于声明一个 Bean。
     * 在这里，它声明了一个 RestTemplate Bean。
     * @return 返回一个 RestTemplate 实例。
     */
    @Bean
    public RestTemplate restTemplate() {
        // 创建并返回一个 RestTemplate 实例。
        // RestTemplate 是 Spring 提供的用于访问 RESTful 服务的客户端工具。
        return new RestTemplate();
    }
}