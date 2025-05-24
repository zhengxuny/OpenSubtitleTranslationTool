package com.niit.subtitletranslationtool.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; // 使用BigDecimal类进行精确的金额计算
import java.time.LocalDateTime; // 使用LocalDateTime类表示日期和时间

@Data // Lombok注解，自动生成getter、setter、equals、hashCode和toString方法
@NoArgsConstructor // Lombok注解，自动生成无参构造函数
@AllArgsConstructor // Lombok注解，自动生成包含所有字段的构造函数
@Builder // Lombok注解，自动生成构建器类，方便对象的链式调用创建
public class User {
    // 用户ID，用于唯一标识用户，数据库中自动生成并递增
    private Long id;

    // 用户名，可以用于用户标识和登录
    private String username;

    // 密码，存储时经过加密处理
    private String password;

    // 邮箱地址，可用于密码找回、通知等功能
    private String email;

    // 用户账户余额，使用BigDecimal来避免浮点数运算中的精度问题
    private BigDecimal balance;

    // 用户创建时间，默认为当前时间戳
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // 记录用户信息最后更新的时间，默认为当前时间戳
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}