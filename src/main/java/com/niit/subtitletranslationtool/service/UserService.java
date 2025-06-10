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
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

/**
 * 用户服务实现类，处理用户注册、认证、余额管理等业务逻辑。
 * 实现 Spring Security 的 UserDetailsService 接口，提供用户认证功能。
 */
@Service
public class UserService implements UserDetailsService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 构造函数，依赖注入用户数据访问对象和密码加密器。
     *
     * @param userMapper      用户数据访问接口
     * @param passwordEncoder 密码加密器
     */
    @Autowired
    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 注册新用户。
     *
     * @param request 用户注册请求对象
     * @return 注册成功的用户实体
     * @throws RuntimeException 用户名或邮箱已存在时抛出
     */
    @Transactional
    public User registerUser(RegisterRequest request) {
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new RuntimeException("用户名已存在");
        }
        if (userMapper.findByEmail(request.getEmail()) != null) {
            throw new RuntimeException("邮箱已存在");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .balance(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userMapper.insertUser(user);
        return user;
    }

    /**
     * 根据用户名加载用户信息，用于 Spring Security 认证流程。
     *
     * @param username 用户名
     * @return 用户认证信息对象
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("[认证流程] 开始查询用户，用户名: " + username);

        // 使用 Optional 来处理可能为 null 的结果，这是一种更现代的 Java 风格
        return Optional.ofNullable(userMapper.findByUsername(username))
                .map(user -> {
                    System.out.println("[认证流程] 用户查询成功，用户名: " + user.getUsername());
                    return new org.springframework.security.core.userdetails.User(
                            user.getUsername(),
                            user.getPassword(),
                            Collections.emptyList() // 或者用户实际的权限列表
                    );
                })
                .orElseThrow(() -> {
                    System.out.println("[认证流程] 用户未找到，用户名: " + username);
                    return new UsernameNotFoundException("User not found with username: " + username);
                });
    }


    /**
     * 用户余额充值。
     *
     * @param userId 用户ID
     * @param amount 充值金额
     * @return 更新后的用户实体
     * @throws RuntimeException 金额无效或用户不存在时抛出
     */
    @Transactional
    public User topUpUserBalance(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("充值金额必须大于0");
        }

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        user.setBalance(user.getBalance().add(amount));
        userMapper.updateUser(user);

        return user;
    }

    /**
     * 根据用户名获取用户信息。
     *
     * @param username 用户名
     * @return 用户实体
     * @throws RuntimeException 用户不存在时抛出
     */
    public User getUserByUsername(String username) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return user;
    }

    /**
     * 扣除用户余额。
     *
     * @param userId 用户ID
     * @param amount 扣除金额
     * @return 更新后的用户实体
     * @throws RuntimeException 金额无效、用户不存在或余额不足时抛出
     */
    @Transactional
    public User deductBalance(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("扣除金额必须大于0");
        }

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (user.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("余额不足，当前余额：" + user.getBalance());
        }

        user.setBalance(user.getBalance().subtract(amount));
        userMapper.updateUser(user);

        return user;
    }
}