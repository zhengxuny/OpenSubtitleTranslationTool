package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.dto.UploadResponse;
import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.entity.User;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import com.niit.subtitletranslationtool.service.AsyncVideoProcessingService;
import com.niit.subtitletranslationtool.service.StorageService;
import com.niit.subtitletranslationtool.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 视频上传控制器，处理视频文件上传请求并协调后续异步处理流程。
 * 提供视频文件存储、用户余额校验、任务创建及异步处理启动等核心功能。
 */
@RestController
@RequestMapping("/api/video")
public class VideoUploadController {

    private final StorageService storageService;
    private final TaskMapper taskMapper;
    private final AsyncVideoProcessingService asyncVideoProcessingService;
    private final Path uploadDir;
    @Autowired
    private UserService userService;

    /**
     * 构造函数，初始化依赖组件及上传目录路径。
     * 自动处理上传目录的绝对路径转换（若配置为相对路径则基于项目根目录解析）。
     *
     * @param storageService            用于文件存储操作的服务组件
     * @param taskMapper                数据库任务表操作组件
     * @param asyncVideoProcessingService 视频异步处理服务组件
     * @param uploadDir                 配置文件中指定的上传目录路径（字符串形式）
     */
    public VideoUploadController(
            StorageService storageService,
            TaskMapper taskMapper,
            AsyncVideoProcessingService asyncVideoProcessingService,
            @Value("${file.upload-dir}") String uploadDir) {
        this.storageService = storageService;
        this.taskMapper = taskMapper;
        this.asyncVideoProcessingService = asyncVideoProcessingService;
        // 确定上传目录的路径
        // 首先检查配置的 uploadDir 路径是否为绝对路径
        this.uploadDir = Paths.get(uploadDir).isAbsolute()
                // 如果 uploadDir 已经是绝对路径，则直接使用该路径
                ? Paths.get(uploadDir)
                // 否则，将 uploadDir 视为相对路径，并将其解析为相对于用户当前工作目录的绝对路径
                : Paths.get(System.getProperty("user.dir"), uploadDir);
    }

    /**
     * 处理视频文件上传请求，执行用户余额校验、文件存储、任务创建及异步处理启动。
     *
     * @param file            客户端上传的视频文件对象
     * @param burnSubtitles 是否将字幕烧录到视频中的标志位（可选，默认false）
     * @return 包含任务ID、处理状态的上传响应实体
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> handleFileUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "false") boolean burnSubtitles) {
        try {
            // 从安全上下文获取当前认证用户信息
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);

            // 校验用户余额是否满足最低要求（≥10元）
            if (user.getBalance().compareTo(BigDecimal.TEN) < 0) {
                return ResponseEntity.badRequest().body(
                        new UploadResponse(null, "余额不足（需≥10元）", file.getOriginalFilename(), null)
                );
            }

            // 生成唯一文件前缀避免同名覆盖，执行文件存储操作
            String uniquePrefix = UUID.randomUUID() + "_";
            String storedFilename = storageService.store(file, uniquePrefix);

            // 初始化任务实体并设置基础属性
            Task task = Task.builder()
                    .originalVideoFilename(file.getOriginalFilename())
                    .storedVideoFilename(storedFilename)
                    .videoFilePath(uploadDir.resolve(storedFilename).toAbsolutePath().toString())
                    .status(TaskStatus.UPLOADED)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .userId(user.getId())
                    .build();

            // 记录用户选择的字幕烧录配置
            task.setBurnSubtitles(burnSubtitles);

            // 插入新任务到数据库并获取自增ID
            taskMapper.insertTask(task);

            // 触发异步视频处理流程（不阻塞当前请求）
            asyncVideoProcessingService.processTaskAsync(task.getId());

            // 返回包含任务信息的成功响应
            return ResponseEntity.ok(new UploadResponse(
                    task.getId(),
                    "文件上传成功，任务已启动（异步处理中）",
                    file.getOriginalFilename(),
                    storedFilename
            ));
        } catch (Exception e) {
            // 异常处理：返回包含错误信息的500状态响应
            return ResponseEntity.internalServerError().body(new UploadResponse(
                    null,
                    "文件上传失败：" + e.getMessage(),
                    file.getOriginalFilename(),
                    null
            ));
        }
    }
}