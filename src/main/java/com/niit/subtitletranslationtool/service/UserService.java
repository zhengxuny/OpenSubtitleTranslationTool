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

/**
 * UserService 类实现了用户相关的业务逻辑，包括用户注册、登录验证和 Spring Security 的用户认证。
 * 实现了 UserDetailsService 接口，用于 Spring Security 的用户认证。
 */
@Service // 使用 Spring 的 @Service 注解，将该类标记为一个服务组件，由 Spring 容器管理。
public class UserService implements UserDetailsService {

    private final UserMapper userMapper; // 用于访问数据库中用户数据的 Mapper 接口。
    private final PasswordEncoder passwordEncoder; // 用于密码加密的 PasswordEncoder 接口。

    /**
     * 构造函数，用于依赖注入。
     * @param userMapper  用户 Mapper，用于数据库操作。
     * @param passwordEncoder  密码加密器，用于加密用户密码。
     */
    @Autowired // 使用 Spring 的 @Autowired 注解，实现构造函数注入。
    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户注册逻辑。
     * @param request 注册请求 DTO，包含用户名、密码和邮箱。
     * @return 注册成功的用户实体。
     * @throws RuntimeException 如果用户名或邮箱已存在，抛出运行时异常。
     */
    @Transactional // 使用 Spring 的 @Transactional 注解，保证事务的一致性。
    public User registerUser(RegisterRequest request) {
        // 检查用户名是否已存在。
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new RuntimeException("用户名已存在"); // 如果用户名已存在，抛出异常。
        }
        // 检查邮箱是否已存在。
        if (userMapper.findByEmail(request.getEmail()) != null) {
            throw new RuntimeException("邮箱已存在"); // 如果邮箱已存在，抛出异常。
        }

        // 构建用户实体。
        User user = User.builder()
                .username(request.getUsername()) // 设置用户名。
                .password(passwordEncoder.encode(request.getPassword())) // 密码加密后存储。
                .email(request.getEmail()) // 设置邮箱。
                .balance(BigDecimal.ZERO) // 注册时默认余额为0。
                .createdAt(LocalDateTime.now()) // 设置创建时间。
                .updatedAt(LocalDateTime.now()) // 设置更新时间。
                .build();
        userMapper.insertUser(user); // 将用户实体插入数据库。
        return user; // 返回注册成功的用户实体。
    }

    /**
     * 根据用户名加载用户详情，供 Spring Security 使用。
     * @param username 用户名。
     * @return UserDetails 对象，包含用户名、密码和权限信息。
     * @throws UsernameNotFoundException 如果用户不存在，抛出此异常。
     */
    @Override // 实现了 UserDetailsService 接口的 loadUserByUsername 方法。
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.findByUsername(username); // 根据用户名从数据库中查找用户。
        if (user == null) {
            throw new UsernameNotFoundException("用户未找到: " + username); // 如果用户不存在，抛出异常。
        }
        // 这里返回 Spring Security 的 User 对象，包含用户名、加密后的密码和权限。
        // 暂时不设置具体角色，只返回一个空权限列表，表示已认证。
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(), // 用户名。
                user.getPassword(), // 密码。
                Collections.emptyList() // 暂时不分配权限，后续可扩展。
        );
    }

    /**
     * 验证用户登录凭据。
     * @param username 用户名。
     * @param rawPassword 原始密码。
     * @return 认证成功的用户实体。
     * @throws RuntimeException 如果认证失败，抛出运行时异常。
     */
    public User authenticateUser(String username, String rawPassword) {
        User user = userMapper.findByUsername(username); // 根据用户名从数据库中查找用户。
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new RuntimeException("用户名或密码不正确"); // 如果用户不存在或密码不匹配，抛出异常。
        }
        return user; // 返回认证成功的用户实体。
    }
}