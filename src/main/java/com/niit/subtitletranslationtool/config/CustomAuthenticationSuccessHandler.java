package com.niit.subtitletranslationtool.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.niit.subtitletranslationtool.entity.User;
import com.niit.subtitletranslationtool.mapper.UserMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义认证成功处理器，用于处理用户登录成功后的响应逻辑。
 * 实现Spring Security的AuthenticationSuccessHandler接口，负责返回包含用户信息的JSON响应。
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    // 用于JSON对象与Java对象之间的序列化和反序列化
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 用户数据库操作接口，用于查询用户详细信息
    private final UserMapper userMapper;

    /**
     * 构造函数，通过依赖注入初始化用户数据库操作对象。
     *
     * @param userMapper 用户数据库操作接口实现类实例
     */
    public CustomAuthenticationSuccessHandler(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 处理用户认证成功后的响应逻辑。
     * 设置HTTP响应状态为成功，返回包含用户基本信息（ID、用户名、余额）的JSON数据。
     *
     * @param request      包含客户端请求信息的HTTP请求对象
     * @param response     用于构造响应的HTTP响应对象
     * @param authentication 包含认证成功用户信息的认证对象
     * @throws IOException 当JSON数据写入响应流失败时抛出
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        // 设置HTTP响应状态码为200（成功）
        response.setStatus(HttpServletResponse.SC_OK);
        // 指定响应内容类型为UTF-8编码的JSON格式
        response.setContentType("application/json;charset=UTF-8");

        // 从认证对象中提取登录用户的用户名
        String username = authentication.getName();
        // 通过用户Mapper根据用户名查询数据库获取用户详细信息
        User user = userMapper.findByUsername(username);

        // 创建并初始化响应数据容器，添加登录成功提示信息
        Map<String, Object> data = new HashMap<>();
        data.put("message", "登录成功");

        // 若用户信息查询成功（非空）
        if (user != null) {
            // 向响应数据中添加用户ID、用户名和余额信息
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("balance", user.getBalance());
        }

        // 使用ObjectMapper将响应数据序列化为JSON并写入响应输出流
        objectMapper.writeValue(response.getWriter(), data);
    }
}