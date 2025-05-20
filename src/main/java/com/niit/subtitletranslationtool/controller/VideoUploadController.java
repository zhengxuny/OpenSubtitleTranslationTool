package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.dto.UploadResponse;
import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import com.niit.subtitletranslationtool.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    private final Path rootLocation;

    @Autowired
    public VideoUploadController(StorageService storageService, TaskMapper taskMapper, @Value("${file.upload-dir}") String uploadDir) {
        this.storageService = storageService;
        this.taskMapper = taskMapper;
        // 与StorageService中逻辑保持一致
        if (Paths.get(uploadDir).isAbsolute()) {
            this.rootLocation = Paths.get(uploadDir);
        } else {
            this.rootLocation = Paths.get(System.getProperty("user.dir"), uploadDir);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> handleFileUpload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new UploadResponse(null, "File is empty", null, null));
        }

        try {
            // 使用UUID作为任务的唯一标识前缀，确保文件名在存储时更独特，也便于后续通过任务ID关联
            String taskPrefix = UUID.randomUUID().toString().substring(0, 8); // 取UUID一部分作为前缀
            String storedFilename = storageService.store(file, taskPrefix);
            Path filePath = rootLocation.resolve(storedFilename);


            Task task = Task.builder()
                    .originalVideoFilename(file.getOriginalFilename())
                    .storedVideoFilename(storedFilename)
                    .videoFilePath(filePath.toAbsolutePath().toString()) // 存储绝对路径
                    .status(TaskStatus.UPLOADED) // 文件上传成功，状态为UPLOADED
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            taskMapper.insertTask(task); // 插入数据库并获取自增ID

            // 异步处理的触发点可以在这里，但当前阶段先完成同步的上传和记录创建
            // asyncVideoProcessingService.processVideo(task.getId());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new UploadResponse(task.getId(),
                            "File uploaded successfully: " + file.getOriginalFilename(),
                            file.getOriginalFilename(),
                            storedFilename));

        } catch (Exception e) {
            // Log the exception e
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UploadResponse(null,
                            "Failed to upload file: " + e.getMessage(),
                            file.getOriginalFilename(),
                            null));
        }
    }
}