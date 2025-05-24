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

// AuthController 是一个用于处理用户认证操作的 REST 控制器，包括用户注册和登录。
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // UserService 是一个处理用户数据的服务类，例如创建新用户和认证现有用户。
    private final UserService userService;
    // AuthenticationManager 是 Spring Security 用于处理认证的管理器。
    private final AuthenticationManager authenticationManager;

    // 构造函数用于初始化 AuthController，接收所需的依赖项。
    @Autowired
    public AuthController(UserService userService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    // register 方法用于处理用户的注册请求。
    // 它接收一个 RegisterRequest 对象，并尝试创建一个新用户。
    // 如果注册成功，返回一个包含成功消息、用户ID和用户名的 AuthResponse 对象。
    // 如果注册失败（例如用户名已存在），则返回一个包含错误消息的 AuthResponse 对象。
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

    // login 方法用于处理用户的登录请求。
    // 它接收一个 LoginRequest 对象，并使用 AuthenticationManager 验证用户凭证。
    // 如果认证成功，将认证结果存储在 SecurityContext 中，并返回一个包含成功消息、用户ID和用户名的 AuthResponse 对象。
    // 如果认证失败，则返回一个包含错误消息的 AuthResponse 对象。
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        try {
            // 通过 AuthenticationManager 进行认证。
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            // 认证成功，将 Authentication 对象设置到 SecurityContext 中。
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 如果认证成功，从 UserService 获取完整用户数据并返回。
            User authenticatedUser = userService.authenticateUser(request.getUsername(), request.getPassword());
            AuthResponse response = new AuthResponse(
                    "登录成功",
                    authenticatedUser.getId(),
                    authenticatedUser.getUsername()
            );
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            // 认证失败，返回一个包含错误消息的 AuthResponse 对象。
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse("登录失败: " + e.getMessage(), null, null));
        }
    }
}