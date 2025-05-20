package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncVideoProcessingService {

    private final TaskProcessingService taskProcessingService;
    private final TaskMapper taskMapper;

    @Autowired
    public AsyncVideoProcessingService(TaskProcessingService taskProcessingService, TaskMapper taskMapper) {
        this.taskProcessingService = taskProcessingService;
        this.taskMapper = taskMapper;
    }

    @Async("taskExecutor") // 使用异步线程池（需在配置中定义）
    public void processTaskAsync(Long taskId) {
        try {
            Task task = taskMapper.findById(taskId);
            taskProcessingService.processTask(task); // 调用原有处理流程
        } catch (Exception e) {
            // 异常处理：更新任务状态为FAILED并记录错误信息
            Task failedTask = taskMapper.findById(taskId);
            failedTask.setStatus(TaskStatus.FAILED);
            failedTask.setErrorMessage(e.getMessage());
            taskMapper.updateTask(failedTask);
        }
    }
}
