package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 提供字幕文件翻译服务，处理SRT文件的分块翻译、API调用和结果处理。
 */
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final RestTemplate restTemplate; // 用于发起HTTP请求的模板
    private final TaskMapper taskMapper; // 用于访问任务数据的Mapper接口
    private final String doubaoApiBase; // 豆包API的基础URL，从配置文件中读取
    private final String doubaoApiKey; // 豆包API的密钥，从配置文件中读取
    private final String doubaoModel; // 使用的豆包模型名称，从配置文件中读取
    private final int doubaoMaxContextLength; // 豆包API最大上下文长度，从配置文件中读取
    private final Path translatedSrtDir; // 翻译后的SRT文件存储目录
    private final ExecutorService translationExecutor = Executors.newFixedThreadPool(5); // 翻译任务的线程池，固定大小为5
    private static final int CHUNK_SIZE = 20; // 字幕分块大小，每块包含20个字幕条目（修改为20）
    private static final int MAX_RETRIES = 1; // 最大重试次数，当API调用失败时重试

    @Autowired
    private UserService userService; // 注入用户服务，用于扣费

    /**
     * 构造TranslationService服务对象。
     *
     * @param restTemplate           HTTP请求模板
     * @param taskMapper             任务数据访问接口
     * @param doubaoApiBase          豆包API基础地址
     * @param doubaoApiKey           豆包API密钥
     * @param doubaoModel            豆包模型名称
     * @param doubaoMaxContextLength API最大上下文长度
     * @param translatedSrtDir       翻译文件存储目录
     */
    @Autowired
    public TranslationService(
            RestTemplate restTemplate,
            TaskMapper taskMapper,
            @Value("${doubao.api-base}") String doubaoApiBase,
            @Value("${doubao.api-key}") String doubaoApiKey,
            @Value("${doubao.model}") String doubaoModel,
            @Value("${doubao.max-context-length}") int doubaoMaxContextLength,
            @Value("${file.translated-srt-dir}") String translatedSrtDir) {
        this.restTemplate = restTemplate;
        this.taskMapper = taskMapper;
        this.doubaoApiBase = doubaoApiBase;
        this.doubaoApiKey = doubaoApiKey;
        this.doubaoModel = doubaoModel;
        this.doubaoMaxContextLength = doubaoMaxContextLength;
        this.translatedSrtDir = Paths.get(translatedSrtDir).isAbsolute() ?
                Paths.get(translatedSrtDir) : Paths.get(System.getProperty("user.dir"), translatedSrtDir);
        initDirectory();
    }

    /**
     * 初始化翻译文件存储目录。
     */
    private void initDirectory() {
        try {
            Files.createDirectories(translatedSrtDir);
        } catch (Exception e) {
            throw new RuntimeException("目录初始化失败: " + translatedSrtDir, e);
        }
    }

    /**
     * 翻译SRT文件的主业务流程。
     *
     * @param task 待处理的任务对象
     * @throws Exception 翻译处理异常
     */
    public void translateSrtFile(Task task) throws Exception {
        // 1. 设置任务状态为“翻译中”
        task.setStatus(TaskStatus.TRANSLATING);
        taskMapper.updateTask(task);

        // 2. 读取原始SRT文件内容
        String originalSrtPath = task.getOriginalSrtFilePath();
        String originalContent = Files.readString(Paths.get(originalSrtPath), StandardCharsets.UTF_8);

        // 3. 解析原始SRT块（保留序号、时间码、内容结构）
        List<String> originalBlocks = parseSrtBlocks(originalContent);
        // 4. 分块（每块20个字幕条目）
        List<List<String>> chunks = chunkBlocks(originalBlocks);

        // 5. 异步翻译每个块
        List<CompletableFuture<List<String>>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.supplyAsync(() -> retryTranslateChunk(chunk), translationExecutor))
                .collect(Collectors.toList());

        // 6. 收集翻译结果
        List<String> translatedBlocks = new ArrayList<>();
        for (CompletableFuture<List<String>> future : futures) {
            translatedBlocks.addAll(future.get());
        }

        // 7. 生成翻译后的文件名和路径
        String translatedSrtFilename = "translated_" + task.getOriginalSrtFilename();
        Path translatedPath = translatedSrtDir.resolve(translatedSrtFilename);
        // 8. 将翻译后的内容写入文件
        Files.writeString(translatedPath, buildSrtContent(translatedBlocks), StandardCharsets.UTF_8);

        // 9. 更新任务状态
        task.setTranslatedSrtFilename(translatedSrtFilename);
        task.setTranslatedSrtFilePath(translatedPath.toString());
        task.setStatus(TaskStatus.TRANSLATED);
        taskMapper.updateTask(task);

        // 10. 计算费用并扣费（统计内容部分字数）
        int totalWords = translatedBlocks.stream()
                .map(block -> block.split("\\r?\\n", 3)) // 分割为序号、时间码、内容
                .filter(parts -> parts.length >= 3) // 过滤无效块
                .mapToInt(parts -> parts[2].trim().length()) // 统计内容长度
                .sum();
        BigDecimal cost = BigDecimal.valueOf(totalWords)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(0.1));
        userService.deductBalance(task.getUserId(), cost);
    }

    /**
     * 解析SRT内容为原始块列表（保留序号、时间码、内容的原始结构）
     *
     * @param content SRT文件内容
     * @return 分割后的SRT块列表
     */
    private List<String> parseSrtBlocks(String content) {
        return Arrays.stream(content.split("\\r?\\n\\r?\\n"))
                .filter(block -> !block.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * 将原始块列表分块（每块20个）
     *
     * @param blocks 原始SRT块列表
     * @return 分块后的SRT块列表
     */
    private List<List<String>> chunkBlocks(List<String> blocks) {
        return IntStream.range(0, (blocks.size() + CHUNK_SIZE - 1) / CHUNK_SIZE)
                .mapToObj(i -> blocks.subList(
                        i * CHUNK_SIZE,
                        Math.min((i + 1) * CHUNK_SIZE, blocks.size())
                ))
                .collect(Collectors.toList());
    }

    /**
     * 构建翻译提示文本（优化版：结合角色、规则和示例）
     *
     * @param chunk 待翻译的SRT块
     * @return 构建好的Prompt字符串
     */
    private String buildTranslationPrompt(List<String> chunk) {
        // 使用 StringBuilder 以获得更好的性能
        StringBuilder prompt = new StringBuilder();

        // 1. 角色和总体任务定义
        prompt.append("你是一个专业的SRT字幕翻译程序。你的任务是将用户提供的SRT字幕文本块中的英文对话翻译成简体中文。");
        prompt.append("你必须严格遵守以下所有规则，并完全模仿示例中的输出格式。\n\n");

        // 2. 规则列表 (更严格和清晰)
        prompt.append("--- 规则 ---\n");
        prompt.append("1. 【格式保留】: 必须完整保留原始的序号、时间戳和空行结构，不得有任何改动。\n");
        prompt.append("2. 【仅翻译内容】: 只翻译对话文本。序号和时间戳绝对不能翻译。\n");
        prompt.append("3. 【翻译风格】: 译文必须达到专业影视剧字幕的标准，具体要求如下：\n" +
                "- a. 【简洁至上】: 句子必须简短、精炼、易于快速阅读。果断舍弃不影响核心意思的冗余词语。\n" +
                "- b. 【塑造角色】: 仔细揣摩原文的语气，使译文符合说话者的身份、性格和情绪。例如，教授的用词应严谨，年轻人的对话应生动活泼。\n" +
                "- c. 【口语地道】: 使用地道的简体中文口语，完全避免“翻译腔”。如果原文是俚语或俗语，请找到中文里对应的口头表达。\n" +
                "- d. 【情景贴合】: 译文要与画面情景（如幽默、紧张、悲伤）相匹配。笑点需要翻译得让中文观众也能笑出来。\n" +
                "- e. 【保持节奏】: 恰当处理原文中的停顿、口头禅（如 'you know', 'like', 'um'），可以酌情省略或用中文里的“呃”、“这个”、“你知道吧”等词来体现人物的说话节奏。\n");
        prompt.append("4. 【无额外信息】: 绝对不要在你的回答中包含任何解释、注释、说明或原始文本。你的输出必须是且仅是翻译好的SRT字幕块。\n\n");

        // 3. 提供一个完美的“输入->输出”示例 (Few-Shot Prompting)
        prompt.append("--- 示例 ---\n");
        prompt.append("【原始字幕】:\n");
        prompt.append("1\n");
        prompt.append("00:00:03,760 --> 00:00:06,220\n");
        prompt.append("This is an example.\n\n");
        prompt.append("2\n");
        prompt.append("00:00:07,100 --> 00:00:10,850\n");
        prompt.append("Please translate the text\nand keep the format.\n\n");

        prompt.append("【你的输出】:\n");
        prompt.append("1\n");
        prompt.append("00:00:03,760 --> 00:00:06,220\n");
        prompt.append("这是一个示例。\n\n");
        prompt.append("2\n");
        prompt.append("00:00:07,100 --> 00:00:10,850\n");
        prompt.append("请翻译这里的文本\n并保持格式。\n\n");
        prompt.append("--- 结束示例 ---\n\n");

        // 4. 给出实际任务
        prompt.append("现在，请处理以下SRT字幕块：\n");
        prompt.append("【原始字幕】:\n");

        // 这部分与你的原始代码相同，用于附加待翻译的字幕
        for (String line : chunk) {
            prompt.append(line).append("\n");
        }
        // 添加一个结尾提示，让AI知道任务的终点
        prompt.append("\n【你的输出】:\n");


        // 直接添加原始块（无需拆分重组）
        for (String block : chunk) {
            prompt.append(block).append("\n\n");
        }
        return prompt.toString().trim();
    }

    /**
     * 调用豆包API进行翻译处理。
     *
     * @param prompt 翻译提示文本
     * @return 翻译后的文本
     * @throws RuntimeException API调用失败时抛出
     */
    private String callDoubaoApi(String prompt) {
        // 1. 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(doubaoApiKey); // 设置API密钥
        headers.setContentType(MediaType.APPLICATION_JSON); // 设置内容类型为JSON

        // 2. 构造请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", doubaoModel); // 使用的模型
        requestBody.put("temperature", 0.3); // 调整随机性，降低发散性
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt))); // 放入prompt
//        requestBody.put("thinking", Map.of("type", "disabled")); // 关闭思考

        // 3. 发起HTTP POST请求
        ResponseEntity<DoubaoResponse> response = restTemplate.exchange(
                doubaoApiBase + "/chat/completions", // API endpoint
                HttpMethod.POST, // HTTP 方法
                new HttpEntity<>(requestBody, headers), // 请求体和请求头
                DoubaoResponse.class // 响应类型
        );

        // 4. 处理响应
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("API调用失败: 状态码=" + response.getStatusCode());
        }

        DoubaoResponse responseBody = response.getBody();
        if (responseBody.getChoices() == null || responseBody.getChoices().isEmpty()) {
            throw new RuntimeException("API返回无效结果");
        }

        DoubaoResponse.Choice firstChoice = responseBody.getChoices().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("无效的choices结构"));

        if (firstChoice.getMessage() == null || firstChoice.getMessage().getContent() == null) {
            throw new RuntimeException("API返回空内容");
        }

        return firstChoice.getMessage().getContent();
    }

    /**
     * 带重试机制的块翻译处理（返回原始块列表）
     *
     * @param chunk 待翻译的SRT块
     * @return 翻译后的SRT块列表
     * @throws RuntimeException 翻译重试失败时抛出
     */
    private List<String> retryTranslateChunk(List<String> chunk) {
        // 循环重试，直到达到最大重试次数
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                // 1. 构建Prompt
                String prompt = buildTranslationPrompt(chunk);
                // 2. 调用API进行翻译
                String translatedText = callDoubaoApi(prompt);
                // 3. 解析翻译后的文本为SRT块
                List<String> translatedBlocks = parseTranslatedBlocks(translatedText);

                // 4. 验证块数量一致性
                if (translatedBlocks.size() != chunk.size()) {
                    throw new RuntimeException("结果数量不匹配: 预期" + chunk.size() + " 实际" + translatedBlocks.size());
                }

                // 5. 验证每个块的格式有效性
                validateSrtBlocks(translatedBlocks);

                // 6. 成功，返回翻译后的块
                return translatedBlocks;
            } catch (Exception e) {
                // 如果达到最大重试次数，则抛出异常
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("翻译重试失败: " + e.getMessage(), e);
                }
                // 否则，继续重试
            }
        }
        throw new IllegalStateException("未处理的重试流程");
    }

    /**
     * 解析翻译后的文本为原始块列表
     *
     * @param translatedText 翻译后的文本
     * @return 分割后的SRT块列表
     */
    private List<String> parseTranslatedBlocks(String translatedText) {
        return Arrays.stream(translatedText.split("\\r?\\n\\r?\\n"))
                .filter(block -> !block.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * 构建最终SRT文件内容（直接拼接原始块）
     *
     * @param blocks SRT块列表
     * @return 完整的SRT文件内容
     */
    private String buildSrtContent(List<String> blocks) {
        return String.join("\n\n", blocks); // 块之间用空行分隔
    }

    /**
     * 验证SRT块格式有效性（序号、时间码、内容）
     *
     * @param blocks SRT块列表
     * @throws Exception 验证失败时抛出
     */
    private void validateSrtBlocks(List<String> blocks) throws Exception {
        // 遍历每个SRT块
        for (String block : blocks) {
            // 分割块为序号、时间码和内容
            String[] parts = block.split("\\r?\\n", 3);
            // 确保至少有三个部分
            if (parts.length < 3) {
                throw new RuntimeException("块格式错误: " + block);
            }
            // 验证序号（数字）
            if (!parts[0].trim().matches("\\d+")) {
                throw new RuntimeException("序号格式错误: " + parts[0].trim());
            }
            // 验证时间码（HH:mm:ss,SSS --> HH:mm:ss,SSS）
            if (!parts[1].trim().matches("\\d{2}:\\d{2}:\\d{2},\\d{3} --> \\d{2}:\\d{2}:\\d{2},\\d{3}")) {
                throw new RuntimeException("时间码格式错误: " + parts[1].trim());
            }
            // 验证内容非空
            if (parts[2].trim().isBlank()) {
                throw new RuntimeException("内容为空: " + parts[0].trim());
            }
        }
    }

    /**
     * 豆包API响应数据结构。
     */
    @Data
    private static class DoubaoResponse {
        private List<Choice> choices;

        @Data
        public static class Choice {
            private Message message;
        }

        @Data
        public static class Message {
            private String content;
        }
    }
}