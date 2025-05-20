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

        List<SrtEntry> translatedEntries = new ArrayList<>();
        for (List<SrtEntry> chunk : chunks) {
            String prompt = buildTranslationPrompt(chunk);
            String translatedText = callDoubaoApi(prompt);
            translatedEntries.addAll(parseTranslatedText(chunk, translatedText));
        }

        String translatedSrtFilename = "translated_" + task.getOriginalSrtFilename();
        Path translatedPath = translatedSrtDir.resolve(translatedSrtFilename);
        Files.writeString(translatedPath, buildSrtContent(translatedEntries), StandardCharsets.UTF_8);

        task.setTranslatedSrtFilename(translatedSrtFilename);
        task.setTranslatedSrtFilePath(translatedPath.toString());
        task.setStatus(TaskStatus.TRANSLATED);
        taskMapper.updateTask(task);
    }

    private List<SrtEntry> parseSrtEntries(String content) {
        return Arrays.stream(content.split("\\r?\\n\\r?\\n"))  // 兼容不同系统换行符
                .filter(block -> !block.isBlank())
                .map(block -> {
                    String[] parts = block.split("\\r?\\n", 3);  // 分割块内三部分
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
        List<List<SrtEntry>> chunks = new ArrayList<>();
        List<SrtEntry> currentChunk = new ArrayList<>();
        int currentLength = 0;

        for (SrtEntry entry : entries) {
            int entryLength = entry.content().length();
            if (currentLength + entryLength > doubaoMaxContextLength) {
                chunks.add(currentChunk);
                currentChunk = new ArrayList<>();
                currentLength = 0;
            }
            currentChunk.add(entry);
            currentLength += entryLength;
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk);
        }
        return chunks;
    }

    private String buildTranslationPrompt(List<SrtEntry> chunk) {
        return "你是一个字幕翻译专家，请将以下英语SRT字幕严格按原格式翻译成简体中文，保持时间戳和序号不变：\n" + buildSrtContent(chunk);
    }

    private String callDoubaoApi(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(doubaoApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", doubaoModel);
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

    private List<SrtEntry> parseTranslatedText(List<SrtEntry> originalChunk, String translatedText) {
        // 关键修复：使用兼容换行符的分割方式（\n或\r\n）
        String[] translatedBlocks = translatedText.split("\\r?\\n\\r?\\n");
        System.out.println("解析翻译结果：原始块大小=" + originalChunk.size() + "，翻译块数量=" + translatedBlocks.length);

        if (translatedBlocks.length < originalChunk.size()) {
            System.err.println("翻译结果不完整！预期至少" + originalChunk.size() + "条，实际" + translatedBlocks.length + "条");
            System.err.println("翻译文本前2000字符：" + (translatedText.length() > 2000 ? translatedText.substring(0, 2000) : translatedText));
            throw new RuntimeException("翻译结果条目数不足，无法完成解析");
        }

        return IntStream.range(0, originalChunk.size())
                .mapToObj(i -> {
                    SrtEntry original = originalChunk.get(i);
                    String translatedBlock = translatedBlocks[i];
                    // 关键修复：分割翻译块内的三部分（兼容不同换行符）
                    String[] parts = translatedBlock.split("\\r?\\n", 3);
                    return createTranslatedSrtEntry(original, parts);
                })
                .collect(Collectors.toList());
    }

    private String buildSrtContent(List<SrtEntry> entries) {
        return entries.stream()
                .map(entry -> entry.sequence() + "\n" + entry.timecode() + "\n" + entry.content())
                .collect(Collectors.joining("\n\n"));
    }

    private SrtEntry createTranslatedSrtEntry(SrtEntry original, String[] parts) {
        if (parts.length < 3) {
            System.err.println("翻译块格式错误（序号=" + original.sequence() + "）: " + Arrays.toString(parts));
            // 保留原始内容，避免丢失数据
            return new SrtEntry(original.sequence(), original.timecode(), original.content());
        }
        // 严格保持原始序号和时间码，仅替换内容部分
        return new SrtEntry(original.sequence(), original.timecode(), parts[2].trim());
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