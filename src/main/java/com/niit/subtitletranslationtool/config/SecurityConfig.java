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
// import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // 移除未使用的导入

import com.niit.subtitletranslationtool.service.AdminService;
import com.niit.subtitletranslationtool.config.CustomAuthenticationFailureHandler;
import com.niit.subtitletranslationtool.config.CustomAuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 普通用户认证服务（来自UserService）
    private final UserDetailsService userDetailsService;
    // 管理员认证服务
    private final AdminService adminService;
    // 自定义登录成功处理器
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    // 自定义登录失败处理器
    private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;
    // 密码编码器
    private final PasswordEncoder passwordEncoder;

    // 构造函数注入所有依赖
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

    // 密码编码器Bean（BCrypt）
    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 普通用户认证管理器
    @Bean
    @Primary
    public AuthenticationManager userAuthenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
        return authenticationManagerBuilder.build();
    }

    // 管理员认证管理器
    @Bean
    public AuthenticationManager adminAuthenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.userDetailsService(adminService).passwordEncoder(passwordEncoder);
        return authenticationManagerBuilder.build();
    }

    // 普通用户安全过滤链（处理非/admin路径）
    @Bean
    @Order(2) // 用户过滤链优先级低于admin过滤链
    public SecurityFilterChain userFilterChain(HttpSecurity http) throws Exception {
        http
                // 仅匹配非/admin开头的路径
                .securityMatcher(security -> !security.getRequestURI().startsWith("/admin/"))

                // 禁用CSRF
                .csrf(AbstractHttpConfigurer::disable)

                // 会话管理（根据需求调整）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                // 路径权限配置
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/topup", "/api/auth/topup").authenticated()
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/register", "/login").permitAll()
                        .requestMatchers("static/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )

                // 普通用户登录配置
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(customAuthenticationSuccessHandler)
                        .failureHandler(customAuthenticationFailureHandler)
                )

                // 普通用户登出配置
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

    // 管理员安全过滤链（处理/admin开头的路径）
    @Bean
    @Order(1) // 确保admin过滤链优先处理/admin路径
    public SecurityFilterChain adminFilterChain(
        HttpSecurity http,
        @Qualifier("adminAuthenticationManager") AuthenticationManager adminAuthenticationManager // 明确指定Bean名称
    ) throws Exception {
        http
                // 匹配/admin开头的路径
                .securityMatcher("/admin/**")

                // 显式指定使用管理员认证管理器
                .authenticationManager(adminAuthenticationManager) // 强制使用管理员认证管理器

                // 禁用CSRF
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.ALWAYS) // 总是创建会话
                        .maximumSessions(1) // 限制单设备登录（可选）
                )

                // 管理员路径权限配置
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("static/**", "/assets/**", "/css/**", "/js/**", "/img/**").permitAll() // 确保静态资源可访问
                        .requestMatchers("/admin/login").permitAll()  // 管理员登录页面公开
                        .requestMatchers("/admin/auth/login").permitAll() // 允许未认证用户访问登录处理接口
                        .anyRequest().hasRole("ADMIN")  // 其他admin路径需要ADMIN角色
                )

                // 管理员登录配置
                .formLogin(form -> form
                        .loginPage("/admin/login")  // 管理员专属登录页面
                        .loginProcessingUrl("/admin/auth/login")  // 管理员登录处理接口
                        .usernameParameter("username")  // 表单用户名字段
                        .passwordParameter("password")  // 表单密码字段
                        .successHandler((request, response, auth) -> {
                            // 登录成功，返回JSON响应
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setContentType("application/json;charset=UTF-8");
                            // 实际应用中，您可能需要从 Authentication 对象中获取管理员信息（例如用户名）
                            // 暂时只返回成功消息
                            response.getWriter().write("{\"success\":true, \"message\":\"管理员登录成功\", \"username\":\"" + auth.getName() + "\"}");
                        })
                        .failureHandler((request, response, ex) -> {
                            // 登录失败，返回JSON响应
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Unauthorized
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":false, \"message\":\"" + ex.getMessage() + "\"}");
                        })
                )

                // 管理员登出配置
                .logout(logout -> logout
                        .logoutUrl("/admin/auth/logout")  // 管理员登出接口
                        .logoutSuccessHandler((request, response, auth) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":true, \"message\":\"管理员登出成功\"}");
                        })
                        .permitAll()
                );

        // 指定使用AdminService作为管理员认证服务
        // 注意：http.userDetailsService(adminService) 在配置多个SecurityFilterChain时可能不是最佳实践
        // 推荐在 adminAuthenticationManager Bean 中明确指定 userDetailsService
        // http.userDetailsService(adminService); // 此行可以移除，因为它已在 adminAuthenticationManager 中设置
        return http.build();
    }
}