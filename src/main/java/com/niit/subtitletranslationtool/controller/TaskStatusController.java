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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/task")
public class TaskStatusController {

    private final TaskMapper taskMapper;
    private final AsyncVideoProcessingService asyncVideoProcessingService; // 新增依赖

    // 新增构造函数注入
    @Autowired
    public TaskStatusController(
            TaskMapper taskMapper,
            AsyncVideoProcessingService asyncVideoProcessingService) {
        this.taskMapper = taskMapper;
        this.asyncVideoProcessingService = asyncVideoProcessingService;
    }

    // 状态查询接口
    @GetMapping("/status/{taskId}")
    public ResponseEntity<Task> getTaskStatus(@PathVariable Long taskId) {
        Task task = taskMapper.findById(taskId);
        return task != null ? ResponseEntity.ok(task) : ResponseEntity.notFound().build();
    }

    /**
     * 用户选择继续处理损坏视频
     */
    @PostMapping("/continue/{taskId}")
    public ResponseEntity<String> continueDamagedTask(@PathVariable Long taskId) {
        Task task = taskMapper.findById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        if (task.getStatus() != TaskStatus.VIDEO_DAMAGED_AWAITING_USER_CHOICE) {
            return ResponseEntity.badRequest().body("当前状态不允许继续处理");
        }
        // 重新触发异步处理（跳过完整性检测，或根据需求调整）
        asyncVideoProcessingService.processTaskAsync(taskId);
        return ResponseEntity.ok("任务已重新启动处理");
    }

    /**
     * 用户选择取消任务
     */
    @PostMapping("/cancel/{taskId}")
    public ResponseEntity<String> cancelTask(@PathVariable Long taskId) {
        Task task = taskMapper.findById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        task.setStatus(TaskStatus.CANCELLED);
        task.setErrorMessage("用户主动取消任务");
        taskMapper.updateTask(task);
        return ResponseEntity.ok("任务已取消");
    }

    // 翻译后SRT下载接口
    @GetMapping("/download/srt/translated/{taskId}")
    public ResponseEntity<Resource> downloadTranslatedSrt(@PathVariable Long taskId) {
        Task task = taskMapper.findById(taskId);
        if (task == null || task.getTranslatedSrtFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(task.getTranslatedSrtFilePath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + task.getTranslatedSrtFilename() + "\"")
                .body(resource);
    }

    // src/main/java/com/niit/subtitletranslationtool/controller/TaskStatusController.java
    @GetMapping("/download/video/subtitled/{taskId}")
    public ResponseEntity<Resource> downloadSubtitledVideo(@PathVariable Long taskId) {
        Task task = taskMapper.findById(taskId);
        if (task == null || task.getSubtitledVideoFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(task.getSubtitledVideoFilePath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + task.getSubtitledVideoFilename() + "\"")
                .body(resource);
    }
}
