package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务管理服务类。
 *
 * <p>此类负责处理与任务相关的业务逻辑，例如获取任务列表和任务详情。
 * 它通过与 TaskMapper 接口交互，实现对数据库中任务数据的访问和操作。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskMapper taskMapper;

    /**
     * 根据用户ID获取该用户的所有任务列表。
     *
     * @param userId 用户的唯一标识符。用于在数据库中查找与该用户关联的任务。
     * @return 包含用户所有任务的列表。如果用户没有任何任务，则返回一个空列表。
     */
    public List<Task> getTasksByUserId(Long userId) {
        // 调用 TaskMapper 的 findByUserId 方法，根据用户ID查询任务列表
        return taskMapper.findByUserId(userId);
    }

    /**
     * 根据任务ID获取任务的详细信息。
     *
     * @param taskId 任务的唯一标识符。用于在数据库中查找特定的任务。
     * @return 如果找到具有给定ID的任务，则返回该任务对象；否则，返回 null。
     */
    public Task getTaskById(Long taskId) {
        // 调用 TaskMapper 的 findById 方法，根据任务ID查询任务详情
        return taskMapper.findById(taskId);
    }
}