package com.niit.subtitletranslationtool.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.niit.subtitletranslationtool.service.AdminService; // 注意：这里虽然引入了AdminService，但并没有实际使用，可能需要检查和调整。
import com.niit.subtitletranslationtool.service.AdminDetailsService;
import com.niit.subtitletranslationtool.service.UserService; // 注意：这里虽然引入了UserService，但并没有实际使用，可能需要检查和调整。
import com.niit.subtitletranslationtool.config.CustomAuthenticationFailureHandler;
import com.niit.subtitletranslationtool.config.CustomAuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity // 启用 Spring Security 的 Web 安全特性
public class SecurityConfig {

    // 用户服务，用于从数据库或其他来源加载用户信息的接口
    private final UserDetailsService userDetailsService;

    // 管理员服务，用于从数据库或其他来源加载管理员信息的接口
    private final AdminDetailsService adminDetailsService;

    // 自定义成功处理程序，用于处理用户登录成功的情况，例如重定向到特定页面或返回自定义响应
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    // 自定义失败处理程序，用于处理用户登录失败的情况，例如返回错误信息或显示错误页面
    private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;

    // 密码编码器，用于密码加密和解密，确保密码安全存储
    private final PasswordEncoder passwordEncoder;

    // 构造函数，通过依赖注入初始化各个服务和处理程序
    public SecurityConfig(@Qualifier("userService") UserDetailsService userDetailsService, // 使用 @Qualifier 指定 bean 的名称，避免歧义
                          AdminDetailsService adminDetailsService,
                          CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
                          CustomAuthenticationFailureHandler customAuthenticationFailureHandler,
                          PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.adminDetailsService = adminDetailsService;
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
        this.customAuthenticationFailureHandler = customAuthenticationFailureHandler;
        this.passwordEncoder = passwordEncoder;
    }

    // 配置BCryptPasswordEncoder作为密码编码器，使用 BCrypt 算法进行密码加密
    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 配置用户认证管理器，负责验证用户提供的身份信息（用户名和密码）
    @Bean
    @Primary // 设置为主要的 AuthenticationManager，当有多个 AuthenticationManager 时，优先使用该 Bean
    public AuthenticationManager userAuthenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class); // 获取 AuthenticationManagerBuilder 的实例

        authenticationManagerBuilder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder); // 配置 UserDetailsService 和 PasswordEncoder
        return authenticationManagerBuilder.build(); // 构建 AuthenticationManager
    }

    // 配置管理员认证管理器
    @Bean
    public AuthenticationManager adminAuthenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);

        authenticationManagerBuilder.userDetailsService(adminDetailsService).passwordEncoder(passwordEncoder);
        return authenticationManagerBuilder.build();
    }

    // 配置用户安全过滤链，定义用户相关请求的安全策略
    @Bean
    @Order(2) // 设置过滤链的优先级，数值越小优先级越高。用户过滤链优先级较低，先经过管理员过滤链
    public SecurityFilterChain userFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(security -> !security.getRequestURI().startsWith("/admin/")) // 定义此过滤器链匹配的请求路径模式，这里是排除以 /admin/ 开头的请求

                .csrf(AbstractHttpConfigurer::disable) // 禁用 CSRF 保护，在 API 应用中通常不需要 CSRF 保护

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) // 设置会话创建策略，如果需要就创建
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/topup", "/api/auth/topup").authenticated() // /topup 和 /api/auth/topup 需要认证才能访问
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll() // /api/auth/register 和 /api/auth/login 允许所有人访问
                        .requestMatchers("/register", "/login").permitAll() // /register 和 /login 允许所有人访问
                        .requestMatchers("static/**").permitAll() // 允许所有人访问静态资源
                        .requestMatchers("/api/**").authenticated() // /api/** 下的任何请求都需要认证
                        .anyRequest().authenticated() // 其他任何请求都需要认证
                )

                .formLogin(form -> form
                        .loginPage("/login") // 设置登录页面 URL
                        .loginProcessingUrl("/api/auth/login") // 设置登录请求处理 URL
                        .usernameParameter("username") // 设置用户名参数名
                        .passwordParameter("password") // 设置密码参数名
                        .successHandler(customAuthenticationSuccessHandler) // 设置登录成功处理程序
                        .failureHandler(customAuthenticationFailureHandler) // 设置登录失败处理程序
                )

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout") // 设置登出 URL
                        .logoutSuccessHandler((request, response, authentication) -> { // 设置登出成功处理程序
                            response.setStatus(200); // 设置 HTTP 状态码为 200
                            response.getWriter().write("{\"message\":\"登出成功\"}"); // 返回 JSON 格式的成功消息
                        })
                        .permitAll() // 允许所有人访问登出 URL
                );

        return http.build(); // 构建 SecurityFilterChain
    }

    // 配置管理员安全过滤链，定义管理员相关请求的安全策略
    @Bean
    @Order(1) // 设置过滤链的优先级，数值越小优先级越高。管理员过滤链优先级较高，先进行匹配
    public SecurityFilterChain adminFilterChain(
            HttpSecurity http,
            @Qualifier("adminAuthenticationManager") AuthenticationManager adminAuthenticationManager // 使用 @Qualifier 指定要注入的 AuthenticationManager Bean
    ) throws Exception {
        http
                .securityMatcher("/admin/**") // 定义此过滤器链匹配的请求路径模式，这里是以 /admin/ 开头的请求

                .authenticationManager(adminAuthenticationManager) // 设置使用的 AuthenticationManager

                .csrf(AbstractHttpConfigurer::disable) // 禁用 CSRF 保护

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.ALWAYS) // 设置会话创建策略，总是创建会话
                        .maximumSessions(1) // 限制每个用户只能有一个会话
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("static/**", "/assets/**", "/css/**", "/js/**", "/img/**").permitAll() // 允许所有人访问静态资源
                        .requestMatchers("/admin/login").permitAll() // 允许所有人访问管理员登录页面
                        .requestMatchers("/admin/auth/login").permitAll() // 允许所有人访问管理员登录处理 URL
                        .anyRequest().hasRole("ADMIN") // 其他任何请求都需要 ADMIN 角色才能访问
                )

                .formLogin(form -> form
                        .loginPage("/admin/login") // 设置管理员登录页面 URL
                        .loginProcessingUrl("/admin/auth/login") // 设置管理员登录请求处理 URL
                        .usernameParameter("username") // 设置用户名参数名
                        .passwordParameter("password") // 设置密码参数名

                        .successHandler((request, response, auth) -> { // 设置管理员登录成功处理程序
                            response.setStatus(HttpServletResponse.SC_OK); // 设置 HTTP 状态码为 200
                            response.setContentType("application/json;charset=UTF-8"); // 设置响应内容类型为 JSON
                            response.getWriter().write("{\"success\":true, \"message\":\"管理员登录成功\", \"username\":\""
                                                      + auth.getName() + "\"}"); // 返回 JSON 格式的成功消息，包含用户名
                        })

                        .failureHandler((request, response, ex) -> { // 设置管理员登录失败处理程序
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 设置 HTTP 状态码为 401 (Unauthorized)
                            response.setContentType("application/json;charset=UTF-8"); // 设置响应内容类型为 JSON
                            response.getWriter().write("{\"success\":false, \"message\":\"" + ex.getMessage() + "\"}"); // 返回 JSON 格式的错误消息，包含异常信息
                        })
                )

                .logout(logout -> logout
                        .logoutUrl("/admin/auth/logout") // 设置管理员登出 URL
                        .logoutSuccessHandler((request, response, auth) -> { // 设置管理员登出成功处理程序
                            response.setStatus(HttpServletResponse.SC_OK); // 设置 HTTP 状态码为 200
                            response.setContentType("application/json;charset=UTF-8"); // 设置响应内容类型为 JSON
                            response.getWriter().write("{\"success\":true, \"message\":\"管理员登出成功\"}"); // 返回 JSON 格式的成功消息
                        })
                        .permitAll() // 允许所有人访问管理员登出 URL
                );

        return http.build(); // 构建 SecurityFilterChain
    }
}