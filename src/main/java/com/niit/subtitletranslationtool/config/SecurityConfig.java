package com.niit.subtitletranslationtool.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
                // 匹配/admin开头的路径
                .securityMatcher("/admin/**")

                // 禁用CSRF
                .csrf(AbstractHttpConfigurer::disable)

                // 会话管理（根据需求调整）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                // 管理员路径权限配置
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()  // 管理员登录页面公开
                        .anyRequest().hasRole("ADMIN")  // 其他admin路径需要ADMIN角色
                )

                // 管理员登录配置
                .formLogin(form -> form
                        .loginPage("/admin/login")  // 管理员专属登录页面
                        .loginProcessingUrl("/admin/auth/login")  // 管理员登录处理接口
                        .usernameParameter("username")  // 表单用户名字段
                        .passwordParameter("password")  // 表单密码字段
                        .successHandler((request, response, auth) ->  // 登录成功跳转
                                response.sendRedirect("/admin/index")
                        )
                        .failureHandler((request, response, ex) ->  // 登录失败跳转
                                response.sendRedirect("/admin/login?error")
                        )
                )

                // 管理员登出配置
                .logout(logout -> logout
                        .logoutUrl("/admin/auth/logout")  // 管理员登出接口
                        .logoutSuccessHandler((request, response, auth) ->  // 登出成功跳转
                                response.sendRedirect("/admin/login")
                        )
                );

        // 指定使用AdminService作为管理员认证服务
        http.userDetailsService(adminService);
        return http.build();
    }
}