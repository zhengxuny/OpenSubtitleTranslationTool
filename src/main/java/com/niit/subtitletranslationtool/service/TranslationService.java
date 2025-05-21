package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    // 并发翻译配置
    private final ExecutorService translationExecutor = Executors.newFixedThreadPool(5);
    private static final int CHUNK_SIZE = 15;  // 每段15条字幕
    private static final int MAX_RETRIES = 1;  // 最大重试次数

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

    private void initDirectory() {
        try {
            Files.createDirectories(translatedSrtDir);
        } catch (Exception e) {
            throw new RuntimeException("初始化翻译SRT目录失败: " + translatedSrtDir, e);
        }
    }

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
    }

    private List<SrtEntry> parseSrtEntries(String content) {
        return Arrays.stream(content.split("\\r?\\n\\r?\\n"))
                .filter(block -> !block.isBlank())
                .map(block -> {
                    String[] parts = block.split("\\r?\\n", 3);
                    if (parts.length < 3) {
                        System.err.println("Skipping malformed SRT block: \n" + block);
                        return null;
                    }
                    return new SrtEntry(parts[0].trim(), parts[1].trim(), parts[2].trim());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<List<SrtEntry>> chunkEntries(List<SrtEntry> entries) {
        return IntStream.range(0, (entries.size() + CHUNK_SIZE - 1) / CHUNK_SIZE)
                .mapToObj(i -> entries.subList(
                        i * CHUNK_SIZE,
                        Math.min((i + 1) * CHUNK_SIZE, entries.size())
                ))
                .collect(Collectors.toList());
    }

    // 修改：发送完整SRT块（包含序号、时间戳、内容）给模型
    private String buildTranslationPrompt(List<SrtEntry> chunk) {
        StringBuilder prompt = new StringBuilder("你是专业的字幕翻译专家，请严格按照以下要求翻译SRT字幕：\n");
        prompt.append("- 保持原有序号（如'1'）完全不变\n");
        prompt.append("- 保持原有时间戳（如'00:00:03,760 --> 00:00:10,220'）完全不变\n");
        prompt.append("- 仅将第三行的字幕内容部分翻译成简体中文，就想原本就是用中文写的一样。在翻译时，请仔细阅读并理解原文，必要时请根据联系上下文进行翻译，但不要添加额外信息或解释\n");
        prompt.append("- 保持每个字幕块的结构（三行一组，块之间用空行分隔）\n");
        prompt.append("原始SRT字幕块如下：\n");

        for (SrtEntry entry : chunk) {
            prompt.append(entry.sequence()).append("\n");    // 序号
            prompt.append(entry.timecode()).append("\n");   // 时间戳
            prompt.append(entry.content()).append("\n\n");  // 内容（末尾加空行分隔块）
        }
        return prompt.toString().trim();  // 移除末尾多余空行
    }

    private String callDoubaoApi(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(doubaoApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", doubaoModel);
        requestBody.put("temperature", 0.3);  // 降低随机性，强化指令跟随
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        System.out.println("Sending prompt to Doubao API:");
        System.out.println(prompt);
        System.out.println("-----------------------------");

        ResponseEntity<DoubaoResponse> response = restTemplate.exchange(
                doubaoApiBase + "/chat/completions",
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                DoubaoResponse.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            System.err.println("Doubao API call failed. Status: " + response.getStatusCode());
            try {
                ObjectMapper errorMapper = new ObjectMapper();
                System.err.println("Response body: " + errorMapper.writeValueAsString(response.getBody()));
            } catch (Exception e) {
                System.err.println("Failed to format error response: " + e.getMessage());
            }
            throw new RuntimeException("Doubao API调用失败: 响应状态码=" + response.getStatusCode() + ", 响应体: " + response.getBody());
        }

        DoubaoResponse responseBody = response.getBody();

        try {
            ObjectMapper mapper = new ObjectMapper();
            System.out.println("Received response from Doubao API:");
            System.out.println(mapper.writeValueAsString(responseBody));
            System.out.println("-----------------------------");
        } catch (Exception e) {
            System.err.println("Failed to print response: " + e.getMessage());
        }

        if (responseBody.getChoices() == null || responseBody.getChoices().isEmpty()) {
            throw new RuntimeException("Doubao API返回空结果或无有效choices");
        }

        DoubaoResponse.Choice firstChoice = responseBody.getChoices().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Doubao API返回的choices为空"));

        if (firstChoice.getMessage() == null || firstChoice.getMessage().getContent() == null) {
            throw new RuntimeException("Doubao API返回内容为空");
        }

        return firstChoice.getMessage().getContent();
    }

    private List<SrtEntry> retryTranslateChunk(List<SrtEntry> chunk) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String prompt = buildTranslationPrompt(chunk);
                String translatedText = callDoubaoApi(prompt);
                List<SrtEntry> result = parseTranslatedText(chunk, translatedText);

                if (result.size() != chunk.size()) {
                    throw new RuntimeException("翻译结果数量不匹配，预期" + chunk.size() + "条，实际" + result.size() + "条");
                }
                return result;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("翻译重试失败（块大小=" + chunk.size() + "）: " + e.getMessage(), e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new IllegalStateException("未处理的重试逻辑");
    }

    // 修改：解析完整SRT块结构（包含序号、时间戳、翻译内容）
    private List<SrtEntry> parseTranslatedText(List<SrtEntry> originalChunk, String translatedText) {
        // 按空行分割翻译后的字幕块（兼容不同系统换行符）
        String[] translatedBlocks = translatedText.split("\\r?\\n\\r?\\n");
        System.out.println("解析翻译结果：原始块大小=" + originalChunk.size() + "，翻译块数量=" + translatedBlocks.length);

        if (translatedBlocks.length != originalChunk.size()) {
            System.err.println("翻译块数量不匹配！预期" + originalChunk.size() + "块，实际" + translatedBlocks.length + "块");
            System.err.println("翻译文本前2000字符：" + (translatedText.length() > 2000 ? translatedText.substring(0, 2000) : translatedText));
            throw new RuntimeException("翻译结果块数量不匹配，无法完成解析");
        }

        return IntStream.range(0, originalChunk.size())
                .mapToObj(i -> {
                    SrtEntry original = originalChunk.get(i);
                    String translatedBlock = translatedBlocks[i];

                    // 分割翻译块内的三部分（序号、时间戳、内容）
                    String[] parts = translatedBlock.split("\\r?\\n", 3);
                    if (parts.length < 3) {
                        System.err.println("翻译块格式错误（原始序号=" + original.sequence() + "）: " + translatedBlock);
                        // 保留原始内容，避免数据丢失
                        return new SrtEntry(original.sequence(), original.timecode(), original.content());
                    }

                    // 验证序号和时间戳是否与原始一致（可选但推荐）
                    if (!parts[0].trim().equals(original.sequence())) {
                        System.err.println("警告：翻译块序号不匹配（原始=" + original.sequence() + "，翻译=" + parts[0].trim() + "）");
                    }
                    if (!parts[1].trim().equals(original.timecode())) {
                        System.err.println("警告：翻译块时间戳不匹配（原始=" + original.timecode() + "，翻译=" + parts[1].trim() + "）");
                    }

                    // 仅使用第三行的翻译内容
                    return new SrtEntry(original.sequence(), original.timecode(), parts[2].trim());
                })
                .collect(Collectors.toList());
    }

    private String buildSrtContent(List<SrtEntry> entries) {
        return entries.stream()
                .map(entry -> entry.sequence() + "\n" + entry.timecode() + "\n" + entry.content())
                .collect(Collectors.joining("\n\n"));
    }

    private void validateSrtContent(List<SrtEntry> entries) throws Exception {
        for (int i = 0; i < entries.size(); i++) {
            SrtEntry entry = entries.get(i);

            if (!entry.sequence().matches("\\d+")) {
                throw new RuntimeException("无效的字幕序号格式（序号: " + entry.sequence() + "）");
            }

            if (!entry.timecode().matches("\\d{2}:\\d{2}:\\d{2},\\d{3} --> \\d{2}:\\d{2}:\\d{2},\\d{3}")) {
                throw new RuntimeException("无效的时间戳格式（序号: " + entry.sequence() + "）: " + entry.timecode());
            }

            if (entry.content().isBlank()) {
                throw new RuntimeException("字幕内容为空（序号: " + entry.sequence() + "）");
            }
        }
    }

    private record SrtEntry(String sequence, String timecode, String content) {}

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