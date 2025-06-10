package com.niit.subtitletranslationtool.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import com.niit.subtitletranslationtool.service.AdminDetailsService;
import com.niit.subtitletranslationtool.config.CustomAuthenticationFailureHandler;
import com.niit.subtitletranslationtool.config.CustomAuthenticationSuccessHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

@Configuration
@EnableWebSecurity // 启用 Spring Security 的 Web 安全特性
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final AdminDetailsService adminDetailsService;
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;

    // 构造函数，通过依赖注入初始化各个服务和处理程序
    public SecurityConfig(@Qualifier("userService") UserDetailsService userDetailsService,
                          AdminDetailsService adminDetailsService,
                          CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
                          CustomAuthenticationFailureHandler customAuthenticationFailureHandler) {
        this.userDetailsService = userDetailsService;
        this.adminDetailsService = adminDetailsService;
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
        this.customAuthenticationFailureHandler = customAuthenticationFailureHandler;
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // [新增] 将 AuthenticationManager 注册为 Bean，以解决 AuthController 的依赖注入问题
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    // [优化] 移除了原有的 userAuthenticationManager 和 adminAuthenticationManager Bean，
    // 因为现代Spring Security不再需要显式创建它们。
    @Bean
    @Order(2)
    public SecurityFilterChain userFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(security -> !security.getRequestURI().startsWith("/admin/"))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            // [优化] 直接在 HttpSecurity 中配置 UserDetailsService
            .userDetailsService(userDetailsService)
            .authorizeHttpRequests(auth -> auth
                // [修复] 修复了静态资源路径并合并了permitAll规则
                .requestMatchers(
                    "/login", "/register",
                    "/api/auth/login", "/api/auth/register",
                    "/static/**", "/css/**", "/js/**", "/assets/**", "/img/**"
                ).permitAll()
                .requestMatchers("/topup", "/api/auth/topup").authenticated()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(customAuthenticationSuccessHandler)
                .failureHandler(customAuthenticationFailureHandler)
            )
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

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/admin/**")
            .csrf(AbstractHttpConfigurer::disable)
            // [优化] 直接在 HttpSecurity 中配置 AdminDetailsService
            .userDetailsService(adminDetailsService)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                .maximumSessions(1)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/login", "/admin/auth/login").permitAll()
                // [修复] 确保管理员静态资源也被正确放行
                .requestMatchers("/static/**", "/assets/**", "/css/**", "/js/**", "/img/**").permitAll()
                .anyRequest().hasRole("ADMIN")
            )
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/auth/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler((request, response, auth) -> {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"success\":true, \"message\":\"管理员登录成功\", \"username\":\"" + auth.getName() + "\"}");
                })
                .failureHandler((request, response, ex) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"success\":false, \"message\":\"" + ex.getMessage() + "\"}");
                })
            )
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
