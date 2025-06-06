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

    /**
     * 处理用户账户充值请求，为当前登录用户的账户余额增加指定金额。
     *
     * @param request 充值请求对象，包含充值金额信息
     * @return 包含充值结果消息、用户ID和用户名的响应实体
     */
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