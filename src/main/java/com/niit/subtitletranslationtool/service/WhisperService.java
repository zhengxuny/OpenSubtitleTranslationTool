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
 * {@code WhisperService} 类负责与 Whisper 命令行工具交互，实现音频文件的转录并生成 SRT 字幕文件。
 * <p>
 * 该服务允许配置 Whisper 模型的选择、计算设备（CPU 或 CUDA）、输出目录等参数，
 * 并处理进程的执行、超时控制以及结果验证，确保生成有效的字幕文件。
 */
@Service
public class WhisperService {
    private final Logger logger = LoggerFactory.getLogger(WhisperService.class);
    private final Path executable; // Whisper 可执行文件的路径
    private final String model; // 使用的 Whisper 模型名称（例如 "base", "large"）
    private final String device; // 计算设备（"cpu" 或 "cuda"）
    private final Path outputDir; // 字幕文件输出目录
    private final boolean vadFilter; // 是否启用语音活动检测 (VAD) 过滤
    private final int timeoutMultiplier; // 超时时间倍数（实际超时时间 = 60秒 * 倍数）
    private final FFmpegService ffmpegService;  // 添加FFmpegService依赖

    /**
     * 构造 {@code WhisperService} 的实例。
     *
     * @param executablePath    Whisper 可执行文件的路径，通过 Spring 的 @Value 注解从配置文件中读取。
     * @param model             用于转录的 Whisper 模型名称，通过 @Value 注解从配置文件中读取。
     * @param device            计算设备类型（"cpu" 或 "cuda"），通过 @Value 注解从配置文件中读取。
     * @param outputDir         字幕文件输出目录的路径，通过 @Value 注解从配置文件中读取。
     * @param vadFilter         是否启用语音活动检测 (VAD) 过滤，通过 @Value 注解从配置文件中读取。
     *                          VAD 过滤可以移除音频中的静音部分，提高转录质量。
     * @param timeoutMultiplier 超时时间倍数，通过 @Value 注解从配置文件中读取。
     *                          用于设置转录过程的最大允许时间。
     * @param ffmpegService     FFmpegService 实例，通过 Spring 的依赖注入传入。
     */
    public WhisperService(
            @Value("${whisper.executable-path}") String executablePath,
            @Value("${whisper.model}") String model,
            @Value("${whisper.device}") String device,
            @Value("${whisper.output-dir}") String outputDir,
            @Value("${whisper.vad-filter}") boolean vadFilter,
            @Value("${whisper.timeout-multiplier}") int timeoutMultiplier,
            FFmpegService ffmpegService) {  // 添加FFmpegService参数
        this.executable = Paths.get(executablePath);
        this.model = model;
        this.device = device;
        this.outputDir = Paths.get(outputDir);
        this.vadFilter = vadFilter;
        this.timeoutMultiplier = timeoutMultiplier;
        this.ffmpegService = ffmpegService;  // 初始化FFmpegService
        initOutputDir(); // 初始化输出目录
    }

    /**
     * 初始化输出目录。如果目录不存在，则创建它。
     * <p>
     * 如果创建目录失败，会抛出一个运行时异常。
     */
    private void initOutputDir() {
        try {
            Files.createDirectories(outputDir); // 如果目录不存在，则创建它
        } catch (IOException e) {
            throw new RuntimeException("初始化 Whisper 输出目录失败", e);
        }
    }

    /**
     * 执行音频转录并生成 SRT 字幕文件。
     *
     * @param audioFilePath 待转录的音频文件的绝对路径。
     * @return {@link TranscriptionResult} 对象，包含生成的 SRT 文件名和完整路径。
     * @throws IllegalArgumentException 如果输入的音频文件不存在。
     * @throws RuntimeException         如果转录超时或未生成有效的 SRT 文件。
     * @throws Exception              如果在进程执行过程中发生其他异常。
     */
    public TranscriptionResult transcribe(String audioFilePath) throws Exception {
        Path audioPath = Paths.get(audioFilePath);

        // 验证输入音频文件是否存在
        if (!Files.exists(audioPath)) {
            logger.error("音频文件不存在，路径：{}", audioPath);
            throw new IllegalArgumentException("音频文件不存在: " + audioPath);
        }

        // 记录调试级别的配置参数
        logger.debug("=== Whisper 转文字配置参数 ===");
        logger.debug("可执行文件路径: {}", executable);
        logger.debug("使用模型: {}", model);
        logger.debug("计算设备: {}", device);
        logger.debug("输出目录: {}", outputDir);
        logger.debug("VAD 过滤启用: {}", vadFilter);
        logger.debug("超时倍数: {}", timeoutMultiplier);

        // 构建 Whisper 命令行参数列表
        List<String> command = new ArrayList<>(List.of(
                executable.toString(), // Whisper 可执行文件路径
                "--model", model, // 使用的模型
                "--device", device, // 计算设备
                "--output_dir", outputDir.toString(), // 输出目录
                "--output_format", "srt" // 输出格式为 SRT
        ));

        // 根据配置启用或禁用 VAD 过滤参数
        command.add("--vad_filter");
        command.add(vadFilter ? "true" : "false");
        command.add(audioPath.toString());  // 音频路径作为最后一个参数

        // 记录完整执行命令（调试级别）
        String commandStr = String.join(" ", command);
        logger.debug("=== 即将执行的 Whisper 命令 ===");
        logger.debug("{}", commandStr);

        // 启动 Whisper 进程并重定向错误流
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true) // 将错误流重定向到标准输出流
                .start();

        // 读取进程标准输出用于日志记录
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        logger.debug("=== Whisper 执行输出 ===");
        logger.debug("{}", output);

        // 计算超时时间（60秒 * 倍数）并等待进程完成
        long timeout = Duration.ofSeconds(60L * timeoutMultiplier).toMillis();
        if (!process.waitFor(timeout, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly(); // 强制销毁进程
            throw new RuntimeException("Whisper 转录超时，音频路径: " + audioPath);
        }

        // 生成并验证 SRT 文件有效性
        String srtFilename = audioPath.getFileName().toString().replaceFirst("\\.[^.]+$", ".srt");
        Path srtPath = outputDir.resolve(srtFilename);

        // 验证生成的 SRT 文件是否有效（存在且非空）
        if (!Files.exists(srtPath) || Files.size(srtPath) == 0) {
            throw new RuntimeException("未生成有效 SRT 文件: " + srtPath);
        }

        return new TranscriptionResult(srtFilename, srtPath.toString());
    }

    /**
     * {@code TranscriptionResult} 类封装了转录结果，包含生成的 SRT 文件名和完整路径。
     * <p>
     * 使用 Lombok 的 {@code @Data} 注解自动生成 getter、setter、equals、hashCode 和 toString 方法。
     * 使用 Lombok 的 {@code @AllArgsConstructor} 注解自动生成包含所有参数的构造方法。
     */
    @Data
    @AllArgsConstructor
    public static class TranscriptionResult {
        private String srtFilename; // 生成的 SRT 文件名
        private String srtFilePath; // 生成的 SRT 文件完整路径
    }
}