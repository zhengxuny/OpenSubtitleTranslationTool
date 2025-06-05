package com.niit.subtitletranslationtool.mapper;

import com.niit.subtitletranslationtool.entity.Admin;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminMapper {
    /**
     * 根据用户名查询管理员（用于登录验证）
     * @param username 用户名
     * @return Admin实体（若不存在返回null）
     */
    Admin selectByUsername(String username);
}