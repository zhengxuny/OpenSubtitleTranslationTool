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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
     * 为FFmpeg滤镜参数转义Windows路径中的冒号。
     * 例如 "C:/path/to/file.srt" 转换为 "C\\:/path/to/file.srt"
     * @param path 原始路径
     * @return 转义后的路径
     */
    private String escapePathForFFmpegFilter(String path) {
        if (path == null) {
            return null;
        }
        // 仅当路径以 "盘符:" 开头时 (例如 C:/ D:/)
        // Pattern to match a windows drive letter followed by a colon and a slash
        // e.g., C:/, D:\
        Pattern windowsPathPattern = Pattern.compile("^([a-zA-Z]):([/\\\\])");
        Matcher matcher = windowsPathPattern.matcher(path);
        if (matcher.find()) {
            // Replace "C:/" with "C\\:/" or "C:\" with "C\\:\"
            // The (/) or (\) is captured in group 2, so we re-add it.
            return matcher.group(1) + "\\\\:" + matcher.group(2) + path.substring(matcher.end());
        }
        // Also escape other special characters if necessary for filtergraphs,
        // like single quotes, backslashes, commas, brackets.
        // For now, focusing on the colon which is the primary issue.
        // path = path.replace("\\", "\\\\"); // Escape backslashes
        // path = path.replace("'", "\\'");   // Escape single quotes
        // path = path.replace(",", "\\,");   // Escape commas
        // path = path.replace("[", "\\[");  // Escape open bracket
        // path = path.replace("]", "\\]");  // Escape close bracket
        return path;
    }

    /**
     * 压制字幕到视频
     * @param videoPath 原始视频路径（绝对路径）
     * @param srtPath 翻译后的SRT路径（绝对路径）
     * @param outputPath 输出视频路径（绝对路径）
     * @return 成功状态
     */
    public boolean burnSubtitles(String videoPath, String srtPath, String outputPath) {
        // 1. 路径规范化：反斜杠替换为正斜杠
        String escapedVideo = videoPath.replace("\\", "/");
        String escapedSrt = srtPath.replace("\\", "/");
        String escapedOutput = outputPath.replace("\\", "/");

        // 2. 为FFmpeg滤镜中的SRT路径特殊转义 (主要是Windows盘符冒号)
        String srtPathForFilter = escapePathForFFmpegFilter(escapedSrt);
        if (srtPathForFilter == null) {
            logger.error("SRT path for filter is null after escaping.");
            return false;
        }

        // 3. 确保输出目录存在
        File outputFile = new File(escapedOutput);
        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                logger.error("Failed to create output directory: {}", outputDir.getAbsolutePath());
                return false;
            }
            logger.info("Created output directory: {}", outputDir.getAbsolutePath());
        }


        List<String> command = new ArrayList<>(Arrays.asList(
                "ffmpeg",
                "-i", escapedVideo,
                "-vf", "subtitles=" + srtPathForFilter, // 使用转义后的SRT路径
                "-c:v", "libx264", // 指定视频编码器 (可选, 但有时能避免编码问题)
                "-c:a", "aac",     // 指定音频编码器 (可选)
                "-strict", "experimental", // 可能需要，取决于FFmpeg版本和字幕样式
                "-y",  // 覆盖已存在文件
                escapedOutput
        ));

        // 如果你的FFmpeg版本较新，可能需要指定字体，特别是处理中文字幕时
        // command.add("-fontfile");
        // command.add("C:/Windows/Fonts/msyh.ttc"); // 示例：微软雅黑字体路径，根据实际情况修改

        logger.info("Executing FFmpeg command for subtitle burning: {}", String.join(" ", command));

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true); // 合并 stdout 和 stderr
            Process process = processBuilder.start();

            StringBuilder ffmpegOutputBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ffmpegOutputBuilder.append(line).append(System.lineSeparator());
                    // 可以选择性地实时打印日志，或者等待结束后一起打印
                    // logger.debug("FFMPEG_LOG: {}", line);
                }
            }
            String ffmpegOutput = ffmpegOutputBuilder.toString();
            logger.info("FFmpeg subtitle burning output: \n{}", ffmpegOutput);

            boolean finished = process.waitFor(30, TimeUnit.MINUTES); // 等待30分钟
            int exitCode;

            if (!finished) {
                logger.error("FFmpeg subtitle burning process timed out after 30 minutes. Destroying forcibly.");
                process.destroyForcibly(); // 强制结束
                exitCode = process.waitFor(); // 获取强制结束后的退出码
                logger.error("FFmpeg process exit code after forcible destruction: {}", exitCode);
                return false;
            }

            exitCode = process.exitValue();
            logger.info("FFmpeg subtitle burning process finished: {}, Exit code: {}", finished, exitCode);


            if (exitCode != 0) {
                logger.error("FFmpeg subtitle burning failed with exit code {}. Command: {}", exitCode, String.join(" ", command));
                // 额外检查输出文件是否存在且有效
                File createdOutputFile = new File(escapedOutput);
                if (!createdOutputFile.exists() || createdOutputFile.length() == 0) {
                    logger.error("Output file was not created or is empty: {}", escapedOutput);
                }
                return false;
            }

            return true;
        } catch (InterruptedException e) {
            logger.error("FFmpeg subtitle burning process was interrupted. Command: {}", String.join(" ", command), e);
            Thread.currentThread().interrupt(); // Restore interrupted status
            return false;
        } catch (Exception e) {
            logger.error("FFmpeg subtitle burning failed due to an exception. Command: {}", String.join(" ", command), e);
            return false;
        }
    }
}