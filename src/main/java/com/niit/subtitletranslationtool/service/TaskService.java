package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务管理服务类，提供与任务数据相关的查询服务。
 * 负责通过任务映射器（TaskMapper）与持久层交互，获取用户任务列表及单个任务详情。
 */
@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskMapper taskMapper;

    /**
     * 根据用户ID获取对应的任务列表。
     *
     * @param userId 用户ID，用于查询关联的任务数据，必须为非空有效用户标识。
     * @return 该用户关联的所有任务对象列表，可能为空列表（无任务时）。
     */
    public List<Task> getTasksByUserId(Long userId) {
        return taskMapper.findByUserId(userId);
    }

    /**
     * 根据任务ID获取具体的任务详情。
     *
     * @param taskId 任务ID，用于唯一标识需要查询的任务。
     * @return 对应ID的任务对象；若不存在则返回null。
     */
    public Task getTaskById(Long taskId) {
        return taskMapper.findById(taskId);
    }
}