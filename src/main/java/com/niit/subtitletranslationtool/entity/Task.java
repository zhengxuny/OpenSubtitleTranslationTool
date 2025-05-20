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
    private Long id; // 数据库自增ID

    // 上传阶段必填字段（非空）
    private String originalVideoFilename;
    private String storedVideoFilename;
    private String videoFilePath; // 视频文件完整路径

    // 后续阶段可选字段（可为空）
    private String extractedAudioFilename;
    private String extractedAudioFilePath;
    private String originalSrtFilename;
    private String originalSrtFilePath;
    private String translatedSrtFilename;
    private String translatedSrtFilePath;
    private String subtitledVideoFilename;
    private String subtitledVideoFilePath;

    // 状态与元信息（非空）
    @Builder.Default // Builder模式下默认值
    private TaskStatus status = TaskStatus.PENDING_UPLOAD; // 默认等待上传
    private String errorMessage;
    private String detectedLanguage;
    private Float languageProbability;

    // 时间字段（非空）
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(); // 创建时间默认当前时间
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now(); // 更新时间默认当前时间
}