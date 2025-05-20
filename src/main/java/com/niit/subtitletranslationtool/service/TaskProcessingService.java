package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import com.niit.subtitletranslationtool.service.FFmpegService;
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
    private final WhisperService whisperService;  // 新增Whisper服务依赖
    private final Path tempAudioDir; // 临时音频存储目录（从配置读取）

    @Autowired
    public TaskProcessingService(
            FFmpegService ffmpegService,
            TaskMapper taskMapper,
            WhisperService whisperService,  // 新增构造参数
            @Value("${temp.audio-dir}") String tempAudioDir) {
        this.ffmpegService = ffmpegService;
        this.taskMapper = taskMapper;
        this.whisperService = whisperService;  // 注入Whisper服务
        // 解析临时目录路径（支持绝对/相对路径）
        this.tempAudioDir = Paths.get(tempAudioDir).isAbsolute()
                ? Paths.get(tempAudioDir)
                : Paths.get(System.getProperty("user.dir"), tempAudioDir);
        // 初始化目录（如果不存在）
        initDirectory(this.tempAudioDir);
    }

    private void initDirectory(Path dir) {
        if (!dir.toFile().exists()) {
            dir.toFile().mkdirs();
            logger.info("初始化临时音频目录：{}", dir.toAbsolutePath());
        }
    }

    /**
     * 启动任务处理流程（音轨提取→音频转文字）
     * @param task 已创建的任务实体
     */
    public void processTask(Task task) {
        logger.info("开始处理任务：{}", task.getId());
        try {
            // 1. 更新任务状态为"音轨提取中"
            task.setStatus(TaskStatus.AUDIO_EXTRACTING);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);

            // 2. 生成唯一音频文件名
            String audioFilename = UUID.randomUUID() + ".mp3";
            Path audioPath = tempAudioDir.resolve(audioFilename);
            String audioOutputPath = audioPath.toAbsolutePath().toString();

            // 3. 执行音轨提取
            boolean extractSuccess = ffmpegService.extractAudio(task.getVideoFilePath(), audioOutputPath);

            // 4. 处理音轨提取结果
            if (!extractSuccess) {
                task.setStatus(TaskStatus.EXTRACTION_FAILED);
                task.setErrorMessage("FFmpeg音轨提取失败");
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateTask(task);
                logger.error("任务{}音轨提取失败", task.getId());
                return; // 提前终止流程
            }

            // 音轨提取成功，更新任务状态
            task.setStatus(TaskStatus.AUDIO_EXTRACTED);
            task.setExtractedAudioFilename(audioFilename);
            task.setExtractedAudioFilePath(audioOutputPath);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);
            logger.info("任务{}音轨提取成功，音频路径：{}", task.getId(), audioOutputPath);

            // 5. 触发音频转文字（新增流程）
            task.setStatus(TaskStatus.TRANSCRIBING);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);

            // 执行转录
            WhisperService.TranscriptionResult transcriptionResult =
                whisperService.transcribe(task.getExtractedAudioFilePath());

            // 转录成功，更新任务SRT信息
            task.setStatus(TaskStatus.TRANSCRIBED);
            task.setOriginalSrtFilename(transcriptionResult.getSrtFilename());
            task.setOriginalSrtFilePath(transcriptionResult.getSrtFilePath());
            logger.info("任务{}音频转文字成功，SRT路径：{}", task.getId(), transcriptionResult.getSrtFilePath());

        } catch (Exception e) {
            // 处理音轨提取或转录过程中的异常
            TaskStatus errorStatus = task.getStatus() == TaskStatus.AUDIO_EXTRACTING
                ? TaskStatus.EXTRACTION_FAILED
                : TaskStatus.TRANSCRIPTION_FAILED;  // 根据当前阶段判断错误状态

            task.setStatus(errorStatus);
            task.setErrorMessage("任务处理异常：" + e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);
            logger.error("任务{}处理过程中发生异常：{}", task.getId(), e.getMessage(), e);
        }
    }
}