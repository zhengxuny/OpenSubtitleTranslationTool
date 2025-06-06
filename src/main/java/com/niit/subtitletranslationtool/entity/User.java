package com.niit.subtitletranslationtool.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户实体类，用于表示系统中的用户信息，包含用户基础属性及数据库映射字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    // 用户唯一标识ID，由数据库自动生成并自增
    private Long id;

    // 登录用户名，用于用户身份标识和系统登录
    private String username;

    // 加密后的登录密码
    private String password;

    // 联系邮箱，用于密码找回和系统通知
    private String email;

    // 用户账户余额，使用BigDecimal保证数值计算精度
    private BigDecimal balance;

    // 用户记录创建时间，默认值为对象创建时的当前时间
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // 用户信息最后更新时间，默认值为对象创建时的当前时间
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}