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
    private final TranslationService translationService; // 新增翻译服务依赖
    private final Path tempAudioDir;

    @Autowired
    public TaskProcessingService(
            FFmpegService ffmpegService,
            TaskMapper taskMapper,
            WhisperService whisperService,
            TranslationService translationService, // 新增构造参数
            @Value("${temp.audio-dir}") String tempAudioDir) {
        this.ffmpegService = ffmpegService;
        this.taskMapper = taskMapper;
        this.whisperService = whisperService;
        this.translationService = translationService; // 注入翻译服务
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

    /**
     * 启动任务处理流程（音轨提取→音频转文字→字幕翻译）
     * @param task 已创建的任务实体
     */
    public void processTask(Task task) {
        logger.info("开始处理任务：{}", task.getId());
        try {
            // 步骤1：更新状态为"视频检查中"
            task.setStatus(TaskStatus.VIDEO_CHECKING);
            taskMapper.updateTask(task);

            // 步骤2：执行视频完整性检测
            boolean isVideoIntegral = ffmpegService.checkVideoIntegrity(task.getVideoFilePath());
            if (!isVideoIntegral) {
                // 视频损坏，更新状态等待用户选择
                task.setStatus(TaskStatus.VIDEO_DAMAGED_AWAITING_USER_CHOICE);
                task.setErrorMessage("视频文件损坏，可能无法正常处理");
                taskMapper.updateTask(task);
                return; // 终止后续处理，等待用户操作
            }
            else {
                logger.info("视频文件完整，继续处理");
            }

            // 1. 音轨提取流程（保持原有逻辑）
            task.setStatus(TaskStatus.AUDIO_EXTRACTING);
            logger.info("任务{}开始音轨提取...",task.getId());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);

            String audioFilename = UUID.randomUUID() + ".mp3";
            Path audioPath = tempAudioDir.resolve(audioFilename);
            String audioOutputPath = audioPath.toAbsolutePath().toString();

            boolean extractSuccess = ffmpegService.extractAudio(task.getVideoFilePath(), audioOutputPath);

            if (!extractSuccess) {
                task.setStatus(TaskStatus.EXTRACTION_FAILED);
                task.setErrorMessage("FFmpeg音轨提取失败");
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateTask(task);
                logger.error("任务{}音轨提取失败", task.getId());
                return;
            }

            task.setStatus(TaskStatus.AUDIO_EXTRACTED);
            task.setExtractedAudioFilename(audioFilename);
            task.setExtractedAudioFilePath(audioOutputPath);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);
            logger.info("任务{}音轨提取成功，音频路径：{}", task.getId(), audioOutputPath);

            // 2. 音频转文字流程（保持原有逻辑）
            task.setStatus(TaskStatus.TRANSCRIBING);
            logger.info("任务{}开始音频转文字...", task.getId());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);

            WhisperService.TranscriptionResult transcriptionResult =
                whisperService.transcribe(task.getExtractedAudioFilePath());

            task.setStatus(TaskStatus.TRANSCRIBED);
            task.setOriginalSrtFilename(transcriptionResult.getSrtFilename());
            task.setOriginalSrtFilePath(transcriptionResult.getSrtFilePath());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);
            logger.info("任务{}音频转文字成功，SRT路径：{}", task.getId(), transcriptionResult.getSrtFilePath());

            // 3. 新增：触发字幕翻译流程
            task.setStatus(TaskStatus.TRANSLATING); // 更新状态为翻译中
            logger.info("任务{}开始字幕翻译...", task.getId()); // 这里应该是info级别日志，修正为info
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);
            logger.info("任务{}开始字幕翻译，原始SRT路径：{}", task.getId(), task.getOriginalSrtFilePath());

            translationService.translateSrtFile(task); // 调用翻译服务

            // 翻译成功后更新状态（翻译服务内部已更新文件路径，此处更新最终状态）
            task.setStatus(TaskStatus.TRANSLATED);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);
            logger.info("任务{}字幕翻译成功，翻译后SRT路径：{}", task.getId(), task.getTranslatedSrtFilePath());

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