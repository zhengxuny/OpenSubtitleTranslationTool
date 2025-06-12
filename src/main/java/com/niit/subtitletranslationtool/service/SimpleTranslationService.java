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

import java.util.Map;

/**
 * 提供基于API的简单翻译服务，负责构建翻译请求、发送网络调用并解析最终翻译结果。
 * 支持通过配置注入API基础地址、密钥和模型参数，处理同步翻译请求流程。
 */
@Service
public class SimpleTranslationService {

    private static final Logger logger = LoggerFactory.getLogger(SimpleTranslationService.class);
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final String apiBase;
    private final String apiKey;
    private final String model;

    /**
     * 初始化翻译服务实例，注入必要的依赖和配置参数。
     *
     * @param webClient    WebClient实例，用于执行HTTP请求
     * @param objectMapper Jackson对象映射器，用于JSON数据处理
     * @param apiBase      翻译API的基础地址（从配置文件注入）
     * @param apiKey       访问API的认证密钥（从配置文件注入）
     * @param model        使用的翻译模型名称（从配置文件注入）
     */
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

    /**
     * 执行文本翻译操作，通过调用外部API将输入文本翻译为简体中文。
     *
     * @param text 待翻译的原始文本内容
     * @return 翻译后的结果字符串；若发生异常则返回包含错误信息的提示字符串
     */
    public String translate(String text) {
        try {
            // 构建系统角色消息节点（保持与原流式版本一致的系统提示）
            ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", "");

            // 构建用户角色消息节点，包含详细翻译指令和待翻译内容
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", "你是一位顶尖的翻译专家和语言艺术家，致力于将各种语言的文本精妙地翻译成高水准的简体中文。你的目标是使译文不仅流畅自然、完全符合简体中文的表达习惯，达到母语水平，更要精准捕捉并传神再现原文的深层含义、细腻情感、独特语境、文学风格和整体意境。最终输出只包含翻译结果，不包含任何额外信息或评论。以纯文本形式返回翻译结果。\n" +
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
                    "以下是需要翻译的内容：" + text);

            // 组装消息列表，包含系统消息和用户消息
            ArrayNode messagesArray = objectMapper.createArrayNode();
            messagesArray.add(systemMessage);
            messagesArray.add(userMessage);

            // 构造API请求体，指定模型、消息列表和非流式模式
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model",model);
            requestBody.set("messages", messagesArray);
            requestBody.put("stream", false);

            // 添加thinking参数以关闭深度思考
            ObjectNode thinkingNode = objectMapper.createObjectNode();
            thinkingNode.put("type", "disabled");
            requestBody.set("thinking", thinkingNode);

            // 通过WebClient发送POST请求，设置请求头、内容类型和请求体，同步获取响应
            JsonNode response = webClient.post()
                    .uri(apiBase + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            // 从响应中提取翻译结果内容（首条选择的消息内容）
            String fullResponse = response.get("choices").get(0).get("message").get("content").asText();

            // 记录完整响应日志并返回处理后的结果（去除首尾空白）
            logger.info("API返回的完整响应内容:\n{}", fullResponse);
            return fullResponse.trim();

        } catch (Exception e) {
            // 捕获异常并记录错误日志，返回包含错误信息的提示字符串
            logger.error("翻译过程发生异常", e);
            return "翻译失败：" + e.getMessage();
        }
    }
}