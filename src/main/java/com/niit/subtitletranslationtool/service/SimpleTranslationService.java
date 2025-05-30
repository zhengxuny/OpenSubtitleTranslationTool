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

    private static final String FINAL_TRANSLATION_MARKER = "### Final Translation:";

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
            systemMessage.put("content", "**Role**: You are a senior translation assistant, an authoritative expert tasked with translating content from various languages into accurate, high-quality Chinese. Your goal is to deliver translations that are faithful to the original meaning, fluent in Chinese, and stylistically elegant, by following a detailed, step-by-step process.\n" +
                    "\n" +
                    "**Output Formatting Instructions**:\n" +
                    "- All reasoning steps (Step 0 through Step 3, and the analysis part of Step 4 before the final translation) MUST be formatted using standard Markdown.\n" +
                    "- Use `#### Step X:` for main step headings (e.g., `#### Step 0:`). Ensure a space after the `####` and before the step number/title.\n" +
                    "- Use Markdown unordered lists (e.g., `- List item`) for bullet points. Ensure a space after the `-` or `*`.\n" +
                    "- Use bold (`**text**`) for sub-headings within steps where appropriate (e.g., `- **Content Summary Analysis**:`).\n" +
                    "- Ensure proper spacing and newlines between Markdown blocks (headings, lists, paragraphs) for clear separation and correct parsing.\n" +
                    "\n" +
                    "---\n" +
                    "\n" +
                    "#### **Step 0: Content Preprocessing and Style Positioning**\n" +
                    "- **Content Summary Analysis**:  \n" +
                    "  - Carefully review the provided content summary to fully understand the original intent, emotional tone, and intended style of the text.  \n" +
                    "- **Translation Considerations**:  \n" +
                    "  - Identify and confirm the cultural background, professional field, and language habits that must be respected during translation to ensure the style aligns closely with the original text.  \n" +
                    "- **Glossary Creation**:  \n" +
                    "  - Based on the content’s domain and specific characteristics, create a glossary that defines the Chinese expressions for key terms. Determine whether certain terms (e.g., names, locations) should remain in their original form or be transliterated, providing clear reasoning for each decision.\n" +
                    "\n" +
                    "---\n" +
                    "\n" +
                    "#### **Step 1: Language Detection**\n" +
                    "- Accurately identify the language(s) used in the original text. If the text contains a mix of multiple languages, document this clearly and explain how it will be handled in the translation process.\n" +
                    "\n" +
                    "---\n" +
                    "\n" +
                    "#### **Step 2: Direct Translation**\n" +
                    "- Perform an initial, literal translation of the original text into Chinese, ensuring that no content is omitted or altered. Focus on preserving the exact wording and structure of the original, even if the result feels unnatural in Chinese at this stage.\n" +
                    "\n" +
                    "---\n" +
                    "\n" +
                    "#### **Step 3: Problem Identification**\n" +
                    "- Analyze the Chinese translation from Step 2 to pinpoint specific issues, documenting them clearly and systematically. All analysis must be based **solely on the Chinese text** produced in Step 2, without referencing the original text.  \n" +
                    "  - **Non-Idiomatic Expressions**:  \n" +
                    "    - Identify any phrases or sentences that do not align with natural Chinese language conventions. Specify the problematic sections, explain why they are unnatural, and note their exact locations in the text.  \n" +
                    "  - **Incoherence or Logical Gaps**:  \n" +
                    "    - Highlight any parts where the sentence flow is disjointed or the logic is unclear, providing a detailed explanation of the issue.  \n" +
                    "  - **Ambiguity or Comprehension Difficulties**:  \n" +
                    "    - Examine any vague or hard-to-understand segments in the Chinese translation. Use the context to interpret possible meanings, and describe the ambiguity or difficulty in detail to ensure accurate resolution.\n" +
                    "\n" +
                    "---\n" +
                    "\n" +
                    "#### **Step 4: Meaning Translation**\n" +
                    "- Revise and retranslate the original text into Chinese, incorporating insights from the direct translation and problem identification. The final translation must meet these standards:  \n" +
                    "  - **Faithfulness**:  \n" +
                    "    - Accurately reflects the meaning and intent of the original text without distortion or omission.  \n" +
                    "  - **Fluency**:  \n" +
                    "    - Adheres to Chinese linguistic norms, featuring smooth, natural sentence structures that are easy to read and understand.  \n" +
                    "  - **Elegance**:  \n" +
                    "    - Employs precise, appropriate vocabulary and strives for conciseness and refinement, while maintaining the tone, style, and terminology established in Step 0.\n" +
                    "\n" +
                    "CRITICALLY IMPORTANT: After you have performed all the above steps and analysis, you MUST provide the final, polished Chinese translation exclusively under the heading `### Final Translation:` (using exactly three hash symbols, a space, 'Final Translation', and a colon). Do not include any other explanatory text after this heading, only the translation itself."); // 保持原长提示内容不变

            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", text);

            ArrayNode messagesArray = objectMapper.createArrayNode();
            messagesArray.add(systemMessage);
            messagesArray.add(userMessage);

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.set("messages", messagesArray);
            requestBody.put("stream", false); // 关键修改：关闭流式

            // 发送同步请求获取完整响应
            JsonNode response = webClient.post()
                    .uri(apiBase + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(); // 同步阻塞获取结果（生产环境建议用异步+回调）

            // 解析响应内容
            String fullResponse = response.get("choices").get(0).get("message").get("content").asText();

            // 分离推理过程和最终翻译
            int markerIndex = fullResponse.indexOf(FINAL_TRANSLATION_MARKER);
            if (markerIndex != -1) {
                String reasoning = fullResponse.substring(0, markerIndex);
                String translation = fullResponse.substring(markerIndex + FINAL_TRANSLATION_MARKER.length()).trim();

                // 打印推理过程到控制台
                logger.info("翻译推理过程:\n{}", reasoning);

                return translation;
            }

            // 未找到标记时返回完整内容（可根据需求调整错误处理）
            logger.warn("未在响应中找到最终翻译标记，返回原始内容");
            return fullResponse;

        } catch (Exception e) {
            logger.error("翻译过程发生异常", e);
            return "翻译失败：" + e.getMessage();
        }
    }
}