package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.service.AsyncSimpleTranslationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * 简易翻译接口的控制器。
 * 该类负责接收HTTP请求，并调用异步翻译服务进行翻译。
 */
@RestController
public class SimpleTranslationController {

    private final AsyncSimpleTranslationService asyncTranslationService;

    /**
     * 构造函数，用于依赖注入异步翻译服务。
     *
     * @param asyncTranslationService 异步翻译服务，用于执行实际的翻译操作。
     */
    public SimpleTranslationController(AsyncSimpleTranslationService asyncTranslationService) {
        this.asyncTranslationService = asyncTranslationService;
    }

    /**
     * 提供简易翻译的API接口。
     * 接收一个文本参数，并异步地将其翻译成目标语言。
     *
     * @param text 需要翻译的文本。
     * @return CompletableFuture<String> 异步操作的结果，最终返回翻译后的文本。
     */
    @GetMapping("/api/simpletranslate")
    public CompletableFuture<String> translate(@RequestParam String text) {
        // 调用异步翻译服务进行翻译，并返回一个代表未来结果的CompletableFuture对象。
        return asyncTranslationService.asyncTranslate(text);
    }
}