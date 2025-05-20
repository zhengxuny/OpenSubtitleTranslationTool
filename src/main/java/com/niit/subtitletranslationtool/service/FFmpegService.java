package com.niit.subtitletranslationtool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
}