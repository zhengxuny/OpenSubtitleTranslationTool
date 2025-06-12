package com.niit.subtitletranslationtool.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 异步翻译服务类。
 *
 * <p>该类使用异步方式调用 {@link SimpleTranslationService}，
 * 以便在不阻塞主线程的情况下执行翻译任务。
 * 适用于需要处理大量翻译请求或对响应时间有要求的场景。
 */
@Service
public class AsyncSimpleTranslationService {

    private final SimpleTranslationService simpleTranslationService;

    /**
     * 构造函数，用于注入 {@link SimpleTranslationService} 实例。
     *
     * @param simpleTranslationService 要使用的同步翻译服务实例。
     */
    public AsyncSimpleTranslationService(SimpleTranslationService simpleTranslationService) {
        this.simpleTranslationService = simpleTranslationService;
    }

    /**
     * 异步翻译方法。
     *
     * <p>使用 Spring 的 {@link Async} 注解，该方法将在独立的线程中执行，
     * 从而不会阻塞主线程。
     *
     * @param text 待翻译的文本。
     * @return 一个 {@link CompletableFuture} 对象，用于异步获取翻译结果。
     */
    @Async // 声明该方法为异步方法，Spring将负责在线程池中执行它
    public CompletableFuture<String> asyncTranslate(String text) {
        // 调用原有同步翻译逻辑
        String result = simpleTranslationService.translate(text);
        // 将结果包装为CompletableFuture返回
        return CompletableFuture.completedFuture(result);
    }
}