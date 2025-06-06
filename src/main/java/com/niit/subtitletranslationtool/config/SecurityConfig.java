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

import com.niit.subtitletranslationtool.service.AdminService;
import com.niit.subtitletranslationtool.config.CustomAuthenticationFailureHandler;
import com.niit.subtitletranslationtool.config.CustomAuthenticationSuccessHandler;

/**
 * Spring Security 配置类，负责定义用户和管理员两套安全过滤链及认证管理器。
 * 支持基于表单的登录认证、密码加密、权限控制以及登录登出成功/失败处理。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;

    private final AdminService adminService;

    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;

    private final PasswordEncoder passwordEncoder;

    /**
     * 使用构造函数注入用户和管理员认证服务、自定义成功失败处理器，以及密码编码器。
     *
     * @param userDetailsService              普通用户认证服务
     * @param adminService                   管理员认证服务
     * @param customAuthenticationSuccessHandler 自定义登录成功处理器
     * @param customAuthenticationFailureHandler 自定义登录失败处理器
     * @param passwordEncoder               密码编码器
     */
    public SecurityConfig(@Qualifier("userService") UserDetailsService userDetailsService,
                          AdminService adminService,
                          CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
                          CustomAuthenticationFailureHandler customAuthenticationFailureHandler,
                          PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.adminService = adminService;
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
        this.customAuthenticationFailureHandler = customAuthenticationFailureHandler;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 提供 BCrypt 算法的密码编码器 Bean，用于统一密码加密和校验。
     *
     * @return 使用 BCrypt 的密码编码器实例
     */
    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置普通用户认证管理器，基于自定义的UserDetailsService和密码编码器进行认证。
     *
     * @param http HttpSecurity对象，用于获取共享的AuthenticationManagerBuilder
     * @return 认证管理器实例
     * @throws Exception 可能抛出配置异常
     */
    @Bean
    @Primary
    public AuthenticationManager userAuthenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);

        // 使用普通用户的UserDetailsService和密码编码器
        authenticationManagerBuilder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
        return authenticationManagerBuilder.build();
    }

    /**
     * 配置管理员认证管理器，使用AdminService及密码编码器进行管理员身份认证。
     *
     * @param http HttpSecurity对象，用于获取共享的AuthenticationManagerBuilder
     * @return 管理员认证管理器实例
     * @throws Exception 可能抛出配置异常
     */
    @Bean
    public AuthenticationManager adminAuthenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);

        // 使用管理员的UserDetailsService和密码编码器
        authenticationManagerBuilder.userDetailsService(adminService).passwordEncoder(passwordEncoder);
        return authenticationManagerBuilder.build();
    }

    /**
     * 定义普通用户的安全过滤链，匹配除了/admin路径之外的所有请求。
     * 配置用户认证、登录、登出及路径访问权限。
     *
     * @param http HttpSecurity对象，用于配置HTTP安全策略
     * @return 构建好的安全过滤链实例
     * @throws Exception 配置异常
     */
    @Bean
    @Order(2)
    public SecurityFilterChain userFilterChain(HttpSecurity http) throws Exception {
        http
                // 仅匹配非/admin开头的请求路径
                .securityMatcher(security -> !security.getRequestURI().startsWith("/admin/"))

                // 禁用CSRF保护，通常API接口使用token或其他防护机制
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        // 根据需求决定是否创建会话
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                // 配置路径访问权限，部分路径允许匿名访问，部分需要认证
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/topup", "/api/auth/topup").authenticated()
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/register", "/login").permitAll()
                        .requestMatchers("static/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )

                // 配置基于表单的登录接口与成功/失败处理
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(customAuthenticationSuccessHandler)
                        .failureHandler(customAuthenticationFailureHandler)
                )

                // 配置登出接口和登出成功时返回的自定义响应
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(200);
                            response.getWriter().write("{\"message\":\"登出成功\"}");
                        })
                        .permitAll()
                );

        return http.build();
    }

    /**
     * 定义管理员的安全过滤链，专门匹配/admin开头路径的请求，使用管理员认证管理器。
     * 配置管理员登录登出、权限控制及静态资源访问。
     *
     * @param http HttpSecurity对象，用于配置HTTP安全策略
     * @param adminAuthenticationManager 管理员认证管理器，确保使用AdminService进行认证
     * @return 构建好的管理员安全过滤链实例
     * @throws Exception 配置异常
     */
    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(
            HttpSecurity http,
            @Qualifier("adminAuthenticationManager") AuthenticationManager adminAuthenticationManager
    ) throws Exception {
        http
                // 仅匹配/admin/**路径
                .securityMatcher("/admin/**")

                // 显式指定管理员认证管理器，确保用户来源正确
                .authenticationManager(adminAuthenticationManager)

                // 禁用CSRF，管理员接口建议配合其他机制保护
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        // 总是创建会话，方便管理员管理登录状态
                        .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                        // 限制单设备登录，防止多次重复登录（可选）
                        .maximumSessions(1)
                )

                // 配置管理员路径的权限，开放部分静态资源和登录页面
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("static/**", "/assets/**", "/css/**", "/js/**", "/img/**").permitAll()
                        .requestMatchers("/admin/login").permitAll()
                        .requestMatchers("/admin/auth/login").permitAll()
                        .anyRequest().hasRole("ADMIN")
                )

                // 管理员登录页面及登录处理配置
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/auth/login")
                        .usernameParameter("username")
                        .passwordParameter("password")

                        // 登录成功时返回JSON格式的响应，包含登录用户名
                        .successHandler((request, response, auth) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":true, \"message\":\"管理员登录成功\", \"username\":\""
                                                      + auth.getName() + "\"}");
                        })

                        // 登录失败时返回JSON格式的错误信息
                        .failureHandler((request, response, ex) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":false, \"message\":\"" + ex.getMessage() + "\"}");
                        })
                )

                // 管理员登出接口与登出成功处理，返回JSON响应
                .logout(logout -> logout
                        .logoutUrl("/admin/auth/logout")
                        .logoutSuccessHandler((request, response, auth) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":true, \"message\":\"管理员登出成功\"}");
                        })
                        .permitAll()
                );

        return http.build();
    }
}
