
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

// 定义一个Spring服务，用于处理字幕翻译任务
@Service
public class TaskProcessingService {
    private final SummaryService summaryService;

    // 日志记录器，用于记录任务处理过程中的重要信息和错误
    private static final Logger logger = LoggerFactory.getLogger(TaskProcessingService.class);

    // FFmpeg服务实例，用于音轨提取和视频字幕压制等操作
    private final FFmpegService ffmpegService;
    // 任务映射器实例，用于数据库中的任务状态和信息更新
    private final TaskMapper taskMapper;
    // Whisper服务实例，负责将音频文件转换为文字
    private final WhisperService whisperService;
    // 翻译服务实例，用于翻译生成的文字字幕
    private final TranslationService translationService;
    // 临时音频文件存储目录路径，用于存放音轨提取后的音频文件
    private final Path tempAudioDir;
    // 压制后的视频文件存储目录路径，通过配置文件读取
    @Value("${file.subtitled-video-dir}")
    private String subtitledVideoDir;

    // 通过构造函数注入各种服务依赖以及临时存储目录路径，并对目录进行初始化检查
    @Autowired
    public TaskProcessingService(
            FFmpegService ffmpegService,
            TaskMapper taskMapper,
            WhisperService whisperService,
            TranslationService translationService,
            SummaryService summaryService,
            @Value("${temp.audio-dir}") String tempAudioDir)
    {
        this.ffmpegService = ffmpegService;
        this.taskMapper = taskMapper;
        this.whisperService = whisperService;
        this.translationService = translationService;
        this.summaryService = summaryService;

        // 将配置文件中指定的临时音频目录配置转换为绝对路径
        // 如果配置路径已经是绝对路径，则保持不变；否则将其相对路径视为相对于项目的根目录
        this.tempAudioDir = Paths.get(tempAudioDir).isAbsolute()
                ? Paths.get(tempAudioDir)
                : Paths.get(System.getProperty("user.dir"), tempAudioDir);
        initDirectory(this.tempAudioDir); // 初始化或创建目录
    }

    // 初始化指定文件目录（如果目录不存在则创建新目录）
    private void initDirectory(Path dir) {
        if (!dir.toFile().exists()) { // 检查路径是否已存在文件或目录
            dir.toFile().mkdirs(); // 创建目录及其所有必要的父目录
            logger.info("初始化或创建临时音频目录: {}", dir.toAbsolutePath());
        }
    }

    // 主要的任务处理方法，接收一个任务对象，执行整个任务流程
    public void processTask(Task task) {
        logger.info("开始处理任务: {}", task.getId()); // 记录正在处理的任务ID
        try {
            // 第一步：更新任务状态为“视频检查中”，准备检查视频文件是否完整
            task.setStatus(TaskStatus.VIDEO_CHECKING);
            taskMapper.updateTask(task);

            // 使用FFmpegService检查视频文件的完整性
            boolean isVideoIntegral = ffmpegService.checkVideoIntegrity(task.getVideoFilePath());
            if (!isVideoIntegral) { // 如果检查结果显示视频文件已损坏
                // 更新任务状态为“等待用户处理”，并记录错误信息
                task.setStatus(TaskStatus.VIDEO_DAMAGED_AWAITING_USER_CHOICE);
                task.setErrorMessage("视频文件损坏，可能无法正常处理");
                taskMapper.updateTask(task);
                return; // 终止后续处理步骤，等待用户上传新的视频文件
            } else { // 如果视频文件完好
                logger.info("视频文件完好，继续处理");
            }

            // 第二步：提取视频中的音轨为单独的音频文件
            task.setStatus(TaskStatus.AUDIO_EXTRACTING); // 更新任务状态为音轨提取中
            logger.info("任务{}开始音轨提取...", task.getId());
            task.setUpdatedAt(LocalDateTime.now()); // 更新任务的最后修改时间为当前时间
            taskMapper.updateTask(task);

            String audioFilename = UUID.randomUUID() + ".mp3"; // 生成一个唯一名称的音频文件名
            Path audioPath = tempAudioDir.resolve(audioFilename); // 指定音频文件的存储路径
            String audioOutputPath = audioPath.toAbsolutePath().toString(); // 构建绝对路径字符串

            // 使用FFmpeg服务尝试提取视频中的音轨到指定的目标路径
            boolean extractSuccess = ffmpegService.extractAudio(task.getVideoFilePath(), audioOutputPath);

            if (!extractSuccess) { // 如果音轨提取失败
                task.setStatus(TaskStatus.EXTRACTION_FAILED); // 更新任务状态为“提取失败”
                task.setErrorMessage("FFmpeg音轨提取失败"); // 记录错误信息
                task.setUpdatedAt(LocalDateTime.now()); // 更新最后修改时间
                taskMapper.updateTask(task); // 将更新后的任务信息保存到数据库
                logger.error("任务{}音轨提取失败", task.getId()); // 记录错误日志
                return; // 终止处理流程，返回
            } else { // 如果音轨提取成功
                // 更新任务状态为“音轨已提取”，并保存提取到的音频文件的相关信息
                task.setStatus(TaskStatus.AUDIO_EXTRACTED);
                task.setExtractedAudioFilename(audioFilename);
                task.setExtractedAudioFilePath(audioOutputPath);
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateTask(task);
                logger.info("任务{}音轨提取成功，音频路径: {}", task.getId(), audioOutputPath); // 记录成功日志
            }

            // 第三步：将提取出的音频文件转换为文字字幕
            task.setStatus(TaskStatus.TRANSCRIBING); // 更新为“正在转换音频到文字”
            logger.info("任务{}开始音频转文字...", task.getId()); // 记录开始日志
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);

            // 使用Whisper服务将音频文件转换为文字，并返回转换结果
            WhisperService.TranscriptionResult transcriptionResult = whisperService.transcribe(task.getExtractedAudioFilePath());

            task.setStatus(TaskStatus.TRANSCRIBED); // 任务状态更新为“已转换”
            // 保存生成的文字字幕文件的文件名和路径到任务对象
            task.setOriginalSrtFilename(transcriptionResult.getSrtFilename());
            task.setOriginalSrtFilePath(transcriptionResult.getSrtFilePath());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task); // 更新数据库中的任务信息记录
            logger.info("任务{}音频转文字成功，SRT路径: {}", task.getId(), transcriptionResult.getSrtFilePath()); // 记录成功日志

            // 生成总结
            if (task.getStatus() == TaskStatus.TRANSCRIBED) {
                try {
                    // Generate summary
                    String summary = summaryService.summarizeVideo(task.getOriginalSrtFilePath());
                    task.setSummary(summary);
                    taskMapper.updateTask(task);
                    logger.info("总结生成成功"); // 记录成功日志
                } catch (Exception e) {
                    System.err.println("Summarization failed: " + e.getMessage());
                }
            }

            // 第四步：开始翻译已生成的文字字幕为指定的目标语言
            task.setStatus(TaskStatus.TRANSLATING); // 更新状态为“正在翻译”
            logger.info("任务{}开始翻译字幕...", task.getId()); // 记录开始日志
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);

            // 记录翻译开始的相关信息，包括源语言的文字字幕文件路径
            logger.info("任务{}开始翻译字幕，原始SRT路径: {}", task.getId(), task.getOriginalSrtFilePath());

            // 调用翻译服务对文字字幕文件进行翻译处理
            translationService.translateSrtFile(task);

            // 字幕文件翻译完成后，更新任务的状态为“已翻译”
            task.setStatus(TaskStatus.TRANSLATED);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);
            logger.info("任务{}字幕翻译成功，翻译后SRT路径: {}", task.getId(), task.getTranslatedSrtFilePath()); // 记录成功日志

            // 如果任务配置中设置了需要将字幕压制到视频文件中
            if (task.getStatus() == TaskStatus.TRANSLATED && task.isBurnSubtitles()) {
                task.setStatus(TaskStatus.SUBTITLE_BURNING); // 更新状态为“正在压制字幕”
                taskMapper.updateTask(task);

                // 使用配置文件中指定的目录来保存压制后的视频文件
                Path outputDir = Paths.get(subtitledVideoDir);
                // 根据源视频文件名生成压制后视频文件的文件名
                String outputFilename = "subtitled_" + task.getOriginalVideoFilename();
                String outputPath = outputDir.resolve(outputFilename).toString(); // 构建绝对路径字符串

                // 调用FFmpeg服务将翻译后的字幕压制到视频文件中
                boolean success = ffmpegService.burnSubtitles(
                        task.getVideoFilePath(), // 源视频文件路径
                        task.getTranslatedSrtFilePath(), // 翻译后的字幕文件路径
                        outputPath // 压制后视频文件保存路径
                );

                // 根据压制操作的结果更新任务状态
                if (success) {
                    // 如果压制成功，则记录最终生成的视频文件名及路径
                    task.setSubtitledVideoFilename(outputFilename);
                    task.setSubtitledVideoFilePath(outputPath);
                    task.setStatus(TaskStatus.COMPLETED); // 任务状态更新为“已完成”
                } else {
                    task.setStatus(TaskStatus.FAILED); // 任务状态更新为“失败”
                    task.setErrorMessage("字幕压制失败"); // 记录错误信息
                }
                taskMapper.updateTask(task); // 更新数据库中的任务信息记录
            }

        } catch (Exception e) { // 捕获在处理过程中发生的任何异常错误
            // 根据当前任务处理到的阶段确定适当的错误状态
            TaskStatus errorStatus;
            if (task.getStatus() == TaskStatus.AUDIO_EXTRACTING) {
                errorStatus = TaskStatus.EXTRACTION_FAILED; // 音轨提取阶段错误
            } else if (task.getStatus() == TaskStatus.TRANSCRIBING) {
                errorStatus = TaskStatus.TRANSCRIPTION_FAILED; // 音频转文字阶段错误
            } else if (task.getStatus() == TaskStatus.TRANSLATING) {
                errorStatus = TaskStatus.TRANSLATION_FAILED; // 翻译字幕阶段错误
            } else {
                errorStatus = TaskStatus.FAILED; // 其他未知错误
            }

            // 更新任务状态为错误状态，保存错误信息，并记录错误日志
            task.setStatus(errorStatus);
            task.setErrorMessage("任务处理异常：" + e.getMessage()); // 记录错误全信息
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);
            logger.error("任务{}处理过程中发生异常: {}", task.getId(), e.getMessage(), e);
        }
    }
}