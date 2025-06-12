package com.niit.subtitletranslationtool.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * FFmpegService类，提供与FFmpeg命令行工具交互的服务。
 * <p>
 * 该类封装了音轨提取、视频完整性检查以及字幕压制等功能，通过调用FFmpeg命令行工具实现。
 * 使用前请确保系统已正确安装FFmpeg，并且FFmpeg可执行文件位于环境变量中，或者可以通过绝对路径调用。
 */
@Service
public class FFmpegService {
    private static final Logger logger = LoggerFactory.getLogger(FFmpegService.class);

    /**
     * 从指定的视频文件中提取音轨，并保存为MP3格式的音频文件。
     *
     * @param videoPath       输入视频文件的完整路径。
     * @param audioOutputPath 输出MP3音频文件的完整路径。
     * @return 如果提取成功，则返回true；如果文件不存在、目录创建失败或FFmpeg执行异常，则返回false。
     */
    public boolean extractAudio(String videoPath, String audioOutputPath) {
        logger.info("开始提取音轨，视频路径：{}，输出路径：{}", videoPath, audioOutputPath);

        // 检查输入视频文件是否存在
        if (!Files.exists(Path.of(videoPath))) {
            logger.error("视频文件不存在：{}", videoPath);
            return false;
        }

        // 获取输出目录路径并创建缺失的目录
        Path outputDir = Path.of(audioOutputPath).getParent();
        if (outputDir != null && !Files.exists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
            } catch (IOException e) {
                logger.error("创建输出目录失败：{}", outputDir, e);
                return false;
            }
        }

        // 构建FFmpeg提取音轨的命令参数列表
        List<String> command = new ArrayList<>();
        command.add("ffmpeg"); // 添加FFmpeg命令
        command.add("-i"); // 指定输入文件
        command.add(videoPath); // 输入视频文件的路径
        command.add("-vn");          // 禁用视频流，只提取音频
        command.add("-acodec");      // 指定音频编码器
        command.add("libmp3lame");   // 使用LAME MP3编码器，用于将音频编码为MP3格式
        command.add("-ab");          // 设置音频比特率
        command.add("192k");         // 设置比特率为192kbps，这是一个常用的MP3比特率
        command.add("-y");           // 覆盖已存在的输出文件，无需提示
        command.add(audioOutputPath); // 指定输出音频文件的路径

        // 使用ProcessBuilder启动FFmpeg进程，并重定向错误流到标准输出流
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();

            // 读取并记录FFmpeg的输出日志
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("FFmpeg输出: {}", line); // 逐行读取FFmpeg的输出信息，并记录到日志中
                }
            }

            // 等待进程执行完成，并检查退出码
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("音轨提取成功，输出路径：{}", audioOutputPath);
                return true; // 如果退出码为0，表示命令执行成功
            } else {
                logger.error("FFmpeg执行失败，退出码：{}", exitCode);
                return false; // 如果退出码非0，表示命令执行失败
            }
        } catch (IOException | InterruptedException e) {
            logger.error("执行FFmpeg命令时发生异常", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // 如果是中断异常，则重置中断状态
            }
            return false; // 发生异常时返回false
        }
    }

    /**
     * 检查视频文件是否完整，通过执行FFmpeg命令并分析其退出码来判断。
     *
     * @param videoFilePath 待检测的视频文件的完整路径。
     * @return 如果视频完整（FFmpeg退出码为0），则返回true；否则返回false。
     * @throws IOException          启动进程或读取输出时发生IO异常。
     * @throws InterruptedException 等待进程完成时线程被中断。
     */
    public boolean checkVideoIntegrity(String videoFilePath) throws IOException, InterruptedException {
        // 构建视频完整性检测命令，该命令仅解析视频文件，不进行任何输出
        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg",
                "-v", "error",          // 设置日志级别为error，仅输出错误信息
                "-i", videoFilePath, // 指定输入视频文件路径
                "-f", "null",           // 设置输出格式为null，即不进行实际输出
                "-"                     // 输出到标准输出
        );
        processBuilder.redirectErrorStream(true); // 将错误流重定向到标准输出流
        Process process = processBuilder.start(); // 启动FFmpeg进程

        // 读取并记录FFmpeg的错误输出
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor(); // 等待进程执行完成

        // 如果退出码不为0，则表示视频文件不完整或存在错误
        if (exitCode != 0) {
            logger.error("视频完整性检测失败，文件路径：{}，错误信息：{}", videoFilePath, output);
            return false;
        }
        return true; // 如果退出码为0，则表示视频文件完整
    }

    /**
     * 转义Windows路径中的冒号，以适配FFmpeg滤镜语法。
     * <p>
     * 在FFmpeg滤镜中，冒号具有特殊含义，因此需要转义Windows驱动器号后的冒号（例如，将"C:/"转义为"C\\:/"）。
     *
     * @param path 原始文件路径（可能为null）。
     * @return 转义后的路径。如果输入为null，则返回null。
     */
    private String escapePathForFFmpegFilter(String path) {
        if (path == null) {
            return null;
        }

        // 匹配Windows驱动器号路径的模式（例如，C:/或D:\）
        Pattern windowsPathPattern = Pattern.compile("^([a-zA-Z]):([/\\\\])");
        Matcher matcher = windowsPathPattern.matcher(path);

        // 如果找到匹配的路径
        if (matcher.find()) {
            // 替换冒号为转义形式（C:/ → C\\:/）
            return matcher.group(1) + "\\\\:" + matcher.group(2) + path.substring(matcher.end());
        }
        return path; // 如果不是Windows路径，则直接返回原始路径
    }

    /**
     * 将SRT字幕文件硬编码（压制）到视频中。
     *
     * @param videoPath  原始视频文件的绝对路径。
     * @param srtPath    翻译后的SRT字幕文件的绝对路径。
     * @param outputPath 包含字幕的输出视频文件的绝对路径。
     * @return 如果压制成功，则返回true；否则返回false（路径转义失败、目录创建失败或FFmpeg执行异常时）。
     */
    public boolean burnSubtitles(String videoPath, String srtPath, String outputPath) {
        // 规范化路径（统一使用正斜杠）
        String escapedVideo = videoPath.replace("\\", "/");
        String escapedSrt = srtPath.replace("\\", "/");
        String escapedOutput = outputPath.replace("\\", "/");

        // 转义SRT路径，以适配FFmpeg滤镜
        String srtPathForFilter = escapePathForFFmpegFilter(escapedSrt);
        if (srtPathForFilter == null) {
            logger.error("SRT路径转义后为空");
            return false;
        }

        // 创建输出目录（如果不存在）
        File outputFile = new File(escapedOutput);
        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                logger.error("创建输出目录失败：{}", outputDir.getAbsolutePath());
                return false;
            }
            logger.info("已创建输出目录：{}", outputDir.getAbsolutePath());
        }

        // 构建FFmpeg压制字幕的命令
        List<String> command = new ArrayList<>(Arrays.asList(
                "ffmpeg",
                "-i", escapedVideo, // 指定输入视频文件
                "-vf", "subtitles=" + srtPathForFilter,  // 指定字幕滤镜及路径，将SRT字幕文件压制到视频中
                "-c:v", "libx264",                       // 指定H.264视频编码器
                "-c:a", "aac",                           // 指定AAC音频编码器
                "-strict", "experimental",               // 允许使用实验性编码器，例如AAC
                "-y",                                    // 覆盖输出文件，无需确认
                escapedOutput                              // 指定输出视频文件
        ));

        logger.info("执行FFmpeg字幕压制命令：{}", String.join(" ", command));

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true); // 将错误流重定向到标准输出流
            Process process = processBuilder.start(); // 启动FFmpeg进程

            // 读取并记录FFmpeg的完整输出
            StringBuilder ffmpegOutputBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ffmpegOutputBuilder.append(line).append(System.lineSeparator()); // 逐行读取FFmpeg的输出信息
                }
            }
            String ffmpegOutput = ffmpegOutputBuilder.toString();
            logger.info("FFmpeg字幕压制输出:\n{}", ffmpegOutput);

            // 等待进程完成（设置超时时间为30分钟）
            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            int exitCode;

            // 如果进程超时，则强制终止
            if (!finished) {
                logger.error("FFmpeg进程超时，强制终止");
                process.destroyForcibly(); // 强制终止FFmpeg进程
                exitCode = process.waitFor(); // 等待进程终止
                logger.error("强制终止后退出码：{}", exitCode);
                return false;
            }

            exitCode = process.exitValue(); // 获取进程的退出码
            // 如果退出码不为0，则表示压制失败
            if (exitCode != 0) {
                logger.error("FFmpeg压制失败，退出码 {}，命令：{}", exitCode, String.join(" ", command));
                // 检查输出文件是否存在或为空
                File createdOutputFile = new File(escapedOutput);
                if (!createdOutputFile.exists() || createdOutputFile.length() == 0) {
                    logger.error("输出文件未创建或为空：{}", escapedOutput);
                }
                return false;
            }
            return true; // 压制成功，返回true
        } catch (InterruptedException e) {
            logger.error("FFmpeg进程被中断，命令：{}", String.join(" ", command), e);
            Thread.currentThread().interrupt(); // 恢复中断状态
            return false;
        } catch (Exception e) {
            logger.error("FFmpeg压制异常，命令：{}", String.join(" ", command), e);
            return false;
        }
    }
}