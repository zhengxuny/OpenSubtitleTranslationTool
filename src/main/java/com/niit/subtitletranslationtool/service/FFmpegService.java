package com.niit.subtitletranslationtool.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * FFmpegService 类是一个服务组件，用于处理与 FFmpeg 相关的操作，例如提取音频、检查视频完整性以及将字幕压制到视频中。
 * 它利用 FFmpeg 命令行工具来执行这些任务。
 *
 * <p>此类被 Spring 管理，通过 @Service 注解声明为一个服务 Bean，可以被其他组件注入和使用。</p>
 * <p>
 *   `@Service` 注解是 Spring 框架提供的，用于将一个类标记为服务组件。
 *   Spring 会自动创建并管理这个类的实例，并将其注入到其他需要它的组件中。
 * </p>
 */
@Service
public class FFmpegService {
  private static final Logger logger = LoggerFactory.getLogger(FFmpegService.class);

  /**
   * 从视频文件中提取音频流并保存为 MP3 文件。
   *
   * @param videoPath 视频文件的路径。
   * @param audioOutputPath 提取的音频文件的输出路径。
   * @return 如果音频提取成功，则返回 true；否则返回 false。
   */
  public boolean extractAudio(String videoPath, String audioOutputPath) {
    logger.info("开始提取音轨，视频路径：{}，输出路径：{}", videoPath, audioOutputPath);

    // 检查视频文件是否存在
    if (!Files.exists(Path.of(videoPath))) {
      logger.error("视频文件不存在：{}", videoPath);
      return false;
    }

    // 获取输出路径的父目录
    Path outputDir = Path.of(audioOutputPath).getParent();

    // 如果父目录不存在，则创建它
    if (outputDir != null && !Files.exists(outputDir)) {
      try {
        Files.createDirectories(outputDir);
      } catch (IOException e) {
        logger.error("创建输出目录失败：{}", outputDir, e);
        return false;
      }
    }

    // 构建 FFmpeg 命令
    List<String> command = new ArrayList<>();
    command.add("ffmpeg"); // FFmpeg 命令
    command.add("-i"); // 输入文件参数
    command.add(videoPath); // 视频文件路径
    command.add("-vn"); // 禁用视频录制，只提取音频
    command.add("-acodec"); // 音频编码器参数
    command.add("libmp3lame"); // 使用 libmp3lame 编码器，用于 MP3 编码
    command.add("-ab"); // 音频比特率参数
    command.add("192k"); // 设置音频比特率为 192kbps
    command.add("-y"); // 覆盖输出文件，如果存在
    command.add(audioOutputPath); // 音频输出路径

    // 创建 ProcessBuilder 对象来执行命令
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    // 将错误流重定向到标准输出流，方便查看错误信息
    processBuilder.redirectErrorStream(true);

    try {
      // 启动进程
      Process process = processBuilder.start();

      // 读取进程的输出
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        // 逐行读取输出，并记录到日志中
        while ((line = reader.readLine()) != null) {
          logger.debug("FFmpeg输出: {}", line);
        }
      }

      // 等待进程完成
      int exitCode = process.waitFor();

      // 检查退出码
      if (exitCode == 0) {
        logger.info("音轨提取成功，输出路径：{}", audioOutputPath);
        return true;
      } else {
        logger.error("FFmpeg执行失败，退出码：{}", exitCode);
        return false;
      }
    } catch (IOException | InterruptedException e) {
      logger.error("执行FFmpeg命令时发生异常", e);
      // 如果是 InterruptedException 异常，则中断当前线程
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return false;
    }
  }

  /**
   * 检查视频文件的完整性。
   *
   * @param videoFilePath 要检查的视频文件的路径。
   * @return 如果视频文件完整，则返回 true；否则返回 false。
   * @throws IOException 如果在读取视频文件时发生 I/O 错误。
   * @throws InterruptedException 如果进程在等待时被中断。
   */
  public boolean checkVideoIntegrity(String videoFilePath) throws IOException, InterruptedException {
    // 创建 ProcessBuilder 对象来执行 FFmpeg 命令
    ProcessBuilder processBuilder =
        new ProcessBuilder(
            "ffmpeg", // FFmpeg 命令
            "-v",
            "error", // 设置日志级别为 error，只显示错误信息
            "-i",
            videoFilePath, // 输入文件参数，指定视频文件路径
            "-f",
            "null", // 指定输出格式为 null，表示不输出任何文件
            "-"); // 输出到标准输出
    // 将错误流重定向到标准输出流，方便查看错误信息
    processBuilder.redirectErrorStream(true);

    // 启动进程
    Process process = processBuilder.start();
    // 读取进程的输出
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    // 等待进程完成
    int exitCode = process.waitFor();

    // 检查退出码
    if (exitCode != 0) {
      logger.error("视频完整性检测失败，文件路径：{}，错误信息：{}", videoFilePath, output);
      return false;
    }
    return true;
  }

  /**
   * 将 SRT 字幕文件硬编码（压制）到视频中。 此版本通过将 SRT 文件路径转换为相对路径，从根本上解决了 Windows 环境下路径冒号的转义问题。
   *
   * @param videoPath 原始视频文件的绝对路径。
   * @param srtPath 翻译后的 SRT 字幕文件的绝对路径。
   * @param outputPath 包含字幕的输出视频文件的绝对路径。
   * @return 如果压制成功，则返回 true；否则返回 false。
   */
  public boolean burnSubtitles(String videoPath, String srtPath, String outputPath) {
    // 将视频路径和输出路径中的反斜杠替换为正斜杠，以避免路径问题
    String normalizedVideo = videoPath.replace("\\", "/");
    String normalizedOutput = outputPath.replace("\\", "/");

    String srtPathForFilter;
    try {
      // --- [决定性修复] ---
      // 1. 获取当前 Java 进程的工作目录。
      Path workingDir = Paths.get(System.getProperty("user.dir"));

      // 2. 将绝对的 SRT 路径转换为相对于工作目录的相对路径。
      Path absoluteSrtPath = Paths.get(srtPath);
      Path relativeSrtPath = workingDir.relativize(absoluteSrtPath);

      // 3. 使用这个不包含盘符冒号的相对路径。确保使用正斜杠。
      srtPathForFilter = relativeSrtPath.toString().replace('\\', '/');
      logger.info("已将绝对SRT路径 {} 转换为相对路径 {}", srtPath, srtPathForFilter);

    } catch (Exception e) {
      logger.error("无法将SRT路径转换为相对路径，将尝试使用原始路径（可能失败）。SRT路径: {}", srtPath, e);
      // 如果转换失败，退回到旧方法，但这几乎肯定会再次失败
      srtPathForFilter = srtPath.replace("\\", "/");
    }

    // 创建输出文件对象
    File outputFile = new File(normalizedOutput);
    // 获取输出文件的父目录
    File outputDir = outputFile.getParentFile();
    // 如果输出目录不存在，则创建它
    if (outputDir != null && !outputDir.exists()) {
      if (!outputDir.mkdirs()) {
        logger.error("创建输出目录失败：{}", outputDir.getAbsolutePath());
        return false;
      }
    }

    // --- NVIDIA 硬件加速编码 (推荐方案) ---
    // 我们再次使用单引号包裹路径，这是最安全的做法，以防未来路径中出现空格。
    // 因为路径现在是相对的，没有冒号，所以不会再有解析问题。
    List<String> command =
        new ArrayList<>(
            Arrays.asList(
                "ffmpeg", // FFmpeg 命令
                "-hwaccel",
                "cuda", // 启用 CUDA 硬件加速
                "-i",
                normalizedVideo, // 输入文件参数，指定视频文件路径
                // FFmpeg 会自动处理 CPU 和 GPU 之间的数据传输
                "-vf",
                String.format("subtitles=filename='%s'", srtPathForFilter), // 使用 subtitles 滤镜添加字幕，filename 指定字幕文件路径
                "-c:a",
                "copy", // 音频编码器参数，copy 表示直接复制音频流，不进行重新编码
                "-c:v",
                "h264_nvenc", // 视频编码器参数，使用 NVIDIA 的 h264_nvenc 编码器进行硬件加速编码
                "-preset",
                "p1", // 编码预设，p1 表示速度最快，但质量稍差
                "-cq",
                "23", // 恒定质量模式，23 是一个比较好的平衡点
                "-y", // 覆盖输出文件，如果存在
                normalizedOutput // 输出文件路径
                ));

    logger.info("执行FFmpeg字幕压制命令：{}", String.join(" ", command));

    try {
      // 创建 ProcessBuilder 对象来执行命令
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      // 将错误流重定向到标准输出流，方便查看错误信息
      processBuilder.redirectErrorStream(true);
      // 启动进程
      Process process = processBuilder.start();

      // 创建 StringBuilder 对象来保存 FFmpeg 的输出
      StringBuilder ffmpegOutputBuilder = new StringBuilder();
      // 读取进程的输出
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        // 逐行读取输出，并添加到 StringBuilder 中
        while ((line = reader.readLine()) != null) {
          ffmpegOutputBuilder.append(line).append(System.lineSeparator());
        }
      }
      // 将 StringBuilder 中的内容转换为字符串
      String ffmpegOutput = ffmpegOutputBuilder.toString();

      // 等待进程完成，最多等待 30 分钟
      boolean finished = process.waitFor(30, TimeUnit.MINUTES);
      int exitCode;

      // 如果进程超时，则强制终止
      if (!finished) {
        logger.error("FFmpeg进程超时，强制终止");
        process.destroyForcibly();
        exitCode = process.waitFor();
        logger.error("强制终止后退出码：{}", exitCode);
        return false;
      }

      // 获取进程的退出码
      exitCode = process.exitValue();
      // 检查退出码
      if (exitCode != 0) {
        logger.error("FFmpeg压制失败，退出码 {}，命令：{}", exitCode, String.join(" ", command));
        logger.error("FFmpeg完整输出日志：\n{}", ffmpegOutput);
        return false;
      }

      logger.info("FFmpeg压制成功！输出文件位于: {}", normalizedOutput);
      return true;
    } catch (InterruptedException e) {
      logger.error("FFmpeg进程被中断", e);
      // 如果是 InterruptedException 异常，则中断当前线程
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      logger.error("FFmpeg压制异常", e);
      return false;
    }
  }

  /**
   * 获取音频文件的时长（秒）。
   *
   * @param audioFilePath 音频文件的路径。
   * @return 音频文件的时长（秒），如果获取失败，则返回 -1。
   */
  public long getAudioDuration(String audioFilePath) {
    try {
      // 构建 FFprobe 命令
      List<String> command =
          Arrays.asList(
              "ffprobe", // FFprobe 命令
              "-v",
              "error", // 设置日志级别为 error，只显示错误信息
              "-show_entries",
              "format=duration", // 显示 format 部分的 duration 信息
              "-of",
              "default=noprint_wrappers=1:nokey=1", // 设置输出格式，只输出值，不输出键和包装器
              audioFilePath // 音频文件路径
              );
      // 创建 ProcessBuilder 对象来执行命令
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      // 将错误流重定向到标准输出流，方便查看错误信息
      processBuilder.redirectErrorStream(true);
      // 启动进程
      Process process = processBuilder.start();
      // 读取进程的输出
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      // 等待进程完成
      int exitCode = process.waitFor();
      // 检查退出码和输出是否为空
      if (exitCode == 0 && !output.isEmpty()) {
        // 将输出转换为 double 类型，并向上取整，然后转换为 long 类型
        return (long) Math.ceil(Double.parseDouble(output));
      } else {
        logger.error("获取音频时长失败，输出: {}", output);
        return -1;
      }
    } catch (Exception e) {
      logger.error("获取音频时长时发生异常", e);
      return -1;
    }
  }
}