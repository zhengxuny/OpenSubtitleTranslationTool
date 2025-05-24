package com.niit.subtitletranslationtool.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 这是一个用于注册请求的数据传输对象（DTO）类。
 * 它封装了用户注册所需的必要信息，并利用Lombok库简化代码。
 */
@Data
@NoArgsConstructor // 无参构造函数，用于没有初始参数的情况下创建RegisterRequest对象
@AllArgsConstructor // 有参构造函数，用于一次性初始化所有重要字段的RegisterRequest对象
public class RegisterRequest {
    // 用户名字段，用于存储用户选择的唯一标识符
    private String username;
    // 密码字段，用于存储用户选择的私密访问凭证
    private String password;
    // 电子邮件字段，用于存储用户的主要联系方式，也是身份验证的重要方法之一
    private String email;
}