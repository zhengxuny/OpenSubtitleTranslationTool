package com.niit.subtitletranslationtool.mapper;

import com.niit.subtitletranslationtool.entity.Task;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select; // 移除这个导入，如果不再使用其他@Select注解
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskMapper {

    // 使用XML配置插入语句，因为需要返回自增ID
    int insertTask(Task task);
    Task findTaskById(Long id);

    // 后续会添加更多方法，如 updateTaskStatus 等
    // int updateTaskStatus(Long id, TaskStatus status, String errorMessage);
}