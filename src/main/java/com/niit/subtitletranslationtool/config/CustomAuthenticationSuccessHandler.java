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

/**
 * 自定义身份验证成功处理器，当用户成功登录时调用。
 * 该类实现了Spring Security的AuthenticationSuccessHandler接口。
 * 用于在用户成功认证后处理用户的响应信息。
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    /**
     * ObjectMapper对象，用于将对象转换为JSON格式。
     * 本例中用于将HashMap对象转换为JSON并写入HTTP响应体。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 用户映射器，由Spring注入的UserMapper对象，
     * 用于从数据库中查询用户信息。
     */
    private final UserMapper userMapper;

    /**
     * 构造函数，用于初始化UserMapper对象。
     * 通过构造函数注入的方式进行依赖注入，确保UserMapper对象可以被正确使用。
     *
     * @param userMapper 用户数据库操作对象
     */
    public CustomAuthenticationSuccessHandler(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 当用户成功认证后调用此方法。
     * 查找并返回用户的身份信息（id，username，balance）作为JSON格式的数据。
     * 设置HTTP响应状态码为200（OK），并指定数据类型为UTF-8编码的application/json。
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param authentication 包含用户身份信息的认证对象
     * @throws IOException 如果在输出JSON数据时发生I/O错误，将抛出IOException
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        // 设置.HTTP响应的状态码为200 OK，表示请求成功。
        response.setStatus(HttpServletResponse.SC_OK);
        // 设置HTTP响应的Content-Type头，指定返回的数据类型为UTF-8编码的JSON。
        response.setContentType("application/json;charset=UTF-8");

        // 从认证对象中获取用户的名称（通常是用户名）。
        String username = authentication.getName();
        // 通过UserMapper的findByUsername方法根据用户名从数据库中获取完整的用户信息对象（如果存在）。
        User user = userMapper.findByUsername(username);

        // 创建存储响应数据的哈希映射。
        Map<String, Object> data = new HashMap<>();
        // 添加登录成功的消息至响应数据。
        data.put("message", "登录成功");
        // 如果成功找到了用户信息（即user不为null），则添加userId、username和balance至响应数据。
        if (user != null) {
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("balance", user.getBalance());
        }

        // 使用ObjectMapper将响应数据转换为JSON字符串并写入HTTP响应体。
        objectMapper.writeValue(response.getWriter(), data);
    }
}