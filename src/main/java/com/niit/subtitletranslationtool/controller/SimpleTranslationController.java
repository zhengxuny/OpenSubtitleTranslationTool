package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.service.SimpleTranslationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简单翻译功能的REST控制器，处理翻译请求的HTTP交互。
 * <p>
 * 提供/.api/simpletranslate接口，接收待翻译文本并返回翻译结果。
 */
@RestController
public class SimpleTranslationController {

    private final SimpleTranslationService translationService;

    /**
     * 构造函数，通过依赖注入初始化翻译服务实例。
     *
     * @param translationService 翻译服务实例，用于实际执行翻译逻辑（不可为空）
     */
    public SimpleTranslationController(SimpleTranslationService translationService) {
        this.translationService = translationService;
    }

    /**
     * 处理GET类型的翻译请求，将输入文本传递给翻译服务并返回结果。
     *
     * @param text 待翻译的原始文本内容（不能为空）
     * @return 翻译后的结果字符串
     */
    @GetMapping("/api/simpletranslate")
    public String translate(@RequestParam String text) {
        return translationService.translate(text);
    }
}