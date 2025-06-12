package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.service.AsyncSimpleTranslationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
public class SimpleTranslationController {

    private final AsyncSimpleTranslationService asyncTranslationService;

    public SimpleTranslationController(AsyncSimpleTranslationService asyncTranslationService) {
        this.asyncTranslationService = asyncTranslationService;
    }

    @GetMapping("/api/simpletranslate")
    public CompletableFuture<String> translate(@RequestParam String text) {
        return asyncTranslationService.asyncTranslate(text);
    }
}