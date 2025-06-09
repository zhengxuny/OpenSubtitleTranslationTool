package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.dto.AuthResponse;
import com.niit.subtitletranslationtool.dto.LoginRequest;
import com.niit.subtitletranslationtool.dto.RegisterRequest;
import com.niit.subtitletranslationtool.dto.TopUpRequest;
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

/**
 * 处理用户认证相关的REST控制器，提供注册、登录和账户充值等接口。
 * 接口路径前缀为"/api/auth"。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    /**
     * 初始化认证控制器，注入用户服务和认证管理器依赖。
     *
     * @param userService             用户服务类，处理用户数据操作
     * @param authenticationManager   Spring Security认证管理器，处理用户认证逻辑
     */
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

//    // login 方法用于处理用户的登录请求。
//    // 它接收一个 LoginRequest 对象，并使用 AuthenticationManager 验证用户凭证。
//    // 如果认证成功，将认证结果存储在 SecurityContext 中，并返回一个包含成功消息、用户ID和用户名的 AuthResponse 对象。
//    // 如果认证失败，则返回一个包含错误消息的 AuthResponse 对象。
//    @PostMapping("/login")
//    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
//        try {
//            // 通过 AuthenticationManager 进行认证。
//            Authentication authentication = authenticationManager.authenticate(
//                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
//            );
//            // 认证成功，将 Authentication 对象设置到 SecurityContext 中。
//            SecurityContextHolder.getContext().setAuthentication(authentication);
//
//            // 如果认证成功，从 UserService 获取完整用户数据并返回。
//            User authenticatedUser = userService.authenticateUser(request.getUsername(), request.getPassword());
//            AuthResponse response = new AuthResponse(
//                    "登录成功",
//                    authenticatedUser.getId(),
//                    authenticatedUser.getUsername()
//            );
//            return ResponseEntity.ok(response);
//        } catch (AuthenticationException e) {
//            // 认证失败，返回一个包含错误消息的 AuthResponse 对象。
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse("登录失败: " + e.getMessage(), null, null));
//        }
//    }


    @PostMapping("/topup")
    public ResponseEntity<AuthResponse> topUp(@RequestBody TopUpRequest request) {
        // 从安全上下文中获取当前认证信息，获取已登录用户的用户名
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // 根据用户名查询用户信息（无需密码验证）
        User user = userService.getUserByUsername(username);

        // 调用用户服务完成充值操作，更新账户余额
        User updatedUser = userService.topUpUserBalance(user.getId(), request.getAmount());

        // 构造充值成功响应，包含当前余额、用户ID和用户名
        AuthResponse response = new AuthResponse(
                "充值成功，当前余额：" + updatedUser.getBalance(),
                updatedUser.getId(),
                updatedUser.getUsername()
        );
        return ResponseEntity.ok(response);
    }
}