package com.niit.subtitletranslationtool.entity;

import lombok.Data;

/**
 * 表示系统管理员的实体类，包含管理员的基本信息（如唯一标识、用户名和哈希密码）。
 */
@Data
public class Admin {
    private Integer id;                // 管理员唯一标识ID
    private String username;           // 管理员用户名（系统内唯一标识）
    private String password;           // 管理员密码的哈希值（非明文存储）
}