package com.niit.subtitletranslationtool.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 封装用户认证成功后的响应信息数据传输对象（DTO）。
 * <p>
 * 包含认证结果消息、用户ID和用户名等核心认证信息，
 * 支持通过Lombok注解自动生成getter、setter及构造方法。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String message;  // 认证结果的状态信息或错误信息（如"登录成功"或"用户名错误"）
    private Long userId;     // 认证成功用户的唯一标识符ID
    private String username; // 认证成功用户的用户名
}