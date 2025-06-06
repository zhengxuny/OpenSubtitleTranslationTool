package com.niit.subtitletranslationtool.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * 负责与Whisper命令行工具交互的服务类，提供音频转录并生成SRT字幕文件的核心功能。
 * 支持配置模型、计算设备、输出目录等参数，并处理进程执行、超时控制及结果验证。
 */
@Service
public class WhisperService {
    private final Logger logger = LoggerFactory.getLogger(WhisperService.class);
    private final Path executable;
    private final String model;
    private final String device;
    private final Path outputDir;
    private final boolean vadFilter;
    private final int timeoutMultiplier;

    /**
     * 通过配置参数初始化Whisper服务实例。
     *
     * @param executablePath    Whisper可执行文件的绝对路径
     * @param model             用于转录的Whisper模型名称（如"base"、"large"）
     * @param device            计算设备（如"cpu"或"cuda"）
     * @param outputDir         字幕文件输出目录路径
     * @param vadFilter         是否启用语音活动检测（VAD）过滤
     * @param timeoutMultiplier 超时时间倍数（实际超时=60秒×倍数）
     */
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
        initOutputDir(); // 初始化输出目录（若不存在则创建）
    }

    /**
     * 初始化输出目录（私有工具方法）。
     * 若目录不存在则递归创建，创建失败时抛出运行时异常。
     */
    private void initOutputDir() {
        try {
            Files.createDirectories(outputDir); // 创建目录（已存在时无操作）
        } catch (IOException e) {
            throw new RuntimeException("初始化Whisper输出目录失败", e);
        }
    }

    /**
     * 执行音频转录并生成SRT字幕文件。
     *
     * @param audioFilePath 待转录的音频文件绝对路径
     * @return 转录结果对象，包含生成的SRT文件名及完整路径
     * @throws IllegalArgumentException 输入音频文件不存在时抛出
     * @throws RuntimeException 转录超时或未生成有效SRT文件时抛出
     * @throws Exception 进程执行过程中发生的其他异常
     */
    public TranscriptionResult transcribe(String audioFilePath) throws Exception {
        Path audioPath = Paths.get(audioFilePath);
        // 验证输入音频文件是否存在
        if (!Files.exists(audioPath)) {
            logger.error("音频文件不存在，路径：{}", audioPath);
            throw new IllegalArgumentException("音频文件不存在: " + audioPath);
        }

        // 记录调试级配置参数
        logger.debug("=== Whisper 转文字配置参数 ===");
        logger.debug("可执行文件路径: {}", executable);
        logger.debug("使用模型: {}", model);
        logger.debug("计算设备: {}", device);
        logger.debug("输出目录: {}", outputDir);
        logger.debug("VAD过滤启用: {}", vadFilter);
        logger.debug("超时倍数: {}", timeoutMultiplier);

        // 构建Whisper命令行参数列表
        List<String> command = new ArrayList<>(List.of(
                executable.toString(),
                "--model", model,
                "--device", device,
                "--output_dir", outputDir.toString(),
                "--output_format", "srt"
        ));
        // 根据配置启用或禁用VAD过滤参数
        command.add("--vad_filter");
        command.add(vadFilter ? "true" : "false");
        command.add(audioPath.toString());  // 音频路径作为最后一个参数

        // 记录完整执行命令（调试级）
        String commandStr = String.join(" ", command);
        logger.debug("=== 即将执行的Whisper命令 ===");
        logger.debug("{}", commandStr);

        // 启动Whisper进程并重定向错误流
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        // 读取进程标准输出用于日志记录
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        logger.debug("=== Whisper执行输出 ===");
        logger.debug("{}", output);

        // 计算超时时间（60秒×倍数）并等待进程完成
        long timeout = Duration.ofSeconds(60L * timeoutMultiplier).toMillis();
        if (!process.waitFor(timeout, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new RuntimeException("Whisper转录超时，音频路径: " + audioPath);
        }

        // 生成并验证SRT文件有效性
        String srtFilename = audioPath.getFileName().toString().replaceFirst("\\.[^.]+$", ".srt");
        Path srtPath = outputDir.resolve(srtFilename);
        // 验证生成的SRT文件是否有效（存在且非空）
        if (!Files.exists(srtPath) || Files.size(srtPath) == 0) {
            throw new RuntimeException("未生成有效SRT文件: " + srtPath);
        }

        return new TranscriptionResult(srtFilename, srtPath.toString());
    }

    /**
     * 封装转录结果的内部类，包含生成的SRT文件名及完整路径。
     */
    @Data
    @AllArgsConstructor
    public static class TranscriptionResult {
        private String srtFilename;
        private String srtFilePath;
    }
}