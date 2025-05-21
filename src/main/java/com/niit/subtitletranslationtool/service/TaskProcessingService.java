// src/main/java/com/niit/subtitletranslationtool/service/TaskProcessingService.java
package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import com.niit.subtitletranslationtool.service.FFmpegService;
import com.niit.subtitletranslationtool.service.TranslationService;
import com.niit.subtitletranslationtool.service.WhisperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TaskProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(TaskProcessingService.class);

    private final FFmpegService ffmpegService;
    private final TaskMapper taskMapper;
    private final WhisperService whisperService;
    private final TranslationService translationService;
    private final Path tempAudioDir;
    // 新增：通过@Value注入压制视频存储目录配置
    @Value("${file.subtitled-video-dir}")
    private String subtitledVideoDir;

    @Autowired
    public TaskProcessingService(
            FFmpegService ffmpegService,
            TaskMapper taskMapper,
            WhisperService whisperService,
            TranslationService translationService,
            @Value("${temp.audio-dir}") String tempAudioDir) {
        this.ffmpegService = ffmpegService;
        this.taskMapper = taskMapper;
        this.whisperService = whisperService;
        this.translationService = translationService;
        this.tempAudioDir = Paths.get(tempAudioDir).isAbsolute()
                ? Paths.get(tempAudioDir)
                : Paths.get(System.getProperty("user.dir"), tempAudioDir);
        initDirectory(this.tempAudioDir);
    }

    private void initDirectory(Path dir) {
        if (!dir.toFile().exists()) {
            dir.toFile().mkdirs();
            logger.info("初始化临时音频目录：{}", dir.toAbsolutePath());
        }
    }

    public void processTask(Task task) {
        logger.info("开始处理任务：{}", task.getId());
        try {
            // ...原有前置处理逻辑...

            // 翻译完成后检查是否需要压制
            if (task.getStatus() == TaskStatus.TRANSLATED && task.isBurnSubtitles()) {
                task.setStatus(TaskStatus.SUBTITLE_BURNING);
                taskMapper.updateTask(task);

                // 修正：使用@Value注入的目录路径
                Path outputDir = Paths.get(subtitledVideoDir);
                String outputFilename = "subtitled_" + task.getOriginalVideoFilename();
                String outputPath = outputDir.resolve(outputFilename).toString();

                // 执行压制
                boolean success = ffmpegService.burnSubtitles(
                        task.getVideoFilePath(),
                        task.getTranslatedSrtFilePath(),
                        outputPath
                );

                // 更新任务状态
                if (success) {
                    task.setSubtitledVideoFilename(outputFilename);
                    task.setSubtitledVideoFilePath(outputPath);
                    task.setStatus(TaskStatus.COMPLETED);
                } else {
                    task.setStatus(TaskStatus.FAILED);
                    task.setErrorMessage("字幕压制失败");
                }
                taskMapper.updateTask(task);
            }

        } catch (Exception e) {
            // 根据当前阶段判断错误状态
            TaskStatus errorStatus;
            if (task.getStatus() == TaskStatus.AUDIO_EXTRACTING) {
                errorStatus = TaskStatus.EXTRACTION_FAILED;
            } else if (task.getStatus() == TaskStatus.TRANSCRIBING) {
                errorStatus = TaskStatus.TRANSCRIPTION_FAILED;
            } else if (task.getStatus() == TaskStatus.TRANSLATING) { // 新增翻译阶段错误处理
                errorStatus = TaskStatus.TRANSLATION_FAILED;
            } else {
                errorStatus = TaskStatus.FAILED;
            }

            task.setStatus(errorStatus);
            task.setErrorMessage("任务处理异常：" + e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);
            logger.error("任务{}处理过程中发生异常：{}", task.getId(), e.getMessage(), e);
        }
    }
}