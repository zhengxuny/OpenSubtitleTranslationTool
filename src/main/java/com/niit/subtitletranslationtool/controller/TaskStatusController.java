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

// 定义一个控制器类，用于处理与任务相关的API请求
@RestController
@RequestMapping("/api/task")
public class TaskStatusController {

    // 任务映射器，用于与数据库进行交互
    private final TaskMapper taskMapper;
    // 异步视频处理服务，用于执行视频处理任务
    private final AsyncVideoProcessingService asyncVideoProcessingService;

    // 构造函数，通过构造注入初始化依赖
    @Autowired
    public TaskStatusController(
            TaskMapper taskMapper,
            AsyncVideoProcessingService asyncVideoProcessingService) {
        this.taskMapper = taskMapper;
        this.asyncVideoProcessingService = asyncVideoProcessingService;
    }

    // 获取任务状态的GET请求接口
    @GetMapping("/status/{taskId}")
    public ResponseEntity<Task> getTaskStatus(@PathVariable Long taskId) {
        // 根据taskId查找任务
        Task task = taskMapper.findById(taskId);
        // 如果找到任务，则返回任务对象；否则，返回404 Not Found
        return task != null ? ResponseEntity.ok(task) : ResponseEntity.notFound().build();
    }

    // 处理用户选择继续处理损坏视频的POST请求接口
    @PostMapping("/continue/{taskId}")
    public ResponseEntity<String> continueDamagedTask(@PathVariable Long taskId) {
        // 根据taskId查找任务
        Task task = taskMapper.findById(taskId);
        // 如果找不到任务，则返回404 Not Found
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        // 如果任务状态不是等待用户选择的损坏状态，则返回400 Bad Request
        if (task.getStatus() != TaskStatus.VIDEO_DAMAGED_AWAITING_USER_CHOICE) {
            return ResponseEntity.badRequest().body("当前状态不允许继续处理");
        }
        // 重新触发异步处理
        asyncVideoProcessingService.processTaskAsync(taskId);
        // 返回成功信息
        return ResponseEntity.ok("任务已重新启动处理");
    }

    // 处理用户选择取消任务的POST请求接口
    @PostMapping("/cancel/{taskId}")
    public ResponseEntity<String> cancelTask(@PathVariable Long taskId) {
        // 根据taskId查找任务
        Task task = taskMapper.findById(taskId);
        // 如果找不到任务，则返回404 Not Found
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        // 设置任务状态为取消，并设置错误信息为用户主动取消
        task.setStatus(TaskStatus.CANCELLED);
        task.setErrorMessage("用户主动取消任务");
        // 更新数据库中的任务记录
        taskMapper.updateTask(task);
        // 返回成功信息
        return ResponseEntity.ok("任务已取消");
    }

    // 获取翻译后的SRT文件进行下载的GET请求接口
    @GetMapping("/download/srt/translated/{taskId}")
    public ResponseEntity<Resource> downloadTranslatedSrt(@PathVariable Long taskId) {
        // 根据taskId查找任务
        Task task = taskMapper.findById(taskId);
        // 如果找不到任务或翻译后的SRT文件路径为空，则返回404 Not Found
        if (task == null || task.getTranslatedSrtFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        // 创建一个文件系统资源对象，表示翻译后的SRT文件
        Resource resource = new FileSystemResource(task.getTranslatedSrtFilePath());
        // 返回HTTP响应实体，包含文件内容和一些HTTP头部信息如Content-Disposition
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + task.getTranslatedSrtFilename() + "\"")
                .body(resource);
    }

    // 获取添加字幕后的视频文件进行下载的GET请求接口
    @GetMapping("/download/video/subtitled/{taskId}")
    public ResponseEntity<Resource> downloadSubtitledVideo(@PathVariable Long taskId) {
        // 根据taskId查找任务
        Task task = taskMapper.findById(taskId);
        // 如果找不到任务或添加字幕后的视频文件路径为空，则返回404 Not Found
        if (task == null || task.getSubtitledVideoFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        // 创建一个文件系统资源对象，表示添加字幕后的视频文件
        Resource resource = new FileSystemResource(task.getSubtitledVideoFilePath());
        // 返回HTTP响应实体，包含文件内容和一些HTTP头部信息如Content-Disposition
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + task.getSubtitledVideoFilename() + "\"")
                .body(resource);
    }
}
