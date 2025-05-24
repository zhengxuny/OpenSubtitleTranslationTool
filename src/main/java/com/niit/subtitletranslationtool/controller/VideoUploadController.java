package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.dto.UploadResponse;
import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import com.niit.subtitletranslationtool.service.AsyncVideoProcessingService;
import com.niit.subtitletranslationtool.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 视频上传控制器类，管理与视频上传和处理相关的请求。
 */
@RestController
@RequestMapping("/api/video")
public class VideoUploadController {

    // 依赖注入存储服务，用于存储文件到服务器本地或云存储
    private final StorageService storageService;

    // 依赖注入任务映射器，用于操作数据库中的任务实体
    private final TaskMapper taskMapper;

    // 依赖注入异步视频处理服务，用于处理视频上传后的所有后台任务
    private final AsyncVideoProcessingService asyncVideoProcessingService;

    // 通过配置文件读取文件上传目录，并将其转换为绝对路径使用
    private final Path uploadDir;

    /**
     * 构造函数，注入必要服务实例和上传目录配置。
     *
     * @param storageService 存储服务实例，用于处理文件存储
     * @param taskMapper 任务映射器实例，用于操作数据库任务
     * @param asyncVideoProcessingService 异步视频处理服务实例，用于处理后台视频任务
     * @param uploadDir 配置文件中定义的文件上传目录
     */
    public VideoUploadController(
            StorageService storageService,
            TaskMapper taskMapper,
            AsyncVideoProcessingService asyncVideoProcessingService,
            @Value("${file.upload-dir}") String uploadDir) {
        this.storageService = storageService;
        this.taskMapper = taskMapper;
        this.asyncVideoProcessingService = asyncVideoProcessingService;
        this.uploadDir = Paths.get(uploadDir).isAbsolute()
                ? Paths.get(uploadDir)
                : Paths.get(System.getProperty("user.dir"), uploadDir);
    }

    /**
     * 处理视频文件上传并启动异步处理任务。
     *
     * @param file 上传的视频文件对象
     * @param burnSubtitles 是否需要将字幕烧录到视频中的标志位
     * @return 上传响应实体对象，包括任务ID、处理消息、原始文件名和存储文件名
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> handleFileUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "false") boolean burnSubtitles) {
        try {
            // 生成唯一文件名，避免文件覆盖问题
            String uniquePrefix = UUID.randomUUID().toString() + "_";
            String storedFilename = storageService.store(file, uniquePrefix);

            // 创建任务实体并设置其属性
            Task task = Task.builder()
                    .originalVideoFilename(file.getOriginalFilename())  // 设置文件原始名称
                    .storedVideoFilename(storedFilename)              // 设置文件存储名称
                    .videoFilePath(uploadDir.resolve(storedFilename).toAbsolutePath().toString())  // 设置视频文件路径
                    .status(TaskStatus.UPLOADED)                      // 设置任务状态为已上传
                    .createdAt(LocalDateTime.now())                   // 设置任务创建时间
                    .updatedAt(LocalDateTime.now())                   // 设置任务最近一次更新时间
                    .build();

            // 将用户的选择（是否烧录字幕）保存到任务实体中
            task.setBurnSubtitles(burnSubtitles);

            // 将任务实体插入数据库，并获取自动生成的任务ID
            taskMapper.insertTask(task);

            // 启动异步处理流程
            asyncVideoProcessingService.processTaskAsync(task.getId());

            // 立即向客户端返回上传成功响应以及任务信息，不等待处理结果
            return ResponseEntity.ok(new UploadResponse(
                    task.getId(),
                    "文件上传成功，任务已启动（异步处理中）",
                    file.getOriginalFilename(),
                    storedFilename
            ));

        // 捕获所有可能的异常情况，并向客户端返回500状态码和具体的错误信息
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new UploadResponse(
                    null,
                    "文件上传失败：" + e.getMessage(),
                    file.getOriginalFilename(),
                    null
            ));
        }
    }
}