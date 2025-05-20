package com.niit.subtitletranslationtool;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.niit.subtitletranslationtool.mapper")
public class SubtitleTranslationToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubtitleTranslationToolApplication.class, args);
    }

}
