package com.niit.subtitletranslationtool.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义身份验证失败处理器，实现Spring Security的AuthenticationFailureHandler接口。
 * 用于在用户登录验证失败时，返回JSON格式的自定义错误响应。
 */
@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    // 用于JSON数据与Java对象的序列化和反序列化
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 处理身份验证失败的请求，返回自定义JSON错误响应。
     * 当用户登录验证失败时触发，设置HTTP状态码为401，并封装异常信息为JSON返回。
     *
     * @param request   HTTP请求对象，包含客户端请求信息
     * @param response  HTTP响应对象，用于设置响应内容
     * @param exception 身份验证过程中抛出的异常，包含具体错误信息
     * @throws IOException      写入响应输出流时发生IO异常
     * @throws ServletException 处理过程中发生Servlet异常
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        // 设置HTTP响应状态码为401（未授权）
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // 设置响应内容类型为JSON，字符集为UTF-8
        response.setContentType("application/json;charset=UTF-8");

        // 创建错误信息容器，用于存储返回的错误内容
        Map<String, String> data = new HashMap<>();
        // 将异常信息封装到消息字段中，提示登录失败原因
        data.put("message", "登录失败: " + exception.getMessage());

        // 将错误信息容器序列化为JSON，并写入响应输出流
        objectMapper.writeValue(response.getWriter(), data);
    }
}