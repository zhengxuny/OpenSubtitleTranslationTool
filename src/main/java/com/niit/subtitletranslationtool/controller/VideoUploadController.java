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

@RestController
@RequestMapping("/api/video")
public class VideoUploadController {

    private final StorageService storageService;
    private final TaskMapper taskMapper;
    private final AsyncVideoProcessingService asyncVideoProcessingService; // 新增异步处理服务依赖
    private final Path uploadDir;

    // 构造函数新增 AsyncVideoProcessingService 注入
    public VideoUploadController(
            StorageService storageService,
            TaskMapper taskMapper,
            AsyncVideoProcessingService asyncVideoProcessingService, // 新增参数
            @Value("${file.upload-dir}") String uploadDir) {
        this.storageService = storageService;
        this.taskMapper = taskMapper;
        this.asyncVideoProcessingService = asyncVideoProcessingService;
        this.uploadDir = Paths.get(uploadDir).isAbsolute()
                ? Paths.get(uploadDir)
                : Paths.get(System.getProperty("user.dir"), uploadDir);
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> handleFileUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "false") boolean burnSubtitles) {  // 新增参数
        try {
            // 1. 生成唯一文件名（避免覆盖）
            String uniquePrefix = UUID.randomUUID().toString() + "_";
            String storedFilename = storageService.store(file, uniquePrefix);

            // 2. 创建任务实体（初始状态为已上传）
            Task task = Task.builder()
                    .originalVideoFilename(file.getOriginalFilename())
                    .storedVideoFilename(storedFilename)
                    .videoFilePath(uploadDir.resolve(storedFilename).toAbsolutePath().toString())
                    .status(TaskStatus.UPLOADED)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // 保存用户压制选择到任务
            task.setBurnSubtitles(burnSubtitles);  // 需在Task实体添加该字段


            // 3. 插入数据库（自增ID生效）
            taskMapper.insertTask(task);

            // 4. 关键修改：触发异步处理（不再同步调用processTask）
            asyncVideoProcessingService.processTaskAsync(task.getId());

            // 5. 立即返回任务ID给前端
            return ResponseEntity.ok(new UploadResponse(
                    task.getId(),
                    "文件上传成功，任务已启动（异步处理中）",
                    file.getOriginalFilename(),
                    storedFilename
            ));

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