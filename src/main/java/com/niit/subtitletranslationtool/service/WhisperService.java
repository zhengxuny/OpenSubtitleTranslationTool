package com.niit.subtitletranslationtool.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class WhisperService {
    private final Logger logger = LoggerFactory.getLogger(WhisperService.class);
    private final Path executable;
    private final String model;
    private final String device;
    private final Path outputDir;
    private final boolean vadFilter;
    private final int timeoutMultiplier;

    public WhisperService(
            @Value("${whisper.executable-path}") String executablePath,
            @Value("${whisper.model}") String model,
            @Value("${whisper.device}") String device,
            @Value("${whisper.output-dir}") String outputDir,
            @Value("${whisper.vad-filter}") boolean vadFilter,
            @Value("${whisper.timeout-multiplier}") int timeoutMultiplier) {
        this.executable = Paths.get(executablePath);
        this.model = model;
        this.device = device;
        this.outputDir = Paths.get(outputDir);
        this.vadFilter = vadFilter;
        this.timeoutMultiplier = timeoutMultiplier;
        initOutputDir();
    }

    private void initOutputDir() {
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new RuntimeException("初始化Whisper输出目录失败", e);
        }
    }

    public TranscriptionResult transcribe(String audioFilePath) throws Exception {
        Path audioPath = Paths.get(audioFilePath);
        if (!Files.exists(audioPath)) {
            logger.error("音频文件不存在，路径：{}", audioPath); // 明确错误路径
            throw new IllegalArgumentException("音频文件不存在: " + audioPath);
        }

        // 记录关键配置参数（DEBUG级别）
        logger.debug("=== Whisper 转文字配置参数 ===");
        logger.debug("可执行文件路径: {}", executable);
        logger.debug("使用模型: {}", model);
        logger.debug("计算设备: {}", device);
        logger.debug("输出目录: {}", outputDir);
        logger.debug("VAD过滤启用: {}", vadFilter);
        logger.debug("超时倍数: {}", timeoutMultiplier);

        // 构建命令参数
        List<String> command = new ArrayList<>(List.of(
                executable.toString(),
                "--model", model,
                "--device", device,
                "--output_dir", outputDir.toString(),
                "--output_format", "srt"
        ));
        if (vadFilter) {
            command.add("--vad_filter");
            command.add("true");  // 显式传递布尔值
        } else {
            command.add("--vad_filter");
            command.add("false");
        }
        command.add(audioPath.toString());  // 音频路径作为最后一个参数

        // 记录完整执行命令（DEBUG级别）
        String commandStr = String.join(" ", command);
        logger.debug("=== 即将执行的Whisper命令 ===");
        logger.debug("{}", commandStr);

        // 执行命令（原有逻辑保持不变）
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        // 读取输出（用于日志）
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        logger.debug("=== Whisper执行输出 ===");
        logger.debug("{}", output);  // 记录工具原始输出


        // 计算超时时间（假设音频时长通过FFmpeg获取，此处简化为60秒）
        long timeout = Duration.ofSeconds(60 * timeoutMultiplier).toMillis();
        if (!process.waitFor(timeout, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new RuntimeException("Whisper转录超时，音频路径: " + audioPath);
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("Whisper执行失败，退出码: " + process.exitValue() + "，输出: " + output);
        }

        // 验证SRT文件
        String srtFilename = audioPath.getFileName().toString().replaceFirst("\\.[^.]+$", ".srt");
        Path srtPath = outputDir.resolve(srtFilename);
        if (!Files.exists(srtPath) || Files.size(srtPath) == 0) {
            throw new RuntimeException("未生成有效SRT文件: " + srtPath);
        }

        return new TranscriptionResult(srtFilename, srtPath.toString());
    }

    @Data
    @AllArgsConstructor
    public static class TranscriptionResult {
        private String srtFilename;
        private String srtFilePath;
    }
}