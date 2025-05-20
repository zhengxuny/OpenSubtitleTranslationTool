package com.niit.subtitletranslationtool.mapper;

import com.niit.subtitletranslationtool.entity.Task;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMapper {

    void insertTask(Task task);

    // 根据ID查询任务（后续阶段会用到）
    Task findById(Long id);

    // 更新任务信息和状态
    void updateTask(Task task);
}