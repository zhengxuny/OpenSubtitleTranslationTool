package com.niit.subtitletranslationtool.mapper;

import com.niit.subtitletranslationtool.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 定义UserMapper接口，用于与数据库中用户表进行交互
@Mapper
public interface UserMapper {

    // 插入新用户的方法，用于用户注册，传入一个用户对象，保存到用户表中
    void insertUser(User user);

    // 根据用户名查找用户的方法，输入要查询的用户名，返回一个用户对象，如果用户名不存在，则返回null
    User findByUsername(@Param("username") String username);

    // 根据用户邮箱查找用户的方法，输入要查询的邮箱地址，返回一个用户对象，如果邮箱地址不存在，则返回null
    User findByEmail(@Param("email") String email);

    // 根据用户ID查找用户的方法，输入要查询的用户ID，返回一个用户对象，如果用户ID不存在，则返回null
    User findById(@Param("id") Long id);

    // 更新用户信息的方法，传入一个用户对象，根据其ID更新用户表中的相应记录
    void updateUser(User user);

    // 查询所有用户
    List<User> findAllUsers();

    // 按ID删除用户
    void deleteUser(@Param("id") Long id);
}