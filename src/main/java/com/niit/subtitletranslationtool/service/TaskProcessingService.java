package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * 字幕翻译任务处理服务，负责协调完成字幕翻译全流程操作。
 * 包含视频完整性检查、音轨提取、音频转文字、字幕翻译及可选的字幕压制到视频等核心步骤。
 */
@Service
public class TaskProcessingService {
    // 任务处理服务的日志记录器，用于记录关键操作和异常信息
    private static final Logger logger = LoggerFactory.getLogger(TaskProcessingService.class);

    // FFmpeg音视频处理服务实例（负责音轨提取、字幕压制等操作）
    private final FFmpegService ffmpegService;
    // 任务数据库操作映射器（用于更新任务状态和详细信息）
    private final TaskMapper taskMapper;
    // Whisper音频转文字服务实例（将提取的音轨转换为文字字幕）
    private final WhisperService whisperService;
    // 字幕翻译服务实例（将原始字幕翻译为目标语言）
    private final TranslationService translationService;
    // 视频内容总结生成服务实例（基于字幕内容生成视频摘要）
    private final SummaryService summaryService;
    // 临时音频文件存储目录路径（用于存放提取的音轨文件）
    private final Path tempAudioDir;
    // 压制后视频文件存储目录路径（通过配置文件注入）
    @Value("${file.subtitled-video-dir}")
    private String subtitledVideoDir;

    /**
     * 构造任务处理服务实例，通过依赖注入初始化核心服务及临时目录。
     *
     * @param ffmpegService       FFmpeg音视频处理服务实例
     * @param taskMapper          任务数据库操作映射器
     * @param whisperService      音频转文字服务实例
     * @param translationService  字幕翻译服务实例
     * @param summaryService      视频总结生成服务实例
     * @param tempAudioDir        临时音频目录配置路径（支持绝对路径或相对于项目根目录的相对路径）
     */
    @SuppressWarnings("DuplicateExpressions")
    @Autowired
    public TaskProcessingService(
            FFmpegService ffmpegService,
            TaskMapper taskMapper,
            WhisperService whisperService,
            TranslationService translationService,
            SummaryService summaryService,
            @Value("${temp.audio-dir}") String tempAudioDir) {
        this.ffmpegService = ffmpegService;
        this.taskMapper = taskMapper;
        this.whisperService = whisperService;
        this.translationService = translationService;
        this.summaryService = summaryService;

        // 解析临时音频目录路径（优先使用绝对路径，否则拼接项目根目录）
        //使用三元表达式判断是否为绝对路径，是则直接使用，否则拼接项目根目录获取完整路径，让代码更简洁
        this.tempAudioDir = Paths.get(tempAudioDir).isAbsolute()
                ? Paths.get(tempAudioDir)
                : Paths.get(System.getProperty("user.dir"), tempAudioDir);
        initDirectory(this.tempAudioDir); // 初始化目录（不存在则创建）
    }

    /**
     * 初始化指定目录（若不存在则创建）。
     *
     * @param dir 需要初始化的目录路径
     */
        private void initDirectory(Path dir) {
        if (!dir.toFile().exists()) {
            if (dir.toFile().mkdirs()) { // 创建目录及其所有父级目录
                logger.info("初始化临时音频目录成功: {}", dir.toAbsolutePath());
            } else {
                logger.error("初始化临时音频目录失败: {}", dir.toAbsolutePath());
            }
        }
    }

    /**
     * 处理字幕翻译任务全流程，包含视频检查、音轨提取、音频转文字、字幕翻译及可选压制步骤。
     * 任务状态将随处理阶段动态更新，异常时记录错误状态和详细信息。
     *
     * @param task 需要处理的任务对象（包含视频路径、目标语言等关键信息）
     */
    public void processTask(Task task) {
        logger.info("开始处理任务: {}", task.getId());
        try {
            // 第一步：视频完整性检查
            // 设置任务状态为视频检查中
            task.setStatus(TaskStatus.VIDEO_CHECKING);
            taskMapper.updateTask(task);

            // 调用FFmpegService检查视频完整性
            boolean isVideoIntegral = ffmpegService.checkVideoIntegrity(task.getVideoFilePath());
            if (!isVideoIntegral) {
                // 视频损坏时更新状态为等待用户处理
                task.setStatus(TaskStatus.VIDEO_DAMAGED_AWAITING_USER_CHOICE);
                task.setErrorMessage("视频文件损坏，无法继续处理");
                taskMapper.updateTask(task);
                return;
            }
            logger.info("视频文件完整性检查通过");

            // 第二步：提取视频音轨
            // 设置任务状态为音频提取中
            task.setStatus(TaskStatus.AUDIO_EXTRACTING);
            logger.info("任务{}开始提取音轨...", task.getId());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);

            // 生成唯一的音频文件名
            String audioFilename = UUID.randomUUID() + ".mp3";
            // 构建音频文件的完整路径
            Path audioPath = tempAudioDir.resolve(audioFilename);
            String audioOutputPath = audioPath.toAbsolutePath().toString();

            // 调用FFmpegService提取音频
            boolean extractSuccess = ffmpegService.extractAudio(task.getVideoFilePath(), audioOutputPath);
            if (!extractSuccess) {
                // 音轨提取失败时更新状态
                task.setStatus(TaskStatus.EXTRACTION_FAILED);
                task.setErrorMessage("FFmpeg音轨提取失败");
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateTask(task);
                logger.error("任务{}音轨提取失败", task.getId());
                return;
            }

            // 音轨提取成功时更新任务信息
            task.setStatus(TaskStatus.AUDIO_EXTRACTED);
            task.setExtractedAudioFilename(audioFilename);
            task.setExtractedAudioFilePath(audioOutputPath);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);
            logger.info("任务{}音轨提取成功，路径: {}", task.getId(), audioOutputPath);

            // 第三步：音频转文字生成原始字幕
            // 设置任务状态为转录中
            task.setStatus(TaskStatus.TRANSCRIBING);
            logger.info("任务{}开始音频转文字...", task.getId());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);

            // 调用WhisperService进行音频转录
            WhisperService.TranscriptionResult transcriptionResult = whisperService.transcribe(task.getExtractedAudioFilePath());

            // 转录成功后更新任务信息
            task.setStatus(TaskStatus.TRANSCRIBED);
            task.setOriginalSrtFilename(transcriptionResult.getSrtFilename());
            task.setOriginalSrtFilePath(transcriptionResult.getSrtFilePath());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);
            logger.info("任务{}音频转文字成功，SRT路径: {}", task.getId(), transcriptionResult.getSrtFilePath());

            // 生成视频内容总结（仅当音频转文字成功时执行）
            if (task.getStatus() == TaskStatus.TRANSCRIBED) {
                try {
                    // 调用SummaryService生成视频总结
                    String summary = summaryService.summarizeVideo(task.getOriginalSrtFilePath());
                    task.setSummary(summary);
                    taskMapper.updateTask(task);
                    logger.info("任务{}总结生成成功", task.getId());
                } catch (Exception e) {
                    logger.error("任务{}总结生成失败: {}", task.getId(), e.getMessage());
                }
            }

            // 第四步：翻译原始字幕
            // 设置任务状态为翻译中
            task.setStatus(TaskStatus.TRANSLATING);
            logger.info("任务{}开始翻译字幕...", task.getId());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);

            logger.info("任务{}开始翻译，原始SRT路径: {}", task.getId(), task.getOriginalSrtFilePath());
            translationService.translateSrtFile(task); // 执行字幕翻译

            // 翻译完成后更新任务信息
            task.setStatus(TaskStatus.TRANSLATED);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);
            logger.info("任务{}字幕翻译成功，翻译后SRT路径: {}", task.getId(), task.getTranslatedSrtFilePath());

            // 可选步骤：压制字幕到视频（仅当任务要求时执行）
            // 检查任务是否已翻译且需要压制字幕
            //if (task.getStatus() == TaskStatus.TRANSLATED && task.isBurnSubtitles()) { // 注释掉原有的条件判断

            // 无论如何都压制字幕，只要任务状态是已翻译
            if (task.getStatus() == TaskStatus.TRANSLATED) {
                // 设置任务状态为字幕压制中
                task.setStatus(TaskStatus.SUBTITLE_BURNING);
                taskMapper.updateTask(task);

                // 构建输出目录和文件名
                Path outputDir = Paths.get(subtitledVideoDir);
                String outputFilename = "subtitled_" + task.getOriginalVideoFilename();

                // 分离文件名和扩展名
                String baseName = outputFilename;
                String extension = "";
                int dotIndex = outputFilename.lastIndexOf('.');
                if (dotIndex > 0) {
                    baseName = outputFilename.substring(0, dotIndex);
                    extension = outputFilename.substring(dotIndex);
                }

                Path outputPath = outputDir.resolve(outputFilename);

                // 检查输出目录中是否存在重名文件
                while (Files.exists(outputPath)) {
                    // 生成随机5位字符后缀
                    String randomSuffix = generateRandomSuffix();
                    outputFilename = baseName + "_" + randomSuffix + extension;
                    outputPath = outputDir.resolve(outputFilename);
                }

                // 调用FFmpegService压制字幕
                boolean success = ffmpegService.burnSubtitles(
                        task.getVideoFilePath(),
                        task.getTranslatedSrtFilePath(),
                        outputPath.toString()
                );

                if (success) {
                    // 压制成功时记录输出信息
                    task.setSubtitledVideoFilename(outputFilename);
                    task.setSubtitledVideoFilePath(outputPath.toString());
                    task.setStatus(TaskStatus.COMPLETED);
                } else {
                    // 压制失败时更新错误状态
                    task.setStatus(TaskStatus.FAILED);
                    task.setErrorMessage("字幕压制失败");
                }
                taskMapper.updateTask(task);
            }

        } catch (Exception e) {
            // 异常处理：根据当前处理阶段确定错误状态
            TaskStatus errorStatus;
            if (task.getStatus() == TaskStatus.AUDIO_EXTRACTING) {
                errorStatus = TaskStatus.EXTRACTION_FAILED;
            } else if (task.getStatus() == TaskStatus.TRANSCRIBING) {
                errorStatus = TaskStatus.TRANSCRIPTION_FAILED;
            } else if (task.getStatus() == TaskStatus.TRANSLATING) {
                errorStatus = TaskStatus.TRANSLATION_FAILED;
            } else {
                errorStatus = TaskStatus.FAILED;
            }

            // 记录错误信息并更新任务状态
            task.setStatus(errorStatus);
            task.setErrorMessage("任务处理异常：" + e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateTask(task);
            logger.error("任务{}处理异常: {}", task.getId(), e.getMessage(), e);
        }
    }

    // 生成随机5位字符后缀的方法
    private String generateRandomSuffix() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(5);
        Random random = new Random();
        for (int i = 0; i < 5; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        return sb.toString();
    }
}