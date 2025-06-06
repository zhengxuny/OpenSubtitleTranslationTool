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

/**
 * 视频字幕摘要服务，负责通过调用大语言模型API生成视频内容的中文摘要。
 * 依赖REST模板发送HTTP请求，并通过配置注入API连接参数。
 */
@Service
public class SummaryService {
    private final RestTemplate restTemplate;
    private final String apiBase;
    private final String apiKey;
    private final String model;

    /**
     * 构造视频摘要服务实例，注入必要的依赖和配置参数。
     *
     * @param restTemplate HTTP请求模板，用于与API服务通信
     * @param apiBase      大语言模型API的基础URL地址（从配置文件注入）
     * @param apiKey       API认证密钥（从配置文件注入）
     * @param model        使用的大语言模型名称（从配置文件注入）
     */
    public SummaryService(RestTemplate restTemplate,
                         @Value("${doubao.api-base}") String apiBase,
                         @Value("${doubao.api-key}") String apiKey,
                         @Value("${doubao.model}") String model) {
        this.restTemplate = restTemplate;
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * 根据视频字幕文件生成内容摘要。
     * 读取SRT文件内容后，通过大语言模型API生成中文摘要。
     *
     * @param srtFilePath 视频字幕文件（SRT格式）的路径
     * @return 视频内容的中文摘要文本
     * @throws Exception 当文件读取失败或API调用异常时抛出
     */
    public String summarizeVideo(String srtFilePath) throws Exception {
        // 读取SRT文件的全部内容
        String srtContent = Files.readString(Path.of(srtFilePath));

        // 构建用于大语言模型的提示词，要求基于字幕内容总结视频主要内容
        String prompt = "请根据以下视频字幕内容，用中文总结视频的主要内容。字幕内容如下：\n\n" + srtContent;

        // 设置API请求头：指定内容类型为JSON，添加认证信息
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        // 构造API请求体，包含使用的模型和对话消息（用户提示）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        // 发送POST请求到API服务端，获取响应（响应体类型为Map）
        ResponseEntity<Map> response = restTemplate.exchange(
            apiBase + "/chat/completions",
            HttpMethod.POST,
            new HttpEntity<>(requestBody, headers),
            Map.class
        );

        // 从响应体中解析生成的摘要内容
        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && responseBody.containsKey("choices")) {
            List<Map> choices = (List<Map>) responseBody.get("choices");
            if (!choices.isEmpty()) {
                Map<String, Object> message = (Map) choices.get(0).get("message");
                return (String) message.get("content");
            }
        }
        throw new RuntimeException("调用API生成摘要失败");
    }
}