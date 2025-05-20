package com.niit.subtitletranslationtool.entity;

import com.niit.subtitletranslationtool.enums.TaskStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {
    private Long id; // 将使用数据库自增ID
    private String originalVideoFilename;
    private String storedVideoFilename;
    private String videoFilePath; // 视频文件在服务器上的完整存储路径

    // 以下字段将在后续阶段添加和使用
    private String extractedAudioFilename;
    private String extractedAudioFilePath;
    private String originalSrtFilename;
    private String originalSrtFilePath;
    private String translatedSrtFilename;
    private String translatedSrtFilePath;
    private String subtitledVideoFilename;
    private String subtitledVideoFilePath;

    private TaskStatus status;
    private String errorMessage;
    private String detectedLanguage;
    private Float languageProbability;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}