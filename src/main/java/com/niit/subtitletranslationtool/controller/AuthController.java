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
 * <h1>AuthController</h1>
 * <p>
 *   处理用户认证相关的REST控制器，提供注册、登录和账户充值等接口。
 *   所有接口的URL都以 "/api/auth" 开头。
 * </p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    /**
     * 构造函数，用于依赖注入。
     *
     * @param userService 用户服务，用于处理用户相关的业务逻辑。
     * @param authenticationManager 认证管理器，用于处理用户认证。
     */
    @Autowired
    public AuthController(UserService userService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    /**
     * <h1>register</h1>
     * <p>
     *   处理用户的注册请求。接收一个 {@link RegisterRequest} 对象，
     *   并尝试创建一个新用户。
     * </p>
     *
     * @param request 包含用户注册信息的请求体，如用户名、密码等。
     * @return {@link ResponseEntity<AuthResponse>} 返回一个包含注册结果的响应实体。
     *         如果注册成功，返回状态码 201 (CREATED) 和包含成功消息、用户ID和用户名的 {@link AuthResponse} 对象。
     *         如果注册失败（例如用户名已存在），则返回状态码 400 (BAD_REQUEST) 和包含错误消息的 {@link AuthResponse} 对象。
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        try {
            // 调用 userService 的 registerUser 方法来注册用户
            User registeredUser = userService.registerUser(request);
            // 创建 AuthResponse 对象，包含注册成功的消息和用户信息
            AuthResponse response = new AuthResponse(
                    "注册成功",
                    registeredUser.getId(),
                    registeredUser.getUsername()
            );
            // 返回注册成功的响应，状态码为 201 (CREATED)
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            // 如果注册过程中发生异常，返回注册失败的响应，状态码为 400 (BAD_REQUEST)
            return ResponseEntity.badRequest().body(new AuthResponse(e.getMessage(), null, null));
        }
    }

    /**
     * <h1>topUp</h1>
     * <p>
     *   处理用户的账户充值请求。
     * </p>
     *
     * @param request 包含充值金额的请求体。
     * @return {@link ResponseEntity<AuthResponse>} 返回一个包含充值结果的响应实体。
     *         如果充值成功，返回状态码 200 (OK) 和包含成功消息、当前余额、用户ID和用户名的 {@link AuthResponse} 对象。
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