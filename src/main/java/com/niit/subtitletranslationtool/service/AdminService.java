package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Admin;
import com.niit.subtitletranslationtool.mapper.AdminMapper;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class AdminService implements UserDetailsService {

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminService(AdminMapper adminMapper, PasswordEncoder passwordEncoder) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 管理员登录认证核心方法（Spring Security自动调用）
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Admin admin = adminMapper.selectByUsername(username);
        if (admin == null) {
            throw new UsernameNotFoundException("管理员不存在");
        }
        // 返回带管理员角色的UserDetails对象
        return new User(
            admin.getUsername(),
            admin.getPassword(),
            Collections.singletonList(() -> "ROLE_ADMIN")  // 赋予ADMIN角色权限
        );
    }

    /**
     * 手动验证管理员密码（可选扩展）
     */
    public boolean verifyPassword(Admin admin, String rawPassword) {
        return passwordEncoder.matches(rawPassword, admin.getPassword());
    }
}