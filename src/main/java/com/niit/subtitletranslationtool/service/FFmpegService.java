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


/**
 * FFmpeg服务类
 * 该类封装了与FFmpeg命令行工具的交互，提供了诸如提取音轨、检查视频完整性、
 * 以及将字幕压制到视频等功能。
 * 注意：本服务依赖于系统中已正确安装FFmpeg，并且其可执行文件路径已添加到系统环境变量中，
 * 或者在构建命令时使用FFmpeg的绝对路径。
 */
@Service // Spring注解，将该类标记为一个服务组件，使其可以被Spring容器管理和依赖注入
public class FFmpegService {
    // 创建一个静态的、最终的Logger实例，用于记录该类中的日志信息
    // LoggerFactory.getLogger(FFmpegService.class) 会获取一个与FFmpegService类关联的Logger
    private static final Logger logger = LoggerFactory.getLogger(FFmpegService.class);

    /**
     * 使用FFmpeg从视频中提取音轨，并将其保存为MP3格式。
     *
     * @param videoPath       输入视频文件的完整路径。
     * @param audioOutputPath 输出MP3音频文件的完整路径。
     * @return 如果音轨提取成功，则返回true；否则返回false。
     */
    public boolean extractAudio(String videoPath, String audioOutputPath) {
        // 记录方法开始执行的日志，包含输入的视频路径和期望的音频输出路径
        logger.info("开始提取音轨，视频路径：{}，输出路径：{}", videoPath, audioOutputPath);

        // 检查输入的视频文件是否存在于文件系统中
        if (!Files.exists(Path.of(videoPath))) {
            // 如果视频文件不存在，记录错误日志并返回false
            logger.error("视频文件不存在：{}", videoPath);
            return false;
        }

        // 获取输出音频文件的父目录路径
        Path outputDir = Path.of(audioOutputPath).getParent();
        // 检查父目录是否存在，如果不存在，则尝试创建它
        if (outputDir != null && !Files.exists(outputDir)) {
            try {
                // 创建所有不存在的父目录
                Files.createDirectories(outputDir);
            } catch (IOException e) {
                // 如果创建目录失败，记录错误日志并返回false
                logger.error("创建输出目录失败：{}", outputDir, e);
                return false;
            }
        }

        // 构建FFmpeg命令行参数列表
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");          // FFmpeg可执行文件的名称。系统需要能找到它（通常通过PATH环境变量）。
        command.add("-i");              // FFmpeg参数，表示接下来的是输入文件。
        command.add(videoPath);         // 输入视频文件的路径。
        command.add("-vn");             // FFmpeg参数，表示禁用视频录制/输出，即只处理音频。
        command.add("-acodec");         // FFmpeg参数，表示指定音频编解码器。
        command.add("libmp3lame");      // 指定使用LAME MP3编码器来编码音频。
        command.add("-ab");             // FFmpeg参数，表示设置音频比特率。
        command.add("192k");            // 设置音频比特率为192kbps，这是一个常见的MP3音质。
        command.add("-y");              // FFmpeg参数，表示如果输出文件已存在，则自动覆盖它，不询问。
        command.add(audioOutputPath);   // 输出音频文件的路径。

        // 创建ProcessBuilder实例，用于执行外部命令
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        // 将错误流（stderr）重定向到标准输出流（stdout），这样可以一起读取所有输出
        processBuilder.redirectErrorStream(true);

        try {
            // 启动FFmpeg进程
            Process process = processBuilder.start();

            // 使用try-with-resources语句确保BufferedReader在用完后能被正确关闭
            // 创建一个BufferedReader来读取FFmpeg进程的输出信息
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line; // 用于存储从FFmpeg输出中读取的每一行
                // 循环读取FFmpeg的输出，直到没有更多行为止
                while ((line = reader.readLine()) != null) {
                    // 将FFmpeg的每一行输出记录为调试信息，有助于排查问题
                    logger.debug("FFmpeg输出: {}", line);
                }
            }

            // 等待FFmpeg进程执行完成，并获取其退出码
            // 退出码为0通常表示成功，非0表示出现错误
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                // 如果退出码为0，表示音轨提取成功
                logger.info("音轨提取成功，输出路径：{}", audioOutputPath);
                return true; // 返回true表示成功
            } else {
                // 如果退出码非0，表示FFmpeg执行失败
                logger.error("FFmpeg执行失败，退出码：{}", exitCode);
                return false; // 返回false表示失败
            }
        } catch (IOException | InterruptedException e) {
            // 捕获执行过程中可能发生的IO异常或中断异常
            logger.error("执行FFmpeg命令时发生异常", e);
            // 如果当前线程被中断，恢复中断状态
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false; // 返回false表示发生异常导致失败
        }
    }

    /**
     * 检测视频文件是否完整（即FFmpeg是否能够无错误地解析它）。
     * 这通常用于初步判断视频文件是否损坏。
     *
     * @param videoFilePath 待检测视频文件的完整路径。
     * @return 如果视频文件被FFmpeg认为是完整的（命令执行成功，退出码为0），则返回true；
     *         如果FFmpeg在处理时报告错误（退出码非0），则认为视频可能损坏或存在问题，返回false。
     * @throws IOException          如果启动进程或读取输出时发生IO错误。
     * @throws InterruptedException 如果等待进程完成时线程被中断。
     */
    public boolean checkVideoIntegrity(String videoFilePath) throws IOException, InterruptedException {
        // 构建FFmpeg命令行参数列表，用于检测视频完整性
        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg",                     // FFmpeg可执行文件。
                "-v", "error",                // FFmpeg参数，设置日志级别为error，只输出错误信息。
                "-i", videoFilePath,          // FFmpeg参数，指定输入视频文件路径。
                "-f", "null",                 // FFmpeg参数，指定输出格式为null，表示不生成任何输出文件，仅进行解码和分析。
                "-"                           // FFmpeg参数，表示输出到标准输出（在这个场景下，由于-f null，实际无文件输出，但命令需要一个输出目标）。
        );
        // 将错误流重定向到标准输出流，以便统一捕获FFmpeg的所有输出（尤其是错误信息）
        processBuilder.redirectErrorStream(true);
        // 启动FFmpeg进程
        Process process = processBuilder.start();

        // 读取FFmpeg进程的所有输出（包括错误信息），并将其转换为UTF-8编码的字符串
        // 这对于记录FFmpeg的具体错误信息非常有用
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        // 等待FFmpeg进程执行完成，并获取其退出码
        int exitCode = process.waitFor();

        // 检查退出码。如果FFmpeg成功处理（退出码为0），说明视频文件在FFmpeg看来是可读且结构完整的。
        if (exitCode != 0) {
            // 如果退出码非0，表示FFmpeg在处理视频时遇到了错误。
            // 这可能意味着视频文件损坏，或者FFmpeg命令本身有问题。
            logger.error("视频完整性检测失败，文件路径：{}，错误输出：{}", videoFilePath, output);
            return false; // 返回false，表示视频完整性检查未通过
        }
        // 如果退出码为0，表示视频完整性检查通过
        return true;
    }

    /**
     * 为FFmpeg的滤镜参数（filtergraph）转义Windows路径中的冒号。
     * 在FFmpeg的滤镜语法中，冒号有特殊含义，因此Windows路径中的驱动器号后的冒号（如 "C:"）需要被转义。
     * 例如，"C:/path/to/file.srt" 会被转换为 "C\\:/path/to/file.srt"。
     *
     * @param path 原始文件路径字符串。
     * @return 转义后的路径字符串，如果输入为null则返回null。
     */
    private String escapePathForFFmpegFilter(String path) {
        // 如果输入路径为null，直接返回null
        if (path == null) {
            return null;
        }
        // 定义一个正则表达式模式，用于匹配Windows驱动器号后跟冒号和路径分隔符（正斜杠或反斜杠）的模式
        // 例如：C:/, D:\
        // - `^`: 匹配字符串的开始
        // - `([a-zA-Z])`: 捕获组1，匹配一个英文字母（驱动器号）
        // - `:`: 匹配冒号
        // - `([/\\\\])`: 捕获组2，匹配一个正斜杠'/'或一个反斜杠'\' (反斜杠在Java字符串和Regex中都需要转义，所以是\\\\)
        Pattern windowsPathPattern = Pattern.compile("^([a-zA-Z]):([/\\\\])");
        // 使用模式匹配输入的路径
        Matcher matcher = windowsPathPattern.matcher(path);

        // 如果路径匹配了Windows驱动器号模式
        if (matcher.find()) {
            // 进行替换：将 "C:/" 替换为 "C\\:/" 或 "C:\" 替换为 "C\\:\"
            // - `matcher.group(1)`: 获取驱动器号 (例如 "C")
            // - `"\\\\:"`: 添加转义后的冒号 (在Java字符串中 `\\\\` 代表两个反斜杠 `\\`, FFmpeg会将其解释为一个反斜杠 `\` 和一个冒号 `:`)
            // - `matcher.group(2)`: 获取原始的路径分隔符 ('/' 或 '\')
            // - `path.substring(matcher.end())`: 获取原始路径中匹配部分之后剩余的子字符串
            return matcher.group(1) + "\\\\:" + matcher.group(2) + path.substring(matcher.end());
        }

        // 注意：以下是关于其他可能需要为FFmpeg滤镜转义的特殊字符的注释。
        // 目前代码主要关注冒号的转义，因为这是Windows路径在FFmpeg滤镜中最常见的问题。
        // 如果需要，可以取消注释并调整以下代码来转义更多字符：
        // path = path.replace("\\", "\\\\"); // 转义反斜杠 (例如，路径中的 '\' 变为 '\\')
        // path = path.replace("'", "\\'");   // 转义单引号
        // path = path.replace(",", "\\,");   // 转义逗号
        // path = path.replace("[", "\\[");  // 转义左方括号
        // path = path.replace("]", "\\]");  // 转义右方括号

        // 如果路径不是Windows驱动器号开头的路径，或者不需要转义，则返回原始路径
        return path;
    }

    /**
     * 将SRT字幕文件硬编码（压制）到视频中。
     *
     * @param videoPath  原始视频文件的绝对路径。
     * @param srtPath    翻译后的SRT字幕文件的绝对路径。
     * @param outputPath 输出视频文件（包含字幕）的绝对路径。
     * @return 如果字幕压制成功，则返回true；否则返回false。
     */
    public boolean burnSubtitles(String videoPath, String srtPath, String outputPath) {
        // 1. 路径规范化：将所有路径中的反斜杠（\）替换为正斜杠（/）。
        //    FFmpeg在很多情况下对正斜杠的路径有更好的兼容性，尤其是在跨平台或复杂滤镜参数中。
        String escapedVideo = videoPath.replace("\\", "/");
        String escapedSrt = srtPath.replace("\\", "/");
        String escapedOutput = outputPath.replace("\\", "/");

        // 2. 为FFmpeg滤镜中的SRT路径进行特殊转义。
        //    主要是处理Windows路径中驱动器号后的冒号，例如 "C:/file.srt" -> "C\\:/file.srt"。
        String srtPathForFilter = escapePathForFFmpegFilter(escapedSrt);
        // 如果转义后的SRT路径为null（例如，原始srtPath为null），则记录错误并返回失败。
        if (srtPathForFilter == null) {
            logger.error("用于滤镜的SRT路径在转义后为空。");
            return false;
        }

        // 3. 确保输出目录存在。
        File outputFile = new File(escapedOutput); // 创建输出文件的File对象
        File outputDir = outputFile.getParentFile(); // 获取输出文件的父目录
        // 如果父目录存在且不为null
        if (outputDir != null && !outputDir.exists()) {
            // 如果父目录不存在，则尝试创建它（包括所有必需的父目录）
            if (!outputDir.mkdirs()) {
                // 如果创建目录失败，记录错误并返回失败
                logger.error("创建输出目录失败：{}", outputDir.getAbsolutePath());
                return false;
            }
            // 记录成功创建输出目录的日志
            logger.info("已创建输出目录：{}", outputDir.getAbsolutePath());
        }

        // 构建FFmpeg命令行参数列表
        List<String> command = new ArrayList<>(Arrays.asList(
                "ffmpeg",                     // FFmpeg可执行文件
                "-i", escapedVideo,           // 输入视频文件路径 (已规范化)
                // "-vf" 指定视频滤镜。 "subtitles=" 是用于加载字幕文件的滤镜。
                // srtPathForFilter 是经过特殊转义的SRT文件路径。
                // 注意：文件名参数在这里需要仔细处理，特别是包含特殊字符或空格时。
                // FFmpeg对滤镜参数中的文件名有严格的转义要求。
                "-vf", "subtitles=" + srtPathForFilter,
                "-c:v", "libx264",            // 指定视频编码器为libx264 (H.264编码)。这是一个高质量且广泛兼容的编码器。
                // （可选，但有时能避免编码问题或获得更好的压缩/质量）
                "-c:a", "aac",                // 指定音频编码器为aac。这也是一个常用且兼容性好的音频编码器。
                // （可选）
                "-strict", "experimental",    // FFmpeg参数，允许使用实验性编解码器或功能。对于某些版本的AAC编码器可能需要。
                "-y",                         // 如果输出文件已存在，则自动覆盖。
                escapedOutput                 // 输出视频文件路径 (已规范化)
        ));

        // 针对中文字幕的提示：
        // 如果你的FFmpeg版本较新，或者在处理特定字体样式的中文字幕时遇到问题，
        // 可能需要明确指定一个字体文件。取消以下行的注释并修改字体路径。
        // command.add("-fontfile");
        // command.add("C:/Windows/Fonts/msyh.ttc"); // 示例：使用微软雅黑字体，路径需根据实际系统调整。

        // 记录将要执行的FFmpeg命令，方便调试
        logger.info("执行FFmpeg字幕压制命令：{}", String.join(" ", command));

        try {
            // 创建ProcessBuilder实例
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            // 合并标准输出流和标准错误流，这样可以从一个流中读取所有FFmpeg的输出
            processBuilder.redirectErrorStream(true);
            // 启动FFmpeg进程
            Process process = processBuilder.start();

            // 用于构建FFmpeg完整输出的字符串构建器
            StringBuilder ffmpegOutputBuilder = new StringBuilder();
            // 使用try-with-resources确保BufferedReader被关闭
            // 以UTF-8编码读取FFmpeg的输出流
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line; // 存储每行输出
                // 逐行读取FFmpeg的输出
                while ((line = reader.readLine()) != null) {
                    ffmpegOutputBuilder.append(line).append(System.lineSeparator()); // 将行添加到构建器，并附加系统换行符
                    // 可以选择性地实时打印FFmpeg日志，用于调试
                    // logger.debug("FFMPEG_LOG: {}", line);
                }
            }
            // 将收集到的FFmpeg输出完整记录下来
            String ffmpegOutput = ffmpegOutputBuilder.toString();
            logger.info("FFmpeg字幕压制输出: \n{}", ffmpegOutput);

            // 等待FFmpeg进程完成，但设置了超时时间（例如30分钟）
            // 这可以防止因FFmpeg卡死而导致程序无限期等待
            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            int exitCode; // 用于存储FFmpeg进程的退出码

            // 如果进程在超时时间内没有完成
            if (!finished) {
                logger.error("FFmpeg字幕压制进程在30分钟后超时。正在强制销毁...");
                process.destroyForcibly(); // 强制结束进程
                exitCode = process.waitFor(); // 获取强制结束后进程的退出码
                logger.error("FFmpeg进程强制销毁后的退出码：{}", exitCode);
                return false; // 返回失败
            }

            // 如果进程正常结束（在超时前），获取其退出码
            exitCode = process.exitValue();
            logger.info("FFmpeg字幕压制进程已完成：{}，退出码：{}", finished, exitCode);

            // 检查退出码。0通常表示成功。
            if (exitCode != 0) {
                logger.error("FFmpeg字幕压制失败，退出码 {}。命令：{}", exitCode, String.join(" ", command));
                // 额外检查：如果FFmpeg失败，输出文件可能未创建或为空
                File createdOutputFile = new File(escapedOutput);
                if (!createdOutputFile.exists() || createdOutputFile.length() == 0) {
                    logger.error("输出文件未创建或为空：{}", escapedOutput);
                }
                return false; // 返回失败
            }

            // 如果退出码为0，表示成功
            return true;
        } catch (InterruptedException e) {
            // 如果等待进程时当前线程被中断
            logger.error("FFmpeg字幕压制进程被中断。命令：{}", String.join(" ", command), e);
            Thread.currentThread().interrupt(); // 恢复中断状态，这是处理InterruptedException的良好实践
            return false; // 返回失败
        } catch (Exception e) {
            // 捕获其他任何在执行过程中可能发生的异常
            logger.error("FFmpeg字幕压制因异常失败。命令：{}", String.join(" ", command), e);
            return false; // 返回失败
        }
    }
}