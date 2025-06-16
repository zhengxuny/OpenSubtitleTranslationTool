package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 字幕翻译任务处理服务类。
 *
 * <p>该类负责协调和执行字幕翻译的整个流程，包括视频完整性检查、提取音轨、音频转录为文字、翻译字幕以及将字幕压制到视频中。
 * 它的主要作用是将各个独立的服务（如FFmpegService, WhisperService, TranslationService）整合在一起，
 * 形成一个完整的字幕翻译工作流。
 *
 * <p>设计意图是通过一个中心化的服务来管理任务的状态和流程，使得任务的执行更加可控和易于追踪。
 */
@Service
public class TaskProcessingService {

  private static final Logger logger = LoggerFactory.getLogger(TaskProcessingService.class);

  private final FFmpegService ffmpegService;
  private final TaskMapper taskMapper;
  private final WhisperService whisperService;
  private final TranslationService translationService;
  private final SummaryService summaryService;
  private final Path tempAudioDir;

  @Value("${file.subtitled-video-dir}")
  private String subtitledVideoDir;

  /**
   * 构造函数，用于初始化 TaskProcessingService 类的实例。
   *
   * <p>通过依赖注入（Dependency Injection）的方式，将各个需要的服务（如 FFmpegService, TaskMapper,
   * WhisperService 等）注入到该类中。 这样做的好处是，可以方便地进行单元测试，并且降低了类之间的耦合度。
   *
   * @param ffmpegService 用于处理音视频的服务类
   * @param taskMapper 用于操作任务数据库的 Mapper 接口
   * @param whisperService 用于将音频转录为文字的服务类
   * @param translationService 用于翻译字幕的服务类
   * @param summaryService 用于生成视频内容总结的服务类
   * @param tempAudioDir 临时音频文件存储目录路径
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

    // 解析临时音频目录路径。如果配置的路径是绝对路径，则直接使用；否则，将其视为相对于项目根目录的路径，并进行拼接。
    // 这样做是为了保证在不同的环境下，临时目录的路径都能正确解析。
    this.tempAudioDir =
        Paths.get(tempAudioDir).isAbsolute()
            ? Paths.get(tempAudioDir)
            : Paths.get(System.getProperty("user.dir"), tempAudioDir);
    initDirectory(this.tempAudioDir); // 初始化临时音频目录，如果目录不存在则创建。
  }

  /**
   * 初始化指定的目录。如果目录不存在，则创建它。
   *
   * @param dir 需要初始化的目录路径
   */
  private void initDirectory(Path dir) {
    if (!dir.toFile().exists()) {
      // 使用 mkdirs() 方法创建目录及其所有父目录。
      if (dir.toFile().mkdirs()) {
        logger.info("初始化临时音频目录成功: {}", dir.toAbsolutePath());
      } else {
        logger.error("初始化临时音频目录失败: {}", dir.toAbsolutePath());
      }
    }
  }

  /**
   * 处理字幕翻译任务的完整流程。
   *
   * <p>该方法是整个字幕翻译流程的核心。它接收一个 Task 对象作为输入，然后按照预定的步骤执行任务，包括：
   *
   * <ol>
   *   <li>视频完整性检查：确保视频文件没有损坏。
   *   <li>提取视频音轨：从视频文件中提取音频。
   *   <li>音频转文字：将提取的音频转换为文字字幕。
   *   <li>翻译字幕：将原始字幕翻译成目标语言。
   *   <li>压制字幕到视频：将翻译后的字幕添加到视频中。
   * </ol>
   *
   * <p>在任务执行过程中，会不断更新任务的状态，并将状态信息保存到数据库中。 如果在任何一个步骤中发生错误，都会捕获异常，并将任务状态设置为相应的错误状态。
   *
   * @param task 需要处理的任务对象，包含了视频路径、目标语言等信息。
   */
  public void processTask(Task task) {
    logger.info("开始处理任务: {}", task.getId());
    try {
      // 第一步：视频完整性检查
      // 设置任务状态为视频检查中
      task.setStatus(TaskStatus.VIDEO_CHECKING);
      taskMapper.updateTask(task);

      // 调用 FFmpegService 检查视频完整性
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

      // 生成唯一的音频文件名，使用 UUID (Universally Unique Identifier) 可以保证文件名的唯一性，避免冲突。
      String audioFilename = UUID.randomUUID() + ".mp3";
      // 构建音频文件的完整路径
      Path audioPath = tempAudioDir.resolve(audioFilename);
      String audioOutputPath = audioPath.toAbsolutePath().toString();

      // 调用 FFmpegService 提取音频
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

      // 调用 WhisperService 进行音频转录
      WhisperService.TranscriptionResult transcriptionResult =
          whisperService.transcribe(task.getExtractedAudioFilePath());

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
          // 调用 SummaryService 生成视频总结
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
      // if (task.getStatus() == TaskStatus.TRANSLATED && task.isBurnSubtitles()) { // 注释掉原有的条件判断

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

        // 调用 FFmpegService 压制字幕
        boolean success =
            ffmpegService.burnSubtitles(
                task.getVideoFilePath(), task.getTranslatedSrtFilePath(), outputPath.toString());

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

  /**
   * 生成随机的 5 位字符后缀。
   *
   * <p>该方法用于在生成文件名时，避免文件名冲突。
   *
   * @return 随机生成的 5 位字符后缀
   */
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