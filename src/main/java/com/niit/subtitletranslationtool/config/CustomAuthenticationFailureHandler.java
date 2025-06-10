package com.niit.subtitletranslationtool.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationFailureHandler.class); // 新增日志

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
        try {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, String> data = new HashMap<>();
        data.put("message", "登录失败: " + exception.getMessage());

        objectMapper.writeValue(response.getWriter(), data);
        } catch (Exception e) {
            log.error("处理认证失败响应时发生异常", e); // 记录异常日志
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"message\":\"服务器处理登录失败时发生异常\"}");
    }
}
}
