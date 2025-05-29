package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskMapper taskMapper;

    public List<Task> getTasksByUserId(Long userId) {
        return taskMapper.findByUserId(userId);
    }

    // 新增：按ID查询任务
    public Task getTaskById(Long taskId) {
        return taskMapper.findById(taskId);
    }
}
