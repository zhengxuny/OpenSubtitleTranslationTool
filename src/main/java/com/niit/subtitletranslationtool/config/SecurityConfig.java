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

/**
 * 安全配置类，负责配置Spring Security的相关设置。
 */
@Configuration
@EnableWebSecurity // 启用Spring Security的Web安全功能
public class SecurityConfig {

    private final UserDetailsService userDetailsService; // 注入自定义的UserService
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;
    private final PasswordEncoder passwordEncoder; // 注入密码编码器

    /**
     * 构造函数，注入所需的依赖。
     *
     * @param userDetailsService 自定义的UserDetailsService实例
     * @param customAuthenticationSuccessHandler 自定义的登录成功处理器
     * @param customAuthenticationFailureHandler 自定义的登录失败处理器
     * @param passwordEncoder 密码编码器
     */
    public SecurityConfig(UserDetailsService userDetailsService,
                          CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
                          CustomAuthenticationFailureHandler customAuthenticationFailureHandler,
                          PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
        this.customAuthenticationFailureHandler = customAuthenticationFailureHandler;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 配置认证管理器，指定如何查找和验证用户信息。
     *
     * @param http HttpSecurity实例
     * @return 配置后的AuthenticationManager实例
     * @throws Exception 可能抛出的异常，需要处理
     */
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder
                .userDetailsService(userDetailsService) // 使用自定义的UserDetailsService
                .passwordEncoder(passwordEncoder);      // 使用注入的密码编码器
        return authenticationManagerBuilder.build();
    }

    /**
     * 配置安全过滤链，定义不同URL路径的访问权限和处理方式。
     *
     * @param http HttpSecurity实例
     * @return 配置后的SecurityFilterChain实例
     * @throws Exception 可能抛出的异常，需要处理
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // 禁用CSRF保护，适用于RESTful API环境
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll() // 放行注册和登录API，无需认证
                        .requestMatchers("/register", "/login").permitAll()                      // 放行注册页面和登录页面，无需认证
                        .requestMatchers("/api/**").authenticated()                              // /api/下的所有请求需要认证
                        .anyRequest().authenticated()                                           // 其他所有请求也需要认证，未认证时会重定向到登录页
                )
                .formLogin(form -> form
                                .loginPage("/login") // 指定Spring Security使用的登录页面URL，未认证时用于重定向
                                .loginProcessingUrl("/api/auth/login") // 指定处理登录请求的API URL
                                .usernameParameter("username")         // 登录表单中的用户名字段名称
                                .passwordParameter("password")         // 登录表单中的密码字段名称
                                .successHandler(customAuthenticationSuccessHandler)              // 自定义登录成功处理器
                                .failureHandler(customAuthenticationFailureHandler)              // 自定义登录失败处理器
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout") // 指定处理登出请求的API URL
                        .logoutSuccessHandler((request, response, authentication) -> {        // 自定义登出成功后的处理逻辑
                            response.setStatus(200);                                              // 设置响应状态码为200
                            response.getWriter().write("{\"message\":\"登出成功\"}");             // 以JSON格式返回成功信息
                        })
                        .permitAll()                                                              // 对登出API放行，允许所有访问
                );

        return http.build();
    }
}