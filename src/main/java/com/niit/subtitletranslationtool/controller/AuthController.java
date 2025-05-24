package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.dto.AuthResponse;
import com.niit.subtitletranslationtool.dto.LoginRequest;
import com.niit.subtitletranslationtool.dto.RegisterRequest;
import com.niit.subtitletranslationtool.entity.User;
import com.niit.subtitletranslationtool.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager; // Spring Security的认证管理器

    @Autowired
    public AuthController(UserService userService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    /**
     * 用户注册接口
     * @param request 注册请求体
     * @return 注册响应
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        try {
            User registeredUser = userService.registerUser(request);
            AuthResponse response = new AuthResponse(
                    "注册成功",
                    registeredUser.getId(),
                    registeredUser.getUsername()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new AuthResponse(e.getMessage(), null, null));
        }
    }

    /**
     * 用户登录接口
     * 注意：实际登录由Spring Security的UsernamePasswordAuthenticationFilter处理
     * 此处仅用于展示如果需要手动触发认证
     * 实际项目中，formLogin配置的/api/auth/login路径会被Spring Security拦截处理，
     * 并由CustomAuthenticationSuccessHandler和CustomAuthenticationFailureHandler返回响应。
     * 所以，此方法体内的逻辑在Spring Security默认配置下不会被直接调用，但作为业务层面的认证示例是有用的。
     * 前端只需向/api/auth/login发送POST请求，包含username和password即可。
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        try {
            // 通过AuthenticationManager进行认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            // 认证成功，将Authentication对象设置到SecurityContext中
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 如果认证成功，可以从UserService获取完整用户数据并返回
            User authenticatedUser = userService.authenticateUser(request.getUsername(), request.getPassword()); // 此处为了获取User实体，实际可从authentication.getPrincipal()获取UserDetails再转型

            AuthResponse response = new AuthResponse(
                    "登录成功",
                    authenticatedUser.getId(),
                    authenticatedUser.getUsername()
            );
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            // 认证失败由CustomAuthenticationFailureHandler处理，这里作为备用或额外处理
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse("登录失败: " + e.getMessage(), null, null));
        }
    }
}