package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 异步视频处理服务，用于异步执行视频处理任务，例如字幕翻译等。
 * 避免阻塞主线程，提高系统响应性。
 */
@Service
public class AsyncVideoProcessingService {

    /**
     * TaskProcessingService 实例，用于处理具体的任务逻辑。
     */
    private final TaskProcessingService taskProcessingService;

    /**
     * TaskMapper 实例，用于访问和操作数据库中的任务数据。
     */
    private final TaskMapper taskMapper;

    /**
     * 构造函数，通过依赖注入获取 TaskProcessingService 和 TaskMapper 的实例。
     *
     * @param taskProcessingService 任务处理服务，负责执行具体的任务逻辑。
     * @param taskMapper            任务数据访问接口，用于查询和更新任务数据。
     */
    @Autowired
    public AsyncVideoProcessingService(TaskProcessingService taskProcessingService, TaskMapper taskMapper) {
        this.taskProcessingService = taskProcessingService;
        this.taskMapper = taskMapper;
    }

    /**
     * 异步处理指定的任务。
     * 使用 Spring 的 @Async 注解，使该方法在独立的线程中执行。
     *
     * @param taskId 要处理的任务的ID。
     *               通过此ID从数据库中检索任务信息。
     */
    @Async("taskExecutor") // 使用名为 "taskExecutor" 的线程池来执行此方法
    public void processTaskAsync(Long taskId) {
        try {
            // 1. 根据 taskId 从数据库中查询 Task 对象
            Task task = taskMapper.findById(taskId);

            // 2. 调用 taskProcessingService 的 processTask 方法来处理任务
            //    这里是实际执行视频处理/字幕翻译等操作的地方
            taskProcessingService.processTask(task);

        } catch (Exception e) {
            // 3. 如果在处理过程中发生任何异常，捕获异常
            //    更新任务状态为失败，并记录错误信息

            // 3.1 重新从数据库中获取 Task 对象，确保是最新的状态
            Task failedTask = taskMapper.findById(taskId);

            // 3.2 设置任务状态为失败
            failedTask.setStatus(TaskStatus.FAILED);

            // 3.3 设置错误信息，记录异常的详细信息，方便排查问题
            failedTask.setErrorMessage(e.getMessage());

            // 4. 将更新后的 Task 对象保存回数据库
            taskMapper.updateTask(failedTask);
        }
    }
}