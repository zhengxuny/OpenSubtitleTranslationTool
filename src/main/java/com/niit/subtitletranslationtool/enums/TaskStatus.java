package com.niit.subtitletranslationtool.enums;

import lombok.Getter;

@Getter
public enum TaskStatus {
    PENDING_UPLOAD("等待上传完成"),       // 等待上传完成 (前端上传中，后端可能还未收到完整文件)
    UPLOADED("上传完成"),             // 上传完成，等待处理
    VIDEO_CHECKING("视频完整性检查中"),       // 视频完整性检查中
    VIDEO_DAMAGED_AWAITING_USER_CHOICE("视频损坏，等待用户选择"), // 视频损坏，等待用户选择

    AUDIO_EXTRACTING("音轨提取中"),     // 音轨提取中
    AUDIO_EXTRACTED("音轨提取完成"),      // 音轨提取完成
    EXTRACTION_FAILED("音轨提取失败"),    // 音轨提取失败（新增）
    TRANSCRIBING("音频转文字中"),         // 音频转文字中
    TRANSCRIBED("音频转文字完成"),          // 音频转文字完成
    TRANSCRIPTION_FAILED("音频转文字失败"), // 音频转文字失败（新增）
    TRANSLATING("字幕翻译中"),          // 字幕翻译中
    TRANSLATED("字幕翻译完成"),           // 字幕翻译完成
    TRANSLATION_FAILED("字幕翻译失败"),   // 字幕翻译失败（新增）
    SUBTITLE_BURNING("字幕压制中"),     // 字幕压制中
    COMPLETED("已完成"),            // 任务完成
    FAILED("任务失败"),               // 任务失败（通用失败状态）
    CANCELLED("已取消");             // 任务取消

    // 获取显示名称的方法
    private final String displayName; // 添加 displayName 属性

    // 构造函数，用于为每个枚举常量设置显示名称
    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

}