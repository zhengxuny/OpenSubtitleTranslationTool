package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Admin;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface AdminDetailsService extends UserDetailsService {
    // 添加特定于管理员的额外方法
    boolean verifyPassword(Admin admin, String rawPassword);
}