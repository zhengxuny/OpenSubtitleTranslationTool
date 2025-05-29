package com.niit.subtitletranslationtool.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SummaryService {
    private final RestTemplate restTemplate;
    private final String apiBase;
    private final String apiKey;
    private final String model;

    public SummaryService(RestTemplate restTemplate,
                         @Value("${doubao.api-base}") String apiBase,
                         @Value("${doubao.api-key}") String apiKey,
                         @Value("${doubao.model}") String model) {
        this.restTemplate = restTemplate;
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.model = model;
    }

        public String summarizeVideo(String srtFilePath) throws Exception {
        // Read SRT file content
        String srtContent = Files.readString(Path.of(srtFilePath));

        // Build prompt for LLM
        String prompt = "请根据以下视频字幕内容，用中文总结视频的主要内容。字幕内容如下：\n\n" + srtContent;

        // Create API request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        // Call API
        ResponseEntity<Map> response = restTemplate.exchange(
            apiBase + "/chat/completions",
            HttpMethod.POST,
            new HttpEntity<>(requestBody, headers),
            Map.class
        );

        // Parse response
        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && responseBody.containsKey("choices")) {
            List<Map> choices = (List<Map>) responseBody.get("choices");
            if (!choices.isEmpty()) {
                Map<String, Object> message = (Map) choices.get(0).get("message");
                return (String) message.get("content");
            }
        }
        throw new RuntimeException("Failed to generate summary from API");
    }
}