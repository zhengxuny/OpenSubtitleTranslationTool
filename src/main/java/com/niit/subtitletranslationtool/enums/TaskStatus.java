package com.niit.subtitletranslationtool.enums;

import lombok.Getter;

/**
 * 表示字幕翻译任务全流程处理状态的枚举类。
 * <p>
 * 定义任务从文件上传开始，经过视频检查、音轨提取、语音转文字、内容翻译、字幕压制等
 * 核心处理环节，直至最终完成/失败/取消的全生命周期状态，每个状态关联用于界面展示的名称。
 */
@Getter
public enum TaskStatus {
    PENDING_UPLOAD("等待上传完成"),
    UPLOADED("上传完成"),
    VIDEO_CHECKING("视频完整性检查中"),
    VIDEO_DAMAGED_AWAITING_USER_CHOICE("视频损坏，等待用户选择"),
    AUDIO_EXTRACTING("音轨提取中"),
    AUDIO_EXTRACTED("音轨提取完成"),
    EXTRACTION_FAILED("音轨提取失败"),
    TRANSCRIBING("音频转文字中"),
    TRANSCRIBED("音频转文字完成"),
    TRANSCRIPTION_FAILED("音频转文字失败"),
    TRANSLATING("字幕翻译中"),
    TRANSLATED("字幕翻译完成"),
    TRANSLATION_FAILED("字幕翻译失败"),
    SUBTITLE_BURNING("字幕压制中"),
    COMPLETED("已完成"),
    FAILED("任务失败"),
    CANCELLED("已取消");

    /**
     * -- GETTER --
     *  获取当前状态的显示名称。
     *
     * @return 用于界面展示的状态名称字符串
     */
    private final String displayName;

    /**
     * 枚举常量构造器，为每个状态设置显示名称。
     *
     * @param displayName 用于界面展示或日志输出的状态名称字符串
     */
    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

}