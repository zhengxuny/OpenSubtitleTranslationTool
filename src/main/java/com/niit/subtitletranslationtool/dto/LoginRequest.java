package com.niit.subtitletranslationtool.dto;

// 导入Lombok库中的@Data注解，该注解会自动生成getter、setter、toString、equals和hashCode方法
import lombok.Data;
// 导入Lombok库中的@NoArgsConstructor注解，该注解会自动生成一个无参数的构造方法
import lombok.NoArgsConstructor;
// 导入Lombok库中的@AllArgsConstructor注解，该注解会自动生成一个包含所有字段的构造方法
import lombok.AllArgsConstructor;

// 使用@Data注解自动生成getter、setter、toString、equals和hashCode方法
// 使用@NoArgsConstructor注解自动生成一个无参数的构造方法
// 使用@AllArgsConstructor注解自动生成一个包含所有字段的构造方法
@Data
@NoArgsConstructor
@AllArgsConstructor
// 定义一个名为LoginRequest的公共类，用于表示用户登录请求的数据传输对象（DTO）
public class LoginRequest {
    // 定义一个私有的String类型的变量username，用于存储用户名
    private String username;
    // 定义一个私有的String类型的变量password，用于存储密码
    private String password;
}