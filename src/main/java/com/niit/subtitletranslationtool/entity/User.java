package com.niit.subtitletranslationtool.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; // 推荐使用BigDecimal处理金额
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    private Long id; // 用户ID，数据库自增主键
    private String username; // 用户名
    private String password; // 密码（存储时应加密处理）
    private String email; // 邮箱，可用于找回密码或通知
    private BigDecimal balance; // 用户余额，推荐使用BigDecimal避免浮点数精度问题

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(); // 注册时间
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now(); // 信息更新时间
}