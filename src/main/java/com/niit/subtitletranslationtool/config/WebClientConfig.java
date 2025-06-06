package com.niit.subtitletranslationtool.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient配置类，负责提供WebClient的Bean实例。
 * 便于在应用中统一管理和注入WebClient。
 */
@Configuration
public class WebClientConfig {

    /**
     * 创建并返回一个默认的WebClient实例。
     *
     * @return WebClient对象，用于执行非阻塞的HTTP请求。
     */
    @Bean
    public WebClient webClient() {
        return WebClient.create();
    }
}
