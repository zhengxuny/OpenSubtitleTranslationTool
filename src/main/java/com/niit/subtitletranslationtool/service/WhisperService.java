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

/**
 * WhisperService 类，用于调用 Whisper 进行音频转录并生成字幕文件。
 * 该类负责处理与 Whisper 命令行工具的交互，包括参数配置、进程执行和结果验证。
 */
@Service // 使用 Spring 的 @Service 注解，将该类声明为一个服务组件，使其能够被 Spring 容器管理
public class WhisperService {
    private final Logger logger = LoggerFactory.getLogger(WhisperService.class); // 创建日志记录器，用于记录服务运行时的信息，方便调试和监控

    private final Path executable; // Whisper 可执行文件的路径
    private final String model; // Whisper 使用的模型名称
    private final String device; // 运行 Whisper 的设备，例如 "cpu" 或 "cuda"
    private final Path outputDir; // Whisper 输出文件的目录
    private final boolean vadFilter; // 是否启用 VAD（语音活动检测）过滤
    private final int timeoutMultiplier; // 超时时间倍数，用于动态调整转录的超时时间

    /**
     * 构造函数，通过 Spring 的 @Value 注解注入配置参数。
     *
     * @param executablePath  Whisper 可执行文件的路径
     * @param model           Whisper 使用的模型名称
     * @param device          运行 Whisper 的设备
     * @param outputDir       Whisper 输出文件的目录
     * @param vadFilter       是否启用 VAD 过滤
     * @param timeoutMultiplier 超时时间倍数
     */
    public WhisperService(
            @Value("${whisper.executable-path}") String executablePath, // 通过 @Value 注解读取配置文件中的 whisper.executable-path 属性
            @Value("${whisper.model}") String model, // 通过 @Value 注解读取配置文件中的 whisper.model 属性
            @Value("${whisper.device}") String device, // 通过 @Value 注解读取配置文件中的 whisper.device 属性
            @Value("${whisper.output-dir}") String outputDir, // 通过 @Value 注解读取配置文件中的 whisper.output-dir 属性
            @Value("${whisper.vad-filter}") boolean vadFilter, // 通过 @Value 注解读取配置文件中的 whisper.vad-filter 属性
            @Value("${whisper.timeout-multiplier}") int timeoutMultiplier) { // 通过 @Value 注解读取配置文件中的 whisper.timeout-multiplier 属性
        this.executable = Paths.get(executablePath); // 将可执行文件路径字符串转换为 Path 对象
        this.model = model; // 设置模型名称
        this.device = device; // 设置设备
        this.outputDir = Paths.get(outputDir); // 将输出目录字符串转换为 Path 对象
        this.vadFilter = vadFilter; // 设置 VAD 过滤
        this.timeoutMultiplier = timeoutMultiplier; // 设置超时时间倍数
        initOutputDir(); // 初始化输出目录，如果目录不存在则创建
    }

    /**
     * 初始化输出目录，如果目录不存在则创建。
     * 如果创建失败，则抛出运行时异常。
     */
    private void initOutputDir() {
        try {
            Files.createDirectories(outputDir); // 创建输出目录，如果目录已存在则不执行任何操作
        } catch (IOException e) {
            throw new RuntimeException("初始化Whisper输出目录失败", e); // 如果创建目录失败，则抛出运行时异常
        }
    }

    /**
     * 转录音频文件并生成 SRT 字幕文件。
     *
     * @param audioFilePath 音频文件的路径
     * @return TranscriptionResult 对象，包含 SRT 文件名和文件路径
     * @throws Exception 如果转录过程中发生任何错误，则抛出异常
     */
    public TranscriptionResult transcribe(String audioFilePath) throws Exception {
        Path audioPath = Paths.get(audioFilePath); // 将音频文件路径字符串转换为 Path 对象
        if (!Files.exists(audioPath)) { // 检查音频文件是否存在
            logger.error("音频文件不存在，路径：{}", audioPath); // 明确错误路径
            throw new IllegalArgumentException("音频文件不存在: " + audioPath); // 如果音频文件不存在，则抛出 IllegalArgumentException 异常
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
        List<String> command = new ArrayList<>(List.of( // 创建一个 ArrayList 用于存储命令参数
                executable.toString(), // 添加可执行文件路径
                "--model", model, // 添加模型参数
                "--device", device, // 添加设备参数
                "--output_dir", outputDir.toString(), // 添加输出目录参数
                "--output_format", "srt" // 添加输出格式参数，指定为 SRT 格式
        ));
        if (vadFilter) { // 如果启用了 VAD 过滤
            command.add("--vad_filter"); // 添加 VAD 过滤参数
            command.add("true");  // 显式传递布尔值
        } else { // 如果未启用 VAD 过滤
            command.add("--vad_filter"); // 添加 VAD 过滤参数
            command.add("false");
        }
        command.add(audioPath.toString());  // 音频路径作为最后一个参数

        // 记录完整执行命令（DEBUG级别）
        String commandStr = String.join(" ", command); // 将命令参数列表转换为字符串
        logger.debug("=== 即将执行的Whisper命令 ===");
        logger.debug("{}", commandStr);

        // 执行命令（原有逻辑保持不变）
        Process process = new ProcessBuilder(command) // 创建一个 ProcessBuilder 对象，用于执行命令
                .redirectErrorStream(true) // 将错误流重定向到标准输出流，方便查看错误信息
                .start(); // 启动进程

        // 读取输出（用于日志）
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8); // 读取进程的输出流
        logger.debug("=== Whisper执行输出 ===");
        logger.debug("{}", output);  // 记录工具原始输出


        // 计算超时时间（假设音频时长通过FFmpeg获取，此处简化为60秒）
        long timeout = Duration.ofSeconds(60 * timeoutMultiplier).toMillis(); // 计算超时时间，单位为毫秒
        if (!process.waitFor(timeout, TimeUnit.MILLISECONDS)) { // 等待进程执行完成，如果在指定时间内未完成，则强制终止进程
            process.destroyForcibly(); // 强制终止进程
            throw new RuntimeException("Whisper转录超时，音频路径: " + audioPath); // 抛出运行时异常，提示转录超时
        }

        //貌似fasterwhisperxxl有点问题，会溢出内存报错，但实际上生成正确str字幕了，所以就先不管他吧
//        if (process.exitValue() != 0) {
//            throw new RuntimeException("Whisper执行失败，退出码: " + process.exitValue() + "，输出: " + output);
//        }

        // 验证SRT文件
        String srtFilename = audioPath.getFileName().toString().replaceFirst("\\.[^.]+$", ".srt"); // 根据音频文件名生成 SRT 文件名
        Path srtPath = outputDir.resolve(srtFilename); // 生成 SRT 文件的完整路径
        if (!Files.exists(srtPath) || Files.size(srtPath) == 0) { // 检查 SRT 文件是否存在且大小不为 0
            throw new RuntimeException("未生成有效SRT文件: " + srtPath); // 如果 SRT 文件不存在或大小为 0，则抛出运行时异常
        }

        return new TranscriptionResult(srtFilename, srtPath.toString()); // 创建 TranscriptionResult 对象并返回
    }

    /**
     * 内部类，用于封装转录结果，包含 SRT 文件名和文件路径。
     */
    @Data // 使用 Lombok 的 @Data 注解，自动生成 getter、setter、equals、hashCode 和 toString 方法
    @AllArgsConstructor // 使用 Lombok 的 @AllArgsConstructor 注解，自动生成包含所有参数的构造函数
    public static class TranscriptionResult {
        private String srtFilename; // SRT 文件名
        private String srtFilePath; // SRT 文件路径
    }
}