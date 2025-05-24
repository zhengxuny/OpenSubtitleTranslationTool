package com.niit.subtitletranslationtool.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.niit.subtitletranslationtool.entity.User;
import com.niit.subtitletranslationtool.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserMapper userMapper;

    public CustomAuthenticationSuccessHandler(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");

        // 从Authentication对象中获取用户名
        String username = authentication.getName();
        User user = userMapper.findByUsername(username); // 再次从数据库获取用户完整信息

        Map<String, Object> data = new HashMap<>();
        data.put("message", "登录成功");
        if (user != null) {
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("balance", user.getBalance());
        }
        objectMapper.writeValue(response.getWriter(), data);
    }
}