package com.niit.subtitletranslationtool.config;

import com.niit.subtitletranslationtool.service.AdminDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置类。
 *
 * <p>
 *   此类负责配置应用程序的安全策略，包括用户认证、授权、会话管理等。
 *   通过定义多个 {@link SecurityFilterChain}，可以为不同的URL模式应用不同的安全规则。
 * </p>
 */
@Configuration
@EnableWebSecurity // 启用 Spring Security 的 Web 安全特性 (启用Spring Security的Web安全功能)
public class SecurityConfig {

  private final UserDetailsService userDetailsService;
  private final AdminDetailsService adminDetailsService;
  private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
  private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;

  /**
   * 构造函数，通过依赖注入初始化各个服务和处理程序。
   *
   * @param userDetailsService 用户详细信息服务，用于加载用户信息。 (用于加载用户信息的服务)
   * @param adminDetailsService 管理员详细信息服务，用于加载管理员信息。 (用于加载管理员信息的服务)
   * @param customAuthenticationSuccessHandler 自定义认证成功处理器，用于处理认证成功后的逻辑。 (自定义认证成功后的处理逻辑)
   * @param customAuthenticationFailureHandler 自定义认证失败处理器，用于处理认证失败后的逻辑。 (自定义认证失败后的处理逻辑)
   */
  public SecurityConfig(
      @Qualifier("userService") UserDetailsService userDetailsService,
      @Lazy AdminDetailsService adminDetailsService,
      CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
      CustomAuthenticationFailureHandler customAuthenticationFailureHandler) {
    this.userDetailsService = userDetailsService;
    this.adminDetailsService = adminDetailsService;
    this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
    this.customAuthenticationFailureHandler = customAuthenticationFailureHandler;
  }


  /**
   * 密码编码器 Bean。
   *
   * <p>
   *   使用 BCryptPasswordEncoder 对用户密码进行加密存储。
   *   BCryptPasswordEncoder 是一种广泛使用的密码哈希算法，能够提供较强的安全性。
   * </p>
   *
   * @return PasswordEncoder 密码编码器实例。 (密码编码器的实例)
   */
  @Bean
  public static PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(); // 使用 BCryptPasswordEncoder 作为密码编码器 (使用BCryptPasswordEncoder作为密码加密工具)
  }

  /**
   * 用户安全过滤器链 Bean。
   *
   * <p>
   *   定义了针对非 "/admin/**" URL模式的安全规则。
   *   包括 CSRF 保护禁用、会话管理、URL 授权、表单登录和注销等配置。
   * </p>
   *
   * @param http HttpSecurity 对象，用于配置安全规则。 (用于配置安全规则的HttpSecurity对象)
   * @return SecurityFilterChain 安全过滤器链实例。 (安全过滤器链的实例)
   * @throws Exception 如果配置过程中发生错误，则抛出异常。 (如果配置过程中发生错误，则抛出异常)
   */
  @Bean
  @Order(2) // 定义执行顺序，数值越小优先级越高 (定义执行顺序，数值越小优先级越高)
  public SecurityFilterChain userFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher(
            security ->
                !security.getRequestURI().startsWith("/admin/")) // 匹配所有非/admin/**开头的请求 (匹配所有非/admin/**开头的请求)
        .csrf(
            AbstractHttpConfigurer::disable) // 禁用 CSRF 保护，因为我们使用 JWT 或其他方式进行保护 (禁用 CSRF 保护，因为我们使用 JWT 或其他方式进行保护)
        .sessionManagement(
            session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy
                        .IF_REQUIRED)) // 设置会话创建策略为 IF_REQUIRED，即仅在需要时创建会话 (设置会话创建策略为 IF_REQUIRED，即仅在需要时创建会话)
        // 直接在 HttpSecurity 中配置 UserDetailsService (直接在HttpSecurity中配置UserDetailsService)
        .userDetailsService(userDetailsService)
        .authorizeHttpRequests(
            auth ->
                auth
                    // 允许访问的URL，无需认证 (允许访问的URL，无需认证)
                    .requestMatchers(
                        "/login",
                        "/register",
                        "/api/auth/login",
                        "/api/auth/register",
                        "/static/**",
                        "/css/**",
                        "/js/**",
                        "/assets/**",
                        "/img/**")
                    .permitAll()
                    // 访问 "/topup" 和 "/api/auth/topup" 需要认证 (访问 "/topup" 和 "/api/auth/topup" 需要认证)
                    .requestMatchers("/topup", "/api/auth/topup")
                    .authenticated()
                    // 访问 "/api/**" 下的所有URL需要认证 (访问 "/api/**" 下的所有URL需要认证)
                    .requestMatchers("/api/**")
                    .authenticated()
                    // 其他任何请求都需要认证 (其他任何请求都需要认证)
                    .anyRequest()
                    .authenticated())
        .formLogin(
            form ->
                form
                    // 指定登录页面URL (指定登录页面URL)
                    .loginPage("/login")
                    // 指定处理登录请求的URL (指定处理登录请求的URL)
                    .loginProcessingUrl("/api/auth/login")
                    // 指定用户名参数名 (指定用户名参数名)
                    .usernameParameter("username")
                    // 指定密码参数名 (指定密码参数名)
                    .passwordParameter("password")
                    // 指定认证成功处理器 (指定认证成功处理器)
                    .successHandler(customAuthenticationSuccessHandler)
                    // 指定认证失败处理器 (指定认证失败处理器)
                    .failureHandler(customAuthenticationFailureHandler))
        .logout(
            logout ->
                logout
                    // 指定注销URL (指定注销URL)
                    .logoutUrl("/api/auth/logout")
                    // 指定注销成功处理器 (指定注销成功处理器)
                    .logoutSuccessHandler(
                        (request, response, authentication) -> {
                          // 设置响应状态码为 200 (设置响应状态码为 200)
                          response.setStatus(200);
                          // 设置响应内容类型为 JSON (设置响应内容类型为 JSON)
                          response.setContentType("application/json;charset=UTF-8");
                          // 向响应中写入 JSON 消息 (向响应中写入 JSON 消息)
                          response.getWriter().write("{\"message\":\"登出成功\"}");
                        })
                    .permitAll()); // 允许所有用户访问注销URL (允许所有用户访问注销URL)
    return http.build(); // 构建 SecurityFilterChain 并返回 (构建 SecurityFilterChain 并返回)
  }

  /**
   * 管理员安全过滤器链 Bean。
   *
   * <p>
   *   定义了针对 "/admin/**" URL模式的安全规则。
   *   包括 CSRF 保护禁用、会话管理、URL 授权、表单登录和注销等配置。
   * </p>
   *
   * @param http HttpSecurity 对象，用于配置安全规则。 (用于配置安全规则的HttpSecurity对象)
   * @return SecurityFilterChain 安全过滤器链实例。 (安全过滤器链的实例)
   * @throws Exception 如果配置过程中发生错误，则抛出异常。 (如果配置过程中发生错误，则抛出异常)
   */
  @Bean
  @Order(1) // 定义执行顺序，数值越小优先级越高 (定义执行顺序，数值越小优先级越高)
  public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/admin/**") // 匹配所有/admin/**开头的请求 (匹配所有/admin/**开头的请求)
        .csrf(
            AbstractHttpConfigurer::disable) // 禁用 CSRF 保护，因为我们使用 JWT 或其他方式进行保护 (禁用 CSRF 保护，因为我们使用 JWT 或其他方式进行保护)
        // 直接在 HttpSecurity 中配置 AdminDetailsService (直接在HttpSecurity中配置AdminDetailsService)
        .userDetailsService(adminDetailsService)
        .sessionManagement(
            session ->
                session
                    // 总是创建会话 (总是创建会话)
                    .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                    // 限制最大会话数为 1，即同一时间只能有一个管理员登录 (限制最大会话数为 1，即同一时间只能有一个管理员登录)
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(true)) // 添加此项以完成配置 (添加此项以完成配置)
        .authorizeHttpRequests(
            auth ->
                auth
                    // 允许访问的URL，无需认证 (允许访问的URL，无需认证)
                    .requestMatchers("/admin/login", "/admin/auth/login")
                    .permitAll()
                    // 允许访问静态资源，无需认证 (允许访问静态资源，无需认证)
                    .requestMatchers("/static/**", "/assets/**", "/css/**", "/js/**", "/img/**")
                    .permitAll()
                    // 其他任何请求都需要 ADMIN 角色 (其他任何请求都需要 ADMIN 角色)
                    .anyRequest()
                    .hasRole("ADMIN")) // 需要 ADMIN 角色才能访问 (需要 ADMIN 角色才能访问)
        .formLogin(
            form ->
                form
                    // 指定登录页面URL (指定登录页面URL)
                    .loginPage("/admin/login")
                    // 指定处理登录请求的URL (指定处理登录请求的URL)
                    .loginProcessingUrl("/admin/auth/login")
                    // 指定用户名参数名 (指定用户名参数名)
                    .usernameParameter("username")
                    // 指定密码参数名 (指定密码参数名)
                    .passwordParameter("password")
                    // 指定认证成功处理器
                    .successHandler(
                        (request, response, auth) -> {
                          // 设置响应状态码为 200
                          response.setStatus(HttpServletResponse.SC_OK);
                          // 设置响应内容类型为 JSON
                          response.setContentType("application/json;charset=UTF-8");
                          // 向响应中写入 JSON 消息，包含登录状态、消息和用户名
                          response
                              .getWriter()
                              .write(
                                  "{\"success\":true, \"message\":\"管理员登录成功\", \"username\":\""
                                      + auth.getName()
                                      + "\"}");
                        })
                    // 指定认证失败处理器
                    .failureHandler(
                        (request, response, ex) -> {
                          // 设置响应状态码为 401 (Unauthorized)
                          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                          // 设置响应内容类型为 JSON
                          response.setContentType("application/json;charset=UTF-8");
                          // 向响应中写入 JSON 消息，包含登录失败状态和错误消息
                          response
                              .getWriter()
                              .write("{\"success\":false, \"message\":\"" + ex.getMessage() + "\"}");
                        }))
        .logout(
            logout ->
                logout
                    // 指定注销URL (指定注销URL)
                    .logoutUrl("/admin/auth/logout")
                    // 指定注销成功处理器 (指定注销成功处理器)
                    .logoutSuccessHandler(
                        (request, response, auth) -> {
                          // 设置响应状态码为 200 (设置响应状态码为 200)
                          response.setStatus(HttpServletResponse.SC_OK);
                          // 设置响应内容类型为 JSON (设置响应内容类型为 JSON)
                          response.setContentType("application/json;charset=UTF-8");
                          // 向响应中写入 JSON 消息 (向响应中写入 JSON 消息)
                          response.getWriter().write("{\"success\":true, \"message\":\"管理员登出成功\"}");
                        })
                    .permitAll()); // 允许所有用户访问注销URL (允许所有用户访问注销URL)
    return http.build(); // 构建 SecurityFilterChain 并返回 (构建 SecurityFilterChain 并返回)
  }
}