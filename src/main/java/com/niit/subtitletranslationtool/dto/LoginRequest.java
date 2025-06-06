package com.niit.subtitletranslationtool.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录请求数据传输对象，用于封装用户登录时提交的用户名和密码信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private String username;
    private String password;
}