package com.niit.subtitletranslationtool.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 类名: AuthResponse
 * 作用: 用于封装用户认证成功后的响应信息。
 * 说明: 该类使用了Lombok库，通过@Data注解自动生成getter、setter、toString、equals和hashCode方法；
 *        通过@NoArgsConstructor和@AllArgsConstructor自动生成无参和全参构造方法。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * 属性名: message
     * 类型: String
     * 作用: 存储认证结果的状态信息或错误信息。
     * 示例: "登录成功" 或 "用户名或密码错误"
     */
    private String message;

    /**
     * 属性名: userId
     * 类型: Long
     * 作用: 存储认证成功的用户的唯一标识符ID。
     * 示例: 123456789
     */
    private Long userId;

    /**
     * 属性名: username
     * 类型: String
     * 作用: 存储认证成功的用户的用户名。
     * 示例: "john_doe"
     */
    private String username;
    // 如果使用JWT，这里可以添加token字段来存储JSON Web Token字符串。
    // 例如:
    // private String token;
    // 其作用: 存储认证成功后生成的JWT，以便客户端在后续请求中携带身份验证信息。
}