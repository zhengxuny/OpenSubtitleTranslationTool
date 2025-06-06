package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 异步视频处理服务，负责通过异步方式调度并执行视频相关的任务处理逻辑。
 */
@Service
public class AsyncVideoProcessingService {

    /**
     * 任务处理服务，封装了具体任务的处理业务逻辑。
     */
    private final TaskProcessingService taskProcessingService;

    /**
     * 任务数据访问接口，用于查询和更新任务数据。
     */
    private final TaskMapper taskMapper;

    /**
     * 通过构造函数注入任务处理服务和任务数据访问接口。
     *
     * @param taskProcessingService 具体的任务处理服务
     * @param taskMapper            任务数据库访问接口
     */
    @Autowired
    public AsyncVideoProcessingService(TaskProcessingService taskProcessingService, TaskMapper taskMapper) {
        this.taskProcessingService = taskProcessingService;
        this.taskMapper = taskMapper;
    }

    /**
     * 异步处理指定任务ID对应的视频处理任务。
     * <p>
     * 使用指定的线程池异步执行任务处理，保证调用方非阻塞。
     * 当任务处理异常时，更新任务状态为失败并记录错误信息。
     *
     * @param taskId 需要处理的任务ID，非空
     */
    @Async("taskExecutor")
    public void processTaskAsync(Long taskId) {
        try {
            // 根据任务ID查询任务信息
            Task task = taskMapper.findById(taskId);

            // 调用业务服务处理具体任务
            taskProcessingService.processTask(task);
        } catch (Exception e) {
            // 处理异常时，标记任务为失败并保存异常信息
            Task failedTask = taskMapper.findById(taskId);
            failedTask.setStatus(TaskStatus.FAILED);
            failedTask.setErrorMessage(e.getMessage());

            // 更新任务状态到数据库
            taskMapper.updateTask(failedTask);
        }
    }
}
