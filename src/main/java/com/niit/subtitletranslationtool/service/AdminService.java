package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Admin;
import com.niit.subtitletranslationtool.mapper.AdminMapper;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 管理员用户相关服务，实现基于Spring Security的用户认证接口。
 * 通过数据库查询管理员信息，提供认证所需的用户详情。
 */
@Service
public class AdminService implements UserDetailsService {

    private final AdminMapper adminMapper;

    private final PasswordEncoder passwordEncoder;

    /**
     * 构造函数，注入管理员数据访问接口和密码编码器。
     *
     * @param adminMapper    管理员数据访问对象
     * @param passwordEncoder 密码加密与匹配工具
     */
    public AdminService(AdminMapper adminMapper, PasswordEncoder passwordEncoder) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 根据用户名加载管理员用户详情，供Spring Security认证流程调用。
     * 如果用户名不存在或密码信息异常，将抛出相关异常。
     *
     * @param username 管理员用户名，非空
     * @return 包含管理员用户名、密码和角色权限的Spring Security用户详情对象
     * @throws UsernameNotFoundException 当用户不存在或密码无效时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 查询数据库中的管理员用户信息
        System.out.println("尝试查询管理员用户：" + username);
        Admin admin = adminMapper.selectByUsername(username);
        if (admin == null) {
            // 管理员不存在，抛出Spring Security识别的用户未找到异常
            System.err.println("管理员用户未找到：" + username);
            throw new UsernameNotFoundException("管理员不存在");
        }
        // 确保密码字段不为空，避免认证空指针异常
        if (admin.getPassword() == null) {
            System.err.println("管理员密码为空：" + username);
            throw new UsernameNotFoundException("管理员密码配置异常");
        }
        // 返回包含角色权限的UserDetails，角色固定为ROLE_ADMIN
        return new User(
                admin.getUsername(),
                admin.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    /**
     * 校验明文密码与管理员数据库中加密密码是否匹配。
     *
     * @param admin       管理员实体，必须包含已加密的密码字段
     * @param rawPassword 用户输入的明文密码
     * @return 匹配返回true，不匹配返回false
     */
    public boolean verifyPassword(Admin admin, String rawPassword) {
        return passwordEncoder.matches(rawPassword, admin.getPassword());
    }
}
