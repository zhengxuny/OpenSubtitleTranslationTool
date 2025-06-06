package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import com.niit.subtitletranslationtool.service.AsyncVideoProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务状态管理控制器，负责处理任务状态查询、异常任务续处理、任务取消及结果文件下载等API请求。
 */
@RestController
@RequestMapping("/api/task")
public class TaskStatusController {

    private final TaskMapper taskMapper;
    private final AsyncVideoProcessingService asyncVideoProcessingService;

    /**
     * 构造注入初始化控制器依赖
     *
     * @param taskMapper                   任务数据库操作映射器，用于任务数据持久化
     * @param asyncVideoProcessingService  异步视频处理服务，用于执行视频处理任务
     */
    @Autowired
    public TaskStatusController(
            TaskMapper taskMapper,
            AsyncVideoProcessingService asyncVideoProcessingService) {
        this.taskMapper = taskMapper;
        this.asyncVideoProcessingService = asyncVideoProcessingService;
    }

    /**
     * 查询指定任务的当前状态信息
     *
     * @param taskId 待查询的任务ID
     * @return 包含任务对象的响应实体（存在时）；或404未找到响应（不存在时）
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<Task> getTaskStatus(@PathVariable Long taskId) {
        // 根据任务ID查询数据库中的任务记录
        Task task = taskMapper.findById(taskId);
        // 存在则返回任务对象，不存在返回404响应
        return task != null ? ResponseEntity.ok(task) : ResponseEntity.notFound().build();
    }

    /**
     * 处理用户对损坏视频任务的继续处理请求
     *
     * @param taskId 待续处理的任务ID
     * @return 操作结果响应（成功时返回提示信息；任务不存在返回404；状态不符返回400）
     */
    @PostMapping("/continue/{taskId}")
    public ResponseEntity<String> continueDamagedTask(@PathVariable Long taskId) {
        // 根据任务ID查询数据库中的任务记录
        Task task = taskMapper.findById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        // 验证任务当前状态是否为等待用户处理的损坏状态
        if (task.getStatus() != TaskStatus.VIDEO_DAMAGED_AWAITING_USER_CHOICE) {
            return ResponseEntity.badRequest().body("当前状态不允许继续处理");
        }

        // 调用异步服务重新处理任务
        asyncVideoProcessingService.processTaskAsync(taskId);
        return ResponseEntity.ok("任务已重新启动处理");
    }

    /**
     * 处理用户主动取消任务的请求
     *
     * @param taskId 待取消的任务ID
     * @return 操作结果响应（成功时返回提示信息；任务不存在返回404）
     */
    @PostMapping("/cancel/{taskId}")
    public ResponseEntity<String> cancelTask(@PathVariable Long taskId) {
        // 根据任务ID查询数据库中的任务记录
        Task task = taskMapper.findById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        // 更新任务状态为取消，并记录用户主动取消的原因
        task.setStatus(TaskStatus.CANCELLED);
        task.setErrorMessage("用户主动取消任务");
        // 持久化更新后的任务状态
        taskMapper.updateTask(task);

        return ResponseEntity.ok("任务已取消");
    }

    /**
     * 下载翻译后的SRT字幕文件
     *
     * @param taskId 目标任务ID
     * @return 包含SRT文件的资源响应（存在时）；或404未找到响应（不存在时）
     */
    @GetMapping("/download/srt/translated/{taskId}")
    public ResponseEntity<Resource> downloadTranslatedSrt(@PathVariable Long taskId) {
        // 根据任务ID查询数据库中的任务记录并校验文件路径有效性
        Task task = taskMapper.findById(taskId);
        if (task == null || task.getTranslatedSrtFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        // 构造文件资源对象并设置下载响应头
        Resource resource = new FileSystemResource(task.getTranslatedSrtFilePath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + task.getTranslatedSrtFilename() + "\"")
                .body(resource);
    }

    /**
     * 下载添加字幕后的视频文件
     *
     * @param taskId 目标任务ID
     * @return 包含视频文件的资源响应（存在时）；或404未找到响应（不存在时）
     */
    @GetMapping("/download/video/subtitled/{taskId}")
    public ResponseEntity<Resource> downloadSubtitledVideo(@PathVariable Long taskId) {
        // 根据任务ID查询数据库中的任务记录并校验文件路径有效性
        Task task = taskMapper.findById(taskId);
        if (task == null || task.getSubtitledVideoFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        // 构造文件资源对象并设置下载响应头
        Resource resource = new FileSystemResource(task.getSubtitledVideoFilePath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + task.getSubtitledVideoFilename() + "\"")
                .body(resource);
    }
}