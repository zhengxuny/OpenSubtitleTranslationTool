package com.niit.subtitletranslationtool.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// 定义一个Spring配置类，负责配置Spring Security的相关设置
@Configuration
@EnableWebSecurity // 启用Spring Security的Web安全功能
public class SecurityConfig {

    // 自定义的UserDetailsService实例，负责从数据库或其它存储中加载用户信息
    private final UserDetailsService userDetailsService;

    // 自定义的登录成功处理器，处理用户登录成功后的逻辑
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    // 自定义的登录失败处理器，处理用户登录失败后的逻辑
    private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;

    // 密码编码器，这里使用BCryptPasswordEncoder来对密码进行哈希处理
    private final PasswordEncoder passwordEncoder;

    // 构造方法，注入所需的依赖
    public SecurityConfig(UserDetailsService userDetailsService,
                          CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
                          CustomAuthenticationFailureHandler customAuthenticationFailureHandler,
                          PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
        this.customAuthenticationFailureHandler = customAuthenticationFailureHandler;
        this.passwordEncoder = passwordEncoder;
    }

    // 配置认证管理器，指定如何查找和验证用户信息
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        // 获取共享的AuthenticationManagerBuilder实例
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);

        // 配置用户DetailsService，指定使用自定义的UserDetailsService来加载用户信息
        authenticationManagerBuilder
                .userDetailsService(userDetailsService)
                // 配置密码编码器，指定使用注入的密码编码器对用户密码进行编码
                .passwordEncoder(passwordEncoder);

        // 构建并返回配置好的AuthenticationManager实例
        return authenticationManagerBuilder.build();
    }

    // 配置安全过滤链，定义不同的URL路径需要的访问权限和处理方式
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF保护，因为这适用于RESTful API环境，CSRF保护通常在表单提交时启用
                .csrf(AbstractHttpConfigurer::disable)

                // 配置URL访问权限
                .authorizeHttpRequests(auth -> auth
                        // 放行注册和登录API请求，无需认证
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        // 放行注册页面和登录页面请求，无需认证
                        .requestMatchers("/register", "/login").permitAll()
                        // 需要认证才能访问/api/下的所有请求
                        .requestMatchers("/api/**").authenticated()
                        // 其他所有的请求也需要认证，未认证时会重定向到登录页面
                        .anyRequest().authenticated()
                )

                // 配置表单登录参数
                .formLogin(form -> form
                        // 指定Spring Security使用的登录页面URL，如果未认证访问需要认证的资源时，会重定向到该页面
                        .loginPage("/login")
                        // 指定处理登录请求的API URL
                        .loginProcessingUrl("/api/auth/login")
                        // 登录表单中的用户名字段名称
                        .usernameParameter("username")
                        // 登录表单中的密码字段名称
                        .passwordParameter("password")
                        // 自定义登录成功处理器，处理登录成功后的逻辑
                        .successHandler(customAuthenticationSuccessHandler)
                        // 自定义登录失败处理器，处理登录失败后的逻辑
                        .failureHandler(customAuthenticationFailureHandler)
                )

                // 配置登出参数
                .logout(logout -> logout
                        // 指定处理登出请求的API URL
                        .logoutUrl("/api/auth/logout")
                        // 自定义登出成功后的处理逻辑，设置响应状态码为200，并以JSON格式返回成功信息
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(200);
                            response.getWriter().write("{\"message\":\"登出成功\"}");
                        })
                        // 对登出API放行，允许所有访问
                        .permitAll()
                );

        // 构建并返回配置好的SecurityFilterChain实例
        return http.build();
    }
}