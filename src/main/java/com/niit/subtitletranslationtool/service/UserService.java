package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.dto.RegisterRequest;
import com.niit.subtitletranslationtool.entity.User;
import com.niit.subtitletranslationtool.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

@Service
public class UserService implements UserDetailsService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder; // 用于密码加密

    @Autowired
    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户注册逻辑
     * @param request 注册请求DTO
     * @return 注册成功的用户实体
     * @throws RuntimeException 如果用户名或邮箱已存在
     */
    @Transactional
    public User registerUser(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new RuntimeException("用户名已存在");
        }
        // 检查邮箱是否已存在
        if (userMapper.findByEmail(request.getEmail()) != null) {
            throw new RuntimeException("邮箱已存在");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword())) // 密码加密
                .email(request.getEmail())
                .balance(BigDecimal.ZERO) // 注册时默认余额为0
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userMapper.insertUser(user);
        return user;
    }

    /**
     * 根据用户名加载用户详情，供Spring Security使用
     * @param username 用户名
     * @return UserDetails 对象
     * @throws UsernameNotFoundException 如果用户不存在
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户未找到: " + username);
        }
        // 这里返回Spring Security的User对象，包含用户名、加密后的密码和权限
        // 暂时不设置具体角色，只返回一个空权限列表，表示已认证
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Collections.emptyList() // 暂时不分配权限，后续可扩展
        );
    }

    /**
     * 验证用户登录凭据
     * @param username 用户名
     * @param rawPassword 原始密码
     * @return 认证成功的用户实体
     * @throws RuntimeException 如果认证失败
     */
    public User authenticateUser(String username, String rawPassword) {
        User user = userMapper.findByUsername(username);
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new RuntimeException("用户名或密码不正确");
        }
        return user;
    }
}