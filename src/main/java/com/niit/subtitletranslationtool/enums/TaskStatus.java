package com.niit.subtitletranslationtool.enums;

public enum TaskStatus {
    PENDING_UPLOAD, // 等待上传完成 (前端上传中，后端可能还未收到完整文件)
    UPLOADED,       // 上传完成，等待处理
    VIDEO_CHECKING,
    VIDEO_DAMAGED_AWAITING_USER_CHOICE,
    AUDIO_EXTRACTING,
    AUDIO_EXTRACTED,
    TRANSCRIBING,
    TRANSCRIBED,
    TRANSLATING,
    TRANSLATED,
    SUBTITLE_BURNING,
    COMPLETED,
    FAILED,
    CANCELLED
}