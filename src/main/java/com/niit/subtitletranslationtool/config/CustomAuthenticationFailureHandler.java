package com.niit.subtitletranslationtool.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code CustomAuthenticationFailureHandler} 是一个自定义的身份验证失败处理器。
 * <p>
 *   它实现了 Spring Security 的 {@link AuthenticationFailureHandler} 接口，
 *   用于在用户登录验证失败时，向客户端返回 JSON 格式的自定义错误响应。
 *   这样做的好处是可以提供更友好的错误提示，而不是默认的 HTML 错误页面。
 * </p>
 * <p>
 *   当用户提供的用户名、密码不正确，或者账户被禁用等情况导致登录失败时，
 *   该处理器会被 Spring Security 框架自动调用。
 * </p>
 */
@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    // 用于将 Java 对象序列化为 JSON 字符串，以及将 JSON 字符串反序列化为 Java 对象。
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 用于记录日志信息，方便调试和问题排查。
    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationFailureHandler.class);

    /**
     * {@inheritDoc}
     * <p>
     *   当身份验证失败时，该方法会被 Spring Security 框架调用，用于处理登录失败的请求。
     *   它会设置 HTTP 响应的状态码为 401 (Unauthorized)，表示未授权访问，
     *   并设置响应的内容类型为 "application/json;charset=UTF-8"，表示返回的是 JSON 格式的数据。
     * </p>
     * <p>
     *   然后，它会创建一个包含错误信息的 Map 对象，并将错误信息封装到该 Map 对象中。
     *   最后，它会将该 Map 对象转换为 JSON 字符串，并通过 HTTP 响应返回给客户端。
     * </p>
     *
     * @param request   HTTP 请求对象，包含了客户端发起的请求信息，例如请求头、请求参数等。
     * @param response  HTTP 响应对象，用于设置响应头、响应状态码、响应内容等。
     * @param exception 身份验证过程中抛出的异常对象，包含了身份验证失败的具体原因。
     *                  例如，{@code BadCredentialsException} 表示用户名或密码错误，
     *                  {@code DisabledException} 表示账户被禁用等。
     * @throws IOException      在写入响应输出流时，可能会发生 I/O 异常。
     * @throws ServletException 在处理 Servlet 请求时，可能会发生 Servlet 异常。
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        try {
            // 设置 HTTP 响应状态码为 401，表示未授权访问。
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            // 设置 HTTP 响应的内容类型为 "application/json;charset=UTF-8"，表示返回的是 JSON 格式的数据。
            response.setContentType("application/json;charset=UTF-8");

            // 创建一个 Map 对象，用于封装错误信息。
            Map<String, String> data = new HashMap<>();
            // 将错误信息封装到 Map 对象中。
            data.put("message", "登录失败: " + exception.getMessage());

            // 将 Map 对象转换为 JSON 字符串，并通过 HTTP 响应返回给客户端。
            objectMapper.writeValue(response.getWriter(), data);
        } catch (Exception e) {
            // 记录异常日志，方便调试和问题排查。
            log.error("处理认证失败响应时发生异常", e);
            // 设置 HTTP 响应状态码为 500，表示服务器内部错误。
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            // 向客户端返回一个简单的 JSON 错误信息。
            response.getWriter().write("{\"message\":\"服务器处理登录失败时发生异常\"}");
        }
    }
}