package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import com.niit.subtitletranslationtool.service.UserService;

/**
 * 提供字幕文件翻译服务，处理SRT文件的分块翻译、API调用和结果处理。
 */
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final RestTemplate restTemplate;
    private final TaskMapper taskMapper;
    private final String doubaoApiBase;
    private final String doubaoApiKey;
    private final String doubaoModel;
    private final int doubaoMaxContextLength;
    private final Path translatedSrtDir;
    private final ExecutorService translationExecutor = Executors.newFixedThreadPool(5);
    private static final int CHUNK_SIZE = 25;
    private static final int MAX_RETRIES = 1;

    @Autowired
    private UserService userService;

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
        task.setStatus(TaskStatus.TRANSLATING);
        taskMapper.updateTask(task);

        String originalSrtPath = task.getOriginalSrtFilePath();
        String originalContent = Files.readString(Paths.get(originalSrtPath), StandardCharsets.UTF_8);

        List<SrtEntry> entries = parseSrtEntries(originalContent);
        List<List<SrtEntry>> chunks = chunkEntries(entries);

        List<CompletableFuture<List<SrtEntry>>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.supplyAsync(() -> retryTranslateChunk(chunk), translationExecutor))
                .collect(Collectors.toList());

        List<SrtEntry> translatedEntries = new ArrayList<>();
        for (CompletableFuture<List<SrtEntry>> future : futures) {
            translatedEntries.addAll(future.get());
        }

        validateSrtContent(translatedEntries);

        String translatedSrtFilename = "translated_" + task.getOriginalSrtFilename();
        Path translatedPath = translatedSrtDir.resolve(translatedSrtFilename);
        Files.writeString(translatedPath, buildSrtContent(translatedEntries), StandardCharsets.UTF_8);

        task.setTranslatedSrtFilename(translatedSrtFilename);
        task.setTranslatedSrtFilePath(translatedPath.toString());
        task.setStatus(TaskStatus.TRANSLATED);
        taskMapper.updateTask(task);

        // 计算费用并进行扣费
        int totalWords = translatedEntries.stream()
                .mapToInt(entry -> entry.content().length())
                .sum();
        BigDecimal cost = BigDecimal.valueOf(totalWords)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(0.1));
        userService.deductBalance(task.getUserId(), cost);
    }

    /**
     * 解析SRT内容为结构化条目列表。
     *
     * @param content SRT文件原始内容
     * @return 解析后的字幕条目列表
     */
    private List<SrtEntry> parseSrtEntries(String content) {
        return Arrays.stream(content.split("\\r?\\n\\r?\\n"))
                .filter(block -> !block.isBlank())
                .map(block -> {
                    String[] parts = block.split("\\r?\\n", 3);
                    if (parts.length < 3) {
                        System.err.println("跳过格式错误块: \n" + block);
                        return null;
                    }
                    return new SrtEntry(parts[0].trim(), parts[1].trim(), parts[2].trim());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 将条目列表分块处理。
     *
     * @param entries 待处理条目列表
     * @return 分块后的字幕组
     */
    private List<List<SrtEntry>> chunkEntries(List<SrtEntry> entries) {
        return IntStream.range(0, (entries.size() + CHUNK_SIZE - 1) / CHUNK_SIZE)
                .mapToObj(i -> entries.subList(
                        i * CHUNK_SIZE,
                        Math.min((i + 1) * CHUNK_SIZE, entries.size())
                ))
                .collect(Collectors.toList());
    }

    /**
     * 构建翻译提示文本。
     *
     * @param chunk 待翻译字幕块
     * @return 完整的翻译提示文本
     */
    private String buildTranslationPrompt(List<SrtEntry> chunk) {
        StringBuilder prompt = new StringBuilder("你是专业的字幕翻译专家，请严格按照以下要求翻译SRT字幕：\n");
        prompt.append("- 保持原有序号（如'1'）完全不变\n");
        prompt.append("- 保持原有时间戳（如'00:00:03,760 --> 00:00:10,220'）完全不变\n");
        prompt.append("- 仅将第三行的字幕内容部分翻译成简体中文，就想原本就是用中文写的一样。在翻译时，请仔细阅读并理解原文，必要时请根据联系上下文进行翻译，但不要添加额外信息或解释\n");
        prompt.append("- 保持每个字幕块的结构（三行一组，块之间用空行分隔）\n");
        prompt.append("原始SRT字幕块如下：\n");

        for (SrtEntry entry : chunk) {
            prompt.append(entry.sequence()).append("\n");
            prompt.append(entry.timecode()).append("\n");
            prompt.append(entry.content()).append("\n\n");
        }
        return prompt.toString().trim();
    }

    /**
     * 调用豆包API进行翻译处理。
     *
     * @param prompt 完整翻译提示
     * @return API返回的翻译结果
     */
    private String callDoubaoApi(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(doubaoApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", doubaoModel);
        requestBody.put("temperature", 0.3);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        ResponseEntity<DoubaoResponse> response = restTemplate.exchange(
                doubaoApiBase + "/chat/completions",
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                DoubaoResponse.class
        );

        // 处理API响应错误情况
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
     * 带重试机制的块翻译处理。
     *
     * @param chunk 待翻译字幕块
     * @return 翻译完成的条目列表
     */
    private List<SrtEntry> retryTranslateChunk(List<SrtEntry> chunk) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String prompt = buildTranslationPrompt(chunk);
                String translatedText = callDoubaoApi(prompt);
                List<SrtEntry> result = parseTranslatedText(chunk, translatedText);

                // 验证返回条目数量
                if (result.size() != chunk.size()) {
                    throw new RuntimeException("结果数量不匹配: 预期" + chunk.size() + " 实际" + result.size());
                }
                return result;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("翻译重试失败: " + e.getMessage(), e);
                }
            }
        }
        throw new IllegalStateException("未处理的重试流程");
    }

    /**
     * 解析翻译后的文本内容。
     *
     * @param originalChunk  原始字幕块
     * @param translatedText 翻译后文本
     * @return 结构化翻译结果
     */
    private List<SrtEntry> parseTranslatedText(List<SrtEntry> originalChunk, String translatedText) {
        String[] translatedBlocks = translatedText.split("\\r?\\n\\r?\\n");

        // 验证块数量一致性
        if (translatedBlocks.length != originalChunk.size()) {
            throw new RuntimeException("块数量不匹配: 预期" + originalChunk.size() + " 实际" + translatedBlocks.length);
        }

        return IntStream.range(0, originalChunk.size())
                .mapToObj(i -> {
                    SrtEntry original = originalChunk.get(i);
                    String[] parts = translatedBlocks[i].split("\\r?\\n", 3);

                    // 处理解析异常情况
                    if (parts.length < 3) {
                        return new SrtEntry(original.sequence(), original.timecode(), original.content());
                    }
                    return new SrtEntry(original.sequence(), original.timecode(), parts[2].trim());
                })
                .collect(Collectors.toList());
    }

    /**
     * 构建最终SRT文件内容。
     *
     * @param entries 翻译完成的条目
     * @return 完整的SRT文件内容
     */
    private String buildSrtContent(List<SrtEntry> entries) {
        return entries.stream()
                .map(entry -> entry.sequence() + "\n" + entry.timecode() + "\n" + entry.content())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 验证SRT内容格式有效性。
     *
     * @param entries 待验证条目
     * @throws Exception 验证失败异常
     */
    private void validateSrtContent(List<SrtEntry> entries) throws Exception {
        for (int i = 0; i < entries.size(); i++) {
            SrtEntry entry = entries.get(i);

            // 验证序号格式
            if (!entry.sequence().matches("\\d+")) {
                throw new RuntimeException("序号格式错误: " + entry.sequence());
            }

            // 验证时间码格式
            if (!entry.timecode().matches("\\d{2}:\\d{2}:\\d{2},\\d{3} --> \\d{2}:\\d{2}:\\d{2},\\d{3}")) {
                throw new RuntimeException("时间码格式错误: " + entry.timecode());
            }

            // 验证内容非空
            if (entry.content().isBlank()) {
                throw new RuntimeException("内容为空: " + entry.sequence());
            }
        }
    }

    /**
     * SRT条目内部表示结构。
     *
     * @param sequence 序号
     * @param timecode 时间码
     * @param content  内容文本
     */
    private record SrtEntry(String sequence, String timecode, String content) {}

    /**
     * 豆包API响应数据结构。
     */
    @Data
    private static class DoubaoResponse {
        private List<Choice> choices;

        /**
         * 选择项数据结构。
         */
        @Data
        public static class Choice {
            private Message message;
        }

        /**
         * 消息内容结构。
         */
        @Data
        public static class Message {
            private String content;
        }
    }
}