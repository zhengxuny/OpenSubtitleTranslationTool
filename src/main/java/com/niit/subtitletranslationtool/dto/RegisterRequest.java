package com.niit.subtitletranslationtool.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注册请求的数据传输对象（DTO），封装注册流程所需的核心用户信息。
 * 包含用户名、密码和电子邮件三个必要字段，用于在客户端与服务端之间传输注册请求数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    // 用户唯一标识符，用于登录认证和用户身份识别
    private String username;
    // 登录密码字段，存储用户设置的访问凭证（需配合加密机制存储）
    private String password;
    // 联系邮箱字段，用于账户验证、密码找回等关键操作的通知渠道
    private String email;
}