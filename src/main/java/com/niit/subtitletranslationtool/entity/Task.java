package com.niit.subtitletranslationtool.entity;

import com.niit.subtitletranslationtool.enums.TaskStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.beans.Transient;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Task类定义了一个字幕翻译工具中的任务实体。
// 它包含了处理视频文件、音频文件、字幕文件的信息以及任务的状态和元信息。
public class Task {
    // id 定义了数据库中自动生成的任务唯一标识符。
    private Long id;

    // originalVideoFilename 定义了上传视频文件的原始文件名，此字段在上传阶段必填，不能为空。
    private String originalVideoFilename;
    // storedVideoFilename 定义了上传后在系统中存储的视频文件的文件名。
    private String storedVideoFilename;
    // videoFilePath 定义了上传视频文件在系统中的完整存储路径。
    private String videoFilePath;
    // burnSubtitles 定义了一个布尔值，用来指示是否需要将字幕文件烧录到视频文件中。
    private boolean burnSubtitles;

    // extractedAudioFilename 定义了从原视频中提取的音频文件的文件名。
    private String extractedAudioFilename;
    // extractedAudioFilePath 定义了提取音频文件在系统中的完整存储路径。
    private String extractedAudioFilePath;
    // originalSrtFilename 定义了上传字幕文件的原始文件名。
    private String originalSrtFilename;
    // originalSrtFilePath 定义了上传字幕文件在系统中的完整存储路径。
    private String originalSrtFilePath;
    // translatedSrtFilename 定义了翻译后字幕文件的文件名。
    private String translatedSrtFilename;
    // translatedSrtFilePath 定义了翻译后字幕文件在系统中的完整存储路径。
    private String translatedSrtFilePath;
    // subtitledVideoFilename 定义了添加字幕后的视频文件的文件名。
    private String subtitledVideoFilename;
    // subtitledVideoFilePath 定义了添加字幕后的视频文件在系统中的完整存储路径。
    private String subtitledVideoFilePath;
    private String summary;  // Add summary field
     // 需要lombok注解或JPA注解，根据实际ORM调整
    private String translatedSrtContent;


    // status 定义了当前任务的状态，初始状态为等待上传。该字段在不同任务执行阶段会被更新。
    @Builder.Default // 使用Builder模式创建对象时，status默认值为PENDING_UPLOAD
    private TaskStatus status = TaskStatus.PENDING_UPLOAD;
    // errorMessage 定义了在任务处理过程中可能出现的错误信息，如果任务顺利进行此字段通常为空。
    private String errorMessage;
    // detectedLanguage 定义了在任务处理过程中检测到的字幕或语音的语言识别结果。
    private String detectedLanguage;
    // languageProbability 定义了检测语言的概率值，值的范围在0到1之间。
    private Float languageProbability;
    private Long userId;  // 新增：关联用户ID

    // createdAt 定义了任务创建的时间，默认值为对象创建后的当前时间。
    @Builder.Default // 使用Builder模式创建对象时，createdAt默认值为当前时间
    private LocalDateTime createdAt = LocalDateTime.now();
    // updatedAt 定义了任务最后一次更新的时间，默认值也为对象创建后的当前时间。
    // 此字段每次对象被修改并保存后都会被更新为当前时间。
    @Builder.Default // 使用Builder模式创建对象时，updatedAt默认值为当前时间
    private LocalDateTime updatedAt = LocalDateTime.now();
}