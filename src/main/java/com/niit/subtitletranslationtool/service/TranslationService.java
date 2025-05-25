package com.niit.subtitletranslationtool.service;

import com.niit.subtitletranslationtool.entity.Task;  // 导入任务实体类
import com.niit.subtitletranslationtool.enums.TaskStatus;  // 导入任务状态枚举
import com.niit.subtitletranslationtool.mapper.TaskMapper;  // 导入任务数据访问接口
import com.fasterxml.jackson.databind.ObjectMapper;  // 导入Jackson库的ObjectMapper类，用于JSON序列化和反序列化
import lombok.Data;  // 导入Lombok库的Data注解，自动生成getter、setter、equals、hashCode和toString方法
import lombok.RequiredArgsConstructor;  // 导入Lombok库的RequiredArgsConstructor注解，自动生成包含final字段的构造器
import org.springframework.beans.factory.annotation.Autowired;  // 导入Spring框架的Autowired注解，用于依赖注入
import org.springframework.beans.factory.annotation.Value;  // 导入Spring框架的Value注解，用于读取配置文件中的值
import org.springframework.http.*;  // 导入Spring框架的HTTP相关类，如HttpHeaders、MediaType和HttpEntity
import org.springframework.stereotype.Service;  // 导入Spring框架的Service注解，标记一个类为服务类
import org.springframework.web.client.RestTemplate;  // 导入Spring框架的RestTemplate类，用于发起HTTP请求

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;  // 导入StandardCharsets类，用于指定字符编码
import java.nio.file.Files;  // 导入Files类，用于文件操作
import java.nio.file.Path;  // 导入Path接口，表示文件或目录的路径
import java.nio.file.Paths;  // 导入Paths类，用于创建Path对象
import java.util.*;  // 导入Java集合框架
import java.util.concurrent.CompletableFuture;  // 导入CompletableFuture类，用于异步编程
import java.util.concurrent.ExecutorService;  // 导入ExecutorService接口，用于管理线程池
import java.util.concurrent.Executors;  // 导入Executors类，用于创建线程池
import java.util.stream.Collectors;  // 导入Collectors类，用于集合操作
import java.util.stream.IntStream;  // 导入IntStream类，用于处理整数流
import com.niit.subtitletranslationtool.service.UserService; // 导入 UserService
/**
 * TranslationService 类负责处理字幕文件的翻译任务。
 * 它使用豆包（Doubao）API 进行翻译，并将翻译后的字幕文件保存到指定目录。
 */
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final RestTemplate restTemplate;  // 用于发起HTTP请求
    private final TaskMapper taskMapper;  // 用于访问任务数据
    private final String doubaoApiBase;  // 豆包API的基础URL，从配置文件中读取
    private final String doubaoApiKey;  // 豆包API的密钥，从配置文件中读取
    private final String doubaoModel;  // 豆包API使用的模型名称，从配置文件中读取
    private final int doubaoMaxContextLength;  // 豆包API的最大上下文长度，从配置文件中读取
    private final Path translatedSrtDir;  // 翻译后的SRT文件保存目录

    // 并发翻译配置
    private final ExecutorService translationExecutor = Executors.newFixedThreadPool(5);  // 创建一个固定大小的线程池，用于并发翻译
    private static final int CHUNK_SIZE = 15;  // 每段15条字幕，将字幕分割成小块进行翻译
    private static final int MAX_RETRIES = 1;  // 最大重试次数，如果翻译失败，会进行重试

    @Autowired
    private UserService userService; // 注入 UserService

    /**
     * 构造函数，使用Autowired注解进行依赖注入，并从配置文件中读取相关配置。
     *
     * @param restTemplate          用于发起HTTP请求
     * @param taskMapper            用于访问任务数据
     * @param doubaoApiBase         豆包API的基础URL
     * @param doubaoApiKey          豆包API的密钥
     * @param doubaoModel           豆包API使用的模型名称
     * @param doubaoMaxContextLength 豆包API的最大上下文长度
     * @param translatedSrtDir      翻译后的SRT文件保存目录
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
        initDirectory();  // 初始化保存翻译后SRT文件的目录
    }

    /**
     * 初始化保存翻译后SRT文件的目录。如果目录不存在，则创建它。
     */
    private void initDirectory() {
        try {
            Files.createDirectories(translatedSrtDir);  // 创建目录，如果目录已存在则不执行任何操作
        } catch (Exception e) {
            throw new RuntimeException("初始化翻译SRT目录失败: " + translatedSrtDir, e);  // 如果创建目录失败，则抛出异常
        }
    }

    /**
     * 翻译SRT文件的主方法。
     *
     * @param task 包含SRT文件路径的任务对象
     * @throws Exception 如果在翻译过程中发生任何错误，则抛出异常
     */
    public void translateSrtFile(Task task) throws Exception {
        task.setStatus(TaskStatus.TRANSLATING);  // 设置任务状态为翻译中
        taskMapper.updateTask(task);  // 更新任务状态到数据库

        String originalSrtPath = task.getOriginalSrtFilePath();  // 获取原始SRT文件路径
        String originalContent = Files.readString(Paths.get(originalSrtPath), StandardCharsets.UTF_8);  // 读取原始SRT文件内容

        List<SrtEntry> entries = parseSrtEntries(originalContent);  // 解析SRT文件内容为SrtEntry列表
        List<List<SrtEntry>> chunks = chunkEntries(entries);  // 将SrtEntry列表分割成小块

        List<CompletableFuture<List<SrtEntry>>> futures = chunks.stream()  // 创建CompletableFuture列表，用于异步翻译每个小块
                .map(chunk -> CompletableFuture.supplyAsync(() -> retryTranslateChunk(chunk), translationExecutor))  // 使用线程池异步翻译每个小块，并进行重试
                .collect(Collectors.toList());  // 收集所有CompletableFuture

        List<SrtEntry> translatedEntries = new ArrayList<>();  // 创建翻译后的SrtEntry列表
        for (CompletableFuture<List<SrtEntry>> future : futures) {  // 遍历所有CompletableFuture
            translatedEntries.addAll(future.get());  // 获取每个CompletableFuture的结果，并添加到翻译后的SrtEntry列表中
        }

        validateSrtContent(translatedEntries);  // 验证翻译后的SRT内容是否有效

        String translatedSrtFilename = "translated_" + task.getOriginalSrtFilename();  // 生成翻译后的SRT文件名
        Path translatedPath = translatedSrtDir.resolve(translatedSrtFilename);  // 生成翻译后的SRT文件路径
        Files.writeString(translatedPath, buildSrtContent(translatedEntries), StandardCharsets.UTF_8);  // 将翻译后的SRT内容写入文件

        task.setTranslatedSrtFilename(translatedSrtFilename);  // 设置任务的翻译后SRT文件名
        task.setTranslatedSrtFilePath(translatedPath.toString());  // 设置任务的翻译后SRT文件路径
        task.setStatus(TaskStatus.TRANSLATED);  // 设置任务状态为已翻译
        taskMapper.updateTask(task);  // 更新任务状态到数据库

        // 统计翻译后总字数（中/英文均按1字计算）
        int totalWords = translatedEntries.stream()
                .mapToInt(entry -> entry.content().length())
                .sum();

        // 计算费用（100字=0.1元，保留2位小数）
        BigDecimal cost = BigDecimal.valueOf(totalWords)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(0.1));

        // 执行扣费
        userService.deductBalance(task.getUserId(), cost);
        //将扣费消息和扣费金额打印到控制台
        System.out.println("扣费成功: " + "扣费金额: " + cost);
    }

    /**
     * 解析SRT文件内容为SrtEntry列表。
     *
     * @param content SRT文件内容
     * @return SrtEntry列表
     */
    private List<SrtEntry> parseSrtEntries(String content) {
        return Arrays.stream(content.split("\\r?\\n\\r?\\n"))  // 将SRT文件内容按空行分割成块
                .filter(block -> !block.isBlank())  // 过滤掉空块
                .map(block -> {  // 将每个块转换为SrtEntry对象
                    String[] parts = block.split("\\r?\\n", 3);  // 将每个块按换行符分割成三部分：序号、时间戳和内容
                    if (parts.length < 3) {  // 如果分割后的部分小于3，则说明SRT块格式错误
                        System.err.println("Skipping malformed SRT block: \n" + block);  // 打印错误信息
                        return null;  // 返回null，表示跳过该块
                    }
                    return new SrtEntry(parts[0].trim(), parts[1].trim(), parts[2].trim());  // 创建SrtEntry对象
                })
                .filter(Objects::nonNull)  // 过滤掉null值
                .collect(Collectors.toList());  // 收集所有SrtEntry对象到List中
    }

    /**
     * 将SrtEntry列表分割成小块。
     *
     * @param entries SrtEntry列表
     * @return 分割后的小块列表
     */
    private List<List<SrtEntry>> chunkEntries(List<SrtEntry> entries) {
        return IntStream.range(0, (entries.size() + CHUNK_SIZE - 1) / CHUNK_SIZE)  // 生成一个整数流，表示分割后的块的索引
                .mapToObj(i -> entries.subList(  // 将每个索引转换为对应的SrtEntry子列表
                        i * CHUNK_SIZE,  // 子列表的起始索引
                        Math.min((i + 1) * CHUNK_SIZE, entries.size())  // 子列表的结束索引
                ))
                .collect(Collectors.toList());  // 收集所有子列表到List中
    }

    /**
     * 构建翻译提示语（Prompt），发送给豆包API。
     *
     * @param chunk SrtEntry块
     * @return 构建好的Prompt字符串
     */
    // 修改：发送完整SRT块（包含序号、时间戳、内容）给模型
    private String buildTranslationPrompt(List<SrtEntry> chunk) {
        StringBuilder prompt = new StringBuilder("你是专业的字幕翻译专家，请严格按照以下要求翻译SRT字幕：\n");  // 创建StringBuilder对象，用于构建Prompt
        prompt.append("- 保持原有序号（如'1'）完全不变\n");  // 添加要求：保持原有序号不变
        prompt.append("- 保持原有时间戳（如'00:00:03,760 --> 00:00:10,220'）完全不变\n");  // 添加要求：保持原有时间戳不变
        prompt.append("- 仅将第三行的字幕内容部分翻译成简体中文，就想原本就是用中文写的一样。在翻译时，请仔细阅读并理解原文，必要时请根据联系上下文进行翻译，但不要添加额外信息或解释\n");  // 添加要求：只翻译字幕内容
        prompt.append("- 保持每个字幕块的结构（三行一组，块之间用空行分隔）\n");  // 添加要求：保持字幕块的结构
        prompt.append("原始SRT字幕块如下：\n");  // 添加提示信息

        for (SrtEntry entry : chunk) {  // 遍历SrtEntry块
            prompt.append(entry.sequence()).append("\n");    // 序号  // 添加序号
            prompt.append(entry.timecode()).append("\n");   // 时间戳  // 添加时间戳
            prompt.append(entry.content()).append("\n\n");  // 内容（末尾加空行分隔块）  // 添加内容，并在末尾添加空行
        }
        return prompt.toString().trim();  // 移除末尾多余空行  // 将StringBuilder对象转换为String对象，并移除末尾多余空行
    }

    /**
     * 调用豆包API进行翻译。
     *
     * @param prompt 翻译提示语
     * @return 翻译结果
     */
    private String callDoubaoApi(String prompt) {
        HttpHeaders headers = new HttpHeaders();  // 创建HttpHeaders对象，用于设置HTTP请求头
        headers.setBearerAuth(doubaoApiKey);  // 设置Authorization头，使用Bearer Token
        headers.setContentType(MediaType.APPLICATION_JSON);  // 设置Content-Type头，指定请求体为JSON格式

        Map<String, Object> requestBody = new HashMap<>();  // 创建Map对象，用于存储请求体
        requestBody.put("model", doubaoModel);  // 设置模型名称
        requestBody.put("temperature", 0.3);  // 降低随机性，强化指令跟随  // 设置温度参数，降低翻译的随机性
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));  // 设置消息内容，包含角色和Prompt

        System.out.println("Sending prompt to Doubao API:");  // 打印日志信息
        System.out.println(prompt);  // 打印Prompt内容
        System.out.println("-----------------------------");  // 打印分隔线

        ResponseEntity<DoubaoResponse> response = restTemplate.exchange(  // 发起HTTP POST请求
                doubaoApiBase + "/chat/completions",  // 请求URL
                HttpMethod.POST,  // 请求方法
                new HttpEntity<>(requestBody, headers),  // 请求体和请求头
                DoubaoResponse.class  // 响应类型
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {  // 如果响应状态码不是2xx或者响应体为空
            System.err.println("Doubao API call failed. Status: " + response.getStatusCode());  // 打印错误信息
            try {  // 尝试打印完整的错误信息
                ObjectMapper errorMapper = new ObjectMapper();  // 创建ObjectMapper对象，用于JSON序列化
                System.err.println("Response body: " + errorMapper.writeValueAsString(response.getBody()));  // 将响应体序列化为JSON字符串并打印
            } catch (Exception e) {  // 如果序列化失败
                System.err.println("Failed to format error response: " + e.getMessage());  // 打印错误信息
            }
            throw new RuntimeException("Doubao API调用失败: 响应状态码=" + response.getStatusCode() + ", 响应体: " + response.getBody());  // 抛出异常
        }

        DoubaoResponse responseBody = response.getBody();  // 获取响应体

        try {  // 尝试打印完整的响应信息
            ObjectMapper mapper = new ObjectMapper();  // 创建ObjectMapper对象，用于JSON序列化
            System.out.println("Received response from Doubao API:");  // 打印日志信息
            System.out.println(mapper.writeValueAsString(responseBody));  // 将响应体序列化为JSON字符串并打印
            System.out.println("-----------------------------");  // 打印分隔线
        } catch (Exception e) {  // 如果序列化失败
            System.err.println("Failed to print response: " + e.getMessage());  // 打印错误信息
        }

        if (responseBody.getChoices() == null || responseBody.getChoices().isEmpty()) {  // 如果choices为空
            throw new RuntimeException("Doubao API返回空结果或无有效choices");  // 抛出异常
        }

        DoubaoResponse.Choice firstChoice = responseBody.getChoices().stream()  // 获取第一个Choice
                .findFirst()  // 获取第一个元素
                .orElseThrow(() -> new RuntimeException("Doubao API返回的choices为空"));  // 如果choices为空，则抛出异常

        if (firstChoice.getMessage() == null || firstChoice.getMessage().getContent() == null) {  // 如果Message或者Content为空
            throw new RuntimeException("Doubao API返回内容为空");  // 抛出异常
        }

        return firstChoice.getMessage().getContent();  // 返回翻译结果
    }

    /**
     * 重试翻译块，如果翻译失败，则进行重试。
     *
     * @param chunk SrtEntry块
     * @return 翻译后的SrtEntry列表
     */
    private List<SrtEntry> retryTranslateChunk(List<SrtEntry> chunk) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {  // 循环重试
            try {
                String prompt = buildTranslationPrompt(chunk);  // 构建Prompt
                String translatedText = callDoubaoApi(prompt);  // 调用豆包API进行翻译
                List<SrtEntry> result = parseTranslatedText(chunk, translatedText);  // 解析翻译结果

                if (result.size() != chunk.size()) {  // 如果翻译结果数量不匹配
                    throw new RuntimeException("翻译结果数量不匹配，预期" + chunk.size() + "条，实际" + result.size() + "条");  // 抛出异常
                }
                return result;  // 返回翻译结果
            } catch (Exception e) {  // 如果翻译过程中发生任何异常
                if (attempt == MAX_RETRIES) {  // 如果已经达到最大重试次数
                    throw new RuntimeException("翻译重试失败（块大小=" + chunk.size() + "）: " + e.getMessage(), e);  // 抛出异常
                }
                try {  // 等待一段时间后重试
                    Thread.sleep(1000);  // 休眠1秒
                } catch (InterruptedException ie) {  // 如果线程被中断
                    Thread.currentThread().interrupt();  // 中断当前线程
                }
            }
        }
        throw new IllegalStateException("未处理的重试逻辑");  // 如果重试逻辑未处理，则抛出异常
    }

    /**
     * 解析翻译后的文本，将其转换为SrtEntry列表。
     *
     * @param originalChunk 原始SrtEntry块
     * @param translatedText 翻译后的文本
     * @return 翻译后的SrtEntry列表
     */
    // 修改：解析完整SRT块结构（包含序号、时间戳、翻译内容）
    private List<SrtEntry> parseTranslatedText(List<SrtEntry> originalChunk, String translatedText) {
        // 按空行分割翻译后的字幕块（兼容不同系统换行符）
        String[] translatedBlocks = translatedText.split("\\r?\\n\\r?\\n");  // 将翻译后的文本按空行分割成块
        System.out.println("解析翻译结果：原始块大小=" + originalChunk.size() + "，翻译块数量=" + translatedBlocks.length);  // 打印日志信息

        if (translatedBlocks.length != originalChunk.size()) {  // 如果翻译后的块数量与原始块数量不匹配
            System.err.println("翻译块数量不匹配！预期" + originalChunk.size() + "块，实际" + translatedBlocks.length + "块");  // 打印错误信息
            System.err.println("翻译文本前2000字符：" + (translatedText.length() > 2000 ? translatedText.substring(0, 2000) : translatedText));  // 打印翻译文本的前2000个字符
            throw new RuntimeException("翻译结果块数量不匹配，无法完成解析");  // 抛出异常
        }

        return IntStream.range(0, originalChunk.size())  // 创建一个整数流，表示原始块的索引
                .mapToObj(i -> {  // 将每个索引转换为对应的SrtEntry对象
                    SrtEntry original = originalChunk.get(i);  // 获取原始SrtEntry对象
                    String translatedBlock = translatedBlocks[i];  // 获取翻译后的块

                    // 分割翻译块内的三部分（序号、时间戳、内容）
                    String[] parts = translatedBlock.split("\\r?\\n", 3);  // 将翻译后的块按换行符分割成三部分：序号、时间戳和内容
                    if (parts.length < 3) {  // 如果分割后的部分小于3，则说明翻译块格式错误
                        System.err.println("翻译块格式错误（原始序号=" + original.sequence() + "）: " + translatedBlock);  // 打印错误信息
                        // 保留原始内容，避免数据丢失
                        return new SrtEntry(original.sequence(), original.timecode(), original.content());  // 创建新的SrtEntry对象，保留原始内容
                    }

                    // 验证序号和时间戳是否与原始一致（可选但推荐）
                    if (!parts[0].trim().equals(original.sequence())) {  // 如果序号不匹配
                        System.err.println("警告：翻译块序号不匹配（原始=" + original.sequence() + "，翻译=" + parts[0].trim() + "）");  // 打印警告信息
                    }
                    if (!parts[1].trim().equals(original.timecode())) {  // 如果时间戳不匹配
                        System.err.println("警告：翻译块时间戳不匹配（原始=" + original.timecode() + "，翻译=" + parts[1].trim() + "）");  // 打印警告信息
                    }

                    // 仅使用第三行的翻译内容
                    return new SrtEntry(original.sequence(), original.timecode(), parts[2].trim());  // 创建新的SrtEntry对象，使用翻译后的内容
                })
                .collect(Collectors.toList());  // 收集所有SrtEntry对象到List中
    }

    /**
     * 构建SRT文件内容。
     *
     * @param entries SrtEntry列表
     * @return SRT文件内容字符串
     */
    private String buildSrtContent(List<SrtEntry> entries) {
        return entries.stream()  // 创建一个Stream对象
                .map(entry -> entry.sequence() + "\n" + entry.timecode() + "\n" + entry.content())  // 将每个SrtEntry对象转换为SRT格式的字符串
                .collect(Collectors.joining("\n\n"));  // 将所有字符串连接起来，使用空行分隔
    }

    /**
     * 验证SRT内容是否有效。
     *
     * @param entries SrtEntry列表
     * @throws Exception 如果SRT内容无效，则抛出异常
     */
    private void validateSrtContent(List<SrtEntry> entries) throws Exception {
        for (int i = 0; i < entries.size(); i++) {  // 遍历SrtEntry列表
            SrtEntry entry = entries.get(i);  // 获取SrtEntry对象

            if (!entry.sequence().matches("\\d+")) {  // 如果序号格式无效
                throw new RuntimeException("无效的字幕序号格式（序号: " + entry.sequence() + "）");  // 抛出异常
            }

            if (!entry.timecode().matches("\\d{2}:\\d{2}:\\d{2},\\d{3} --> \\d{2}:\\d{2}:\\d{2},\\d{3}")) {  // 如果时间戳格式无效
                throw new RuntimeException("无效的时间戳格式（序号: " + entry.sequence() + "）: " + entry.timecode());  // 抛出异常
            }

            if (entry.content().isBlank()) {  // 如果字幕内容为空
                throw new RuntimeException("字幕内容为空（序号: " + entry.sequence() + "）");  // 抛出异常
            }
        }
    }

    /**
     * 内部记录类，用于表示SRT文件中的一个条目。
     *
     * @param sequence 字幕序号
     * @param timecode 字幕时间码
     * @param content  字幕内容
     */
    private record SrtEntry(String sequence, String timecode, String content) {}

    /**
     * 内部静态类，用于表示豆包API的响应。
     */
    @Data
    private static class DoubaoResponse {
        private List<Choice> choices;  // 响应中的choices列表

        /**
         * 内部静态类，用于表示豆包API响应中的一个Choice。
         */
        @Data
        public static class Choice {
            private Message message;  // Choice中的消息
        }

        /**
         * 内部静态类，用于表示豆包API响应中的一个Message。
         */
        @Data
        public static class Message {
            private String content;  // 消息内容
        }
    }
}