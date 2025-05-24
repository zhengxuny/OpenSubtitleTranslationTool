package com.niit.subtitletranslationtool.mapper;

import com.niit.subtitletranslationtool.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    /**
     * 插入新用户（注册）
     * @param user 用户实体
     */
    void insertUser(User user);

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 对应的用户实体，如果不存在则返回 null
     */
    User findByUsername(@Param("username") String username);

    /**
     * 根据邮箱查询用户
     * @param email 邮箱
     * @return 对应的用户实体，如果不存在则返回 null
     */
    User findByEmail(@Param("email") String email);

    /**
     * 根据ID查询用户
     * @param id 用户ID
     * @return 对应的用户实体，如果不存在则返回 null
     */
    User findById(@Param("id") Long id);

    /**
     * 更新用户信息
     * @param user 用户实体
     */
    void updateUser(User user);
}