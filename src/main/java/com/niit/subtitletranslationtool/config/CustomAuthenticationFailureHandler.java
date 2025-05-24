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
 * 自定义的身份验证失败处理器类。
 * 该类实现了Spring Security提供的AuthenticationFailureHandler接口。
 * 它用于在用户身份验证失败时处理异常并返回自定义的错误响应。
 */
@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    /**
     * ObjectMapper是Jackson库中的一个类，用于序列化和反序列化JSON数据。
     * 该对象实例在这里用于将Java对象转换为JSON格式的字符串。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 当用户身份验证失败时，会调用此方法。
     * 这个方法是AuthenticationFailureHandler接口的一部分。
     *
     * @param request   包含用户身份验证请求信息的对象。
     * @param response  包含HTTP响应信息的对象。
     * @param exception 包含身份验证过程中发生的异常信息的对象。
     * @throws IOException      当在写入响应时发生IO错误时抛出。
     * @throws ServletException 当在处理身份验证失败时发生Servlet异常时抛出。
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        // 设置响应的状态码为401，表示未授权。
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // 设置响应的内容类型为JSON格式。
        response.setContentType("application/json;charset=UTF-8");

        /**
         * 创建一个Map对象用于存储错误信息。
         * 这里的映射关系是：键为"message"，值为错误的消息内容。
         */
        Map<String, String> data = new HashMap<>();
        // 在错误消息中包含异常的消息内容，这个消息通常会是“Bad credentials”或“User not found”。
        data.put("message", "登录失败: " + exception.getMessage());

        /**
         * 使用objectMapper将Map对象转换为JSON字符串，并将其写入到HTTP响应的输出流中。
         * 这样客户端就可以接收到JSON格式的错误消息。
         */
        objectMapper.writeValue(response.getWriter(), data);
    }
}