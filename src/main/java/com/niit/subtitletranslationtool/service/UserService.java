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
import java.util.Optional;

/**
 * 用户服务实现类，负责处理用户相关的业务逻辑，例如注册、登录、余额管理等。
 * 实现了 Spring Security 的 UserDetailsService 接口，用于用户认证。
 */
@Service
public class UserService implements UserDetailsService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 构造函数，通过依赖注入获取 UserMapper 和 PasswordEncoder 的实例。
     *
     * @param userMapper      用于访问用户数据的 Mapper 接口
     * @param passwordEncoder 用于密码加密的 PasswordEncoder 接口
     */
    @Autowired
    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 注册新用户。
     *
     * @param request 包含用户注册信息的请求对象
     * @return 注册成功的用户对象
     * @throws RuntimeException 如果用户名或邮箱已存在，则抛出运行时异常
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

        // 构建新的用户对象
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword())) // 使用密码加密器加密密码
                .email(request.getEmail())
                .balance(BigDecimal.ZERO) // 初始余额为0
                .createdAt(LocalDateTime.now()) // 创建时间为当前时间
                .updatedAt(LocalDateTime.now()) // 更新时间为当前时间
                .build();

        // 将新用户插入数据库
        userMapper.insertUser(user);
        return user;
    }

    /**
     * 根据用户名加载用户详细信息，用于 Spring Security 的认证。
     *
     * @param username 用户名
     * @return 包含用户信息的 UserDetails 对象
     * @throws UsernameNotFoundException 如果找不到具有给定用户名的用户，则抛出此异常
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("[认证流程] 开始查询用户，用户名: " + username);

        // 使用 Optional 来处理可能为 null 的结果，这是一种更现代的 Java 风格
        return Optional.ofNullable(userMapper.findByUsername(username))
                .map(user -> {
                    System.out.println("[认证流程] 用户查询成功，用户名: " + user.getUsername());
                    // 创建 Spring Security 的 User 对象，用于认证
                    return new org.springframework.security.core.userdetails.User(
                            user.getUsername(),
                            user.getPassword(),
                            Collections.emptyList() // 用户权限列表，这里设置为空
                    );
                })
                .orElseThrow(() -> {
                    System.out.println("[认证流程] 用户未找到，用户名: " + username);
                    return new UsernameNotFoundException("User not found with username: " + username);
                });
    }

    /**
     * 为用户充值余额。
     *
     * @param userId 用户ID
     * @param amount 充值金额
     * @return 更新后的用户对象
     * @throws RuntimeException 如果充值金额无效或用户不存在，则抛出运行时异常
     */
    @Transactional
    public User topUpUserBalance(Long userId, BigDecimal amount) {
        // 检查充值金额是否有效
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("充值金额必须大于0");
        }

        // 根据用户ID查找用户
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 增加用户余额
        user.setBalance(user.getBalance().add(amount));
        // 更新用户信息
        userMapper.updateUser(user);

        return user;
    }

    /**
     * 根据用户名获取用户对象。
     *
     * @param username 用户名
     * @return 用户对象
     * @throws RuntimeException 如果用户不存在，则抛出运行时异常
     */
    public User getUserByUsername(String username) {
        // 根据用户名查找用户
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return user;
    }

    /**
     * 从用户余额中扣除指定金额。
     *
     * @param userId 用户ID
     * @param amount 扣除金额
     * @return 更新后的用户对象
     * @throws RuntimeException 如果扣除金额无效、用户不存在或余额不足，则抛出运行时异常
     */
    @Transactional
    public User deductBalance(Long userId, BigDecimal amount) {
        // 检查扣除金额是否有效
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("扣除金额必须大于0");
        }

        // 根据用户ID查找用户
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 检查余额是否充足
        if (user.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("余额不足，当前余额：" + user.getBalance());
        }

        // 扣除用户余额
        user.setBalance(user.getBalance().subtract(amount));
        // 更新用户信息
        userMapper.updateUser(user);

        return user;
    }
}