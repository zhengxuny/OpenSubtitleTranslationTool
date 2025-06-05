package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Admin;
import com.niit.subtitletranslationtool.mapper.AdminMapper;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority; // <-- 新增导入
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
        // 添加日志输出
        System.out.println("尝试查询管理员用户：" + username);
        Admin admin = adminMapper.selectByUsername(username);
        if (admin == null) {
            System.err.println("管理员用户未找到：" + username); // 错误日志
            throw new UsernameNotFoundException("管理员不存在");
        }
        // 确认密码字段非空（防止空指针）
        if (admin.getPassword() == null) {
            System.err.println("管理员密码为空：" + username);
            throw new UsernameNotFoundException("管理员密码配置异常");
        }
        return new User(
            admin.getUsername(),
            admin.getPassword(),
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    /**
     * 手动验证管理员密码（可选扩展）
     */
    public boolean verifyPassword(Admin admin, String rawPassword) {
        return passwordEncoder.matches(rawPassword, admin.getPassword());
    }
}