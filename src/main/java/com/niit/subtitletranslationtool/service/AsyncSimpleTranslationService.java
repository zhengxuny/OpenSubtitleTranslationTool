package com.niit.subtitletranslationtool.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 异步翻译服务，包裹同步翻译服务以支持异步操作
 */
@Service
public class AsyncSimpleTranslationService {

    private final SimpleTranslationService simpleTranslationService;

    public AsyncSimpleTranslationService(SimpleTranslationService simpleTranslationService) {
        this.simpleTranslationService = simpleTranslationService;
    }

    /**
     * 异步翻译方法（通过@Async开启异步线程）
     * @param text 待翻译文本
     * @return 包含翻译结果的CompletableFuture
     */
    @Async
    public CompletableFuture<String> asyncTranslate(String text) {
        // 调用原有同步翻译逻辑（无需修改）
        String result = simpleTranslationService.translate(text);
        // 将结果包装为CompletableFuture返回
        return CompletableFuture.completedFuture(result);
    }
}