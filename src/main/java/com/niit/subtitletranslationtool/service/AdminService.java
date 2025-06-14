package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Admin;
import com.niit.subtitletranslationtool.mapper.AdminMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

/**
 * 管理员服务类，实现了Spring Security的UserDetailsService接口，用于处理管理员的认证和授权。
 */
@Service
public class AdminService implements UserDetailsService, AdminDetailsService {

    private final AdminMapper adminMapper;

    private final PasswordEncoder passwordEncoder;

    /**
     * 构造函数，用于依赖注入。
     *
     * @param adminMapper     管理员Mapper，用于访问数据库中的管理员信息。
     * @param passwordEncoder 密码编码器，用于密码加密和验证。
     */
    public AdminService(AdminMapper adminMapper, PasswordEncoder passwordEncoder) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
    }


//    @Override
//    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
//        // 从数据库中查询管理员信息
//        System.out.println("尝试查询管理员用户：" + username);
//        Admin admin = adminMapper.selectByUsername(username);
//
//        // 如果管理员不存在，抛出异常
//        if (admin == null) {
//            System.err.println("管理员用户未找到：" + username);
//            throw new UsernameNotFoundException("管理员不存在");
//        }
//
//        // 确保密码不为空
//        if (admin.getPassword() == null) {
//            System.err.println("管理员密码为空：" + username);
//            throw new UsernameNotFoundException("管理员密码配置异常");
//        }
//
//        // 创建UserDetails对象，包含用户名、密码和权限
//        // 这里只赋予管理员 "ROLE_ADMIN" 角色
//        return new User(
//                admin.getUsername(),
//                admin.getPassword(),
//                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
//        );
//    }
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 从数据库中查询管理员信息
        System.out.println("尝试查询管理员用户：" + username);
        Optional<Admin> adminOptional = Optional.ofNullable(adminMapper.selectByUsername(username));

        // 如果管理员不存在，抛出异常
        Admin admin = adminOptional.orElseThrow(() -> {
            System.err.println("管理员用户未找到：" + username);
            return new UsernameNotFoundException("管理员不存在");
        });

        // 确保密码不为空
        if (admin.getPassword() == null) {
            System.err.println("管理员密码为空：" + username);
            throw new UsernameNotFoundException("管理员密码配置异常");
        }

        // 创建UserDetails对象，包含用户名、密码和权限
        // 这里只赋予管理员 "ROLE_ADMIN" 角色
        return new User(
                admin.getUsername(),
                admin.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    /**
     * 验证密码是否匹配。
     *
     * @param admin       管理员对象，包含加密后的密码。
     * @param rawPassword 原始密码（用户输入的密码）。
     * @return 如果密码匹配，则返回true；否则返回false。
     */
    public boolean verifyPassword(Admin admin, String rawPassword) {
        return passwordEncoder.matches(rawPassword, admin.getPassword());
    }
}