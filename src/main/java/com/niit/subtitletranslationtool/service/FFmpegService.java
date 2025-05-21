package com.niit.subtitletranslationtool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

@Service
public class FFmpegService {
    private static final Logger logger = LoggerFactory.getLogger(FFmpegService.class);

    /**
     * 使用FFmpeg从视频中提取音轨为MP3
     * @param videoPath 输入视频完整路径
     * @param audioOutputPath 输出音频完整路径
     * @return 提取成功返回true
     */
    public boolean extractAudio(String videoPath, String audioOutputPath) {
        logger.info("开始提取音轨，视频路径：{}，输出路径：{}", videoPath, audioOutputPath);

        // 检查输入文件是否存在
        if (!Files.exists(Path.of(videoPath))) {
            logger.error("视频文件不存在：{}", videoPath);
            return false;
        }

        // 创建输出目录（如果不存在）
        Path outputDir = Path.of(audioOutputPath).getParent();
        if (outputDir != null && !Files.exists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
            } catch (IOException e) {
                logger.error("创建输出目录失败：{}", outputDir, e);
                return false;
            }
        }

        // 构建FFmpeg命令
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");          // FFmpeg可执行文件路径（需确保环境变量中存在或使用绝对路径）
        command.add("-i");              // 输入文件
        command.add(videoPath);
        command.add("-vn");             // 禁用视频流
        command.add("-acodec");         // 音频编码器
        command.add("libmp3lame");
        command.add("-ab");             // 音频比特率
        command.add("192k");
        command.add("-y");              // 覆盖已存在的输出文件
        command.add(audioOutputPath);

        // 执行命令并捕获输出
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true); // 合并错误流和输出流

        try {
            Process process = processBuilder.start();
            // 读取命令输出（用于调试和错误诊断）
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("FFmpeg输出: {}", line);
                }
            }

            // 等待命令执行完成
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
            return false;
        }
    }
    /**
     * 检测视频文件是否完整（无损坏）
     * @param videoFilePath 视频文件完整路径
     * @return 完整返回true，损坏返回false
     */
    public boolean checkVideoIntegrity(String videoFilePath) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg",                     // 需确保系统已安装ffmpeg并配置环境变量
                "-v", "error",                // 仅输出错误日志
                "-i", videoFilePath,          // 输入视频路径
                "-f", "null",                 // 输出格式为null（不生成文件）
                "-"                           // 输出到空设备
        );
        processBuilder.redirectErrorStream(true); // 合并错误流和标准流
        Process process = processBuilder.start();

        // 读取命令输出（用于日志记录）
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor(); // 等待命令执行完成

        if (exitCode != 0) {
            logger.error("视频完整性检测失败，文件路径：{}，错误输出：{}", videoFilePath, output);
            return false;
        }
        return true;
    }

    /**
     * 压制字幕到视频
     * @param videoPath 原始视频路径
     * @param srtPath 翻译后的SRT路径
     * @param outputPath 输出视频路径
     * @return 成功状态
     */
    public boolean burnSubtitles(String videoPath, String srtPath, String outputPath) {
        // 处理Windows路径转义（替换反斜杠）
        String escapedSrt = srtPath.replace("\\", "/");
        String escapedVideo = videoPath.replace("\\", "/");

        List<String> command = Arrays.asList(
                "ffmpeg",
                "-i", escapedVideo,
                "-vf", "subtitles=" + escapedSrt,  // ffmpeg字幕滤镜
                "-y",  // 覆盖已存在文件
                outputPath.replace("\\", "/")
        );

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            // 等待处理完成（设置合理超时，如30分钟）
            boolean success = process.waitFor(30, TimeUnit.MINUTES);
            return success && process.exitValue() == 0;
        } catch (Exception e) {
            logger.error("字幕压制失败: {}", command, e);
            return false;
        }
    }
}