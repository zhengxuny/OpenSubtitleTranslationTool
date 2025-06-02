package com.niit.subtitletranslationtool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class SimpleTranslationService {

    private static final Logger logger = LoggerFactory.getLogger(SimpleTranslationService.class);
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final String apiBase;
    private final String apiKey;
    private final String model;

    // 此常量将不再使用，因为它用于分离逻辑
    // private static final String FINAL_TRANSLATION_MARKER = "### Final Translation:";

    public SimpleTranslationService(WebClient webClient,
                                    ObjectMapper objectMapper,
                                    @Value("${doubao.api-base}") String apiBase,
                                    @Value("${doubao.api-key}") String apiKey,
                                    @Value("${doubao.model}") String model) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String translate(String text) {
        try {
            // 构建系统提示（与原流式版本相同）
            ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", ""); // 保持原长提示内容不变

            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", "\"你是一位顶尖的翻译专家和语言艺术家，致力于将各种语言的文本精妙地翻译成高水准的简体中文。你的目标是使译文不仅流畅自然、完全符合简体中文的表达习惯，达到母语水平，更要精准捕捉并传神再现原文的深层含义、细腻情感、独特语境、文学风格和整体意境。最终输出只包含翻译结果，不包含任何额外信息或评论。以纯文本形式返回翻译结果。\n" +
                    "\n" +
                    "目的和目标：\n" +
                    "\n" +
                    "*   **卓越的翻译质量**：确保翻译后的简体中文文本达到出版级母语水准，文笔优美，表达精准。\n" +
                    "*   **深层语义与风格的忠实再现**：\n" +
                    "    *   **精准传达**：不仅翻译字面意思，更要深入理解并准确传达原文的言外之意、暗示及情感色彩。\n" +
                    "    *   **风格匹配**：细致体察原文的文学风格（如诗意、朴素、戏谑、庄重等），并以最恰当的中文风格予以再现。\n" +
                    "    *   **意境营造**：对于描述性或富有画面感的文本，要力求在译文中重塑原文所描绘的意境和氛围。\n" +
                    "*   **文化元素的巧妙处理**：\n" +
                    "    *   对于原文中特有的文化现象、习语、典故，需寻找中文中最能传达其核心意涵和韵味的表达方式，避免生硬直译。\n" +
                    "    *   对拟声拟态词的处理，要选择中文中对应自然且生动的词语，准确还原其动态或情态。\n" +
                    "*   **保持文本的整体性与连贯性**：确保长句、复杂从句结构在翻译后依然清晰、连贯，符合中文逻辑。\n" +
                    "\n" +
                    "输出格式：\n" +
                    "\n" +
                    "a) 翻译完成后，直接以纯文本形式返回翻译结果。\n" +
                    "b) 除翻译结果外，不包含任何开场白、结束语、评论、表情符号或其他非翻译内容。\n" +
                    "\n" +
                    "以下是需要翻译的内容："+text);


            ArrayNode messagesArray = objectMapper.createArrayNode();
            messagesArray.add(systemMessage);
            messagesArray.add(userMessage);

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "doubao-1-5-thinking-pro-250415");
            requestBody.set("messages", messagesArray);
            requestBody.put("stream", false); // 关闭流式


            // 发送同步请求获取完整响应
            JsonNode response = webClient.post()
                    .uri(apiBase + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(); // 同步阻塞获取结果（生产环境建议用异步+回调）

            // 解析响应内容并直接返回
            String fullResponse = response.get("choices").get(0).get("message").get("content").asText();

            // 直接返回API的完整响应，不再分离推理过程和最终翻译
            logger.info("API返回的完整响应内容:\n{}", fullResponse);
            return fullResponse;

        } catch (Exception e) {
            logger.error("翻译过程发生异常", e);
            return "翻译失败：" + e.getMessage();
        }
    }
}