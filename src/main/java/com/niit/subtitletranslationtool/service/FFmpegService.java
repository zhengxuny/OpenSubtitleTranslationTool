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
 * 提供与FFmpeg命令行工具交互的服务类，支持音轨提取、视频完整性检查及字幕压制等功能。
 * <p>
 * 依赖系统已正确安装FFmpeg且可执行文件在环境变量中，或通过绝对路径调用。
 */
@Service
public class FFmpegService {
    private static final Logger logger = LoggerFactory.getLogger(FFmpegService.class);

    /**
     * 从指定视频文件中提取音轨并保存为MP3格式。
     *
     * @param videoPath       输入视频文件的完整路径
     * @param audioOutputPath 输出MP3音频文件的完整路径
     * @return 提取成功返回true，否则返回false（文件不存在、目录创建失败或FFmpeg执行异常时）
     */
    public boolean extractAudio(String videoPath, String audioOutputPath) {
        logger.info("开始提取音轨，视频路径：{}，输出路径：{}", videoPath, audioOutputPath);

        // 检查输入视频文件是否存在
        if (!Files.exists(Path.of(videoPath))) {
            logger.error("视频文件不存在：{}", videoPath);
            return false;
        }

        // 获取输出目录路径并创建缺失目录
        Path outputDir = Path.of(audioOutputPath).getParent();
        if (outputDir != null && !Files.exists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
            } catch (IOException e) {
                logger.error("创建输出目录失败：{}", outputDir, e);
                return false;
            }
        }

        // 构建FFmpeg提取音轨命令参数
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-i");
        command.add(videoPath);
        command.add("-vn");          // 禁用视频流
        command.add("-acodec");      // 指定音频编码器
        command.add("libmp3lame");   // 使用LAME MP3编码器
        command.add("-ab");          // 设置音频比特率
        command.add("192k");         // 比特率192kbps
        command.add("-y");           // 覆盖已存在的输出文件
        command.add(audioOutputPath);

        // 启动FFmpeg进程并合并错误流
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();

            // 读取并记录FFmpeg输出日志
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("FFmpeg输出: {}", line);
                }
            }

            // 等待进程执行并检查退出码
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("音轨提取成功，输出路径：{}", audioOutputPath);
                return true;
            } else {
                logger.error("FFmpeg执行失败，退出码：{}", exitCode);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            logger.error("执行FFmpeg命令时发生异常", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * 检查视频文件是否完整（FFmpeg可解析无错误）。
     *
     * @param videoFilePath 待检测的视频文件完整路径
     * @return 视频完整（FFmpeg退出码为0）返回true，否则返回false
     * @throws IOException          启动进程或读取输出时发生IO异常
     * @throws InterruptedException 等待进程完成时线程被中断
     */
    public boolean checkVideoIntegrity(String videoFilePath) throws IOException, InterruptedException {
        // 构建视频完整性检测命令（仅解析不输出）
        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg",
                "-v", "error",          // 仅输出错误日志
                "-i", videoFilePath,
                "-f", "null",           // 输出格式为null（无实际输出）
                "-"
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // 读取并记录FFmpeg错误输出
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            logger.error("视频完整性检测失败，文件路径：{}，错误信息：{}", videoFilePath, output);
            return false;
        }
        return true;
    }

    /**
     * 转义Windows路径中的冒号以适配FFmpeg滤镜语法。
     * FFmpeg滤镜中冒号有特殊含义，需转义Windows驱动器号后的冒号（如"C:/"转义为"C\\:/"）。
     *
     * @param path 原始文件路径（可能为null）
     * @return 转义后的路径，输入为null时返回null
     */
    private String escapePathForFFmpegFilter(String path) {
        if (path == null) {
            return null;
        }

        // 匹配Windows驱动器号路径模式（如C:/或D:\）
        Pattern windowsPathPattern = Pattern.compile("^([a-zA-Z]):([/\\\\])");
        Matcher matcher = windowsPathPattern.matcher(path);

        if (matcher.find()) {
            // 替换冒号为转义形式（C:/ → C\\:/）
            return matcher.group(1) + "\\\\:" + matcher.group(2) + path.substring(matcher.end());
        }
        return path;
    }

    /**
     * 将SRT字幕文件硬编码（压制）到视频中。
     *
     * @param videoPath  原始视频文件绝对路径
     * @param srtPath    翻译后的SRT字幕文件绝对路径
     * @param outputPath 包含字幕的输出视频文件绝对路径
     * @return 压制成功返回true，否则返回false（路径转义失败、目录创建失败或FFmpeg执行异常时）
     */
    public boolean burnSubtitles(String videoPath, String srtPath, String outputPath) {
        // 规范化路径（统一使用正斜杠）
        String escapedVideo = videoPath.replace("\\", "/");
        String escapedSrt = srtPath.replace("\\", "/");
        String escapedOutput = outputPath.replace("\\", "/");

        // 转义SRT路径以适配FFmpeg滤镜
        String srtPathForFilter = escapePathForFFmpegFilter(escapedSrt);
        if (srtPathForFilter == null) {
            logger.error("SRT路径转义后为空");
            return false;
        }

        // 创建输出目录（若不存在）
        File outputFile = new File(escapedOutput);
        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                logger.error("创建输出目录失败：{}", outputDir.getAbsolutePath());
                return false;
            }
            logger.info("已创建输出目录：{}", outputDir.getAbsolutePath());
        }

        // 构建FFmpeg压制字幕命令
        List<String> command = new ArrayList<>(Arrays.asList(
                "ffmpeg",
                "-i", escapedVideo,
                "-vf", "subtitles=" + srtPathForFilter,  // 指定字幕滤镜及路径
                "-c:v", "libx264",                       // H.264视频编码
                "-c:a", "aac",                           // AAC音频编码
                "-strict", "experimental",               // 允许实验性编码器
                "-y",
                escapedOutput
        ));

        logger.info("执行FFmpeg字幕压制命令：{}", String.join(" ", command));

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // 读取并记录FFmpeg完整输出
            StringBuilder ffmpegOutputBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ffmpegOutputBuilder.append(line).append(System.lineSeparator());
                }
            }
            String ffmpegOutput = ffmpegOutputBuilder.toString();
            logger.info("FFmpeg字幕压制输出:\n{}", ffmpegOutput);

            // 等待进程完成（超时30分钟）
            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            int exitCode;

            if (!finished) {
                logger.error("FFmpeg进程超时，强制终止");
                process.destroyForcibly();
                exitCode = process.waitFor();
                logger.error("强制终止后退出码：{}", exitCode);
                return false;
            }

            exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.error("FFmpeg压制失败，退出码 {}，命令：{}", exitCode, String.join(" ", command));
                // 检查输出文件是否存在或为空
                File createdOutputFile = new File(escapedOutput);
                if (!createdOutputFile.exists() || createdOutputFile.length() == 0) {
                    logger.error("输出文件未创建或为空：{}", escapedOutput);
                }
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            logger.error("FFmpeg进程被中断，命令：{}", String.join(" ", command), e);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            logger.error("FFmpeg压制异常，命令：{}", String.join(" ", command), e);
            return false;
        }
    }
}