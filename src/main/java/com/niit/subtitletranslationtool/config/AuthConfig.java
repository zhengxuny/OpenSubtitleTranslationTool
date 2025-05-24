package com.niit.subtitletranslationtool.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// 这是一个配置类，用于配置应用程序中的安全性设置
@Configuration
public class AuthConfig {

    // 定义一个Bean，该Bean是一个密码编码器，用于对密码进行加密
    // 密码编码器作为一个Bean定义，意味着它可以在整个应用程序中复用
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 创建并返回一个BCryptPasswordEncoder对象
        // BCryptPasswordEncoder是Spring Security提供的一个密码编码器实现
        // 它使用BCrypt哈希算法对密码进行密钥拉伸（key stretching），以提高密码的安全性
        return new BCryptPasswordEncoder();
    }
}