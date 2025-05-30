package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.service.SimpleTranslationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TranslationController {

    private final SimpleTranslationService translationService;

    public TranslationController(SimpleTranslationService translationService) {
        this.translationService = translationService;
    }

    @GetMapping("/api/translate")
    public String translate(@RequestParam String text) {
        return translationService.translate(text);
    }
}