package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// 定义一个名为AsyncVideoProcessingService的类，服务将异步处理视频任务
@Service
public class AsyncVideoProcessingService {

    // 引入TaskProcessingService，用于处理具体的任务逻辑
    private final TaskProcessingService taskProcessingService;

    // 引入TaskMapper，用于数据库相关的操作，如查找和更新任务信息
    private final TaskMapper taskMapper;

    // 通过构造方法注入依赖
    @Autowired
    public AsyncVideoProcessingService(TaskProcessingService taskProcessingService, TaskMapper taskMapper) {
        this.taskProcessingService = taskProcessingService;
        this.taskMapper = taskMapper;
    }

    // 定义一个方法processTaskAsync，该方法将异步执行，并使用名为taskExecutor的线程池
    @Async("taskExecutor")
    public void processTaskAsync(Long taskId) {
        try {
            // 根据taskId查找任务信息
            Task task = taskMapper.findById(taskId);
            // 处理具体任务
            taskProcessingService.processTask(task);
        } catch (Exception e) {
            // 如果在处理过程中发生异常，则更新任务状态为FAILED，并记录错误信息
            Task failedTask = taskMapper.findById(taskId);
            failedTask.setStatus(TaskStatus.FAILED);
            failedTask.setErrorMessage(e.getMessage());
            taskMapper.updateTask(failedTask);
        }
    }
}
