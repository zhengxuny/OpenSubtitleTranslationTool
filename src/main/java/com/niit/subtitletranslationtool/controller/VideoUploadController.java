package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.dto.UploadResponse;
import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import com.niit.subtitletranslationtool.service.StorageService;
import com.niit.subtitletranslationtool.service.TaskProcessingService;
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
    private final TaskProcessingService taskProcessingService;
    private final Path uploadDir;

    public VideoUploadController(
            StorageService storageService,
            TaskMapper taskMapper,
            TaskProcessingService taskProcessingService,
            @Value("${file.upload-dir}") String uploadDir) {
        this.storageService = storageService;
        this.taskMapper = taskMapper;
        this.taskProcessingService = taskProcessingService;
        this.uploadDir = Paths.get(uploadDir).isAbsolute()
                ? Paths.get(uploadDir)
                : Paths.get(System.getProperty("user.dir"), uploadDir);
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> handleFileUpload(@RequestParam("file") MultipartFile file) {
        try {
            // 1. 生成唯一文件名（避免覆盖）
            String uniquePrefix = UUID.randomUUID().toString() + "_";
            String storedFilename = storageService.store(file, uniquePrefix);

            // 2. 创建任务实体
            Task task = Task.builder()
                    .originalVideoFilename(file.getOriginalFilename())
                    .storedVideoFilename(storedFilename)
                    .videoFilePath(uploadDir.resolve(storedFilename).toAbsolutePath().toString())
                    .status(TaskStatus.UPLOADED) // 初始状态：已上传
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // 3. 插入数据库
            taskMapper.insertTask(task);

            // 4. 触发任务处理（音轨提取）
            taskProcessingService.processTask(task);

            // 5. 返回上传结果
            return ResponseEntity.ok(new UploadResponse(
                    task.getId(),
                    "文件上传成功，开始处理",
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