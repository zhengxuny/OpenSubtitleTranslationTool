package com.niit.subtitletranslationtool.enums;

public enum TaskStatus {
    PENDING_UPLOAD,       // 等待上传完成 (前端上传中，后端可能还未收到完整文件)
    UPLOADED,             // 上传完成，等待处理
    VIDEO_CHECKING,       // 视频完整性检查中
    VIDEO_DAMAGED_AWAITING_USER_CHOICE, // 视频损坏，等待用户选择
    AUDIO_EXTRACTING,     // 音轨提取中
    AUDIO_EXTRACTED,      // 音轨提取完成
    EXTRACTION_FAILED,    // 音轨提取失败（新增）
    TRANSCRIBING,         // 音频转文字中
    TRANSCRIBED,          // 音频转文字完成
    TRANSCRIPTION_FAILED, // 音频转文字失败（新增）
    TRANSLATING,          // 字幕翻译中
    TRANSLATED,           // 字幕翻译完成
    TRANSLATION_FAILED,   // 字幕翻译失败（新增）
    SUBTITLE_BURNING,     // 字幕压制中
    COMPLETED,            // 任务完成
    FAILED,               // 任务失败（通用失败状态）
    CANCELLED             // 任务取消
}