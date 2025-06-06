package com.niit.subtitletranslationtool.entity;

import java.beans.Transient;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.niit.subtitletranslationtool.enums.TaskStatus;

/**
 * 字幕翻译工具中的任务实体类，用于封装任务全生命周期的元数据及状态信息。
 * 包含视频/音频/字幕文件的存储信息、任务处理状态、错误信息、语言检测结果、用户关联信息及时间戳等核心数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {
    // 数据库自动生成的任务唯一标识符
    private Long id;

    // 上传阶段用户提供的原始视频文件名（必填）
    private String originalVideoFilename;
    // 系统存储时使用的视频文件名（避免重名）
    private String storedVideoFilename;
    // 视频文件在存储系统中的完整路径
    private String videoFilePath;
    // 标记是否需要将字幕烧录到视频画面中（布尔标识）
    private boolean burnSubtitles;

    // 从视频中提取的音频文件名称
    private String extractedAudioFilename;
    // 提取音频文件的完整存储路径
    private String extractedAudioFilePath;
    // 上传阶段用户提供的原始字幕文件名
    private String originalSrtFilename;
    // 原始字幕文件的完整存储路径
    private String originalSrtFilePath;
    // 翻译后生成的字幕文件名称
    private String translatedSrtFilename;
    // 翻译后字幕文件的完整存储路径
    private String translatedSrtFilePath;
    // 添加字幕后的输出视频文件名
    private String subtitledVideoFilename;
    // 添加字幕后视频的完整存储路径
    private String subtitledVideoFilePath;

    // 任务简要描述信息（可选摘要）
    private String summary;
    // 翻译后的字幕文件内容（需根据ORM配置持久化策略）
    private String translatedSrtContent;

    // 任务当前处理状态（初始值：等待上传）
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING_UPLOAD;
    // 任务执行异常时的错误详情（正常状态为空）
    private String errorMessage;
    // 自动检测到的源语言类型（如"en-US"）
    private String detectedLanguage;
    // 语言检测结果的置信度（0-1之间的浮点值）
    private Float languageProbability;
    // 关联的用户唯一标识（用于权限控制）
    private Long userId;

    // 任务记录创建时间（默认值：对象实例化时间）
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    // 任务信息最后更新时间（默认值：对象实例化时间，每次修改后自动更新）
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}