package com.niit.subtitletranslationtool.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.niit.subtitletranslationtool.entity.User;

/**
 * 用户表MyBatis映射接口，负责用户数据的持久化操作，
 * 包含用户信息的增、删、改、查及统计等数据库交互方法。
 */
@Mapper
public interface UserMapper {

    /**
     * 插入新用户记录（用于用户注册场景）。
     *
     * @param user 待插入的用户对象（需包含完整用户信息）
     */
    void insertUser(User user);

    /**
     * 根据用户名查询用户信息。
     *
     * @param username 待查询的用户名（非空）
     * @return 匹配的用户对象；若不存在则返回null
     */
    User findByUsername(@Param("username") String username);

    /**
     * 根据邮箱地址查询用户信息。
     *
     * @param email 待查询的邮箱地址（非空）
     * @return 匹配的用户对象；若不存在则返回null
     */
    User findByEmail(@Param("email") String email);

    /**
     * 根据用户ID查询用户信息。
     *
     * @param id 待查询的用户ID（非负）
     * @return 匹配的用户对象；若不存在则返回null
     */
    User findById(@Param("id") Long id);

    /**
     * 更新用户信息（根据用户ID更新对应记录）。
     *
     * @param user 包含新信息的用户对象（需包含有效ID）
     */
    void updateUser(User user);

    /**
     * 查询系统中所有用户信息。
     *
     * @return 所有用户对象的列表（可能为空列表）
     */
    List<User> findAllUsers();

    /**
     * 根据用户ID删除指定用户记录。
     *
     * @param id 待删除的用户ID（非负）
     */
    void deleteUser(@Param("id") Long id);

    /**
     * 统计系统中注册的总用户数量。
     *
     * @return 系统总用户数（非负整数）
     */
    int countAllUsers();

    /**
     * 查询最近注册的用户（按创建时间倒序排序）。
     *
     * @param limit 需要获取的最近记录数量（正整数）
     * @return 按创建时间倒序排列的前limit条用户记录列表
     */
    List<User> findRecentUsers(int limit);

    /**
     * 统计每日新增用户数量（过去N天）。
     *
     * @param days 需要获取的天数
     * @return 以日期为键，用户数量为值的Map
     */
    @MapKey("date")  // 指定使用结果中的date字段作为Map的键
    List<Map<String, Object>> countDailyNewUsers(@Param("days") int days);
}