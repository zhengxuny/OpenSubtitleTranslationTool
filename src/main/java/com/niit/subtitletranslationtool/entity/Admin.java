package com.niit.subtitletranslationtool.entity;

import lombok.Data;

@Data
public class Admin {
    private Integer id;
    private String username; // 用户名（唯一）
    private String password; // 密码（哈希值）
}